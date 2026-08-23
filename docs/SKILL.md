# MiXianTu AI 快速技能

## 定位

MiXianTu 是 Minecraft `26.1.2` / NeoForge `26.1.2.92` 的服务端权威修仙框架，Mod ID 为 `mxt`。Curios 是必需前置；KubeJS、JEI、Jade 为可选扩展。项目未发布，不保留旧数据兼容性。

## 工作顺序

1. 先读 `README-zh.md`、`docs/index.md`、`docs/ai/FORMAT.md` 和相关 `research/`。
2. 检查当前工作树，保留用户改动。
3. 从当前 Definition Codec 和测试数据确认字段，不凭旧文档猜测。
4. 分清服务端结算、客户端显示和 C2S/S2C 网络边界。
5. 修改后运行 `gradlew compileJava compileTestModJava --offline --no-daemon --console=plain`。

## 代码约定

- 动态定义使用 NeoForge 原版数据包注册表；固有行为使用 `MapCodec` 注册表和 `type` 分派。
- Definition 的 `CODEC` 是 Holder Codec，`DIRECT_CODEC` 是直接对象 Codec。
- 数据包对象和 Codec 集合视为不可变；不做无意义的 Mutable 转换或 `copyOf`。
- 跨表引用优先 Holder；可选引用用 optional Codec，列表/Map 用容错集合 Codec。
- 行为叫 `action`，判断叫 `condition`，消耗叫 `Cost`。
- 所有资源/灵气按类型独立存储；除非语义明确，不要把 Map 求和成单值。
- 服务端负责 Cost、资源扣除、修炼、境界、交易和实体行为；客户端只渲染和发请求。
- 颜色使用 `MiscCodecs.COLOR`；有限值加载失败，运行期 NaN/Infinity 警告并返回 0。

## 公开接口重点

`AuraService` 查询灵气，`ResourceService` 修改资源，`CultivationService` 处理修炼和突破，`AbilityService` 执行技能，`MxtDatapackRegistries` 查询动态表，`SpiritAccess`/`SpiritItemAccess` 处理灵气存取，`Cost` 处理行为消耗，`HotbarEntry` 是纯客户端条目回调。

完整规则见 [`docs/ai/SKILL.md`](ai/SKILL.md)。
