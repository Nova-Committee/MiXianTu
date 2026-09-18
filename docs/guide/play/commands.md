---
title: 命令
---

所有命令都挂在 `/mxt` 根节点下，需要管理员权限的命令会在命令树中校验 `gamemaster` 权限。

面向玩家的部分命令同时注册了顶层别名，所以 `/aura` 和 `/mxt aura` 是同一棵树。每个别名都在服务端配置的**「命令别名」标签页**里单独开关（存档键为 `commands.<名字>`，界面上显示的就是命令本身，默认全开），例如关闭 `aura` 只移除 `/aura` 这个顶层写法；`/mxt` 下的入口始终完整，不会出现配置误关导致命令完全不可用的情况。`formation` 和 `friend` 也在这个列表里（顶层写法分别是 `/formation` 和 `/friend`）。

| 命令 | 作用 |
| --- | --- |
| `/mxt registries list` | 列出动态注册表及条目数量。 |
| `/mxt registries validate` | 校验数据包定义，并把本次构建**发现的全部问题一次列出**：每条都带出错的文件路径；没有问题时报告注册表与条目数量。 |
| `/picker [<category>]`（= `/mxt picker`） | 打开物品选择器，列出所选数据包注册表定义对应的物品；`category` 是注册表 ID（如 `mxt:aura`、`mxt:currency`、`mxt:item_binding`），不填列出全部已注册分类。需要 gamemaster 权限，且只在创造模式下可用。 |
| `/mxt attachment status` | 查看自身附件数量和修炼数据。 |
| `/mxt resource <id>` | 查询资源值。 |
| `/mxt resource <id> set <value>` | 设置资源值。 |
| `/mxt resourcebar [resource] [index]` | 查看资源条的原始当前值、上下限、未截断百分比、上下文、位置和顺序；不填参数时列出全部资源条。 |
| `/mxt cultivate status` | 查看修炼状态。 |
| `/aura`（= `/mxt aura`） | 打开灵气快捷栏配置界面。 |
| `/aura query [type]`（= `/mxt aura query [type]`） | 查询当前位置灵气；`type` 是资源 ID，不填时显示全部资源，并附带资源的元素标记。 |
| `/aura vein`（= `/mxt aura vein`） | 查询当前位置灵石矿脉等级。 |
| `/aura cache clear [radius]`（= `/mxt aura cache clear [radius]`） | 清除并立即重建周围已加载区块的子区块灵气缓存；半径按区块计算，默认 3，范围 0–32。 |
| `/ability`（= `/mxt ability`） | 打开技能快捷栏配置界面。 |
| `/ability cast <id>`（= `/mxt ability cast <id>`） | 强制施放技能。 |
| `/mxt breakthrough <resource>` | 尝试突破指定资源对应的境界。 |
| `/mxt realm set <realm>` | 设置线性境界。 |
| `/mxt soul reclaim` | 回收可回收的灵魂。 |
| `/mxt trigger list [<entity>]` | 列出该实体当前的运行时触发器订阅：模块/标识/信号/状态。订阅从不存档，这是运行中的服务器里唯一能看见它们的地方；不填实体时用自己。 |
| `/mxt trigger rules <signal>` | 按执行顺序列出响应某个信号的数据包规则，以及每条规则的行为类型。 |
| `/mxt trigger publish <signal> [<entity>]` | 手动发布一个信号（需要 gamemaster 权限），不必等待真实事件就能检查规则或订阅；既没有订阅也没有规则监听时会明确提示。 |
| `/mxt formation list`（= `/formation list`） | 列出当前维度所有已激活阵法：ID、阵心坐标、半径、阵主与已付费的维持次数。 |
| `/mxt formation info`（= `/formation info`） | 列出覆盖玩家所在位置的阵法；重叠时全部列出，不做取舍。 |
| `/mxt formation bind <formation>`（= `/formation bind`） | 把指定阵法写入**主手**的阵盘（需要 gamemaster 权限）。阵盘是唯一能把阵法带进世界的物品，而它的绑定存在物品组件里；这条命令是生存流程里取得可用阵盘的入口。Tab 补全只列出**这块阵盘允许的**阵法，不在白名单里的会被拒绝且不修改阵盘；重复绑定会覆盖原值，ID 写错时阵盘保持原样。 |
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

阵盘的拆除入口不在命令里：对已激活的 controller 使用阵盘即拆除该阵法（需要是阵主或管理员；服务端配置「阵法 → 队友可拆除」`config.mxt.server.formation.teammates_can_dismantle` 打开后，阵主的好友——装着 FTB Teams 时的队友与盟友也算——同样可以拆）。

激活同样用阵盘：手持已绑定的阵盘右键阵心即可；**没绑定的阵盘会自己认出脚下这座阵法**（按白名单逐座比对结构，离点击位置最近的一座胜出；默认开启，可用 `config.mxt.server.formation.plate_auto_detect` 关掉）。**点歪一格不会失败** —— 系统会在点击位置周围 3×3×3 内寻找最近一个满足结构的阵心，因此不必精确命中中心方块；点击位置本身有效时永远优先取它。拆除也走同一次查找，所以对着已激活阵法的旁边一格右键同样是拆除。

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
| `start <id> [<target>]` | 开始一场天劫。`id` 是 `data/<命名空间>/mxt/tribulation/<id>.json`，Tab 补全列出当前注册表里的全部。被拒绝时会说明原因：已有天劫在进行、时间线为空、启动闸门不成立、某个节拍现在跑不了、被事件取消。 |
| `status [<target>]` | 报告正在跑的天劫、第几拍（`第 2 拍，还剩 5 拍`）以及**当前节拍的现场**。现场按存档里的写法打印，例如 `{"remaining":37,"type":"mxt:idle_countdown"}` 表示这一拍还剩 37 tick；`尚未开始（空）` 表示这一拍刚轮到、entry 还没写现场。 |
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
