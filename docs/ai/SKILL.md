---
title: AI 快速技能
sidebar_position: 1
---

# MiXianTu AI Skill

## 项目定位

MiXianTu 是 NeoForge `26.1.2` / Minecraft `26.1.2` 的服务端权威修仙框架。它提供可复用规则，不负责具体世界观数值。Curios 是必需前置，KubeJS、JEI、Jade 是可选扩展。

## 修改前检查

1. 阅读 `README-zh.md`、本目录文档和 `research/` 中对应模块。
2. 检查当前工作树，不回滚用户已有改动。
3. 确认数据包字段来自当前 Definition Codec，不根据旧文档猜字段。
4. 明确逻辑属于服务端、客户端还是网络两端。

## 代码约定

- 动态注册表使用原版 NeoForge datapack registry。
- 跨表引用优先使用 Holder；可选和列表使用容错 Codec。
- Definition 的 `CODEC` 是 Holder Codec，`DIRECT_CODEC` 是直接对象 Codec。
- 所有数据包对象视为不可变；不要对 Codec 结果调用不必要的 `copyOf` 或 Mutable 转换。
- 行为统一使用 `action`，判断统一使用 `condition`，消耗统一使用 `Cost`。
- 术语固定：载体与定义一律写「符箓」（`talisman`），配「符纸」「符笔」「符墨」；「符篆」是误用，不得出现在文案、注释或文档中。
- 资源和灵气必须保持 Map 的独立类型语义，不要把不同元素求和后丢失类型。
- 语义边界：`resource` 只是一套**数值系统**——`default_value`/`min`/`max`/`icon`/`particle_color`/`bars`，它**不知道自己这个数是干什么用的**；`aura`（`mxt:aura`，旧名 `cultivation`）才是**灵气身份**，回答"这是哪一种灵气"（`aura_type` 元素标记、`burst_amount` 射线量）以及"它怎么参与修炼"（境界链入口、`regen`、两个换算、`use_condition`），它引用一个 `resource` 作为**被计量的单位**（`Aura.resource()` 是"我用哪个数计量"，不是"我是什么"）。判断规则是**看使用场景**：凡是"哪一种灵气"的字段、参数、存储键一律用 `Holder<Aura>`——存取接口 `AuraAccess`/`AuraItemAccess`、物品与方块存储（`mxt:spirit_storage` 的键、`mxt:spirit_burst` 的载荷）、世界灵气池（`aura_zone`/`block_aura`/`AuraResult`/客户端快照与 S2C 包）、`item_aura.type`、`has_realm`、`aura_costs`/`aura_gains`/`aura_cost`/`minimum_aura`/`max_bonus`/阵法 `storage.capacity`。按 `Holder<Resource>` 开键的只有**值**本身：玩家池子 `ResourceHolderAttachment` 与 `ResourceService`/`ResourceTransactions`（里面同时装纯计数器）、公式的 `resource_value.*` 与 `FormulaContext.ResourceSubject`、`Resource.bars`、`mastery_resource`、`add_resource`、`Cost`/`ResourceCost`/`ResourceGain`、`mxt:resource_container`（浮点池子副本）。`AuraLookup` 只保留 value→aura 一个方向（"这个值有没有带灵气"），不要再加整表反查。物品自己装的是哪种灵气也记在物品身上，不要靠"当前匹配到的定义"反推已有存量。**不要重新引入"灵气类型标记"（原 `aura_kinds`）这套裸字符串词汇**：一处环境有什么灵气就是它 `aura` 的键，"能在哪修炼"由 `CultivateAction` 的 `start_condition`/`condition` 表达，炼丹由 `minimum_aura` 表达。
- 服务端负责消耗、校验、修炼、突破、交易和实体行为；客户端只显示和发送请求。
- 颜色使用项目的 `MiscCodecs.COLOR`，不要重新引入字符串颜色解析。

## 验证命令

```bash
gradlew compileJava compileTestModJava --offline --no-daemon --console=plain
gradlew runTestClient --offline --no-daemon --console=plain
```

数据包问题优先查看 `run-test-client/logs/latest.log` 或 `run-test-server/logs/latest.log`。修改后至少运行对应源码集编译；涉及 Codec、网络或渲染时补充测试客户端启动。

## 文档更新规则

代码字段、注册表、公开接口或完成度变化时，同步更新 `docs/` 对应模块页、`README-zh.md` 链接和模块审计。不要把仅存在于研究设计中的内容写成“已完成”。
