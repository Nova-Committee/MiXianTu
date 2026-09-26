---
title: 命令
---

所有命令都挂在 `/mxt` 根节点下，需要管理员权限的命令会在命令树中校验 `gamemaster` 权限；纯查询的入口（例如 `/mxt curse list`、`/ability list`、`/mxt trigger list`）不需要权限，只是不填目标时要用到自己，因此仍需由玩家执行。

面向玩家的部分命令同时注册了顶层别名，所以 `/aura` 和 `/mxt aura` 是同一棵树。每个别名都在服务端配置的**「命令别名」标签页**里单独开关（条目名就是命令本身，默认全开），例如关闭 `aura` 只移除 `/aura` 这个顶层写法；`/mxt` 下的入口始终完整，不会出现配置误关导致命令完全不可用的情况。别名一共 19 个：`ability`、`aura`、`contract`、`curse`、`display`、`flight`、`formation`、`friend`、`lifespan`、`lightning`、`physique`、`picker`、`quality`、`realm`、`spirit_root`、`talisman`、`technique`、`trade`、`tribulation`。

**客户端命令有两条**：`/hud`（查看与复位可拖动 HUD 元素）与 `/wheel`（打开轮盘配置界面，`/wheel configure` 是同一个入口的另一种写法）。它们注册在客户端自己的命令表里（不进 `/mxt` 树，也不发往服务端），只在聊天栏里手打有效、不需要任何权限，详见文末的[客户端命令](#客户端命令hud--wheel)。

| 命令 | 作用 |
| --- | --- |
| `/mxt registries list` | 列出动态注册表及条目数量。 |
| `/mxt registries validate` | 校验数据包定义，并把本次构建**发现的全部问题一次列出**：每条都带出错的文件路径；没有问题时报告注册表与条目数量。 |
| `/picker [<category>]`（= `/mxt picker`） | 打开物品选择器，列出所选数据包注册表定义对应的物品；`category` 是注册表 ID（如 `mxt:aura`、`mxt:artifact`、`mxt:item_binding`），不填列出全部已注册分类。需要 gamemaster 权限，且只在创造模式下可用。 |
| `/quality`（= `/mxt quality`） | 查看自己**主手**物品的品质：解析出来的那一档（覆盖组件 → 锻造结果 → 定义默认 → 链条默认 → 灵植声明）。不需要权限。 |
| `/quality get [<target>]`（= `/mxt quality get …`） | 同上，看别人的（需要 gamemaster 权限）。 |
| `/quality set <targets> <quality>`（= `/mxt quality set …`） | 把品质**覆盖组件**写到目标主手的物品上（需要 gamemaster 权限）。它盖过定义默认档，`/quality clear` 摘掉；这一档能不能用仍由它自己的 `condition` 与所属链条决定。 |
| `/quality clear <targets>`（= `/mxt quality clear …`） | 摘掉主手物品上的覆盖组件，让它回到定义默认档（需要 gamemaster 权限）。本来就没有覆盖时逐个目标报失败。 |
| `/quality upgrade <targets>`（= `/mxt quality upgrade …`） | 把主手物品在它所属的链条上**往上推一档**（需要 gamemaster 权限）：代价就是链条那一步自己声明的 `costs`（`plan` → `commit` 整组原子，付不出就一点不动），并先过它的 `condition`。没声明代价的那一步不能升；已经在顶端、不属于任何链条、或同一档属于多条链时都会逐个目标报出原因。 |
| `/quality chain <quality>`（= `/mxt quality chain …`） | 打印这一档所在的**整条品质链**，不需要权限：链上在它之前的是灰色、它自己是绿色、之后的是白色。同一档可能同时在多条链上，那就每条链各一行；一条都没有时报"没有品质链包含它"，这一档自己被 `#mxt:disabled` 停用时同样按"没有这个定义"拒绝。 |
| `/mxt attachment status` | 查看自身附件数量和修炼数据。 |
| `/flight fill`（= `/mxt flight fill`） | 把当前玩家所骑飞行器的**空座位全部塞上僵尸**（需要 gamemaster 权限）：僵尸无 AI、不消失、头上戴着一顶**不毁的铁头盔**（原版口径：头上有东西就不会被日光点燃），位置由载具按 `seat_offsets` 自己摆，用来对着实机调座位落点（改完 `/reload` 它们会跟着定义立刻换位）。它们随这一趟飞行结束一起消失；没在飞或座位已满会说明原因。 |
| `/mxt resource <id>` | 查询资源值。 |
| `/mxt resource <id> set <value>` | 设置资源值。 |
| `/mxt resourcebar [resource] [index]` | 查看资源条的原始当前值、上下限、未截断百分比、上下文、位置和顺序；不填参数时列出全部资源条。 |
| `/mxt cultivate status` | 查看修炼状态。 |
| `/lifespan [<targets>]`（= `/mxt lifespan`） | 查看目标（不填则自己）的寿元账本「剩余 / 上限」，没有账本时读作「未记账」。不需要权限，单位是刻。 |
| `/lifespan set <targets> <ticks>`（= `/mxt lifespan set …`） | 把两个数一起重写成 `ticks`（必须 ≥ 0，需要 gamemaster 权限）。 |
| `/lifespan add <targets> <ticks>`（= `/mxt lifespan add …`） | 加减寿元：正数延寿（两个数一起涨）、负数抽寿（只减剩余），需要 gamemaster 权限。 |
| `/lifespan reincarnate <targets>`（= `/mxt lifespan reincarnate …`） | 让目标当场转世：跑一遍服务端配置「转世」页的重置清单，并把账本按「凡人基础寿元」重开，需要 gamemaster 权限。 |
| `/aura`（= `/mxt aura`） | 不带子命令时什么都不做（它以前打开轮盘配置界面，现在是客户端命令 `/wheel`）。 |
| `/aura query [type]`（= `/mxt aura query [type]`） | 查询当前位置灵气；`type` 是**灵气 ID**（`mxt:aura` 的条目，补全给的就是它），不填时显示全部灵气，并在名字后附带该灵气的元素标记。 |
| `/aura query element <element>`（= `/mxt aura query element …`） | 按**元素**查询：把这个位置上所有元素标记为该元素的灵气汇总列出（元素被停用时不参与）。补全来自 `mxt:element`。 |
| `/aura vein`（= `/mxt aura vein`） | 查询当前位置灵石矿脉等级。 |
| `/aura cache clear [radius]`（= `/mxt aura cache clear [radius]`） | 清除并立即重建周围已加载区块的子区块灵气缓存；半径按区块计算，默认 3，范围 0–32。 |
| `/ability`（= `/mxt ability`） | 不带子命令时什么都不做（它以前打开轮盘配置界面，现在是客户端命令 `/wheel`）。 |
| `/ability cast <id>`（= `/mxt ability cast <id>`） | 强制施放技能。 |
| `/ability list [<target>]`（= `/mxt ability list …`） | 列出持有者身上的技能：名字与**还在维持它的来源**。读的是附件而不是注册表，所以被停用/定义已删除的技能照样列出来——它仍然被持有，也仍然只能按名字撤销。不填 `target` 时看自己，不需要权限。 |
| `/ability grant <targets> <ability>`（= `/mxt ability grant …`） | 以命令自己的来源 `mxt:command` 授予技能（需要 gamemaster 权限）。逐个目标报告成功或失败，失败发生在该目标已由这一来源持有时。 |
| `/ability revoke <targets> <ability>`（= `/mxt ability revoke …`） | 只撤销 `mxt:command` 这一份来源（需要 gamemaster 权限）；还有别的来源持有就什么都不发生，该目标记为失败。逐个目标报告结果。 |
| `/mxt breakthrough <aura>` | 尝试突破到这门**灵气**（`mxt:aura` 条目，补全给的就是它）所通往的境界。缺哪一种修炼资源由境界自己声明，失败时会点名。 |
| `/realm set <realm>`（= `/mxt realm set …`） | 把自己的境界直接设成链上的某一档（需要 gamemaster 权限）；不在当前有效修炼链上的档会被拒绝。 |
| `/realm chain <realm>`（= `/mxt realm chain …`） | 打印这一档所在的**整条境界链**，不需要权限：链上在它之前的是灰色、它自己是绿色、之后的是白色。抬头是这条链的身份，也就是该链所属的 `mxt:aura` 条目 ID。 |
| `/contract list [<player>]`（= `/mxt contract list`） | 按**主人索引**列出该玩家名下的灵兽：契约类型、灵兽 UUID，以及它此刻是否已加载；不填 `player` 时看自己，不需要权限。索引是名单不是真值，所以每行都会回查灵兽身上的契约记录，已经对不上的行当场清掉。 |
| `/contract info <target>`（= `/mxt contract info`） | 读目标身上的契约记录：类型、主人、签订时刻、召回状态与冷却剩余；它没有契约时按"它没有契约"拒绝。不需要权限。 |
| `/contract bind <player> <target> <contract_type> [force]`（= `/mxt contract bind …`） | 让 `<player>` 与目标生物签订契约（需要 gamemaster 权限），走的是与契约卷轴完全相同的那条流程，代价由该玩家支付；目标必须实现 `Contractable`，否则按"它不能被契约"拒绝。`force` 跳过代价与每人上限。 |
| `/contract break <target> [force]`（= `/mxt contract break …`） | 解除目标身上的契约（需要 gamemaster 权限），灵宠与主人都还活着：执行该契约类型的 `release_action`，清掉记录与主人索引。`force` 跳过"必须是主人"的校验。 |
| `/contract recall <target> [force]`（= `/mxt contract recall …`） | 让目标响应召回，等同于在御兽铃轮盘上点它的「召回」那一格（需要 gamemaster 权限）：置上召回闩，由它下一个 tick 落地。`force` 跳过召回冷却。 |
| `/contract behavior <target> <behavior> [force]`（= `/mxt contract behavior …`） | 给目标下一条行为命令（需要 gamemaster 权限），走的是与御兽铃轮盘完全相同的那条流程。`behavior` 是代码里的行为 id（默认 `mxt:follow` / `mxt:wander` / `mxt:stay` / `mxt:recall`，补全给的是框架已知的那一份），目标没提供这条命令时按"它不接受这道命令"拒绝；`mxt:recall` 是**一次性**的，等价于上面的 `recall`。`force` 跳过"必须是主人"的校验（召回时也跳过冷却）。 |
| `/mxt secret_realm list` | 列出当前所有秘境实例：维度键、序号、定义、在场人数与上限、主人、地形是否已布置、维度当前是否加载。 |
| `/mxt secret_realm info <dimension>` | 查看某一份实例的同一行信息。 |
| `/mxt secret_realm enter <definition>` | 以自己为进入者开一份或加入一份秘境实例（需要 gamemaster 权限）。这是无需令牌就能进秘境的管理入口，走的是与令牌完全相同的那条流程（条件、人数、实例上限、生成）。 |
| `/mxt secret_realm exit` | 把自己从当前秘境送回进入时的位置；与令牌离开走同一条路，所以定义的 `exit_condition` 同样生效。 |
| `/mxt secret_realm destroy <dimension>` | 强制结束一份实例：把里面的人送回，然后卸载维度并清空它的地形数据，**认领过的秘境也会被删掉**（需要 gamemaster 权限）。 |
| `/mxt soul reclaim` | 回收可回收的灵魂。 |
| `/mxt trigger list [<entity>]` | 列出该实体当前的运行时触发器订阅：模块/标识/信号/状态。订阅从不存档，这是运行中的服务器里唯一能看见它们的地方；不填实体时用自己。 |
| `/mxt trigger rules <signal>` | 按执行顺序列出响应某个信号的数据包规则，以及每条规则的行为类型。 |
| `/mxt trigger publish <signal> [<entity>]` | 手动发布一个信号（需要 gamemaster 权限），不必等待真实事件就能检查规则或订阅；既没有订阅也没有规则监听时会明确提示。 |
| `/mxt formation list`（= `/formation list`） | 列出当前维度所有已激活阵法：ID、阵心坐标、半径、阵主与已付费的维持次数。2026-09-25 起阵主可以有多位，列出来的是**逗号分隔的一整组**。 |
| `/mxt formation info`（= `/formation info`） | 列出覆盖玩家所在位置的阵法；重叠时全部列出，不做取舍。 |
| `/mxt formation upkeep` | 列出覆盖玩家所在位置的阵法**下一期还差的份额**：把地脉供给与阵法存量扣掉之后，真正要向阵主账户收的那部分，写法是 `数量 资源id`。它是维护账单的预告口，与真正扣费走的是同一份计划（`FormationService.MaintainRule.remaining`），所以看到的数就是要收的数；`owed=-` 表示这一期已经付得出来。 |
| `/mxt formation owners <pos>` | 打印该阵心上的**归属名单**（一组 UUID）。 |
| `/mxt formation owners <pos> add\|remove <player>` | 加 / 减一位阵主（需要 gamemaster 权限）。归属是一组 UUID：名单上的人都算阵主，因此 `mxt:formation_owner`、拆除权限、逐实体行为的"给阵主"与"给队友"都按这一组判定；好友系统也改成问**每一位**阵主（任一位认得你就算队友）。加一位已经在名单上的、或减一位不在名单上的，会照实回答且不改动。 |
| `/mxt formation bind <formation>`（= `/formation bind`） | 把指定阵法写入**主手**的阵盘（需要 gamemaster 权限）。阵盘是唯一能把阵法带进世界的物品，而它的绑定存在物品组件里；这条命令是生存流程里取得可用阵盘的入口。Tab 补全列出注册表里的全部阵法（不再只列白名单内那些），但**白名单仍在写盘之前把关**：不在名单里的会被拒绝且不修改阵盘；重复绑定会覆盖原值，ID 写错时连解析都过不去，阵盘自然保持原样。 |
| `/spirit_root list [<target>]`（= `/mxt spirit_root list`） | 列出该实体持有的灵根：名字、稀有度、绑定元素与是否生效。读附件而不是注册表，所以定义被停用/删除的灵根照样列出来。不填 `target` 时看自己，不需要权限。 |
| `/spirit_root grant\|remove <targets> <root>`（= `/mxt spirit_root …`） | 授予或移除灵根（需要 gamemaster 权限）。授予走实体行为 `mxt:grant_spirit_root` 的同一套服务，因此 `conflicting_elements` 与「已持有」都会拒绝并逐个目标报出原因；移除按 `spirit_identity` 附件里**持有的那条引用**去找，所以被 `mxt:disabled` 停用的灵根照样摘得掉。 |
| `/spirit_root enable\|disable <targets> <root>`（= `/mxt spirit_root …`） | 「关闭但不失去」：关掉的灵根仍然持有，只是不再提供元素、修炼倍率、授予能力与 `conflicting_elements`。这与数据包标签 `mxt:disabled` 不是一回事。 |
| `/physique list [<target>]`（= `/mxt physique list`） | 列出该实体持有的体质：名字、稀有度与是否生效（叠加时同名只列一行），不需要权限。 |
| `/physique grant\|remove <targets> <physique>`（= `/mxt physique …`） | 授予（按当前实体判定 `holder_condition` 与互斥标签）或移除体质（需要 gamemaster 权限）。移除与灵根同一口径：按附件里持有的引用找。 |
| `/physique enable\|disable <targets> <physique>`（= `/mxt physique …`） | 与灵根同义的开关：关闭后属性修正、授予能力与两个伤害倍率全部不生效，但体质仍然被持有。 |
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
| `/talisman blank [count <count>]`（= `/mxt talisman blank …`） | 给空白载体：什么都没铭刻，因此没有灵气账单。 |
| `/talisman give <talisman> [count <count>] [stored]`（= `/mxt talisman give …`） | 发给你已铭刻这条符箓定义的载体；`count` 一次给出多份（1–64，默认 1），`stored` 以储存模式铭刻，于是它们靠手动灌注而不是下一次点按发动。**定义的 `durability` 会当场写进载体**，所以拿到手就有耐久条；带耐久的载体不叠放，`count` 给的是**多份单张**。 |
| `/talisman give <talisman> count <count> charged`（= `/mxt talisman give …`） | 同上，并同时把整笔灵气灌进去，这正是让载体在下一次点按发动的方式。`charged` 只能写在 `count` 之后。 |

**`give` 一次只收一个符箓 ID，一张载体只铭刻一条定义。** 以前的逗号列表写法已取消：`ResourceArgument` 表达不了"一次列举多个条目"，那种写法连补全和解析都拿不到。要在一张载体上刻多条（例如一条触发符配一条储能符），改用物品组件写法 `give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:flame_sigil","mxt_test:common_sigil"]}]`，字段含义见[数据包 JSON 格式](../../数据包格式)。

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

命令中的注册表 ID 使用原版 `ResourceArgument`：解析、Tab 补全与"没有这个条目"的报错都由它给出，补全来自服务端当前注册表。有一点要记住：`ResourceArgument` 读的是**原始注册表**，所以被 `#mxt:disabled` 停用的条目**会出现在补全里**，但真正执行时仍会被拒绝（与以前一样按"没有这个定义"处理）。

少数参数**故意**仍然用 `IdentifierArgument`，它们的用途就是点名一个**当前数据包已经不提供**的引用：`/technique drop <id>`、`/spirit_root remove|enable|disable <targets> <id>`、`/physique remove|enable|disable <targets> <id>`、`/curse remove`、`/ability revoke`。换成 `ResourceArgument` 会在解析阶段就被拒绝。要注意**"已经删掉的定义"实际上到不了这几条**：灵根/体质在附件里存的是 `Holder`，解码时条目已被删除的那一条会被容错 Codec 丢掉，所以真正需要它们救的是**被 `mxt:disabled` 停用**的条目——它仍然被身体持有，这三条都按身体持有的引用去找（不是查注册表），因此照样摘得掉、关得掉。它们的 Tab 补全来自当前注册表里**还生效**的条目。

维度 ID（`/mxt secret_realm info|destroy`、`/mxt rift target|place|bind`）与触发器信号（`/mxt trigger …`）同样不是注册表条目，也留在 `IdentifierArgument`；`/picker <category>` 收的是**注册表自己的 ID**（如 `mxt:aura`）而不是某个条目，所以也留在它那里。

### 寿元（`/lifespan`）

寿元是每个生物自己的一对数：**剩余**（还能活多少刻）与**上限**（这一世一共拿到过多少刻）。它由数据包给（境界的 `lifespan`、`mxt:modify_lifespan` 行为），由服务端配置决定怎么流逝、耗尽了会怎样。这两个数不懂"岁"，命令读写的单位都是刻；面板与提醒会按配置的「每岁刻数」折算成岁显示。

| 命令 | 作用 |
| --- | --- |
| `/lifespan` 或 `/lifespan get [<targets>]`（= `/mxt lifespan …`） | 报每个目标的「剩余 / 上限」；没有账本的读作「未记账」。不写目标是读执行者自己。 |
| `/lifespan set <targets> <ticks>` | 把两个数一起重写成 `ticks`（必须 ≥ 0）。 |
| `/lifespan add <targets> <ticks>` | 加减。正数延寿（两个数一起涨），**负数抽寿**（只减剩余，上限不动）。 |
| `/lifespan reincarnate <targets>` | 当场走一遍服务端配置「转世」页那份重置清单，并把账本按「凡人基础寿元」重开。 |

写入需要管理员权限。前三行只记账，**不会当场致死**：耗尽只在下一次结算时判定，所以 `add` 把剩余写到 0 之后，要等结算周期到了才见后果（把「结算周期」配成 1 刻就是立刻）。给一个从没被授过寿元的生物 `add` 时，先从配置的「凡人基础寿元」起算（那是 0 就从这个数起算＝0）。

`reincarnate` 是唯一一条**当场改动身体**的：它跑的就是寿元耗尽时 `REINCARNATE` 结局跑的那份清单（境界、层数记录、渡劫、资源、灵根 / 功法 / 魂按「转世」页的开关来），`kills` 打开时还会先真死一次。同一个入口也挂在数据包行为 `mxt:reincarnate`、KubeJS `MxtLifespan.reincarnate` 与 Java `LifeSpanService.reincarnate` 上。它不发 `lifespanEnd`（那个事件只回答"寿元耗尽了没有"），而是发 **`LifeSpanRebirthEvent.Pre` / `Post`**：`Pre` 可取消，取消就是这次转世整件不做、身体原样不动，命令会逐个目标报「被监听者拦下」。「启用寿元」关着时它同样执行，因为这是一句命令而不是时间流逝。目标是玩家时，**本人**还会在聊天栏收到一句通知（`message.mxt.lifespan.remade`）：命令的反馈只发给执行者，一身修为当场没了的人不该一无所知。

### 境界链（`/realm`）
**境界（`mxt:realm_stage`）和秘境（`mxt:secret_realm`）是两套东西**：前者是一条数值修炼链上的一档，后者是一份按需生成的实例维度。`/realm` 只管前者，秘境实例那几条在下面的 `/mxt secret_realm` 里。

境界链属于**灵气定义**（[aura](../../数据包格式) 的 `first_realm` 是链的入口），链上每一档用 `next_realm` 指向下一档，所以一条链是单向的、每份定义一条。`/realm chain <realm>` 不看谁持有哪一档，纯粹回答"这一档前面是谁、后面是谁"——数据包写错 `next_realm` 时这是最快的核对方式。它看的是**当前生效**的阶段：某一档被 `#mxt:disabled` 停用就从链上断开（服务端重建境界索引时同样会拒绝这样的链），被停用的那一档本身会报"没有可用的境界链包含它"。

### 契约（`/contract`）

**能不能被契约是代码事实**：目标生物必须自己实现 `com.iafenvoy.mxt.api.Contractable`（见[特殊公开接口](../java/interfaces)），任何数据包都造不出这个资格，所以原版生物默认都签不了。数据包能做的是：用契约类型自己的**实体类型标签** `#<命名空间>:contract/<路径>` 收窄"这类生物签不签这份契约"（没写标签或标签为空就是不限制，见 [`contract_type`](../../数据包格式.md#contract_type)）；用 `owner_condition` / `creature_condition` 收窄双方；用 `costs` 收代价。

签订一步的顺序是固定的，也是这组命令与卷轴共用的那一份：已经签过 → 目标没实现接口 → 契约类型被 `#mxt:disabled` 停用 → 接口的 `acceptsContract` → 主人条件 → 灵宠条件 → 每人上限 → `Pre` 事件（可取消）→ **最后才收钱** → 写记录 → 写主人索引 → 生物的 `onContractBound`。**收钱排在事件之后**是因为脚本通道退不了款，取消之后要还钱的地方就不该先收。

解除与死亡是**两条不同的路**：`break` 走 `release_action` 并回调 `onContractReleased`，灵宠还活着；灵宠自己死亡走 `death_action` 并回调 `onContractDeath`。两者都会清掉记录与主人索引，也都会发对应的事件。**捕捉不是实体侧的门槛**：任何生物都可能被捕捉，怎么捕捉由物品决定（灵兽袋自己的规则是"你自己的已契约灵兽、一次一只"）。生物只有在实现 `CaptureListener` 时才会收到"被收走/被放出"的通知——不实现它照样能被收走，只是收不到通知。

失败原因共用一套文案键 `contract.mxt.failure.<小写枚举名>`（卷轴、御兽铃、灵兽袋与这组命令打的是同一张表），取值有 `already_bound`、`disabled`、`not_contractable`、`owner_conditions`、`creature_conditions`、`limit_reached`、`insufficient_cost`、`not_bound`、`not_owner`、`recall_cooldown`、`cancelled`、`unsupported_behavior`、`behavior_refused`。

**行为（order）不是数据包字段**：它由生物自己回答（`ContractOperations.behaviors()`），框架只内置跟随 / 游荡 / 驻守 / 召回四条，其余由内容方用 `ContractBehavior` + `ContractBehaviors.register` 添。当前那条写在灵兽的 `mxt:contract` 记录里（读不出来就退回跟随），`follow_action` 只在当前是**跟随**时才跑。玩家的入口是御兽铃右键生物（对准它）再右键空处（开轮盘选），这组命令是管理员入口。

### 秘境实例（`/mxt secret_realm`）

秘境定义（`mxt:secret_realm`）是模板而不是某个固定维度：每次进入都可能开出一份**新的实例维度**，维度键是 `<定义命名空间>:secret_realm/<定义路径>/<序号>`，序号从 `0` 开始（只能开一份的定义也带序号）。这组命令是它的运维入口，**整棵子树都需要 gamemaster 权限**（`list`、`info`、`exit` 也一样，它们是给管理员看状态用的）。

| 子命令 | 行为 |
| --- | --- |
| `list` | 列出所有实例。`loaded=false` 表示这份实例正在休眠——通常是因为它被认领过、人都走光了，地形留在存档里等着主人再来。 |
| `info <dimension>` | 只看一份，参数写维度键，例如 `mxt:secret_realm/trial_realm/0`。 |
| `enter <definition>` | 自己进去。走完整流程：停用检查、进入条件、找一份没满的实例或新开一份（受 `max_instances` 限制）、生成维度与结构、落到入口。 |
| `exit` | 回进入时的位置。定义里的 `exit_condition` 对这条命令同样生效（和自己用令牌离开一样）。 |
| `destroy <dimension>` | 结束一份实例并**删除它的地形**。被锁在里面的玩家会被送回；`mxt:existing` 型秘境只清空成员，不动那个真实维度。 |

### 裂隙（`/mxt rift`）

裂隙（`mxt:rift`）不是数据包定义，而是运行时摆出来的方块：每个裂隙在方块中心画一个点，和 3×3×3 内所有相邻裂隙连线（不看朝向），两条线彼此也相邻时就围出一个三角形并填充内部。这组命令是它的运维入口，**整棵子树都需要 gamemaster 权限**；玩家侧的正常用法是拿裂隙方块（`mxt:rift` 物品）摆、拿裂隙锚（`mxt:rift_anchor` 物品）改。

| 子命令 | 行为 |
| --- | --- |
| `info <pos>` | 打印这个裂隙通往哪里、颜色（自己设的还是随目标维度），以及**连线数、三角形数、连成一片的格数**与**是否孤立**（周围 3×3×3 内没有第二个裂隙）—— 一眼看出它会画成什么样、为什么没有连上邻居。 |
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

## 客户端命令（`/hud`、`/wheel`）

这两条命令只存在于客户端：它们注册在客户端自己的命令表里，不进 `/mxt` 树、也不发往服务端，所以**只在聊天栏里手打才有效**（写进命令方块、或由别的东西代为发送都不行），也不需要任何权限。

| 命令 | 说明 |
| --- | --- |
| `/hud` | 列出框架登记的全部可移动 HUD 元素：布局键、显示名、**绑定的锚点**、位置、尺寸、当前要画几个块、是否可见、是否可拖。 |
| `/hud open` | 打开 HUD 布局编辑器，等同于按键 `key.mxt.hud_layout`（默认右 Shift）。 |
| `/hud <布局键> reset` | 把某个元素复位到它自己的默认位置（布局键见 `/hud` 的输出，如 `resource_bars.left`）。复位会**同时删掉 `config/mxt/mxt-hud.json` 里那一项**，所以它跨重启有效；删掉之后这个元素重新跟着窗口走（默认位置就定义在窗口上），直到玩家再次拖动它。 |
| `/wheel`（= `/wheel configure`） | 打开轮盘配置界面，等同于按键 `key.mxt.wheel_configuration`（**默认未绑定**）。左边 6 列是能发射的灵气、右边 6 列是已学会的主动技能，下面一排 12 格是**主盘**的 12 格；`Esc` 保存并关闭。**从盘（主手物品 / 副手物品 / 法器 / 契约灵兽）不在这里**：它们的内容由随身装备与手里的御兽铃现读，界面只编辑主盘。还没进世界（主菜单里）时它只报一句"现在无法打开轮盘配置"，不会打开空界面。 |

`/hud` 存在的理由是**让"编辑器里什么都没有"变成一句能回答的问题**：`/hud` 打出"没有任何可移动元素"就说明元素根本没登记，打出 `resource_bars.left/right` 与 `wheel.selection` 这几行则说明框架是有元素的、只是当前没有内容可画（没有资源条的存档里那两行会是 `块 0`，尺寸仍是空列的 71×8；`wheel.selection` 是轮盘格，它整块自绘所以永远是 `块 0`，尺寸随内容变——永远 4 列、行数按格子数往下长）。这两种情况的界面表现一模一样，只有这里能分开。

`/wheel` 曾经是服务端命令（`/ability` 与 `/aura` 不带参数时让服务端发一个包去开界面），现在两条都改成客户端自己打开：界面读的是同步过来的附件与注册表，服务端没有任何事可做，也就没有必要为画一个界面往返一次，更没有必要让命令带一个客户端只会忽略的参数。
