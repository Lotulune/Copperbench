import os

from sdk.python.copperbench import CopperbenchClient, read_workspace_connection

workspace_path = os.environ["COPPERBENCH_WORKSPACE"]
connection = read_workspace_connection(workspace_path)
client = CopperbenchClient(
    os.environ.get("COPPERBENCH_MCP_URL", connection["url"]),
    os.environ["COPPERBENCH_TOKEN"],
    os.environ.get("COPPERBENCH_WORKSPACE_ID", connection["workspaceId"]),
)
client.initialize()
workspace = client.get_workspace()
revision = workspace.get("data", {}).get("workspace", {}).get("revision", 0)
for element in client.list_mod_elements(fields=["id", "name", "type"]):
    print(element)
print(client.build_workspace(revision))
