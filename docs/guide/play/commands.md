---
title: 命令
---

所有命令根节点为 `/mxt`。需要管理员权限的命令会在命令树中校验 `gamemaster` 权限。

| 命令 | 作用 |
| --- | --- |
| `/mxt registries list` | 列出动态注册表及条目数量。 |
| `/mxt registries validate` | 显示当前数据包注册表校验状态。 |
| `/mxt attachment status` | 查看自身附件数量和修炼数据。 |
| `/mxt resource <id>` | 查询资源值。 |
| `/mxt resource <id> set <value>` | 设置资源值。 |
| `/mxt cultivate status` | 查看修炼状态。 |
| `/mxt aura query [type]` | 查询当前位置灵气；不填类型时显示全部元素。 |
| `/mxt aura vein` | 查询当前位置灵石矿脉等级。 |
| `/mxt ability cast <id>` | 强制施放技能。 |
| `/mxt breakthrough <resource>` | 尝试突破指定资源对应的境界。 |
| `/mxt realm set <realm>` | 设置线性境界。 |
| `/mxt sect claim` / `release` | 占领或释放宗门领地。 |
| `/mxt soul reclaim` | 回收可回收的灵魂。 |
| `/display [player] [slot]` | 展示槽位物品。 |

命令中的注册表 ID 使用原版 `IdentifierArgument`，Tab 补全来自服务端当前注册表。
