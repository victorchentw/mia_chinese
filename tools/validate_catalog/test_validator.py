#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from tools.notion_import.import_public_catalog import canonical_youtube, youtube_id
from tools.validate_catalog.range_probe import probe_range
from tools.validate_catalog.validate_catalog import validate_catalog


class CatalogToolTest(unittest.TestCase):
    def test_bundled_catalog_is_valid(self):
        path = Path(__file__).parents[2] / "app/src/main/assets/catalog/lessons.json"
        catalog = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual([], validate_catalog(catalog))

    def test_notion_file_references_have_permission_metadata(self):
        path = Path(__file__).parents[2] / "app/src/main/assets/catalog/lessons.json"
        catalog = json.loads(path.read_text(encoding="utf-8"))
        attachments = [
            section["attachment"]
            for edition in catalog["editions"]
            for course in edition["courses"]
            for section in course["sections"]
            if section.get("type") == "attachment" and section.get("attachment")
        ]
        notion_files = [
            attachment
            for attachment in attachments
            if (
                "prod-files-secure.s3." in attachment.get("url", "")
                or "secure.notion-static.com" in attachment.get("url", "")
            )
        ]
        self.assertTrue(notion_files)
        self.assertTrue(all(attachment.get("notionBlockId") for attachment in notion_files))
        self.assertTrue(all(attachment.get("notionSpaceId") for attachment in notion_files))

    def test_youtube_url_forms_are_normalized(self):
        cases = {
            "https://www.youtube.com/watch?v=XPvJgDZ06A8": "XPvJgDZ06A8",
            "https://www.youtube.com/embed/XPvJgDZ06A8?start=5": "XPvJgDZ06A8",
            "https://youtu.be/XPvJgDZ06A8?si=tracking": "XPvJgDZ06A8",
            "https://www.youtube.com/shorts/XPvJgDZ06A8": "XPvJgDZ06A8",
        }
        for source, expected in cases.items():
            self.assertEqual(expected, youtube_id(source))
            self.assertEqual(
                "https://www.youtube.com/watch?v=XPvJgDZ06A8",
                canonical_youtube(source, expected),
            )

    def test_range_probe_never_uses_plain_http(self):
        result = probe_range("http://example.com/video.mp4")
        self.assertFalse(result.ok)
        self.assertEqual("URL must use HTTPS", result.error)

    def test_invalid_reference_is_reported(self):
        catalog = {
            "schemaVersion": 2,
            "contentVersion": "test",
            "updatedAt": "2026-01-01T00:00:00Z",
            "editions": [{
                "id": "edition",
                "name": "版",
                "courses": [{
                    "id": "course",
                    "title": "課",
                    "sections": [{
                        "id": "section",
                        "order": 1,
                        "type": "video",
                        "title": "影片",
                        "video": {
                            "id": "video",
                            "revision": 1,
                            "sourceType": "mp4",
                            "url": "file:///private/video.mp4",
                        },
                    }],
                }],
            }],
        }
        self.assertTrue(any("HTTPS" in error for error in validate_catalog(catalog)))


if __name__ == "__main__":
    unittest.main()
