---
title: 命令
---

所有命令都挂在 `/mxt` 根节点下，需要管理员权限的命令会在命令树中校验 `gamemaster` 权限。

面向玩家的部分命令同时注册了顶层别名，所以 `/aura` 和 `/mxt aura` 是同一棵树。别名可以在服务端配置 `config.mxt.server.commands.root_aliases` 中关闭（默认开启）；关闭只移除顶层写法，`/mxt` 下的入口始终完整，不会出现配置误关导致命令完全不可用的情况。纯诊断节点（`registries`、`resource` 等）没有顶层别名。

| 命令 | 作用 |
| --- | --- |
| `/mxt registries list` | 列出动态注册表及条目数量。 |
| `/mxt registries validate` | 显示当前数据包注册表校验状态。 |
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
| `/mxt sect claim` / `release` | 占领或释放宗门领地。 |
| `/mxt soul reclaim` | 回收可回收的灵魂。 |
| `/technique repair [dry-run]`（= `/mxt technique repair`） | 清理指向已删除功法定义的失效数据。 |
| `/technique drop <id>`（= `/mxt technique drop <id>`） | 移除一项已习得功法并重建其带来的属性与能力。 |
| `/technique diagnose`（= `/mxt technique diagnose`） | 逐条检查手持功法物品为何无法使用。 |
| `/display [player] [slot]`（= `/mxt display`） | 展示槽位物品。 |
| `/trade <player>`（= `/mxt trade <player>`） | 向玩家发起交易请求。 |

命令中的注册表 ID 使用原版 `IdentifierArgument`，Tab 补全来自服务端当前注册表。
