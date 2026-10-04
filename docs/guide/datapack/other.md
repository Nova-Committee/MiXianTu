---
title: 其他注册表
---

- `curse`：可被多个模块引用的诅咒定义与持续类型；到期与被解毒各有一个行为，而「谁能解我」不由诅咒决定——解毒剂用 `mxt:remove_curses_by_tag` 声明它能解的 `mxt:curse` 标签，标签文件列出诅咒。物品可以携带诅咒（`mxt:curse_container`，装上即施加、脱下即移除），`display_condition` 决定它在人物信息面板里露不露面。详见[数据包格式](../../数据包格式.md#curse)。
- `creature_profile` / `contract_type`：生物档案和契约规则，框架不提供具体生物数值。**能不能被契约由代码决定**（目标生物要实现 `Contractable`），数据包只能用契约类型自己的实体类型标签与条件收窄名单，另可约定代价、每人上限、召回冷却，以及**主人侧**的三个时刻行为与存续期授予的能力（`owner_bind_action` / `owner_release_action` / `owner_death_action` + `owner_abilities`）。详见[数据包格式](../../数据包格式.md#contract_type)。
- `secret_realm`：秘境模板——每次进入按它开出一份独立维度的实例，声明维度怎么生成、边界多大、要放哪些结构、从哪进、谁来认领，以及进出的条件与行为。详见[数据包格式](../../数据包格式.md#secret_realm)。
- `spirit_herb`：绑定现有物品的灵植。可写药性药力与寒热；写了 `growth` 才能在灵田 `mxt:spirit_herb_plot` 单格培育。药龄在组件 `mxt:herb_age`。详见[数据包格式](../../数据包格式.md#spirit_herb)。
- `artifact`：法器——用 `items` 认领已有物品，用 `abilities`（固有分派）声明授予技能、载人飞行、自带储物与**周期性代价**（`mxt:upkeep`），用 `spirit_capacity` 声明每种灵气的上限，用 `hold_ticks` / `claim_condition` / `claim_action` / `pour_action` / `use_action` 声明长按（未认主时认主、认主后注入自身灵气）。认主的代价就是 `claim_action` 的默认值（扣 4 点生命），想免费就写 `"claim_action": {"type": "mxt:no_op"}`。字段与内置类型见[数据包格式](../../数据包格式.md#artifact)。
- `talisman`：符箓定义——铭刻后授予的 `ability`、载体能装下几次发动的灌注容量倍率 `capacity`、每次发动的代价 `costs`（灵气条目从载体自己的存量里扣，其余向持有者收）、**能不能用**的门槛 `condition`（对持有者判定、排在 `costs` 之前，不满足就拒绝且什么都没动）、载体自己的磨损（`durability` / `consume`）。**定义里没有 `quality`**：载体组件装的是一列符的 id，堆上没有单份定义可问，所以符的档由画符配方的 `grades[].quality` 在铭刻时写进 `mxt:quality` 组件，写不上才由数据表 `default_quality` 按载体物品兜底；“已经铭刻了哪些符箓”由物品 `mxt:talisman` 的组件保存，见[数据包格式](../../数据包格式.md#符箓)。

> 称号（`title`）、徽章（`badge`）与宗门（`sect`）曾是预留注册表，现已彻底移除，详见[数据包格式](../../数据包格式.md#动态注册表)。
