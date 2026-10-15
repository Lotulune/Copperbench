"""Discover an existing desktop MCP session without credentials in command arguments."""
import argparse
import getpass
import json
import os

from copperbench import ClientMetrics, CopperbenchClient


def main():
    parser = argparse.ArgumentParser(description="Inspect the desktop's public MCP API")
    parser.add_argument("workspace", help="Open desktop workspace root or .mcreator path")
    parser.add_argument("--metrics", action="store_true", help="Print request counts and response bytes")
    args = parser.parse_args()
    token = os.environ.get("COPPERBENCH_MCP_TOKEN") or getpass.getpass("Copperbench pairing token: ")
    metrics = ClientMetrics() if args.metrics else None
    client = CopperbenchClient.from_workspace(args.workspace, token, metrics=metrics)
    try:
        identity = client.initialize()
        tools = {tool["name"] for tool in client.list_tools()}
        if "get_workspace_environment" not in tools:
            raise RuntimeError("This host does not expose get_workspace_environment")
        environment = client.get_workspace_environment()["data"]
        print(json.dumps({"serverInfo": identity.get("serverInfo"), "generator": environment.get("generator"),
                          "execution": environment.get("execution"), "application": environment.get("application")},
                         ensure_ascii=False))
        for item in client.list_mod_elements(limit=100, fields=["id", "name", "type"]):
            print(json.dumps(item, ensure_ascii=False))
    finally:
        client.close()
        if metrics is not None:
            print(json.dumps(metrics.snapshot(), ensure_ascii=False))


if __name__ == "__main__":
    main()
