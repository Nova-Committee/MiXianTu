# 118 NeoForge 灵气存取与传输

基准为 MiXianTu `f1f728cc` 与 NeoForge `26.1.2.109`。小数端点由 [AuraHandler](../src/main/java/com/iafenvoy/mxt/api/AuraHandler.java) 定义，搬运由 [AuraHandlerUtil](../src/main/java/com/iafenvoy/mxt/runtime/spirit/AuraHandlerUtil.java) 执行，方块和物品端点由 [MxtCapabilities](../src/main/java/com/iafenvoy/mxt/registry/MxtCapabilities.java) 注册。

建议仿照 NeoForge `EnergyHandler` 的能力注册方式，定义独立的 `AuraHandler`。灵气库存、公式和传输数量继续用 `double`，能力查找和事务复用 NeoForge。需要与通用物流联动时，再为同一库存提供明确计量单位的 `ResourceHandler` 视图。

## 1. 平台能力

| 职责 | NeoForge 26.1.2.109 提供的能力 |
| --- | --- |
| 资源标识 | 自定义不可变的 `Resource`，以 `equals` 和 `hashCode` 比较。灵气适配器可以用完整的 `mxt:aura` 条目 ID 标识介质。 |
| 库存访问 | `ResourceHandler<T>` 按索引枚举介质。存量和容量可读为 `long`，插入量、提取量和实际移动量为 `int`。容量与 `isValid` 是提示，实际可用量由事务中的操作返回。 |
| 能力注册 | `RegisterCapabilitiesEvent` 支持按方块、方块实体类型、物品、实体类型注册提供者。方块端可带方向，物品端可使用 `ItemAccess` 定位并替换实际槽位中的物品。 |
| 库存搬运 | `ResourceHandlerUtil.move` 按过滤条件从来源搬到目标，返回合计搬运量。只传一种灵气时用精确 ID 过滤。`moveFirst` 只搬第一种成功转移的介质，返回介质和数量。`moveStacking` 与 `moveFirstStacking` 优先填已有同类资源的索引。 |
| 事务 | 外层 `Transaction` 决定提交，子事务可用于探测，退出而未提交会还原。`SnapshotJournal` 管理恢复状态，`onRootCommit` 用于标脏、更新客户端或其他提交后操作。 |

`move` 会先在子事务里试取来源，再尝试插入目标，并从来源实取目标实际接受的量。如果实取量与插入量不一致，该段转移回滚。需要精确搬运时传入外层事务，仅在返回量等于请求量时提交。需要模拟时，在外层事务关闭前读取结果并让它回滚。`moveFirst` 在未传外层事务时可能分别提交多个索引上的转移，需要整次回滚时传入同一外层事务。

```java
try (var transaction = Transaction.openRoot()) {
    int moved = ResourceHandlerUtil.move(source, target, aura::equals, requestedUnits, transaction);
    if (moved == requestedUnits) transaction.commit();
}
```

这里的传输发生在调用方持有的两个库存端点之间。管道连接、方向搜索、吞吐与 tick 调度仍由物流调用方决定。NeoForge 将 `EnergyHandler` 直接注册为能量能力的接口类型，并由 `EnergyHandlerUtil.move` 操作一份整数能量余额。MXT 可以沿用这种组织方式，将 `AuraHandler` 直接注册为灵气能力的接口类型。

[NeoForge 对应版本源码包](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.109/neoforge-26.1.2.109-sources.jar) · `transfer/energy/`、`transfer/ResourceHandler.java`、`transfer/ResourceHandlerUtil.java`、`transfer/transaction/`、`capabilities/Capabilities.java`、`capabilities/RegisterCapabilitiesEvent.java`

## 2. MiXianTu 的迁移边界

| 现有职责 | 接入方式 |
| --- | --- |
| 方块实体与物品堆存取 | 能力端点统一表达介质、库存和容量。把工作台、展示架、灵气容器和其他模组的提供者交给同一查询出口。业务规则保存在现有库存和定义中。 |
| 手持灌注、符箓充满触发 | `UseItemAuraAccess` 还描述灌注条件、速率与充满动作。把存取接到统一端点，继续由灌注业务解释操作者、消耗倍率和充满动作。 |
| 支付与来源身份 | `AuraCostSource` 在报价阶段绑定既定金额和物理来源。`CostPayment` 继续按原列表诊断重复目标、逐条模拟和逐条实扣。单条可用事务实现精确扣除。整份账单实际扣除失败时照常 WARN 并继续。 |
| 环境灵气与阵法 | `AuraChunkAttachment` 持有子区块库存。位置浓度、区域覆盖、方块贡献、再生和阵法份额由灵气服务计算。对外端点只可访问明确提供的库存。位置浓度会合并多处贡献，直接按该值提款会把同一库存重复计入。 |
| 客户端数据同步 | HUD 和 KubeJS 使用服务端发出的实际与环境浓度采样。库存事务的提交负责触发适用的容器更新，环境采样继续走 `AuraStateS2CPayload`。 |

`AuraHandler` 返回**已移动量**，物品堆存取接口 `ItemAuraAccess` 返回**未移动量**，其调用仍接收操作者、注册表及 `exact` 和 `simulate` 参数。灵气端点按 `mxt:aura` 辨别介质，个人账户按该灵气引用的 Saturn 数值记账。

相关入口见 [灌注入口](../src/main/java/com/iafenvoy/mxt/runtime/spirit/SpiritChargeService.java)、[费用来源](../src/main/java/com/iafenvoy/mxt/data/cost/context/AuraCostSources.java)、[世界库存访问](../src/main/java/com/iafenvoy/mxt/runtime/world/WorldAuraAccess.java)、[阵法与玩家分配](../src/main/java/com/iafenvoy/mxt/runtime/cultivation/AuraDistributionService.java)、[环境同步](../src/main/java/com/iafenvoy/mxt/network/payload/AuraStateS2CPayload.java)。

## 3. 推荐实现

### 3.1 AuraHandler 与能力查找

新增 `api/AuraHandler`，以 `Holder<Aura>` 直接指定要操作的灵气。接口支持同一库存保存多种灵气，返回只读的种类集合，包括当前储有的种类及当前为空但能够接收的种类。

```java
public interface AuraHandler {
    Set<Holder<Aura>> getAuras();

    double getAmount(Holder<Aura> aura);

    double getCapacity(Holder<Aura> aura);

    Object storageIdentity(Holder<Aura> aura);

    double insert(Holder<Aura> aura, double amount, TransactionContext transaction);

    double extract(Holder<Aura> aura, double amount, TransactionContext transaction);
}
```

插入和提取接受有限、非负的请求，返回实际处理量。未知灵气的存量和容量为零。每个端点绑定实际库存，`storageIdentity` 依相等性识别同一库存的多个包装器，供搬运和重复扣除检查共用。区块库存以实际世界和子区块坐标识别，物品库存以实际持有位置识别。

用 `mxt:aura_handler` 声明方块、物品能力，提供者通过 `RegisterCapabilitiesEvent` 按类型登记。方块查询上下文提供方向与可选操作者，注册表可从世界读取。物品查询上下文显式提供 `ItemAccess`、注册表和可选操作者。通过当前物品堆查询能力，并在处理库存时写回对应 `ItemAccess`。自动化调用可省略操作者。实体端点的采用条件见 §3.6。

`ItemAccess` 可指向玩家手、菜单光标或容器槽。`forStack` 适用于允许直接改写且物品类型固定的堆。整堆换成其他物品、拆分或退回不同数据组件的物品时需要真实槽位。展示架转发到当前展示槽，目标栈替换后重新解析。

能力查询前先用现有的非阻塞已加载区块入口拿到 `LevelChunk`，并从中取得端点所需状态。长期缓存按位置和方向建立，并响应能力变化。直接让能力查询读取未知区块可能走到 `getBlockState` 或 `getBlockEntity`。在服务端线程内开启和关闭事务，引用存活范围限于该次操作。

方块与子区块统一使用 `AuraHandler`。展示架始终提供端点并转发到当前展示物的能力，空槽的接收量为零，脉冲仍在展示架上方的交互范围内命中。访问第三方方块库存时，从 `BLOCK_AURA` 查询。绑定付款库存时通过 `AuraCostSources.handler`。

### 3.2 搬运和提交

`AuraHandlerUtil.move` 接收来源、目标、灵气、`double` 请求量和可选的外层 `TransactionContext`。先在子事务里试取来源，再在搬运事务里写入目标并从来源提取目标可接受的量。符合金额校验时，部分或足额实扣提交到外层，模拟回滚。调用方省略外层事务时，由 `move` 开启根事务。末尾返回两端实际变化中的较小值，且最多返回请求量。指向同一库存的端点返回零。

每个库存使用稳定的 `SnapshotJournal`。变更前保存可还原的库存状态。物品由真实槽位的事务记录覆盖组件和数量，方块实体与区块在根提交后标脏。展示架和灌注在根提交后比较该次事务前后的库存，确认有净灌入、持有位置仍对应原目标且满足触发条件，才执行充满动作。符箓、发包和掉落均在提交后处理。

普通一比一搬运可以走上述共同事务。手持灌注会经 Saturn `ResourceService` 支付按倍率计算的成本，且可能触发事件或被余额上限截断。只有来源扣款也能参与回滚时，才能把两者作为一次原子搬运。首期保留灌注业务路径，单独验证实际入库量、已扣量与充满动作之间的关系。个人资源账户如需开放实体能力，沿用 Saturn 的资源边界与上下文。灵气脉冲仍由命中逻辑决定接收端与余量的玩法，能力只负责实际接收。

### 3.3 小数精度与足额判断

MXT 内部的灵气库存、数据包数值、公式结果和搬运请求继续用 `double`。NeoForge 的 `TransactionContext` 负责提交与回滚，可以给自定义的小数端点直接使用。快照保存变更前的余额，回滚时直接恢复该值，避免通过反向加减引入额外舍入。

- **试算和实算共用金额逻辑。** `AuraHandlerUtil.requireNonNegative` 校验金额有限且非负。比较两笔金额时，容差取相关最大数值的两个浮点间距、绝对差额 `1e-9`、两笔金额中较小者的百万分之一这三者的最小值。比如库存是 `0.3`，请求来自 `0.1 + 0.2`，允许把这点浮点尾差视为足额。正数请求面对零库存或零可用容量时直接拒绝。
- **核对库存实际变动。** 依据两端变更前后的余额核对实际扣除与入库量。两端数额不一致或实际变动超过请求，并且差值超出容差时，取消该次搬运。极小金额加到大余额上可能舍入回原值，例如给 `1e16` 增加 `0.01`。任一端余额未变时以零移动处理，并恢复另一端的余额。
- **统一 `exact` 与 `simulate` 的提交规则。** `exact` 使用同一容差判断实际移动量是否足额。`simulate` 执行同一计算，再回滚事务。普通扣费继续沿用单项的足额判定和账单现有的失败策略。

### 3.4 通用物流的整数接口

首版先实现小数端点。有第三方物流接入需求时，再给同一库存暴露 `ResourceHandler<AuraResource>`，使用 `ResourceHandlerUtil.move`。适配器的 `AuraResource` 使用完整注册 ID 和独立空值。索引对应同一次能力查询中稳定排序的介质，并包括可插入的空槽。

整数接口需要公开固定换算单位。例如选 `1/1000` 灵气为单位时，单次最大请求为 `2_147_483.647` 灵气。小于一个单位的余额留在原库存，容量和数量读数转换时需要处理 `long` 上界。试写时只接受整数单位能表达的实际变化，向调用方报告对应整数，超出精度或无法可靠表达的边界返回零。刻度要在提供端与调用端一致，取两位小数的界面显示与该选型独立。整个游戏改用定点数会影响公式结果、区块分摊和既有金额，属于独立的数值设计。

### 3.5 BigDecimal 评估

**AuraHandler、灵气库存、环境计算及同步采用 `double`。** `BigDecimal` 可以精确地加减由十进制输入解析出的有限小数。灵气的距离衰减、区块分摊和阵法权重包含除法，遇到 `1 / 3` 这类无限循环小数，十进制实现仍需统一精度和舍入模式。Saturn 的 `NumberProvider` 输出 `double`，事后转成 `BigDecimal` 会带入现有误差。例如 `BigDecimal.valueOf(0.1 + 0.2)` 得到 `0.30000000000000004`。[环境加权](../src/main/java/com/iafenvoy/mxt/runtime/world/AuraService.java) · [灵气分配](../src/main/java/com/iafenvoy/mxt/runtime/cultivation/AuraDistributionService.java)

`double` 的粒度随余额增大。以余额 `1e9` 为例，相邻值相差约 `0.00000012`，余额到 `1e15` 时已相差 `0.125`。堆级存取计算的 `AuraTransfers.covers` 使用 `1e-12` 的纯相对容差，请求 `1e12` 时即使少 `1` 也可能通过。新搬运入口使用 §3.3 的绝对上限，并将这个案例列入探针。界面显示的两位小数只用于格式化。[旧存取容差](../src/main/java/com/iafenvoy/mxt/runtime/spirit/AuraTransfers.java)

本机使用 Java 25.0.2、Core Ultra 9 285H、固定 256 MiB 堆。每个实现独立启动三个 JVM，每个 JVM 预热 10 批、测量 9 批。表中为三次进程内中位数的中位数。余额搬运测双端加减，输入为预构造的三位小数。邻域加权每次查询 49 个来源，每个来源执行一次倒数、乘法和累加。分配量统计当前线程累计分配的字节数。

| 算术场景 | `double` | `BigDecimal` | `BigDecimal` 分配量 |
| --- | ---: | ---: | ---: |
| 双端余额更新 | 0.53 ns/次 | 11.09 ns/次 | 80 B/次 |
| 49 来源加权，`DECIMAL64` | 36.99 ns/次 | 3.56 μs/次 | 约 16.6 KiB/次 |
| 49 来源加权，`DECIMAL128` | 36.99 ns/次 | 9.09 μs/次 | 约 45.1 KiB/次 |

两组 `double` 算术均测得约 0 B/次分配。测量范围是单线程算术，游戏中的方块查询、缓存、事件和事务快照均在测量范围之外。区块每段都包含若干灵气池，全面换型还会增加常驻堆占用，并涉及物品组件、区块存档和网络编码的迁移。现有灵气池用正无穷表示无上限，十进制表示需要显式的无上限状态。[灵气池与编码](../src/main/java/com/iafenvoy/mxt/runtime/world/AuraPool.java) · [物品灵气组件](../src/main/java/com/iafenvoy/mxt/data/aura/SpiritStorageComponent.java)

只有明确要求余额按十进制最小单位严格对账的场景，才有理由为该账本单独评估 `BigDecimal`。届时需从数据输入到结算、持久化和对外读取统一规定精度，公式边界也要明确采用哪种舍入规则。

### 3.6 实体能力的采用条件

**当前建议暂缓新增 `ENTITY_AURA`。第一个需要与物品、方块直接搬运灵气的实体出现时，再注册实体能力，并由实体类型显式提供。** NeoForge 支持 `EntityCapability<AuraHandler, EntityContext>`，可以在 `MxtCapabilities` 沿用 `mxt:aura_handler`。`RegisterCapabilitiesEvent.registerEntity` 匹配具体 `EntityType`，调用者可以对已取得的实体执行 `entity.getCapability`，提供者还能按每个实例的状态决定是否返回端点。注册和查询本身无需 Mixin、实体扫描或额外的 tick 调度。 [能力注册](../src/main/java/com/iafenvoy/mxt/registry/MxtCapabilities.java) · [NeoForge 源码包](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.109/neoforge-26.1.2.109-sources.jar)

| 场景 | 建议 |
| --- | --- |
| 玩家或生物的个人资源账户 | 继续由 Saturn 处理技能扣费、实体间资源转移和资源变化。若以后要让机器直接给灵宠充能或让玩家授权自动化供取，再按具体玩法适配相应资源。持有 `RESOURCE_HOLDER` 只表示实体有一份账户，开放范围由该玩法单独决定。 |
| 附属设计的移动灵气容器或可充能载具 | 适合作为首个实体端点。该实体已有专门用于交换的库存时，可直接为同一份库存加事务能力，复用 `AuraHandlerUtil.move`，支持与方块或物品之间的部分搬运、足额检查与回滚。 |
| 持有物品的实体 | 继续通过 `ITEM_AURA` 和对应物品槽位访问。装备、手持物和掉落物携带的库存归所在物品堆，实体无需额外聚合这些库存。 |
| 灵气脉冲等临时实体 | 脉冲当前只处理方块命中。若将来允许脉冲给指定实体充能，需先定义命中、允许接收的对象及余量处理，再由命中逻辑查询目标能力。 |

当前领用个人灵气的费用已经按 `Aura.resource()` 进入 Saturn `ResourceChannel`，Saturn 也提供 `transfer_resource` 实体间动作。专用实体容器接入后的主要收益是与方块、物品共享事务协议。目标拒收或部分接收时可按实际入库量扣除，外层事务取消时可恢复双方库存。 [费用来源](../src/main/java/com/iafenvoy/mxt/data/cost/context/AuraCostSources.java) · [脉冲命中](../src/main/java/com/iafenvoy/mxt/entity/SpiritBurstEntity.java)

若采用实体端点，查询上下文只需携带可选操作者。提供者按实例决定可查询、可存入和可取出的灵气，操作者为空时仅开放明确允许自动化的端点。持有者可施展某种灵气的 `use_condition` 由玩法调用，库存授权由端点单独判断。每次交互在服务端查询仍有效的实体，长时间的外部连接按需重新定位和查询端点。端点查询只读既有库存，移除、卸载或换维度后的失效引用应拒绝写入。 [灵气使用条件](../src/main/java/com/iafenvoy/mxt/runtime/resource/ResourceUseService.java)

如需将个人账户接入，先由 Saturn 提供可加入 NeoForge 事务的资源操作。当前 `ResourceService.change` 会按公式初始化和钳制余额，写入 `ResourceHolderAttachment` 时同时标脏。`ResourceTransactions.tryConsume` 属于 Saturn 的一次性结算入口，签名未接收 `TransactionContext`。适配需要让同一账户的所有包装器共享事务状态，快照包含余额、审计记录和条目存在状态，根提交后再同步。`getAmount` 读取账户实际余额，存取在持有者的公式上下文里计算上下限。仅显式开放有效范围符合非负库存约定的灵气，提取量以超过下限的余额为限，插入量以距上限的空间为限。额度按两端观察到的实际变化核对。数值越界后的重绑定交给原有资源生命周期处理。

同一条 Saturn 余额的库存身份由实际账户和 `Holder<Resource>` 确定，灵气到资源的对应关系沿用现有注册表校验。若未来同时把同一账户用于 `AuraCost` 与 Saturn 资源费用，还要复用 Saturn 的账户费用目标，确保账单按实际余额识别重复扣除。验证覆盖所有者允许与拒绝、只进不出、双端回滚、上下限和小数、查询不创建附件，以及多包装器识别为同一账户。性能关注点是高频连接的公式求值与提交同步次数，基线待出现实际调用场景时测量。 [灵气与资源定义](../src/main/java/com/iafenvoy/mxt/data/aura/Aura.java) · [注册表校验](../src/main/java/com/iafenvoy/mxt/registry/cache/RealmChainCache.java)

## 4. 实施顺序与验证

`mxt:aura_handler` 的方块端点覆盖灵气工作台和展示架，物品端点覆盖四档灵石、符箓和灵力容器。展示架按当前物品转发能力，并在根事务提交后调用充满动作。玩家手持灌注与灵力容器截收脉冲通过真实物品栏槽位写入。非玩家手部与已取出的物品堆使用仅更新既有物品堆的访问，避开平台装备槽对普通副手物品的插入限制。灵力容器经能力灌入，持有者取用仍通过蓄放手势。符箓命令初始化库存时直接写入随即发放的物品堆。

区块端点显式绑定世界与子区块，读取既有库存，能力查询不会同步加载区块。环境灵气的计算、再生与分摊继续由现行灵气服务执行。手持灌注的 Saturn 付款与库存写入分开结算。单项灵气账单的模拟与实扣分别执行事务，账单顺序和失败处理由 Saturn 管理。整数物流视图与供热能力属于后续接入范围。

1. 先实现小数能力、稳定的库存身份和事务快照，接入工作台及单件灵气容器。使用第三方或原版类型上的外部提供者验证 A08 的灵气适配需求。动态供热另有自己的数值和位置上下文，继续使用 [A08](audit/外部开发接口与数据包审查.md) 的独立设计。
2. 接入展示架和物品真实槽位，再接灵气脉冲与单条灵气费用。区块分配仍由现行策略决定来源，按需为明确授权的库存提供端点。
3. 覆盖部分搬运、精确取消、嵌套事务、同库存回绝、未知介质、区块卸载、物品替换和提交后回调只触发一次。验证 `0.001` 以下的小数、`0.1 + 0.2` 的尾差、大额请求中真实短缺的拒绝、极小金额写入大余额时的双端回滚，以及原序实扣失败后继续执行。
4. 整数物流接入时验证单整数单位、低于单位的残量、单次请求上界和大数精度，并核对两侧实际库存的变化。客户端另外验证容器更新、环境 HUD 和跨维度后的采样。

本体与测试包编译通过。独立 Java 进程使用 NeoForge 事务执行了 `verify/aura_handler/transfer` 的 22 项断言。服务端 GameTest 实跑 **21 组全部通过**，包括 5 组灵气能力探针、6 组小数灵气、6 组符箓、3 组灵石袋和 1 组生物脉冲。灵气能力探针覆盖已放置工作台、测试附属注册的方向能力、灵气脉冲命中、物品栏回滚、展示架充满回调与子区块库存。物品栏探针分别验证旧格式的整堆余额与当前格式的单颗余额。

移除 `AuraAccess` 后，服务端回归 5 组灵气能力、6 组小数灵气和 13 组费用测试，**24 组全部通过**。本次加测空展示架的脉冲命中，以及实扣不足时回滚该笔费用并继续执行后续费用。

清理旧数据读取后，灵石只读取标有 `per_item: true` 的余额，缺少标记的整堆余额按空处理。普通燃料只接受 `per_item` 对象。物品堆和物品模板均不再换算旧值，探针改为验证单件余额的存档往返与拆堆守恒。
