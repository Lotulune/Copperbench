"""Write content into a data pack workspace through Core and build its ZIP.

Started by DataPackLoadDeliveryMain, which then loads the ZIP in the Fabric 1.21.1 GameTest host.
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from copperbench import Workspace

FIXTURE = Path(__file__).resolve().parents[2] / "examples" / "datapack-load" / "pack"
TAG = {"name": "probe_items", "namespace": "mod", "category": "items", "members": ["Items.STICK"]}


def main(config_path: str) -> None:
    config = json.loads(Path(config_path).read_text(encoding="utf-8"))
    evidence = Path(config["evidence"])
    record: dict = {"writes": []}

    def save(name: str, value) -> None:
        (evidence / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")

    with Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"]) as ws:
        environment = ws.get_workspace_environment(field_contracts="summary")["data"]
        save("environment-summary.json", environment)
        record["generator"] = environment.get("generator")
        point = ws.command("create_recovery_point", label="Before data pack load delivery")
        record["recoveryPoint"] = point.get("status")
        for source in sorted(path for path in FIXTURE.rglob("*") if path.is_file()):
            relative = "src/main/" + source.relative_to(FIXTURE).as_posix()
            result = ws.write_file(relative, source.read_text(encoding="utf-8"))
            assert result["status"] == "committed", result
            record["writes"].append({"path": relative, "sha256": result["data"]["sha256"]})
        tag = ws.command("create_registry_entry", registry="tags", entry=TAG)
        save("tag-entry.json", tag)
        assert tag["status"] == "committed", tag
        accepted = ws.build()
        assert accepted["status"] == "accepted", accepted
        result = ws.wait_task(accepted["task"]["id"], timeout=1500, poll_interval=1)
        save("build.json", result)
        task = result["data"]["task"]
        record["build"] = {"taskId": task["id"], "state": task["state"]}
        assert task["state"] == "succeeded", result
    archive = Path(config["workspace"]).parent / "build" / "export" / "export.zip"
    record["export"] = {"path": str(archive), "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()}
    save("workspace-result.json", record)
    print("Data pack built:", archive, flush=True)


if __name__ == "__main__":
    main(sys.argv[1])
