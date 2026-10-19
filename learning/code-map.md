# 代码地图

| 要改什么 | 去哪 |
| --- | --- |
| 对外 Java API（别的模组实现或调用的契约） | `src/main/java/com/iafenvoy/mxt/api/`——**只放接口**（外加 `package-info`）；实现留在各自模块，服务类暂不设代理（推迟项） |
| 数据包定义（字段 / Codec / 加载期校验） | `src/main/java/com/iafenvoy/mxt/data/<模块>/` |
| 动态注册表声明 | `registry/MxtDatapackRegistries.java` + `registry/MxtResourceKeys.java`（42 张表，全部是原版 datapack registry；**没有 NeoForge 数据表**——`item_aura` / `currency` / `item_binding` / `weapon_binding` / `tool_binding` / `blueprint_binding` / `default_quality` / `incomplete` 是物品键、按 `items` 认领，`block_aura` / `heat_source` 是方块键、按 `blocks` 认领，形状与其余定义表一致，别再造第二套"按物品写值"的表） |
| 固有分派类型（条件 / 行为 / 触发器 …） | `data/condition/builtin/`、`data/action/builtin/`、`registry/Mxt*Conditions.java`、`registry/Mxt*Actions.java` |
| 原版配方类型 | `registry/MxtRecipeTypes.java`——`mxt:alchemy`、`mxt:spirit_shaped`、`mxt:spirit_shapeless`、`mxt:talisman_drawing` 四个。`mxt:formation` / `mxt:refining` 不在其中（它们没有执行者、产物恒空）；法器的产出走蓝图锻造、阵法按定义落地。**符笔蘸料不是配方**：它是物品上的原版点击钩子（照 `minecraft:bundle`——光标提着符笔点一下颜料物品就蘸，一次一份），判定与换算只有 `runtime/talisman/BrushPigmentService` 一处 |
| 模块钩子 / tick 流水线 | `event/dispatcher/`——六个内部事件（`CollectTickStagesEvent` / `CollectHoldBindingsEvent` / `CollectAbilityGrantsEvent` / `CollectPassiveAttributesEvent` / `CollectProgressionOwnersEvent` / `TriggerRehydrateEvent`，**载荷就是事件自己**）；`runtime/EntityTickStages`（按 `order` 跑的每实体 tick 表）；`runtime/progression/ProgressionOwners`（`Owner` ＋ `heldBy` / `held`）。**根 `event/` 是给别的模组听的 NeoForge 事件，`event/dispatcher` 只给本模组内部用**，见 [house-rules.md](house-rules.md) 里的「模块之间用事件，只有 tick 顺序用表」"模块之间用事件，只有 tick 顺序用表" |
| 运行时服务（结算、事务、调度） | `runtime/<模块>/` |
| 附件（存档 / 同步状态） | `attachment/` + `registry/MxtAttachments.java`——**一个模块一份附件**：拆分的判据是"这些字段属不属于同一件事"（各自拥有者 / 各自生命周期 / 转世各有开关），**不是"几个模块读它"**（`ResourceHolderAttachment` 被二十多个模块读也不拆）；同一件事的字段必须留在一起（`technique_levels` 与 `learned_techniques` 同属 `TechniqueAttachment`）。见 [`research/86`](research/86_附件按模块拆分设计.md) |
| 伤害结算 | `runtime/damage/DamageCalculationService.java`（唯一出口）、`DamageElements.java`、`DamageEventBridge.java` |
| 元素 / 灵根 / 体质 / 反应 | `runtime/cultivation/`、`runtime/element/` |
| 命令 | `command/`（一个节点一个类，`ROOT` 是 `Function<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>>` 子树工厂；服务端节点在 `command/server/`、客户端节点在 `command/client/`）+ `command/ServerCommandManager.java` / `command/ClientCommandManager.java` / `command/RegistryReferences.java` + `config/MxtServerConfig.Commands` + 两份 lang |
| KubeJS 桥接 | `compat/kubejs/MxtKubeJsApi.java`（受校验的操作）+ `compat/kubejs/binding/`（一个全局对象一个类）+ `MxtKubeJsPlugin.registerBindings` |
| 界面 / HUD / 信息面板 | `screen/` |
| 原版注入（mixin / 访问器） | `mixin/`（登记在 `src/main/resources/mxt.mixins.json` 的 `mixins` 段；访问器接口与实现分开时放 `accessor/`）——**注入只在运行时生效，编译不代表注入成功**，`defaultRequire: 1` 会让失败的注入直接崩在启动 |
| 测试探针与夹具 | `src/test-mod/java/com/iafenvoy/mxt/probe/`（**全部 GameTest**，按域分文件夹，一个测试只验一块；框架在 `Probe` / `ProbeContext` / `ProbeTests`）、`src/test-mod/java/com/iafenvoy/mxt/testmod/`（测试包自己的物品 / 方块 / 实体 / 渲染注册，**不写断言**）、`src/test-mod/resources/data/mxt_test/`（夹具与 `structure/arena.nbt`、`structure/grid.nbt` 两个空竞技场） |
| 可选依赖（GeckoLib、FTB、JEI、Jade…） | `compat/<模组>/`——**两半隔离**：不引用那个模组类型的那个类无条件可达，引用它、且**只从 mod-list 门内被触及**的那个类才是它自己（照 `compat/ftb/FtbChunksCompat` 与 `compat/geckolib/GeckoLibCompat`；这里出现一个缺失的类型就会让没装它的机器起不来）。**Jade 的两半各登记一次、且各有自己的对象**：显示组件走 `registerClient` 的 `registerBlockComponent`，实现 `IServerDataProvider` 的那个对象在 `register(IWailaCommonRegistration)` 里 `registerBlockDataProvider` 一次——只登客户端组件不会送任何数据，`accessor.getServerData()` 永远是空 NBT；而**同一个对象不许两职兼**（Jade 自 1.21.6 起在登记时直接拒收：`Data providers cannot implement IComponentProvider`，客户端启动即崩——老代码里"一个 enum 同时实现两个接口"的写法必须拆成两个对象，两边靠**相同的 `getUid()`** 配对，NBT 键与 uid 常量放在数据提供者那一侧给显示读）。`MxtJadePlugin` 是唯一登记点，Jade 插件配置页的名字在 `config.jade.plugin_mxt.<provider id>` |
| 设计稿 / 审计 / 测试设定 | `research/`——**大改、或按用户要求先设计时才留档在这里**，命名与分工见 [`research/README.md`](research/README.md) |
| 文档站（唯一一处） | `docs/`——**junction 到 `IAFEnvoy/mxt-docs` 检出**（站点内容在 `docs/docs/`：中文根 + `docs/docs/en/` 镜像，VitePress，新页要登记进 `docs/docs/.vitepress/locales/pages.mjs`）；怎么写看 [`docs/AGENTS.md`](docs/AGENTS.md)，机制看 `docs/README.md` |
