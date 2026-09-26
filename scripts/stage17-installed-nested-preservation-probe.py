"""Verify unknown nested data preservation in a fresh copy of the contract-probe workspace."""
import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("product", "launcher", "seed", "workspace", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    product, target, output = args.product.resolve(), args.workspace.resolve(), args.output.resolve()
    seed = args.seed.resolve()
    if target.exists() or output.exists() or seed == target or seed in target.parents:
        parser.error("Use new workspace/evidence paths outside the seed; preserve existing evidence.")
    if target == product or product in target.parents or target in product.parents:
        parser.error("Workspace must be separate from the product directory.")
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    shutil.copytree(seed, target)
    definition = target / "elements/villagertrade_probe.mod.json"
    raw = json.loads(definition.read_text(encoding="utf-8"))
    raw["definition"]["trades"][0]["thirdPartyFlag"] = {"keep": [17, "opaque"]}
    definition.write_text(json.dumps(raw, indent=2), encoding="utf-8")
    original = definition.read_bytes()
    proof = {"scope": "Dedicated copied definition with injected unknown nested field; not gameplay",
             "jarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest()}
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with Workspace.open(target / "wayfinder_bell.mcreator", launcher=args.launcher.resolve(), cwd=product) as workspace:
            element = next(item for item in workspace.list_mod_elements() if item["name"] == "villagertrade_probe")
            revision = workspace.revision
            workspace.query("get_mod_element_editor", elementId=element["id"])
            assert definition.read_bytes() == original
            update = {"elementId": element["id"], "changes": [{"path": "/description", "value": "unrelated"}]}
            rejected = []
            for call in (lambda: workspace.update_mod_element(**update),
                         lambda: workspace.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}],
                                                                  idempotency_key="preserve-unknown-trade")):
                try:
                    call()
                except NativeApiError as error:
                    diagnostic = error.details["diagnostics"][0]
                    assert diagnostic["code"] == "FIELD_PRESERVATION_REQUIRES_REVIEW", error.details
                    assert diagnostic["path"].endswith("/trades/0/thirdPartyFlag"), error.details
                    rejected.append({"code": diagnostic["code"], "path": diagnostic["path"]})
                else:
                    raise AssertionError("Unknown nested data accepted an unsafe rewrite")
                assert workspace.revision == revision and definition.read_bytes() == original
        with Workspace.open(target / "wayfinder_bell.mcreator", launcher=args.launcher.resolve(), cwd=product) as workspace:
            assert workspace.revision == revision and definition.read_bytes() == original
        proof.update(status="passed", rejections=rejected, revision=revision,
                     preservedDefinitionSha256=hashlib.sha256(original).hexdigest())
    except Exception as error:
        proof.update(status="failed", error=str(error))
        raise
    finally:
        with output.open("x", encoding="utf-8") as stream:
            json.dump(proof, stream, indent=2, ensure_ascii=False)
        print(json.dumps({"status": proof["status"], "output": str(output)}))


if __name__ == "__main__":
    main()
