---
title: resource：数值存储与资源条
---

`resource` 只是一个按实体存储的数值：它的边界（`min`/`max`/`default_value`）和显示方式（`icon`、`particle_color`、`bars`）。它不关心数值来自哪里、做什么用。

把数值变成灵气的那些内容都在 `aura` 定义里（一对一，通过 `resource` 字段指回数值）：境界链入口 `first_realm`、凡人阈值 `start_exp`、开始修炼条件 `start_cultivate_conditions`、自然恢复 `regen`、灵气标记 `aura_type`、灵力射线 `burst_amount`、修为双向换算、可用性门禁 `use_condition` 与信息面板开关 `show_cultivation_info`。没有 `aura` 定义的数值就是一个普通计数器，仍可被消耗、比较和写入公式；但它**不能被存入物品**——存取接口（`AuraAccess`/`ItemAuraAccess`）交换的是灵气，而普通计数器没有灵气身份（它能进玩家池子，因为池子按值开键）。

示例（字段以当前 Codec 为准）：

```json
{
  "min": 0,
  "max": 100,
  "default_value": 0,
  "particle_color": "#66CCFF",
  "bars": [{
    "renderer": {"type": "mxt:boss_bar", "bar_index": 0},
    "anchor": "left",
    "order": 0,
    "context": "mxt:self_hud",
    "value_display": "current_and_maximum"
  }]
}
```

数值上限可以由境界、已吸收灵气和 NumberProvider 计算。需要按玩家条件选择时可使用 `mxt:conditional`：按顺序检查分支，`fallback` 只能是数字或表达式字符串，在没有 `Player` 或所有分支不匹配时使用；不填写时返回 `0`。资源条是数值定义的内联字段，不再是单独的数据包注册表。

资源数值提供器 `mxt:environment_concentration` 只返回环境模板浓度，`mxt:actual_concentration` 返回包含库存、方块和阵法来源的最终浓度。两者均由服务端计算并同步给客户端。

`use_condition`（在 `aura` 定义中）是可选的 `EntityCondition`，用于控制实体能否主动消耗该数值，同时控制其所有资源条的可见性。它不影响修炼、环境吸收、自然恢复或突破。`show_cultivation_info` 默认为 `true`；设为 `false` 时该数值仍可拥有境界链和修为，但不会出现在人物信息面板的“境界”或“修为进度”中。

数值、境界、元素、技能等数据驱动定义不再填写 `translation_key`。显示名称统一由定义文件的标识符自动生成翻译键 `<注册表类别>.<注册表命名空间>.<定义命名空间>.<路径>`：注册表命名空间就是 `mxt`（本模组的注册表都在 `mxt` 下），类别默认取注册表自己的 path，例如 `example:qi` 在 `resource` 类别下对应 `resource.mxt.example.qi`。路径里的 `/` **原样**保留（`example:foo/bar` 得到 `resource.mxt.example.foo/bar`），不会被转成 `.`——所以把定义放进子文件夹时，语言文件里的键要照着写上那个 `/`（见 `docs/数据包格式.md` 的「文件位置」）。数据包作者只需在语言文件中提供该键的翻译，也可以给上表中的 `name` / `description` 直接写文本。
