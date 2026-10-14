"""Real product/SDK build regression. Config must point at a disposable, empty Fabric 1.21.1 workspace.

Accepts the same launcher/workspace/cwd config as native_smoke.py; launcher can also be an installed
copperbench.sh or copperbench.exe. Writes receipts and verifies the actual compiled JAR contents.
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

from copperbench import NativeApiError, Workspace


def main(config_path):
    config = json.loads(Path(config_path).read_text(encoding="utf-8"))
    root = Path(config["workspace"]).parent
    evidence = Path(config.get("evidence", str(root.parent / "receipts")))
    evidence.mkdir(exist_ok=True)

    def save(name, value):
        (evidence / f"{name}.json").write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")

    def sources():
        return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
                for path in (root / "src").rglob("*") if path.is_file()}

    with Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"],
                        request_timeout=120, startup_timeout=120) as workspace:
        assert not list(workspace.list_mod_elements()), "Use a new disposable workspace"

        def create(kind, name, values):
            result = workspace.create_mod_element(elementType=kind, name=name, initialValues=values)
            save("create-" + name, result)
            assert result["status"] == "committed", result

        def task(name, start):
            accepted = start()
            save(name + "-accepted", accepted)
            assert accepted["status"] == "accepted", accepted
            result = workspace.wait_task(accepted["task"]["id"], timeout=1200)
            save(name, result)
            assert result["data"]["task"]["state"] == "succeeded", result
            print("PASS:", name, flush=True)
            return result

        create("block", "xiucopper_mirror", {"displayName": "岫铜镜", "hardness": 2.0, "resistance": 6.5})
        create("item", "xiucopper_mirror_item", {"displayName": "岫铜镜物品"})
        # The report's None slots are invalid. The documented empty MItemBlock value is an empty string.
        create("recipe", "xiucopper_mirror_recipe", {
            "recipeType": "Crafting", "recipeShapeless": False,
            "recipeSlots": ["Items.COPPER_INGOT", "Blocks.GLASS_PANE", "Items.COPPER_INGOT",
                            "Items.COPPER_INGOT", "Blocks.COPPER_BLOCK", "Items.COPPER_INGOT",
                            "", "Items.COPPER_INGOT", ""],
            "recipeReturnStack": "CUSTOM:xiucopper_mirror"})
        # Same name-only Procedure request as the report: it must be a valid empty body.
        create("procedure", "xiucopper_right_click", {"displayName": "岫铜镜右键"})
        task("prepare-generate", workspace.generate)

        for kind, suffix, values in [
            ("tool", "tool", {"toolType": "Pickaxe"}),
            ("livingentity", "living", {}),
            ("specialentity", "special", {}),
            ("tool", "shears", {"toolType": "Shears", "enchantability": 2, "customModelName": "Normal"}),
        ]:
            before, revision = sources(), workspace.revision
            name = "xiucopper_probe_" + suffix
            values["displayName"] = name
            try:
                workspace.create_mod_element(elementType=kind, name=name, initialValues=values)
                raise AssertionError(f"Incomplete {kind} unexpectedly committed")
            except NativeApiError as error:
                save("rejected-" + suffix, error.details)
                assert error.code == "WORKSPACE_PERSISTENCE_FAILED", error.details
            assert workspace.revision == revision
            assert sources() == before, f"Failed {kind} changed generated source or resources"
            assert not any(item["name"] == name for item in workspace.list_mod_elements())
        task("validate-after-probes", workspace.validate)
        task("generate-after-probes", workspace.generate)
        task("build-after-probes", workspace.build)

    # Reopen through the product to cover stored body projection, not just the live state.
    with Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"]) as reopened:
        accepted = reopened.validate()
        result = reopened.wait_task(accepted["task"]["id"], timeout=120)
        save("reopened-validate", result)
        assert result["data"]["task"]["state"] == "succeeded", result

    jars = [path for path in (root / "build/libs").glob("*.jar")
            if not path.name.endswith(("-sources.jar", "-dev.jar"))]
    assert len(jars) == 1, jars
    with zipfile.ZipFile(jars[0]) as jar:
        names = set(jar.namelist())
        assert "fabric.mod.json" in names
        assert any(name.endswith("/xiucopper_mirrorBlock.class") for name in names), names
        assert any(name.endswith("/xiucopper_mirror_itemItem.class") for name in names), names
        assert any(name.endswith("/xiucopper_right_clickProcedure.class") for name in names), names
        assert f"data/{Path(config['workspace']).stem}/recipe/xiucopper_mirror_recipe.json" in names, names
        assert not any("xiucopper_probe" in name.lower() for name in names), names
    save("jar", {"path": str(jars[0]), "size": jars[0].stat().st_size,
                 "sha256": hashlib.sha256(jars[0].read_bytes()).hexdigest()})
    print("PASS: product SDK -> rejected probes -> validate/generate/build -> compiled Fabric JAR", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])
