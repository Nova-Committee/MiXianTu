---
title: 数据包示例
---

### 目录

```text
data/example/mxt/resource/spirit_power.json
data/example/mxt/element/common.json
data/example/mxt/realm_stage/qi_condensation.json
```

### 停用一条定义

写在定义文件自己的 `neoforge:conditions` 里；条件不成立的条目不会进注册表：

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "example_addon" }
  ],
  "default": 0.0
}
```

不要使用自定义 `tags` 键代替原版标签；标签文件位于 `data/<namespace>/tags/...`，而**标签不能用来停用条目**（加载期解码时标签还没绑定）。
