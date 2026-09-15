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
| `FormationProtection` | 查询一个动作是否被阵法的守御模块拦下：行为者与被作用的一方**任一**在半径内即生效；`delegate_to_claims` 置真且当前有生效的领地保护时（`FtbChunksCompat.claimsProtect()`，受服务端配置 `formation.delegate_requires_claims` 约束）整个让给领地插件，因此那时它永远投"不拦"。收的是行为者 UUID 而不是 `ServerPlayer`，所以其他模组自己接事件时也能复用同一条判定（含阵主恒定豁免、好友按配置豁免）。 |
| `CurrencyValueService` | 计算物品货币价值并处理不可用原因。 |
| `ItemMatcher` / `UniversalMatcher` | 匹配物品、标签、通配符、正则和混合数组。 |
| `NumberProvider` | 常量、表达式、注册表类型分派和有限值处理。 |

除非接口明确标记为客户端 API，不要在渲染线程调用服务端生命周期注册表查询。
