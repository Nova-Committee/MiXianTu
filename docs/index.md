---
sidebar_position: 1
title: MiXianTu 文档
description: MiXianTu 修仙模组框架的使用、数据包、KubeJS 与 Java 开发文档
---

# MiXianTu

MiXianTu（觅仙途，模组 ID：`mxt`）是一个 NeoForge 修仙模组框架。它提供数据包注册表、条件与行为分派、资源与境界、灵气环境、物品绑定、技能、阵法、经济和扩展 API，但不预设具体世界观数值。

:::info 这个目录是什么，不是什么

这里是**仓库内的开发文档**（中文，写给内容作者与开发者）。

**玩家文档站在另一个仓库**：[`IAFEnvoy/mxt-docs`](https://github.com/IAFEnvoy/mxt-docs)——那是中英双语、面向玩家的站点，别和这里搞混。字段级权威始终是 [数据包格式](数据包格式)，与代码不一致时以代码为准；本目录与本仓库的 `research/` 分工见 [docs/README](README)。

:::

## 文档导航

- [基本信息与安装](getting-started)
- [游玩内容](guide/play/index)
- [数据包开发](guide/datapack/overview)
- [KubeJS 开发](guide/kubejs/index)
- [Java 模组开发](guide/java/index)
- [AI 快速技能说明](ai/SKILL)
- [文档格式规范](ai/FORMAT)

## 设计边界

本体负责可复用的规则框架和运行时结算，不负责具体生物数值、门派内容或完整世界观。物品、方块和配方可以由 KubeJS 或其他内容模组注册，再用 MiXianTu 的绑定表接入玩法。
