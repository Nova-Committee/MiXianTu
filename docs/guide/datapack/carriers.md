---
title: 载体物品：哪件物品装哪份定义
---

数据包里的定义最后总要落到某个**物品堆**上。本页只回答一件事：**基座里哪些物品能直接"指名一份定义"、读的是哪个键、写下去之后怎么才生效**。字段级权威仍是[数据包格式](../../数据包格式.md)，本页不重复字段表，字段怎么写去那边查。

## 三种投放方式

| 方式 | 这份引用由谁写 | 有没有专属物品 | 例子 |
| --- | --- | --- | --- |
| **专属载体 + 堆上的组件** | 物品那一堆自己带组件 | 有 | 灵根、体质、功法玉简、丹药、符箓、阵盘、秘境令牌、契约卷轴、丹炉核心与外壳 |
| **定义用 `items` 认领** | 定义那一侧写 `items`（`ItemMatcher`） | 没有，任何物品都行 | `artifact`、`item_aura`、`currency`、`spirit_herb`，以及五张 binding 与 `technique_binding.items`（`block_aura` 认的是方块，形状同源） |
| **通用组件** | 物品那一堆自己带组件 | 没有，任何物品都行 | `mxt:quality`、`mxt:element`、`mxt:pill`、`mxt:technique_reading`、`mxt:forging_methods`、`mxt:forging_blueprints`、`mxt:item_abilities`、`mxt:curse_container` |

三种可以叠加。下面第一节是**本体自带的专属载体**（一件物品装一份定义），第二节是**任何物品都能挂的通用组件**，第三节是**没有专属物品、只能靠 `items` 认领的那一半**。

## 一、专属载体（一件物品 = 点名一份定义）

| 物品 | 组件 | 组件里写什么 | 怎么生效 | 没写组件时 |
| --- | --- | --- | --- | --- |
| `mxt:spirit_root`（灵根） | `mxt:spirit_root` | 一份 `spirit_root`（单值 Holder） | 右键授予该灵根，**成功才消耗 1 个**（创造模式不消耗） | 报「这枚灵根没有写明是哪一种灵根」，物品留在手上 |
| `mxt:physique`（体质） | `mxt:physique` | 一份 `physique` | 右键授予该体质，**成功才消耗 1 个** | 报「这具体质物品没有写明是哪一种体质」 |
| `mxt:cultivation_jade_slip`（功法玉简） | `mxt:technique` | 一份 `technique` | 右键学会；`technique_binding` 把 `learn_time` 写成大于 0 时改为**长按读完才学会**（姿势、音效与门槛也在那条声明上）。**身份在组件上，玉简只是惯例底材**：任何带这个组件的物品都能这样读 | 什么都不发生（除非这件物品被 `technique_binding.items` 认领） |
| `mxt:pill`（丹药） | `mxt:pill` | 可选 `pill` 一份，外加五个效果覆盖键 | 原版食用（载体自带 32 tick 的 `minecraft:consumable`），吃完结算一次 | 照旧能吃掉，但没有药效；**次数与冷却只跟匹配到的 `pill_binding` 走**，组件变不出身份 |
| `mxt:talisman`（符箓载体） | `mxt:talisman` | `talismans`（一份或多份 `talisman`，**追加式**）+ `mode`（`fire` / `store`） | 按住右键灌注，够一次就右键激发；画符工作站按符方 `mxt:talisman_drawing` 把符纸画成一张带铭刻的新载体 | 空载体＝还没铭刻，激发只给一句「符箓上未铭刻任何道法」 |
| `mxt:formation_plate`（阵盘） | `mxt:formation_plate` | `allowed`（白名单，`id` 或 `#标签`）+ `formation`（选中哪一份） | 对着结构中心右键开阵，再点一次拆阵 | 按服务端配置「阵法 → 阵盘自动识别」现场找匹配的一座；关掉自动识别就报未绑定 |
| `mxt:secret_realm_token`（秘境令牌） | `mxt:secret_realm_token` | 一份 `secret_realm` | 右键进入；人在秘境里时右键退出 | 报「秘境令牌尚未绑定秘境」 |
| `mxt:contract_scroll`（契约卷轴） | `mxt:contract_scroll` | 一份 `contract_type` | 对生物右键缔结契约，**成功才消耗 1 个** | 报「契约卷轴尚未绑定契约类型」 |
| `mxt:alchemy_furnace`（丹炉核心，方块物品） | `mxt:alchemy_furnace` | 一份 `alchemy_furnace`（槽位、容量、品质、降温） | 放下时随核心物品进方块实体，实体实时读它 | **整台炉子开不了工**（预览给出「没有有效炉型」） |
| `mxt:alchemy_furnace_casing`（丹炉外壳，方块物品） | `mxt:alchemy_wall_material` | 一份 `alchemy_wall_material` | 放下时随外壳进方块实体，决定这一格炉壁的耐温 | 这一格读不出材料，整炉耐温取最小值＝0，同样开不了工 |

丹炉的两个方块物品**掉落时把组件一起带回来**（战利品表用原版 `minecraft:copy_components` 从方块实体复制），所以一台炉子拆掉再摆回去，规格与炉壁材料都还在。

### 存量型载体：键就是定义，但你不是"选一份"

这一类不是"点名一份定义"，而是**按定义开键存数量**，所以单列：

| 物品 | 组件 | 键是什么 | 值 |
| --- | --- | --- | --- |
| `mxt:spirit_vessel`（灵力容器，`stacksTo(1)`） | `mxt:resource_container` | `resource`（**数值**系统） | 数量（double，裸映射，没有外壳）；右键取出、潜行右键存入 |
| 灵石系列、符箓、法器，以及内容包自己挂了组件的一切物品 | `mxt:spirit_storage` | `aura`（**灵气身份**，不是计量它的 `resource`） | 已存单位数（double） |

两处口径不同、不要混：`resource_container` 按**数值**开键，`spirit_storage` 按**灵气身份**开键，`0` 一律不写进表里（空表＝空容器）。灵石还有第三个组件 `mxt:item_aura`，它只装一个数，**身份由这件物品的 `item_aura` 定义给**；所以"缺组件"与"空组件"含义不同——一块灵石没有 `spirit_storage` 时读作"完好的满灵石"。

## 二、通用组件：任何物品都能带

这些不是给某件物品用的，而是**给任何一堆物品**用的，本体物品、原版物品、KubeJS 物品一视同仁：

| 组件 | 能指名什么 |
| --- | --- |
| `mxt:quality` | 一份 `quality`（**整份对象**，所以既换档位也换这一堆读的那条链） |
| `mxt:element` | `element` 的 id 或 `#标签`列表 |
| `mxt:forging_methods` | `forging_method` 列表（与 `tool_binding.methods` 取并集） |
| `mxt:forging_blueprints` | `forging_blueprint` 列表（与 `blueprint_binding.blueprints` 取并集） |
| `mxt:item_abilities` | `ability` 的 id 列表（与 `artifact.abilities` 取并集，物品因此"自带技能"） |
| `mxt:curse_container` | 一组 `apply_curse` 行为：装上即施加、脱下即移除 |
| `mxt:technique_reading` | 逐件覆盖读这门功法的观感（`learn_time` / `hold_animation` / `hold_sound`，键与 `technique_binding` 同名） |

合成规则（单值覆盖、列表并集、记录按字段覆盖）见[数据包格式](../../数据包格式.md)的「物品组件」。

## 三、没有专属物品的那一半

这些注册表**本体不为它们提供物品**：定义自己写 `items`（`ItemMatcher`：单个 id、标签、`*`、正则、混合数组），谁被写中谁就是它。想做一件特殊的东西，就自己造物品（KubeJS / 内容模组）或复用现成物品——不存在"从本体拿一件专属载体"这条路。

| 注册表 | 认领字段 | 备注 |
| --- | --- | --- |
| `artifact` | `items` | 法器。一件物品被两条定义同时认领时按 `priority` 取一条，同分会在加载期诊断里报出来 |
| `item_aura` | `items` | 物品可释放的灵气；剩余量在 `mxt:item_aura` 组件里 |
| `currency` | `items` | 货币价值；硬币与灵石只是"被认领的物品" |
| `spirit_herb` | `items`（必填） | 灵植；药龄在 `mxt:herb_age` |
| `block_aura` | `blocks` | 认领的是方块而不是物品，形状与 `item_aura` 同源 |
| `item_binding` / `weapon_binding` / `pill_binding` / `tool_binding` / `blueprint_binding` | `items` | 五张绑定表，只匹配已有物品 |
| `technique_binding` | `items`（可选） | 手册的身份仍是堆上的 `mxt:technique` 组件，`items` 是"这件物品就是那门功法的手册"的第二条路 |

## 四、只装状态、不指定义的组件

看见这些别指望"换一份定义"：它们记的是这一堆自己的账。（`mxt:spirit_storage` / `mxt:resource_container` 已在上面单列。）

| 组件 | 装什么 | 谁写 |
| --- | --- | --- |
| `mxt:storage` | 技能状态（一个宿主一份值） | 技能运行时 |
| `mxt:brush_pigment` | 符笔的颜料存量（整数；缺省＝`0`＝空笔） | 蘸料手势 |
| `mxt:herb_age` | 药龄（整数） | 灵田生长 |
| `mxt:cheque` | 面额 + 签发人；**不指 `currency` 定义** | 支票台 |
| `mxt:token` | `kind` / `value` / `owner` 三个自由字符串；本体目前只画 tooltip | 内容包 |
| `mxt:rift` | 目标**维度 id** + 颜色 | `/mxt rift bind`、锚潜行右键 |
| `mxt:identification` | 目标**物品 id**，`mxt:identification_mirror` 拿它把"未鉴定物品"换成那件物品 | 内容包（本体没有写入点） |
| `mxt:artifact_state` | 主人 UUID + 名字 + 滋养度 | 认主与法器运行时 |
| `mxt:forging_result` | 锻造记录（蓝图 id + 步数 + 品阶 Holder） | 锻造台产出 |
| `mxt:contract_bell` | 灵宠 UUID + 显示名 + 它自己答的命令 id 列表 | 御兽铃右键生物 |
| `mxt:spirit_beast` | 灵兽袋里那具实体的数据；**其中含一份 `contract_type` 引用**（捕获时写入，放出时用来重建契约） | 灵兽袋 |

## 五、怎么把定义写上去

**命令给**（组件写在方括号里，值就是 id 字符串）：

```mcfunction
/give @s mxt:spirit_root[mxt:spirit_root="mxt:fire_root"]
/give @s mxt:physique[mxt:physique="example:sword_bone"]
/give @s mxt:cultivation_jade_slip[mxt:technique="example:qingxiao_sword_art"]
/give @s mxt:pill[mxt:pill={pill:"example:warming_pill"}]
/give @s mxt:talisman[mxt:talisman={talismans:["example:flame_sigil"],mode:"store"}]
/give @s mxt:secret_realm_token[mxt:secret_realm_token={realm:"example:trial_realm"}]
```

**创造模式取物**：`/picker <注册表 id>`，例如 `/picker mxt:physique`。有专属载体的那几类（灵根、体质、功法、丹药、符箓、阵盘、秘境、契约、丹炉核心与外壳）**每个定义一行，行里的物品已经带好组件**，拿出来就能用；认领式的注册表给的是被认领的那件物品本身。需要创造模式。

**管理员命令**：`/talisman give <符箓> [count <张数> [charged]] [stored [count <张数>]]`（不给 `charged` 就是空符，`stored` 是"存着不自动激发"的模式）、`/talisman blank [count <张数>]`、`/mxt formation bind <阵法>`（绑手上的阵盘）、`/mxt rift bind <维度> [颜色]`（绑手上的锚）。

**内容包自己发**（给玩家的正路）：

```json
{
  "type": "mxt:give_item",
  "stack": {
    "id": "mxt:physique",
    "count": 1,
    "components": { "mxt:physique": "example:sword_bone" }
  }
}
```

- `mxt:give_item`（实体行为）的 `stack` 是 `ItemStackTemplate`，`components` 就是原版组件补丁（组件里的定义引用按 id 解析，与 `/give` 同一套写法）；
- `mxt:merge_components`（物品行为）把一份组件补丁并进手上那一堆，用于"炼出成品再改它"这类场合；
- `mxt:alchemy` 的 `success_outputs` / `failure_outputs` 与灵气合成配方的输出是同一个 `ItemStackTemplate` 形状，同样带 `components`；原版战利品表用 `minecraft:set_components`；
- KubeJS 侧见 [KubeJS 物品示例](../../kubejs物品示例.md)。

## 六、坑

- **组件里写的是具体条目，不能写标签**：`mxt:spirit_root`、`mxt:physique`、`mxt:technique`、`mxt:quality`、`mxt:talisman`、`mxt:pill.pill`、`formation_plate.formation` 都是单值 Holder。要"一类东西"就写在**定义**的字段里——`artifact.items`、`technique.granted_abilities`、`item_binding.element`、`formation_plate.allowed` 这些才收 `#标签`。
- **组件是逐堆的**：一叠 64 个共享同一份组件，`/give ... 64` 给的是 64 个"同一份定义"，要每件不同就一件一件给。以下载体本来就 `stacksTo(1)`：`mxt:spirit_vessel`、`mxt:identification_mirror`、`mxt:talisman_brush`、`mxt:spirit_beast_bag`、`mxt:formation_plate`、`mxt:secret_realm_token`、`mxt:rift_anchor`。符箓本体可叠，但**声明了耐久的载体由框架写成单张**（补 `max_damage` 时一并补 `max_stack_size: 1`）。
- **组件指向的条目从包里消失时，各处表现不同**：`mxt:pill` 指名的那份丹药"键还在、值没了"就**拒绝这一口**；灵根、体质、秘境令牌、契约卷轴报"未写明"；丹炉直接开不了工。而**定义里**的必填引用指向不存在的条目是整个数据包加载失败，不是运行期拒绝——两者别混。
- **本体里也有几件什么都不做的物品**：`recall_talisman`、`spirit_ring`、`spirit_stone_bag`、`secret_realm_reward_box`。本体没有任何读取它们的代码，它们也不带定义引用。

## 相关页面

- [数据包格式](../../数据包格式.md)：字段级权威（「物品组件」「物品绑定与法器」「符箓」「炼丹」等节）
- [物品绑定、品质与经济](items.md)
- [内置物品与组件](../play/items.md)
