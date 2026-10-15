"""Read a workspace directly, optionally build it, without MCP."""
import argparse
import json
from copperbench import Workspace

parser = argparse.ArgumentParser(description="通过 Python Core API 打开 Copperbench 工作区")
parser.add_argument("workspace", help=".mcreator 文件路径")
parser.add_argument("--launcher", required=True, help="Copperbench 启动程序路径")
parser.add_argument("--task-authorization", help="用户签发的任务授权 ID")
parser.add_argument("--build", action="store_true", help="读取后执行构建")
args = parser.parse_args()

with Workspace.open(args.workspace, launcher=args.launcher,
                    task_authorization_id=args.task_authorization) as workspace:
    workspace.require_schema()
    print(json.dumps(workspace.get_workspace_environment(), ensure_ascii=False))
    for element in workspace.list_mod_elements(limit=100, fields=["id", "name", "type"]):
        print(json.dumps(element, ensure_ascii=False))
    if args.build:
        accepted = workspace.build()
        result = workspace.wait_task(accepted["task"]["id"])
        print(json.dumps(result, ensure_ascii=False))
        if result["data"]["task"]["state"] != "succeeded":
            raise SystemExit(1)
