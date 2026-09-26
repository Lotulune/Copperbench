"""Validate Procedure body persistence through the shipped launcher and SDK; no gameplay assertion."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("product", "launcher", "workspace", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    product, target, output = args.product.resolve(), args.workspace.resolve(), args.output.resolve()
    if target.exists() or output.exists() or target == product or product in target.parents or target in product.parents:
        parser.error("Use new workspace and evidence paths separate from the product.")
    shutil.copytree(product / "examples/agent-native/wayfinder-bell", target)
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    document = target / "wayfinder_bell.mcreator"
    definition = target / "elements/body_probe.mod.json"
    trigger_id, opaque_id = "22222222-2222-4222-8222-222222222222", "33333333-3333-4333-8333-333333333333"
    opaque = f'<block type="future_block" id="{opaque_id}" x="240" y="80"><mutation custom="retain"/><field name="opaque">A &amp; B</field></block>'
    ir = {"schemaVersion": "1.0", "trigger": "no_ext_trigger", "nodes": [
        {"id": trigger_id, "type": "event_trigger", "kind": "statement", "x": 0, "y": 40.5,
         "fields": {"trigger": "no_ext_trigger"}, "inputs": {}, "next": None, "unknown": False},
        {"id": opaque_id, "type": "future_block", "kind": "statement", "x": 240, "y": 80,
         "fields": {"opaque": "A & B"}, "inputs": {}, "next": None, "unknown": True, "rawPayload": opaque}],
        "dependencies": [], "unknownRoot": {"retained": "draft metadata"}}
    proof = {"scope": "Definition and IR persistence, alias synchronization and rejection atomicity; not generation or gameplay",
             "jarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest(), "rejections": []}

    def opened():
        return Workspace.open(document, launcher=args.launcher.resolve(), cwd=product)

    def read_xml():
        return json.loads(definition.read_text(encoding="utf-8"))["definition"]["procedurexml"]

    def assert_body(x, y):
        xml = read_xml()
        assert opaque in xml
        blocks = list(ET.fromstring(xml))
        trigger = next(block for block in blocks if block.attrib.get("type") == "event_trigger")
        assert float(trigger.attrib["x"]) == x and float(trigger.attrib["y"]) == y
        view = ws.query("get_procedure_editor", elementId=element_id)["data"]["ir"]
        node = next(node for node in view["nodes"] if node["id"] == trigger_id)
        assert node["x"] == x and node["y"] == y
        assert next(node for node in view["nodes"] if node["unknown"])["rawPayload"] == opaque

    def reject(call, code, suffix):
        revision = ws.revision
        before = definition.read_bytes() if definition.exists() else None
        try:
            call()
        except NativeApiError as error:
            diagnostic = error.details["diagnostics"][0]
            assert diagnostic["code"] == code and diagnostic["path"].endswith(suffix), error.details
            assert ws.revision == revision
            assert (definition.read_bytes() if definition.exists() else None) == before
            proof["rejections"].append({"code": code, "path": diagnostic["path"], "revisionAndDefinitionUnchanged": True})
        else:
            raise AssertionError("Malformed Procedure input was committed")

    try:
        with opened() as ws:
            proof["contract"] = ws.field_contract("procedure")
            duplicate = copy.deepcopy(ir)
            duplicate["nodes"].append(copy.deepcopy(duplicate["nodes"][0]))
            wrong_coordinate = copy.deepcopy(ir)
            wrong_coordinate["nodes"][0]["x"] = "12"
            for values, code, path in [
                ({"procedurexml": True}, "FIELD_TYPE_INVALID", "/procedurexml"),
                ({"procedurexml": None}, "FIELD_TYPE_INVALID", "/procedurexml"),
                ({"procedurexml": "<not_blockly/>"}, "PROCEDURE_XML_INVALID", "/procedurexml"),
                ({"fields": {"procedureIr": None}}, "FIELD_TYPE_INVALID", "/fields/procedureIr"),
                ({"procedureIr": {"nodes": False}}, "FIELD_TYPE_INVALID", "/procedureIr/nodes"),
                ({"procedureIr": {"dependencies": {}}}, "FIELD_TYPE_INVALID", "/procedureIr/dependencies"),
                ({"procedureIr": {"unknownRoot": []}}, "FIELD_TYPE_INVALID", "/procedureIr/unknownRoot"),
                ({"procedureIr": wrong_coordinate}, "FIELD_TYPE_INVALID", "/procedureIr/nodes/0/x"),
                ({"procedureIr": duplicate}, "PROCEDURE_IR_INVALID", "/procedureIr/nodes/2/id"),
                ({"procedureIr": ir, "procedurexml": "<xml/>"}, "PROCEDURE_BODY_CONFLICT", "/procedurexml"),
            ]:
                reject(lambda: ws.create_mod_element(elementType="procedure", name="invalid_input", initialValues=values), code, path)
            created = ws.create_mod_element(elementType="procedure", name="body_probe", initialValues={"fields": {"procedureIr": ir}})
            element_id = created["data"]["element"]["id"]
            assert_body(0, 40.5)
            update = {"elementId": element_id, "changes": [{"path": "/procedureIr/nodes/0/x", "value": "12"}]}
            reject(lambda: ws.update_mod_element(**update), "FIELD_TYPE_INVALID", "/procedureIr/nodes/0/x")
            reject(lambda: ws.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}], idempotency_key="invalid-body"),
                   "FIELD_TYPE_INVALID", "/procedureIr/nodes/0/x")
            replacement = f'<xml><block type="event_trigger" id="{trigger_id}" x="120" y="40.5"><field name="trigger">no_ext_trigger</field></block>{opaque}</xml>'
            update["changes"] = [{"path": "/fields/procedurexml", "value": replacement}]
            plan = ws.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}], idempotency_key="replace-body")["data"]
            ws.apply_workspace_plan(plan)
            assert read_xml() == replacement
            assert_body(120, 40.5)
        with opened() as ws:
            assert_body(120, 40.5)
            ws.update_procedure(elementId=element_id, edits=[{"operation": "move_node", "nodeId": trigger_id, "x": 240.25, "y": 0}])
            assert_body(240.25, 0)
            ws.update_mod_element(elementId=element_id, changes=[{"path": "/description", "value": "Keep both body representations current"}])
            assert_body(240.25, 0)
        with opened() as ws:
            assert_body(240.25, 0)
            proof["revision"] = ws.revision
        proof.update(status="passed", definitionSha256=hashlib.sha256(definition.read_bytes()).hexdigest(),
                     planApplied=True, reopenedTwice=True, opaquePayloadPreserved=True, coordinates=[240.25, 0])
    except Exception as error:
        proof.update(status="failed", error=str(error))
        if isinstance(error, NativeApiError):
            proof["errorDetails"] = error.details
        raise
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("x", encoding="utf-8") as stream:
            json.dump(proof, stream, ensure_ascii=False, indent=2)
        print(json.dumps({"status": proof["status"], "output": str(output)}))


if __name__ == "__main__":
    main()
