"""Exercise shipped field contracts in a new disposable copy through the public SDK."""
import argparse
import hashlib
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product", type=Path, required=True)
    parser.add_argument("--launcher", type=Path, required=True)
    parser.add_argument("--workspace", type=Path, required=True, help="New dedicated directory; must not exist")
    parser.add_argument("--output", type=Path, required=True, help="New evidence file; must not exist")
    args = parser.parse_args()
    product, target, output = args.product.resolve(), args.workspace.resolve(), args.output.resolve()
    if target.exists() or output.exists():
        parser.error("Workspace and evidence destinations must be new; preserve prior evidence.")
    if target == product or product in target.parents or target in product.parents:
        parser.error("Workspace must be separate from the product directory.")
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError

    shutil.copytree(product / "examples/agent-native/wayfinder-bell", target)
    document = target / "wayfinder_bell.mcreator"
    proof = {
        "at": datetime.now(timezone.utc).isoformat(), "product": str(product),
        "launcher": str(args.launcher.resolve()), "workspace": str(target),
        "scope": "Product launcher and shipped SDK: definitions and reopen, not gameplay or installer certification",
        "applicationJarSha256": hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest(),
        "cases": [], "contracts": {},
    }
    output.parent.mkdir(parents=True, exist_ok=True)

    def reject(call, code, suffix):
        revision = workspace.revision
        try:
            call()
        except NativeApiError as error:
            diagnostic = error.details["diagnostics"][0]
            assert diagnostic["code"] == code, error.details
            assert diagnostic["path"].endswith(suffix), error.details
            assert workspace.revision == revision, error.details
            proof["cases"].append({"code": code, "path": diagnostic["path"], "revisionUnchanged": True})
        else:
            raise AssertionError("Invalid write was committed")

    expected = {
        "function": {"commands": ["say before"]},
        "projectile": {"fields": {"knockback": 100, "showParticles": True}},
        "achievement": {"fields": {"title": "Verified title", "frame": "goal", "rewardXP": 100}},
        "villagertrade": {"trades": [{"price1": "Items.EMERALD", "price2": "", "offer": "Items.DIAMOND",
                                     "countPrice1": 2, "countPrice2": 1, "countOffer": 3, "level": 1,
                                     "maxTrades": 37, "xp": 5, "priceMultiplier": 0.05}]},
    }
    ids = {}
    try:
        with Workspace.open(document, launcher=args.launcher.resolve(), cwd=product) as workspace:
            for kind in ("function", "projectile", "achievement", "generic"):
                contract = workspace.field_contract(kind)
                assert contract["contractVersion"] == 1
                proof["contracts"][kind] = contract
            invalid = [
                ("function", {"commands": "say lost"}, "FIELD_TYPE_INVALID", "/commands"),
                ("function", {"commands": [42]}, "FIELD_TYPE_INVALID", "/commands/0"),
                ("function", {"commands": ["say first"], "code": "say second"}, "FIELD_ALIAS_CONFLICT", "/code"),
                ("projectile", {"showParticles": "false"}, "FIELD_TYPE_INVALID", "/showParticles"),
                ("projectile", {"knockback": 4294967296}, "FIELD_VALUE_OUT_OF_RANGE", "/knockback"),
                ("projectile", {"fields": {"damage": None}}, "FIELD_TYPE_INVALID", "/fields/damage"),
                ("achievement", {"rewardLoot": "lost"}, "FIELD_TYPE_INVALID", "/rewardLoot"),
                ("achievement", {"rewardRecipes": [42]}, "FIELD_TYPE_INVALID", "/rewardRecipes/0"),
                ("achievement", {"frame": "typo"}, "FIELD_ENUM_INVALID", "/frame"),
                ("villagertrade", {"trades": [{"countOffer": 1.5}]}, "FIELD_VALUE_OUT_OF_RANGE", "/trades/0/countOffer"),
                ("villagertrade", {"trades": [{"typo": True}]}, "FIELD_UNSUPPORTED", "/trades/0/typo"),
                ("villagertrade", {"fields": {"trades": [None]}}, "FIELD_TYPE_INVALID", "/fields/trades/0"),
                ("item", {"repairItems": [42]}, "FIELD_TYPE_INVALID", "/repairItems/0"),
                ("biome", {"groundBlock": "Blocks.GRASS_BLOCK", "undergroundBlock": "Blocks.STONE", "genDepthMin": 0.3},
                 "FIELD_UNSUPPORTED", "/genDepthMin"),
            ]
            for kind, values, code, path in invalid:
                reject(lambda: workspace.create_mod_element(elementType=kind, name="invalid_input", initialValues=values), code, path)
                assert not (target / "elements/invalid_input.mod.json").exists()
            for kind, values in expected.items():
                created = workspace.create_mod_element(elementType=kind, name=kind + "_probe", initialValues=values)
                ids[kind] = created["data"]["element"]["id"]
                definition = target / ("elements/" + kind + "_probe.mod.json")
                before = definition.read_bytes()
                field = {"function": "commands", "projectile": "knockback", "achievement": "rewardXP", "villagertrade": "trades/0/countOffer"}[kind]
                invalid_value = "say lost" if kind == "function" else 4294967296
                code = "FIELD_TYPE_INVALID" if kind == "function" else "FIELD_VALUE_OUT_OF_RANGE"
                field_path = ("/" if kind == "villagertrade" else "/fields/") + field
                update = {"elementId": ids[kind], "changes": [{"path": field_path, "value": invalid_value}]}
                reject(lambda: workspace.update_mod_element(**update), code, field_path)
                reject(lambda: workspace.plan_workspace_changes([{"operation": "update_mod_element", "payload": update}],
                        idempotency_key=kind + "-invalid"), code, field_path)
                assert before == definition.read_bytes()
                update["changes"][0]["value"] = ["say after", "say verified"] if kind == "function" else 42
                workspace.update_mod_element(**update)
            revision = workspace.revision
        with Workspace.open(document, launcher=args.launcher.resolve(), cwd=product) as workspace:
            assert workspace.revision == revision
            for element_id in ids.values():
                workspace.query("get_mod_element_editor", elementId=element_id)
            assert len(list(workspace.list_mod_elements())) == len(expected)
        definitions = {kind: json.loads((target / ("elements/" + kind + "_probe.mod.json")).read_text(encoding="utf-8"))["definition"] for kind in expected}
        assert definitions["function"]["code"] == "say after\nsay verified\n"
        assert definitions["projectile"]["knockback"] == 42 and definitions["projectile"]["showParticles"] is True
        assert definitions["achievement"]["rewardXP"] == 42
        assert definitions["achievement"]["achievementName"] == "Verified title"
        assert definitions["achievement"]["achievementType"] == "goal"
        assert definitions["villagertrade"]["trades"][0]["countOffer"] == 42
        assert definitions["villagertrade"]["trades"][0]["maxTrades"] == 37
        proof.update(status="passed", revision=revision, reopenedElementIds=ids, effectiveDefinitions=definitions)
    except Exception as error:
        proof.update(status="failed", error=str(error))
        raise
    finally:
        with output.open("x", encoding="utf-8") as stream:
            json.dump(proof, stream, ensure_ascii=False, indent=2)
        print(json.dumps({"status": proof["status"], "output": str(output)}))


if __name__ == "__main__":
    main()
