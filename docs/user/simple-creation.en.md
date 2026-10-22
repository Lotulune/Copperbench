# Simple creation

[简体中文](simple-creation.md) · [Installation](getting-started.en.md) · [Manual first creation in Professional](first-creation.en.md)

Simple creation brings content, conversations, texture previews and common settings into one workspace. Switch modes in the title bar without losing editor drafts, chat input or running tasks.

## Start a creation

Select **New creation**, enter a name and confirm the Minecraft version and loader. Match an existing modpack's target when joining one. The mod ID and Java package are generated automatically and can be changed in More settings. Choose a new empty directory; existing directories are not overwritten.

Use **Open creation** to select an existing `.mcreator` file. Unfinished new-project forms retain input. After successful creation, choose Create another project or revisit New creation to start a fresh form.

## Install and connect an assistant

Open **Install and connect assistants** above the composer. It lists Codex CLI, Claude Code, Grok Build, Antigravity CLI and DeepSeek Harness, with icons, installation states, versions, paths and detection errors. Select your operating system, copy a command to run in a system terminal, or open the official guide. Codex and DeepSeek npm installations need Node.js. Follow the official platform instructions for Antigravity. Select **Detect all again** after installation; restart Copperbench if the new PATH is not visible. Copperbench does not install tools or change global CLI configuration automatically.

Detection checks the executable and version. **Check CLI connection** performs a protocol handshake in a separate temporary directory. It does not create a creation session, request a model or approve tools. Antigravity has no standalone handshake. Installation, protocol availability, the last successful model request and the current execution failure are separate facts. Unknown authentication is never labeled signed in. A successful real reply verifies the model configuration only for that request.

Sign-in opens the CLI's own flow. Claude Code runs the host executable directly and keeps your configured service and credentials. Without history or a saved preference, Copperbench recommends an installed assistant. Existing preferences and conversation bindings take priority. Each conversation uses one backend; switching starts a new conversation and retains the old one. **Refresh status** does not sign in or resend anything.

Select a model at the bottom of the composer. Search, refresh the list, or enter a model ID. Codex, Grok, DeepSeek and Antigravity use their CLI model catalogs; a custom Claude service uses that service’s model endpoint, including models from other providers. Discovery errors are shown and known IDs can still be entered. Each conversation keeps its model. Changing it starts a new conversation and transfers the unsent draft; previous conversations retain their selection. This does not rewrite global CLI settings.

For [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness), run `dsh web` and configure a model in Settings → Models. Copperbench uses `dsh` and its existing configuration. It supports operation approval, stopping, and native-session recovery only after an explicit new message.

Antigravity uses its configured permission rules. Its headless interface cannot receive interactive approvals. If a command is denied, inspect the reason and handle permissions in the official CLI before explicitly sending again. Other backends display supported approvals and clarification questions. Questions can use one or several options and a custom answer when offered; submit the answer explicitly.

## Models, effort and attachments

The model menu always offers **Enter a model ID manually**, including during loading and after discovery fails. Enter the full ID supplied by your service and choose **Use model**. DSH catalogs retain provider identity; use the full ID when several providers share a model. The CLI and upstream service must still accept the selected model.

Older conversations with a saved native CLI session but no recorded model show **Keep session model**. Send a message to continue that same session without overriding its model with your current default. Choosing a model explicitly starts a new conversation and carries over the unsent draft. A new conversation without a native session still requires a model selection.

The control to the right of the model offers `low`, `medium`, `high`, `xhigh` and `max`. Choose a level supported by the model. Not every model supports every level; the CLI or service may reject or adjust the requested effort. Copperbench passes the selected value without remapping it. Effort is retained with the conversation and changes apply to the next message.

Use the paperclip, drop files, or paste images into the composer. Review thumbnails, filenames and sizes, or remove attachments before sending. Up to 8 files and 20 MiB total are allowed per message, with a 10 MiB limit per file. PNG, JPEG and GIF images must be at most 5 MiB and 24 megapixels. Changing models transfers the unsent attachments with the text draft.

Files are copied into `.copperbench/agent-chat/attachments/` in the workspace. Moving the original does not affect a sent copy. Ordinary files are provided as paths for the CLI to read. Codex and Claude receive native image input; Grok and DSH receive images only when they advertise image support. Antigravity's current streaming interface accepts only text: remove images or switch assistants. File paths are never presented as proof of visual input. Upload and capability failures retain the unsent draft with an actionable error.

Only the latest 1,000 messages are displayed. Their earlier attachments remain available to the native CLI session and are tracked until the conversation is deleted, including after reopening the workspace. Deletion reclaims copies with no remaining conversation or draft reference and releases their storage budget.

**Default permissions** follow CLI rules and show approval requests in the conversation. **Automatic approval** requires confirmation and applies only to this in-app conversation. Codex also removes its workspace sandbox; Claude, Grok and Antigravity use their session flags; DSH automatically selects a single-use allowance only for requests it actually sends. Service and system restrictions still apply. Starting a conversation with another model or assistant restores default permissions. No global settings are written.

## Animated tour and first creation

Select **Quick tour** in Simple or replay the current mode's tour from Help. The spotlight moves between real controls. Use Back and Next, or Escape/close to leave. Reduced-motion preferences retain brief fade feedback. Help also contains the full first-creation route. The professional manual route remains available.

1. **Prepare an assistant**: install and configure one host CLI. The handshake verifies only the scope above; verify the model through a real reply.
2. **Create a workspace**: select New creation, enter a name and new empty directory, and choose Fabric 1.21.1. Verify the name and generator after opening.
3. **Send a request**: use the example below. Review actual approvals and clarification questions; no exact model wording is required.
4. **Inspect saved content**: select Trail Block in Creation content and inspect its actual name, hardness and texture. Professional exposes all fields. Confirm changes were applied.
5. **Build**: use View task to check the successful terminal state and artifact. `accepted` means only that a task was accepted.
6. **Recover from failure**: read diagnostics and repair sign-in/model configuration, fields or build inputs. Inspect existing tasks before continuing; read the new result after repair to avoid duplicate operations.
7. **Playtest**: select Playtest in game, create a uniquely named isolated test world, obtain and place the block, and inspect appearance and behavior.
8. **Check persistence**: exit the client normally and wait for the task to finish. Start a new client process, reopen the same world and confirm the block and inventory remain.
9. **Export and continue**: use Export mod and inspect the actual artifact and destination. Close and reopen the creation, select the original conversation and explicitly send a message to continue.

Example request:

> Create a normal block named Trail Block in the current workspace, with the built-in stone texture, hardness 3, resistance 6 and its block item. Inspect the current generator and available fields first. Save and build through the public SDK, then report the actual results and how to playtest.

Actual generator contracts govern field names. A model reply, saved content, a successful build, gameplay checks and an exported artifact are separate results. See the [verification record for this change](../testing/simple-mode-onboarding.md) for package-level acceptance status.

## Create, playtest and export

The assistant reads the live desktop workspace through the public SDK and existing field contracts, then edits, builds and reads task results. Expand action details to inspect tool output. The preview uses actual workspace assets and settings; it does not replace game inspection.

If Professional has unsaved edits, playtest offers a choice to cancel and apply them or play saved content. Drafts for removed elements can be discarded separately without affecting other input.

Stopping the assistant retains existing changes. Core tasks may still be running and must be inspected or stopped separately in the task panel. Recovery points use the existing project history mechanism.

## Read and manage conversations

Replies support headings, lists, emphasis, links and code blocks. Copy a code block or a whole message with accessible success/failure feedback. Only HTTP/HTTPS links open through the external desktop browser. HTML and scripts are not executed. New output does not force you back down while reading earlier messages.

Use **Manage conversations** in the sidebar to inspect capacity, export or delete history. Exports contain the provider, model, effort, permission choice, timestamps, title, messages and attachment metadata (not the file contents), excluding native resume IDs and CLI configuration. Known credential fields are redacted; review personal content before sharing. Files are saved under `.copperbench/agent-chat/exports/` in the workspace and their location is shown after success.

Deletion requires confirmation and removes the selected Copperbench record and attachment copies no longer referenced by another conversation. If selected conversations have unsent text or attachments, the confirmation explains that these drafts will also be deleted. Drafts are cleared only after history deletion succeeds; cancellation or failure retains the input. Your creation, recovery points and native CLI history remain unchanged. Stop an active conversation and wait for it to settle before deleting it. Capacity is 100 conversations and 16 MiB, with a reminder at 90%. Writes beyond the budget preserve the old file and in-memory records; successful cleanup permits continued work.

An oversized or damaged existing file is never automatically overwritten. **Back up and restart** makes a uniquely named original backup in the same directory, verifies it byte for byte, then creates empty history. Raw backups may contain sensitive content. Repair permissions or disk problems before retrying failed writes.

## Leave and return

Minimizing and switching modes do not stop the assistant. Exiting with unfinished work or unsaved drafts requires confirmation, saves history and stops the owned CLI process. Reopening never resumes execution automatically; send a message to continue. Unsent chat input is a draft and is discarded if you explicitly exit without saving it.

Copperbench history lives in `.copperbench/agent-chat/`. CLI native sessions and account settings remain managed by each tool. The browser preview cannot run host CLIs.
