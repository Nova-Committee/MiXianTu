# 符箓（`talisman`）灌注与激发审计

审计基准：MC 26.1.2 / NeoForge 26.1.2.99。
结论来自直接读代码与本工作区实测（服务端审计 `MiXianTu server audit passed`）。

> 变更记录
> - 2026-09-17：新增本文件。按用户要求把符箓的灌注—激发闭环接上："符箓物品可以用这个方法注入灵气，满了之后就激发功能"。
> - 2026-09-17：**术语统一**：载体与定义一律叫「符箓」，原先混用的「符篆」是误用，已全库改净（代码、语言文件、docs/、research/）。顺带修掉 `actionbar.mxt.talisman.blank` 自查指自己的文案（"符箓上未铭刻任何符箓" → "符箓上未铭刻任何道法"，与 `actionbar.mxt.talisman.invoked` 的"道法"一致）。
> - 2026-09-17：**补齐夹具与审计**。新增四个符箓定义——`free_sigil`（账单为空）、`tag_sigil`（`abilities` 里写标签）、`storm_sigil`（在展示架上引雷）、`toll_sigil`（能力自带消耗）——加一条能力标签 `#mxt_test:sigil/manifold_arts` 与两个能力（`mxt_test:storm_pulse` 引雷、`mxt_test:toll_pulse` 收贡），审计相应加 5 步（§5 第 11–15 条），把此前只有代码、没有夹具覆盖的分支全部钉住：空账单走点击、标签展开、多定义合计与去重、跨定义的徒手开关、能力自身消耗与拒绝保进度、以及"原点"到达只存在于某处的行为（引雷）。手动试用的命令见 §7。
> - 2026-09-17：**顺带修掉空载体的点击文案**。`TalismanService.invokeOnUse` 原来先问 `invokableOnUse` 再发动，而"一张都没铭刻"也答"不可徒手"，于是点一张空符得到的是"此符不可徒手激发"——明明没有任何定义在拒绝。现在空载体直接交给 `invoke`，由它说"符箓上未铭刻任何道法"；有铭刻但定义不许徒手的才说那句。纯文案分支，无客户端环境不可断言（两者都返回 `FAIL`），所以只改代码、不进审计。
> - 2026-09-17：**徒手发动加了可配置冷却**（用户要求："给符箓的使用加个20tick的冷却（可配置）"）。新增服务端配置页「符箓」`talisman.use_cooldown`（默认 `20`、范围 `0..72000`、`0` 关闭，见 §5 第 17 条），挂的是原版 `Player#getCooldowns`（热键栏灰色扫描与 `mxt:on_cooldown` 条件免费可见，与功法阅读同一套）。**按"尝试"计费而不是按"发动成功"**：能力自身拒绝（它自己的冷却、消耗付不起）也算一次尝试，而空载体/未灌满/`invoke_on_use:false` 的点击不算——那几种点击本来只会得到一句说明，玩家要能立刻改。被灌满而**自动**发动的不走这条路，因此既不计也不查。为此 `invoke` 拆出一个私有 `attempt(...)`，用 `Attempt(attempted, fired)` 把"点了但没发动成"和"根本没成为一次尝试"分开。
> - 2026-09-17：**修掉"冷却好像不生效"**（用户反馈："冷却好像还不大行，用原版那套"）。用的确实是原版那套（`player.getCooldowns()`，也就是 `ItemCooldowns`），但**写冷却时交的那一堆错了**：原版 `ItemCooldowns.getCooldownGroup(stack)` 就是 `BuiltInRegistries.ITEM.getKey(stack.getItem())`，而符箓发动会 `consume(1)` 把手上那张烧掉——**单张**符用完之后那一堆已经是空堆，`getItem()` 是 `minecraft:air`，冷却于是被记在 `minecraft:air` 头上，谁也读不到：烧掉一张，从背包再拿一张立刻就能发动。原版自己不会踩这个坑，因为 `UseCooldown#apply` 收到的正是**使用前**那一堆（`ItemStack#applyAfterUseComponentSideEffects` 传的就是 `stackBeforeUsing`），它的实现也就是 `player.getCooldowns().addCooldown(stackBeforeUsing, ticks)` 这一行。现在照抄这一行：发动前留一份拷贝，发动后原样交给原版。审计第 17 条改用**单张**符（原来写的是两张，正好绕开了这个坑，是"测试骗自己"的又一例），并把断言换成"下一张符也在冷却里"——这条在修之前必失败（已用临时改回旧写法实测确认）。
> - 2026-09-17：**"是否自动激发"从定义搬到 stack 上**（用户："目前是用 `invoke_on_use` 控制是否立即激发，改成一个 stack 上面的模式（跟 `TalismanComponent` 放一起），默认是直接激发模式，shift+使用可以切换到存储模式。特别注意：如果存储模式下满了则无法切换，尝试切换将立即激发"）。据此：① `Talisman` 记录与 codec **删掉 `invoke_on_use`**（全库只有测试夹具 `stand_sigil` 用过一次），定义不再管行为；② `TalismanComponent` 加 `mode`（`fire` 缺省／`store`，`TriggerMode` 枚举 + `Codec.STRING`），缺省即"直接激发"，所以**已存在世界的载体**解码出来就是 fire、不需要迁移；③ `TalismanService.invokableOnUse` 与 `TalismanItem.use` 里那句"此符不可徒手激发"的拒绝**删除**（键 `actionbar.mxt.talisman.not_on_use` 一并从两个语言文件移除）；④ 新增 `TalismanService.autoFires`（读模式）与 `toggleMode`：潜行右键切模式并给一句 action bar 提示，`store` 且**已满**时不切换、直接 `invokeOnUse`（先写回 `fire` 再发动，因为发动会消耗这一张，剩下的堆该留在哪个模式要明确）；⑤ `onCharged` 的"满了做什么"留在 `TalismanItem`（由物品回答自己），`store` 时只提示 `actionbar.mxt.talisman.stored`、不消耗；⑥ tooltip 增一行 `tooltip.mxt.talisman.mode.*`。展示台**不认识**这套规则：它照旧只调 `UseItemAuraAccess#onCharged`，模式判断在符箓自己身上，所以"地方"与"物品"的分层没有被这次改动破坏。附带：`/talisman give` 增 `stored` 分支与模式/灌注状态回显。**未实测**（用户："你也不用测试了，我自己来测试"），只过了 `compileJava compileTestModJava`。
> - 2026-09-17：**灌注进度不再有自己的组件**（用户提示："我记得有个物品组件用来存灵气（Map存储，支持多个），做的通用一些"）。`mxt:talisman_charge` 删除，载体与灵石共用 `mxt:spirit_storage`：形状从 `(resource, amount)` 改成一张「灵气 → 已存单位」的表（`{amounts:{...}}`，键类型随后由 `Holder<Resource>` 改为 `Holder<Aura>`），**把数量记在键下**——这样"记下自己装的是哪种"不再需要额外字段，改 `item_aura.type` 也改不动已有存量的含义。缺组件读作"满"还是"空"仍然**由物品回答**（灵石满、符箓空），组件只是一份数据。顺带：灵石读它**唯一**的那条记录（`soleAura()`），创造栏的空灵石因此简化成写一个空 map、不用再查定义。

## 1. 用户拍定的三条

| 分叉 | 选择 | 备选（未采用） |
| --- | --- | --- |
| 激发时机 | **灌满立即触发**，同时消耗一件本体；另补一条右键路径 | 满后右键激发（可以提前备符、急用时再放） |
| 激发代价 | **一用即焚**（消耗载体本体） | 只清空所注灵气、本体保留 |
| 多灵气账单 | **现在就做分资源充能** | 先只支持单一灵气、多灵气账单不参与灌注 |

## 2. 设计：账单即容量

- `aura_cost` 的语义就此定下：它既是"激发要付的灵气"，也就是"载体要灌多少才算满"。于是**"满了"不是谁拍的数字，而是这一次激发确切的代价**——灌进去的正好是要花掉的。
- 按灵气分别计量：灌注进度记在一张「灵气 → 已存单位」的表里。当时落的是一张专用组件 `mxt:talisman_charge`（`Map<Holder<Resource>, Integer>`）；2026-09-17 起它与灵石共用 `mxt:spirit_storage`，键类型随后改成 `Holder<Aura>`（见文首变更记录与 `research/audit/spirit.md` §7.7）。"满了" = 账单里每一条都满。
- 一次灌注只填**一条**：按 `aura_cost` 的书写顺序取第一条未满的；填满一条后再按住会继续填下一条。顺序之所以是确定的，是因为 `CollectionCodecs.map` 解出的是保序 Map（`AutoIgnoreMapCodec` 读进 `LinkedHashMap` 再 `ImmutableMap.copyOf`）。
- 账单按**空公式上下文**求值：容量同时决定灌注时长，而客户端要为姿势算出同一个数（与 `item_aura.aura` 对容量的口径一致）。因此只在有持有者时才有值的写法（`"realm_rank * 4"`）会求出 0、被当作"这条不参与灌注"；一条都不剩的载体等同于免费符（右键即发动）。这一条是"必须两端同源"的直接后果，而不是取舍。
- 速率是常量（现归手势：`SpiritChargeService.POUR_INTAKE_PER_TICK`/`POUR_COST_PER_UNIT`）：每 tick 灌 1 单位、1 单位收 1 点自身灵气。于是账单既是价钱也是时间；不按堆叠放大（理由见 §4.4）。
- **不走 `item_aura`**：符箓没有 `item_aura` 定义，因此也不会顺手变成修炼燃料——这是它自己声明存储（`pour`）的直接好处。
- 两个手势按"是否已满"自动二分：未满 → 长按灌注（`claims` 为真，点击根本走不到 `Item.use`）；已满或账单为空 → `Item.use` 发动。此外，**灌满那一刻也自动发动一次**（用户要的"满了立即触发"）。
- 发动 = 把铭刻定义里的能力全部取出来（标签展开、去重）逐个走 `AbilityService.useCarried`；一个都没发动成就不消耗载体、不清空灵气（冷却过去后可以重试），至少一个成功才清空进度并 `stack.consume(1, holder)`。
- **载体不必在手上**：填满它的那一方负责汇报（`AuraItemAccess.onCharged`，见 §3），展示架也一样——它把 `worldPosition` 报上去，于是**行为者**是填符的人（出账、被记录、为能力作答），**位置**却是展示架。这是用户明确指出的一条："不能只传入 `LivingEntity`，因为展示架也可以激发。"
- **位置既进公式也进"在哪里发生"**：位置写成 `block_x`/`block_y`/`block_z` 进公式上下文，同时作为这次激发的**原点**（`Context` 扩展数据 `mxt:origin`）交出去：`spawn_projectile`/`spawn_particles`/`spawn_effect_cloud`/`spawn_lightning`/`explode`/`play_sound`/`block_action` 以它为位置，`mxt:area` 目标选择器以它为圆心，`mxt:teleport` 把它当作"行为者所在处"（缺省 = 行为者自己，普通施法一字未变）。朝向仍是行为者的：位置说"从哪发出"，朝向说"谁在瞄"。
- **`invoke_on_use`（数据驱动 bool，默认 true）**：灌满后能否**徒手右键激发**。`false` 表示这张符只回应"被灌满"（用户要的那个开关）；载体上多个符箓时全部允许才可徒手激发。它只管右键那条路，灌满自动触发不受影响。

## 3. 接口与 `AbilityService` 的改动

- `AuraItemAccess` 新增两个默认方法：
  - `pour(Provider, ItemStack)`：容量取决于**这一堆上写了什么**的物品只有堆自己能回答；返回按灵气分列的 `SpiritPour`（`stored`/`capacity`，顺序即灌注顺序），默认返回空 = 沿用 `item_aura` 定义那条共享读法（灵石一字未改）。每 tick 的注入与扣费**不在**这里——那是手势的事，物品只回答"我是什么"。
  - `onCharged(SpiritSource, ItemStack)`：写入者在**真实写入之后**汇报，物品自己判断是否已满并决定做什么。**由写入者汇报而不是物品在自己的 `add` 里判断**，是因为只有写入者知道这东西在哪、谁付的账；`SpiritSource(level, position, actor)` 就是这两件事。
- `SpiritChargeService` 重构为"先问物品，没有才回落到 `item_aura`"，`Charge` 变成已求值的读数（`resource`/`stored`/`capacity`/`intake`/`costPerUnit`）；`full(...)` 保留为"满没满"的对外提问（用户提的 `isFull`），它同时认两条路，所以放不进接口。
- `DisplayStandBlockEntity.add` 在真实写入后汇报自己 `worldPosition` 的位置——这就是"展示架也能激发"落地的那一行。
- `AbilityService` 新增 `useCarried(...)`：与 `use(...)` 走同一条流水线，只把 `abilities.has(ability)`（"你已习得"）换成由调用者作答，`requiresGrant` 贯穿 `prepare`、`useComposite` 与单子能力分支。
  **为什么不是"临时授予再撤销"**：`AbilityAttachment.revoke` 在最后一个来源消失时会一并清掉该能力的冷却与组件状态（充能次数），临时授予会把冷却和次数一起抹掉——那才是真的后门。审计第 4 步就是为了钉住"拒绝不消耗、冷却照算"。
- 新增 `Failure.CARRIED_NOT_INSTANT`（见 §4.1）。

## 4. 发现的问题

### 4.1 吟唱与引导的能力在载体上收不了尾

`AbilityEventBridge.finishDueCasts` 遍历的是 `abilities.sources().keySet()`（**已授予**的能力），`AbilityService.tickChannel` 也要求 `abilities.has(ability)`。载体从不授予任何东西，于是：

- 吟唱型（`cast_time > 0`）能力被载体起手后，`cast_ends_at` 会永远留在状态里，没人来收尾；
- 引导型（`mxt:channelled`）能力会在第一次脉冲时被 `NOT_GRANTED` 掐掉。

两条都不可用，所以 `useCarried` 在**起手前**就拒绝，并且是在付出任何代价之前。文案单独说明原因（`carried_not_instant`），否则作者只会看到"没反应"。

### 4.2 计费时的 clamp 可能把"实付"算大（顺手修掉）

上一轮的写法是 `units = floor(paid / unitCost)`。如果灵气池被定义上限**从上方裁掉**（例如凡人持有 `mxt_test:spirit_power`，其上限定为 0），`before - after` 会把"被裁掉的量"也算成实付，于是物品凭空多灌。现在改成 `units = min(请求量, floor(paid / unitCost))`：实测付款只可能让单位数变少，不可能变多。审计里"付不起就一动也不动"覆盖的是拒绝路径，这个上界修正由 `min` 本身保证（审计构造不出"池高于自己上限"的世界状态）。

### 4.3 账单为空的符箓

"灌满才触发"对不耗灵气的符箓没有意义（没有可灌的东西）。因此：账单为空 → 不进入灌注手势，但**随时算满** → 右键即可发动。这一条不是补丁，而是"满 = 没有缺口"的直接推论。

### 4.4 一用即焚 + 整组：容量不按堆叠放大

若容量随堆叠放大（一组 8 张 = 8 份账单），发动一次只烧一件、却按"满"清空整池，会白白浪费 7 份灵气；保留剩余又会让"未满"这个闸门失效（剩下的 7 份仍然满，却不能再灌）。所以符箓的容量就是**一张**的账单（`pour` 的数值按整堆给出，符箓选择不乘堆叠）：每次发动恰好对应一次灌注，多张就是多次灌注，既不浪费也不死锁。副产品是姿势长度恰好等于一次灌注（`ceil(容量/注入)`），因此同一手势里不会连发两次。

## 5. 审计覆盖（`verifyTalismanInvocation`）

1. 空载体：没有铭刻、不参与灌注（`resolve` 为空、`claims` 为假）、点击不消耗。
2. 账单即容量：`common_sigil` 的容量 4、已存 0、未满、姿势 4 tick，且未充能时 `ready` 为假。
3. 分资源：`twin_sigil` 的 `pour` 是两条且顺序等于书写顺序；填满第一条后 `active` 换到第二条；两条都满才算满；从满的载体里抽掉一条就又不满了。
4. 灌注到底：一 tick 进 1 单位、扣 1 点灵气；第 4 tick 灌满 → 发动（`sigil_pulse` 给持有者 +3 `mxt:common`）→ 烧掉一件（2 → 1）→ 进度清空。10 − 4 + 3 = 9，逐项对上。
5. 拒绝不花钱：把能力压进冷却后发动被拒，载体仍在手上、进度仍是满的。
6. 吟唱/引导不可承载：`channel_sigil` 发动被拒，载体不消耗。
7. **不在手上也照样发动，而且用自己所在的位置**：把 `stand_sigil`（能力是"加 `block_x` 点灵气 + 发射一支箭"）摆在 `(12,70,12)` 的展示架上，由站在别处的 `FakePlayer` 通过 `DisplayStandBlockEntity.add` 填满 → 发动：持有者恰好得到 `12.5` 点（展示架方块中心，说明**公式**读到的是展示架位置），并且箭出现在展示架中心半径 1 格内（说明**行为**也用了这个位置），载体同时被烧掉。
8. **范围与"移到行为者处"也从原点算起**：把 `pull_sigil`（`mxt:area` 半径 4 + `mxt:teleport` 把目标移到行为者处）摆在 `(-6,70,-6)` 的展示架上，一只猪站在架子旁 3 格 → 填满发动后猪出现在展示架中心 1 格内（若范围仍按行为者算，猪根本不会被选中，因为它离行为者约 70 格），载体烧掉。
9. **徒手激发的开关**：`stand_sigil` 写了 `invoke_on_use: false` → 满了之后拿在手上右键不发动、提示"此符不可徒手激发"、灵气与本体都不消耗；`common_sigil`（默认 true）同样条件下右键即发动并烧掉一张。`Talisman.DIRECT_CODEC` 同时钉住默认 true 与显式 false 两种解析。
10. 文案：六条符箓消息、两条 tooltip 提示必须有翻译；**遍历 `AbilityService.Failure.values()`** 检查每一个拒绝原因都有 `actionbar.mxt.talisman.failure.<name>`，新增原因无法漏掉文案。
11. **账单为空的符箓（`free_sigil`）**：`bill` 为空、`resolve` 为空、`claims` 为假（说明**灌注手势永不认领它**），但 `ready` 与 `invokableOnUse` 都为真 → 塞进主手点一下即发动（+3 `mxt:common`）并烧掉本体。注意这里问的是两个不同的问题：手势层的 `SpiritChargeService.full(...)` 对"没有可灌之物"的载体答**否**（没有"满"可言），而载体自己的 `TalismanService.ready(...)` 答**是**（没有缺口），点击问的是后者。
12. **`abilities` 写标签（`tag_sigil` + `#mxt_test:sigil/manifold_arts`）**：账单按定义声明的 3 点算；发动后 `mxt:common` 与 `mxt_test:divine_sense` **各 +3**（标签里两个成员都真的跑了），本体烧掉。这条保证"定义里能写 `#标签`"不只是 Codec 接受，而是运行时真的展开。
13. **多定义合计与去重**：一张载体同时写上 `common_sigil` 与 `twin_sigil` → 容量是 `mxt:common` 6 + `mxt_test:qi` 1（按定义书写顺序排），填满后发动只给 **+3**（两张都铭刻的 `sigil_pulse` 只跑一次），本体烧掉且 `mxt:spirit_storage` 被清掉（进度随本体一起走）。
14. **徒手开关跨定义取与**：`common_sigil` + `stand_sigil`（后者 `invoke_on_use: false`）→ `invokableOnUse` 为假；`common_sigil` + `twin_sigil` → 为真。一条"只认被灌满"是写在**物件**上的属性。
15. **能力自身的消耗是第二笔账（`toll_sigil` + `mxt_test:toll_pulse`）**：载体只收 1 点 `mxt:common`，能力自己还要 1 个苹果（吃完还会加 1 点 `mxt:common` 回来）。填满后身上没有苹果 → 发动被拒（`INSUFFICIENT_COST`），**进度与本体都保住，灵气也一动不动**；补上苹果后同一次发动成功：苹果被吃掉、灵气 4 → 5（能力返还的 1 点）、本体烧掉。
    - 为什么用苹果而不是灵气做"能力的账"：`mxt_test:spirit_power` 的上限是"境界 = 炼神境 → 100 + level × 10，否则 0"，一个没有境界的 `FakePlayer` 池上限就是 0，拿它当能力消耗永远付不出来，测的就不是符箓而是境界系统了。
16. **位置到达只存在于某处的行为（`storm_sigil`）**：在 `(20,70,20)` 的展示架上填满 → 落雷出现在展示架中心的水平 1 格内（若读的是填符者位置，雷会落在别处），本体烧掉。第 7、8 条证明位置进公式、进行为、进范围，这一条把"引雷"这类只在某处发生的行为也补上。
17. **徒手发动的冷却（`talisman.use_cooldown`）**：拿**单张** `free_sigil`（空账单、永远算满）走真实的"用掉一张、再拿一张"顺序——第一次点击发动并把自己那张烧成空堆；随后拿出的**另一张**符必须已经在冷却里（这正是"窗口记在物品上、不是记在刚被烧掉的那一堆上"的断言，修之前必失败）；窗口内点它 → `FAIL`，不烧也不给灵气；清掉冷却后同一张又能发动。配置读到 `0` 时断言反过来：用一次**不**进冷却（所以这条测试在关了冷却的服务器上也照样有意义，而不是直接跳过）。
    - 注意审计自己的坑：这些点击都在**同一个服务 tick** 里跑，而原版冷却按 tick 计数，所以前一次点击设下的冷却对后一次点击仍然有效。凡是"不是来测冷却的"手点（第 9、11 条）都先 `clearUseCooldown(...)` 再点，只有第 17 条故意不清。
18. **存储组件是一张表，且经数据包读取用的 ops 走一遍**（2026-09-17 合并两个整单位存储时补）：`mxt:spirit_storage` 的 JSON 写成 `{"amounts":{"mxt:common":3,"mxt_test:qi":1}}`，用 `RegistryOps.create(JsonOps.INSTANCE, registryAccess)` 解出来必须两条都在，`soleAura()` 在多于一条时**必须为空**（灵石就是靠它判断"我装的是哪一种"），编码回去再解一遍必须相等；单资源那条则相反——`EMPTY.with(common, 3)` 的 `soleAura()` 必须是 `mxt:common`，`with(common, 0)` 必须得到空表（清空是删键而不是留个 0，这样"被抽空"和"从没写过"才分得开）。这条把文档里写的组件形状也钉住了。

## 6. 边界与未闭环

- **铭刻 / 染色服务仍未接入**：`mxt:talisman` 组件目前手写或用物品组件语法写入。铭刻落地时应把 `mxt:spirit_storage` 一并移除——账单变了，旧进度没有意义（缺省即空，所以"移除"就等于重置）。
- 只承载立即生效的能力（§4.1）。
- 能力自身的冷却、次数与消耗与"自己放"共享：符箓不绕过它们；被拒绝时符箓自己也保住（可以等冷却，或换一张）。
- **能力的 `costs` 与符箓账单是两笔账**：灌注付账单（1:1，账单=容量），发动时能力自己的 `costs` 照付。这是刻意的（符是载体，法术是法术），但内容作者需要知道；第 15 条把"付不起就被拒、进度保住、补上就发动"整条路径钉住。
- **徒手冷却挂在物品上，所以同一玩家的所有符箓共享一个窗口**：原版 `ItemCooldowns` 按"玩家 + 冷却组"记，而本模组所有载体都是同一个物品 `mxt:talisman`（除非给某张符加 `use_cooldown` 组件指定别的冷却组）。要挡的正是"一秒内连点一叠符"，因此这样反而对；但如果将来要"一种符一个冷却"，那就得给载体的冷却组按定义取不同的值，或不用原版这套。
- **冷却只管徒手那条路**：被灌满而自动发动（长按灌注到底、展示架被填）既不计冷却也不查冷却——那是"被填满"，不是"被使用"；而且展示架上那张符并不属于某个玩家的手，按物品记的冷却会把填符人的所有符一起锁上。
- **游戏里挡住点击的是原版，不是我们**：`ServerPlayerGameMode.useItem` 在 `itemStack.use(...)` **之前**就先 `player.getCooldowns().isOnCooldown(itemStack)` → `PASS`（右键方块那条 `useItemOn` 的兜底分支同样带这个条件）。所以冷却期内右键**根本不会问到符箓**，玩家看到的反馈是热键栏那道灰色扫描加上"什么都没发生"——这正是原版的观感（末影珍珠、紫颂果都是这样）。`actionbar.mxt.talisman.cooldown`（"符箓尚在冷却"）留给**绕过原版检查**的调用方（服务端 API、KubeJS、审计里直接调 `TalismanItem.use`），`invokeOnUse` 里的 `coolingDown` 是这条规则的服务端半边，不是游戏内的唯一闸门。
- 一组符箓共享同一份进度：发动消耗一件后进度清空，剩下的是未充能的（§4.4）。
- 客户端只有 tooltip 提示与 action bar，没有符箓专门 HUD；tooltip 的进度条显示的是"下一次要灌的那一条灵气"。
- **位置进公式、进行为、进范围与"移到行为者处"，但不进条件与取值**：`condition` 一族（`AuraRangeEntityCondition`、`BrightnessCondition`、`ExposedToSky`/`ExposedToSun`、`InBlock`/`OnBlock`）与按位置读环境灵气的取值仍读**行为者**自身位置。要让它们也认原点，得给 `EntityConditionContext` 同样加可选原点并逐个改读——独立的一步，本轮没做。
- 朝向不属于位置：投射物按**行为者**的朝向发射（位置是"从哪发出"，朝向是"谁在瞄"）；展示架没有朝向可言。
- **灵石灌注模块会对符箓多说一句（无害）**：`SpiritChargeService.onItemUse` 的点击提示是按**物品**判断（`HoldLookup.hold(stack) instanceof SpiritChargeHold`，即"这个物品实现 `AuraItemAccess`"）而不是按**这一堆**判断（`claims` = `holdTicks != NO_HOLD`）。所以点一张满符时会先按灌注模块的口径说"此物已充满灵气"（点空符则是"此物无从容纳灵气"），紧接着载体自己才说"符箓已激发…"。两条都走 action bar（`sendSystemMessage(..., true)`），同一 tick 内后者覆盖前者，玩家实际只看到符箓自己那句，因此**只是白说一句**，不是可见错误——所以本轮没有改代码，只在这里记下。真要收干净，最小改法是让 `AuraItemAccess` 多一个"这个物品自己作答点击"的默认方法，由 `TalismanItem` 返回真，灌注模块据此闭嘴。

## 7. 手动试用（`mxt_test` 夹具）

审计之外的肉眼验证，进游戏用 `give` 直接写组件即可（`mxt:talisman` 是定义列表，`mxt:spirit_storage` 是灌注进度，与灵石共用）：

```mcfunction
# 账单为空的符：拿在手上直接右键即发动并消耗一张
give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:free_sigil"]}]

# 标签符：右键按住灌注 3 点 mxt:common（约 3 tick），灌满自动发动（加灵气 + 觉醒神识）并烧掉
give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:tag_sigil"]}]

# 引雷符：灌满 5 点 mxt:common 后落雷 + 雷声；摆在展示架上填满时雷落在展示架处
give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:storm_sigil"]}]

# 献祭符：灌满 1 点 mxt:common 后发动还要 1 个苹果，缺了就被拒且进度保住；发动成功则吃掉苹果并返还 1 点灵气
give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:toll_sigil"]}]

# 双定义载体：容量 = 6 common + 1 qi，两张符都铭刻的 sigil_pulse 只发动一次
give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:common_sigil","mxt_test:twin_sigil"]}]

# 只认被灌满（invoke_on_use:false）：立到展示架上，被填满时才发动
give @s mxt:talisman[mxt:talisman={talismans:["mxt_test:stand_sigil"]}]
```

`mxt_test` 的展示名分别是无耗符 / 众法符 / 引雷符 / 献祭符（`talisman.mxt_test.*`），所以 `give` 出来的 tooltip 与提示都是可读的中文，而不是原始键名。

