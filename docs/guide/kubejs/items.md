---
title: KubeJS 物品与绑定
---

```js
// kubejs/startup_scripts/mxt_items.js
StartupEvents.registry('item', event => {
  event.create('jade_token').displayName('玉令')
})
```

随后在数据包中用 `item_binding`、`weapon_binding` 或 `pill_binding` 匹配 `example:jade_token`；**功法手册常规走物品堆上的 `mxt:technique` 组件**（在 `technique_binding` 里用 `carrier_item` 指到这件物品），也可以把物品写进那条声明的 `items`，让这一叠不带组件就当那门功法的手册（见[数据包格式](../../数据包格式.md)的该节）。KubeJS 负责注册物品，MiXianTu 负责行为、条件、灵气、货币和 Tooltip。

```js
// kubejs/server_scripts/mxt_reload_notice.js
ServerEvents.loaded(event => {
  console.log('MiXianTu 数据包已加载，使用 /mxt registries validate 检查注册表')
})
```
