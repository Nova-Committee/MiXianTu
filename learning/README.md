# LEARNING —— MiXianTu 的引用解释

这是 [`AGENTS.md`](../AGENTS.md) 的详版：AGENTS.md 只留每次动手都必须知道的，这里按主题存"为什么这么写"、术语口径、逐个模块的硬约束，以及踩过的坑。中文正文，术语、类名、字段名与命令保留英文原文。

| 想了解 | 去看 |
| --- | --- |
| 术语与口径：符箓 / 符纸 / 符笔 / 颜料，灵根 vs 体质，`resource` vs `aura` … | [terminology.md](terminology.md) |
| 代码地图：每张注册表在哪、附件怎么拆、Jade 两半、原版配方类型、tick 流水线 | [code-map.md](code-map.md) |
| 写代码的房规：52 条硬约束与踩过的坑（Codec、缓存键、UUID 记账、阵法、消耗、能力…） | [house-rules.md](house-rules.md) |
| 测试与探针：哪条腿能无头跑、夹具里的数字也是断言、探针风格 | [testing.md](testing.md) |
| 文档改动清单：改了 X 要同步哪些页，以及站点正文的禁令 | [docs-checklist.md](docs-checklist.md) |
| 验证细节：lang 键比对与 WAMT 漏翻检查 | [verification.md](verification.md) |

定义字段见[数据格式参考](https://docs.iafenvoy.com/zh/docs/mod/mxt/datapack/json)，设计意图见本仓库的 [research](../research/README.md)，模块完成情况见两份 README 的状态表，并以代码为准。

本文档中的完整源码路径以 MiXianTu 仓库根目录为基准；省略前缀的 Java 路径以 `src/main/java/com/iafenvoy/mxt/` 为基准。仓库内的 Markdown 链接相对当前文件解析。文档站及工作站附属为独立项目，可检出至任意目录，不要求与本仓库相邻，也不要求存在本地 `docs/` 映射；跨项目引用通过项目名称、公开链接与目标仓库内路径定位。
