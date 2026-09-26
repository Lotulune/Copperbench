# PRD17 v36 原共鸣工坊定向回归

**场景 B 原 14 项通过。** 这是主线程定向补验，不是第四次独立评估。原 v7 独立记录和工程未修改。

## 为何重放及输入

v7→v36 的生成准备和原生写入网关类已经变化，因此旧 B 证据不能单独覆盖这些保护路径；可信导出和任务记录类与 v7 字节相同。比较记录 `output/stage17-v36/workshop-impact.json`。

以原独立评估保存的 87 文件清单核对原工程，再复制至 `D:/Hyper-V/Stage17-Linux/final-acceptance/v36/workshop-b`。保留原 36 部件模型、全部 14 个测试方法及 minimumTests=14，没有替换断言、世界或验收任务记录。

使用 v36 导出 EXE 与随包 SDK，主 JAR SHA-256 `27fac176653d52302bd3f3b179039e3307cc2c288350d5e9801e504104835894`。执行前重新查询签名授权，目标根目录下 `build/test` 仍有效，到期时间为 `2026-09-24T16:03:50Z`；未扩展服务器、恢复或 EULA 权限。

## 实际结果

| 步骤 | 正式任务 | 结果 |
|---|---|---|
| 构建 | `e4853be4-15ed-4794-969e-db12193f64ec` | succeeded |
| 隔离 JAR GameTest | `6103841b-a439-4f27-bba6-afe77c157340` | 14/14 passed，0 failed/skipped/framework，退出 0，sourceCurrentAtCompletion=true |
| 可信导出 | `dad0bcf1-0ba5-43a8-8bde-5fa16a957f92` | succeeded，当前输入通过 |
| 关闭重开 | 同上两个任务 | recentTasks 可发现，get_task 保持 succeeded，restoredFromHistory=true |

四次保护核对（打开前、构建后、测试后、重开后）均通过：4 个锁定/Code 元素关联的 11 个文件及 3 个锁定定义、全部 26 个 Java 源文件、8 个用户保留区、21 个模型/纹理/测试配置文件保持原字节。实际 XML 的 14 个用例名逐一匹配原清单，无 failure/error 节点。

被测和导出 JAR SHA-256 均为 `5e71416a4dc707423fe1dde8cec18e1d11841aae94db0417f9fafd7187e88175`，与旧成功工坊 JAR 相同。新 XML SHA-256 为 `533e78ac050884ab26bc4137411a5f982f98b2a1553b93983e4a38c69b78d2bf`。两份 JAR 中模型均为原 36 部件，模型 SHA-256 `df28318dd6750721807cc7d692ba304be043fda2b4e67e6ddac1e668f8ddf88a`。导出 proof 的相对路径、目录边界及实际 JAR/XML 哈希一致。

完整公开调用、逐文件哈希、脚本、日志和汇总在 `output/stage17-v36/scene-b/`。`workshop-v36-summary.json` 的 `allChecksPassed=true`，评估进程退出 0；本轮无新产品修复或玩法重试。

## 复用边界

`code-example-impact.json` 核对旧 Code 独立试作的归档输入：Fabric/NeoForge 模板与 renderer 与 v36 完全相同，README 仅将“待验收”更新为已完成的双 Loader 正负 GameTest 结果并保留真实鼠标输入限制。因此保留原 Code 行为证据，不重复执行未改动的两套模板。

`scoped-reuse-impact.json` 确认：导出服务与 v4 字节相同；Blockbench 环境探测和导入服务亦相同；v14 的建模服务、任务记录、导入及探测服务均与 v36 相同。这是选择回归范围的依据，不把类身份当作新的运行结果，不抹掉旧报告的缓存、平台和失败边界。

本轮不含客户端画面、真实 Blockbench 操作、用户桌面恢复或新的独立试作。G17 总门禁继续开放，桌面仍被其他应用占用，v36 JCEF 与场景 C 后续待继续。
