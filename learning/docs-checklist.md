# 文档改动清单

| 改了什么 | 必须一并改 |
| --- | --- |
| 新增 / 改名 / 删除数据包字段 | `docs/docs/datapack/json/<定义>.md`（中英各一份）→ 测试包示例（README 与站点首页的**介绍列**只在模块范围变化时才改） |
| 公开 API / KubeJS 全局对象 | `docs/docs/kubejs/`（`api-reference.md` 与 `api/<对象>.md`，中英） |
| 新命令 | `docs/docs/player-guide/commands.md` 与 `commands/<组>.md`（中英）+「命令别名」标签页（`MxtServerConfig.Commands`）+ 两份 lang |
| 新配置项 | `docs/docs/player-guide/config.md`（**服务端配置独立成页，别再塞进命令页**，中英）+ 两份 lang（含 `config.mxt.server.*` 与它的 `.tooltip`；文档站那边按它的铁律只写"配置页 → 条目"，不写键名与文件路径） |
| 新增 / 改动面向玩家的文案（界面 / 提示 / tooltip / 配置） | 两份 lang，再跑一次 `runTestClient` 看 `run-test-client/config/wamt/exports/` 有没有漏掉的键（见 AGENTS.md 的「构建 & 运行命令」） |
| 新增 / 改动内置物品 | 两份 lang（`item.mxt.*` 与它自己的 tooltip 键）与资源（`items/` 的定义、`models/item/` 的模型、`textures/item/` 的贴图）；**彩蛋 / 玩笑物品不进任何说明文档**——README 与 `docs/` 都不写，只留 lang。彩蛋物品也**只有一种写法**：`item/EasterEggItem`，构造函数第二个参数是 tooltip 行数，行文案由 `item/EasterEggTooltipAppender` 按 `tooltip.<命名空间>.<路径>.<i>`（`i` 从 0 起）自动生成——**别再为单个彩蛋写类或写 appender**，新增一个彩蛋＝一次注册 + 它的 lang 键 |
| 模块完成度变化 | README 两张表与站点两页（`docs/docs/`、`docs/docs/en/`）的**状态列**（**四处**保持一致，完成度以代码为准） |
| 推翻 / 关闭了研究里的设计 | `research/audit/*.md` 标注"已关闭 / 修正"（**不写日期**，见 AGENTS.md 的「任何文档都不写日期」），并写清新行为 |
| 改了数据包语义（比如某倍率改由管线消费） | 文档站的技术说明、公式变量页与相关教程（`docs/docs/technical/`、`docs/docs/tutorial/`，中英），**教程里的旧写法必须改掉**，否则包会重复相乘 |

**README 的「模块完成情况 / Module Status」表两列各管一件事，别互相串**：**状态列**只放 ✅ / 🚧 / 🔲（图例写在表上方），表示这个模块做到哪一步（**完成度以代码为准**，README 中英两张表与文档站中英两页保持一致）；**介绍列只讲这个模块提供什么**——不写完成情况：不出现「已完成 / 制作中 / 计划中」、✅ / 🚧 / 🔲，也不写"XX 已就绪""XX 还没做""只有数据结构或资源"这类进度句，并且写成**能做什么**的能力口径，而不是"现在跑到哪"。介绍列每行一句，有长度上限：**中文不超过 100 个汉字，英文不超过 250 个字母**，两边都把空格与标点算进去（按去掉表格对齐空格后的正文量）；加模块或改模块范围时顺手量一下：超了就删细节，细节属于 `docs/` 的对应页。

文档站怎么构建、怎么校验、正文用什么骨架，看 [`docs/AGENTS.md`](docs/AGENTS.md)；本仓库只负责把该改的内容改到。

### 写 `docs/` 的正文时，别把实现细节搬进去

站点写给写数据包、写 KubeJS、玩的人；字段级形状以**代码**为准（各定义的 Codec 与加载期校验），站点只重述它。所以改站点正文时：

- **内部标识不出去。** Java 类名（`MxtEntityActions`、`ArtifactDescription`、`DataStorageCollector`…）、内部接口与方法（`AbilityType.isActive`、`createComponents`、`refreshDimensions`…）、只描述实现的类型名（`Holder<aura>`、`MapCodec`、`RegistryFixedCodec`…）都不写进文档站。那边的读者写的是 JSON 与脚本。例外只有三类：**读者要照抄的键名与路径**、**公有 KubeJS 全局对象名**、**原版与平台的公开 ID**。
- **改动痕迹不出去。** 「此前是」「曾经」「已删除」「旧包」「老存档」「已标记为将来可能移除」，以及任何带日期的「……起」「……更新」这类句子，都属于这里的审计与研究，不属于玩家文档；那边只写**当前是什么**。要留的规则就直接写规则。
- **一个类型族一页。** 站点不把整张分派表塞进一页（完整规格以代码为准，`docs/docs/datapack/json/` 是一页一张表）。
- **字段不在站点发明。** 站点只能重述代码里已经写下的字段；发现缺了字段、或与代码不符，先回去看代码（各定义的 Codec 与加载期校验），再改站点页。
