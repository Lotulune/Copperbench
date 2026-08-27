import os

from sdk.python.copperbench import CopperbenchClient

client = CopperbenchClient(
    os.environ.get("COPPERBENCH_MCP_URL", "http://127.0.0.1:8787/mcp"),
    os.environ["COPPERBENCH_TOKEN"],
    os.environ["COPPERBENCH_WORKSPACE_ID"],
)
client.initialize()
workspace = client.get_workspace()
revision = workspace.get("data", {}).get("workspace", {}).get("revision", 0)
for element in client.list_mod_elements(fields=["id", "name", "type"]):
    print(element)
print(client.build_workspace(revision))
