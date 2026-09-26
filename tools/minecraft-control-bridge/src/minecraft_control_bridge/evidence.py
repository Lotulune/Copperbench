import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

from .errors import BridgeError


def utc():
    return datetime.now(timezone.utc).isoformat()


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


class Evidence:
    def __init__(self, root: Path, metadata: dict):
        self.root = root
        root.mkdir(parents=True, exist_ok=False)
        self.metadata = metadata
        self.write_json("session.json", metadata)
        self.write_json("verifications.json", [])
        self.verifications = []

    def write_json(self, name, value):
        path = self.root / name
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        temporary.replace(path)

    def event(self, event):
        with (self.root / "actions.jsonl").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps({"at": utc(), **event}, ensure_ascii=False) + "\n")
            stream.flush()

    def record_verification(self, expectation, verdict, evidence_refs, author):
        if not isinstance(expectation, str) or not expectation.strip() or len(expectation) > 8192:
            raise BridgeError("INVALID_VERIFICATION", "Provide an explicit expectation, at most 8192 characters")
        if verdict not in {"passed", "failed", "unverified"} or author != "planner":
            raise BridgeError("INVALID_VERIFICATION", "Only planner-authored passed/failed/unverified records are accepted")
        if not isinstance(evidence_refs, list) or len(evidence_refs) > 64:
            raise BridgeError("INVALID_VERIFICATION", "Provide at most 64 relative evidence paths")
        for ref in evidence_refs:
            if not isinstance(ref, str) or not (self.root / ref).resolve().is_relative_to(self.root.resolve()):
                raise BridgeError("INVALID_VERIFICATION", "Evidence must belong to this session")
            if not (self.root / ref).is_file():
                raise BridgeError("INVALID_VERIFICATION", "Evidence file does not exist")
        # A tool receipt or just a screenshot does not establish gameplay behavior.
        backed = "actions.jsonl" in evidence_refs and any(ref.endswith(".png") for ref in evidence_refs)
        record = {"at": utc(), "author": author, "expectation": expectation,
                  "verdict": verdict if backed else "unverified", "requested_verdict": verdict,
                  "evidence": [{"path": ref, "sha256_at_recording": sha256(self.root / ref)} for ref in evidence_refs],
                  "assessment_source": "external_planner", "bridge_independently_verified": False}
        self.verifications.append(record)
        self.write_json("verifications.json", self.verifications)
        return record

    def finish(self, extra):
        self.metadata.update({"ended_at": utc(), **extra})
        self.write_json("session.json", self.metadata)
        manifest = {p.relative_to(self.root).as_posix(): sha256(p) for p in sorted(self.root.rglob("*"))
                    if p.is_file() and p.name != "hashes.json"}
        self.write_json("hashes.json", manifest)
        return {"evidence_dir": str(self.root), "manifest": str(self.root / "hashes.json"),
                "verifications": self.verifications, "bridge_independently_verified": False}
