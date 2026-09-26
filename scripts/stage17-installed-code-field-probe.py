"""Validate Code text, compatibility aliases and external-source guards via the shipped SDK."""
import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("product", "launcher", "workspace", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--cold", action="store_true", help="Copy the fixture without its src directory")
    args = parser.parse_args()
    product, target, output = args.product.resolve(), args.workspace.resolve(), args.output.resolve()
    if target.exists() or output.exists():
        parser.error("Workspace and evidence destinations must be new.")
    if target == product or product in target.parents or target in product.parents:
        parser.error("Workspace must be separate from the product directory.")
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    shutil.copytree(product / "examples/agent-native/wayfinder-bell", target,
                    ignore=shutil.ignore_patterns("src") if args.cold else None)
    if args.cold:
        assert not (target / "src/main/java").exists()
    document = target / "wayfinder_bell.mcreator"
    package = json.loads(document.read_text(encoding="utf-8"))["workspaceSettings"]["modElementsPackage"]
    initial = f"package {package};\npublic class code_probe {{ public static final int VALUE = 1; }}\n"
    helper_text = f"package {package}.parts;\npublic class Helper {{}}\n"
    output.parent.mkdir(parents=True, exist_ok=True)
    proof = {"scope": "Exact manual Java file persistence and conflict guards; not compilation or lifecycle execution",
             "coldSourceRoot": args.cold, "jarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest(), "rejections": []}

    def opened():
        return Workspace.open(document, launcher=args.launcher.resolve(), cwd=product)

    def reject(call, code, suffix):
        revision = ws.revision
        try:
            call()
        except NativeApiError as error:
            diagnostic = error.details["diagnostics"][0]
            assert diagnostic["code"] == code and diagnostic["path"].endswith(suffix), error.details
            assert ws.revision == revision
            proof["rejections"].append({"code": code, "path": diagnostic["path"], "revisionUnchanged": True})
        else:
            raise AssertionError("Expected Code write to be rejected")

    try:
        with opened() as ws:
            proof["contract"] = ws.field_contract("code")
            for values, code, path in [
                ({"code": True}, "FIELD_TYPE_INVALID", "/code"),
                ({"code": None}, "FIELD_TYPE_INVALID", "/code"),
                ({"codeFiles": [{"path": "Helper.java", "code": 42}]}, "FIELD_TYPE_INVALID", "/codeFiles/0/code"),
                ({"fields": {"codeFiles": False}}, "CODE_BUNDLE_INVALID", "/fields/codeFiles"),
                ({"codeFiles": [{"path": "../Escape.java", "code": ""}]}, "CODE_BUNDLE_INVALID", "/codeFiles/0/path"),
                ({"codeFiles": [{"path": "Helper.java", "code": "", "enabled": False}]}, "FIELD_UNSUPPORTED", "/codeFiles/0/enabled"),
                ({"sourceFingerprints": True}, "FIELD_TYPE_INVALID", "/sourceFingerprints"),
                ({"sourceFingerprints": {"$primary": 42}}, "FIELD_TYPE_INVALID", "/sourceFingerprints/$primary"),
            ]:
                reject(lambda: ws.create_mod_element(elementType="code", name="invalid_input", initialValues=values), code, path)
            created = ws.create_mod_element(elementType="code", name="code_probe", initialValues={"fields": {
                "code": initial, "codeFiles": [{"path": "parts\\Helper.java", "code": helper_text}], "sourceFingerprints": {}}})
            element_id = created["data"]["element"]["id"]
            sources = list((target / "src/main/java").rglob("code_probe.java"))
            assert len(sources) == 1, sources
            primary = sources[0]
            helper = primary.parent / "parts/Helper.java"
            assert primary.read_text(encoding="utf-8") == initial and helper.read_text(encoding="utf-8") == helper_text
            update = {"elementId": element_id, "changes": [{"path": "/fields/codeFiles/0/code", "value": True}]}
            reject(lambda: ws.update_mod_element(**update), "FIELD_TYPE_INVALID", "/fields/codeFiles/0/code")
            reject(lambda: ws.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}], idempotency_key="invalid-helper"),
                   "FIELD_TYPE_INVALID", "/fields/codeFiles/0/code")
            assert primary.read_text(encoding="utf-8") == initial and helper.read_text(encoding="utf-8") == helper_text
            update["changes"] = [{"path": "/fields/code", "value": initial.replace("= 1", "= 2")}]
            ws.update_mod_element(**update)
            assert primary.read_text(encoding="utf-8") == initial.replace("= 1", "= 2")
            revision = ws.revision
        external = initial.replace("= 1", "= 3")
        primary.write_text(external, encoding="utf-8")
        with opened() as ws:
            assert ws.revision == revision
            ws.query("get_mod_element_editor", elementId=element_id)
            ws.update_mod_element(elementId=element_id, changes=[{"path": "/description", "value": "After external edit"}])
            assert primary.read_text(encoding="utf-8") == external
            latest = initial.replace("= 1", "= 5")
            primary.write_text(latest, encoding="utf-8")
            reject(lambda: ws.update_mod_element(elementId=element_id, changes=[{"path": "/fields/code", "value": initial.replace("= 1", "= 6")}]),
                   "SOURCE_CONTENT_CONFLICT", "/code")
            assert primary.read_text(encoding="utf-8") == latest
            assert helper.read_text(encoding="utf-8") == helper_text
        proof.update(status="passed", primary=str(primary), helper=str(helper),
                     externalSourcePreserved=True, normalizedBundlePath="parts/Helper.java",
                     primarySha256=hashlib.sha256(primary.read_bytes()).hexdigest(), helperSha256=hashlib.sha256(helper.read_bytes()).hexdigest())
    except Exception as error:
        proof.update(status="failed", error=str(error))
        if isinstance(error, NativeApiError):
            proof["errorDetails"] = error.details
        raise
    finally:
        with output.open("x", encoding="utf-8") as stream:
            json.dump(proof, stream, ensure_ascii=False, indent=2)
        print(json.dumps({"status": proof["status"], "output": str(output)}))


if __name__ == "__main__":
    main()
