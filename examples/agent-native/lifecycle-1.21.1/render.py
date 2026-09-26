"""Render a reviewable Code payload and an idempotent keep-section edit; never write a workspace."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def render(loader: str, package: str, block_id: str, main_source: str) -> dict:
    if loader not in {"fabric-1.21.1", "neoforge-1.21.1"}:
        raise ValueError("Only Fabric/NeoForge 1.21.1 is supported")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+", package):
        raise ValueError("Expected the workspace's Java package")
    if not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", block_id):
        raise ValueError("Expected an existing block namespace:path")
    source = (Path(__file__).parent / (loader.split("-")[0] + ".java.template")).read_text(encoding="utf-8")
    runtime_name = "stage17_machine_runtime"
    source = source.replace("__PACKAGE__", package).replace("__BLOCK_ID__", block_id).replace("Stage17MachineRuntime", runtime_name)
    start = "// Start of user code block mod init"
    end = "// End of user code block mod init"
    if main_source.count(start) != 1 or main_source.count(end) != 1:
        raise ValueError("Expected exactly one generated mod init keep section")
    a = main_source.index(start) + len(start)
    b = main_source.index(end)
    if b < a:
        raise ValueError("Reversed keep-section markers")
    call = f"{package}.{runtime_name}.init();"
    body = main_source[a:b]
    if ("Stage17MachineRuntime" in body or runtime_name in body) and body.count(call) != 1:
        raise ValueError("Conflicting runtime initialization; review manually")
    newline = "\r\n" if "\r\n" in main_source else "\n"
    updated = main_source if call in body else main_source[:a] + newline + "\t\t" + call + main_source[a:]
    return {
        "templateVersion": "1.1", "generatorId": loader,
        "command": {"elementType": "code", "name": runtime_name, "initialValues": {"code": source}},
        "mainSourceSha256": hashlib.sha256(main_source.encode("utf-8")).hexdigest(),
        "mainSourceChanged": updated != main_source,
        "mainSourceAfter": updated,
        "runtimeStatus": "requires_build_and_interaction_acceptance",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generator", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--block-id", required=True)
    parser.add_argument("--main-source", type=Path, required=True)
    args = parser.parse_args()
    # Preserve CRLF in the content hash and proposed edit.
    print(json.dumps(render(args.generator, args.package, args.block_id,
                            args.main_source.read_bytes().decode("utf-8")), ensure_ascii=False, indent=2))
