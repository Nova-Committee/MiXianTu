# 寿元与转世的可选联动

## 依赖边界

两模组的生产 Java 代码互不引用。MiXianTu 将 Reincarnation 声明为可选运行时依赖，删除了境界阶段的 `lifespan` 字段、专用增寿结算和 `ReincarnationIntegration`。突破增减寿元统一在境界阶段的 `success_action` 中配置 `reincarnation:add_lifespan` / `reincarnation:remove_lifespan`；与原有能力、效果行为组成数组，按顺序执行。两者的 `amount` 使用非负数量或公式，不需要 mode；增加同时提高剩余与总额，扣除只影响剩余。测试源集通过 `testModCompileOnly` 访问 API，仅用于断言，不进入生产编译类路径。

可选联动数据通过顶层 `neoforge:conditions` / `neoforge:mod_loaded` 控制加载；运行时条件不能屏蔽未知行为的解码。条件排除整个定义，相关引用也要相应处理。通用境界夹具不依赖寿元行为；专用寿元测试链、境界和资源均由加载条件控制。

寿元信息行直接可选引用 `reincarnation:lifespan`，伤害加成排除标签也用可选引用。命令 `/lifespan`、寿元与转世行为、公式变量、Java 事件、KubeJS 绑定均由 Reincarnation 注册；MiXianTu 不重复提供入口。灵草补算使用炼丹的独立上限，与是否安装寿元模组无关。

## 附件清理与继承

MiXianTu 不注册转世 handler，不监听原地重置事件，不逐项捕获和恢复附件。Reincarnation 的完整换世和原地重置都默认清空附件，保留名单由 `reincarnation:keep_on_rebirth` 与附件自身的死亡复制规则共同决定。

| 要保留的数据 | 附件 ID |
| --- | --- |
| 境界、修为和打坐 | `mxt:cultivation` |
| 子境界层数 | `mxt:minor_stages` |
| 进行中的天劫 | `mxt:tribulation` |
| 灵根与体质 | `mxt:spirit_identity` |
| 功法及进度 | `mxt:technique` |
| 魂四项 | `mxt:soul_stats` |
| Saturn 数值资源 | `saturn:resource_holder` |

完整换世固定归档中的附件名单，恢复不读取新的标签或配置；新玩家与登录恢复都由 Reincarnation 统一重算 Saturn 技能和被动。原地重置移除未保留附件，让后续访问创建默认状态，并重建技能、被动和寿元；不处理原版背包和进度。

MiXianTu 的八项转世配置全部移除。通用设置移入 Reincarnation：`lifespan.kill_on_reset=false`，`reset.clear_resources=true`。其余数据按附件标签选择继承。资源保留仍受未知模组数据重置的总策略约束。配置文件不自动跨模组迁移。含旧 `mxt:cultivation` handler 的未完成事务应在更新前恢复；已完成归档的历史读取不受影响。

## 验证

当前 MiXianTu 只保留8组联动探针：伤害管线、到期转世、突破增寿、显式转世、转世行为、完整玩家换世的标签保留与默认清空，以及可选依赖。移除8组 Reincarnation 基础测试及其专用实现：`switch_off`、`switch_on`、`ledger`、`seed`、`settle`、`pre_cancel`、`action`、`no_ledger`；基础行为由 Reincarnation 自身测试负责。主代码与测试代码编译通过，保留的测试 ID 和方法实现已核对未变。下文16项同装探针结果为删除前的历史验证，本次清理未重新运行 GameTest。

寿元行为拆分后，Reincarnation 的 `build` 与全部 43 项必需测试通过；MiXianTu 编译和资源处理通过，同装 16 项与未安装 1 项探针通过。新 JSON 使用 `add_lifespan` / `remove_lifespan` 与非负 `amount`，覆盖公式求值、负数拒绝、零值不建账与扣除下限。日志为 Reincarnation 的 `build/lifespan-actions-validation.log`，以及本项目的 `build/lifespan-actions-present.log`、`build/lifespan-actions-absent.log`。中英 220 页对齐，站内链接校验和构建通过，未运行客户端。

Reincarnation 构建和 43 项必需 GameTest 通过。MiXianTu 主代码、测试代码编译通过，同装 Reincarnation 的 16 项寿元探针通过，包含默认清空和标签保留两个完整玩家换世场景、技能重建、资源策略及突破增寿。纯数据驱动改造后重新通过 16 项同装寿元探针，覆盖成功行为组合、寿元正负增量、失败不执行、链结束不重复授予。未安装时重新通过 1 项突破探针，确认通用成功行为有效、条件化寿元定义缺席。日志为 `build/lifespan-datapack-present.log` 与 `build/lifespan-datapack-absent.log`。未安装 Reincarnation 的冷启动和突破探针 1 项、灵草探针 8 项通过。Reincarnation 与可选 KubeJS 同装的完整 45 项必需测试通过。中英页面 220 对齐，链接与配置名称校验零问题，文档站构建通过。

开发运行默认同装 Reincarnation，本地构建版本取自它的 Gradle 属性；`-PmxtWithoutReincarnation` 排除运行时 Jar，用于验证未安装场景。发布前应换为正式的可选依赖坐标。客户端尚未实跑。
