# MiXianTu 文档格式规范

- 文档使用 Markdown 和 Docusaurus front matter。
- 一个页面描述一个模块或紧密相关主题；目录页命名为 `index.md`，分类使用 `_category_.json`。
- 页面顺序：定位、完成状态、数据/API、最小示例、联动、边界、测试排错。
- 使用简体中文；API、类名、注册表名、字段名和命令保留英文。
- “action”翻译为“行为”，“condition”翻译为“条件”，“resource”翻译为“资源”。
- JSON 示例必须是合法 JSON，代码块注明语言；不确定字段必须写“以当前 Codec 为准”。
- 明确标注“完成”“制作中”“预留”，不要把研究设计写成已实现功能。
- 字段、公开 API 或完成度变化时，同步更新对应模块页、README、测试示例和 research 审计。
- 链接使用相对路径；Mermaid 只表达关系和流程，不代替字段表。

完整规范见 [`docs/ai/FORMAT.md`](ai/FORMAT.md)。
