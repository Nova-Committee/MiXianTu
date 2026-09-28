# 60 通用进度（`progression`）设计

> 用户要求：把 `mxt:skill_stage` 改名为 `mxt:progression`，并且**不再只服务功法**——将来灵宠等也要共用同一套等级链，所以这一轮要做成通用的。

## 1. 结论（拍板）

| 问题 | 决定 |
| --- | --- |
| 做到哪一步 | 改名 + 抽出通用层，**本轮只把功法接上**；灵宠等留接口 |
| 进度状态存哪 | **新建通用附件** `mxt:progression`：`{所有者 id → 已达等级}`，功法那段从 `spirit_identity` 迁出 |
| 等级带哪些字段 | 保留 `mastery`（到达该级所需熟练度）与 `damage_multiplier`；"这一级给什么能力、要什么条件"仍写在**所有者定义**里（功法的 `configuration`） |
| 字段术语 | 一并换成 level：`next_stage` → `next_level`，条件里的 `stage` → `level`，功法的 `default_stage` → `default_level` |

## 2. 形状

**定义**：注册表 `mxt:progression`，目录 `data/<namespace>/mxt/progression/<path>.json`，字段
`name` / `description` / `next_level` / `mastery` / `damage_multiplier`。

链**没有身份字段**（沿用 `research/02` 2026-09-28 的拍板）：链就是 `next_level` 串出来的直线，入口＝没有任何一级指向它的那一级；成环、被两处写成后继（分叉）、指向不存在的条目都会让这条链不进索引。比较先后只在同一条链内成立（缓存记"每一级的链首"+ 序号）。

**所有者**：`data/progression/ProgressionOwner`——一个定义只要实现它就能拥有进度：

- `entryLevel()`：持有者没晋升过时算作所在的那一级；
- `levels()`：这一级（以及更低每一级）给它什么能力、到达条件是什么。

功法实现它（`default_level` + `configuration`）。灵宠侧将来实现同一个接口即可，**通用层不需要认识任何一张具体注册表**。

**状态**：附件 `mxt:progression`，`Map<Identifier, Holder<Progression>>`，键＝所有者定义的 id（功法 id / 灵宠定义 id…）。**只在晋升之后才写入**，所以"包改了入口等级，没晋升过的人跟着走"这条口径保持不变。附件随实体同步、随死亡复制（与其它身份附件一致）。

**读取与驱动**：

- `runtime/progression/ProgressionService`：通用核心——`currentLevel`（存的那一级，或所有者的 `entryLevel`）、`nextLevel`（走一跳）、`advanceCondition`、`canAdvance`、`grantedAbilities`（累积：当前所在及其以下每一级授予的能力）。
- `runtime/progression/ProgressionSources`：回答"这个身体现在持有哪些进度所有者"——每个系统一个条目（今天只有功法：已学功法表）。**这就是接一个新系统的全部改动点之一**。
- 每个系统自己那条驱动保留在各模块里：功法是 `TechniqueMasteryService`（`mastery_resource` 达到要求 + 该级 `condition` 成立 → 写入并发布 `mxt:progression_level` 信号，携带 `level` = 链内序号与 `owner` = 所有者定义的 id）。**通用晋升驱动（一个 tick 推所有系统）本轮不做**，等灵宠落地时再抽——那时才知道"晋升之后要做什么"是否真的一样。
- **归属校验刻意低频**：存下的等级若已经不在该所有者自己的链上（包改了入口等级留下的，即审计里的 T11），在**登录**与**数据包重载**时清掉并按新等级重算授予（`ProgressionService.pruneForeignLevels` + `runtime/progression/ProgressionEventBridge`），**不在每次读取时校验**。重载那条排在 `ServerCache` 重建之后（`EventPriority.LOWEST`），因为重算要读那份索引；重载时只扫在线玩家——非玩家所有者（灵宠）落地时这个扫描点要跟着扩。

**条件**：`mxt:progression`，字段 `level`（点名一级）、`comparison`（`exact` 默认 / `at_least` / `at_most`）、`owner`（可选，单个 id 或 id 数组，把问题限定到某几个所有者；省略＝问这个身体持有的每一种进度，任一命中即成立）。读的是"身体到达过的那一级"。旧条件的 `technique` 字段随所有者概念泛化成 `owner`；`#标签` 本来就没实现，一并从文档里去掉。

## 3. 为什么不是"链上再写一个所有者字段"

2026-09-28 已经把链身份字段（`skill`）删掉了，拍板口径是"链就是链接本身"。这一轮的所有者概念与那个字段**不是同一层**：链是"这一级的下一级是谁"，所有者是"谁在爬这条链、每一级对它意味着什么"。所有者挂在**引用链的那个定义**上（功法的 `default_level` + `configuration`），而不是挂在链的每一级上——同一条链仍然可以被任意多个所有者共用。

## 4. 迁移与不兼容

- 旧附件字段 `spirit_identity.technique_stages` **删除、不迁移**（项目未发布，未发布阶段允许不兼容）。旧存档里的那一段会在加载时被容错列表丢掉，即已晋升的等级归零；要保留就得手写迁移，本轮不做。
- 旧包里的 `skill_stage` 目录、`default_stage`、条件 `mxt:skill_stage` 都不再读取：目录不再被扫（定义整条消失），字段改了名就等于没写（`RecordCodecBuilder` 静默忽略未知键），条件类型 id 不存在时按未知条件处理。全部要改成新名。
- KubeJS 侧 `MxtTechniques.stage(...)` 改名 `level(...)`（读的是"记录的等级"，没有记录就是 `null`，语义不变）。

## 5. 影响面

- 数据层：`data/progression/{Progression, ProgressionConfig, ProgressionOwner}`（`data/cultivation/SkillStage` 删除）。
- 注册：`MxtResourceKeys.PROGRESSION`、`MxtDatapackRegistries`、`MxtEntityConditions`、`MxtAttachments.PROGRESSION`。
- 运行时：`runtime/progression/{ProgressionService, ProgressionSources, ProgressionEventBridge}`（`runtime/cultivation/SkillStageService` 删除）、`ServerCache`（索引与校验改名）、`TechniqueMasteryService`、`CultivationGrantService`、`TechniqueProgress`、`TechniqueService`、`TechniqueCommand`、`LifeSpanService`。
- 信号：`mxt:technique_stage` → `mxt:progression_level`（`TriggerSignals.PROGRESSION_LEVEL` + `MxtTriggers`），上下文变量 `stage` / `technique` → `level` / `owner`。
- 数据包与脚本：`Technique`（`default_level` / `configuration`）、条件 `mxt:progression`、KubeJS 绑定。
- 界面：功法面板取名字的类别由 `skill_stage` 换成 `progression`（生成键随之变成 `progression.mxt.<ns>.<path>`）。
- 文档：本仓库 `docs/数据包格式.md`、`docs/ai/SKILL.md`、`AGENTS.md` 术语行；文档站中英对应页（页面文件一并改名）。

## 6. 未做

- 通用晋升驱动（见 §2 最后一段）。
- 灵宠侧的 `ProgressionOwner` 实现与 `ProgressionSources` 条目，以及把归属校验的扫描点扩到非玩家实体。
- 旧附件字段的迁移。
- `damage_multiplier` 仍然只被"技能伤害"这一条路消费；灵宠将来要用它（或换别的效果字段）时再定。
