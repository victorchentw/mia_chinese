#!/usr/bin/env python3
"""Import the public 115-1 國七 Notion page into the bundled catalog.

The importer only reads a public Notion page. It does not read or write any
credentials. Notion-hosted file URLs are kept as source references until the
media migration to a managed CDN is completed, together with the public block
metadata needed to request a temporary download URL.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse, urlunparse
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_catalog.validate_catalog import report, validate_catalog  # noqa: E402

DEFAULT_PAGE_ID = "39e62249-b4ca-8067-9529-fdf2ec7f9f32"
DEFAULT_HOST = "sly-clove-390.notion.site"

# These are the three collection views in the public page. The IDs are stable
# Notion collection IDs; the display names are the names used by the TV UI.
COLLECTIONS = {
    "ba962249-b4ca-8241-8856-0765af39d9c3": ("hanlin-115-fall", "翰林版"),
    "e7a62249-b4ca-8367-9145-87bf2fd23b5b": ("kanghsuan-115-fall", "康軒版"),
    "37262249-b4ca-83b5-8acc-873d48643d13": ("nani-115-fall", "南一版"),
}


def plain(value: object) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        parts: list[str] = []
        for item in value:
            if isinstance(item, list):
                parts.append(plain(item[0] if item else ""))
            else:
                parts.append(plain(item))
        return "".join(parts)
    if isinstance(value, dict):
        return plain(value.get("text") or value.get("content") or "")
    return ""


def property_text(block: dict, key: str) -> str:
    return plain(block.get("properties", {}).get(key, "")).strip()


def youtube_id(source: str) -> str | None:
    parsed = urlparse(source)
    host = parsed.netloc.lower().split(":", 1)[0]
    if host in {"youtu.be", "www.youtu.be"}:
        return parsed.path.strip("/").split("/", 1)[0] or None
    if "youtube" not in host:
        return None
    if parsed.path == "/watch":
        return parse_qs(parsed.query).get("v", [None])[0]
    match = re.match(r"^/(?:embed|shorts|live)/([^/]+)", parsed.path)
    return match.group(1) if match else None


def canonical_youtube(source: str, video_id: str) -> str:
    del source
    return urlunparse(
        ("https", "www.youtube.com", "/watch", "", urlencode({"v": video_id}), "")
    )


def get_block(payload: dict, block_id: str) -> dict:
    return (
        payload.get("recordMap", {})
        .get("block", {})
        .get(block_id, {})
        .get("value", {})
        .get("value", {})
    )


def fetch_json(endpoint: str, page_id: str) -> dict:
    body = json.dumps(
        {"page": {"id": page_id}, "cursor": {"stack": []}, "verticalColumns": False}
    ).encode("utf-8")
    request = Request(
        endpoint,
        data=body,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "User-Agent": "mia-chinese-catalog-importer/1.0",
            "Referer": endpoint.rsplit("/api/", 1)[0] + "/",
        },
    )
    with urlopen(request, timeout=45) as response:
        return json.loads(response.read())


def parent_instructions(parent: dict, parent_id: str) -> list[str]:
    page = get_block(parent, parent_id)
    result: list[str] = []
    for block_id in page.get("content", []) or []:
        block = get_block(parent, block_id)
        block_type = block.get("type")
        if block_type == "header":
            break
        if block_type in {"sub_header", "sub_sub_header"}:
            text = property_text(block, "title")
            if text:
                result.append(text)
    return result


def import_catalog(page_id: str, host: str, workers: int) -> dict:
    endpoint = f"https://{host}/api/v3/loadCachedPageChunkV2"
    parent = fetch_json(endpoint, page_id)
    record_map = parent.get("recordMap", {})
    views: dict[str, list[str]] = {}
    for record in record_map.get("collection_view", {}).values():
        value = record.get("value", {}).get("value", {})
        collection_id = (
            value.get("format", {}).get("collection_pointer", {}).get("id")
        )
        if collection_id in COLLECTIONS:
            views[collection_id] = value.get("page_sort", []) or []

    if set(views) != set(COLLECTIONS):
        missing = sorted(set(COLLECTIONS) - set(views))
        raise RuntimeError(f"public page is missing collection views: {missing}")

    page_ids = list(dict.fromkeys(page_id for ids in views.values() for page_id in ids))
    pages: dict[str, dict] = {}
    failures: list[str] = []
    with ThreadPoolExecutor(max_workers=max(1, workers)) as executor:
        futures = {executor.submit(fetch_json, endpoint, child_id): child_id for child_id in page_ids}
        for future in as_completed(futures):
            child_id = futures[future]
            try:
                pages[child_id] = future.result()
            except Exception as error:  # noqa: BLE001 - report the page ID clearly
                failures.append(f"{child_id}: {error}")
    if failures:
        raise RuntimeError("failed to fetch course pages:\n" + "\n".join(sorted(failures)))

    editions: list[dict] = []
    max_edited = int(get_block(parent, page_id).get("last_edited_time", 0) or 0)
    instructions = parent_instructions(parent, page_id)

    for collection_id, (edition_id, edition_name) in COLLECTIONS.items():
        courses: list[dict] = []
        for child_id in views[collection_id]:
            payload = pages[child_id]
            page = get_block(payload, child_id)
            title = property_text(page, "title")
            # page_sort can retain deleted/empty rows; they are not courses.
            if not title:
                continue
            max_edited = max(max_edited, int(page.get("last_edited_time", 0) or 0))
            course_id = f"{edition_id}-course-{child_id.replace('-', '')}"
            sections: list[dict] = []
            current_heading = ""
            heading_video_numbers: dict[str, int] = {}

            for order, block_id in enumerate(page.get("content", []) or [], start=1):
                block = get_block(payload, block_id)
                if not block or block.get("alive") is False:
                    continue
                block_type = block.get("type")
                section_id = f"{course_id}-section-{block_id.replace('-', '')}"

                if block_type in {"header", "sub_header", "sub_sub_header"}:
                    text = property_text(block, "title")
                    if not text:
                        continue
                    current_heading = text
                    sections.append(
                        {"id": section_id, "order": order, "type": "heading", "title": text}
                    )
                    continue

                if block_type == "video":
                    source = property_text(block, "source")
                    if not source:
                        continue
                    video_id = youtube_id(source)
                    media_title = (
                        property_text(block, "title")
                        or str(block.get("format", {}).get("link_title", "")).strip()
                    )
                    heading_video_numbers[current_heading] = (
                        heading_video_numbers.get(current_heading, 0) + 1
                    )
                    video_number = heading_video_numbers[current_heading]
                    if video_id:
                        source_type = "youtube"
                        normalized_source = canonical_youtube(source, video_id)
                        title_hint = media_title or current_heading or "課程影片"
                        description_hint = (
                            current_heading
                            if media_title and current_heading and media_title != current_heading
                            else None
                        )
                    else:
                        # Uploaded Notion videos are MP4 in this course page.
                        source_type = "mp4"
                        normalized_source = source
                        # Prefer the surrounding Notion heading for the TV title;
                        # retain the uploaded filename as a useful description.
                        title_hint = current_heading or media_title or "課程影片"
                        description_hint = media_title or None
                    if video_number > 1 and not media_title:
                        title_hint = (
                            f"{current_heading or '課程影片'}（第 {video_number} 部）"
                        )
                    elif video_number > 1 and source_type == "mp4":
                        title_hint = f"{title_hint}（第 {video_number} 部）"
                    video = {
                        "id": f"{course_id}-video-{block_id.replace('-', '')}",
                        "revision": 1,
                        "sourceType": source_type,
                        "url": normalized_source,
                    }
                    if video_id:
                        video["videoId"] = video_id
                    sections.append(
                        {
                            "id": section_id,
                            "order": order,
                            "type": "video",
                            "title": title_hint,
                            "description": description_hint,
                            "video": video,
                        }
                    )
                    continue

                if block_type in {"text", "bulleted_list", "numbered_list", "quote", "callout"}:
                    text = property_text(block, "title")
                    if not text:
                        continue
                    if block_type == "bulleted_list":
                        text = f"• {text}"
                    elif block_type == "numbered_list":
                        text = f"{order}. {text}"
                    sections.append(
                        {
                            "id": section_id,
                            "order": order,
                            "type": "note",
                            "title": "補充說明",
                            "description": text,
                        }
                    )
                    continue

                if block_type in {"pdf", "file", "audio", "image"}:
                    source = property_text(block, "source")
                    title_hint = (
                        property_text(block, "title")
                        or str(block.get("format", {}).get("link_title", "")).strip()
                        or "附件"
                    )
                    attachment = {
                        "id": f"{course_id}-attachment-{block_id.replace('-', '')}",
                        "kind": block_type,
                        "title": title_hint,
                    }
                    if source:
                        attachment["url"] = source
                        # Notion's S3 source references are private. Keep the
                        # permission record so the TV app can request a fresh,
                        # short-lived download URL when it renders a QR code.
                        space_id = str(block.get("space_id") or "").strip()
                        if space_id:
                            attachment["notionBlockId"] = block_id
                            attachment["notionSpaceId"] = space_id
                    sections.append(
                        {
                            "id": section_id,
                            "order": order,
                            "type": "attachment",
                            "title": title_hint,
                            "description": current_heading or None,
                            "attachment": attachment,
                        }
                    )

            courses.append(
                {
                    "id": course_id,
                    "title": title,
                    "instructions": [],
                    "sections": sections,
                }
            )

        editions.append(
            {
                "id": edition_id,
                "name": edition_name,
                "grade": "七年級",
                "semester": "115 學年度上學期",
                "instructions": instructions,
                "courses": courses,
            }
        )

    if max_edited:
        updated_at = datetime.fromtimestamp(max_edited / 1000, tz=timezone.utc).isoformat().replace(
            "+00:00", "Z"
        )
        content_version = f"notion-115-{max_edited}"
    else:
        updated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        content_version = "notion-115-unknown"
    return {
        "schemaVersion": 2,
        "contentVersion": content_version,
        "updatedAt": updated_at,
        "editions": editions,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--page-id", default=DEFAULT_PAGE_ID)
    parser.add_argument("--host", default=DEFAULT_HOST, help="public *.notion.site host")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/catalog/lessons.json"),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="fetch and summarize the catalog without writing the output file",
    )
    parser.add_argument(
        "--report",
        type=Path,
        help="optional JSON validation report path",
    )
    args = parser.parse_args()
    try:
        catalog = import_catalog(args.page_id, args.host, args.workers)
        errors = validate_catalog(catalog)
        if errors:
            raise RuntimeError("generated catalog failed validation:\n" + "\n".join(errors))
        output_text = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(
                json.dumps(
                    report(catalog, errors, raw_bytes=output_text.encode("utf-8")),
                    ensure_ascii=False,
                    indent=2,
                ) + "\n",
                encoding="utf-8",
            )
        if not args.dry_run:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(output_text, encoding="utf-8")
    except Exception as error:  # noqa: BLE001 - CLI should return a useful error
        print(f"Notion import failed: {error}", file=sys.stderr)
        return 1

    print("dry-run complete" if args.dry_run else f"wrote {args.output}")
    for edition in catalog["editions"]:
        sections = [section for course in edition["courses"] for section in course["sections"]]
        print(
            f"{edition['name']}: courses={len(edition['courses'])} "
            f"videos={sum(section['type'] == 'video' for section in sections)} "
            f"notes={sum(section['type'] == 'note' for section in sections)} "
            f"attachments={sum(section['type'] == 'attachment' for section in sections)}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
