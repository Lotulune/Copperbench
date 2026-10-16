# Reviewed public-package evidence

[Run 38903643192](https://github.com/Lotulune/Copperbench/actions/runs/38903643192)
passed Windows Setup, Ubuntu Setup and Ubuntu DEB acceptance against the public
0.1.9 packages, upgrading from 0.1.7.

See [verified-summary.json](verified-summary.json), [review.json](review.json)
and [the scope report](../../../../docs/testing/cloud-acceptance-0.1.9.md).
Selected JSON receipts and representative previews are retained here. Each
lane's `evidence-hashes.json` describes its complete uploaded artifact, not just
the selected files copied into this directory. Complete downloaded artifacts are
under `output/cloud-release-runs/38903643192` in the acceptance worktree.

The intentional Fabric compilation failure is a passed recovery test. No actual
failed product task was omitted from the final success decision. Desktop/player
limits remain explicit in the report and review receipt.
