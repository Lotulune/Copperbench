These projections were captured from Fabric263ItemTest through the real UI/Core adapter. Only the element UUID and timestamp were normalized, and source-management paths were omitted from the editor fixture. Browser interaction tests consume these Core fields; they do not infer the field scope. Runtime gameplay evidence is tracked separately.

`fabric-item-new-editor.json` is the empty `initialValues` creation projection, including stack size 64 and zero fuel/composting defaults. The browser create flow starts with this projection and applies edits through the controls. `fabric-item-editor.json` captures the edited sample and its field contract.

The corresponding `neoforge-item-*` projections come from `NeoForge263ItemTest` through its actual NeoForge 26.3 UI/Core session. The same normalization and empty-creation rules apply.
