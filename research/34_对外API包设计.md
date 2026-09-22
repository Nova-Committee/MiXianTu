# 34 对外 API 包设计

> **基准**：2026-09-22 的工作树（`src/main/java/com/iafenvoy/mxt/`）、[`docs/guide/java/interfaces.md`](../docs/guide/java/interfaces.md)、[`docs/guide/java/api.md`](../docs/guide/java/api.md)。
> **状态**：§1–§3、§6 已落地（只编译未实跑；搬动是纯重命名）；§4 是本轮明确推迟的部分。

## 1. 为什么要有一个 api 包

在此之前，别的模组要接进来的那几个接口散在两个包里：

- `runtime/spirit/` 的 `AuraAccess`、`ItemAuraAccess`、`UseItemAuraAccess`——和 `SpiritChargeService`、`SpiritPour`、`SpiritSource` 这些**实现**住在一起；
- `screen/wheel/` 的 `WheelMenuEntry`——和 `WheelMenuScreen`、`WheelMenuController` 这些**客户端实现**住在一起。

于是"我要实现这个接口"看起来等于"我要依赖那个模块的内部包"，而包名本身还在说另一件事（`runtime` / `screen` / `data` 都是**本体的分层**，不是对外契约的分层）。一个 `com.iafenvoy.mxt.api` 让扩展点有唯一的名字：**实现什么就看这个包**。

## 2. 结论：`api` 只放契约

1. **只放接口**（以及 `api/package-info.java` 一句口径）：没有实现、没有服务、没有 Manager、没有辅助类。
2. **实现留在原包**：`AuraAccess` 的读取方、`WheelMenuEntry` 的渲染方、`ToggableArtifactAbility` 的两个固有类型都还在各自的模块里，`api` 不中转、不缓存、不改写任何东西。
3. **搬动是纯重命名**：`package` 行、必要的 `import` 与 Javadoc 里的全限定名之外，一个字符都没改；方法签名、默认实现、运行时行为完全不变，因此没有可观测的行为差异。
4. **数据包面与 Java 面分开**：数据包作者写的 `type`（Action / Condition / Trigger …）不算这个包的成员，它们的接口留在 `data/`（见 §5）。

## 3. 本轮搬进来的四个接口

| 接口 | 原位置 | 谁在实现 / 使用 |
| --- | --- | --- |
| `AuraAccess` | `runtime/spirit` | 展示架、灵气工作台（方块实体的灵气存取面），灵力射线按它找落点 |
| `ItemAuraAccess` | `runtime/spirit` | 灵石、符箓载体；`mxt:spirit_storage` 匹配器按它认物品 |
| `UseItemAuraAccess` | `runtime/spirit` | 继承 `ItemAuraAccess`，多出手势的三个问题（`pour` / `canPourInto` / `onCharged`） |
| `WheelMenuEntry` | `screen/wheel` | 轮盘条目契约，技能 / 灵气 / 法器技能各一个实现 |

四个接口的 Javadoc 一并搬了过来；包外目标（`WheelSelection`、`WheelMenuProvider`、`SpiritChargeService`、`SpiritChargeHold`）在 Javadoc 里改成全限定名，`{@link}` 不会因为搬家而断链。

**同日回撤**：`ToggableArtifactAbility` 一度也搬进了 `api`，随后按用户判断**搬回 `data/artifact/ability`**——它不算对外 API，而是本体自己登记法器技能类型的形状（`mxt:flight` / `mxt:storage` 两个固有类型实现它，轮盘按它认"要按键的条目"）。判断规则记在这里：**`api` 收的是"外部实现者要实现的契约"**；一种本体内部固有一套类型、外部只是间接消费的接口，留在它自己的模块里。

## 4. 推迟：服务类的静态代理

原始要求是「接口直接移动过去，**Manager 等为了防止语义混乱写个静态代理类代理 API**」。本轮拍板：**先把接口搬完，服务类暂时不开代理**（用户 2026-09-22 的决定："服务类暂时不需要"）。

将来要做的话，形状定成这样，别再重新讨论：

- **一个域一个纯转发类**，命名 `XxxApi`（`AbilityApi`、`AuraApi`、`ResourceApi`、`CultivationApi`、`CurseApi`、`FormationApi`、`CurrencyApi`、`FriendApi`…），只有 `public static` 方法，方法体一行转发到 `runtime.*` 里的服务；
- **不把服务搬进 `api`**——那正是要避免的语义混乱：`api` 里躺着实现，下一个人就分不清"这个类是契约还是本体的一部分"；
- **不做**汇总全部域的单个 `MxtApi`：文件太大，且与 KubeJS 侧"一个域一个全局对象"的既有形状不一致；
- 代理只是**换个名字**，不会让服务变得更安全：服务端权威、客户端一律返回 `false` / `SERVER_ONLY` 这些语义仍然由服务自己保证，代理不许加逻辑。

## 5. 明确不做 / 推迟

| 项 | 为什么 |
| --- | --- |
| 事件（`event/**`） | 用户 2026-09-22 明确"事件不用动"；NeoForge 事件类本来就是公开面，且有它自己的阶段语义 |
| `ToggableArtifactAbility` | 一度搬进 `api`，同日**搬回 `data/artifact/ability`**：它是本体登记法器技能类型的形状，不算对外 API（见 §3 末尾） |
| 数据包固有类型接口（`EntityAction` / `EntityCondition` / `AbilityType` / `Trigger` / `TimelineEntry` / `DataStorage` / `ResourceValueProvider` …约 30 个） | 它们是**数据包作者**的扩展点（JSON 里写 `type`），不是 Java 侧调用面；搬动要动数百处 `import`，换来的只是包名好看 |
| `ItemMatcher`、`NumberProvider`、`Cost`、`FormationActionType` | 同上，先留在 `util` / `data`；它们已经在 `docs/guide/java/api.md` 里作为公开 API 登记，使用者照文档找得到 |
| `ISpiritStorage`、`ISpiritEnergy` | 目前实现者只有本体自己（`ArtifactStorageService`、`ArtifactSpiritEnergy`），还没有第三方要接；等真有外部实现者再搬 |
| 从属类型 `SpiritPour`、`SpiritSource`、`WheelSelection`、`WheelEntryKind`、`IconReference` | 它们出现在这四个接口的签名里，外部实现者今天仍得从 `runtime` / `screen` / `data` import。**这是下一轮最该收的口子**（`ToggableArtifactAbility` 那条已经不存在了：它回了 `data/artifact/ability`，和它的父接口 `ArtifactAbility`、参数 `ArtifactToggleContext` 又住在一起） |

## 6. 验证方式

- `./gradlew compileJava compileTestModJava --console=plain` → **BUILD SUCCESSFUL**（搬动完成、import 收口之后）。
- 复核旧全限定名已清零：全仓 `*.java` 里搜不到 `com.iafenvoy.mxt.runtime.spirit.AuraAccess`、`...ItemAuraAccess`、`...UseItemAuraAccess`、`com.iafenvoy.mxt.screen.wheel.WheelMenuEntry`；反向也不该存在 `com.iafenvoy.mxt.api.ToggableArtifactAbility`（同日回撤，它现在是 `com.iafenvoy.mxt.data.artifact.ability.ToggableArtifactAbility`）。
- **没实跑**：搬动是纯重命名，编译期已经把"符号找不到"这类错误全暴露出来，行为不可观测，所以没有开客户端；将来做 §4 的代理层时必须补一次实机（转发是否改变了调用时机、是否丢了 `SERVER_ONLY` 分支）。
