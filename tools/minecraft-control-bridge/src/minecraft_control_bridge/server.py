import base64
import asyncio
import threading
import json
from contextlib import asynccontextmanager
from pathlib import Path

from mcp.server.fastmcp import FastMCP
from mcp.types import CallToolResult, ImageContent, TextContent

from .client import Bridge
from .errors import BridgeError
from .jev import JevClient


def create_server(evidence_dir="./evidence"):
    holder = {}
    initialization_lock = threading.Lock()

    @asynccontextmanager
    async def lifespan(_):
        try:
            yield {}
        finally:
            if "bridge" in holder:
                await asyncio.to_thread(holder["bridge"].close)

    server = FastMCP("minecraft-control-bridge", lifespan=lifespan,
                     instructions="The lead LLM owns goals, profile review, exceptions and acceptance. Start with mc_doctor and mc_list_windows; attach only the intended test client. Prefer mc_menu_flow for calibrated enter-world/save/quit navigation: local screenshots guard each transition, with no model/Jev call per button. Inspect the final image/receipt. Unknown menus use mc_observe/mc_step with bounded 2000 ms batches; reuse step images. Jev is optional only for unresolved choices. Keep foreground focus; F8 stops control. Navigation/input receipts never prove gameplay. Detach when finished.")

    def choose(state, goal, candidates):
        try:
            frame_id = state.get("frame_id")
            if not isinstance(frame_id, str) or not frame_id.strip():
                raise BridgeError("JEV_INVALID_REQUEST", "State must include the frame_id of the observation summarized by the lead LLM")
            with JevClient() as jev:
                result = jev.choose(state, goal, candidates)
            # Provenance supplied by the planner, not independent frame validation.
            result["frame_id"] = frame_id
            return CallToolResult(content=[TextContent(type="text", text=json.dumps(result, ensure_ascii=False))],
                                  structuredContent=result)
        except BridgeError as error:
            payload = {"error": error.as_dict()}
            return CallToolResult(isError=True, content=[TextContent(type="text", text=json.dumps(payload))],
                                  structuredContent=payload)

    def call(operation, **arguments):
        try:
            with initialization_lock:
                if "bridge" not in holder:
                    holder["bridge"] = Bridge()
            bridge = holder["bridge"]
            result = bridge.stop(**arguments) if operation == "stop" else bridge.call(operation, **arguments)
            content = [TextContent(type="text", text=json.dumps(result, ensure_ascii=False))]
            observation = result.get("observation", result)
            if "image" in observation:
                content.append(ImageContent(type="image", mimeType="image/png",
                                            data=base64.b64encode(Path(observation["image"]["path"]).read_bytes()).decode("ascii")))
            return CallToolResult(content=content, structuredContent=result)
        except BridgeError as error:
            payload = {"error": error.as_dict()}
            return CallToolResult(isError=True, content=[TextContent(type="text", text=json.dumps(payload))], structuredContent=payload)

    @server.tool()
    async def mc_doctor() -> CallToolResult:
        """Inspect desktop/backend availability; this is not a gameplay acceptance test."""
        return await asyncio.to_thread(call, "doctor")

    @server.tool()
    async def mc_list_windows() -> CallToolResult:
        """List Minecraft/Java candidates. Select the exact intended process/window."""
        return await asyncio.to_thread(call, "list_windows")

    @server.tool()
    async def mc_attach(window_id: str, game_dir: str | None = None, focus: bool = True) -> CallToolResult:
        """Bind one window, reserve F8, and create an evidence session. game_dir is the actual Minecraft run directory."""
        return await asyncio.to_thread(call, "attach", window_id=window_id, evidence_dir=str(Path(evidence_dir).resolve()), game_dir=game_dir, focus=focus)

    @server.tool()
    async def mc_observe(session_id: str, max_width: int = 1280) -> CallToolResult:
        """Return a PNG image plus frame_id/coordinates; foreground unobscured window required."""
        return await asyncio.to_thread(call, "observe", session_id=session_id, max_width=max_width)

    @server.tool()
    async def mc_jev_choose(state: dict, goal: str, candidates: dict[str, str]) -> CallToolResult:
        """Optional advice for an unresolved local choice; skip for already-decided menus or commands.

        First inspect mc_observe's image and summarize it as text/JSON in state, including frame_id,
        source='planner_visual_summary', focus and uncertainties. No images or secrets in arguments.
        candidates maps IDs to descriptions, including WAIT when observation may be insufficient.
        Uses server-side TYPESAFE_API_KEY. Returns choice, confidence, probabilities and executed=false.
        frame_id is caller-supplied provenance, not proof of a fresh frame. Review the choice and current
        state before mapping it to mc_step. On errors, report Jev unavailable; never assume an action ran.
        """
        return await asyncio.to_thread(choose, state, goal, candidates)

    @server.tool()
    async def mc_step(session_id: str, action_id: str, actions: list[dict]) -> CallToolResult:
        """Batch predictable actions up to 2000 ms; return a PNG to inspect and reuse for the next step.

        Actions: hold(keys=[],buttons=[],dx=0,dy=0,duration_ms=100), look(dx,dy,duration_ms),
        click(x,y,frame_id,button='left',duration_ms=80,settle_ms=100), scroll(steps), text(text), wait(duration_ms).
        Each action needs type. Keys: letters/digits, space, shift, ctrl, alt, enter, escape, tab,
        backspace, arrows, home/end/delete/pageup/pagedown, F1..F12 lowercase except reserved f8.
        Click coordinates refer to the returned image; look/hold dx/dy are relative OS mouse units.
        Text supports printable ASCII only and never sends Enter implicitly. Observe within 120 seconds.
        A bounded 150 ms post-release settle uses remaining batch budget (trailing wait counts).
        Stop at uncertain transitions; no blind multi-screen scripts. Repeat action_id only for the same receipt.
        Receipts include before_frame_age_ms, declared_duration_ms, post_action_settle_ms and elapsed_ms.
        """
        return await asyncio.to_thread(call, "step", session_id=session_id, action_id=action_id, actions=actions)

    @server.tool()
    async def mc_menu_flow(session_id: str, action_id: str, flow: str, profile_path: str,
                           world_name: str | None = None, timeout_ms: int = 20000) -> CallToolResult:
        """Run enter_world, save_to_title or save_and_quit locally using a reviewed image profile.

        Preferred for known menu navigation: no per-button model or Jev calls. Screenshots are
        captured/matched internally before each transition, with final image/receipt returned.
        profile_path is an absolute local JSON path built from planner-reviewed screenshots;
        its capture size, language/theme and world-name template must match the test client.
        enter_world requires explicit world_name exactly equal to the profile. Filtered results
        must match the target row with no additional rows. Never creates/deletes worlds.
        At most six input batches (each <=2000 ms) and timeout_ms 1000..25000; same focus/F8 guards.
        Unknown transition screens are observed without input until timeout; known wrong states stop.
        Never retries a dispatched click. Reusing an identical action_id returns its old receipt.
        Navigation completion is not gameplay acceptance. window_closed_unverified still needs
        process/log/Copperbench terminal-state checks. Reconnect MCP after upgrading bridge code.
        """
        return await asyncio.to_thread(call, "menu_flow", session_id=session_id, action_id=action_id, flow=flow,
                                       profile_path=profile_path, world_name=world_name, timeout_ms=timeout_ms)

    @server.tool()
    async def mc_read_logs(session_id: str, cursor: str | None = None) -> CallToolResult:
        """Read up to 64 KiB of logs/latest.log, preserving an incremental cursor and rotation status."""
        return await asyncio.to_thread(call, "read_logs", session_id=session_id, cursor=cursor)

    @server.tool()
    async def mc_record_verification(session_id: str, expectation: str, verdict: str, evidence_refs: list[str]) -> CallToolResult:
        """For the lead planner: record passed/failed/unverified with session-relative evidence paths.

        This records an external assessment, not independent bridge verification. Without actions.jsonl
        and a screenshot reference it is downgraded to unverified. Jev should return evidence to the planner.
        """
        return await asyncio.to_thread(call, "record_verification", session_id=session_id, expectation=expectation,
                    verdict=verdict, evidence_refs=evidence_refs, author="planner")

    @server.tool()
    async def mc_stop(session_id: str) -> CallToolResult:
        """Request immediate stop and input release. Reattach to resume; safe during a running step."""
        return await asyncio.to_thread(call, "stop", session_id=session_id)

    @server.tool()
    async def mc_detach(session_id: str) -> CallToolResult:
        """Release control, finalize hashes and evidence. Does not close Minecraft or its world."""
        return await asyncio.to_thread(call, "detach", session_id=session_id)

    return server
