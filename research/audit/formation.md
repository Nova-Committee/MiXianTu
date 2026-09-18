# 阵法系统落地设计（对照 `research/18` 与当前工作区代码）

审计基准：MC 26.1.2 / NeoForge 26.1.2.99。
所有结论来自**直接读当前工作区代码**，行号对应本文件写入时的状态。本文件是"先记录、后动手"的持久化笔记。

> 变更记录
> - 2026-09-14：新增本文件。核对 `research/18_阵法系统设计.md` 的基座侧判断，**修订 3 处已过时结论 + 重写 A–E 五项缺口方案**。

## 0. 一句话结论

`research/18` 的**需求侧**（17 个阵法 / 4 类 / 编号 17 的重设计）继续有效，不必推翻。
但它的**基座侧技术判断有三处已经过时**，因为它写的时候假设的是"基座还没有区域能力、阵法接不上 ability"：

> 工作区的真相是——**阵法早就有自己的球面逐实体分发**（`FormationWorldTicker.executeEntityTickAction`），"
> 对谁生效"**已经是数据驱动的**。缺的不是区域选择器，而是**上下文里的阵法身份**。

按这个判断重新收敛，`tick_ability` 字段**不必新增**，`formation_x/y/z` **不该做成注册表变量**，领地保护**基座已经做了**。真正要补的是三件小事 + 一个正确的"离开范围"模型。

---

## 1. 现状核对：§2.1 的字段表是准确的

`research/18` §2.1 列的 10 个字段，与 `data/Formation.java:24-29` **逐项一致**：

| §18 字段 | 工作区 | 判定 |
| --- | --- | --- |
| `structure_template` | `structureTemplate`（`Identifier`，必填） | ✅ 一致 |
| `radius` | `radius`（`NumberProvider`，必填） | ✅ 一致 |
| `max_bonus` | `maxBonus`（`Map<Holder<Aura>, NumberProvider>`，默认空） | ✅ 一致 |
| `activation_costs` | `activationCosts`（默认空） | ✅ 一致 |
| `maintenance_costs` | `maintenanceCosts`（默认空） | ✅ 一致 |
| `activate_action` / `tick_action` / `deactivate_action` | 同名字段（`BlockAction`，默认 `no_op`） | ✅ 一致 |
| `entity_tick_action` | `entityTickAction`（`EntityAction`，默认 `no_op`） | ✅ 一致 |
| `aura_zone` | `auraZone`（`Optional<Holder<AuraZone>>`） | ✅ 一致 |

§1.3 的"三条运行时机制迁移要点"也**已经落地**：不再有临时实体（`FormationWorldAttachment` 按 controller 位置持久化）、不再有巨型 if-else（`formation` JSON 分派）、增益传递走 `grant_ability` + `source`。这一节可以从"迁移要点"改写成"已完成"。

§2.1 的两条 ✅ 也复核无误：

- **按距离衰减确实已数据驱动**：`FormationWorldTicker.java:76-79` 给每个实体传 `formation_radius` 与 `distance`。
- **上下文陷阱提醒仍然有效**：`tick_action` / `deactivate_action` 走 `FormulaContext.of(level)`（`:58`、`:43`、`:52`），确实只有 `zero` / `random`；`entity_tick_action` 才有实体与距离。

---

## 2. 三处已过时的判断

### 2.1 ❌ "单点最大改进 = 打通 `mxt:area`" —— 不成立

§18 的推论是"基座有区域选择能力但阵法接不上，所以加 `tick_ability` 指向 ability"。
问题在于，**阵法已经自带这套分发**：

```java
// FormationWorldTicker.java:68-81
private static void executeEntityTickAction(ServerLevel level, BlockPos controller,
                                            FormationInstance instance, Formation definition) {
    double radius = instance.radius();
    double radiusSquared = radius * radius;
    Vec3 center = controller.getCenter();
    for (Entity entity : level.getEntities(null, AABB.ofSize(center, radius * 2, radius * 2, radius * 2))) {
        double distanceSquared = entity.distanceToSqr(center);
        if (distanceSquared > radiusSquared) continue;      // ← 球面判定
        definition.entityTickAction().execute(entity, FormulaContext.of(entity, Map.of(
                "formation_radius", radius,
                "distance", Math.sqrt(distanceSquared))));
    }
}
```

也就是说"半径内对每个实体执行 X"**今天就能纯 JSON 表达**。§18 §2.2 描述的落差（"formation 没有任何字段能引用或复用 ability"）**不是**"对谁生效"的落差，而是**双实体语义**的落差：

| 能力 | `entity_tick_action` 能表达吗 |
| --- | --- |
| 半径内所有实体施加效果 | ✅ 能（今天就能） |
| 按距离衰减 | ✅ 能（`distance` / `formation_radius`） |
| 排除阵法所有者 | ❌ 不能（不知道阵主是谁，见 §2.4） |
| 传送到阵心 | ❌ 不能（没有阵心坐标，见 §3C） |
| **"施法者灵根克目标灵根"**（编号 17 的核心） | ❌ 不能（`mxt:element_overcomes` 是 `bientity_condition`，需要 actor + target 两个实体，而逐实体分发**只有一个实体**） |

真正需要 actor 的**只有最后一行**。为这一行引入 `tick_ability` + 一个"阵心上的实体"，代价远大于收益（见 §4）。

**附带证据：`mxt:area` 本身还不适合当阵法半径。**
`AreaTargetSelector.java:28-35` 的选择是 `actor.getBoundingBox().inflate(radius)` —— 这是**方盒**，不是球：

```java
Stream<Entity> entities = actor.level().getEntities(actor, actor.getBoundingBox().inflate(radius)).stream();
if (this.includeActor) return Stream.concat(Stream.of(actor), entities);
return entities;
```

它内部**不做距离过滤**（距离过滤是 `AbilityEventBridge.tickAuras:281-289` 在 aura 里另做的）。若阵法改用它，角落处的实际作用距离最远可达半径的 `√3 ≈ 1.73` 倍，与 `max_bonus` / `aura_zone` 的球面判定（`AuraService.java:219` 的 `distSqr <= r²`）**不一致** —— 同一个半径会出现"灵气加成是球形、伤害是立方形"的错位。

### 2.2 ❌ `formation_x/y/z` 不该是"公式变量"

`MxtFormulaVariables.java:43-47` 目前只注册了 5 个变量（`zero`/`random`/`caster`/`target`/`realm`），而 `formula_variables.md` 里写的 `formation_radius` / `distance` **根本不在注册表里** —— 它们是 `FormationWorldTicker` 通过 `Map.of(...)` 传的**显式上下文值**。这不是遗漏，是设计规则，`FormulaVariables` 自己的类注释写得很明白：

> Values that belong to no object — damage, a block position, an event payload — are not variables;
> callers keep those in the context's explicit value map.

阵心坐标正属于"belongs to no object"，而且 `FormulaContext.value()`（`FormulaContext.java:136-139`）**先查显式值再查注册表**：

```java
public double value(String name) {
    double explicit = this.explicit(name);
    return Double.isNaN(explicit) ? FormulaVariables.resolve(name, this) : explicit;
}
```

而 `NumberProvider.CODEC`（`NumberProvider.java:21-26`）接受字符串形式的表达式，`"formation_x"` 会被 `Expression` 解析成变量名，于是走上面的 `value()` —— **注册表那条路一分钱都不用花**。

做成注册表变量的代价反而是负的：

- 要在 `MxtFormulaVariables` 加一个 `FormulaVariable` 实现 + 注册；
- 会进全局名字索引（`FormulaVariables.lookup()`），于是**在阵法之外**求值 `formation_x` 会从"显式值缺失"退化成"未知名/产不出值"的诊断噪声；
- 要同步改两份文档。

结论：**改 `FormationWorldTicker` 里那个 `Map.of(...)` 就够了**，3 个 key。

### 2.3 ❌ "区域保护移出基座，交 FTB Utilities" —— 基座曾实现过宗门领地，现已删除

> 🔁 **本节已作废（删除宗门系统时更新）。** 基座曾有一套完整的宗门领地保护：`SectTerritoryEventBridge.java:34-50`
> 拦 `BreakBlockEvent` / `EntityPlaceEvent` / `PlayerInteractEvent.RightClickBlock`（`EventPriority.HIGH`），
> `:55-63` 判定未认领区块公开、已认领区块走阵主宗门的 rank 权限，认领 / 释放由
> `SectService.claimTerritory` / `releaseTerritory` 提供，数据存在 `MxtAttachments.SECT_TERRITORY`（`LevelChunk` 附件），
> 命令入口是 `/mxt sect claim` 与 `/mxt sect release`。
>
> 但这套实现完全建立在 `mxt:sect` 数据包注册表之上，而玩家**没有任何正式入口能加入宗门**
> （`SectService.join` 只被测试模组调用，测试数据的权限 id `mxt:claim` 还与代码硬编码的 `mxt:territory_*` 不一致），
> 于是它随 `sect` 注册表一起被整体删除。**基座现在不自带任何领地保护。**

按"基座没有领地保护"重新读 §3：

- 要拦草方格级别的破坏 / 放置 / 交互，只能靠外部领地模组（FTB Chunks 之类）或内容包自己实现。
- 《10 守家阵》原来说"一个 `formation` + 一次 `sect claim` 就能当守家阵，一行代码都不用补"——**不再成立**；
  阵法自身的九个开关仍然有效，但它们只是阵法的保护，不是领地系统。

> 仍然成立的一点：§3.3 的技术证据（旧模组依赖每 tick 生成临时实体，被取消 `EntityJoinWorld` 就失效）—— 但那描述的是**旧模组**，基座没有这个问题。

---

## 3. §2.3 A–E 逐条修订

| 编号 | §18 建议 | 修订后 | 依据 |
| --- | --- | --- | --- |
| **A** | 加 `tick_ability` 字段指向 ability | **不采纳**，改为"给 `entity_tick_action` 补上下文"（§4.1/§4.2） | §2.1：区域分发已存在；`tick_ability` 会引入 actor 实体问题，且与 `entity_tick_action` 语义重叠 |
| **B** | 加 `mxt:formation_owner` 条件 | **采纳**，语义定为"**当前正在评估的那个阵法**的所有者" | `FormationMemberEntityCondition` 现状是"拥有本维度**任意**阵法"，且是 `MapCodec.unit` 无参数，无法表达"这个" |
| **C** | 加 `formation_x/y/z` **公式变量** | **采纳，改形态**：作为 `entity_tick_action` 的**显式上下文值**，不进注册表 | §2.2 |
| **D** | 加 `entity_enter_action` / `entity_exit_action` | **采纳，但必须配套"来源对账"**，否则会漏撤销（§5） | `ABILITY_HOLDER` 是 `copyOnDeath()` 的**持久**附件，授予的能力不会自己消失 |
| **E** | `mxt:damage` 加 `damage_type` | **采纳方向，但改落点**：问题不止缺类型，更缺**攻击者**（§6） | `DamageAction` 只发 `damageSources().generic()`，无 attacker；且其类注释明确反对加字段 |

---

## 4. 修订后的核心设计：阵法携带者（carrier）

### 4.1 数值进 `FormulaContext`，对象进 `Context`

这是工作区自己已经定好的分工：

- **数值** → `FormulaContext` 的显式值表（`damage` / `block_x` 就是这么传的）；
- **对象** → `Context` 的扩展数据槽（`Context.set/get`，`Context.java:39-41`）。

而 `Context` 的扩展数据**今天就能穿透整棵行为/条件树** —— 这是本次设计能做得这么便宜的关键：

```java
// EntityAction.java:26-34
void execute(@NotNull EntityActionContext context);

default void execute(Entity entity, Context parent) {
    this.execute(parent.copyTo(new EntityActionContext(entity, parent.formula())));
}

// SequenceAction.java:19        → action.execute(entity, ctx)     ← 传的是父 Context
// IfElseAction.java:24          → ifAction.execute(entity, ctx)
// NotEntityCondition.java:17    → this.condition.test(entity, ctx)
```

`copyTo` 把扩展数据复制进新的子上下文，所以嵌套的 `mxt:sequence` / `mxt:if_else` / `mxt:not` / `mxt:and` 全都能读到。

**改动量为零 API 变更**：`EntityActionContext` 是 public 构造器，ticker 直接自己构造即可（`EntityAction.execute(EntityActionContext)` 就是抽象方法本身）。

### 4.2 携带者内容

| 字段 | 用途 |
| --- | --- |
| formation id / definition | 让条件与行为知道"是哪张阵法" |
| `ServerLevel` + controller `BlockPos` | 阵心、维度 |
| radius | 与 `formation_radius` 显式值同源 |
| `Optional<UUID> owner` | `mxt:formation_owner` 的判定源 |
| （可选）当前 tick | 行为做节流用 |

对应的 `entity_tick_action` 上下文变为：

- `FormulaContext` 显式值：`formation_radius`、`distance`（已有）+ **`formation_x` / `formation_y` / `formation_z`**（新增）；
- `Context` 扩展数据：`formation` → 携带者（新增）。

于是 §18 §2.3 C 想要的 JSON **一字不改**就能成立：

```json
{ "type": "mxt:teleport", "x": "formation_x", "y": "formation_y", "z": "formation_z" }
```

### 4.3 新增 `mxt:formation_owner`

```json
{
  "type": "mxt:not",
  "condition": { "type": "mxt:formation_owner" }
}
```

语义与边界，必须写进文档：

| 情形 | 结果 |
| --- | --- |
| 在阵法上下文中，实体 UUID == 携带者 owner | `true` |
| 在阵法上下文中，携带者 owner 为空（无主阵法） | `false` |
| **不在阵法上下文中**（普通技能、物品绑定条件里） | **`false`**（不是"拥有任意阵法"） |

**现有 `mxt:formation_member` 怎么处理？**
它现在的真实语义是"这个实体拥有本维度**任意一个** active 阵法"（`FormationMemberEntityCondition.java:22-23`），名字与行为不符。建议二选一，**不要**按 §18 的拆法把它改成"在范围内且通过成员判定"（那是第三种语义，且需要额外定义"成员判定"）：

1. **保留行为、改名**为 `mxt:owns_formation`（更贴切，且与新增的 `formation_owner` 形成"任意 vs 这个"的清晰对立）—— 代价是破坏现有数据包引用；
2. **保留 id、改文档**，只在文档里写清"任意 active 阵法"，并标注它与 `mxt:formation_owner` 的区别。

若模组尚未发布内容包，推荐 1；否则 2。

**`mxt:formation_is_owner`（§18 §2.4 建议 3）不采纳。** 它想要的是"非阵法所有者无法控制阵法"这条**交互期**规则，那属于阵盘物品/命令的检查点，不是实体条件。顺带指出一个现存缺口：`FormationPlateItem.useOn`（`FormationPlateItem.java:28-46`）**只校验阵盘组件，不校验所有权** —— 任何人拿到绑定某阵法的阵盘都能激活它。这是玩法决策，需要拍板。

---

## 5. D 的正确形态：进出钩子 + 来源对账

### 5.1 只加 enter/exit 是不够的

`MxtAttachments.java:37` + `:66-71`：`ABILITY_HOLDER` 走 `entity(...)`，即 `.serialize(CODEC).copyOnDeath()` —— **授予的能力是持久的**，玩家退出游戏、死亡、换维度都还在。所以：

- 玩家在增益阵里**退出游戏** → 没有任何 tick 会给他发 `exit` → **能力永久滞留**；
- 玩家在增益阵里**换维度** → 同上；
- 阵法被拆（结构破坏 / 灵力付不出）时，`deactivate_action` 是 **`BlockAction`，上下文是 `Level`**（`FormationWorldTicker.java:43`、`:52`），**看不到实体**，无法逐实体撤销。

因此 §18 §2.4 的第 3 问（"增益阵离开范围如何清理"）的正确答案不是"加两个钩子"，而是**加两个钩子 + 一套来源对账**。

### 5.2 对账原语已经存在

`AbilityAttachment.java:96` 有现成的：

```java
public boolean reconcileSource(Identifier source, Collection<Holder<Ability>> desiredAbilities)
```

语义正是"**这个 source 下只保留 desired，其余全部撤销**"。`AbilityEventBridge.java:230-231` 已经用它处理 curios 装备的来源。

所以设计是：

1. **阵法用带作用域的 source id**，例如 `mxt:formation/<namespace>/<path>` 或 `mxt:formation/<controller 的 long 编码>`；`grant_ability` 的 `source` 直接填它。
2. **每个阵法 tick，对范围内每个实体**：先 `reconcileSource(该阵法的 source, 当前应授集合)`，再执行 `entity_tick_action`。
3. **离场**：`entity_exit_action` 显式执行 + 兜底 `reconcileSource(source, Set.of())`。
4. **拆除/付不出灵力**：拆之前对**上一 tick 记录在册**的实体逐个跑一遍"第 3 步"。

### 5.3 记账集合放内存，不进存档

需要记住"上一 tick 谁在阵内"才能判断 enter/exit。**不要**把它塞进 `FormationInstance.Snapshot`：

- 它是瞬时状态，跨存档没有意义；
- 进存档会平白扩大 `formation_world` 附件体积；
- 重启后"里面所有人都是新进入"其实是**正确**行为（授予是幂等的）。

放一个 level 作用域的内存表即可（`Map<ServerLevel, Map<BlockPos, Set<UUID>>>`），阵法被移除时清对应项。

### 5.4 必须写进文档的三条边界

| 边界 | 行为 |
| --- | --- |
| 20 tick 粒度 | `FormationWorldTicker` 挂在 `gameTime % 20`，所以"穿过"（<20 tick 进出）与"同 tick 内进出"都观察不到 |
| 区块卸载 | `level.getEntities(...)` 只看已加载实体 → 卸载再加载会产生一次**假 exit + 假 enter** |
| 重启 | 记账表清空 → 阵内实体全体视为 enter |

这三条与用户已经接受的 `aura.entity_refresh_interval`（`research/17`）是**同一族取舍**：用可配置的延迟换掉每 tick 全量扫描。建议同样把阵法 tick 周期做成配置项，而不是硬编码 20。

### 5.5 顺带：现在没有办法「关掉」一个阵法

`FormationWorldService` **没有 `deactivate`**，命令系统里也没有阵法命令。于是阵法只有两条消亡路径：结构被破坏，或灵力付不出（`FormationWorldTicker.java:48-56`）。这与 §18 §5 缺陷 #4 记录的旧模组"`阵法开关` 只能开不能关"是**同一类问题换了个形式**。

而且 `FormationPlateItem` 在 controller 已占用时返回 `OCCUPIED` 失败 —— 也就是说阵盘对已激活的阵法**完全没有交互**。

建议补：阵盘对已激活的 controller = 拆除（校验所有权 + `FormationEvent.Deactivate`），并给 `/mxt formation` 一组只读诊断（list / info）。这与刚做完的命令系统改造天然契合（诊断节点留在 `/mxt` 下即可）。

---

## 6. E 的正确形态：伤害类型 + 攻击者

### 6.1 现状与既有决策冲突

`DamageAction.java:11-13`：

> Applies generic damage; **source-specific damage belongs in a dedicated code-owned action type.**

也就是说"不要给 `mxt:damage` 加 `damage_type` 字段"是**已经做过的决定**。全库只有两处发伤害（`DamageAction.java:22`、`DamageTargetBiEntityAction.java:19`），都是 `damageSources().generic()`，另外两处 `explode` 用的是 `explosion`。

### 6.2 更严重的缺口是没有攻击者

§18 只提了"伤害无元素通道"，但现状是**连攻击者都没有**：

- `mxt:damage` 打出来的伤害**无主**，击杀不记在任何人头上；
- 依赖攻击者的东西一律失灵：`mxt:damage_type` / `DirectDamageCondition` 这类条件的上游触发、死亡消息归因、怪物仇恨、天劫/反噬的判定。

对 1 落雷阵 / 15 一意水剑阵 / 16 赤魂炼狱阵 / 7 禁绝阵 来说，"谁打的"和"什么类型"**同等重要**。而 actor 恰好就是本次引入的携带者所提供的**阵主**。

### 6.3 建议落点

按既有决策，**加动作类型而不是加字段**，且与总纲 D1（统一伤害管线）合并：

| 方案 | 说明 |
| --- | --- |
| `mxt:damage` 保持不动 | generic、无主，作为"环境伤害"语义保留 |
| 新增**一个** `mxt:typed_damage`（或并入 D1 的统一伤害动作） | 字段：`amount` + `damage_type`（`Holder<DamageType>`）+ `attacker`（可选，取值来源：携带者的 owner / `caster` / `target`） |
| 双实体版 `mxt:damage_target` 同样需要 attacker | 否则"阵法打人"永远记不到阵主头上 |

`attackers` 的取值需要用 `Context` 扩展数据（携带者）或 `FormulaContext.caster()`，所以 **E 依赖 §4 的携带者**，排序上应在 A/B/C 之后。

---

## 7. 重排后的优先级

| 优先级 | 事项 | 改动量 | 解锁 |
| :--: | --- | --- | --- |
| **P0** | **C**：`entity_tick_action` 补 `formation_x/y/z` 显式值 | 极小（3 个 key） | 1、15、16 的"拉到阵心" |
| **P0** | **A′**：`entity_tick_action` 补携带者（`Context` 扩展数据） | 小（ticker 内构造 `EntityActionContext`） | 后续 B / D / E 的前置 |
| **P1** | **B**：`mxt:formation_owner` 条件 + 明确 `formation_member` 语义 | 小 | 全部 17 个的"区分敌我"，编号 17 |
| **P1** | 文档：阵法 tick 周期 / 区块卸载 / 重启三条边界；所有者离线行为；阵法数量上限；阵法持久化范围 | 无 | — |
| **P1** | **拆除路径**（阵盘对已激活 controller + `Deactivate` + `/mxt formation` 诊断） | 小 | 所有阵法的可控性 |
| **P2** | **D**：`entity_enter_action` / `entity_exit_action` + **来源对账** | 中 | 8 个增益阵的正确性 |
| **P2** | 阵法 tick 周期做成配置项 | 极小 | 与 `aura.entity_refresh_interval` 一致的取舍旋钮 |
| **P3** | **E**：typed damage + attacker，与总纲 D1 合并 | 中 | 1、7、15、16（依赖 A′） |
| **P3** | 编号 17 的重设计（五行相克） | 数据侧为主 | 依赖 B |

> §18 §2.4 的第 2 问（"`structure_template` 是否支持自动铺设"）**已经答完**：工作区是"玩家手搭结构 + 阵盘在视线点激活"（`FormationStructureValidator.TEMPLATE` 反查模板，controller 即模板原点），**没有自动铺设**。所以 15 个阵盘的"右键自动铺"玩法**不会**被保留，需要你拍板是否要新增一个"施工"动作。

---

## 8. 顺带发现的现存缺陷（本次重构前就存在）

| # | 缺陷 | 证据 |
| --: | --- | --- |
| 1 | ✅ **已删：`FormationWorldService.maintain`**（连同只服务于它的 `MaintainResult`）。复核发现它**从初始提交 `3a7a81b` 起就没有任何调用者** —— ticker 当时就自己内联了 `restore → FormationService.maintain → world.replace/remove` 三步。**更重要的是它的语义后来变得主动错误**：末尾的 `if (!result.maintained()) deactivate(...)` 会无条件拆除，而 `FormationEvent.UpkeepFailed` 存在的全部意义就是让监听器取消这个拆除。所以它无法靠"接上调用"复活，要复活就得把 ticker 那整段搬进来。`FormationWorldService` 现在只剩"激活"与"唯一拆除路径"，两半都不碰资源 | 原 `FormationWorldService.java:55-62`；历史见 `git show 3a7a81b` |
| 2 | ✅ **已删：`FormationInstance.active`** —— 原先只有 `maintain` 会写 `false`，而它一写 `false` 就立刻把条目从附件删除；ticker 也从不读 `active()`。于是附件里的 `active` **恒为 true**，`FormationMemberEntityCondition` 里那个判别是恒真装饰，而手改存档写 `"active": false` 会被完全忽略、照样扣维持费。现在**"在索引里"就是"活着"**，字段、`active()`、`deactivate()` 全部删除 | 原 `FormationInstance.java:52-66`、`FormationWorldTicker.java:47-61`；现见 `FormationInstance` 类注释 |
| 3 | **有维护消耗 + 无主 → 阵法活不过一个 tick**：`payer` 为 null 直接判定拆除 | `FormationWorldTicker.java:49-55` |
| 4 | **主人离线 → 阵法当场拆除**（同上路径，`level.getEntities().get(uuid)` 返回 null）。§18 §2.4 第 4 问问的正是这个，现在的答案是"拆掉" | 同上 |
| 5 | **没有阵法数量上限**，也没有每玩家上限 | 无相关代码 |
| 6 | `MxtEventBus` 的 `FormationEvent.Tick` 可取消，但取消只跳过 `tick_action` + 实体分发，**维护费照扣**（扣费在 `:48-56`，早于 `:57` 的取消判定） | `FormationWorldTicker.java:48-61` |
| 7 | `mxt:area` 是方盒不是球，与阵法/灵气的球面判定不一致 | `AreaTargetSelector.java:32` |
| 8 | `timer` / `toggle` / `resource` / `target_lock` 四个能力组件**无消费者** —— 直接影响 D 的备选方案（"给授予加计时器自动过期"今天做不到） | `docs/模块实现审计.md:174` |

> #2 的最终处置（2026，用户拍板走"删掉"）：**"补上消费点"这条路走不通** —— 它的消费点本该是 §5.5 的拆除路径，
> 但拆除路径恰恰是"移除条目"本身，实例一旦离开索引就再没有可读的读者。让它变成真开关需要它真的能挡住分派
> （ticker / `AuraService` / 命令 / 实体条件全都要补判别），而那是一件"阵法保留但暂停"的新玩法，与
> `UpkeepFailed` 的取消语义重复。删掉之后不可能再有人把它误当开关读。

---

## 9. 审计计划（测试模组）

沿用 `MxtTestMod.verifyFormationTemplate` 的 ServerStartedEvent 审计风格，本次设计可全部在服务端断言：

| 断言 | 内容 |
| --- | --- |
| 上下文完整性 | 激活一张测试阵法，在 `entity_tick_action` 里用 `mxt:js` 或专门探针读出 `formation_x/y/z`、`formation_radius`、`distance`、携带者 owner，逐值比对 controller 与实体位置 |
| `formation_owner` 语义 | 阵主实体 → true；同维度另一个阵法的阵主 → **false**（区分"任意"与"这个"）；无主阵法里的任何实体 → false；**阵法外求值 → false** |
| 携带者穿透 | 把探针塞进 `mxt:sequence` → `mxt:if_else` → `mxt:not` 三层嵌套，确认扩展数据仍可读（这是 §4.1 的核心假设，必须有断言） |
| 来源对账 | 授予一个带 `source` 的能力 → 移出范围 → `ABILITY_HOLDER` 中该 source 清空，且**其他 source 的授予不受影响** |
| 拆除清账 | 有维护消耗的阵法付不出灵力 → 附件条目移除 + `Deactivate` 发布 + 阵内实体该 source 清空 |
| 方盒 vs 球面 | 在阵心对角线 `radius * 1.4` 处放实体：阵法**不应**影响它（守住球面语义，防止将来误改回 `mxt:area`） |
| 20 tick 粒度 | 连续两个 `gameTime % 20 != 0` 的 tick 内 `entity_tick_action` **不执行**（把当前隐含行为固化成断言） |

---

## 10. 待你拍板

1. ~~**`mxt:formation_member` 改名还是改文档？**（§4.3，影响现有数据包兼容性）~~ → **已拍板：保留 id、改文档**，并在
   `FormationMemberEntityCondition` 类注释里写明它与 `mxt:formation_owner` 的区别（见 §18 §2.3-B、步骤 4）。
2. **阵盘是否要校验所有权？**（§4.3，现在是"谁拿到谁能开"）
3. **要不要"自动铺设结构"这个玩法？**（§7 末尾，决定 15 个阵盘的手感）
4. **增益阵要"能力"还是"药水效果"？** 若走 `grant_ability`，D + 来源对账是**必须**的（§5）；若维持旧模组那种"打标记自然过期"，则 D 可以不做，但失去"属性由本体发放"的收益。
5. **阵法 tick 周期是否照 `aura.entity_refresh_interval` 做成配置项？**（§5.4）
6. **拆掉 §18 §3 整章？** 领地保护已是既成事实，留着会误导后来者（§2.3）。

---

## 附录：本文件引用的工作区位置

| 位置 | 内容 |
| --- | --- |
| `src/main/java/com/iafenvoy/mxt/data/Formation.java:24-29` | `formation` 字段表 |
| `src/main/java/com/iafenvoy/mxt/runtime/formation/FormationWorldTicker.java:37-81` | 20 tick 周期、维护、球面实体分发、上下文显式值 |
| `src/main/java/com/iafenvoy/mxt/runtime/formation/FormationInstance.java:38-104` | 不变式（`flatXmap`）、`maintained`；`active` 已删除 |
| `src/main/java/com/iafenvoy/mxt/runtime/formation/FormationWorldService.java:47-59` | 无调用者的 `maintain` |
| `src/main/java/com/iafenvoy/mxt/data/condition/builtin/entity/FormationMemberEntityCondition.java:14-24` | "任意阵法"语义 |
| `src/main/java/com/iafenvoy/mxt/data/ability/target/AreaTargetSelector.java:28-35` | 方盒选择、128 上限、默认排除 actor |
| `src/main/java/com/iafenvoy/mxt/data/ability/type/AuraAbilityType.java:9-13` | `interval` / `radius` |
| `src/main/java/com/iafenvoy/mxt/runtime/ability/AbilityEventBridge.java:266-294` | aura 逐 tick 分发 + 球面过滤 |
| `src/main/java/com/iafenvoy/mxt/util/formula/FormulaContext.java:136-149` | 显式值优先 |
| `src/main/java/com/iafenvoy/mxt/registry/MxtFormulaVariables.java:43-47` | 只有 5 个公式变量 |
| `src/main/java/com/iafenvoy/mxt/data/context/Context.java:39-61` | 扩展数据槽与 `copyTo` |
| `src/main/java/com/iafenvoy/mxt/data/action/EntityAction.java:26-34` | `execute(Entity, Context)` 穿透路径 |
| `src/main/java/com/iafenvoy/mxt/data/action/builtin/entity/DamageAction.java:11-22` | generic 伤害与既有决策 |
| `src/main/java/com/iafenvoy/mxt/attachment/AbilityAttachment.java:96` | `reconcileSource` |
| `src/main/java/com/iafenvoy/mxt/registry/MxtAttachments.java:37,66-71` | `ABILITY_HOLDER` 序列化 + `copyOnDeath` |
| ~~`src/main/java/com/iafenvoy/mxt/runtime/sect/SectTerritoryEventBridge.java:34-63`~~ | 领地保护曾已实现，**该文件已随宗门系统删除**（见 §2.3） |
| `src/main/java/com/iafenvoy/mxt/item/FormationPlateItem.java:28-46` | 激活路径、无所有权校验 |
| `docs/模块实现审计.md:174` | 四个无消费者的能力组件 |
