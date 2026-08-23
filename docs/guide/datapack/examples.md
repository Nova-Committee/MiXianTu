---
title: 数据包示例
---

### 目录

```text
data/example/mxt/resource/spirit_power.json
data/example/mxt/element/common.json
data/example/mxt/realm_stage/qi_condensation.json
data/example/tags/mxt/resource/disabled.json
```

### 禁用一条定义

```json
{
  "replace": false,
  "values": ["example:old_resource"]
}
```

不要使用自定义 `tags` 键代替原版标签；标签文件位于 `data/<namespace>/tags/...`。
