# 验证细节：lang 与漏翻检查

改完 lang 文件后核对两份键集合一致（需要 Node，Windows / macOS / Linux 通用）：

```bash
node -e "const a=require('./src/main/resources/assets/mxt/lang/zh_cn.json'),b=require('./src/main/resources/assets/mxt/lang/en_us.json');const k=o=>Object.keys(o).sort();const m=k(a).filter(x=>!(x in b)),e=k(b).filter(x=>!(x in a));console.log(m.length||e.length?'DIFF zh-only='+m+' en-only='+e:'keys identical ('+k(a).length+')')"
```

**上面那条只保证两份键集合一致，不保证"该有翻译的文本都有翻译"。** 漏掉的键用 **Where Are My Translations**（`where-are-my-translations`）查：它是**开发环境专用**依赖（`build.gradle` 里那句 `runtimeOnly "curse.maven:where-are-my-translations-…"`，只进 `runTestClient`，不打进发行包），挂在客户端语言系统上，把查不到翻译的键导出成 JSON。跑一次 `runTestClient`（实机仍需先问，见 AGENTS.md 的「⚠️ Ask first」）后看 `run-test-client/config/wamt/exports/`，关注两个文件：

- `mxt/en_us.json` —— **游戏里显示过、但两份 lang 都没有**的键（界面上直接显示成 `item.mxt.xxx` 这种原文）。它**只在真正显示过一次时才记录**，没打开过的界面查不到，所以它只能当实机补漏，代替不了上面那条静态比对。
- `mxt/zh_cn.json` —— **`en_us` 有、`zh_cn` 没覆盖**的键（缺键，或两边写着一模一样的文案）。

`zh_cn.json` 那一份是"缺键"和"两边写得一样"混在一起的，**逐条看过再动手**：纯格式串（`%s %s`、`%ss`）与命令字面量（`/realm`、`/talisman`）在两份文件里本来就该一模一样，不是漏翻，别给它们编中文。命名规律上，`item.*` / `block.*` / `screen.*` / `command.*` 这些才是有实义的文案。

**每次加 / 改面向玩家的文案（界面、命令、提示、tooltip、配置项）之后都跑一次看一眼**，把里面出现的键补进两份 lang。两个注意点：导出目录按"键里最先出现的模组 id"分，测试包的键可能落在 `mxt/` 也可能落在 `mxt_test/`，两个都看；导出文件是**增量重写**的，上一次留下的旧文件不会被删掉，别把旧内容当成本次结论。
