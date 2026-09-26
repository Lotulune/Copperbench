"""JSONL development console. One Bridge lives until stdin closes.

Requests: {"operation":"list_windows","arguments":{}}
For agents use the MCP server; this console is useful for inspecting the same API.
"""
import json
import sys

from minecraft_control_bridge import Bridge, BridgeError


def main():
    with Bridge() as bridge:
        print(json.dumps({"ready": True}), flush=True)
        for line in sys.stdin:
            try:
                request = json.loads(line)
                op, args = request["operation"], request.get("arguments", {})
                result = bridge.stop(**args) if op == "stop" else bridge.call(op, **args)
                print(json.dumps({"result": result}, ensure_ascii=True), flush=True)
            except BridgeError as error:
                print(json.dumps({"error": error.as_dict()}, ensure_ascii=True), flush=True)
            except (ValueError, KeyError, TypeError) as error:
                print(json.dumps({"error": {"code": "INVALID_REQUEST", "message": str(error)}}), flush=True)


if __name__ == "__main__":
    main()
