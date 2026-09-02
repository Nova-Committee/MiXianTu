---
title: resource：资源与资源条
---

`resource` 是所有可增长、消耗、转换或显示的数值资源。资源可以绑定灵气类型、境界链、最大值公式、恢复速度和资源条。

资源数值提供器 `mxt:environment_concentration` 只返回环境模板浓度，`mxt:actual_concentration` 返回包含库存、方块和阵法来源的最终浓度。两者均由服务端计算并同步给客户端。

示例（字段以当前 Codec 为准）：

```json
{
  "min": 0,
  "max": 100,
  "default_value": 0,
  "regen": 0,
  "aura_type": "mxt:common",
  "bars": [{
    "renderer": {"type": "mxt:boss_bar", "bar_index": 0},
    "anchor": "left",
    "order": 0,
    "context": "mxt:self_hud",
    "value_display": "current_and_maximum"
  }]
}
```

资源最大值可以由境界、已吸收灵气和 NumberProvider 计算。需要按玩家条件选择时可使用 `mxt:conditional`：按顺序检查分支，`fallback` 只能是数字或表达式字符串，在没有 `Player` 或所有分支不匹配时使用；不填写时返回 `0`。资源条是资源定义的内联字段，不再作为单独数据包注册表。

`use_condition` 是可选的 `EntityCondition`，用于控制实体能否主动消耗资源，同时控制该资源所有资源条的可见性。它不影响修炼、环境吸收、自然恢复或突破。例如把资源限制为进入对应境界链后才可用：

```json
"use_condition": {
  "type": "mxt:has_realm",
  "resource": "example:qi"
}
```

`show_cultivation_info` 默认为 `true`。设为 `false` 时，该资源仍可拥有境界链和修为，但不会出现在人物信息面板的“境界”或“修为进度”中。

资源、境界、元素、技能等数据驱动定义不再填写 `translation_key`。显示名称统一由定义文件的标识符通过原版 `Identifier.toLanguageKey` 自动生成，例如 `example:qi` 在 `resource` 类别下对应 `resource.example.qi`；路径中的 `/` 会转换为 `.`。数据包作者只需在语言文件中提供该键的翻译。
