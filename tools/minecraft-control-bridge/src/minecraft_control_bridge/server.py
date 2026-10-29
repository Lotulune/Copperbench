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
                     instructions="The lead LLM owns goals, calibration, exceptions and acceptance. Start with mc_doctor and mc_list_windows; attach the intended client. Use ONE mc_menu_flow for reviewed create/enter/save/quit navigation. Its default Jev controller receives locally recognized textual states and chooses among bounded transitions inside the worker; do not call mc_jev_choose or the lead model for every menu button. A deterministic controller is also available. Creation requires exact world_name, explicit creation_settings and reviewed form markers. Inspect the final image/receipt. Unknown screens never receive guessed input. keyboard_policy=english temporarily closes target IME conversion or selects an already installed English layout before keys, verifies the change, and restores on detach when still owned. F8/focus guards stay active during Jev waits. Navigation/input receipts are not gameplay acceptance; detach on completion.")

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
    async def mc_attach(window_id: str, game_dir: str | None = None, focus: bool = True,
                        keyboard_policy: str = "english") -> CallToolResult:
        """Attach the exact client. english prepares IME input reversibly before keys; preserve reports IME_ACTIVE without switching. No system defaults change."""
        return await asyncio.to_thread(call, "attach", window_id=window_id, evidence_dir=str(Path(evidence_dir).resolve()), game_dir=game_dir, focus=focus, keyboard_policy=keyboard_policy)

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
                           world_name: str | None = None, timeout_ms: int = 20000,
                           controller: str = "jev", creation_settings: dict | None = None) -> CallToolResult:
        """One call runs create_world/enter_world/save_to_title/save_and_quit/wait_for_world locally.

        Default controller=jev: local exact image markers produce structured state for Jev;
        each choice is rechecked on a fresh capture before bounded input. No lead-LLM round trip
        per button. controller=deterministic skips network decisions. No silent fallback on Jev failure.
        Use a planner-reviewed profile matching viewport/language/theme, never guessed coordinates.
        Enter/create/wait requires world_name exactly matching the profile. Creation additionally
        requires explicit creation_settings matching a v2 profile's game_mode, world_type,
        allow_commands, generate_structures. It filters for duplicates, verifies each form state
        and the final name, and submits once. No delete/overwrite/dialog approvals.
        Maximum 6 batches (12 for create), <=2000 ms each, 25 seconds total. Slow creation returns
        pending with next_flow=wait_for_world; never rerun create just because loading is slow.
        F8/focus/disconnect checks also run during Jev waits. Unknown screens receive no input.
        Reusing action_id retrieves the old receipt. Gameplay still needs separate acceptance.
        Reconnect MCP after a bridge upgrade; doctor reports the running worker version.
        """
        return await asyncio.to_thread(call, "menu_flow", session_id=session_id, action_id=action_id, flow=flow,
                                       profile_path=profile_path, world_name=world_name, timeout_ms=timeout_ms,
                                       controller=controller, creation_settings=creation_settings)

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
