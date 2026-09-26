import argparse
import json

from .client import Bridge
from .errors import BridgeError


def main():
    parser = argparse.ArgumentParser(description="Local Minecraft screenshot/input bridge; F8 stops control")
    parser.add_argument("command", choices=["doctor", "windows", "serve"])
    parser.add_argument("--evidence-dir", default="./evidence")
    args = parser.parse_args()
    try:
        if args.command == "serve":
            from .server import create_server
            create_server(args.evidence_dir).run(transport="stdio")
        else:
            with Bridge() as bridge:
                result = bridge.doctor() if args.command == "doctor" else bridge.list_windows()
            print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except BridgeError as exc:
        print(json.dumps({"error": exc.as_dict()}, ensure_ascii=False))
        return 1
