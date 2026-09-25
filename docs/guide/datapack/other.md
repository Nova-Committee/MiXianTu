---
title: 其他注册表
---

- `curse`：可被多个模块引用的诅咒定义与持续类型；到期与被解毒各有一个行为，而「谁能解我」不由诅咒决定——解毒剂用 `mxt:remove_curses_by_tag` 声明它能解的 `mxt:curse` 标签，标签文件列出诅咒。物品可以携带诅咒（`mxt:curse_container`，装上即施加、脱下即移除），`display_condition` 决定它在人物信息面板里露不露面。详见[数据包格式](../../数据包格式.md#curse)。
- `creature_profile` / `contract_type`：生物档案和契约规则，框架不提供具体生物数值。**能不能被契约由代码决定**（目标生物要实现 `Contractable`），数据包只能用契约类型自己的实体类型标签与条件收窄名单，另可约定代价、每人上限与召回冷却。详见[数据包格式](../../数据包格式.md#contract_type)。
- `secret_realm`：秘境模板——每次进入按它开出一份独立维度的实例，声明维度怎么生成、边界多大、要放哪些结构、从哪进、谁来认领，以及进出的条件与行为。详见[数据包格式](../../数据包格式.md#secret_realm)。
- `spirit_herb`：绑定现有物品的灵植数据。
- `artifact`：法器——用 `items` 认领已有物品，用 `abilities`（固有分派）声明授予技能、载人飞行、自带储物与**周期性代价**（`mxt:upkeep`），用 `spirit_capacity` 声明每种灵气的上限，用 `hold_ticks` / `claim_condition` / `claim_action` / `pour_action` / `use_action` 声明长按（未认主时认主、认主后注入自身灵气）。认主的代价就是 `claim_action` 的默认值（扣 4 点生命），想免费就写 `"claim_action": {"type": "mxt:no_op"}`。字段与内置类型见[数据包格式](../../数据包格式.md#artifact)。
- `talisman`：符箓定义——铭刻后授予的 `ability`、灌注灵气的账单 `aura_cost`、载体自己的磨损（`durability` / `consume`）、向持有者收的代价 `costs`（"灵力不足无法使用"这类门槛就写在这里，不另立条件字段）与品阶 `quality`；“已经铭刻了哪些符箓”由物品 `mxt:talisman` 的组件保存，见[数据包格式](../../数据包格式.md#符箓)。

> 称号（`title`）、徽章（`badge`）与宗门（`sect`）曾是预留注册表，现已彻底移除，详见[数据包格式](../../数据包格式.md#动态注册表)。
