---
title: resource：资源与资源条
---

`resource` 是所有可增长、消耗、转换或显示的数值资源。资源可以绑定灵气类型、境界链、最大值公式、恢复速度和资源条。

示例（字段以当前 Codec 为准）：

```json
{
  "translation_key": "resource.mxt.spirit_power",
  "min": 0,
  "max": 100,
  "initial": 0,
  "regen": 0,
  "aura_type": "mxt:common",
  "bars": [{
    "renderer": {"type": "origins", "bar_index": 0},
    "anchor": "left",
    "order": 0,
    "context": "self_hud",
    "value_display": "current_and_maximum"
  }]
}
```

资源最大值可以由境界、已吸收灵气和 NumberProvider 计算。资源条是资源定义的内联字段，不再作为单独数据包注册表。
