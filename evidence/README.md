# Historical validation evidence

This directory stores test receipts, logs, screenshots and artifacts for the dates and candidates recorded in each report. Images may show retired Copperbench interfaces, Minecraft test sessions or failed attempts.

For promotional videos, covers and current product introductions, use the [current media index](../assets/screenshots/README.md). Do not select images from this directory as current UI footage merely because they are native desktop captures.

发布验收图片按原始日期与候选保留；当前宣传片、封面和产品介绍请使用上方素材索引。

The `stage16/` screenshots, world saves, archives and dumps of 200 KB or more are kept in commit [`ad0bc231810c`](https://github.com/Lotulune/Copperbench/tree/ad0bc231810c536c21374c36ac476defcab11ea0/evidence/stage16) instead of the working tree; no document, test, script or release manifest referenced them. Their manifests, receipts and logs remain here, and `git show ad0bc231810c536c21374c36ac476defcab11ea0:<path>` restores a file whose hash a manifest records.

Some evidence is referenced by release manifests and hash-bound authorizations. Preserve its paths and bytes when maintaining this archive; do not replace historical screenshots with new ones. The [testing index](../docs/testing/README.md) records the scope of each acceptance result, and [current downloads](../docs/releases/current.md) identifies the published versions.
