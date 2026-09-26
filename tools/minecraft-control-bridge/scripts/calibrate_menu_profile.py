"""Build a local menu profile from planner-reviewed screenshot regions; sends no desktop input."""
import argparse
import json
from pathlib import Path

from minecraft_control_bridge.menu_profile import build_profile

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(build_profile(json.loads(args.spec.read_text(encoding="utf-8-sig")), args.output), indent=2))
