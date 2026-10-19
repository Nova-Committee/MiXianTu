# 测试与探针

- 本仓库**没有 JUnit**。验证靠：编译 →（获准时）实机跑 `/mxt_test`。
- 探针在 `src/test-mod`，子命令：`kit` / `cultivate`（`cultivate probe` 是修炼方式的腿组：选择器、三个条件、双修、起停行为）/ `verify` / `damage` / `element` / `contract` / `progression`（灵宠成长：档案链、熟练度晋升、条件拦截、管理员写入、清扫、解约清零）/ `perch` / `identity` / `artifacts` / `secret_realm [keep|reopen]` / `rift` / `talisman` / `lifespan` / `alchemy` / `herb` / `pill` / `picker`（选择器目录的公开查询：分类、按定义筛、按模组 id 筛、堆视图去重与拷贝）/ `info` / `guide`。风格是**一次性探针实体 + 精确数字断言**（`close(actual, expected)`），一条腿一个 `OK / MISMATCH`，最后汇总。
- **哪条腿能在这台无客户端的专用服务端上跑，是各腿自己的事**：`progression` 腿带了**假人回退**（`source.getPlayer()` 为空时新建一个一次性 `FakePlayer`，照 `AlchemyProbes` 的写法），所以 `runTestServer` 的控制台可以直接 `mxt_test progression` 跑完整套断言；`contract` / `perch` / `verify` / `damage` / `lifespan` 等仍要客户端——它们要么读**付款者自己的修炼状态**（`qi` 上限是公式）、要么要真会话手里的物品或玩家实体表里的主人解析，假人身上会得到"看起来像功能坏了"的 `MISMATCH`。**新增腿组时先想清楚它属于哪一类**：能无头就把假人回退写进去（落点用 `BlockPos.containing(source.getPosition())`），不能就别硬塞。
- **夹具里那些数字是断言的一部分**：例如测试包的火/水克制与适应倍率决定了 `10 × 1.5 × 0.5 = 7.5`。给测试包加内容时，先确认不会改变既有腿的算式（新内容用新文件承载，或让默认倍率为 1）。
- 探针**只编译不等于跑过**：报告里必须写明"未实跑"，并给出跑一次该看什么输出。
- **不要求玩家的腿组可以在专用服务端上跑**（`runTestServer`，无客户端）：任务已配置 `standardInput = System.in`，交互控制台可直接下发命令，不必启用 RCON。落点用 `BlockPos.containing(source.getPosition())`（控制台的位置就是世界出生点）；探针区域需要强加载时先记录既有范围，结束只撤销本轮新增的区块。要求 `source.getPlayer()` 的腿组（`verify` / `damage` / `contract` / `lifespan` 等）仍要客户端；`picker` 属于这一类里最省的一种——它只读 `source.getLevel().registryAccess()`，控制台直接 `mxt_test picker` 即可。
