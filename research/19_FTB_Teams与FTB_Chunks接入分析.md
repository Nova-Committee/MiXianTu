# FTB Teams 与 FTB Chunks 接入分析

> 来源：Gradle 缓存中的源码 jar，逐文件阅读，未依赖任何二手文档。
>
> - `dev.ftb.mods:ftb-teams:26.1.2.4`（common）+ `ftb-teams-neoforge:26.1.2.4`（平台层）
> - `dev.ftb.mods:ftb-chunks:26.1.2.8`（common）+ `ftb-chunks-neoforge:26.1.2.8`（平台层，从 `maven.ftb.dev` 下载）
> - `dev.ftb.mods:ftb-library:26.1.2.8`（配置与事件基类）
>
> 目标项目：MiXianTu（Mxt），Minecraft 26.1.2 / NeoForge 26.1.2.99 / Java 25。
>
> 本文只记录**接口事实与边界**，不复制 FTB 的实现，也不假设 FTB 的内部结构稳定。

## 结论摘要

1. **两边的"common jar"都是 Mojang 映射**，可以直接 `compileOnly`；但**平台事件类只在 `-neoforge` 里**（`FTBTeamsEvent`、`FTBChunksEvent`，两者都继承 ftb-library 的 `BaseEventWithData`）。
2. **FTB Teams 没有"盟友队伍"这个概念**：盟友就是把外部玩家以 `TeamRank.ALLY` 塞进本队的等级表。因此没有 `getAllies()`，答案只能从 `Team#getRankForPlayer(UUID)` 读。
3. **判断"是不是自己人"的正确写法是 `rank.isMemberOrBetter() || rank == ALLY`**（FTB Chunks 自己的读法）。诱人的 `isAllyOrBetter()` 是错的：它把 `INVITED` 也算进来，而 `INVITED(75) > ALLY(50)`，且自由加入的队伍会给**任何陌生人**返回 `INVITED`。
4. **FTB Teams 的队伍数据属于服务端**，按 UUID 索引，**玩家离线也能查**。这正是法阵"阵主离线"场景所需要的；纯实体上的好友列表在结构上做不到这一点。
5. **FTB Chunks 只拦玩家动作**：`shouldPreventInteraction` 第一行就是 `!(actor instanceof ServerPlayer) → false`。法阵、其他模组直接改方块、实体 AI 都不在它的管辖范围内（少数环境规则除外：爆炸/生物破坏/活塞/流体/火）。
6. **FTB Chunks 没有"领地保护开关"的 API**：总开关是服务器配置 `disable_protection`（可代码读写，可落盘，但不是 API 面）；官方 API 里唯一的相关方法是**给某个玩家开无敌**的 `ClaimedChunkManager#setBypassProtection`。
7. **"强制锁定"在 FTB 体系里的内置范式是"检查时现读配置、忽略队伍属性"**（`ally_mode=FORCED_ALL/FORCED_NONE`、`pvp_mode`）。队伍属性本身除 `ftbchunks:bypass_protection` 外都对玩家可编辑（GUI 需 OFFICER+，命令需队主），**属性变更事件不可取消**，想锁只能监听后回写。
8. **没有任何保护判定事件可挂**：`FTBChunksEvent` 只有 `RegisterCustomMinYCalculator` 和 `ChunkChange.Pre/Post`（认领/强制加载），全部保护挂钩都注册在 `EventPriority.HIGHEST`，且不对外暴露判定结果。
9. **交互是独立于"破坏/放置"的一条开关线，而且默认就是拦的**：右键方块、右键实体、攻击非生物各有自己的 `PrivacyMode`（默认全是 `ALLIES`），但**物品使用默认放行**（只有 `ftbchunks:right_click_blacklist` 能拦）。所以"FTB Chunks 不拦喝药水/吃东西/扔末影珍珠"，这一点与 Mxt 的 `mxt:protection` 正好相反。

---

## 版本与依赖形态

### 坐标现状（`build.gradle`）

```groovy
//FTB
compileOnly "dev.ftb.mods:ftb-library:26.1.2.8"
compileOnly "dev.ftb.mods:ftb-teams:26.1.2.4"
compileOnly "dev.ftb.mods:ftb-chunks:26.1.2.8"

//    runtimeOnly "dev.architectury:architectury-neoforge:20.1.14"
//    runtimeOnly "dev.ftb.mods:ftb-library-neoforge:26.1.2.8"
//    runtimeOnly "dev.ftb.mods:ftb-teams-neoforge:26.1.2.4"
//    runtimeOnly "dev.ftb.mods:ftb-chunks-neoforge:26.1.2.8"
```

`runtimeOnly` 全部注释掉，所以开发服里 **FTB 缺席**：只能编译验证，不能运行验证。

需要一个平台事件类时才加对应的 `-neoforge` 坐标（例如要监听 `ChunkChange.Pre` 就得加 `ftb-chunks-neoforge`）。纯查询用法（本文第三部分列出的 API）common jar 足够。

### 软依赖写法

`com.iafenvoy.mxt.compat.ftb` 已经确立了写法，FTB Chunks 若要接入应沿用同一套：

- **哨兵类不含任何 FTB 类型**（`FtbTeamsCompat`），`@EventBusSubscriber` 无条件注册，监听里先问 `ModList.get().isLoaded("ftbteams")`；
- **真正碰 FTB 的类只在守卫之内被首次加载**（`FtbTeamsRelation`）；
- 理由写在 `FtbTeamsCompat` 的 Javadoc 里：这个文件里只要出现一个 FTB 类型，**没有装 FTB 的实例就整个服务器起不来**。

---

## 第一部分：FTB Teams

### 数据模型

#### 三种队伍

| 类型 | `getId()` | `getTeamId()` | 说明 |
| --- | --- | --- | --- |
| 玩家队 `PlayerTeam` | 该玩家 UUID | 未加入聚会时同 `getId()`，加入后是聚会队 ID | 每人一个，首次登录自动创建 |
| 聚会队 `PartyTeam` | 随机 UUID | 同 `getId()` | 玩家组队 |
| 服务器队 `ServerTeam` | 随机 UUID | 同 `getId()` | 管理用途 |

`getTeamId()` 是"有效队伍 ID"，领地归属一类的东西读的是它。

#### 队伍查询

```java
TeamManager mgr = FTBTeamsAPI.api().getManager();

mgr.getTeamForPlayerID(uuid);      // 有效队伍：组队则返回聚会队；【离线也能查】
mgr.getTeamForPlayer(serverPlayer); // = getTeamForPlayerID(player.getUUID())
mgr.getPlayerTeamForPlayerID(uuid); // 永远是【个人队】，即使是聚会成员
mgr.getTeamByID(teamId);
mgr.getTeamByName(shortName);
mgr.arePlayersInSameTeam(id1, id2); // 比较 getId()
mgr.getKnownPlayerTeams();          // 个人队 map，不含聚会队
```

离线的可靠性来自实现：`knownPlayers` 在服务端启动时由 `loadAllTeams` 从磁盘载入，`getTeamForPlayerID` 只是 `knownPlayers.get(uuid)` 再取有效队伍；**没见过的玩家（从未登录过）返回 `Optional.empty()`**，不是"没队伍"的等价物，两者都要按 `empty` 处理。

#### 等级 `TeamRank`

```
ENEMY(-100) < NONE(0) < ALLY(50) < INVITED(75) < MEMBER(100) < OFFICER(500) < OWNER(1000)
```

- `isAtLeast` 对**负权**做了特殊处理（`power <= rank.power`），所以 `isEnemyOrWorse()` 只有 `ENEMY` 自身成立。
- **`ENEMY` 在 FTB Teams 里从未被赋值**：全代码库只有声明和 `isEnemyOrWorse()`，没有任何写入路径。所以不能指望用"敌对等级"表达敌意。
- `PartyTeam#getRankForPlayer` 对队长返回 `OWNER`；`PlayerTeam#getRankForPlayer` 对本人返回 `OWNER`。
- 其他情况：`ranks.get(uuid)`，取不到时 **`isFreeToJoin() ? INVITED : NONE`**（对**任何**队伍类型都成立，包括个人队）。

### 查询"队友"与"盟友"

#### 没有 `getAllies()`

FTB Teams 里队伍之间没有关系表。`/ftbteams party ally add <player>` 的实质是**给对方在本队的等级表里写一个 `ALLY`**，和成员存在同一张 map 里。所以：

```java
// 队友（不含盟友、不含被邀请者）
Set<UUID> members = team.getMembers();          // == getPlayersByRank(MEMBER).keySet()

// 全部有等级的人（含 ALLY / INVITED / 可能的 ENEMY）
Map<UUID, TeamRank> all = team.getPlayersByRank(TeamRank.NONE);   // 返回整张 ranks map

// 盟友：自己筛
team.getPlayersByRank(TeamRank.ALLY).forEach((id, rank) -> { if (rank == TeamRank.ALLY) ... });

// 单个判断：唯一推荐的入口
TeamRank rank = team.getRankForPlayer(uuid);
```

注意 `getMembers()` 是 `getPlayersByRank(MEMBER).keySet()`，**不含 `ALLY`、不含 `INVITED`**；而 `getPlayersByRank(NONE)` 直接返回整张表（`NONE` 被当成"不过滤"的哨兵，不是"等级 ≥ NONE"）。

#### 正确的"友方"判定

FTB Chunks 自己的读法（`ChunkTeamDataImpl#isAlly`，26.1.2.8）：

```java
rank.isMemberOrBetter() || rank == TeamRank.ALLY
```

Mxt 的 `FtbTeamsRelation#friendly` 从这里出发，只允许**向上加宽**：

```java
rank.isMemberOrBetter()
    || (MxtServerConfig.ftbTeamsAllyCounts()    && rank == TeamRank.ALLY)     // 默认开
    || (MxtServerConfig.ftbTeamsInvitedCounts() && rank == TeamRank.INVITED)  // 默认关
```

- **成员资格不可配置**：在队里就是在队里。
- 两个外来等级可配置，因为"包服所说的自己人"是哪一档是服务器的决定。
- `INVITED` 默认关是有依据的，见下。

#### 陷阱：`isAllyOrBetter()` 与自由加入的队伍

```java
team.getRankForPlayer(uuid).isAllyOrBetter();   // 不要用
```

两处叠加出错：

1. `INVITED(75) > ALLY(50)`，而 `isAllyOrBetter()` 是 `isAtLeast(ALLY)` —— 它把 `INVITED` 一起算作盟友；
2. `getRankForPlayer` 在 `free_to_join` 为真时给**任何陌生人**返回 `INVITED`。

即：**在开了自由加入的服务器上，这一行会把全服陌生人变成"队友"**，而"攻击法阵不生效"会因此对所有人生效。这就是 Mxt 把 `INVITED` 单独做成默认关闭的开关、并在配置描述里写明风险的原因。

#### 离线可查

因为队伍数据在服务端按 UUID 索引，`getRankForPlayer` / `getTeamForPlayerID` **不要求玩家在线**。这一点决定了 Mxt 的 `FriendEvent.Relation` 同时携带 `judgeId` 和可空的 `judge` 实体：阵主离线时纯实体好友列表无解，而队伍级来源仍然可以作答。

### 事件清单

common 里定义的是数据记录（`XxxEvent.Data`），NeoForge 侧的事件类叫 `FTBTeamsEvent.Xxx` 并继承 `BaseEventWithData<...>`：

| NeoForge 事件 | `Data` 字段 | 备注 |
| --- | --- | --- |
| `TeamManager` | `manager`, `action` | `CREATED`（服务端即将启动）/`LOADED`/`SAVED`/`DESTROYED` |
| `TeamCreated` | `team`, `creator?`, `creatorId` | |
| `TeamLoaded` | `team` | 从磁盘载入 |
| `TeamSaved` | `team` | |
| `TeamDeleted` | `team` | |
| `TeamPlayerLoggedIn` | `team`, `player` | 队伍信息就绪**之后**才发 |
| `PlayerChangedTeam` | `team`, `previousTeam?`, `playerId`, `player?` | 任何换队都会发 |
| `PlayerJoinedPartyTeam` | `team`, `previousTeam`, `player` | |
| `PlayerLeftPartyTeam` | `team`, `playerTeam`, `playerId`, `player?`, `teamDeleted` | `player` 可能为 null |
| `PlayerTransferredOwnership` | `team`, `fromPlayer?`, `to: Either<ServerPlayer, NameAndId>` | `fromPlayer` 为 null 表示原队长离线/控制台操作 |
| `TeamPropertiesChanged` | `team`, `previousProperties`, `isClient` | **不可取消**，只是通知 |
| `TeamAlly` | `team`, `players: List<NameAndId>`, `adding` | 加/减盟友 |
| `CollectTeamProperties` | `consumer` | 追加自定义队伍属性 |
| `TeamInfo` | `consumer` | 往 `/ftbteams info` 输出加行 |

**没有**"玩家上下线"以外的队伍成员变更事件：成员增减通过 `TeamAlly`（盟友）与 `PlayerJoinedPartyTeam`/`PlayerLeftPartyTeam`（聚会）体现；个人队没有成员变化。

### 队伍属性体系

#### 内置属性

| 属性 | 类型 | 玩家可编辑 |
| --- | --- | --- |
| `ftbteams:display_name` | String（`.{3,}`） | 是，`syncToAll` |
| `ftbteams:description` | String | 是 |
| `ftbteams:color` | Color | 是，`syncToAll` |
| `ftbteams:free_to_join` | Boolean（默认 false） | 是 |
| `ftbteams:max_msg_history_size` | Int（默认 1000） | 是 |
| `ftbteams:team_stages` | StringSet | **否**（`hidden` + `notPlayerEditable`） |
| `ftbteams:lives_remaining` | Int | **否**（同上） |

FTB Chunks 通过 `CollectTeamPropertiesEvent` 追加自己的一批（详见第二部分）。

#### 编辑权限（两条路径都查）

| 路径 | 权限 | 过滤 |
| --- | --- | --- |
| 客户端 GUI → `UpdatePropertiesRequestMessage` | `isOfficerOrBetter(player)` | `copyIf(p -> p.isPlayerEditable() && !p.isHidden())` |
| 命令 `/ftbteams party settings <key> <value>` | 队主（`settings_for` 需 GAMEMASTER） | `key.isPlayerEditable()` 检查 |

代码层 `AbstractTeamBase#setProperty` **没有任何权限校验**（`properties.set(...)` + `markDirty()`），所以任何 mod 都能写。

#### 强制与锁定

- `TeamProperty#notPlayerEditable()` / `hidden()` 只能在**定义属性时**调用；外部 mod 无法把 FTB 已有的属性改成"玩家不可编辑"。
- 内置的"强制"做法是**检查时现读配置**：FTB Chunks 的 `ally_mode`（`FORCED_ALL`/`FORCED_NONE` 直接短路 `isAlly`）、`pvp_mode`（`NEVER`/`ALWAYS`）都是这个范式，配置注释写明 *"Forced modes won't let players change their ally settings"*。
- 想让某个属性"改了也弹回去"，只能监听不可取消的 `TeamPropertiesChanged` 后回写 `setProperty`。
- 另一个官方控制点是 `FTBTeamsAPI.api().setPartyCreationFromAPIOnly(true)`：**禁止玩家通过命令或 GUI 创建聚会队**，只允许 mod 经 API 创建（用于"建队与建家绑定"的整合包）。

---

## 第二部分：FTB Chunks

### 判定核心

所有玩家动作保护都汇到一处：

```java
// ClaimedChunkManagerImpl#shouldPreventInteraction
if (!(actor instanceof ServerPlayer player)          // ← 只拦玩家
        || FTBChunksWorldConfig.DISABLE_PROTECTION.get()
        || getBypassProtection(player.getUUID())) {
    return false;
}
if (isFakePlayer && ALLOW_FAKE_PLAYERS.get().isOverride()) return 该策略;
ClaimedChunkImpl chunk = getChunk(new ChunkDimPos(player.level(), pos));
if (chunk == null && !noWilderness(player)) return false;    // 未认领且不启用荒野保护
ProtectionPolicy policy = protection.getProtectionPolicy(player, pos, hand, chunk, targetEntity);
boolean prevented = policy.isOverride() ? policy.shouldPreventInteraction()
                                        : isFake || !player.isSpectator();
```

由此可得三条不显然的结论：

- **旁观者永远不被拦**（CHECK 分支下 `isFake || !isSpectator` 为假）。
- **假玩家在 CHECK 分支下默认被拦**，除非全局 `fake_players.fake_players` 覆盖或队伍开了假玩家项。
- **被拦时会顺手发提示**：`PlayerNotifier.notifyWithCooldown(player, "ftbchunks.action_prevented" | "ftbchunks.need_to_claim_chunk", 2000)`，假玩家还会写 `prevented_access` 日志。

`ProtectionPolicy` 只有三个值：`ALLOW`（无条件放行）、`DENY`（无条件拒绝）、`CHECK`（走默认，即"玩家就拦、旁观者放行"）。

内置策略（`Protection` 里的常量，都是 `@FunctionalInterface`）：

| 常量 | 放行条件 | 用于 |
| --- | --- | --- |
| `EDIT_BLOCK` | `ftbchunks:edit_whitelist` 标签，或 `canPlayerUse(BLOCK_EDIT_MODE)` | 破坏、放置、踩踏耕地 |
| `INTERACT_BLOCK` | `ftbchunks:interact_whitelist` 标签，或 `canPlayerUse(BLOCK_INTERACT_MODE)` | 右击方块 |
| `EDIT_FLUID` | `canPlayerUse(BLOCK_EDIT_MODE)` | 桶（**当前未接线**） |
| `RIGHT_CLICK_ITEM` | 食物 / 无负面效果药水 / `right_click_whitelist`，或 `BLOCK_INTERACT_MODE`，否则 `right_click_blacklist` 为 DENY，**默认 ALLOW** | 对空气右键物品 |
| `INTERACT_ENTITY` | `entity_interact_whitelist` 标签，或 `ENTITY_INTERACT_MODE` | 交互实体 |
| `ATTACK_NONLIVING_ENTITY` | `nonliving_entity_attack_whitelist` 标签，或 `NONLIVING_ENTITY_ATTACK_MODE` | 攻击非生物实体 |
| `EDIT_AND_INTERACT_BLOCK` | `interact_whitelist` 或 `BLOCK_EDIT_AND_INTERACT_MODE` | **仅 Fabric** 使用 |

NeoForge 注入的是 `new PlatformProtections.Impl(EDIT_BLOCK, INTERACT_BLOCK, EDIT_BLOCK)`（破坏 / 交互 / 放置），在 mod 构造器里设置。

### 保护范围

#### 玩家动作

| 动作 | NeoForge 挂钩（全部 `EventPriority.HIGHEST`） | 依据 |
| --- | --- | --- |
| 破坏方块 | `BreakBlockEvent` + `PlayerInteractEvent.LeftClickBlock` | `EDIT_BLOCK` |
| 放置方块 | `BlockEvent.EntityPlaceEvent` | `EDIT_BLOCK` |
| 右击方块 | `PlayerInteractEvent.RightClickBlock` | `INTERACT_BLOCK`；**手持 BlockItem 时会额外再查一次"放置"** |
| 右键物品（对空气） | `PlayerInteractEvent.RightClickItem` | `RIGHT_CLICK_ITEM`（默认放行） |
| 交互实体 | `EntityInteract` + `EntityInteractSpecific` + `ArmorStandMixin` | `INTERACT_ENTITY` |
| 攻击非生物/盔甲架 | `AttackEntityEvent` + `EntityMixin#hurtOrSimulate` | `ATTACK_NONLIVING_ENTITY`；**不拦攻击生物** |
| 踩踏耕地 | `BlockEvent.FarmlandTrampleEvent` | `EDIT_BLOCK` |
| PvP | `LivingIncomingDamageEvent` | `pvp_mode`（ALWAYS/NEVER/PER_TEAM）+ 队伍 `allow_pvp`；攻守任一方在受保护区即拦 |

`blockRightClick` 里那句双查是有代价的（源码注释自认）：**拿着方块物品时，对任何方块右键都会被按"放置"拦一次**（上游 issue #1752）。

#### 环境（非玩家，不看 actor）

| 项目 | 实现 | 开关（默认） |
| --- | --- | --- |
| 爆炸 | `ExplosionEvent.Detonate` 逐区块剔除方块；实体只剔**非生物** | 队伍 `allow_explosions`（默认关 = 保护） |
| 生物破坏 | `EntityMobGriefingEvent`，只对 `ftbchunks:entity_mob_griefing_blacklist`（默认仅末影人） | 队伍 `allow_mob_griefing`（默认关） |
| 活塞跨领地 | `PistonBaseBlockMixin` + `PistonHelper` | `piston_protection`（**默认开**） |
| 流体水平跨领地 | `FlowingFluidMixin` + `FlowingFluidHelper` | `flowing_fluid_protection`（默认关，性能） |
| 火蔓延 | `FireBlockMixin` + `BlockEvent.FluidPlaceBlockEvent` | `fire_spread_protection`（默认关，性能） |

后三项的拦截条件一致：**目标区块属于不同队伍且 `block_edit_mode != PUBLIC`**，并且都额外检查 `disable_protection`。

#### 荒野

`no_wilderness` / `no_wilderness_dimensions`（非空时覆盖前者）/ FTB Ranks 权限 `ftbchunks.no_wilderness`：未认领区块也要求先认领才能动手，提示换成 `ftbchunks.need_to_claim_chunk`。这是"把保护扩到全场"的近似物。

#### 已经是死代码的部分（26.1.2.8 实测）

- **桶**：`NeoEventListeners` 里 `// bus.addListener(this::fillBucket);` 被注释，注释写着 `TODO … (do we need this?)`；`EDIT_FLUID` 只被该方法使用 → NeoForge 端没有桶保护。
- **`protect_unknown_explosions`**：配置项已声明，**全代码库无人读取**。
- **生物生成保护**：`ClaimedChunkImpl#canEntitySpawn` 恒返回 `true`，`MobSpawnEvent.SpawnPlacementCheck` 链路空转。

#### 数据包标签（公开、可用）

`ftbchunks:edit_whitelist`、`interact_whitelist`（方块）；`right_click_whitelist`、`right_click_blacklist`（物品）；`entity_interact_whitelist`、`nonliving_entity_attack_whitelist`、`entity_mob_griefing_blacklist`（实体类型）。这些是"强制放行"的白名单，适合整合包自己加内容。

#### 不保护的范围（对 Mxt 最关键）

**只拦 `ServerPlayer` 发起的动作。** 因此：

- 法阵产生的世界改动、其他模组直接调用 `level.destroyBlock` / `setBlock`、实体 AI 行为，FTB Chunks **既不拦也不感知**；
- 想"让法阵尊重领地"，必须由 Mxt 自己查 API；
- 反过来，如果 Mxt 希望某个玩家的法阵/技能在领地里生效，也**不需要**绕过 FTB Chunks —— 只要那条路径不是原版玩家破坏/放置。

例外是上面那五项环境规则：它们不看 actor，**会**作用到法阵造成的爆炸/火/流体/活塞上（前提是相应开关打开）。

### 交互保护：拦，而且默认就拦

FTB Chunks 的保护不止"别拆我的地"，**交互是一条独立的开关线**，默认值同样是拦：

| 判定常量 | 挂的事件（全部 `EventPriority.HIGHEST`） | 依据 | 默认 |
| --- | --- | --- | --- |
| `INTERACT_BLOCK` | `PlayerInteractEvent.RightClickBlock`（取消 + `InteractionResult.FAIL`） | 队伍属性 `block_interact_mode` | `ALLIES` |
| `INTERACT_ENTITY` | `EntityInteract` + `EntityInteractSpecific` + `ArmorStandMixin` | `entity_interact_mode` | `ALLIES` |
| `ATTACK_NONLIVING_ENTITY` | `AttackEntityEvent` + `EntityMixin#hurtOrSimulate` | `nonliving_entity_attack_mode` | `ALLIES` |
| `RIGHT_CLICK_ITEM` | `PlayerInteractEvent.RightClickItem` | 标签 `right_click_whitelist` / `right_click_blacklist`；食物与无负面效果药水无条件放行 | **放行** |

四个默认值在 `FTBChunksWorldConfig.TEAM_PROP_DEFAULTS` 里逐个核过，全是 `PrivacyMode.ALLIES`（成员 + 盟友）。`ALLIES` 之外的陌生人一律被拦，除非队伍属性被改成 `PUBLIC`。

**`RIGHT_CLICK_ITEM` 的默认分支是 `ALLOW`：FTB Chunks 刻意不管物品使用。** 喝药水、吃东西、扔末影珍珠在别人的领地里是允许的，要拦只能把物品写进 `ftbchunks:right_click_blacklist`。同理 `Nonliving` 那条**不拦攻击生物** —— 攻击生物属于 `pvp_mode`（默认 `ALWAYS`，即全图允许）。

两个坑：

1. **手持 `BlockItem` 时，右键任何方块会被额外按"放置"判一次**（`FTBChunks.blockRightClick` 里的双查，源码注释自认"不理想但必要"，上游 issue #1752）。所以拿着方块右键别人的箱子，被拒的原因可能是 `block_edit_mode` 而不是 `block_interact_mode`，排查时要看清是哪一个。
2. **假玩家走另一套判定**（`ChunkTeamDataImpl#canFakePlayerUse`）：`allow_fake_players`（默认 false）、`allow_fake_players_by_id`（默认 **true**）、`allow_named_fake_players`（默认空）。默认下"冒充某个有权限玩家的假玩家"是放行的，自动化模组正是靠这一条工作。

#### 与 Mxt 阵法守御模块的分工

| | FTB Chunks | Mxt 的阵法守御模块 |
| --- | --- | --- |
| 范围 | 认领的区块 | 球形半径（`radius`） |
| 判定依据 | 玩家在**队伍里的等级**（成员 / 盟友 / 公开） | 阵主的**好友系统**；装了 FTB Teams 时该来源正是 `成员及以上 或 == ALLY` |
| 右键方块 | ✅ 默认拦（`block_interact_mode`） | ✅ `block_interact`，默认拦 |
| 右键实体 | ✅ 默认拦（`entity_interact_mode`） | ✅ `entity_interact`，默认拦 |
| 攻击 | 只拦**非生物**实体 | `attack_entity`，拦所有近战（含生物） |
| 物品使用 | ❌ **默认放行**，黑名单 opt-in | ✅ `item_use`，默认拦 |
| 破坏 / 放置 | ✅ 各自的开关 | ✅ `block_break` / `block_place` |
| 环境 | 爆炸 / 生物破坏 / 活塞 / 流体 / 火 | 爆炸 / 生物破坏 |

三点值得记住：

- **两边的"盟友"口径是一致的**：Mxt 的 FTB 来源抄的就是 FTB Chunks 的 `isMemberOrBetter() || == ALLY`（`config.mxt.server.friends.ftb_teams_ally` 默认开），所以 FTB 眼里的队友与盟友会被**两边同时豁免**，不会出现"FTB 放行、阵法拦住"的分裂。
- **两边同时装上是"与"关系**：各自在自己的事件处理器里取消，谁拒绝都算拒绝。
- **差集正好是 Mxt 多出来的那两块**：`item_use` 与"攻击生物"。FTB Chunks 不管这两件事，所以"在我的领地里不能喝药 / 不能打我的宠物"这类规则只能由阵法守御模块表达；反过来，一个只想沿用 FTB Chunks 那套规则的整合包，可以让阵法把守御整个交出去（见第三部分「把守御交给领地插件」）。

### 开关与锁定分层

| 层级 | 手段 | 官方 API？ | 立即生效 | 能锁住吗 |
| --- | --- | --- | --- | --- |
| 全局总开关 | 配置 `disable_protection` | ✗（是 `FTBChunksWorldConfig` 的 public 静态字段） | 是（每次判定现读） | 不能，无变更事件 |
| 全局单项环境 | `piston_protection` / `flowing_fluid_protection` / `fire_spread_protection` | ✗（同上） | 是 | 不能 |
| 全局荒野 | `no_wilderness` / `no_wilderness_dimensions` | ✗（同上），另有 FTB Ranks 权限 `ftbchunks.no_wilderness` | 是 | 不能 |
| 全局假玩家 | `fake_players.fake_players` = CHECK/DENY/ALLOW | ✗（同上） | 是 | 不能 |
| 全队放行 | 队伍属性 `ftbchunks:bypass_protection` | ✓ `Team#setProperty` | 是 | **可以**（玩家改不了这个属性） |
| 单玩家放行 | `ClaimedChunkManager#setBypassProtection(uuid, bool)` | ✓ | 是 | 可以，且落在**个人队**上，组队/退队不丢 |
| 队伍细粒度 | `block_edit_mode` / `block_interact_mode` / `entity_interact_mode` / `nonliving_entity_attack_mode` / `claim_visibility` / `location_mode`（PRIVATE/ALLIES/PUBLIC）；`allow_explosions` / `allow_mob_griefing` / `allow_pvp` / 三个假玩家项 | ✓ `Team#setProperty` | 是 | **不能**，玩家 OFFICER+ 可改回；只能监听 `TeamPropertiesChanged` 回写 |
| 内置强制模式 | `ally_mode=FORCED_ALL/FORCED_NONE`、`pvp_mode=NEVER/ALWAYS/PER_TEAM` | ✗（配置） | 是 | 是（现读配置，队伍属性被无视） |
| 注入点 | `FTBChunksAPIImpl.INSTANCE.setPlatformProtections(...)` | ✗（impl 包） | 是 | — |

**结论**：**"强制禁止领地保护"没有专门的 API**。可用的组合是：

1. **全域关**：`FTBChunksWorldConfig.DISABLE_PROTECTION.set(true)`，需要落盘再调 `ConfigManager.getInstance().save(FTBChunksWorldConfig.KEY)`（`KEY == "ftbchunks-world"`）。配置文件是 `<instance>/config/ftbchunks-world.json5`，若存在 `<world>/serverconfig/ftbchunks-world.json5` 则以后者为准且优先载入（`Json5Util.FILE_EXT == ".json5"`，不是 `.snbt`）。注意配置在 `ServerAboutToStartEvent` 才从文件读入，**在此之前读到的是默认值 `false`**；且该开关会让活塞/流体/火三项一并失效。
2. **针对某人/某队**：`setBypassProtection` 或写 `BYPASS_PROTECTION` 属性。
3. **要"锁死"**：FTB 没有配置变更事件，只能在 `ServerStartedEvent` 之类的时机回写，或依赖文件权限。

#### 没有 per-claim（单块领地）的保护开关

三条独立证据，都是全量查过的：

1. **存档侧**：`ClaimedChunkImpl` 的字段只有 `teamData` / `pos` / `time` / `forceLoaded` / `forceLoadExpiryTime`，`toJson` 也只写这五个；`ChunkTeamDataImpl` 的队伍存档里没有逐区块的表。
2. **网络侧**：一个领地的同步形态 `ChunkSyncInfo` 只有 `claimed` / `forceLoaded` / 两个相对时间 / `expires`，标志位一共两位（`0x01` 强制加载、`0x02` 有到期），**没有给保护留字段**。
3. **界面侧**：全部 `ftbchunks.gui.*` 文案与全部注册命令里没有任何按领地的东西。客户端点开一块领地只有：认领 / 取消认领、强制加载 / 取消、卸载全部、管理员模式、认领笔刷形状、区块信息 —— 其中 "Admin Mode" 的提示是 *"you can modify chunks, regardless of ownership"*，管的是**改认领**，不是绕过保护。

判定路径本身也只读队伍属性：`shouldPreventInteraction` → `chunk.getTeamData().canPlayerUse(player, property)`。

**等价做法（不用写模组）**：保护属于**队伍**，而领地是**认领给某个队伍**的，所以"单独控制一块领地"= "让它属于另一个队伍"：

```mcfunction
# 1) OP：建一个"开放区"队伍
/ftbteams server create 开放区
# 2) OP：把它四个隐私模式全设成 public（等价于这块地不保护）
/ftbteams server settings <server_team> ftbchunks:block_edit_mode public
/ftbteams server settings <server_team> ftbchunks:block_interact_mode public
/ftbteams server settings <server_team> ftbchunks:entity_interact_mode public
/ftbteams server settings <server_team> ftbchunks:nonliving_entity_attack_mode public
# 3) GAMEMASTER：站在要开放的区块上认领给它
/ftbchunks admin claim_as <server_team>
```

代价与边界：

- 多一个队伍身份：地图颜色不同，`/ftbteams list`、`/ftbchunks info` 里都多一项；成员与盟友要各自维护。
- **上限按队伍算**：`max_claimed_chunks` / `hard_team_claim_limit` 都是每队的；服务器队没有"玩家"可查 FTB Ranks，走的是配置平值（`getMaxClaimedChunks(playerData, null)`）。
- 收回要 `/ftbchunks admin unclaim_as`。
- 这是**按队伍分块**，不是按块开关：同一队的两块地不可能一个保护一个不保护。

**真·按区块只能是模组的活**：`Protection#getProtectionPolicy(player, pos, hand, chunk, entity)` 本身**收得到 `chunk`**，而 `FTBChunksAPIImpl.INSTANCE.setPlatformProtections(...)` 是唯一注入点 —— 一个附加模组可以据此实现"按区块的保护表"（impl 包、无 API 稳定性保证）。换一个支持 per-claim 的领地模组是另一条路。

> 这条直接决定 `delegate_to_claims` 的粒度：把守御交给 FTB 就等于接受**按队伍**的粒度。服务器只要按上面的手法把某片设成 public，那片上的阵法守御就是零 —— 而且我们的警告检查的是"FTB 在不在"与"全局开关关没关"，**看不出队伍级的 public**。

### 查询接口（无副作用）

```java
ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
if (chunk != null) {
    ChunkTeamData data = chunk.getTeamData();
    boolean canEdit = data.canPlayerUse(player, FTBChunksProperties.BLOCK_EDIT_MODE);
    data.canExplosionsDamageTerrain();
    data.allowMobGriefing();
    data.allowPVP();
    data.isAlly(uuid);
    data.isTeamMember(uuid);
}
FTBChunksAPI.api().getOwningTeam(level, new ChunkPos(pos));   // Optional<Team>
```

- public 的 `ClaimedChunk` **不含** `allowExplosions()` / `allowMobGriefing()` / `canEntitySpawn()`（那些在 `ClaimedChunkImpl`）；public 侧走 `ChunkTeamData`。
- `shouldPreventInteraction(...)` 能得到与 FTB Chunks **完全一致**的答案（含 bypass / 荒野 / 假玩家 / 旁观者），但**有副作用**（弹提示、记日志）。**只查询时不要用它**。
- `FTBChunksAPI.api()` 是 `Objects.requireNonNull`，必须先过 `isManagerLoaded()`；mod 列表检查用 `ModList.get().isLoaded("ftbchunks")`。

### 事件

`FTBChunksEvent` 只有两项：

- `RegisterCustomMinYCalculator`（地图最低高度计算）；
- `ChunkChange.Pre` / `ChunkChange.Post`（认领 / 取消认领 / 强制加载 / 取消加载，`Pre` 可取消并能改 `ClaimResult`）。

**没有保护判定事件，也没有可注入的保护回调**。唯一能在判定链上插入自己规则的位置是 `PlatformProtections` 的替换（impl 包，无 `@ApiStatus` 标注，也没有防覆盖保护）。

---

## 第三部分：Mxt 现有接入与设计取舍

### 已实现（FTB Teams 方向）

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `FriendEvent` / `FriendEvent.Relation` | `event/` | 判定扩展点，`UUID judgeId` + 可空 `judge` 实体 + `candidate`，`TriState` 结果 |
| `FriendService` | `runtime/friend/` | 发事件 → 无人作答则走内置；`identify`/`builtin` 分离，**内置永不重发事件** |
| `FriendAttachment` + `FriendCache` | `attachment/`、`runtime/friend/` | 玩家自己的名单（永久/临时）；离线镜像缓存，登录填充、登出刷新 |
| `FtbTeamsCompat` / `FtbTeamsRelation` | `compat/ftb/` | 无 FTB 类型的哨兵 + 只写 `TRUE` 的等级判定 |
| `FormationRelations#affects` | `runtime/formation/` | 敌对法阵对"自己人"不生效；阵主离线走停火 |
| `FormationAllyEntityCondition` | `data/condition/builtin/entity/` | `mxt:formation_ally`：按阵主 UUID 判定，供数据包自建行动树 |
| `MxtBiEntityConditions.FRIEND` | `registry/` | `mxt:friend`：按 `actor`/`target` 两个实体判定 |
| `friendRelation` | `compat/kubejs/` | KubeJS 事件，视图含 `judgeId`/`judge`/`candidate`/`result`/`setFriend`/`abstain` |

配置：

| 键 | 默认 | 含义 |
| --- | --- | --- |
| `config.mxt.server.formation.respect_friends` | true | 敌对法阵是否执行敌我判断 |
| `config.mxt.server.friends.ftb_teams_ally` | true | `ALLY` 等级算队友 |
| `config.mxt.server.friends.ftb_teams_invited` | false | `INVITED` 等级算队友 |

### 设计取舍（有意为之，不要"顺手优化"掉）

1. **`DEFAULT` ≠ "没人能回答"**，而是"交给内置好友系统"，内置系统是最后一环且**自己不再发事件**。监听器里要用 `builtin` 而不是 `identify`，否则递归。
2. **FTB 来源只写 `TRUE`**：FTB 知道成员和盟友，不知道玩家的私人好友；写 `FALSE` 会**覆盖**玩家自己的名单，而不是补充它。
3. **FTB 查询不做缓存**：队伍数据常驻内存、按 UUID 索引，一次判定只有两次 map 读；缓存只会引入过期，还得在加入/退出/升职/盟友变更时失效。`TeamAlly`、`PlayerJoinedPartyTeam`、`PlayerLeftPartyTeam` 因此**有意不订阅**。
4. **`ModList.isLoaded` 每次现问**，不缓存：mod 列表在启动后固定，缓存没有收益。
5. **好友镜像是纯内存的**：落盘一份陈旧镜像会给出**错误**答案（把不该保的人保了），空镜像只会给出**保守**答案（停火），所以重启后镜像为空、等玩家登录再填。镜像里**空集合也记录**："这人没有好友"（`FALSE`，该打就打）与"没见过这人"（`DEFAULT`，停火）是两个答案。
6. **镜像只在会话两端刷新**（登录清空临时名单后、登出时）：在线时读实体上的实时名单，镜像陈旧无害；这样以后新增任何写好友数据的路径都**不需要知道**这个类。
7. **`mxt:friend` 与 `mxt:formation_ally` 不要和 `hostile` 同时用**：前者作用于两个实体，后者作用于"阵主—实体"这一对，语义不同。
8. **认不出敌就不开火**：没有任何来源作答时，敌对法阵对谁都不生效。逃生口是自己写行动树（不设 `hostile`，改用 `mxt:formation_ally`，上下文外答案为 `false` 走"命中"分支），或关掉 `respect_friends`。

### 把守御交给领地插件（Mxt 侧的实现）

Mxt 的阵法守御模块有一个 `delegate_to_claims` 开关：置真且**当前确实有生效的领地保护**时，**它自己一个开关都不执行**。
实现上是"什么都不做"，而这一点正是值得记下来的第一条结论 —— **不需要调用 FTB Chunks 的 API**：

- FTB Chunks 在自己的事件处理器里**无条件**保护它自己的领地，从不问任何人；
- Mxt 的拦截是"再取消一次"，因此是**加法**。让 Mxt 站下（不取消），剩下的就是 FTB Chunks 的判定，一个字节的 API 都不必碰；
- 于是"交出判定"本身不需要把 Mxt 的动作映射到 `Protection.EDIT_BLOCK` / `INTERACT_BLOCK` 之类的常量上，对**任何**领地插件都成立。

但"当前确实有生效的领地保护"这个前提总得问一句，而那**需要**碰 FTB Chunks —— 于是有了本仓库第二处软依赖（照 `FtbTeamsCompat`
那套哨兵写法）：

| 类 | 含 FTB 类型？ | 职责 |
| --- | --- | --- |
| `compat/ftb/FtbChunksCompat` | **否** | `loaded()` 与 `claimsProtect()`；后者 = 装了**且**全局保护没被关 |
| `compat/ftb/FtbChunksState` | 是 | 读 `FTBChunksWorldConfig.DISABLE_PROTECTION`（该状态**没有 API**，只有这个字段） |

`claimsProtect()` 读的是**全局**开关，**队伍级看不见**：FTB 是按归属队伍的四项 PrivacyMode 逐块判定的，某个队伍把自己的领地
全设成 public 时这里仍返回 true。要把这一层也重算，等于把领地插件的规则抄第二份，而那正是"交出去"想避开的事。

**服务端配置 `config.mxt.server.formation.delegate_requires_claims`（默认开启）决定"没有领地保护"时怎么办**：

- **开（默认）**：`delegate_to_claims` 只在有生效的领地保护时才交出；否则阵法**回退到自己那九个开关**，并在激活时按定义打一条
  警告日志（`FormationProtection.warnIfDelegationFallsBack`）。一座静悄悄什么都不保护的守御阵，正是这个模块存在的意义所在，
  值得一个服务端选项。
- **关**：字面读法 —— 一律交出，没有领地保护时它就真的什么都不保护。

两条代价仍属于设置者：交出时阵法只保护**被认领**的地方（自己的 `radius` 不再参与判定）；而在上面的"回退"分支里，阵法保护的是
**它自己半径内**的东西，与 FTB 的判定无关。

#### 服务端侧：三种联动方式（`claim_linkage`）

上面那些是**内容包**对单座阵法的声明；服务器另有一个配置决定**所有**保护阵法与领地插件怎么相处
（`config.mxt.server.formation.claim_linkage`，默认 `none`）：

| 取值 | Mxt 侧实现 |
| --- | --- |
| `none` | 不联动，两边都在时是"与"关系（谁拒绝都算拒绝） |
| `claims_only` | `FormationWorldService.activate` 在**结构校验之后、扣费之前**要求阵心所在区块已被认领，否则 `Failure.NOT_CLAIMED`；只作用于带守御模块的阵法，且**不要求是自己的队** |
| `claims_precedence` | 阵心所在区块被认领时，该阵法的九个开关整个交出（走同一个 `delegationHandsOver()` 前提检查）；**判定看阵心区块**，不看逐个受影响位置 |

**两个模式在 FTB Chunks 缺席时都保持惰性**，而不是让阵法立不起来：没有领地可要求，也没有规则可让。把找不到前提的选项做成硬故障，
等于让服务器因为卸了一个软依赖而变成不可玩。`claims_only` 的这种情况会打一条警告（整局一次）。

`claims_precedence` 是这三种里唯一在**判定路径**上要问 FTB 的：每次判定、每座立在领地内的阵法一次 `getOwningTeam`。
`none` 档靠 `&&` 短路，一次 FTB 调用都不产生 —— 这也是它适合做默认值的另一个理由。

#### 谁的地上能立：`wards_need_claim_permission`（默认开启）

上面那个枚举管"两个系统如何共处一个位置"，这条独立开关管"**阵法能站在谁的地上**"。它补的是一个 `claims_only` 盖不住的洞：
允许在某地放方块 ≠ 允许在那里立法，而**结构本来就立着**时（模板匹配自然地形、或别人搭好的）方块保护什么都拦不住。

阵心落在别人认领的区块里时，只有三种人能立守御阵，**三种都是地主自己的答案**而不是这里另立的规矩：

| 通过方式 | 问的是谁 |
| --- | --- |
| 有编辑权限 | FTB 自己：`ChunkTeamData#canPlayerUse(player, BLOCK_EDIT_MODE)`（公开领地、职级、盟友、被许可的假玩家由它判） |
| 是地主的好友 | Mxt 的好友系统（装了 FTB Teams 时队友与盟友已算好友，自然通过） |
| 无主之地 | 没有人可问，谁都能立 |

地主取的是**队伍所有者那个玩家**（`Team#getOwner()`）：领地属于队伍，而能回答"这人算不算自己人"的是队伍所有者的好友名单；
控制台所有的服务器队没有玩家可问，直接过滤掉，而不是拿 nil UUID 去问好友系统。无领地插件时这条同样惰性。

> 交出去之前值得先看一眼上面那张对照表的差集：FTB Chunks **默认不拦物品使用**，也**不拦攻击生物**。也就是说
> `item_use` 与 `attack_entity` 一旦交出去，这两条就真的没人管了。

### 未决问题

1. **队友/盟友能否拆阵法**：`FormationRelations#canDismantle` 的 TODO 仍然只关于"好友能不能拆"，尚未决定。
2. **是否需要 FTB 方向的总开关**（当前只有两个等级开关，没有"完全不看 FTB"的开关）。
3. **是否打开 `runtimeOnly`** 让开发服真的带上 FTB：目前 FTB 全是 `compileOnly`，审计只能覆盖"FTB 缺席"分支和配置读取。
4. **是否让法阵/技能尊重 FTB Chunks 领地**：查询层（`FtbChunksCompat`）尚未建立；需要先确认是要"尊重保护"还是"在保护区内强制生效"。
5. **是否引入 `ftb-chunks-neoforge`** 以便监听 `ChunkChange.Pre`（例如禁止在阵法覆盖范围内部认领区块）。

### 若接入 FTB Chunks，建议的落点

- 沿用 `compat/ftb/` 的哨兵写法：`FtbChunksCompat`（**无 FTB 类型**，`loaded()` / `claimsProtect()`）+ `FtbChunksState`（含 FTB 类型，只在守卫内被加载）。**已按此落地**（见第三部分「把守御交给领地插件」）。
- 对外只暴露与 FTB 无关的查询结果（例如 `boolean claimsProtect()`），不要把 FTB 类型泄漏到 `runtime/`。
- 需要"只查询"就用 `getChunk` + `ChunkTeamData#canPlayerUse`；需要"和 FTB 完全一致"才用 `shouldPreventInteraction`，并接受它的提示副作用。
- 需要"让某阵主无视领地"时用 `setBypassProtection`，不要用 `disable_protection`（后者是全局的，且会关掉环境三项）。
- `runtime/` 侧只经由哨兵问状态，绝不直接 `ModList.get().isLoaded("ftbchunks")` 之外再碰 FTB —— 那条规则是整套软依赖能成立的唯一原因。

---

## 附：核对方式

- FTB Teams：`ftb-teams-26.1.2.4-sources.jar`（common，`dev/ftb/mods/ftbteams/api/**`、`data/**`）+ `ftb-teams-neoforge-26.1.2.4-sources.jar`（`api/neoforge/FTBTeamsEvent.java`）。
- FTB Chunks：`ftb-chunks-26.1.2.8-sources.jar`（common，API、`data/`、`core/mixin/`、`config/`）+ `ftb-chunks-neoforge-26.1.2.8-sources.jar`（`NeoEventListeners.java`、`api/neoforge/FTBChunksEvent.java`、`FTBChunksNeoForge.java`）。
- FTB Library：`ftb-library-26.1.2.8-sources.jar`（`config/value/BaseValue.java`、`config/manager/ConfigManager.java`）。
- 本文所有"从未被赋值""无人读取""恒返回 true"的结论都是对上述源码全量 grep 的结果，不是推测。
