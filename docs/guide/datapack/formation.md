---
title: 阵法、锻造与炼丹
---

`formation` 定义一座阵法：结构、半径、资源消耗、生命周期行为，以及范围内逐实体执行的行为。运行时阵法还可以提供临时灵气覆写与灵气上限加成。

| 字段                       | 类型                              | 默认          | 说明                            |
|--------------------------|---------------------------------|-------------|-------------------------------|
| `structure_template`     | Identifier                      | 见下          | 结构模板；controller 即模板原点          |
| `structure`              | `List<RequiredBlock>`           | 见下          | 内联结构；controller 即偏移原点          |
| `radius`                 | NumberProvider                  | **必填**      | 球形作用半径                        |
| `max_bonus`              | `Map<resource, NumberProvider>` | `{}`        | 范围内区块的灵气上限加成（重叠取最高）           |
| `activation_costs`       | `List<ResourceCost>`            | `[]`        | 激活消耗                          |
| `maintenance_costs`      | `List<ResourceCost>`            | `[]`        | 每 20 tick 的维持消耗；**阵法内方块供的灵气先抵扣**，缺口由阵主支付，付不出即拆除（可被拦截，见下） |
| `activate_action`        | Block Action                    | `mxt:no_op` | 激活时                           |
| `tick_action`            | Block Action                    | `mxt:no_op` | 每 20 tick，上下文是 **Level**      |
| `deactivate_action`      | Block Action                    | `mxt:no_op` | 拆除时，上下文是 **Level**            |
| `entity_tick_action`     | Entity Action                   | `mxt:no_op` | 每 20 tick，对半径内**每个**实体         |
| `entity_enter_action`    | Entity Action                   | `mxt:no_op` | 实体进入半径时                       |
| `entity_exit_action`     | Entity Action                   | `mxt:no_op` | 实体离开半径、或阵法被拆除时                |
| `aura_zone`              | aura_zone                       | 无           | 高优先级运行时灵气覆盖                   |

## 结构：二选一

`structure_template` 与 `structure` **必须且只能给一个**，两个都给或都不给都会让定义加载失败。

**优先用 `structure`。** 阵法要表达的通常是「阵基 + 几根阵旗」这种少量固定位置，内联写法已经够用，而且它在数据包加载时就把期望值解析完并固定下来——每次校验只是每个方块一次 `getBlockState`。`structure_template` 只在布局真的复杂到无法逐格列出时才值得用。

- **`structure`（内联，首选）**：无需 `.nbt` 资源，偏移量直接写在定义里。

  ```json
  "structure": [
    { "offset": [0, 0, 0], "state": "minecraft:gold_block" },
    { "offset": [0, 0, -8], "state": "mypack:wood_flag" }
  ]
  ```

  `offset` 相对 controller。`state` 可以直接写方块 id（取默认状态）；需要指定属性时才用原版的 `{ "Name": "...", "Properties": { ... } }` 写法——能用一个 id 表达的状态在导出时也会写回 id。

- **`structure_template`**：引用原版结构模板（`data/<命名空间>/structure/<路径>.nbt`，与结构方块、`/place structure` 共用同一批文件）。适合大型或复杂布局。

  代价有两层：

  1. **每次校验都要重新序列化并解析一遍模板**，另加每方块一次方块状态注册表查询——20 tick 一次、每座阵法都来一遍，所以别拿它描述几个坐标。
  2. 模板里**所有方块都必须逐个对上**，除了空气。

  > **空气不参与校验。** 用结构方块保存时，它会记录整个包围盒，空位也作为空气条目写进 `.nbt`；如果要求这些格子保持空气，旁边落一根火把或者掉一个方块就会把阵法废掉。所以模板里的空气条目表示"这里不关心"，不是"这里必须是空气"。反过来说，**模板只能约束"什么必须在"，不能约束"什么必须不在"**——需要"此处必须空着"的判定只能靠条件自行实现。
  >
  > 注意这与原版**放置**模板的行为正好相反：`placeInWorld` 会把空气覆盖到世界上，因为结构复现的是整个体积；阵法只断言自己的阵旗还立着，是个更弱的问题。

## 逐实体行为的上下文

`entity_tick_action` / `entity_enter_action` / `entity_exit_action` 对每个实体各执行一次，可以读到：

| 名称                                   | 位置    | 含义                                        |
|--------------------------------------|-------|-------------------------------------------|
| `formation_radius`                   | 公式显式值 | 阵法半径                                      |
| `distance`                           | 公式显式值 | 该实体到阵心的距离                                 |
| `formation_x` / `formation_y` / `formation_z` | 公式显式值 | 阵心坐标，可直接喂给 `mxt:teleport`                  |
| 阵法携带者                                | 上下文数据 | 记录"是哪座阵法、阵主是谁、阵心在哪"，由 `mxt:formation_owner` 消费 |

`tick_action` / `deactivate_action` 的上下文是 **`Level`**，只有 `zero` / `random` 两个变量 ——
**不要在它们里面写依赖实体状态的公式**。

## 阵法所有者

- **`mxt:formation_owner`** —— 该实体是**当前正在评估的这座**阵法的所有者。阵法之外恒为 `false`。
- **`mxt:formation_member`** —— 该实体拥有本维度**任意**一座在册阵法。与上一条是不同的问题，不要混用。

放置者在阵盘激活时记录。**有维持消耗的阵法在阵主离线时会被拆除**（取不到付费者）——除非有脚本取消 `UpkeepFailed`
让它撑过去。

`mxt:formation_owner` 目前**只排除阵主本人**。队友/宗门成员的保护尚未设计，接入点在
`FormationRelations`（见其 `TODO(队友保护)`），且不会改动 `mxt:formation_owner` 的语义。

## 周期与三个事件

一切以 **20 tick** 为周期，一个周期会依次发三个事件（KubeJS 里同在 `formation` 组，用 `isCancellable()` / `getPhase()` 区分）：

| 事件             | 可取消 | 含义                                                    |
|----------------|-----|-------------------------------------------------------|
| `Tick`         | ❌   | 维持费已经结算完。**观察点**：需要「每个付过费的周期都发生点什么」就挂这里 |
| `TickEffects`  | ✅   | 只挡 `tick_action` 与逐实体行为。取消**不退费** —— 阵法还立着，也还在被维持 |
| `UpkeepFailed` | ✅   | 付不出维持费。取消 = 这一周期不付也不做事，但阵法**保留**；不取消才拆除     |

拆分的原因：`Tick` 从前是可取消的，而它触发时钱已经花了，于是「取消」在一句话里同时意味着"什么都不发生"和"照扣钱"——
一个无条件取消的监听器就能零效果地掏空付费者。现在两个含义各有一个明确的家。

## 范围与时间粒度

- 范围是**球**，不是方盒：方盒角落处不会生效。
- 一切以 **20 tick** 为周期，这也是进出判定的粒度：20 tick 内穿过阵法不会被观察到。
- 区块卸载再加载会产生一次假的"离开再进入"；服务器重启后阵内实体一律视为新进入。
  因此 `entity_enter_action` **必须是幂等的**。

## 释放阵法授予的能力

`mxt:grant_ability` 的 `source` 建议取该 `formation` 自己的 id 对应的 `mxt:formation/<命名空间>/<路径>`。
基座会在实体离开、阵法拆除、以及玩家脱离阵法时对账这个 source 并撤销它的全部授予 ——
否则能力会永久留在实体身上（`ability_holder` 是持久附件，而 `deactivate_action` 的上下文看不到实体）。
不遵守这个约定不会出错，只是失去自动清理。

## 阵法吞噬地脉（维持费的第一个来源）

**阵法半径内的方块灵气发射器不再向环境供气，改为全部供给阵法。** 阵法用这些灵气支付自己的
`maintenance_costs`，缺口才由阵主出；如果阵法自己供的灵气就够，**阵主一分钱都不用出**（甚至不需要
持有那种资源）。

规则要点：

- 判定范围就是阵法自己声明的 `radius` 球，没有第二套定义。
- 被吞的方块会从**环境灵气里彻底消失** —— 它们不再参与"这里有多少灵气"的解析，也不会被其他查询共享。
  所以阵法是个真正的灵气黑洞，建在灵脉上会把那片地脉抽干（地脉本身仍按 `regen_per_tick` 恢复）。
- **按资源类型抵扣，不通用**：阵法里全是火属性灵气，抵不掉一笔灵力账单。不匹配的灵气既不给环境、
  也不给阵法，就是被吞掉了。
- **不设存量**：本周期吞到的灵气直接抵本期维护费，超出的部分不返还、不留到下期。
- 只有**阵主**能作为付费者（沿用现有模型）。阵主离线时，阵法仍会因为"付不出缺口"而被拆除 ——
  除非缺口被自家灵气完全覆盖，那时根本不需要付费者。
- **可选：连地皮的自然灵气也一起抽。** 服务端配置 `formation_plate.draws_environment`（默认**关闭**）
  打开后，阵法**所在位置**解析出的环境灵气也会参与抵扣。注意两项：
  - 只算**自然环境**（群系/维度/人工区域以及该处的场地灵气），阵法内发射器贡献的那一份会被扣掉，
    不会重复计入；
  - 它让阵法比"站在什么之上"更便宜 —— 灵气浓郁处即使不摆发射器也可能白跑，这是另一套平衡。

配套的引擎要求：**阵法激活或拆除时会让半径覆盖到的区块重算方块灵气**（否则被吞的方块会继续供环境，
阵法那一份就等于白拿）。重算是排队执行的，在下一个 `block_aura_tick_interval` 边界发生。

> 想让阵法完全靠自己运转，就在半径内摆足够多的发射器（例如 `mxt:spirit_stone_block`）。

## 灵气覆写

`aura_zone` 让这座阵法**替换**阵地所在位置的灵气解析结果（优先级高于群系 / 维度 / 人工区域）：

- 覆盖通过可取消的 `AuraZoneEvent.Override` 发布 —— 取消它就退回静态结果，阵法仍然立着。
- 覆盖只在**阵法在册期间**生效，拆除后立刻退回静态解析。
- 灵气答案是**按 tick 记忆化**的，所以同一个 tick 内激活的阵法要到下一个 tick 才影响灵气查询。

`max_bonus` 给灵气池加上限，但有一个必须知道的耦合：**它只随着 `aura_zone` 的覆盖一起应用**。
上限是加在「覆盖后选中的那个 zone」所提供的资源上的，因此：

- 没有 `aura_zone` → 没有覆盖 → `max_bonus` 无处可加。
- 有 `aura_zone` 但该 zone 不提供某个资源 → 对这个资源加不上（不会凭空创造资源）。

两者都已在服务端审计里断言。

## 激活与拆除

阵盘（`mxt:formation_plate`）是唯一能把阵法带进世界的物品：它的 `mxt:formation_plate` 组件里存着一个 `formation` 引用，
右键方块时由 `FormationWorldService` 结算。同一块阵盘就是开关 —— 对着阵心右键是激活，对着已激活的阵心右键是拆除
（需为阵主或管理员）。

**绑定阵法**：用 `/mxt formation bind <formation>` 写入主手的阵盘（需要 gamemaster 权限），或在物品组件里直接指定：

```mcfunction
give @s mxt:formation_plate[mxt:formation_plate={formation:"mypack:green_shade_array"}]
```

没有绑定的阵盘右键会提示「阵盘尚未绑定阵法」，不会消耗任何东西。激活失败时会说明原因（结构不匹配 / 资源不足 / 位置已占用），
不再只回一个内部枚举名。

### 阵盘白名单

`mxt:formation_plate` 组件有两个独立字段：

| 字段 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| `allowed` | `List<ID 或 #标签>` | `[]` | 这块阵盘**允许激活哪些**阵法。属性属于物品本身，同一 ID 的每一份都一样 |
| `formation` | 阵法 ID | 无 | 这一份**当前选中**哪一座；必须属于 `allowed` 才可用 |

```mcfunction
# 只允许两座具体阵法
give @s mxt:formation_plate[mxt:formation_plate={allowed:["mypack:thunder_array","mypack:ice_array"],formation:"mypack:thunder_array"}]

# 用一个标签把同组阵法交给多块阵盘（推荐）
give @s mxt:formation_plate[mxt:formation_plate={allowed:["#mypack:wood_arrays"],formation:"mypack:green_shade_array"}]
```

标签文件放在 `data/<命名空间>/tags/mxt/formation/<路径>.json`，写法与物品标签一致：

```json
{ "values": ["mypack:green_shade_array", "mypack:spirit_gathering_array"] }
```

`allowed` 为空是**歧义情况**，所以做成配置项：默认「不限制」（兼容所有旧阵盘），把服务端配置
`formation_plate.empty_plate_allows_all` 关掉后，空 `allowed` 表示**什么都不允许**，必须显式列出。
`/mxt formation bind` 的 Tab 补全只列出这块阵盘允许的阵法；白名单把它们限定住，补全也就有了上界。

**白名单只在写入前和激活前生效**，都先于任何资源消耗：`bind` 拒绝不在名单里的阵法且**不改动物品**；
若某块阵盘的 `formation` 不在自己的 `allowed` 里（手改存档、或配置改动导致），右键会提示
「阵盘不允许激活它所绑定的阵法」而不是照常激活 —— 这是一种显式配置，不是损坏的物品。

**阵心容错**：点击位置周围 3×3×3 内会寻找最近一个满足结构的阵心。点歪一格不会失败；点击位置本身有效时永远优先取它，
所以贴着一个可当阵心的方块点永远不会把激活挪到别处。拆除走同一次查找。

> 注意这个窗口只有 3 格宽，所以它只对**紧凑布局**真正有用：一个位置要能当阵心，必须从它开始就能满足整份结构，
> 因此跨度超过 1 格时只有原阵心能命中。这正是「点方块偏一格」这个真实约束对应的范围。

## 诊断

`/mxt formation list` 列出当前维度所有已激活阵法；`/mxt formation info` 列出覆盖玩家所在位置的阵法；
`/mxt formation bind` 是这棵子树里唯一的写操作。三者都有顶层别名 `/formation ...`
（可用 `config.mxt.server.commands.formation` 关掉别名，`/mxt formation` 始终完整）。

`forging_blueprint` 定义输入、锻造步骤、偏移范围、质量阈值和失败结算；`forging_method` 定义每次操作的消耗、条件与锻打音效。

`alchemy_recipe` 使用原版配方系统和炼丹环境条件，材料、灵气和结果由数据包定义。
