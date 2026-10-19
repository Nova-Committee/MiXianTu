# AGENTS.md —— 用 AI 开发 MiXianTu

本文件放**每次动手都必须知道的**：怎么验证、代码放哪、哪些红线不能碰。
「为什么这么写」、术语口径、逐个模块的硬约束与踩过的坑在 [`learning/`](learning/README.md)——需要细节时按需去读，别在聊天里复述。

## 项目概述

NeoForge 修真框架模组，本体只出框架与规则、世界观内容在数据包：Java 25 / Minecraft 26.1.2 / NeoForge 26.1.2.99，模组版本以 `gradle.properties` 为准。
核心架构一条链：**数据包定义**（`data/<模块>/`，Codec + 加载期校验，走原版 datapack registry）→ **运行时服务**（`runtime/<模块>/`，结算、事务、调度）→ **附件**（`attachment/`，存档与同步状态）。模块之间用 NeoForge 事件通信（根 `event/` 给外部模组听，`event/dispatcher/` 只给本模组内部用），每实体 tick 顺序用表（`runtime/EntityTickStages`）。界面与 HUD 在 `screen/`。

## 构建 & 运行命令

包管理器是 Gradle wrapper（没有 pnpm/npm；只有一条 Node 脚本用于 lang 键比对）。命令都在仓库根目录，Windows 一律用 `gradlew.bat`。

| 用途 | 命令 |
| --- | --- |
| 编译（改完必做） | `gradlew.bat compileJava compileTestModJava --console=plain` |
| 只刷测试数据包 | `gradlew.bat processTestModResources --console=plain`（改了 `src/test-mod/resources` 时） |
| 实机客户端（先问） | `gradlew.bat runTestClient` —— 日志 `run-test-client/logs/` |
| 实机专用服（先问） | `gradlew.bat runTestServer`（`--nogui`，端口 25566）—— 日志 `run-test-server/logs/` |
| 游戏测试（先问） | `gradlew.bat runGameTestServer --console=plain` —— 退出码 ＝ 失败的 required 测试数 |
| 打包 | `gradlew.bat build` |
| 清理 | `gradlew.bat clean` |

改完 lang 文件后核对中英两份键集合（需要 Node）：

```bash
node -e "const a=require('./src/main/resources/assets/mxt/lang/zh_cn.json'),b=require('./src/main/resources/assets/mxt/lang/en_us.json');const k=o=>Object.keys(o).sort();const m=k(a).filter(x=>!(x in b)),e=k(b).filter(x=>!(x in a));console.log(m.length||e.length?'DIFF zh-only='+m+' en-only='+e:'keys identical ('+k(a).length+')')"
```

这条只保证键集合一致，不保证该有翻译的都有翻译；漏翻用 Where Are My Translations 查（细节见 [`learning/verification.md`](learning/verification.md)）。

**编译命令不拷测试包资源**：`compileTestModJava` 只编 Java，实机加载的是 `build/resources/testMod`，改了 `src/test-mod/resources` 必须跑 `processTestModResources`——否则旧文件会让服务端以"定义写了已废弃字段"之类的方式启动失败，而源码看起来是对的。

## 测试

- **框架**：NeoForge GameTest，**没有 JUnit**（仓库里没有 JUnit 测试源）。
- **位置**：探针在 `src/test-mod/java/com/iafenvoy/mxt/probe/`（按域分文件夹，框架 `Probe` / `ProbeContext` / `ProbeTests`）；测试包自己的注册在 `.../testmod/`（**不写断言**）；夹具与两个空竞技场在 `src/test-mod/resources/data/mxt_test/`。
- **全量**：`gradlew.bat runGameTestServer --console=plain`（退出码 0 ＝ 全绿）。它会在无头服务端里**真的建玩家、开菜单、开维度**，不是纯逻辑单测。
- **只跑一部分**：加 `-PmxtGameTestSelection=mxt_test:*alchemy*`。
- **新增代码要不要写测试**：**行为变化必须有一条探针**（一个测试只验一块，一次性探针实体 + 精确数字断言 `close(actual, expected)`，一条腿一个 `OK / MISMATCH`）；纯重构、改名、注释、文档不用。
- **CI 覆盖不到**：`.github/workflows/build.yml` 只跑 `gradlew build`，**不含** `compileTestModJava`，也不跑 GameTest——改了 `src/test-mod` 必须本地显式编译，行为回归只能在本地跑。
- 探针**只编译不等于跑过**：汇报里要写"未实跑"，并说明跑起来该看哪一行输出。

## 项目目录结构

```text
src/main/java/com/iafenvoy/mxt/
  api/            对外契约：只放接口 + package-info，实现留在各自模块
  data/<模块>/     数据包定义（Codec + 加载期校验）与固有分派类型（builtin/）
  registry/        原版注册、动态注册表声明（MxtDatapackRegistries / MxtResourceKeys）、缓存（cache/）
  runtime/<模块>/  运行时服务：结算、事务、调度
  attachment/      附件（存档 / 同步状态），登记在 registry/MxtAttachments
  event/           对外 NeoForge 事件；event/dispatcher/ 只给本模组内部用的钩子
  command/         一个节点一个类（server/ / client/）+ 两个 Manager + RegistryReferences
  screen/          界面 / HUD / 信息面板
  mixin/           mixin 与 accessor，登记在 src/main/resources/mxt.mixins.json
  compat/<模组>/   可选依赖（两半隔离）
  util/            通用工具（chain/ formula/ matcher/ codec/ …）
src/main/resources/  assets/<命名空间>/{lang,textures,models}、data/mxt/（本体自带的阵形 JSON）、mxt.mixins.json
src/test-mod/        测试包：probe（断言）+ testmod（注册）+ resources/data/mxt_test（夹具）
docs/                文档站（junction → mxt-docs 检出，独立仓库）
research/            设计稿与审计（大改才写）
```

新代码放哪：定义与字段 → `data/<模块>/`；结算 / 事务 / 调度 → `runtime/<模块>/`；别的模组实现或调用的契约 → `api/`（只接口）；存档或同步状态 → `attachment/`（**一个模块一份**）；命令节点 → `command/server|client/` 并在 `ServerCommandManager` / `ClientCommandManager` 登记；界面 → `screen/`；原版注入 → `mixin/` + `mxt.mixins.json`；可选依赖 → `compat/<模组>/`（两半隔离）；探针 → `src/test-mod/.../probe/<域>/` 并在 `ProbeTests` 登记。

**完整代码地图**（哪张表在哪、附件怎么拆、Jade 的两半、原版配方类型、tick 流水线…）见 [`learning/code-map.md`](learning/code-map.md)。

## 代码规范（只写本仓特殊约定）

默认 Java 规范不在这里。以下几条是本仓特有的，其余细则见 [`learning/house-rules.md`](learning/house-rules.md)：

- **语言分工**：聊天回答用中文；Java 注释与 Javadoc 一律英文；`docs/` 中文根 + `docs/docs/en/` 镜像、改一页要两边都到；`research/` 正文中文。术语、类名、字段名、命令保留英文原文。
- **注释三律**：名字先自解释（能读出来的不写）；只有 `api/` 与 KubeJS 桥接这类对外契约写完整 Javadoc，内部（含 `public` 成员）只写"有明确限制需要标明"的；单处注释两行内、同一条解释不重复。设计动机写文档站与 `research/`，不写进代码注释。
- **术语固定**：载体与定义写「符箓」（`talisman`），配套「符纸」「符笔」，颜料存量叫「颜料」；**「符篆」「符墨」禁用**。灵根 ≠ 体质、功法 ≠ 技能 ≠ 进度等级、`resource` 是数值而 `aura` 是灵气身份。完整术语表见 [`learning/terminology.md`](learning/terminology.md)。
- **两个 Codec**：`CODEC` 是 Holder Codec、`DIRECT_CODEC` 是直接对象 Codec（注册表注册后者），且 **`DIRECT_CODEC` 每次解码都必须造新对象**（用 `MapCodec.unit(X::new)`，不能捕获共享实例，否则第二份文件撞 `Adding duplicate value`)。
- **定义文本一律按 id 生成**：定义里没有 `name` / `description` 字段；名字键是 `<注册表命名空间>.<注册表路径>.<条目命名空间>.<条目路径>`，出口只有 `DefinitionText`（`name` / `description` / `resolved` / `nameOrId`）。没翻译时：可以空着的地方用 `resolved` 整行不画，必须显示点什么的地方用 `nameOrId` 回退 id——**不许露键名**。
- **一套形状，绝不造第二套**：链的走法（`util/chain/`）、消耗（`Cost[]`，两遍执行）、能力（`mxt:ability` 一个定义）、命令的注册表参数、阵法的四个节拍、品质阶梯、灵气分配——各自只有一份实现与一个出口，新代码接进去。
- **失败用结果记录**（`Result` + `Failure` 枚举，客户端回 `false` / `SERVER_ONLY`，不写日志），不用异常做常规控制流；重入守卫用 `ThreadLocal<Set<…>>` + try/finally。
- **不写死模组版本号**：版本以 `gradle.properties` / 安装的 Jar 为准；平台与依赖版本（Minecraft / NeoForge / Curios / KubeJS）可以写。
- **任何文档都不写日期**：本文件、`learning/`、`docs/`、两份 README、`research/` 的正文里都不写日期（`YYYY-MM-DD`、「X 月 Y 日」这类都不行），除非用户明确要求；要表达时间先后就用「之前 / 现在」或设计稿编号。
- **没有 lint / format 命令**：格式与静态检查就是编译。

## 边界规则

### ✅ Always（可以直接做）

- 动手前读 [`README-zh.md`](README-zh.md) 的模块完成情况表、目标模块的类注释、`research/` 里对应的设计稿——**完成度以代码为准，不以研究文档为准**。
- 改完做最低验证：`gradlew.bat compileJava compileTestModJava --console=plain`。
- 行为变化补一条 GameTest 探针；改了 `src/test-mod/resources` 就跑 `processTestModResources`。
- 改了面向玩家的文案（界面、命令、提示、tooltip、配置项）就补两份 lang 并跑一次键比对。
- 改了数据包字段就同步 `docs/docs/datapack/json/<定义>.md`（中英各一份）与测试包示例。
- 大改（新模块、换形状的改版、跨模块重构）在 `research/` 写设计稿，编号接 `NN_` 往下排；审计放 `research/audit/`。
- 汇报按「做了什么 / 验证结果 / 没跑的 / 遗留与开放项」四段写，数字优先。

### ⚠️ Ask first（先问再动）

- **实机验证**：`runTestClient` / `runTestServer` / `runGameTestServer`（跑完把结果贴出来）。
- **新增依赖**（`build.gradle` 的 `implementation` / `jarJar` / `compileOnly`）或改仓库本身的配置。
- **CI / 工作流**：`.github/workflows/`。
- **删字段 / 改字段名**（未发布阶段允许不兼容，但仍要问，并同步全部文档与测试包）。
- **语义选择**：某个倍率该由数据包自己乘进公式还是由管线统一乘；一个零引用的类是"该成为实现"还是"被新写法取代的遗留可删"；哪些状态只给脚本与管理员命令（不加按键与界面）。
- **移动或删除整个模块 / 包**（跨模块重构）。

### 🚫 Never（绝对不做）

- `git commit` / `git push` / `git checkout --` / `git stash` / `git reset`；也不回滚、不顺手格式化别人（或上一轮 AI）未提交的改动。
- 在 `docs/` 以外另建一份文档站、或把站点内容抄到 `docs/` 之外；**不在本仓库 `git add docs/`**（它是独立仓库，`.gitignore` 已忽略）。
- 用「符篆」「符墨」，或用第二套实现替代上面那些"只有一份"的形状。
- 把世界观内容（数值、五行、丹方、灵根表）写进本体；把彩蛋 / 玩笑物品写进任何说明文档（两份 README 与 `docs/` 都不提，名称与 tooltip 只留两份 lang）。
- 在文档里写死模组版本号；在本仓库写"文档站怎么操作"（构建、校验、部署、站内脚本都归 `docs/AGENTS.md`）；在文档站写内部类名 / 改动痕迹 / 代码里没有的字段。
- 把研究稿写成"已完成"——「制作中 / 完成」只能由代码事实支撑，做不到的要明说。
- 让设计说明只存在于聊天、提交信息或代码注释里。

## 文档索引

| 想了解 | 去哪 |
| --- | --- |
| 术语、口径、逐个模块的硬约束、踩过的坑 | [`learning/`](learning/README.md) |
| 数据包字段与形状 | 代码（各定义的 Codec 与加载期校验）+ `docs/docs/datapack/`（一页一张表，中英各一份） |
| 模块完成情况 | [`README-zh.md`](README-zh.md) / [`README.md`](README.md) 的表（四处一致，以代码为准） |
| 设计意图与历史决策 | `research/`——规矩见 [`research/README.md`](research/README.md) |
| 文档站怎么写、怎么校验 | [`docs/AGENTS.md`](docs/AGENTS.md)；机制见 `docs/README.md` |
| 一次改动的文档同步清单 | [`learning/docs-checklist.md`](learning/docs-checklist.md) |
