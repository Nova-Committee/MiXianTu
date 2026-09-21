---
title: 命令
---

所有命令都挂在 `/mxt` 根节点下，需要管理员权限的命令会在命令树中校验 `gamemaster` 权限；纯查询的入口（例如 `/mxt curse list`、`/ability list`、`/mxt trigger list`）不需要权限，只是不填目标时要用到自己，因此仍需由玩家执行。

面向玩家的部分命令同时注册了顶层别名，所以 `/aura` 和 `/mxt aura` 是同一棵树。每个别名都在服务端配置的**「命令别名」标签页**里单独开关（条目名就是命令本身，默认全开），例如关闭 `aura` 只移除 `/aura` 这个顶层写法；`/mxt` 下的入口始终完整，不会出现配置误关导致命令完全不可用的情况。别名一共 13 个：`ability`、`aura`、`curse`、`display`、`formation`、`friend`、`identity`、`lightning`、`picker`、`talisman`、`technique`、`trade`、`tribulation`。

**唯一一条客户端命令**是 `/hud`：它注册在客户端自己的命令表里（不进 `/mxt` 树，也不发往服务端），只在聊天栏里手打有效，作用是查看与复位可拖动 HUD 元素，详见文末的[客户端命令](#客户端命令hud)。

| 命令 | 作用 |
| --- | --- |
| `/mxt registries list` | 列出动态注册表及条目数量。 |
| `/mxt registries validate` | 校验数据包定义，并把本次构建**发现的全部问题一次列出**：每条都带出错的文件路径；没有问题时报告注册表与条目数量。 |
| `/picker [<category>]`（= `/mxt picker`） | 打开物品选择器，列出所选数据包注册表定义对应的物品；`category` 是注册表 ID（如 `mxt:aura`、`mxt:artifact`、`mxt:item_binding`），不填列出全部已注册分类。需要 gamemaster 权限，且只在创造模式下可用。 |
| `/mxt attachment status` | 查看自身附件数量和修炼数据。 |
| `/mxt resource <id>` | 查询资源值。 |
| `/mxt resource <id> set <value>` | 设置资源值。 |
| `/mxt resourcebar [resource] [index]` | 查看资源条的原始当前值、上下限、未截断百分比、上下文、位置和顺序；不填参数时列出全部资源条。 |
| `/mxt cultivate status` | 查看修炼状态。 |
| `/aura`（= `/mxt aura`） | 打开灵气快捷栏配置界面。 |
| `/aura query [type]`（= `/mxt aura query [type]`） | 查询当前位置灵气；`type` 是**灵气 ID**（`mxt:aura` 的条目，补全给的就是它），不填时显示全部灵气，并在名字后附带该灵气的元素标记。 |
| `/aura query element <element>`（= `/mxt aura query element …`） | 按**元素**查询：把这个位置上所有元素标记为该元素的灵气汇总列出（元素被停用时不参与）。补全来自 `mxt:element`。 |
| `/aura vein`（= `/mxt aura vein`） | 查询当前位置灵石矿脉等级。 |
| `/aura cache clear [radius]`（= `/mxt aura cache clear [radius]`） | 清除并立即重建周围已加载区块的子区块灵气缓存；半径按区块计算，默认 3，范围 0–32。 |
| `/ability`（= `/mxt ability`） | 打开技能快捷栏配置界面。 |
| `/ability cast <id>`（= `/mxt ability cast <id>`） | 强制施放技能。 |
| `/ability list [<target>]`（= `/mxt ability list …`） | 列出持有者身上的技能：名字与**还在维持它的来源**。读的是附件而不是注册表，所以被停用/定义已删除的技能照样列出来——它仍然被持有，也仍然只能按名字撤销。不填 `target` 时看自己，不需要权限。 |
| `/ability grant <targets> <ability>`（= `/mxt ability grant …`） | 以命令自己的来源 `mxt:command` 授予技能（需要 gamemaster 权限）。逐个目标报告成功或失败，失败发生在该目标已由这一来源持有时。 |
| `/ability revoke <targets> <ability>`（= `/mxt ability revoke …`） | 只撤销 `mxt:command` 这一份来源（需要 gamemaster 权限）；还有别的来源持有就什么都不发生，该目标记为失败。逐个目标报告结果。 |
| `/mxt breakthrough <resource>` | 尝试突破指定资源对应的境界。 |
| `/mxt realm set <realm>` | 设置线性境界。 |
| `/mxt realm_instance list` | 列出当前所有秘境实例：维度键、序号、定义、在场人数与上限、主人、地形是否已布置、维度当前是否加载。 |
| `/mxt realm_instance info <dimension>` | 查看某一份实例的同一行信息。 |
| `/mxt realm_instance enter <definition>` | 以自己为进入者开一份或加入一份秘境实例（需要 gamemaster 权限）。这是无需令牌就能进秘境的管理入口，走的是与令牌完全相同的那条流程（条件、人数、实例上限、生成）。 |
| `/mxt realm_instance exit` | 把自己从当前秘境送回进入时的位置；与令牌离开走同一条路，所以定义的 `exit_condition` 同样生效。 |
| `/mxt realm_instance destroy <dimension>` | 强制结束一份实例：把里面的人送回，然后卸载维度并清空它的地形数据，**认领过的秘境也会被删掉**（需要 gamemaster 权限）。 |
| `/mxt soul reclaim` | 回收可回收的灵魂。 |
| `/mxt trigger list [<entity>]` | 列出该实体当前的运行时触发器订阅：模块/标识/信号/状态。订阅从不存档，这是运行中的服务器里唯一能看见它们的地方；不填实体时用自己。 |
| `/mxt trigger rules <signal>` | 按执行顺序列出响应某个信号的数据包规则，以及每条规则的行为类型。 |
| `/mxt trigger publish <signal> [<entity>]` | 手动发布一个信号（需要 gamemaster 权限），不必等待真实事件就能检查规则或订阅；既没有订阅也没有规则监听时会明确提示。 |
| `/mxt formation list`（= `/formation list`） | 列出当前维度所有已激活阵法：ID、阵心坐标、半径、阵主与已付费的维持次数。 |
| `/mxt formation info`（= `/formation info`） | 列出覆盖玩家所在位置的阵法；重叠时全部列出，不做取舍。 |
| `/mxt formation bind <formation>`（= `/formation bind`） | 把指定阵法写入**主手**的阵盘（需要 gamemaster 权限）。阵盘是唯一能把阵法带进世界的物品，而它的绑定存在物品组件里；这条命令是生存流程里取得可用阵盘的入口。Tab 补全只列出**这块阵盘允许的**阵法，不在白名单里的会被拒绝且不修改阵盘；重复绑定会覆盖原值，ID 写错时阵盘保持原样。 |
| `/identity root list [<target>]`（= `/mxt identity root list`） | 列出该实体持有的灵根：名字、稀有度、绑定元素与是否生效。读附件而不是注册表，所以定义被停用/删除的灵根照样列出来。不填 `target` 时看自己，不需要权限。 |
| `/identity root grant\|remove <targets> <root>`（= `/mxt identity root …`） | 授予或移除灵根（需要 gamemaster 权限）。授予走实体行为 `mxt:grant_spirit_root` 的同一套服务，因此 `conflicting_elements` 与「已持有」都会拒绝并逐个目标报出原因。 |
| `/identity root enable\|disable <targets> <root>`（= `/mxt identity root …`） | 「关闭但不失去」：关掉的灵根仍然持有，只是不再提供元素、修炼倍率、授予能力与 `conflicting_elements`。这与数据包标签 `mxt:disabled` 不是一回事。 |
| `/identity physique list [<target>]`（= `/mxt identity physique list`） | 列出该实体持有的体质：名字、稀有度与是否生效（叠加时同名只列一行），不需要权限。 |
| `/identity physique grant\|remove <targets> <physique>`（= `/mxt identity physique …`） | 授予（按当前实体判定 `holder_condition` 与互斥标签）或移除体质（需要 gamemaster 权限）。 |
| `/identity physique enable\|disable <targets> <physique>`（= `/mxt identity physique …`） | 与灵根同义的开关：关闭后属性修正、授予能力与两个伤害倍率全部不生效，但体质仍然被持有。 |
| `/technique repair [dry-run]`（= `/mxt technique repair`） | 清理指向已删除功法定义的失效数据。 |
| `/technique drop <id>`（= `/mxt technique drop <id>`） | 移除一项已习得功法并重建其带来的属性与能力。 |
| `/technique diagnose`（= `/mxt technique diagnose`） | 逐条检查手持功法物品为何无法使用。 |
| `/display [player] [slot]`（= `/mxt display`） | 展示槽位物品。 |
| `/trade <player>`（= `/mxt trade <player>`） | 向玩家发起交易请求。 |
| `/friend`（= `/mxt friend`） | 输出好友指令帮助，每一行可点击把指令填入聊天栏（不发送）。 |
| `/friend list`（= `/mxt friend list`） | 列出永久与临时好友名单，名单中的名字可点击填入移除指令。 |
| `/friend add <player>`（= `/mxt friend add`） | 添加**临时**好友，重登后失效。 |
| `/friend remove <player>`（= `/mxt friend remove`） | 移除临时好友；对永久好友会拒绝并提示改用下一条。 |
| `/friend permanent add <player>`（= `/mxt friend permanent add`） | 添加**永久**好友，写入存档；临时好友会被升级。 |
| `/friend permanent remove <player>`（= `/mxt friend permanent remove`） | 移除永久好友。 |
| `/mxt lightning [pos] [color … | palette …]`（= `/lightning`） | 直接打下一道雷，需要 gamemaster 权限。单色或渐变、亮度、粗细、伤害按固定顺序可选，见下。 |
| `/mxt tribulation start <id> [<target>]`（= `/tribulation start …`） | 手动开始一场天劫（需要 gamemaster 权限），不必等突破；不填 `target` 时挂在自己身上。配套的 `status` 报告跑到第几拍与当前节拍的现场，`stop` 清除。 |
| `/mxt curse list [<target>]`（= `/curse`） | 列出持有者身上的诅咒：名字、层数、剩余 tick 或「永不到期」。不填 `target` 时看自己，不需要权限。 |
| `/mxt curse apply <targets> <curse> [<stacks>] [<duration_ticks>]`（= `/curse apply …`） | 施加一条诅咒（需要 gamemaster 权限），`stacks` 取 1–256，走与内容同一条事务：条件、叠层、`on_apply` 照常；被 `#mxt:disabled` 停用或已删除的定义会被拒绝并报出原因。`duration_ticks` 只能收紧定义自己的时长。 |
| `/mxt curse remove <targets> <curse>`（= `/curse remove …`） | 以 `explicit` 原因移除（需要 gamemaster 权限）。这也是**被停用/已删除定义的唯一出口**。 |
| `/mxt curse cleanse <targets> <tag>`（= `/curse cleanse …`） | 按 `mxt:curse` 标签解毒（需要 gamemaster 权限），与解毒剂同一个 `cleansed` 原因；被停用的实例会拒绝并说明原因。 |

### `/mxt lightning`

在指定位置打下一道雷，不写 `pos` 时落在命令执行者脚下。除了颜色，它就是原版闪电：伤害、引燃、避雷针充能、铜氧化、雷声、天空闪光，以及村民→女巫、猪→僵尸猪灵、苦力怕充能这些雷击转化全部照旧。

```
/mxt lightning
/mxt lightning ~ ~ ~
/mxt lightning ~ ~ ~ color 66CCFF
/mxt lightning ~ ~ ~ color 66CCFF alpha 0.5 thickness 2 damage 10 visual_only
/mxt lightning ~ ~ ~ palette 7A5CFF,66CCFF
/mxt lightning ~ ~ ~ palette 7A5CFF,66CCFF,FF4444 alpha 0.5 visual_only
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `pos` | 执行者位置 | 落点，支持 `~` 相对坐标。 |
| `color <六位十六进制>` | `737380`（原版那身冷白） | 不带 `#`，例如 `66CCFF`；Tab 补全会给几个常用色。 |
| `palette <颜色,颜色,…>` | 无 | **渐变**：逗号分隔的六位十六进制颜色，**第一项在顶端**（雷的起点）、最后一项在落地点，最多 16 项；Tab 补全给几个预设渐变。写出 `palette` 后 `color` 不参与着色。 |
| `alpha <0..1>` | `0.3` | 雷的**亮度**。原版闪电是加法混合，顶点色的 `RGB × alpha` 就是发光强度，所以它不是透明度。 |
| `thickness <0.1..4>` | `1` | 雷柱粗细倍率。 |
| `damage <≥0>` | `5` | 雷击伤害。 |
| `visual_only` | 关 | 只打雷，不结算伤害、不引燃，适合做纯装饰。 |

`color <色>` 与 `palette <渐变>` 是**二选一**的两支，各自后面接着同一条固定顺序的尾巴 `[alpha [thickness [damage [visual_only]]]]`：想写后面的就必须把前面的也写出来（Tab 补全会一路提示），例如要 `thickness` 就得先写颜色或渐变、再写 `alpha`。数据包侧的同一个行为 `mxt:spawn_lightning` 支持任意组合的字段（渐变写在 `palette`），见[数据包 JSON 格式](../../数据包格式)。

命令中的注册表 ID 使用原版 `IdentifierArgument`，Tab 补全来自服务端当前注册表。

### 秘境实例（`/mxt realm_instance`）

秘境定义（`mxt:realm_instance`）是模板而不是某个固定维度：每次进入都可能开出一份**新的实例维度**，维度键是 `<定义命名空间>:realm/<定义路径>/<序号>`，序号从 `0` 开始（只能开一份的定义也带序号）。这组命令是它的运维入口，**整棵子树都需要 gamemaster 权限**（`list`、`info`、`exit` 也一样，它们是给管理员看状态用的）。

| 子命令 | 行为 |
| --- | --- |
| `list` | 列出所有实例。`loaded=false` 表示这份实例正在休眠——通常是因为它被认领过、人都走光了，地形留在存档里等着主人再来。 |
| `info <dimension>` | 只看一份，参数写维度键，例如 `mxt:realm/trial_realm/0`。 |
| `enter <definition>` | 自己进去。走完整流程：停用检查、进入条件、找一份没满的实例或新开一份（受 `max_instances` 限制）、生成维度与结构、落到入口。 |
| `exit` | 回进入时的位置。定义里的 `exit_condition` 对这条命令同样生效（和自己用令牌离开一样）。 |
| `destroy <dimension>` | 结束一份实例并**删除它的地形**。被锁在里面的玩家会被送回；`mxt:existing` 型秘境只清空成员，不动那个真实维度。 |

### 裂隙（`/mxt rift`）

裂隙（`mxt:rift`）不是数据包定义，而是运行时摆出来的方块：每个裂隙在方块中心画一个点，和 3×3×3 内所有相邻裂隙连线（不看朝向），两条线彼此也相邻时就围出一个三角形并填充内部。这组命令是它的运维入口，**整棵子树都需要 gamemaster 权限**；玩家侧的正常用法是拿裂隙方块（`mxt:rift` 物品）摆、拿裂隙锚（`mxt:rift_anchor` 物品）改。

| 子命令 | 行为 |
| --- | --- |
| `info <pos>` | 打印这个裂隙通往哪里、颜色（自己设的还是随目标维度），以及**连线数、三角形数、连成一片的格数** —— 一眼看出它会画成什么样、为什么没有连上邻居。 |
| `target <pos> <dimension>` | 改它通往哪个维度（有 Tab 补全，列出服务端所有维度）。 |
| `color <pos> <color>` | 设颜色覆盖，写 `RRGGBB`（可带 `#`）或 `auto`。 |
| `place <pos> <dimension>` | 直接在某个可替换的位置放一个通往该维度的裂隙。 |
| `bind <dimension> [color]` | 把手上的裂隙锚设成这个目标（与潜行使用物品等价，但可以直接写成任意维度）。 |

### 阵盘

阵盘的拆除入口不在命令里：对已激活的 controller 使用阵盘即拆除该阵法（需要是阵主或管理员；服务端配置「阵法 → 队友可拆除」打开后，阵主的好友——装着 FTB Teams 时的队友与盟友也算——同样可以拆）。

激活同样用阵盘：手持已绑定的阵盘右键阵心即可；**没绑定的阵盘会自己认出脚下这座阵法**（按白名单逐座比对结构，离点击位置最近的一座胜出；默认开启，可用服务端配置「阵法 → 阵盘自动识别」关掉）。**点歪一格不会失败** —— 系统会在点击位置周围 3×3×3 内寻找最近一个满足结构的阵心，因此不必精确命中中心方块；点击位置本身有效时永远优先取它。拆除也走同一次查找，所以对着已激活阵法的旁边一格右键同样是拆除。

### 好友名单

好友名单的"临时"指的是**下次登录时会被清空**：重登与服务器重启都会清掉它，**重生不会**。添加和移除都按玩家档案解析，对方离线也能操作，因此要加一个离线玩家直接写名字即可。细节见[好友与敌我识别](friends)。

### `/mxt tribulation`

手动跑一场天劫。它走的就是突破触发时的**同一条路径**——启动闸门、启动前的逐拍校验、之后每 tick 消费一拍全部照旧，被替换的只有"要不要开始"这一个决定。因此它既是触发器，也是观测器。

```
/mxt tribulation start mxt_test:probe_timeline
/mxt tribulation start mxt_test:probe_timeline @e[type=minecraft:armor_stand,limit=1]
/mxt tribulation status
/mxt tribulation stop
```

| 子命令 | 说明 |
| --- | --- |
| `start <id> [<target>]` | 开始一场天劫。`id` 是 `data/<命名空间>/mxt/tribulation/<path>.json`，Tab 补全列出当前注册表里的全部。被拒绝时会说明原因：已有天劫在进行、时间线为空、启动闸门不成立、某个节拍现在跑不了、被事件取消。 |
| `status [<target>]` | 报告正在跑的天劫、第几拍（`第 2 拍，还剩 5 拍`）以及**当前节拍的现场**。现场按存档里的写法打印，例如 `{"remaining":37,"type":"mxt:idle_countdown"}` 表示这一拍还剩 37 tick；`尚未开始（空）` 表示这一拍刚轮到、entry 还没写现场。定义里写了启动前摇（`windup`）时，前摇期间报的是`还在前摇，还剩 %s tick`——那时时间线还没开始消费，报"第几拍"会撒谎。 |
| `stop [<target>]` | 清除当前天劫，留下的状态与跑完一场之后完全一致。 |

目标必须是**活体实体**：天劫挂在实体附件上、由实体的 tick 推进。所以测试时可以直接对一只召唤物下手，不必登录玩家——`execute positioned` 给选择器一个位置、`limit=1` 保证单选即可，例如

```
/execute positioned 0 100 0 run mxt tribulation start mxt_test:probe_timeline @e[tag=probe,limit=1]
/execute positioned 0 100 0 run mxt tribulation status @e[tag=probe,limit=1]
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `id` | 必填 | 天劫定义 ID。 |
| `target` | 命令执行者 | 天劫挂在哪一个活体实体上。 |

## 客户端命令（`/hud`）

这条命令只存在于客户端：它注册在客户端自己的命令表里，不进 `/mxt` 树、也不发往服务端，所以**只在聊天栏里手打才有效**（写进命令方块、或由别的东西代为发送都不行），也不需要任何权限。

| 子命令 | 说明 |
| --- | --- |
| `/hud` | 列出框架登记的全部可移动 HUD 元素：布局键、显示名、位置、尺寸、当前要画几个块、是否可见、是否可拖。 |
| `/hud open` | 打开 HUD 布局编辑器，等同于按键 `key.mxt.hud_layout`（默认右 Shift）。 |
| `/hud <布局键> reset` | 把某个元素复位到它自己的默认位置（布局键见 `/hud` 的输出，如 `resource_bars.left`）。复位会**同时删掉 `config/mxt/mxt-hud.json` 里那一项**，所以它跨重启有效；删掉之后这个元素重新跟着窗口走（默认位置就定义在窗口上），直到玩家再次拖动它。 |

它存在的理由是**让"编辑器里什么都没有"变成一句能回答的问题**：`/hud` 打出"没有任何可移动元素"就说明元素根本没登记，打出两行 `resource_bars.left/right` 则说明框架是有元素的、只是当前没有内容可画（没有资源条的存档里那两行会是 `块 0`，尺寸仍是空列的 71×8）。这两种情况的界面表现一模一样，只有这里能分开。
