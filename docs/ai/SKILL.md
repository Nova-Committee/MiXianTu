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
- 资源和灵气必须保持 Map 的独立类型语义，不要把不同元素求和后丢失类型。
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
