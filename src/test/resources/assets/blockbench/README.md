# Blockbench review regression models

`signal_lantern.bbmodel` and `signal_lantern_lit.bbmodel` are byte-for-byte copies of the original Copper Signal Lantern review
models from 2026-09-13 (`.tmp/s16-review/copper_signal/assets/blockbench/`).
The recorded review opened both models in the actual desktop editor and exported
their Minecraft block/item models. They retain all 13 cubes, 78 face bindings,
texture UUIDs, metadata and embedded 32x32 PNGs; they are not reduced JSON fixtures.

`signal_lantern_saved_5_1_6.bbmodel` was produced during this fix on 2026-09-13:
open the copied `signal_lantern.bbmodel` in the installed desktop Blockbench
5.1.6, then **File > Save Project As** to that new filename. The editor writes
format version `5.0`, retaining 13 cubes, 78 face bindings and one embedded PNG.
The saved model was captured directly from the editor without JSON postprocessing.

| File | SHA-256 |
| --- | --- |
| signal_lantern.bbmodel | 7dd64ea0602cb092b0a380267c0deb99f43f8a2eb9507d0e11857e3d5cbbe3f1 |
| signal_lantern_lit.bbmodel | 1ecfcf0144ae6aa57ccee07a762ea3a4502ef4ca16a3760c6c14225408a7b4fa |
| signal_lantern_saved_5_1_6.bbmodel | 7ec5582407a71a091177ad14dc70f107581dade903d9e15a0dbf235828656529 |

`BlockbenchSemanticsTest` derives destructive and reference-format variants in
temporary directories. The checked-in source models remain intact.

Local implementation evidence: the installed Blockbench 5.1.6 `app.asar`,
`dist/bundle.js`, saves `Texture.getSaveCopy()` objects in a `textures` array and
loads `relative_path` against the project directory, then `path`, then a `data:`
source. The parser follows those structural distinctions. It never interprets
`mode`, `id`, `uuid`, `name`, `folder`, or `namespace` as standalone filenames.
Layered images are explicitly marked for review because this reader does not
reconstruct the editor's layer compositor.
