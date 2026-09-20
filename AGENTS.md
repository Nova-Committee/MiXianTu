# AGENTS.md —— 用 AI 开发 MiXianTu

本文件是 AI 协作者（以及人类贡献者）进入本仓库的**入口约定**：怎么验证、改哪里、哪些红线不能碰。
更细的规则分别放在这些地方，动手前按需读：

| 想了解 | 去哪 |
| --- | --- |
| 代码与数据层面的约定、术语边界 | [`docs/ai/SKILL.md`](docs/ai/SKILL.md) |
| 文档怎么写、怎么维护 | [`docs/ai/FORMAT.md`](docs/ai/FORMAT.md) |
| 字段级数据格式（唯一权威） | [`docs/数据包格式.md`](docs/数据包格式.md) |
| 每个模块做到哪一步、还缺什么 | [`docs/模块实现审计.md`](docs/模块实现审计.md) |
| 设计意图与历史决策 | `research/`——**新设计必须留档在这里**，规矩见 [`research/README.md`](research/README.md) |
| 玩家文档站 | **另一个仓库**：[`IAFEnvoy/mxt-docs`](https://github.com/IAFEnvoy/mxt-docs)——本仓库不含它，怎么在那边干活看它自己的 `AGENTS.md` |

## 0. 五分钟上手

1. 读 [`README-zh.md`](README-zh.md) 与 `docs/模块实现审计.md` 的对应行——**完成度以代码为准，不以研究文档为准**。
2. 看 `git status`：工作树里通常有别人（或上一轮 AI）未提交的改动，**不要回滚、不要顺手格式化**。
3. 找到要改的模块（见第 3 节的代码地图），先读它现有的类注释——这个项目的类注释写得很满，多数"为什么这么写"就在那里。
4. 改完跑第 2 节的编译。
5. 汇报时说清三件事：改了什么、验证到什么程度、**哪些没跑**。

## 1. 铁律

1. **语言分工。** 聊天回答用中文；Java 注释与 Javadoc 一律英文；本仓库 `docs/`、`research/` 正文中文；文档站仓库是中文根文档 + `/en/` 英文镜像，改动要两边都到。术语、类名、字段名、命令一律保留英文原文。
2. **术语固定。** 载体与定义一律写「符箓」（`talisman`），配套「符纸」「符笔」「符墨」；**「符篆」是误用**，不得出现在文案、注释或文档里。灵气工作台的配方族叫**灵气合成**（`mxt:spirit_shaped` / `mxt:spirit_shapeless`），方块叫**灵气工作台**（`spirit_crafting_table`）。灵根（`spirit_root`）与体质（`physique`）是两套东西；功法（`technique`）与技能（`ability`）与技能水平（`skill_stage`）是三层；`resource` 是**数值**系统、`aura` 是**灵气身份**（详见 `docs/ai/SKILL.md`）。
3. **不要自行 `git commit` / `git push`。** 也不要 `git checkout --`、`git stash`、`git reset` 别人的改动。
4. **默认不启动游戏/测试服务端。** 代码改动的最低验证是编译（第 2 节）；要实机验证（`runTestClient` / `runTestServer`）**先问**，跑完把结果贴出来。
5. **文档同步三处**：本仓库 `docs/`（字段与教程）+ 本仓库 `docs/模块实现审计.md`（完成度）+ **文档站仓库**（中英各一份）。只改一处等于制造 bug。
6. **设计先留档。** `research/` 是**设计稿存储处**：新模块、改版、重构方案（哪怕最后不做）、以及"推翻了以前哪个设计"都要在那里落一份档，动手写代码之前或同时写，编号接着 `NN_` 往下排（当前下一个是 `23_`），审计放 `research/audit/`。规矩见 [`research/README.md`](research/README.md)。**不要在聊天里、提交信息里或代码注释里留下唯一一份设计说明。**
7. **不把研究设计写成"已完成"。** 「制作中 / 完成」只能由代码事实支撑；做不到的部分要明说。
8. **不在文档里写死模组版本号。** 版本以 `gradle.properties` / 你装的那份 Jar 为准。平台与依赖版本（Minecraft / NeoForge / Curios / KubeJS）可以写。
9. **内容不进本体。** 具体世界观数值、五行、丹方、灵根表这类内容属于数据包 / 测试包 / 内容模组；本体只提供框架与规则。
10. **本仓库不写文档站怎么操作。** 构建、校验、开发服务器、部署、站内脚本这些只属于文档站仓库自己（那边有它自己的 `AGENTS.md`）；这里只保留指向它的链接。同理，本仓库的 `docs/` 是**仓库内的开发文档源**（中文，作者向），不是那个站点——两者别混，用途对照见 [`docs/README.md`](docs/README.md)。

## 2. 验证命令

| 用途 | 命令 | 说明 |
| --- | --- | --- |
| 编译（必做） | `./gradlew compileJava compileTestModJava --console=plain`（Windows 用 `gradlew.bat`） | 在仓库根目录。只改文档时不必跑。 |
| 实机（先问） | `./gradlew runTestClient` / `runTestServer` | 加载 `src/test-mod`；日志在 `run-test-client/logs/`、`run-test-server/logs/`。 |

> CI（`.github/workflows/build.yml`）只跑 `./gradlew build`，而那条命令**不包含** `compileTestModJava`（仓库里也没有 JUnit 测试源）。所以改了 `src/test-mod` 一定要在本地显式编译，别指望 CI 替你发现错误。

改完 lang 文件后核对两份键集合一致（需要 Node，Windows / macOS / Linux 通用）：

```bash
node -e "const a=require('./src/main/resources/assets/mxt/lang/zh_cn.json'),b=require('./src/main/resources/assets/mxt/lang/en_us.json');const k=o=>Object.keys(o).sort();const m=k(a).filter(x=>!(x in b)),e=k(b).filter(x=>!(x in a));console.log(m.length||e.length?'DIFF zh-only='+m+' en-only='+e:'keys identical ('+k(a).length+')')"
```

## 3. 代码地图

| 要改什么 | 去哪 |
| --- | --- |
| 数据包定义（字段 / Codec / 加载期校验） | `src/main/java/com/iafenvoy/mxt/data/<模块>/` |
| 动态注册表声明 | `registry/MxtDatapackRegistries.java` + `registry/MxtResourceKeys.java`（34 张表，原版 datapack registry） |
| 固有分派类型（条件 / 行为 / 触发器 …） | `data/condition/builtin/`、`data/action/builtin/`、`registry/Mxt*Conditions.java`、`registry/Mxt*Actions.java` |
| 原版配方类型 | `registry/MxtRecipeTypes.java`（`mxt:alchemy`、`mxt:spirit_shaped`、`mxt:spirit_shapeless`、`mxt:formation`、`mxt:refining`） |
| 运行时服务（结算、事务、调度） | `runtime/<模块>/` |
| 附件（存档 / 同步状态） | `attachment/` + `registry/MxtAttachments.java` |
| 伤害结算 | `runtime/damage/DamageCalculationService.java`（唯一出口）、`DamageElements.java`、`DamageEventBridge.java` |
| 元素 / 灵根 / 体质 / 反应 | `runtime/cultivation/`、`runtime/element/` |
| 命令 | `command/`（一个节点一个类，`ROOT` 常量）+ `command/CommandManager.java` + `config/MxtServerConfig.Commands` + 两份 lang |
| KubeJS 桥接 | `compat/kubejs/MxtKubeJsApi.java`（受校验的操作）+ `compat/kubejs/binding/`（一个全局对象一个类）+ `MxtKubeJsPlugin.registerBindings` |
| 界面 / HUD / 信息面板 | `screen/` |
| 测试探针与夹具 | `src/test-mod/java/com/iafenvoy/mxt/testmod/`、`src/test-mod/resources/data/mxt_test/` |
| 设计稿 / 审计 / 测试设定 | `research/`——**新设计必须留档在这里**，命名与分工见 [`research/README.md`](research/README.md) |
| 模组文档（仓库内，作者向） | `docs/`（Docusaurus 风格：front matter + `_category_.json`）——**不是**玩家文档站，两者用途对照见 [`docs/README.md`](docs/README.md) |
| 玩家文档站 | **另一个仓库**：[`IAFEnvoy/mxt-docs`](https://github.com/IAFEnvoy/mxt-docs) |

## 4. 写代码的房规（都是踩过的坑）

- **Definition 的两个 Codec**：`CODEC` 是 Holder Codec，`DIRECT_CODEC` 是直接对象 Codec，注册表注册后者。
- **`RecordCodecBuilder` 不认识未知字段**，会静默丢掉：一份写着 `element` 的体质会被当成"没有元素"照常跑。要"这个定义不许带那些字段"就用 `util/codec/DefinitionCodecs.refuseKeys(...)`，让它加载期报错并指名字段。空表 / 空列表同理，用 `.validate(...)` 拒绝（否则会静默变成恒真或恒假）。
- **链式 `.validate(...)` 会打断类型推断**：尾部接了 `.validate` 之后要写显式见证 `RecordCodecBuilder.<X>create(...)` / `RecordCodecBuilder.<X>mapCodec(...)`（参见 `Element`、`Physique`）。
- **集合 Codec 是容错的**：`CollectionCodecs` / `AutoIgnoreMapCodec` / `AutoIgnoreListCodec` 会把坏条目打一条日志后**丢弃**。所以"定义写错"往往表现为"这一项不存在"，排查时先看日志里的 `Ignoring invalid list element`。
- **只读查询用 `getExistingData(...)`**，不要为了问一句"它有没有灵根"就创建一份空附件（会跟着进存档）；只有真正要写的地方才 `getData(...)`。
- **缓存按注册表实例开键**（见 `DamageElements`、`ElementReactionService`、`FormulaNames`）：`/reload` 不会重建 datapack registry，世界加载才会换实例，缓存键天然正确；上限参考 `MAX_CACHED_REGISTRIES` + `LOCK` 双检。
- **重入守卫用 `ThreadLocal<Set<...>>` + try/finally**（见 `CurseService.IN_TRANSACTION`、`ElementReactionService.IN_CHAIN`、`TriggerDispatcher.DISPATCHING`）：链式触发很容易写成无限递归。
- **失败用结果记录，不用异常**：`Result(changed, failure)` + `Failure` 枚举（`CultivationIdentityService`、`CultivationToggleService`、`AbilityService.UseResult` …）。客户端调用一律返回 `false` / `0` / `failure = SERVER_ONLY`，不写日志。
- **显示名走 `DefinitionText.name(holder, category)`**；自由文本（`rarity`、功法 `grade`）先查 `mxt.rarity.<值>` / `mxt.technique_grade.<值>`，有翻译用翻译、否则显示原文。
- **服务端权威**：扣费、校验、修炼、突破、实体行为只在服务端；客户端只渲染与发请求。
- **数据包对象视为不可变**，别做多余的 `copyOf` / Mutable 转换；颜色用 `MiscCodecs.COLOR`；数值加载期校验有限性，运行期遇到 NaN/Infinity 记一次警告并按 0（或 1，视语义）处理。
- **元素相关规则只有一份实现**：`mxt:disabled` 的语义、灵根"持有 vs 生效"、这一击是什么元素、伤害管线的每个因子（`damage_multiplier`、`element_modifier`、攻击方 `overcomes`、受击方 `adapted_to`、体质的 `damage_dealt_multiplier` / `damage_taken_multiplier`）都已经有公共入口（`Elements`、`DamageElements`、`DamageCalculationService`），新代码接进去，不要在别处再算一套。
- **KubeJS 只能扩展固有类型**，不能注入数据包定义；脚本侧读得到实体的附件，写操作全部走 `MxtKubeJsApi` 里那些受校验的方法。

## 5. 测试与探针

- 本仓库**没有 JUnit**。验证靠：编译 →（获准时）实机跑 `/mxt_test`。
- 探针在 `src/test-mod`，子命令：`kit` / `cultivate` / `verify` / `damage` / `element` / `identity` / `realm [keep|reopen]` / `rift` / `info` / `guide`。风格是**一次性探针实体 + 精确数字断言**（`close(actual, expected)`），一条腿一个 `OK / MISMATCH`，最后汇总。
- **夹具里那些数字是断言的一部分**：例如测试包的火/水克制与适应倍率决定了 `10 × 1.5 × 0.5 = 7.5`。给测试包加内容时，先确认不会改变既有腿的算式（新内容用新文件承载，或让默认倍率为 1）。
- 探针**只编译不等于跑过**：报告里必须写明"未实跑"，并给出跑一次该看什么输出。

## 6. 文档同步清单

| 改了什么 | 必须同步 |
| --- | --- |
| 新增 / 改名 / 删除数据包字段 | 本仓库 `docs/数据包格式.md` → `docs/模块实现审计.md` 对应行 → 文档站仓库的对应页（中英各一份）→ 测试包示例 → README(`.md` / `-zh.md`) 的完成度表 |
| 公开 API / KubeJS 全局对象 | 本仓库 `docs/guide/kubejs/api.md` → 文档站仓库的 KubeJS 页（中英） |
| 新命令 / 新配置项 | 本仓库 `docs/guide/play/commands.md` → 文档站仓库的命令页与功能页（中英）+ 两份 lang（含 `config.mxt.server.*.tooltip`） |
| 模块完成度变化 | `docs/模块实现审计.md` + README 两张表 + 文档站仓库的功能表（中英） |
| 推翻 / 关闭了研究里的设计 | `research/audit/*.md` 标注"已于 <日期> 关闭 / 修正"，并写清新行为 |
| 改了数据包语义（比如某倍率改由管线消费） | 文档站仓库的技术说明、公式变量页与相关教程（中英），**教程里的旧写法必须改掉**，否则包会重复相乘 |

文档站仓库怎么构建、怎么校验、正文用什么骨架，看它自己的 `AGENTS.md`；本仓库只负责把该改的内容改到。

## 7. 需要先问的设计选择

这些不是"照文档做"的事，动手前先确认，别猜：

- **倍率语义**：某个数该由数据包自己乘进公式，还是由管线统一乘（历史上 `element_modifier` 从"公式变量"改成"管线因子"就属于这一类，会静默改变已有包的伤害）。
- **注册 vs 删除**：发现一个零引用的类，是让它成为该类型的实现，还是认定它是被新写法取代的遗留物而删掉。
- **玩家入口**：哪些状态只给脚本与管理员命令（当前：灵根/体质的开关只有 KubeJS 与 `/mxt identity`），不加按键与界面。
- **删字段 / 改字段名**：未发布阶段允许不兼容，但仍要问，并同步全部文档与测试包。

## 8. 汇报格式

用中文，按这个顺序：

1. **做了什么** —— 按模块分点，带上文件路径。
2. **验证结果** —— 编译（以及你实际跑过的检查）的具体输出，数字比形容词有用。
3. **没跑的** —— 例如"探针只编译未实跑"，以及跑起来后该看哪一行输出。
4. **遗留与开放项** —— 有意不做的、发现的其它不一致、需要拍板的设计选择。
