"""Exercise built-in custom field adapters through a shipped product and SDK."""
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
    args = parser.parse_args()
    product, target, output = args.product.resolve(), args.workspace.resolve(), args.output.resolve()
    legacy = target.with_name(target.name + "-legacy")
    if target.exists() or legacy.exists() or output.exists():
        parser.error("Workspace, legacy copy and evidence paths must be new.")
    if target == product or product in target.parents or target in product.parents:
        parser.error("Workspace must be separate from the product directory.")
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    shutil.copytree(product / "examples/agent-native/wayfinder-bell", target)
    output.parent.mkdir(parents=True, exist_ok=True)
    proof = {"scope": "Shipped SDK definition validation and reopen; not GUI rendering or gameplay acceptance",
             "jarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest(), "rejections": []}
    state = {"property": {"type": "logic", "name": "blocking"}, "value": True}
    gui_values = {"components": [{"type": "label", "data": {"name": "status", "x": 12, "y": 20,
                    "text": {"name": None, "fixedValue": "Verified text"}, "color": -1, "hasShadow": True}}]}

    def opened(directory):
        return Workspace.open(directory / "wayfinder_bell.mcreator", launcher=args.launcher.resolve(), cwd=product)

    def reject(call, code, path):
        revision = ws.revision
        try:
            call()
        except NativeApiError as error:
            diagnostic = error.details["diagnostics"][0]
            assert diagnostic["code"] == code and diagnostic["path"].endswith(path), error.details
            assert ws.revision == revision
            proof["rejections"].append({"code": code, "path": diagnostic["path"], "revisionUnchanged": True})
        else:
            raise AssertionError("Invalid custom field write was committed")

    try:
        with opened(target) as ws:
            proof["contract"] = ws.field_contract("custom")
            assert proof["contract"]["contractVersion"] == 1
            assert "label" in proof["contract"]["guiComponentTypes"]
            cases = [
                ("item", {"glowCondition": "false"}, "FIELD_TYPE_INVALID", "/glowCondition"),
                ("item", {"glowCondition": {"fixedValue": "false"}}, "FIELD_TYPE_INVALID", "/glowCondition/fixedValue"),
                ("item", {"glowCondition": {"fixedValue": True, "typo": 1}}, "FIELD_UNSUPPORTED", "/glowCondition/typo"),
                ("item", {"onRightClickedInAir": 42}, "FIELD_TYPE_INVALID", "/onRightClickedInAir"),
                ("item", {"specialInformation": [42]}, "FIELD_TYPE_INVALID", "/specialInformation/0"),
                ("gui", {"components": [{"type": "typo", "data": {"secret": "lost"}}]}, "FIELD_ENUM_INVALID", "/components/0/type"),
                ("gui", {"components": [{"type": "label", "data": {"name": "status", "x": 1.5}}]}, "FIELD_VALUE_OUT_OF_RANGE", "/components/0/data/x"),
                ("gui", {"components": [{"type": "label", "data": {"name": "status", "hasShadow": "false"}}]}, "FIELD_TYPE_INVALID", "/components/0/data/hasShadow"),
                ("item", {"states": [{"stateMap": [state, state]}]}, "FIELD_ALIAS_CONFLICT", "/states/0/stateMap/1/property/name"),
                ("item", {"states": [{"stateMap": [{"property": {"type": "integer", "name": "level", "min": 0, "max": 10}, "value": 1.5}]}]},
                 "FIELD_VALUE_OUT_OF_RANGE", "/states/0/stateMap/0/value"),
            ]
            for kind, values, code, path in cases:
                reject(lambda: ws.create_mod_element(elementType=kind, name="invalid_input", initialValues=values), code, path)
                assert not (target / "elements/invalid_input.mod.json").exists()
            item = ws.create_mod_element(elementType="item", name="item_probe", initialValues={
                "glowCondition": False, "specialInformation": ["one", "two"],
                "states": [{"renderType": 0, "customModelName": "Normal", "texture": "minecraft:barrier", "stateMap": [state]}]})
            gui = ws.create_mod_element(elementType="gui", name="gui_probe", initialValues=gui_values)
            gui_id = gui["data"]["element"]["id"]
            update = {"elementId": gui_id, "changes": [{"path": "/components/0/data/x", "value": 1.5}]}
            definition = target / "elements/gui_probe.mod.json"
            before = definition.read_bytes()
            reject(lambda: ws.update_mod_element(**update), "FIELD_VALUE_OUT_OF_RANGE", "/components/0/data/x")
            reject(lambda: ws.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}], idempotency_key="invalid-gui-x"),
                   "FIELD_VALUE_OUT_OF_RANGE", "/components/0/data/x")
            assert definition.read_bytes() == before
            update["changes"][0]["value"] = 24
            ws.update_mod_element(**update)
            revision = ws.revision
        with opened(target) as ws:
            assert ws.revision == revision
            ws.query("get_mod_element_editor", elementId=gui_id)
            ws.query("get_mod_element_editor", elementId=item["data"]["element"]["id"])
        stored_gui = json.loads(definition.read_text(encoding="utf-8"))["definition"]
        label = stored_gui["components"][0]["data"]
        assert label["x"] == 24 and label["text"]["fixedValue"] == "Verified text" and label["color"]["falpha"] == 0
        stored_item = json.loads((target / "elements/item_probe.mod.json").read_text(encoding="utf-8"))["definition"]
        assert stored_item["glowCondition"]["fixedValue"] is False
        assert stored_item["specialInformation"]["fixedValue"] == ["one", "two"]
        assert stored_item["states"][0]["stateMap"][0]["value"] is True
        shutil.copytree(target, legacy)
        legacy_definition = legacy / "elements/gui_probe.mod.json"
        raw = json.loads(legacy_definition.read_text(encoding="utf-8"))
        raw["definition"]["components"][0]["data"]["pluginValue"] = {"opaque": [17, "keep"]}
        legacy_definition.write_text(json.dumps(raw, indent=2), encoding="utf-8")
        original = legacy_definition.read_bytes()
        with opened(legacy) as ws:
            update = {"elementId": gui_id, "changes": [{"path": "/description", "value": "unrelated"}]}
            reject(lambda: ws.update_mod_element(**update), "FIELD_PRESERVATION_REQUIRES_REVIEW", "/components/0/data/pluginValue")
            reject(lambda: ws.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}], idempotency_key="legacy-gui"),
                   "FIELD_PRESERVATION_REQUIRES_REVIEW", "/components/0/data/pluginValue")
            assert ws.revision == revision and legacy_definition.read_bytes() == original
        assert legacy_definition.read_bytes() == original
        proof.update(status="passed", revision=revision, guiX=24, text="Verified text", stateValue=True,
                     preservedDefinitionSha256=hashlib.sha256(original).hexdigest())
    except Exception as error:
        proof.update(status="failed", error=str(error))
        raise
    finally:
        with output.open("x", encoding="utf-8") as stream:
            json.dump(proof, stream, ensure_ascii=False, indent=2)
        print(json.dumps({"status": proof["status"], "output": str(output)}))


if __name__ == "__main__":
    main()
