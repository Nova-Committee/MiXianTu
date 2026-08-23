---
title: KubeJS 物品与绑定
---

```js
// kubejs/startup_scripts/mxt_items.js
StartupEvents.registry('item', event => {
  event.create('jade_token').displayName('玉令')
})
```

随后在数据包中用 `item_binding`、`weapon_binding` 或 `pill_binding` 匹配 `example:jade_token`。KubeJS 负责注册物品，MiXianTu 负责行为、条件、灵气、货币和 Tooltip。

```js
// kubejs/server_scripts/mxt_reload_notice.js
ServerEvents.loaded(event => {
  console.log('MiXianTu 数据包已加载，使用 /mxt registries validate 检查注册表')
})
```
