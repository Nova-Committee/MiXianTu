# MiXianTu GUI 概念图

通过用户指定的图片网关，以 `gpt-image-2` 生成 6 张 1536×1024 PNG。生成使用经用户确认的临时 API 兼容流程，原有图片生成脚本未修改。请求显式指定 Base64 响应，最终 6 张均以 Base64 返回并保存。

总览： [overview.png](overview.png)。每组左侧为人物界面，右侧为灵气工作台。

| 方向 | 人物原图 | 工作台原图 | 左右对照 |
| --- | --- | --- | --- |
| 宣纸朱砂 | [人物](01-parchment-character.png) | [工作台](01-parchment-workbench.png) | [对照](01-parchment-pair.png) |
| 青玉竹简 | [人物](02-jade-character.png) | [工作台](02-jade-workbench.png) | [对照](02-jade-pair.png) |
| 玄墨鎏金 | [人物](03-ink-gold-character.png) | [工作台](03-ink-gold-workbench.png) | [对照](03-ink-gold-pair.png) |

## 可借鉴的设计

- 宣纸朱砂：细木框、双层纸边、朱砂标题牌、角落水墨与小印章。适合延续当前宣纸主题。
- 青玉竹简：浅青玉底、竹节连接件、几何玉扣与低对比装饰。工作台的灵气栏与输入区分隔较清晰。
- 玄墨鎏金：深色漆木、细金边、局部青色法阵与浅色正文，资源图标对比明显。实际使用时应控制边角纹样的数量。

## 检查与使用范围

六张原图均已打开检查，人物主要功能区和工作台九宫格可辨认。玄墨工作台将背包区画得更宽，部分图的面板外缘为画入图像的棋盘格；它们适合作为美术方向参考，不是可直接投入游戏的透明贴图。原图中的示例数值、物品与角色服装不属于玩法修改。

本轮没有修改游戏代码、CSS、界面布局或资源引用，没有启动客户端或 GameTest。

实际生成提示词见 [PROMPTS.md](PROMPTS.md)，机器可读请求为 [prompts-v2.jsonl](prompts-v2.jsonl)，尺寸与文件列表见 [manifest.json](manifest.json)。
