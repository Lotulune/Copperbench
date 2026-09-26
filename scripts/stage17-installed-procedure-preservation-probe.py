"""Check that unsupported Blockly XML cannot be lost through structured product edits."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys


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
    proof = {"scope": "Unsupported XML preservation guards; no claim of native support for the blocked constructs or gameplay",
             "jarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest(), "guards": []}
    records = []
    node_id = "22222222-2222-4222-8222-222222222222"
    reader = '<block type="variables_get_number" id="44444444-4444-4444-8444-444444444444"><field name="VAR">score</field></block>'
    base_block = f'<block type="event_trigger" id="{node_id}"><field name="trigger">no_ext_trigger</field>{{}}</block>'

    def opened():
        return Workspace.open(document, launcher=args.launcher.resolve(), cwd=product)

    def read_xml(path):
        return json.loads(path.read_text(encoding="utf-8"))["definition"]["procedurexml"]

    def guard(call, label):
        revision = ws.revision
        before = {str(record["path"]): record["path"].read_bytes() for record in records}
        try:
            result = call()
        except NativeApiError as error:
            diagnostics = error.details["diagnostics"]
        else:
            assert result["data"].get("canApply") is False, result
            diagnostics = result["data"]["diagnostics"]
        assert diagnostics[0]["code"] == "PROCEDURE_XML_PRESERVATION_REQUIRED", diagnostics
        assert diagnostics[0]["path"].endswith("/procedurexml")
        assert ws.revision == revision
        assert before == {str(record["path"]): record["path"].read_bytes() for record in records}
        proof["guards"].append({"operation": label, "revisionAndDefinitionsUnchanged": True})

    def editor(element_id):
        return ws.query("get_procedure_editor", elementId=element_id)

    try:
        with opened() as ws:
            proof["contract"] = ws.field_contract("procedure")
            variable_id = ws.command("create_registry_entry", registry="variables", entry={"name": "score", "dataType": "number", "scope": "global"})["data"]["entry"]["id"]
            for name, xml in [
                ("root_variables", '<xml><variables><variable id="v">score</variable></variables>' + base_block.format("") + reader + '</xml>'),
                ("known_mutation", '<xml>' + base_block.format('<mutation extension="preserve"/>') + reader + '</xml>'),
            ]:
                created = ws.create_mod_element(elementType="procedure", name=name, initialValues={"procedurexml": xml})
                element_id = created["data"]["element"]["id"]
                record = {"id": element_id, "path": target / f"elements/{name}.mod.json", "xml": xml}
                records.append(record)
                assert read_xml(record["path"]) == xml
                view = editor(element_id)
                assert view["data"]["readOnly"] and any(d["code"] == "PROCEDURE_XML_PRESERVATION_REQUIRED" for d in view["diagnostics"])
                payload = {"elementId": element_id, "edits": [{"operation": "move_node", "nodeId": node_id, "x": 80, "y": 40}]}
                guard(lambda: ws.update_procedure(**payload), name + ":update_procedure")
                guard(lambda: ws.query("preview_procedure_change", **payload), name + ":preview_procedure")
                guard(lambda: ws.plan_workspace_changes([{"operation": "update_procedure", "payload": payload}], idempotency_key=name + "-structured"), name + ":plan_procedure")
                update = {"elementId": element_id, "changes": [{"path": "/procedureIr/nodes/0/x", "value": 80}]}
                guard(lambda: ws.update_mod_element(**update), name + ":update_ir")
                guard(lambda: ws.query("preview_mod_element_change", **update), name + ":preview_ir")
                guard(lambda: ws.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}], idempotency_key=name + "-ir"), name + ":plan_ir")
                ws.update_mod_element(elementId=element_id, changes=[{"path": "/description", "value": "Keep opaque XML content"}])
                assert read_xml(record["path"]) == xml
                record["xml"] = xml.replace('<xml>', '<xml xmlns="https://developers.google.com/blockly/xml">')
                ws.update_mod_element(elementId=element_id, changes=[{"path": "/fields/procedurexml", "value": record["xml"]}])
                assert read_xml(record["path"]) == record["xml"]
            guard(lambda: ws.query("preview_registry_rename", entryId=variable_id, newName="renamed_score"), "preview_registry_rename")
            guard(lambda: ws.command("rename_registry_entry", entryId=variable_id, newName="renamed_score"), "rename_registry_entry")
            variables = ws.query("list_workspace_registries", registry="variables")["data"]["variables"]
            assert next(item for item in variables if item["id"] == variable_id)["name"] == "score"
        with opened() as ws:
            for record in records:
                assert read_xml(record["path"]) == record["xml"]
                assert editor(record["id"])["data"]["readOnly"]
            proof["preservedXmlHashes"] = {record["path"].name: hashlib.sha256(record["xml"].encode()).hexdigest() for record in records}
            # Explicitly replacing one full body with supported XML restores graph editing.
            first = records[0]
            plain = '<xml>' + base_block.format("") + '</xml>'
            ws.update_mod_element(elementId=first["id"], changes=[{"path": "/procedurexml", "value": plain}])
            assert not editor(first["id"])["data"]["readOnly"]
            ws.update_procedure(elementId=first["id"], edits=[{"operation": "move_node", "nodeId": node_id, "x": 80, "y": 40}])
            assert 'x="80.0"' in read_xml(first["path"])
            proof["revision"] = ws.revision
        proof.update(status="passed", extendedBodiesSurvivedReopen=True, registryNamePreserved=True, editingRestoredAfterExplicitReplacement=True)
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
