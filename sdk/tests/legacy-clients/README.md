# Fixed old Python client regression

`python-b1a69d244ab7.py` is the unchanged Git blob of
`sdk/python/copperbench.py` at
`b1a69d244ab7337608cc302826ed55d4a707f37f`, extracted with
`git cat-file blob <commit>:sdk/python/copperbench.py`. The manifest records the
commit, source path, byte count, Git blob SHA-1 and SHA-256. The test checks all
of them before importing the fixture; no network or Git history is needed when
running it in a shallow checkout.

The import name is isolated, and a guard rejects use of current Native/scripting
siblings both during loading and the common route. Only this fixed MCP module
and Python's standard library implement the legacy side of the comparison.

```sh
python -m unittest discover -s sdk/python -p 'test_legacy_client_compatibility.py'
```

The actual old client and current client send the same preserved public requests
against controlled current-protocol HTTP responses: initialization, environment
and element reads, selected-field pagination, revision-bound creates/updates,
one build, incremental queries of its original task, and the generic exact-export
tool/terminal receipt. Tests also retain complete conflict/revocation/input-change
diagnostics and reject automatic replay of an uncertain build start. The old
client has no new `wait_task`, catalog/field-contract helper or metrics API;
the regression deliberately uses its existing generic tools and manual polling.

This is SDK request/response compatibility evidence for one fixed source
revision, not live Java Core, grant issuance, artifact-byte verification, native
desktop or Minecraft acceptance. Incompatible protocol negotiation for the new
client remains in the regular Python/TypeScript regression tests. A paired
version-mismatch case records that this immutable pre-optimization client did
not validate the selected version, while the current client explicitly rejects
it before initialization notification. Do not credit old code with that new
fail-closed behavior. The comparison does not
establish compatibility with every historical product release or plugin ABI.

该 fixture 是指定旧提交的原始 SDK 字节，不是当前 SDK 的改名副本。测试执行旧实现的
公开读取、带 revision 的写入、原任务增量查询及通用精确导出调用，并保留原始错误。
结果仅覆盖受控 HTTP seam 和这个固定版本；真实 Core、授权落地、导出字节及游戏验收
仍使用各自的原始运行证据。
