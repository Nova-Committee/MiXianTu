# Curios 槽位

本模组为玩家提供两个 `back` 槽位和两个 `belt` 槽位。两个槽位都保留 `curios:tag` 验证器，并额外使用一个由本模组注册的自动验证器：

- `mxt:back_auto`
- `mxt:belt_auto`

Jupiter server config 文件 `config/mxt-server.json` 中的 `curios` 部分控制自动验证范围。该配置会随服务器同步给客户端，容器名称为 `MxtServerConfig`：

```toml
[curios]
back_mode = "MANUAL"
belt_mode = "MANUAL"
```

`back_mode` 可选 `MANUAL`、`WEAPONS`、`ALL`：

- `MANUAL`：自动验证不允许额外物品，只有 `curios:tag` 允许的物品可放入。
- `WEAPONS`：在 tag 物品之外，允许匹配 `weapon_binding` 的物品。
- `ALL`：在 tag 物品之外，允许所有物品。

`belt_mode` 可选 `MANUAL`、`WEAPONS_ARTIFACTS`、`ALL`：

- `MANUAL`：只允许 `curios:tag`。
- `WEAPONS_ARTIFACTS`：额外允许已匹配 `weapon_binding` 的武器，以及已经绑定 `item_archetype` 的灵宝。
- `ALL`：在 tag 物品之外，允许所有物品。

两个验证器是“额外允许”逻辑，不会覆盖或修改 `curios:tag`。其他模组也可以通过 Curios API 注册自己的 validator，但不能通过数据包创建新的验证算法。
