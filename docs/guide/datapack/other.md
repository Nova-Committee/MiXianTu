---
title: 其他注册表
---

- `curse`：可被多个模块引用的诅咒定义与持续类型。
- `creature_profile` / `contract_type`：生物档案和契约规则，框架不提供具体生物数值。
- `sect`：宗门职级、任务、贡献兑换与领地权限。
- `realm_instance`：运行时维度实例入口。
- `spirit_herb`：绑定现有物品的灵植数据。
- `item_archetype`：法器和飞行能力定义。
- `talisman`：符篆定义，目前只声明铭刻后授予的 `ability`；“已经铭刻了哪些符篆”由物品 `mxt:talisman` 的组件保存，见[数据包格式](../../数据包格式.md#符箓)。

> 称号（`title`）与徽章（`badge`）曾是预留注册表，现已彻底移除，详见[数据包格式](../../数据包格式.md#动态注册表)。
