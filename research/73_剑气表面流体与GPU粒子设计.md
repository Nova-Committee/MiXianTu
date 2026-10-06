# 剑气表面流体与 GPU 粒子设计

基准：2026-10-06，`6b04afba` 的 `SwordAuraEntity`。本轮开始工作树干净，没有待还原文件。用户要求替换旧外层火焰，保留内部剑身、颜色、透明度、尺寸和运动朝向。

## 实现边界

Minecraft 26.1.2 的 GPU 抽象提供 RenderPipeline、实例化绘制和纹理渲染目标，但没有 Compute / SSBO 接口，纹理格式也没有浮点 RGBA。采用用户允许的 ping-pong FBO 路线：两张共享 RGBA8 图集，每对象独占 64×64 tile，最多 1024 个活动对象。GPU 更新温度、燃料、反应强度、氧气四个归一化通道；RGBA8 的量化通过随机舍入减轻。

`BurningItemManager` 跟踪可见剑气与图集槽位；`SurfaceFluidSimulation` 执行对流、扩散、反应、补充与冷却；`FlameProbabilityField` GLSL 定义概率密度；`ParticleSampler` 只创建一次粒子模板，顶点着色器用 Halton 候选、生命周期和软拒绝采样；`FlameRenderer` 在世界透明层合成后绘制贴表面火焰和粒子；`LODController` 控制距离、样本数与流体更新频率。

每批最多 64 条 std140 对象记录，低于 OpenGL 3.3 保证的 16 KiB UBO 上限；每对象只上传位置、朝向、形状、颜色、风与 tile 状态，不逐粒子更新 CPU 数据。复用三缓冲 UBO 和静态顶点/索引缓冲。不引入原生 OpenGL 调用与额外依赖；沿用 build.gradle 的 Minecraft / NeoForge / LWJGL。

## 坐标与合成约束

局部剑尖固定为 +Y，按运动方向旋转，再绕本地 +Y 滚转 90°。剑身与外层共用参数化表面。GPU 仅对局部粒子做运动，再通过独立的缩放基向量与相机相对实体中心变换，最后使用游戏当前 Projection UBO 和相机 viewRotation。不能把 VertexConsumer 已变换的位置重新当作模型局部坐标。

相对风为世界风减去实体速度 ×20（blocks/tick 转 blocks/second），再投影到局部切线。流体采用半拉格朗日对流及有界步长；火焰粒子沿相对风拖曳，并叠加世界向上浮力与扰动。热核趋白、外焰保持实体 tint、消亡时变暗。

外层加法混合只写 RGB，深度测试但不写深度，不改变目标 alpha。复制主深度纹理供软粒子采样，避免读写同一深度附件。玻璃/水的透明合成已先执行；不承诺与第三方 shader pack 的自定义合成兼容。未实现基于透明物体的完整深度排序或 OIT。

## LOD 与资源

默认 16 / 48 / 128 格边界，200 / 50 / 10 候选粒子。近处每帧更新流体，中距隔帧更新，远处不分配流体槽位并使用简化程序火焰，超过最远距离不渲染外层。远处仍使用小 billboard，避免依赖 point size。候选数不是同时可见粒子数，概率与年龄还会筛选。

跳过更新的 tile 必须复制旧值到另一图集，不能直接交换出两帧前的状态。离开视野后重新出现、LOD 返回近处或槽位复用时重新点火，避免采到其他实体的数据。退出世界、换世界、禁用效果时释放纹理与 GPU 缓冲。实体的可见距离不再依赖 0.2 格碰撞箱，视锥包围盒包含火焰拖尾。

客户端配置集中在现有 `MxtClientConfig.Flames`：强度、风系数、世界风、LOD 距离与样本数、表面层透明度、软粒子距离。实体 NBT 保留原有尺寸、RGB、blade_alpha、aura_alpha（默认 0.8），增加 lifetime（默认 80 tick，测试可设 12000）。没有旧 big 模式。

## 验证与延后

`compileJava compileTestModJava processResources processTestModResources --console=plain` 已通过。主资源和测试包的中英文键集合一致；检查了已有 WAMT 导出，补齐剑气实体名称，未重新运行游戏生成导出。三组 GLSL 在本机 Intel Arc 140T 的隐藏 OpenGL 3.3 上下文中编译、链接通过，实际 UBO 大小分别为 144 / 9216 bytes，与 Java 写入一致；这项检查没有启动 Minecraft。

测试包提供生成 64 / 1024 个静态剑气的探针和清理命令：`/mxt_test sword_aura` 在眼前生成一把，`/mxt_test sword_aura 64 10`、`/mxt_test sword_aura 1024 48` 生成相应阵列，`/mxt_test sword_aura clear` 清理本探针的实体。大阵列应在空中生成，避免下半部分进入地面。实际客户端集成、视觉还原和 FPS 由用户测试，不能由 Java / GLSL 编译推断已达到 60 FPS。

性能对比固定分辨率、相机、视距与图形设置，分别记录关闭效果、64 和 1024 对象在近/中/远距离的 FPS 与帧时间。视觉检查包括静止/飞行、转动相机、不同尺寸/颜色/alpha、玻璃/水、实体在墙后、F3+T、暂停、切换维度与重进世界。CPU 实体同步、内部剑身提交仍有开销，不声称零 CPU 或任意数量一条 draw call。
