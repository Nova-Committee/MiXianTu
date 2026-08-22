# Curios 槽位

本模组为玩家按“背部 → 腰部 → 功法”的顺序提供两个 `back_weapon` 槽位、两个 `belt_item` 槽位和四个 `technique` 槽位。所有槽位均为物理槽位，不创建 Curios cosmetic 槽位；背部和腰部物品渲染直接读取物理槽位。

`back_weapon` 和 `belt_item` 都保留 `curios:tag` 验证器，并额外使用一个由本模组注册的自动验证器：

- `mxt:back_weapon_auto`
- `mxt:belt_item_auto`

Jupiter server config 文件 `config/mxt-server.json` 中的 `curios` 部分控制自动验证范围。该配置会随服务器同步给客户端，容器名称为 `MxtServerConfig`：

```toml
[curios]
back_mode = "MANUAL"
belt_mode = "MANUAL"
force_render_slots = false
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

`technique` 槽位只使用 `curios:tag`，接受 `mxt:cultivation_jade_slip` 与 `#mxt:technique_equipable` 中的物品。内容包可扩展后者来添加书籍、玉简或其他功法载体。

Curios 的槽位界面按钮会控制每个槽位的 `getRenders()` 状态以及槽位整体的可见状态；本模组的背部和腰部渲染会遵守这些状态。`config/mxt-server.json` 的 `curios.force_render_slots` 为 `true` 时，仅强制显示本模组的背部和腰部槽位，不影响其他模组槽位。
