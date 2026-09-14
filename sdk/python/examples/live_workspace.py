"""Operate on the desktop's current workspace without MCP or another Java process."""
import argparse
from copperbench import Workspace

parser = argparse.ArgumentParser(description="连接 Copperbench 当前桌面工作区")
parser.add_argument("workspace", help="桌面已打开的 .mcreator 文件")
parser.add_argument("--element", help="要读取的元素名称或 ID")
parser.add_argument("--display-name", help="确认要提交的新显示名称")
args = parser.parse_args()
if args.display_name is not None and args.element is None:
    parser.error("--display-name requires --element")

with Workspace.connect(args.workspace) as workspace:
    if args.element is None:
        for element in workspace.elements:
            print(element.id, element.name)
    else:
        element = workspace.elements[args.element]
        print(element.name, element['/displayName'])
        if args.display_name is not None:
            element.update(displayName=args.display_name)
            print(element['/displayName'])
