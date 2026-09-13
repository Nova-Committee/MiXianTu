# resource / cultivation 拆分记要

日期：2026-09-12。目的：让 `resource` 只负责"存储数值 + 展示资源条"，把修炼相关字段移入新的 `mxt:cultivation` 档案（一对一引用 `resource`）。本次为破坏性变更，不考虑数据兼容性。

## 1. 字段去向

| 原 `Resource` 字段 | 现位置 |
| --- | --- |
| `default_value`、`min`、`max`、`icon`、`particle_color`、`bars` | **留在 `resource`**（数值与资源条） |
| `regen`、`use_condition` | `cultivation`（按用户决定一并迁入） |
| `aura_type`、`burst_amount` | `cultivation`（灵气的身份与射线量） |
| `first_realm`、`start_exp`、`start_cultivate_conditions`、`cultivation_to_resource`、`resource_to_cultivation`、`show_cultivation_info` | `cultivation` |

`Resource.ResourceConversion` 内嵌类型随之搬到 `CultivationProfile.ResourceConversion`。

## 2. 结构与校验

- 新注册表 `mxt:cultivation`（`data/cultivation/CultivationProfile.java`），JSON 目录 `data/<ns>/mxt/cultivation/<id>.json`，条目 id 与被描述的 resource id 相互独立。
- **强制一对一**：`ServerCache.rebuild()` 遍历档案并按 `resource` 建表，发现第二个档案直接抛错；境界索引也从档案的 `first_realm` 建立。
- **链条以档案为键**：`RealmStage.cultivation` 指向 `cultivation` 档案（不再是 `resource`），档案再指向数值。`ServerCache` 建 `境界 -> 档案` 映射与链内序号，`indexChain` 校验链上每一级都指向同一档案（`stage.cultivation == 该档案`），并检测成环与跨链。
- 状态键与链一致：`CultivationAttachment.cultivation_progress` / `realm_stages` 现在都以 `Holder<CultivationProfile>` 为键——链本身就是档案，读状态不需要再经由阶段反查数值。存档格式随之改变（不要求兼容）。

## 3. 查找入口

`runtime/cultivation/CultivationProfiles.java`：

| 方法 | 用途 |
| --- | --- |
| `find(@Nullable Provider, Holder<Resource>/Identifier)` | 双端通用；`access == null` 时返回空而不是抛错 |
| `find(LivingEntity/Level, …)` | 从实体或客户端世界取同步后的注册表 |
| `findServer(…)` | 服务端专用，直接读服务器注册表，**不依赖 ServerCache 已构建**（启动期与命令路径用） |
| `byResource(@Nullable Provider)` | 一次扫描得到 `resource id → 档案`，供逐个数值遍历的循环使用 |
| `access(FormulaContext)` | 公式求值时的兜底访问入口（caster → target → 服务器） |

刻意不做第二份缓存：档案注册表很小，而缓存需要与注册表同步失效。

## 4. 行为变化（有意为之）

- 自然恢复只对**有档案**的数值生效（`AbilityEventBridge.onEntityTick` 先按档案过滤）；无档案的数值不再自动变化。
- `use_condition` 语义保持"门禁主动消耗 + 资源条显示"，但由档案提供；没有档案的数值视为恒真（可正常消耗与显示）。
- 信息面板的"境界/修为进度"只显示有档案的数值（原来 `show_cultivation_info` 默认 true 对所有资源生效）。
- 灵气相关读取（灵根亲和、生物元素偏好、环境渲染、JEI、命令、灵力爆发、客户端热键栏）改成先取档案再取 `aura_type` / `burst_amount`；`particle_color` 仍是数值自己的展示字段。

## 5. 验证

- `compileJava` / `compileTestModJava` 通过；`runTestServer` 打印 `MiXianTu server audit passed`，无 `mxt` 相关 WARN/ERROR、无注册表解析失败。
- 测试包已拆分：`data/mxt_test/mxt/cultivation/{qi,soul_power,spirit_power,water_power}.json` 承接原资源文件里的修炼字段；`channel_probe`、`divine_sense`、`true_essence` 原本 `regen: 0`，因此不建档案。

## 6. 后续可做

- 若一个数值将来需要多条修炼路径，需要放宽"一数值一档案"的唯一性校验，并把 `CultivationAttachment` 的键保持为档案（已经是了），无需再动状态结构。
- `RealmStage.cultivation` 与 `CultivationProfile.first_realm` 的一致性现在是结构性的（链上每一级都指向同一档案），不再需要额外校验；剩下的是成环与"first_realm 不在自己的链上"两类，均已在 `indexChain` 覆盖。
- 遍历所有数值再解析档案的旧写法（`CultivationTriggerService.refresh`、`CultivationActionEventBridge` 的自动突破）已改为直接遍历档案，无档案的普通计数器不再被访问。
