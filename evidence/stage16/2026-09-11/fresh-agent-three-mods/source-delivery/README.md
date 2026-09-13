# Independent source delivery

These three directories contain only the selected source and build inputs from the final accepted projects. Every included file was compared with the corresponding product source-snapshot SHA-256 and then verified after copying. See `source-inventory.json` and each per-project inventory for file paths, sizes, hashes, and excluded snapshot paths.

Excluded: `.mcreator` cache/setup bookkeeping, Gradle caches, local history, credentials, logs, task hosts, world saves, IDE state, and built mod artifacts. The Gradle wrapper JAR is retained as a reproducible build input. Actual tested mod JARs and reports are stored separately under the trial's `artifacts` directory.

The original product-created projects passed the recorded builds and packaged-JAR GameTests. This sanitized export has not been subjected to another clean build; no additional heavyweight process was started after the final acceptance run.

For a reproduction, use the exact packaged product identity and dependency versions in `../attempt3-inputs.json` and `../trial-summary.json`, a valid user-issued authorization for the selected directory, and the public commands:

```powershell
$productExe = '<path to the frozen copperbench.exe>'
$workspaceFile = '<absolute path to the selected project .mcreator file>'
$taskGrant = '<user-issued authorization ID>'
& $productExe headless --workspace $workspaceFile environment
& $productExe headless --workspace $workspaceFile build --task-authorization $taskGrant --stream true
& $productExe headless --workspace $workspaceFile run-gametest --task-authorization $taskGrant --stream true
```

The test configuration is `packaged_jar`; minimum acceptance counts are 5, 5, and 7. These are server gameplay and serialization/disk-state tests. They do not certify client rendering/input or a complete Minecraft process quit/reopen.
