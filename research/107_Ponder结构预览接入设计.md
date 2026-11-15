# Ponder 结构预览接入

## 基准与结论

接入前基线提交为 `63632cf1`。原 `/formation show` 由本体维护 AUI 页面、交互状态、相机、画中画渲染器和专用轮廓管线。现在将播放、关键帧、旋转、方块识别与界面绘制交给 Ponder；本体仅负责把服务端的结构转换成临时场景。

选择 `net.createmod.ponder:ponder-neoforge:1.0.52+mc26.1.2`。Ponder 与 Catnip 按上游 Maven 发布物原样 JiJ，保留各自 MIT 许可证。两者要求 NeoForge 至少 `26.1.2.109`，因此同步提高平台最低版本。依赖仓库只匹配这两个 group，不干预其他依赖的解析。

上游 Maven 将 Flywheel 列为运行依赖，但 Ponder 没有引用它，Catnip 仅有未被这两份 Jar 调用的 `VirtualRenderHelper` 引用它；两者的模组元数据均不要求 Flywheel。本次预览不使用该辅助类，因此只显式引入 Ponder 与 Catnip，并禁用传递解析，避免将 Flywheel 带入开发运行和发行包。

## 数据与场景

- 命令与网络包保留原入口。服务端仍解析 inline 结构或模板的第一套 palette，客户端不依赖服务端数据包中的 NBT 资源。
- `FormationPreview.prepare` 是服务端与客户端共用的准备出口。最多接收 4096 条原始记录，去重时同坐标最后一条生效，再过滤空气；空结构和任意轴跨度超过 64 格的结构不打开预览。跨度使用 `long` 计算，防止整数溢出绕过限制。
- 仅平移坐标，保留方块状态及相对间距。最低层归一到 y=1，避免 Ponder 把 y=0 当作底板；长方形居中到方形视图。服务端在网络序列化前完成归一化，避免超大原始偏移超出压缩 BlockPos 的表示范围。准备过程幂等，不修改定义中的原坐标。
- 客户端使用当前世界作为隔离的 `PonderLevel` 的上下文，按服务端方块填充并创建备份，不写真实区块。断开世界后到达的消息直接丢弃。
- 每个实际有方块的高度是一段关键帧，从下往上显示。选择集只包含该层的实际方块，不填充空层或扫描整个稀疏包围盒。按整体尺寸缩放与居中，不添加阴影。y=0 单独铺满方形视图底板，雪块与白色混凝土逐格交替；底板在结构方形范围的四周各外扩一格，视图原点相应移到 (-1, 0, -1)，缩放按扩展后的底板大小计算。底板只在客户端展示，不进入阵法定义、服务端校验或网络结构数据。
- 客户端使用一个固定的 Ponder plugin，只有预览打开期间注册 `mxt:formation_plate` 对应的一条 storyboard。场景从本体的空 `assets/mxt/ponder/formation_preview.nbt` 模板编译，再填入本次服务端方块并建立重播备份；模板不包含预设结构、底板或实体。
- 原生界面标题使用中英翻译的「阵法结构预览」，本次服务端 Component 的解析文本作为常驻结构说明，并作为图标的自定义物品名。说明直接使用上游 `TextWindowElement`，不安排限时移除指令，也不靠超长时长延长播放进度；重播时由原生场景清理并重新加入，避免累积。查看方块模式仍按上游行为隐藏场景说明，退出该模式后恢复。只保留当前一份预览，不为每个阵法生成全局 id 或资源文件。

## 与上游的边界

通过 `PonderUI.of` 直接创建上游原生界面，不再派生自己的 Screen，也不覆盖重播。普通重播使用场景备份；Shift 重播通过上游 reload 和 plugin 重建同一份服务端预览，避免丢失动态结构。

打开时使用 Catnip 的标准 `ScreenOpener.transitionTo`，清空旧返回栈。关闭当前界面或退出服务器时清理当前快照和界面引用，并通过上游 reload 撤掉临时 storyboard；反复打开只替换这一条，不留下阵盘的常驻教程入口。其他 plugin 也按上游 reload 协议重新注册。渲染缓存与界面资源仍使用上游生命周期；本体不添加 Mixin、原生 GL 调用或新的世界渲染管线。

### 标准打开流程修正

首次接入曾用薄子类接受临时场景，并直接调用 `ScreenOpener.open`。用户截图中按钮被放大且部分内容超出画面。源码确认 Catnip 的 `transition` 初始为 0，此时缩放公式的结果为 1.5；直接 open 不会设置动画目标。标准 `transitionTo` 会初始化进场动画并让缩放收敛到 1。现在连同场景注册与编译改用原生入口；这修正了已确认的缩放初始化问题，图像是否完整仍需客户端复验，不能由无头探针代替。

### 场景渲染器注册过晚修正

用户复验原生界面后，控件尺寸恢复正常，但九方块结构仍全部空白。核对场景原点与尺寸：`configureBasePlate(0, 0, size)` 已设置视图基准，`SchematicLevel.setBlock` 会扩展世界边界；各层选择集直接使用实际方块坐标，不依赖空模板最初的一格范围。因此没有用扩大模板或再添加一块底板掩盖问题。

真正缺失的是启动时的画中画渲染器。Minecraft 构造 `GameRenderer` 时，NeoForge 已经发送 `RegisterPictureInPictureRenderersEvent` 并将收集结果存入 GUI 渲染器池；随后才进入 `ClientModLoader.finish`，触发 `FMLClientSetupEvent`。Ponder/Catnip 却在后一个阶段才将工厂加入 Catnip 的队列，早先的收集事件读到的是空队列。GUI 收到 `PonderSceneRenderState` 后找不到池，会直接返回，既不画方块，也不报渲染异常。界面文字、按钮不走这条路径，所以仍然显示。

`compat/ponder/PonderRenderCompatibility` 在客户端 `FMLConstructModEvent` 的串行工作中，通过 Catnip 的公开 `ModClientHooksHelper` 提前排入 Ponder 场景及 Catnip 的方块模型、方块实体、流体四个原生工厂。随后仍由 Catnip 自己的注册事件交给 NeoForge；这里只登记工厂，不构造渲染器或访问 GPU。后续上游 setup 写入同一类键，不重复添加注册项。不替换原生界面、渲染器或 MC 代码，不增加 Mixin、AT 或反射补丁。类前保留了适用上游版本与升级后删除条件。

### 查看方块的坐标尺度修正

Ponder 的 `updateIdentifiedItem` 将鼠标位置转换成 GUI 坐标，但 `SceneTransform.screenToScene` 按渲染时保存的窗口像素尺寸与 GUI 缩放反算射线。GUI 缩放大于 1 时，两者使用的尺度不同，导致鼠标明明位于方块上，识别射线却偏离结构。

`compat/ponder/PonderPickingCompatibility` 仅在本体当前预览的 `ScreenEvent.Render.Pre` 中补算识别。调用原生 `updateIdentifiedItem` 期间，将场景变换临时设置为 GUI 尺度，并在 `finally` 恢复绘制尺寸；射线求交、方块高亮、物品提示与开关均继续使用上游实现。该作用域只适用于本体的一条 storyboard，场景之间的滑动偏移固定为零；不作为其他多场景教程的通用补丁。上游统一识别与绘制尺度后应删除这层兼容。

### 方块提示固定在左上角的修正

用户确认方块已经能够选中，但物品 tooltip 固定在左上角。Ponder 在鼠标位置平移绘制矩阵后，以 (0, 0) 调用 `setTooltipForNextFrame`；Minecraft 延迟绘制 tooltip 时没有保留提交时的矩阵，Ponder 的平移也已经被撤销。因此识别出的物品正确，提示仍被放在屏幕原点。

`compat/ponder/PonderTooltipCompatibility` 通过 NeoForge 的 `RenderTooltipEvent.Pre` 将原点提示改用当前鼠标的绝对 GUI 坐标。事件处理只对本体当前预览生效，只修正使用原版默认定位器且坐标仍为 (0, 0) 的提示；其他位置和自定义定位器保持原样。物品内容、字体、样式与边缘避让仍由原版和其他 tooltip 扩展处理。本体不替换 Screen、不重画 tooltip，也不新增 Mixin 或反射。上游改为向延迟 API 传递绝对坐标后应删除这层兼容。

### 上游发布问题的兼容

首次 GameTest 在创建测试玩家时，三组都被 Catnip 的命令树序列化阻断，尚未进入测试正文。源码确认 Catnip 在这个版本注释掉了参数类型注册，却仍注册使用它们的配置命令。本体通过 `compat/ponder/PonderCompatibility` 在原版命令参数注册事件中补齐 `ModIdArgument`、`ConfigIdArgument` 和 `ConfigPathArgument` 的无上下文网络 serializer；仅在对应类尚未注册时补齐，使用本体自己的注册 ID。

移除 Flywheel 时同时移除之前为其错误资源路径添加的 AT 文件、开发期声明与发行元数据。本次接入不增加访问转换规则。

Catnip 的 `ConfigIdArgument.parse` 和 `ConfigPathArgument.parse` 本身还未实现完整解析。本轮只解决它们阻断所有玩家入服的问题，不接管 Catnip 的配置命令；Ponder 预览仍使用自身的客户端配置与界面。以后升级上游应重新核对并删除已不再需要的兼容补丁。

### 客户端配置首次加载修正

用户实际打开九方块结构时，客户端先在 `PonderUI.init`、随后在 `renderWidgets` 读取 `editingMode`，抛出 `Config value 'editingMode' accessed too early, not loaded yet`。无头的结构准备与网络探针没有访问客户端界面，因此之前三组通过不能证明这一条加载链正常。

源码确认有两处问题：Ponder 与 Catnip 在 `FMLClientSetupEvent` 才注册客户端配置，晚于 NeoForge 的客户端配置加载阶段；此外，Catnip 的 `ConfigValue` 初始值是 null，首次 Loading 回调却调用要求已经初始化的 `set()`。只重放加载事件，或只给默认值，都不能同时恢复文件取值、保存与重载。

`compat/ponder/PonderConfigCompatibility` 在客户端 `FMLLoadCompleteEvent` 的串行工作中，仅处理 `catnip` 与 `ponder` 尚未加载的 CLIENT 配置。先将其树中仍为 null 的值设置为声明的默认值，不触发回写；再调用 FML 单个配置的正常加载出口，使文件读取、默认文件生成、Loading 事件、保存与文件监视仍由 FML/Catnip 负责。已加载配置直接跳过，不重载其他模组，也不覆盖玩家设置。

上游没有公开首次赋值及单文件加载接口，因此这层兼容通过 `VarHandle` 访问 Catnip 的 `value`，通过 `MethodHandle` 调用 FML 的 `ConfigTracker.openConfig`。不修改依赖 Jar，不增加 Mixin 或 AT，不接触 Minecraft 渲染代码。这两个非公开成员是明确的升级检查点；它们的名称或签名发生变化会让兼容初始化失败，升级依赖时应重新核对，优先删除上游已经修好的补丁。

旧 `screen/multiblock` 的 7 个类、结构预览 AUI 资源、专用 RenderType / RenderPipeline、页面路径与 8 个旧翻译键一并清理。其他界面与世界特效管线不受这次替换影响。

## 验证与开放项

新增 `verify/formation_preview/shape`、`limits`、`payload` 三组 GameTest，覆盖负坐标、稀疏层、居中、状态属性、重复坐标与空气覆盖、输入不变、幂等、三个轴的上限、整数边界、原始数量限制及网络往返。无头探针不加载 Ponder 客户端类，不能替代实际画面验证。

移除 Flywheel 后的 `compileJava compileTestModJava build` 通过。已检查发行 Jar：Ponder 与 Catnip 各 JiJ 一次，均与下载的上游 Jar 逐字节一致，MIT 许可证保留；没有 Flywheel、原 AT 文件或 AT 元数据声明。`runtimeClasspath` 的依赖查询确认没有 Flywheel。接入阶段的中英文 1368 个 lang 键比对与文档站两种语言构建通过，移除依赖只需同步两份 README 与本稿，站点正文无需改动。

获授权后使用 `-PmxtGameTestSelection=mxt_test:*formation_preview*` 实跑三组 GameTest；移除 Flywheel 与 AT 后回归仍为退出码 0，日志为 `All 3 required tests passed`，19 条 `formation preview ...: OK`，没有 `MISMATCH`。测试玩家实际入服，因此同时覆盖了 Catnip 参数 serializer 缺失导致的命令同步失败；模组加载列表没有 Flywheel，也不再出现其 AT 路径错误。实际客户端按用户选择未启动。

配置修正新增 `verify/formation_preview/config` 一组探针，使用独立 `ConfigTracker`、真实 `ModConfigSpec`、Catnip 值与模组配置事件，检查缺失文件、已有文件的非默认值、嵌套值、首次加载、运行时保存、重复调用、Reloading 事件及卸载后重开。临时配置与文件监视在退出探针时释放，等待异步移除监视完成后再删临时文件，不重新加载其他配置。Reloading 使用 FML 的同步配置入口确定性触发；不等同于实际验证操作系统文件监视。修正后的 `compileJava compileTestModJava build` 通过，获授权后实跑四组 GameTest，退出码 0，日志为 `All 4 required tests passed`：原 19 项与新增 10 条 `ponder config ...: OK` 全部通过。实际客户端修正后尚未复验。

原生界面接入新增 `verify/formation_preview/template`，检查与上游相同路径下的压缩 NBT 能由 Minecraft 读取、尺寸有效且无方块或实体污染；后续补充实际 Catnip 世界的零原点、动态边界扩展与范围内方块状态读取。编译与 1370 个中英 lang 键比对通过。渲染器注册修正后回归获授权的五组测试，退出码 0，日志为 `All 5 required tests passed`，共 35 条 `OK`，没有 `MISMATCH`；无头探针仍不验证客户端模型绘制、窗口裁剪或进场动画。

注册时序另用真实 Catnip 注册队列、事件收集方法和 NeoForge 注册事件完成 6 项独立检查：复现 setup 前收集为空，确认事后入队无法更新已经收集的列表，提前入队能交给原生事件收集器，场景只有一个注册项，重复入队不重复，以及全过程无需 Minecraft 实例或 GPU。不是实际客户端启动或画面测试。

新增只在物理客户端登记的 `verify/formation_preview/client_registration` 探针，捕获 NeoForge 收集事件当时的工厂快照，检查事件早于 client setup、四种工厂已经就绪，共 6 项。探针已编译，未实跑；需要在测试客户端的单人世界使用 `/test run mxt_test:verify/formation_preview/client_registration`，通过时输出六条 `ponder render ...: OK`。专用服不登记这组，不加载客户端类。按用户之前选择，本轮未启动测试客户端，提前注册修正后的画面仍待复验；应完整重启客户端，资源重载不会重新创建启动时的渲染器池。

常驻说明、方块识别与棋盘底板调整后，编译通过，回归五组 GameTest 仍为退出码 0，日志为 `All 5 required tests passed`，共 38 条 `OK`。其中底板新增 3 项检查：铺满方形范围、两种方块交替、展示数据不进入原始结构与网络输入。另用真实上游 `SceneTransform` 完成 10 项独立检查，复现 GUI 缩放 2、4 的识别射线偏差，并确认缩放 1、2、4 修正后的射线通过投影目标、正常与异常退出均恢复绘制尺寸；该检查不启动 Minecraft，也不验证实际渲染与鼠标事件。

新增仅物理客户端登记的 `verify/formation_preview/client_scene` 与 `client_picking`，共 16 项，检查真实场景播放 2000 tick 后说明不消失、不延长时间线、两次重播不累积说明、底板备份，以及 GUI 缩放 1、2、4 的原生方块命中与异常恢复。这两组已编译，未实跑；可在测试客户端单人世界分别执行 `/test run mxt_test:verify/formation_preview/client_scene` 与 `/test run mxt_test:verify/formation_preview/client_picking`，通过时输出 `ponder scene ...: OK`。本轮未启动客户端，常驻说明、识别交互和棋盘底板的实际画面仍待复验。

底板向四周各外扩一格后，补充精确边界断言：3×3 结构对应 5×5 底板，x/z 均覆盖 -1 到 3，结构坐标与网络输入不变。编译通过，回归五组 GameTest 为退出码 0、共 39 条 `OK`，没有 `MISMATCH`；实际客户端未启动。

tooltip 定位修正后，编译通过，使用真实 NeoForge tooltip 事件与原版默认定位器的 7 项独立检查通过，覆盖跟随鼠标、下一次提示随鼠标移动、右下边缘避让、事件元数据与显式坐标/自定义定位器保留。新增仅物理客户端登记的 `verify/formation_preview/client_tooltip` 探针共 6 项，另检查真实物品、字体和 tooltip 内容不变。该组已编译、未实跑；在测试客户端执行 `/test run mxt_test:verify/formation_preview/client_tooltip`，通过时输出六条 `ponder scene ...: OK`。本轮没有启动客户端，也没有将独立事件检查当作画面验证。

日志见 `run-game-test-server/logs/latest.log`。测试启动另有原有无效链夹具校验和 Jade 的 loot_table 查询日志，没有导致接入阶段的测试失败。文档构建仅有已有的 vscode-languageserver-types 动态 require 警告。

仍需客户端验收：小窗口与 GUI 缩放、超高和横长结构、暂停与关键帧跳转、Shift 重播、重复打开/关闭、资源重载、多人服务器上的服务端专属模板，以及与第三方方块模型/渲染器的兼容。

本次不额外实现教程作者注册 API，不加入按键入口，不把阵法注册为物品的常驻 Ponder 教程。
