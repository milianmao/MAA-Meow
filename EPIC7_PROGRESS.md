# Epic7 接入任务进度表

| 编号 | 任务 | 状态 | 说明 |
|---|---|---|---|
| T1 | 定义 Epic7 任务模型与枚举 | 未开始 | `Epic7TaskTypeInfo` / `Epic7TaskChainNode` / `Epic7TaskProfile` |
| T2 | 定义 Epic7 配置类与 JSON 转换 | 未开始 | 输出 `MaaTaskParams(MaaTaskType.CUSTOM, params)` |
| T3 | 实现 `Epic7TaskChainState` 与独立持久化 | 未开始 | DataStore 名称建议 `epic7_task_chain` |
| T4 | 实现 `PrepareEpic7TaskStartUseCase` | 未开始 | 过滤 enabled 并按顺序组装任务 |
| T5 | 实现 `Epic7PanelViewModel` | 未开始 | 管理节点编辑/Profile/启动停止 |
| T6 | 实现 `Epic7Panel` 与子面板 | 未开始 | 对齐"一键长草"交互 |
| T7 | 接入 `ExpandedControlPanel` 的 `PanelTab.EPIC7` | 未开始 | 修正 pager ordinal 映射与底部按钮路由 |
| T8 | 注册 Koin 依赖并验证 | 未开始 | `assembleDebug` + 手工功能验证 |

## 更新规则
- 开始某任务时，将状态改为：进行中
- 完成某任务时，将状态改为：已完成
- 若阻塞，状态改为：阻塞，并在说明中写明阻塞原因
