# Copperbench 0.1.3 Windows maintenance release

The user authorized committing, pushing and publishing the JAR-folder button, with proportionate validation for this small change. The publication target is Windows 11 x64; Linux 0.1.2 and its immutable acceptance records remain unchanged. Unrelated local Minecraft-control bridge and documentation changes are excluded.

## Change and validation

- The toolbar button opens `build/libs` under the desktop host's current workspace. No arbitrary browser-supplied path is accepted. A missing directory produces build-first guidance; a missing native capability disables the button.
- `npm run build` passed, including TypeScript and Chinese/English localization checks.
- Java compilation and `JcefWindowBridgeTransportTest` / `CopperbenchProductShellResourceTest` passed (three tests) using the repository's Windows Gradle launcher after the direct launcher encountered a loopback connection error.
- `playwright test e2e/adaptive-and-frameless.spec.ts` passed all 22 cases across the existing two viewports.
- A one-off browser replay passed native-host invocation, missing-directory guidance and disabled browser-only behavior. It used a stub host; actual installed-client Explorer opening remains unverified.

Existing stable-blocking gate records are retained as historical baseline evidence, not represented as fresh 0.1.3 runs. No extra gameplay, full local regression or Linux installed acceptance is required for this Windows-only maintenance scope. GitHub's existing required PR checks and release packaging workflow still run unchanged.

## Publication

The release uses a signed `v0.1.3` tag on merged main and the existing protected Windows packaging workflow. Create the draft with [version-specific notes](../releases/v0.1.3-windows.md) before approving production, so the workflow preserves the correct release text. The `ready` declaration authorizes publication; it does not claim that packages have already been published.
