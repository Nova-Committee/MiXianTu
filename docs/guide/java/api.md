---
title: Java 公开 API
---

| API | 作用 |
| --- | --- |
| `MxtDatapackRegistries` | 查询原版动态注册表、Holder、禁用标签和客户端同步数据。 |
| `AuraService` | 查询坐标最终灵气、环境来源、分元素灵气和区域覆写。 |
| `ResourceService` | 初始化、读取、修改资源并进行最大值和恢复计算。 |
| `CultivationService` | 修炼、资源转换、境界突破和境界设置。 |
| `AbilityService` | 服务端执行技能、Cost、冷却、取消和行为。 |
| `CurrencyValueService` | 计算物品货币价值并处理不可用原因。 |
| `ItemMatcher` / `UniversalMatcher` | 匹配物品、标签、通配符、正则和混合数组。 |
| `NumberProvider` | 常量、表达式、注册表类型分派和有限值处理。 |

除非接口明确标记为客户端 API，不要在渲染线程调用服务端生命周期注册表查询。
