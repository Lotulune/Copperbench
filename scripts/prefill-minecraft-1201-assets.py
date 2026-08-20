#!/usr/bin/env python3
"""Prefill official Minecraft 1.20.1 assets into the NeoGradle userdev cache.

Copies same-SHA-1 objects from Fabric Loom, NeoForm, and PCL2 caches, then
downloads remaining objects from BMCLAPI. Gradle still validates official hashes.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

INDEX_CANDIDATES = [
    Path.home() / ".gradle/caches/fabric-loom/assets/indexes/1.20.1-5.json",
    Path.home() / ".gradle/caches/minecraft/assets/indexes/asset-index.json",
]
DEST_OBJECTS = Path.home() / ".gradle/caches/minecraft/assets/objects"
SOURCE_OBJECTS = [
    Path.home() / ".gradle/caches/fabric-loom/assets/objects",
    Path.home() / ".gradle/caches/neoformruntime/assets/objects",
    Path(r"D:\Minecraft\.minecraft\assets\objects"),
]
MIRROR = "https://bmclapi2.bangbang93.com/assets"
WORKERS = 12
RETRIES = 5


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_index() -> tuple[Path, dict[str, dict]]:
    for candidate in INDEX_CANDIDATES:
        if candidate.is_file():
            payload = json.loads(candidate.read_text(encoding="utf-8"))
            objects = payload.get("objects")
            if isinstance(objects, dict) and objects:
                return candidate, objects
    raise FileNotFoundError("No 1.20.1 asset index found in Loom or NeoGradle caches")


def object_path(root: Path, digest: str) -> Path:
    return root / digest[:2] / digest


def present(path: Path, size: int, digest: str) -> bool:
    return path.is_file() and path.stat().st_size == size and sha1_file(path) == digest


def download_one(digest: str, size: int) -> str:
    dest = object_path(DEST_OBJECTS, digest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    url = f"{MIRROR}/{digest[:2]}/{digest}"
    last_error = "unknown"
    for attempt in range(1, RETRIES + 1):
        tmp = dest.with_suffix(f".part{os.getpid()}")
        try:
            with urllib.request.urlopen(url, timeout=60) as response, tmp.open("wb") as handle:
                shutil.copyfileobj(response, handle)
            if tmp.stat().st_size != size:
                last_error = f"size {tmp.stat().st_size} != {size}"
                tmp.unlink(missing_ok=True)
                time.sleep(0.4 * attempt)
                continue
            if sha1_file(tmp) != digest:
                last_error = "sha1 mismatch"
                tmp.unlink(missing_ok=True)
                time.sleep(0.4 * attempt)
                continue
            tmp.replace(dest)
            return "downloaded"
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = str(error)
            tmp.unlink(missing_ok=True)
            time.sleep(0.4 * attempt)
    raise RuntimeError(f"{digest}: {last_error}")


def main() -> int:
    index_path, objects = load_index()
    needed: dict[str, int] = {}
    for info in objects.values():
        digest = str(info["hash"])
        size = int(info["size"])
        needed[digest] = size

    already = 0
    copied = 0
    missing: list[tuple[str, int]] = []
    DEST_OBJECTS.mkdir(parents=True, exist_ok=True)
    for digest, size in needed.items():
        dest = object_path(DEST_OBJECTS, digest)
        if dest.is_file() and dest.stat().st_size == size:
            already += 1
            continue
        found = None
        for source in SOURCE_OBJECTS:
            candidate = object_path(source, digest)
            if candidate.is_file() and candidate.stat().st_size == size:
                found = candidate
                break
        if found is None:
            missing.append((digest, size))
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(found, dest)
        copied += 1

    downloaded = 0
    errors: list[str] = []
    if missing:
        with ThreadPoolExecutor(max_workers=WORKERS) as pool:
            futures = {pool.submit(download_one, digest, size): digest for digest, size in missing}
            for future in as_completed(futures):
                digest = futures[future]
                try:
                    future.result()
                    downloaded += 1
                except Exception as error:  # noqa: BLE001 — record and continue
                    errors.append(f"{digest}: {error}")

    evidence = {
        "schemaVersion": "1.0",
        "kind": "minecraft-1201-asset-prefill",
        "index": str(index_path),
        "uniqueObjects": len(needed),
        "alreadyPresent": already,
        "copiedFromLocalCaches": copied,
        "downloadedFromBmclapi": downloaded,
        "stillMissing": len(errors),
        "errors": errors[:20],
        "completedAt": datetime.now(timezone.utc).isoformat(),
    }
    repo = Path(__file__).resolve().parents[1]
    evidence_dir = repo / "evidence" / "stage-8" / datetime.now().date().isoformat()
    evidence_dir.mkdir(parents=True, exist_ok=True)
    evidence_path = evidence_dir / "neoforge-1201-assets-prefill.json"
    evidence_path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, indent=2))
    print(f"evidence={evidence_path}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
