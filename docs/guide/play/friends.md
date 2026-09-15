---
title: 好友与敌我识别
---

### 模块定位

好友系统回答一个问题：**"这个实体算不算我的人"**。它本身不改变任何战斗或效果行为，只提供判断结果；阵法用它做队友保护（`hostile` 阵法不打好友），技能的友伤过滤等后续接入点也从这里取值。

每个玩家有一份自己的好友名单，判断是有方向的：A 把 B 当好友，和 B 把 A 当好友是两件事。需要"互相都算"的调用方自己问两次。

### 完成状态

已完成：附件、命令、TriState 判断事件、`mxt:friend` / `mxt:formation_ally` 两个数据包条件、阵法的敌我判断接入、KubeJS 事件挂载、服务端审计。

### 两种好友

名单分成两条，区别只有一条：**下一次登录时还在不在**。

| 名单 | 添加方式 | 生命周期 |
| --- | --- | --- |
| 临时 | `/friend add <玩家>` | 存档里也有一份，但**玩家登录时被清空**，因此重登、重启后失效 |
| 永久 | `/friend permanent add <玩家>` | 写进存档并保留 |

两条都进 Codec，所以**死亡重生不会丢临时好友**（NeoForge 的 `copyOnDeath` 是一次序列化加反序列化，字段不进 Codec 才会在重生时被丢掉）。结束一次"本次登录"的是登录事件本身：`FriendSessionBridge` 在玩家上线时清空临时名单，而不是在下线时清——下线钩子会漏（崩溃、被杀的进程、跨服转移），登录不会。

代价是：玩家离线期间临时名单仍然躺在存档里，直到他下次登录才被清掉。这份数据离开玩家实体没有意义（判断需要阵主/本人实体在线），所以只是文件里多几行。所有匹配按 UUID 进行，名字只用于显示，改名不会导致判断失败。

同一个玩家只会出现在一条名单里。`/friend permanent add` 对临时好友是**升级**（从临时挪到永久），不会两边同时存在；`/friend add` 遇到永久好友会直接拒绝；手改存档把同一玩家写进两条时，以永久那条为准。

### 命令

| 命令 | 作用 |
| --- | --- |
| `/friend`（= `/mxt friend`） | 输出帮助，每一行可点击填入聊天栏。 |
| `/friend list` | 列出两条名单，人数与名字；名字可点击填入移除指令。 |
| `/friend add <玩家>` | 添加临时好友。 |
| `/friend remove <玩家>` | 移除临时好友；对永久好友会拒绝并提示改用下面那条。 |
| `/friend permanent add <玩家>` | 添加永久好友；临时好友会被升级。 |
| `/friend permanent remove <玩家>` | 移除永久好友。 |

顶层 `/friend` 别名受 `config.mxt.server.commands.friend` 控制，默认开启；`/mxt friend` 始终完整。帮助与列表里的可点击指令会按当前配置选择根节点，所以关掉别名之后给出的建议仍然能执行。

**点击行为是"填入聊天栏"而不是"直接执行"**（`ClickEvent.SuggestCommand`）：好友要靠玩家输入名字，帮助无法预知，所以点一下只是把指令连同结尾的空格放进输入框，光标停在名字该在的位置。`/friend remove` 的补全只列出**当前真的能移除的名字**（临时名单与永久名单各自对应自己那条命令），不会给出一个点了必然被拒绝的选项。

`<玩家>` 走的是原版档案缓存，不是在线玩家列表，因此**对方离线也能添加和移除**；这也意味着选择器语法（`@a` 等）是合法的，命令会要求恰好解析出一个玩家。

### 判断流程

`FriendService.isFriend(judge, candidate)` 的答案按顺序来自两处：

1. **`FriendEvent.Relation` 事件**：其他模组可以给出自己的判断。事件同时带**判断者的 UUID** 和（可选）实体，因此数据保存在服务端管理器里的来源（队伍、阵营等）**在判断者离线时也能作答**。
2. **内置好友系统**：事件没有表态（`DEFAULT`）时由它兜底。它是**最后一环，自己不再发事件**。判断者在线就读实体上的名单，离线就读下面的镜像缓存。

事件的结论是一个原版 `TriState`：

| 取值 | 含义 |
| --- | --- |
| `DEFAULT` | 没有意见，**交给内置好友系统**。事件初始值就是这个。 |
| `TRUE` | 算自己人。 |
| `FALSE` | 不算，**即使名单里写着算**。 |

写入是直接覆盖的，所以最后一个写入者生效，用 `EventPriority` 排序。`FriendService.builtin(judge, candidate)` 提供"只看内置系统"的答案，可以在监听器里调用而不会递归回事件——**监听器里不要调 `identify`**，那会在自己内部再发一次事件。

**监听器应当只在"按我自己的规则算自己人"时写 `TRUE`**，其余情况保持 `DEFAULT`。写 `FALSE` 是在声明"这两人不是盟友"，它会覆盖包括玩家自己名单在内的所有其他来源——只有当你的来源确实知道这对关系是敌对的，而不是"我不认识他们"时才该这么写。

名单判断里有两条固定规则：

- **自己算自己的好友**（按 UUID 比较，所以只凭 id 提问也算），调用方不必先判自伤/自指。
- **不是玩家就没有名单**：一个在线的非玩家判断者（比如带阵法的怪）没有名单可读，因此按"谁都不是它好友"作答；要让自定义生物有自己的敌我观，就走事件。

判断不会因为"问一句"而创建附件：查询走 `getExistingData`，一个从未用过好友系统的实体不会被凭空挂上名单。判断本身也不做缓存，每个 tick 对同一对实体反复询问的调用方应当自己记住结果。

### 离线镜像缓存

名单存在玩家实体上，玩家一离线就没了——而"阵主下线后阵法还立着"正是最需要判断的时候。所以内置系统维护一份**纯内存的镜像** `FriendCache`：每条记录是一个玩家 UUID 和他的好友 UUID 集合。

刷新时机只有**一场会话的两端**，这也是它不需要每个写入点都记得去同步的原因：

| 时机 | 做什么 |
| --- | --- |
| 登录（`PlayerLoggedInEvent`） | 先清空临时名单，**再**把镜像更新为清空后的结果 |
| 登出（`PlayerLoggedOutEvent`） | 把镜像更新为最终结果 |

玩家在线期间镜像的陈旧是无害的——那时读的是实体上的实时名单——而它开始被使用的时刻（下线之后）之前，登出那次刷新刚好补上。因此**以后新增任何写好友数据的路径都不需要知道这个类的存在**。代价是：进程重启后镜像清空，要等玩家下次登录才重新填充；在那之前对该玩家的提问仍会得到"无人能回答"。

记录是"该玩家有这些好友"，包括**空集合**——"这人没有好友"和"没见过这人"是两个不同的答案，只有后者才是真的没人能回答。

### FTB Teams（可选）

装了 FTB Teams 时，**队伍成员与队伍盟友自动算作"自己人"**，通过 `FriendEvent.Relation` 接进上面的流程（软依赖：没装 FTB Teams 时这段代码完全不加载）。

FTB Teams 的"盟友"是**队内等级**，不是"结盟的另一支队伍"——没有 `getAllies()` 这类 API，盟友就是队内 rank 为 `ALLY` 的外来玩家。判定基准照抄 FTB Chunks 自己的口径（成员及以上，或恰好 `ALLY`），再按配置放开：

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `config.mxt.server.friends.ftb_teams_ally` | 开启 | 把 `ALLY` 等级算作队友 |
| `config.mxt.server.friends.ftb_teams_invited` | **关闭** | 把 `INVITED` 等级算作队友 |

**正式队员（`MEMBER` 及以上）永远算队友**，不设开关——在队里就是在队里。两个开关只管"外来者"的两档。

**别用 `isAllyOrBetter()`** —— 它是 `power >= ALLY(50)`，而 `INVITED` 是 `75`，并且 `getRankForPlayer` 对 `free_to_join`（自由加入）的队伍会给**任何陌生人**返回 `INVITED`；用它会把这种队伍里所有人都变成友军。这也正是 `INVITED` 那一项默认关闭的原因：打开它，自由加入的队伍就等于"全服皆友"。另外 `TeamRank.ENEMY` 虽然存在，但 FTB 全代码库没有任何地方给它赋值，所以没有"标记敌人"这种东西可用。

这个来源**只写 `TRUE`**：FTB 知道谁是自己队员和盟友，但不知道玩家手填的好友名单，写 `FALSE` 会覆盖名单而不是与之合并。它读的是管理器内存里的队伍数据（按 UUID 索引），所以**阵主离线时也能回答**；正因为只是两次 map 查询，这里没有做缓存，也就不需要订阅 FTB 的队伍变更事件。

队伍在玩家**第一次登录时**创建，所以从未进过服的玩家没有队伍（`Optional.empty()`），这个来源对他不表态。

### 数据包条件

| 条件 | 类型 | 含义 |
| --- | --- | --- |
| `mxt:friend` | 双实体条件 | `actor` 把 `target` 当自己人 |
| `mxt:formation_ally` | 实体条件 | 该实体是**当前阵法**阵主的好友；阵法之外、或阵法没有记录阵主时恒为 `false`。阵主只是离线不属于后者——判断仍会按 UUID 问事件。 |

`mxt:friend` 用在有双实体条件槽的地方，最典型的是技能的 `target_condition`：

```json
"target_condition": { "type": "mxt:not", "condition": { "type": "mxt:friend" } }
```

`mxt:formation_ally` 用在阵法的逐实体行为里（那些行为的条件槽是实体条件，没有第二个实体可以和该实体配对，所以由阵法补上阵主这一半）。例如同一座阵法伤敌而治疗友军：

```json
"entity_tick_action": {
  "type": "mxt:if_else",
  "condition": { "type": "mxt:formation_ally" },
  "if_action": { "type": "mxt:heal", "amount": 1 },
  "else_action": { "type": "mxt:damage", "amount": 2 }
}
```

阵法还有一个更省事的入口：`hostile: true` 的阵法由运行时自动跳过好友，见[阵法](../datapack/formation#敌我判断队友保护)。两个条件都不受服务端配置影响——配置只决定自动过滤做不做。

### 接入示例

```java
@SubscribeEvent
public static void onRelation(FriendEvent.Relation event) {
    if (!(event.candidate() instanceof Player candidate)) return;
    // 判断者可能离线，所以按 id 走；需要实体时才取，取不到就交给别的来源。
    Player judge = event.judge().filter(Player.class::isInstance).map(Player.class::cast).orElse(null);
    if (judge == null) return;
    Holder<Sect> mine = judge.getData(MxtAttachments.SECT).sect().orElse(null);
    Holder<Sect> theirs = candidate.getData(MxtAttachments.SECT).sect().orElse(null);
    // 同宗门算自己人；其余情况不表态，交回好友名单。
    if (mine != null && mine.equals(theirs)) event.setResult(TriState.TRUE);
}

public static boolean mayHarm(Entity attacker, Entity victim) {
    return !FriendService.isFriend(attacker, victim);
}
```

监听器只需要在**有意见**时写结果。什么都不做（保持 `DEFAULT`）等价于"按名单来"，而不是"否定"。

脚本侧对应 `MxtEvents.friendRelation`：`getJudgeId()` 永远有值，`getJudge()` 在判断者离线时返回 `null`（`hasJudge()` 可先判），另有 `getCandidate()` / `getResult()`，用 `setFriend(true|false)` 表态、`abstain()` 交回名单。因为好友查询比生命周期事件频繁得多，**没有脚本监听时这条转发会被直接跳过**，不会为每次查询构造包装对象。

### 服务端/客户端边界

好友附件**不同步到客户端**：客户端不读它，而一次同步出来的快照在下一次登录清空临时名单之后就过期了，同步一份会立刻说谎的数据没有意义。因此客户端拿不到好友信息，任何界面显示都需要模组自己走网络层。

### 测试与故障排查

服务端审计覆盖：两条名单都能过 Codec（死亡不掉临时好友）、真实登录事件清空临时名单且不动永久名单、手改存档把同一玩家写进两条时以永久为准、`add`/`remove` 的状态机、事件覆盖与 `DEFAULT` 回退、只凭 id 提问时"自己算自己"与"没人回答"两种结果、镜像在登录时填充 / 登出时刷新 / 不跟随会话中途的改动、非玩家判断、FTB Teams 缺席时该来源保持沉默且两个等级开关各读各的条目、`/mxt friend` 四条写命令各自落到正确的名单上、两个数据包条件按 id 解码后可用、以及 `hostile` 阵法只跳过阵主与好友、配置关闭后恢复无条件生效、**阵主无法解析时能被识别的照旧按判断处理、没人能识别的整座阵法停火**。

排查时的常见误区是"临时好友没生效"：先确认不是在**重登或服务器重启**之后问的——那正是它被设计成会消失的时候（重生不会）。另外注意 `FriendAttachment` 的两条名单都以 UUID 匹配，名字只用于显示，改名不会导致匹配失败。
