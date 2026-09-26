"""Validate reference existence and target kinds through the shipped SDK; return/context types are out of scope."""
import argparse
import copy
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
    product, target, output = (getattr(args, name).resolve() for name in ("product", "workspace", "output"))
    if target.exists() or output.exists() or target == product or product in target.parents or target in product.parents:
        parser.error("Use new fixture and evidence paths outside the product.")
    shutil.copytree(product / "examples/agent-native/wayfinder-bell", target)
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    proof = {"jarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest(),
             "scope": "Known call-node and typed Procedure reference existence/kind, including nested maps and GUI components; not return types, trigger contexts, compilation or gameplay", "checks": []}
    document = target / "wayfinder_bell.mcreator"
    call_id = "33333333-3333-4333-8333-333333333333"

    def create(kind, name, values):
        return ws.create_mod_element(elementType=kind, name=name, initialValues=values)["data"]["element"]["id"]

    def body(callee):
        return {"schemaVersion": "1.0", "trigger": "no_ext_trigger", "nodes": [
            {"id": "22222222-2222-4222-8222-222222222222", "type": "event_trigger", "kind": "statement", "x": 40, "y": 40,
             "fields": {"trigger": "no_ext_trigger"}, "inputs": {}, "next": call_id, "unknown": False},
            {"id": call_id, "type": "call_procedure", "kind": "statement", "x": 80, "y": 40,
             "fields": {"procedureId": callee, "procedure": callee}, "inputs": {}, "next": None, "unknown": False}],
            "dependencies": [], "unknownRoot": {}}

    def graph():
        return ws.query("get_workspace_references")["data"]

    def edges(g, source):
        return [e for e in g["edges"] if e["sourceId"] == source and e["kind"] == "procedure"]

    def codes(g, source):
        return [d["code"] for d in g["diagnostics"] if d["elementId"] == source]

    def opened():
        return Workspace.open(document, launcher=args.launcher.resolve(), cwd=product)

    try:
        with opened() as ws:
            caller = create("procedure", "caller", {"fields": {"procedureIr": body("target_proc")}})
            g = graph()
            assert len(edges(g, caller)) == 1 and codes(g, caller) == ["WORKSPACE_REFERENCE_DANGLING"]
            proof["checks"].append("Call remains visible with empty dependencies and compatibility aliases")
            wrong = create("item", "wrong_target", {"displayName": "missing_condition", "glowCondition": False})
            wrong_caller = create("procedure", "wrong_caller", {"procedureIr": body("wrong_target")})
            assert codes(graph(), wrong_caller) == ["WORKSPACE_REFERENCE_TYPE_MISMATCH"]
            proof["checks"].append("A same-named item cannot satisfy a Procedure call")
            target_id = create("procedure", "target_proc", {})
            variable_id = ws.command("create_registry_entry", registry="variables", entry={"name": "target_proc", "dataType": "number", "scope": "global"})["data"]["entry"]["id"]
            g = graph()
            assert not codes(g, caller) and edges(g, caller)[0]["targetId"] == target_id != variable_id
            proof["checks"].append("New Procedure resolves an unchanged caller; a same-named registry entry does not shadow it")
            referenced_item = create("item", "referenced_item", {"fields": {"glowCondition": {"name": "missing_condition", "fixedValue": False}, "onRightClickedInAir": {"name": "target_proc"}, "specialInformation": ["fixed text", "minecraft:stone"]}})
            g = graph()
            assert len(edges(g, referenced_item)) == 2 and codes(g, referenced_item) == ["WORKSPACE_REFERENCE_DANGLING"]
            assert next(e for e in edges(g, referenced_item) if e["sourcePath"] == "/glowCondition")["targetId"] is None
            proof["checks"].append("Typed named fields are indexed; fixed text and another element's display label do not bind")
            before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (target / "elements").glob("*.mod.json")}
            g = graph()
            health = ws.query("get_workspace_health")["data"]["references"]
            assert health["danglingCount"] == len(g["diagnostics"]) == 2
            assert before == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (target / "elements").glob("*.mod.json")}
            proof["checks"].append("Reference and health diagnostics agree; queries preserve definition bytes")
            ws.update_procedure(elementId=wrong_caller, edits=[{"operation": "update_node", "nodeId": call_id,
                              "fields": {"procedureId": target_id, "procedure": "target_proc"}}])
            ws.update_mod_element(elementId=referenced_item, changes=[{"path": "/fields/glowCondition", "value": False}])
            g = graph()
            assert not codes(g, wrong_caller) and edges(g, wrong_caller)[0]["targetId"] == target_id
            assert len(edges(g, referenced_item)) == 1 and not codes(g, referenced_item)
            assert not g["diagnostics"]
            proof["checks"].append("Stable UUID binding resolves; replacing a named condition with a fixed boolean removes its edge")
            nested_item = create("item", "nested_item", {"fields": {"customProperties": {"state": "missing_nested"}, "glowCondition": False}})
            nested_gui = create("gui", "nested_gui", {"components": [
                {"type": "button", "data": {"name": "run", "x": 10, "y": 10, "width": 60, "height": 20,
                 "text": "Run", "onClick": "missing_nested", "displayCondition": None}},
                {"type": "label", "data": {"name": "fixed", "x": 10, "y": 35, "color": -1,
                 "text": {"name": None, "fixedValue": "minecraft:stone"}}}]})
            g = graph()
            assert len(edges(g, nested_item)) == len(edges(g, nested_gui)) == 1
            assert edges(g, nested_item)[0]["sourcePath"] == "/customProperties/state"
            assert edges(g, nested_gui)[0]["sourcePath"] == "/components/0/data/onClick"
            assert codes(g, nested_item) == codes(g, nested_gui) == ["WORKSPACE_REFERENCE_DANGLING"]
            assert not any(e["sourceId"] == nested_gui and "/components/1/" in e["sourcePath"] for e in g["edges"])
            for diagnostic in g["diagnostics"]:
                assert diagnostic["path"] == diagnostic["actions"][0]["target"]
            before_nested = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (target / "elements").glob("*.mod.json")}
            assert ws.query("get_workspace_health")["data"]["references"]["danglingCount"] == 2
            assert before_nested == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (target / "elements").glob("*.mod.json")}
            proof["checks"].append("Nested property maps and GUI hooks produce exact field diagnostics; fixed labels are not references and health queries preserve definitions")
            unresolved_revision = ws.revision
        with opened() as ws:
            assert ws.revision == unresolved_revision
            g = graph()
            assert codes(g, nested_item) == codes(g, nested_gui) == ["WORKSPACE_REFERENCE_DANGLING"]
            nested_target = create("procedure", "missing_nested", {})
            g = graph()
            assert not g["diagnostics"]
            assert edges(g, nested_item)[0]["targetId"] == edges(g, nested_gui)[0]["targetId"] == nested_target
            for path in (target / "elements").glob("nested_*.mod.json"):
                assert hashlib.sha256(path.read_bytes()).hexdigest() == before_nested[str(path)]
            proof["checks"].append("Unresolved nested references survive reopen and resolve when their target is added without rewriting either caller")
            revision = ws.revision
        with opened() as ws:
            assert ws.revision == revision
            g = graph()
            assert not g["diagnostics"] and edges(g, caller)[0]["targetId"] == target_id and edges(g, wrong_caller)[0]["targetId"] == target_id
            assert len(edges(g, referenced_item)) == 1
            assert edges(g, nested_item)[0]["targetId"] == edges(g, nested_gui)[0]["targetId"] == nested_target
            proof["checks"].append("Reference results survive close and reopen")
        proof.update(status="passed", revision=revision, reopened=True, queryDefinitionsUnchanged=True)
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
