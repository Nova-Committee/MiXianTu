---
title: Java 公开 API
---

| API | 作用 |
| --- | --- |
| `MxtDatapackRegistries` | 查询原版动态注册表、Holder、禁用标签和客户端同步数据。 |
| `AuraService` | 查询坐标最终灵气、环境来源、按资源分离的灵气池和区域覆写；资源可附带元素标记。 |
| `ResourceService` | 初始化、读取、修改资源并进行最大值和恢复计算。 |
| `CultivationService` | 修炼、资源转换、境界突破和境界设置。 |
| `AbilityService` | 服务端执行技能、Cost、冷却、取消和行为。 |
| `FriendService` / `FriendEvent` | 判断一个实体是否把另一个实体当作"自己人"。`FriendEvent.Relation` 用原版 `TriState` 让其他模组给出自己的判断；判断者以 **UUID + 可选实体**给出，因此数据保存在服务端管理器里的来源（队伍、阵营等）在玩家离线时也能作答。`DEFAULT` 表示交给**内置好友系统**兜底，而它自己不再发事件；内置系统在判断者离线时读 `FriendCache` 这份会话两端刷新的内存镜像。 |
| `FormationActionType` / `MxtFormationActionTypes` | 阵法的**功能模块**：`formation.actions` 用 `type` 选中一种模块，每种的独有字段放在自己的记录里。新增一种模块 = 一条记录 + 一次 `DeferredRegister` 注册；运行时按记录类型分派，`data` 包因此不碰世界。 |
| `FormationProtection` | 查询一个动作是否被阵法的守御模块拦下：行为者与被作用的一方**任一**在半径内即生效；`delegate_to_claims` 置真且当前有生效的领地保护时（`FtbChunksCompat.claimsProtect()`，受服务端配置「兼容 → 委派需领地保护」约束）整个让给领地插件，因此那时它永远投"不拦"。收的是行为者 UUID 而不是 `ServerPlayer`，所以其他模组自己接事件时也能复用同一条判定（含阵主恒定豁免、好友按配置豁免）。 |
| `CurrencyValueService` | 计算物品货币价值并处理不可用原因。 |
| `ItemMatcher` | 数据定义的物品匹配语法：`ItemMatcher.Entry` 有 `item`、`tag`、`wildcard`、`regex`、`spirit_storage` 五种，支持单项简写（物品 id 或标签）与混合数组；`find`/`findAll` 按 `priority` 升序取第一个/全部。 |
| `NumberProvider` | 常量、表达式、注册表类型分派和有限值处理。 |
| `DefinitionText` | 把数据包定义翻成名字：手里有 `Holder` / `ResourceKey` 时直接 `name(holder)`，类别就是注册表自己的 path（`mxt:aura` 里的 `mxt:fire` 查 `aura.mxt.mxt.fire`，多出的那一段 `mxt` 是注册表命名空间），所以同一个定义在提示框和物品选择器里不会有两种叫法。定义自带 `name` / `description` 字段时（19 张注册表，`quality` 与 `quality_chain` 是其中两个），`name(...)` 直接读字段，字段省略时由 `ContextNameCodec` 在加载期按 id 填上同一个键，见[数据包格式](../../数据包格式.md)的对应小节。 |
| `TooltipText` | 提示框的数值与一行的拼法：`number` / `signed` 按同一套小数规则格式化，`join(parts)`（或 `appendJoined`）用可翻译的 `tooltip.mxt.separator` 把多个片段拼成**一行**。**别在别处再拼一套分隔符、也别硬编码「、」或「, 」**——列表标点要跟着语言走，这里就是那唯一一处。 |

要**实现**的契约接口（`AuraAccess`、`ItemAuraAccess`、`UseItemAuraAccess`、`WheelMenuEntry`）都在 `com.iafenvoy.mxt.api` 下，见[特殊公开接口](interfaces)；上表列的是调用入口，仍留在各自的模块包里。`Toggable`（2026-09-23 由 `ToggableArtifactAbility` 升级而来）也不在此列，它留在 `data/ability`（见同页说明）。

除非接口明确标记为客户端 API，不要在渲染线程调用服务端生命周期注册表查询。
