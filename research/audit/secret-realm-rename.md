# 秘境一系改名 `realm_instance` → `secret_realm`（2026-09-23）

> **状态：已落地（只编译，未实机）。** 基准：工作区 `src/main`、`src/test-mod`、`docs/`、`docs/模块实现审计.md`、文档站 `mxt-docs`
> 。MC 26.1.2 / NeoForge 26.1.2.99。
> 本文是这次改名的**新旧对照表与破坏性影响清单**；`22_秘境重做设计.md` 正文保留当时的 `realm_instance` 写法作为历史记录，以本文为准。

## 1. 为什么改

- 用户 2026-09-23 要求把动态注册表 `mxt:realm_instance` 改成 `mxt:secret_realm`，并选择**全量改到 `secret_realm` 一系**（连
  Java 类名一起）。
- 动因是撞名：`realm` 这个词在本仓同时承担两个意思——**境界**（`realm_stage`、公式变量 `realm` / `realm_rank`、条件
  `mxt:realm` 与 `mxt:has_realm`、`/mxt realm`）与**秘境实例**（`realm_instance`）。两个命令在 `/mxt` 下更是并排。既然「秘境」的英文已经定为
  secret realm，`secret_realm` 一系就不会再和境界混在一起。
- 模组未发布，允许不兼容：**不做兼容别名、不做迁移**。

## 2. 新旧对照（完整映射）

### 2.1 数据包看得见的

| 类别                       | 旧                                                                                       | 新                                                                                     |
|--------------------------|-----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| 动态注册表 id                 | `mxt:realm_instance`                                                                    | `mxt:secret_realm`                                                                    |
| 定义目录                     | `data/<ns>/mxt/realm_instance/`                                                         | `data/<ns>/mxt/secret_realm/`                                                         |
| 标签目录                     | `data/<ns>/tags/mxt/realm_instance/`                                                    | `data/<ns>/tags/mxt/secret_realm/`                                                    |
| 定义名语言键                   | `realm_instance.<ns>.<path>`                                                            | `secret_realm.<ns>.<path>`                                                            |
| 界面标题键                    | `mxt.registry.realm_instance`                                                           | `mxt.registry.secret_realm`                                                           |
| 固有注册表（`generation.type`） | `mxt:realm_generation_type`                                                             | `mxt:secret_realm_generation_type`                                                    |
| 实体条件                     | `mxt:in_realm_instance`                                                                 | `mxt:in_secret_realm`                                                                 |
| 公式变量                     | `realm_instance_members` / `_limit` / `_elapsed` / `_duration` / `_index` / `_is_owner` | `secret_realm_members` / `_limit` / `_elapsed` / `_duration` / `_index` / `_is_owner` |
| 公式变量条目 id                | `mxt:formula_variable` 里的 `realm_instance`                                              | `secret_realm`                                                                        |
| 命令                       | `/mxt realm_instance list\|info\|enter\|exit\|destroy`                                  | `/mxt secret_realm …`                                                                 |
| KubeJS 事件组               | `realmInstance`                                                                         | `secretRealm`                                                                         |

### 2.2 物品、组件、附件（包与存档都看得见）

| 类别        | 旧                      | 新                             |
|-----------|------------------------|-------------------------------|
| 入口物品      | `mxt:realm_token`      | `mxt:secret_realm_token`      |
| 奖励箱物品     | `mxt:realm_reward_box` | `mxt:secret_realm_reward_box` |
| 数据组件      | `mxt:realm_token`      | `mxt:secret_realm_token`      |
| 实体附件      | `mxt:realm_travel`     | `mxt:secret_realm_travel`     |
| 关卡附件（实例表） | `mxt:realm_world`      | `mxt:secret_realm_world`      |

物品的语言键与资源一并跟着走：`item.mxt.realm_token*` / `tooltip.mxt.realm_token.realm` → `…secret_realm_token…`，
`assets/mxt/{items,models/item,textures/item}/` 下的四个 json 与两张 png 也改了文件名。

### 2.3 磁盘与维度键

| 类别                 | 旧                                   | 新                                          |
|--------------------|-------------------------------------|--------------------------------------------|
| 实例维度键              | `<定义命名空间>:realm/<定义路径>/<序号>`        | `<定义命名空间>:secret_realm/<定义路径>/<序号>`        |
| 实例地形目录             | `dimensions/<ns>/realm/<path>/<n>/` | `dimensions/<ns>/secret_realm/<path>/<n>/` |
| `mxt:template` 模板根 | `<服务器目录>/mxt_realm/<名称>/`           | `<服务器目录>/mxt_secret_realm/<名称>/`           |

### 2.4 Java

| 类别        | 旧                                                                                                                      | 新                                                                                                                                                                                                  |
|-----------|------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 包         | `com.iafenvoy.mxt.data.realm`                                                                                          | `com.iafenvoy.mxt.data.secretrealm`                                                                                                                                                                |
| 定义 record | `RealmInstance`                                                                                                        | `SecretRealm`                                                                                                                                                                                      |
| 维度生成      | `RealmGeneration`、`MxtRealmGenerations`、`RealmGenerationService`                                                       | `SecretRealmGeneration`、`MxtSecretRealmGenerations`、`SecretRealmGenerationService`                                                                                                                 |
| 实例表与记录    | `RealmRecord`、`RealmInstanceRegistry`、`RealmInstanceBridge`、`RealmWorldAttachment`                                     | `SecretRealmRecord`、`SecretRealmRegistry`、`SecretRealmBridge`、`SecretRealmWorldAttachment`                                                                                                         |
| 服务        | `RealmInstanceService`、`RealmBorderService`、`RealmEntryLocator`、`RealmStructurePlacer`、`RealmSeedBridge`               | `SecretRealmService`、`SecretRealmBorderService`、`SecretRealmEntryLocator`、`SecretRealmStructurePlacer`、`SecretRealmSeedBridge`                                                                     |
| 入口与旅程     | `RealmTokenItem`、`RealmTokenComponent`、`RealmTravelAttachment`、`RealmTravelEventBridge`                                | `SecretRealmTokenItem`、`SecretRealmTokenComponent`、`SecretRealmTravelAttachment`、`SecretRealmTravelEventBridge`                                                                                    |
| 条件与事件     | `InRealmInstanceEntityCondition`、`RealmInstanceEvent`、嵌套 `RealmInstanceVariable`                                       | `InSecretRealmEntityCondition`、`SecretRealmEvent`、`SecretRealmVariable`                                                                                                                            |
| 注册表常量     | `MxtResourceKeys.REALM_INSTANCE`、`REALM_WORLD`、`REALM_TRAVEL`、`REALM_TOKEN`、`REALM_GENERATION_TYPE`、`REALM_REWARD_BOX` | 依次改成 `SECRET_REALM*`（`MxtEntityConditions.IN_REALM_INSTANCE` → `IN_SECRET_REALM`；`MxtFormulaVariables.REALM_INSTANCE` → `SECRET_REALM`；`MxtKubeJsEventDispatcher.REALM_INSTANCE` → `SECRET_REALM`） |
| 探针        | `/mxt_test realm [keep\|reopen]`                                                                                       | `/mxt_test secret_realm [keep\|reopen]`（断言标签 `realm probe:` → `secret realm probe:`）                                                                                                               |

## 3. 有意保留的名字

- **境界那一系**：`realm_stage` / `RealmStage` / `REALM_STAGE`、`mxt:realm`、`mxt:has_realm`、公式变量 `realm` 与
  `realm_rank`、`/mxt realm`、`RealmEntityCondition`、`HasRealmEntityCondition`、`RealmLootCondition`、面板 `info.mxt.realm`、
  `first_realm` / `next_realm` 这些阶段名。
- **`realm` 作为一个字段名**：`SecretRealmTokenComponent.realm` 与 `SecretRealmTravelAttachment.realm`（它们的 JSON / NBT
  键都是 `"realm"`）、lang 键 `tooltip.mxt.secret_realm_token.realm`。它读作"这个令牌 / 这段旅程指向的秘境"，类型已经由
  `Holder<SecretRealm>` 说清。**要不要也改成 `secret_realm` 属于改字段名，见 §5。**
- **夹具的内容名**：`trial_realm`、`mirror_realm`、`existing_realm`、`template_realm`、`missing_structure_realm`
  是测试包自己的定义文件名，不是系统名，不改。
- **`research/` 里的历史稿正文**：不改，由本文提供对照。

## 4. 破坏性影响

1. **数据包**：注册表目录、条件 id、公式变量名、KubeJS 事件名、两个物品 id 全部换名。旧写法**不会报错**——注册表目录换了名字就没人读，
   `RecordCodecBuilder` 也会静默丢掉不再认识的键——所以旧包只会"少东西"。要按 §2.1 / §2.2 改名。
2. **存档**：附件 id 与实例维度键都变了。旧存档里的 `mxt:realm_travel` / `mxt:realm_world` 会被 NeoForge
   当未知附件跳过；旧的实例维度目录落在 `dimensions/<ns>/realm/`，而启动清扫只扫 `secret_realm/`，所以**旧目录要手工删**（
   `<世界存档>/dimensions/<ns>/realm/`），否则会一直占着磁盘。
3. **服务器目录**：手工搭的模板目录 `<服务器目录>/mxt_realm/` 要改名成 `mxt_secret_realm/`，否则 `mxt:template` 会报
   `Missing secret realm template` 并拒绝创建实例（这是设计上的失败路径，不是崩溃）。
4. **文案**：命令与物品的语言键全部换了前缀，资源包若覆盖过旧键要一并改名。
5. **文档**：`docs/数据包格式.md`、`docs/模块实现审计.md`、`docs/guide/**` 与文档站（中英各一份）已按同一张表同步。

## 5. 未做 / 待拍板

| # | 事项                                                | 现状                                    |
|---|---------------------------------------------------|---------------------------------------|
| 1 | 组件与附件里的 `"realm"` 字段是否也改成 `"secret_realm"`        | **未拍板**。属于改字段名，本轮按"只改注册表一系的名字"处理，没有动它 |
| 2 | 旧数据包 / 旧存档的兼容别名与迁移                                | 有意不做（模组未发布）                           |
| 3 | 旧实例维度目录（`dimensions/<ns>/realm/`）的自动清扫            | 不做，见 §4.2                             |
| 4 | 夹具定义名（`trial_realm` 等）是否也统一成 `secret_realm` 风格的名字 | 不做：它们是内容名，不是系统名                       |

## 6. 验证方式

- 编译：`./gradlew.bat compileJava compileTestModJava --console=plain` → `BUILD SUCCESSFUL`。
- 实机：**未跑**（本轮只编译）。跑起来该看这些：
    - `/mxt_test secret_realm` —— 断言标签全部是 `secret realm probe: …`，最后一行 `secret realm probe: OK`
      ；断言数字不受改名影响（夹具定义名没变）。
    - `/mxt_test secret_realm keep` 后重启，再 `/mxt_test secret_realm reopen` —— 应报
      `secret realm reopen: terrain kept=true prepared=true`，验证 `mxt:secret_realm_world` 附件与新维度键都落了盘。
    - `/mxt secret_realm list` —— 表里维度键应为 `<ns>:secret_realm/<path>/<n>`。
    - `/mxt registries list` —— 应列出 `mxt:secret_realm` 与 `mxt:secret_realm_generation_type`，不再有
      `mxt:realm_instance`。
