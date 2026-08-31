#!/usr/bin/env python3
"""HTTP Range probe used by catalog release validation (never uses HEAD)."""

from __future__ import annotations

from dataclasses import dataclass
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class RangeProbeResult:
    url: str
    ok: bool
    status: int | None
    content_type: str
    content_range: str
    error: str | None = None


def probe_range(url: str, timeout: float = 20.0) -> RangeProbeResult:
    if not url.startswith("https://"):
        return RangeProbeResult(url, False, None, "", "", "URL must use HTTPS")
    request = Request(
        url,
        headers={
            "Accept": "video/mp4,application/pdf,application/octet-stream,*/*",
            "Range": "bytes=0-1023",
            "User-Agent": "mia-chinese-catalog-validator/1.0",
        },
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            # Read only the requested sample. A server returning 200 is not
            # rejected here: some CDNs ignore Range while still serving media.
            response.read(1024)
            status = getattr(response, "status", response.getcode())
            content_type = response.headers.get("Content-Type", "")
            content_range = response.headers.get("Content-Range", "")
            ok = status in {200, 206} and bool(content_type)
            error = None if ok else "expected HTTP 200/206 and Content-Type"
            return RangeProbeResult(url, ok, status, content_type, content_range, error)
    except Exception as error:  # noqa: BLE001 - report endpoint failure
        return RangeProbeResult(url, False, None, "", "", str(error))
