#!/usr/bin/env python3
"""Validate a schema-v2 Mia Chinese catalog and print release statistics."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path
try:
    from .range_probe import probe_range
except ImportError:  # direct `python validate_catalog.py` execution
    from range_probe import probe_range

YOUTUBE_ID = re.compile(r"^[A-Za-z0-9_-]{6,}$")


def validate_catalog(catalog: dict) -> list[str]:
    errors: list[str] = []
    if catalog.get("schemaVersion") != 2:
        errors.append(f"unsupported schemaVersion={catalog.get('schemaVersion')!r}")
    if not str(catalog.get("contentVersion", "")).strip():
        errors.append("contentVersion is blank")
    updated_at = str(catalog.get("updatedAt", ""))
    try:
        if not updated_at.endswith("Z"):
            raise ValueError
        datetime.fromisoformat(updated_at.replace("Z", "+00:00"))
    except ValueError:
        errors.append("updatedAt must be ISO-8601 UTC")

    editions = catalog.get("editions")
    if not isinstance(editions, list) or not editions:
        errors.append("editions is empty or not a list")
        return errors

    ids: dict[str, str] = {}
    video_ids: dict[str, str] = {}

    def check_id(value: object, kind: str) -> None:
        identifier = str(value or "").strip()
        if not identifier:
            errors.append(f"{kind} id is blank")
        elif identifier in ids:
            errors.append(f"duplicate id={identifier} ({ids[identifier]}/{kind})")
        else:
            ids[identifier] = kind

    for edition in editions:
        check_id(edition.get("id"), "edition")
        if not str(edition.get("name", "")).strip():
            errors.append(f"edition {edition.get('id')} name is blank")
        for course in edition.get("courses", []) or []:
            check_id(course.get("id"), "course")
            if not str(course.get("title", "")).strip():
                errors.append(f"course {course.get('id')} title is blank")
            sections = course.get("sections", []) or []
            orders = Counter(section.get("order") for section in sections)
            for order, count in orders.items():
                if count > 1:
                    errors.append(f"course {course.get('id')} duplicate section order={order}")
            for section in sections:
                check_id(section.get("id"), "section")
                if not isinstance(section.get("order"), int) or section["order"] < 1:
                    errors.append(f"section {section.get('id')} order must be >= 1")
                if not str(section.get("title", "")).strip():
                    errors.append(f"section {section.get('id')} title is blank")
                section_type = str(section.get("type", "")).lower()
                video = section.get("video")
                attachment = section.get("attachment")
                if section_type == "video":
                    if not isinstance(video, dict):
                        errors.append(f"video section {section.get('id')} has no video")
                        continue
                    check_id(video.get("id"), "video")
                    video_id = str(video.get("id", ""))
                    if video_id in video_ids:
                        errors.append(f"duplicate video id={video_id}")
                    else:
                        video_ids[video_id] = section.get("id", "")
                    if int(video.get("revision", 0) or 0) < 1:
                        errors.append(f"video {video.get('id')} revision must be >= 1")
                    source_type = str(video.get("sourceType", "")).lower()
                    if source_type == "mp4":
                        if not str(video.get("url", "")).startswith("https://"):
                            errors.append(f"MP4 {video.get('id')} must use HTTPS URL")
                    elif source_type == "youtube":
                        youtube_id = str(video.get("videoId", ""))
                        if not YOUTUBE_ID.fullmatch(youtube_id):
                            errors.append(f"YouTube {video.get('id')} has invalid videoId")
                        if video.get("url") and not str(video["url"]).startswith("https://"):
                            errors.append(f"YouTube {video.get('id')} URL must use HTTPS")
                    else:
                        errors.append(f"video {video.get('id')} has unsupported sourceType={source_type}")
                    if attachment is not None:
                        errors.append(f"video section {section.get('id')} must not have attachment")
                elif section_type == "attachment":
                    if not isinstance(attachment, dict):
                        errors.append(f"attachment section {section.get('id')} has no attachment")
                    else:
                        check_id(attachment.get("id"), "attachment")
                        if not str(attachment.get("title", "")).strip():
                            errors.append(f"attachment {attachment.get('id')} title is blank")
                        if attachment.get("url") and not str(attachment["url"]).startswith("https://"):
                            errors.append(f"attachment {attachment.get('id')} must use HTTPS URL")
                    if video is not None:
                        errors.append(f"attachment section {section.get('id')} must not have video")
                elif section_type in {"heading", "note"}:
                    if video is not None or attachment is not None:
                        errors.append(f"{section_type} section {section.get('id')} has media")
                else:
                    errors.append(f"section {section.get('id')} has unsupported type={section_type}")
    return sorted(set(errors))


def report(catalog: dict, errors: list[str], raw_bytes: bytes | None = None) -> dict:
    editions = catalog.get("editions", []) or []
    sections = [s for e in editions for c in e.get("courses", []) for s in c.get("sections", [])]
    videos = [s.get("video", {}) for s in sections if s.get("type") == "video" and isinstance(s.get("video"), dict)]
    attachments = [s.get("attachment", {}) for s in sections if s.get("type") == "attachment" and isinstance(s.get("attachment"), dict)]
    source_counts = Counter(
        str(video.get("videoId") or video.get("url"))
        for video in videos
        if video.get("videoId") or video.get("url")
    )
    duplicate_sources = {source: count for source, count in source_counts.items() if count > 1}
    invalid_links = [
        str(item.get("url"))
        for item in videos + attachments
        if item.get("url") and not str(item.get("url")).startswith("https://")
    ]
    return {
        "schemaVersion": catalog.get("schemaVersion"),
        "contentVersion": catalog.get("contentVersion"),
        "sha256": hashlib.sha256(
            raw_bytes
            if raw_bytes is not None
            else json.dumps(catalog, ensure_ascii=False, separators=(",", ":")).encode()
        ).hexdigest(),
        "editions": len(editions),
        "courses": sum(len(e.get("courses", []) or []) for e in editions),
        "sections": len(sections),
        "videos": len(videos),
        "youtube": sum(str(v.get("sourceType", "")).lower() == "youtube" for v in videos),
        "mp4": sum(str(v.get("sourceType", "")).lower() == "mp4" for v in videos),
        "attachments": len(attachments),
        "duplicateVideoSources": duplicate_sources,
        "invalidLinks": sorted(set(invalid_links)),
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--json", action="store_true", help="print machine-readable report")
    parser.add_argument(
        "--check-links",
        action="store_true",
        help="probe media/file URLs with GET Range (may report temporary CDN failures)",
    )
    args = parser.parse_args()
    try:
        raw_bytes = args.catalog.read_bytes()
        catalog = json.loads(raw_bytes.decode("utf-8"))
        if not isinstance(catalog, dict):
            raise ValueError("catalog root must be an object")
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"catalog read failed: {error}", file=sys.stderr)
        return 2
    errors = validate_catalog(catalog)
    link_checks = []
    if args.check_links and not errors:
        urls = sorted({
            item.get("url")
            for edition in catalog.get("editions", [])
            for course in edition.get("courses", [])
            for section in course.get("sections", [])
            for item in ([section.get("video")] if section.get("video") else []) +
                ([section.get("attachment")] if section.get("attachment") else [])
            if isinstance(item, dict) and item.get("url")
        })
        for url in urls:
            result = probe_range(url)
            link_checks.append(result.__dict__)
            if not result.ok:
                errors.append(f"link probe failed: {url}: {result.error}")
    result = report(catalog, sorted(set(errors)), raw_bytes=raw_bytes)
    if link_checks:
        result["linkChecks"] = link_checks
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(
            f"editions={result['editions']} courses={result['courses']} "
            f"sections={result['sections']} videos={result['videos']} "
            f"youtube={result['youtube']} mp4={result['mp4']} attachments={result['attachments']}"
        )
        if errors:
            print("validation errors:", file=sys.stderr)
            print("\n".join(f"- {error}" for error in errors), file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
