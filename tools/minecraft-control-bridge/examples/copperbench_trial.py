"""Launch using Copperbench's public SDK and wait without closing its session early.

This example requires an installed Copperbench SDK and an existing authorized test workspace.
Run this launcher alongside the MCP bridge. The lead LLM controls known steps directly
and may consult Jev for unresolved choices; this launcher calls no models or approvals.
"""
import argparse
import json


def main():
    from copperbench import Workspace
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--product", required=True)
    parser.add_argument("--task-authorization")
    args = parser.parse_args()
    with Workspace.open(args.workspace, launcher=args.product,
                        task_authorization_id=args.task_authorization, request_timeout=120) as workspace:
        accepted = workspace.run_client()
        print(json.dumps({"phase": "accepted", "result": accepted}, ensure_ascii=False), flush=True)
        task = accepted.get("task")
        if not task or not task.get("id"):
            raise RuntimeError("Copperbench did not accept a run_client task")
        # The operator selects the matching PID/window through the bridge while this waits.
        final = workspace.wait_task(task["id"], timeout=86400)
        print(json.dumps({"phase": "finished", "result": final}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
