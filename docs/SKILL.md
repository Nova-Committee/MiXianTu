# MiXianTu AI 快速技能

## 定位

MiXianTu 是 Minecraft `26.1.2` / NeoForge `26.1.2.99` 的服务端权威修仙框架，Mod ID 为 `mxt`。Curios 是必需前置；KubeJS、JEI、Jade 为可选扩展。项目未发布，不保留旧数据兼容性。

## 工作顺序

1. 先读 `README-zh.md`、`docs/index.md`、`docs/ai/FORMAT.md` 和相关 `research/`。
2. 检查当前工作树，保留用户改动。
3. 从当前 Definition Codec 和测试数据确认字段，不凭旧文档猜测。
4. 分清服务端结算、客户端显示和 C2S/S2C 网络边界。
5. 修改后运行 `gradlew compileJava compileTestModJava --offline --no-daemon --console=plain`。

## 代码约定

- 动态定义使用 NeoForge 原版数据包注册表；固有行为使用 `MapCodec` 注册表和 `type` 分派。
- **数据包 JSON 鼓励按类别分文件夹**（`data/<命名空间>/mxt/<注册表名>/<分类>/<名字>.json`）：目录会进入定义 ID（`example:sword/slash`），所以分类要在写内容之前定好；标签那棵树不必逐层对应，层级也不参与加载与校验。规则见 `docs/数据包格式.md` 的「文件位置」。
- Definition 的 `CODEC` 是 Holder Codec，`DIRECT_CODEC` 是直接对象 Codec。
- 数据包对象和 Codec 集合视为不可变；不做无意义的 Mutable 转换或 `copyOf`。
- 跨表引用优先 Holder；可选引用用 optional Codec，列表/Map 用容错集合 Codec。
- 行为叫 `action`，判断叫 `condition`，消耗叫 `Cost`。
- 术语固定：载体与定义一律写「符箓」（`talisman`），配「符纸」「符笔」「符墨」；「符篆」是误用，不要出现在任何文案或文档里。
- 所有资源/灵气按类型独立存储；除非语义明确，不要把 Map 求和成单值。
- 服务端负责 Cost、资源扣除、修炼、境界、交易和实体行为；客户端只渲染和发请求。
- 颜色使用 `MiscCodecs.COLOR`；有限值加载失败，运行期 NaN/Infinity 警告并返回 0。
- 注释三律：命名先自解释；对外 API 之外不写成套 Javadoc（只在顺序、重入、平台怪癖、魔数来历这类硬约束上点一句）；一处不超过两行、不重复解释；长解释写进 `research/` / `docs/`。

## 公开接口重点

`AuraService` 查询灵气，`ResourceService` 修改资源，`CultivationService` 处理修炼和突破，`AbilityService` 执行技能（`useCarried` 是"由物品代持能力"的入口），`AbilityActivationService` 是"按一下"的唯一入口（轮盘、命令与脚本都走它，`Toggable` 在那里分派），`DamageCalculationService` 是模组自己发伤害的唯一出口（第一层出力在发伤害处，第二层减免在 `LivingIncomingDamageEvent`，元素克制/适应倍率住在 `element` 定义里），`MxtDatapackRegistries` 查询动态表，`AuraAccess`/`ItemAuraAccess` 处理灵气存取（键是 `Holder<Aura>`），`UseItemAuraAccess` 是物品"按住右键被灌注"的接口（`pour` 自定义容量、`canPourInto` 在付灵气前否掉一 tick、`onCharged` 汇报已写入），`SpiritChargeService` 把持有者灵气灌注进可充能物品，`TalismanService` 在符箓载体灌满时发动铭刻的能力并烧掉一张，`HoldService` 驱动"按住使用"手势，`Cost` 处理行为消耗，`WheelMenuEntry` 是纯客户端轮盘条目契约（技能与灵气各一个实现，法器技能已并入技能），`Contracts` 是"这只生物能不能被契约、它自己怎么做"的唯一查找入口（资格、操作与捕捉通知是 `api` 里的 `Contractable` / `ContractOperations` / `CaptureListener`，契约记录在 `mxt:contract` 附件上，主人由实体自己的 `OwnableEntity` 回答，主人名单在 `BoundBeastService` 的主世界索引里）；契约兽的**命令**是 `data/creature/ContractBehavior` 类 + `ContractBehaviors` 装载（内置跟随 / 游荡 / 驻守 / 召回，**不是 enum**，内容方 new 一个再 register 就多一种），输入端唯一出口是 `runtime/creature/ContractBehaviorService`（御兽铃轮盘的 `WheelSourceTypes.CONTRACT` 与 `/contract behavior` 都走它）。

完整规则见 [`docs/ai/SKILL.md`](ai/SKILL.md)。
