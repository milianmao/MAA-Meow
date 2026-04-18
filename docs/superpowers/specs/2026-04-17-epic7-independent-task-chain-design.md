# 设计文档：EPIC7 独立任务链（骨架阶段）

## 1. 目标与范围

本阶段目标：

- 以最小改动接入 `PanelTab.EPIC7`。
- 复用现有任务链交互能力（启用/禁用、拖拽、新增/删除/重命名、Profile 管理）。
- 可以从 EPIC7 页面启动并下发一条占位 `CUSTOM` 任务。
- 占位任务固定输出 `task_names = ["EPIC7@StartGame"]`。
- 停止逻辑可用。

明确不在本阶段实现：

- EPIC7 独立 DataStore 持久化。
- 多个 EPIC7 真实任务类型与业务参数。
- 独立 `Epic7TaskChainState` / `Epic7PanelViewModel` 大规模拆分。

验收标准（用户确认）：

1. `EPIC7` 页完整交互可用（拖拽、Profile、增删改重命名）。
2. 点击启动会下发 `MaaTaskType.CUSTOM`，且 `task_names=["EPIC7@StartGame"]`。
3. 停止功能可用。

## 2. 方案选择

采用方案 A：**EPIC7 轻分支接入现有任务链**。

原因：

- 与“先完成骨架并可启动占位 CUSTOM 任务”最匹配。
- 支持“允许先在现有通用类里加少量分支来快速接通”。
- 改动面最小，风险可控。

## 3. 架构设计

### 3.1 总体边界

- 保持 `ExpandedControlPanelViewModel` + `TaskChainState` 为主干，不新增独立 EPIC7 状态类。
- 在以下三个点补少量 EPIC7 分支：
  - 任务类型来源（新增 EPIC7 占位类型）
  - 配置面板分发（EPIC7 页展示 EPIC7 类型集合）
  - 启动参数组装（EPIC7 页走 CUSTOM 组装）
- 启动/停止入口继续复用底部按钮，不新增独立按钮。

### 3.2 数据模型

- 在 `TaskTypeInfo` 增加 EPIC7 占位任务类型（命名在实现阶段确定）。
- 新增最小配置类 `Epic7StartGameConfig`（实现 `TaskParamProvider`）。
- `toTaskParams()` 固定生成：
  - `MaaTaskParams(MaaTaskType.CUSTOM, paramsJson)`
  - `paramsJson` 至少包含：

```json
{
  "task_names": ["EPIC7@StartGame"]
}
```

- 继续复用 `TaskChainNode`，保证现有链式编辑能力直接可用。

### 3.3 UI 与路由

- 在 `ExpandedControlPanel` 补齐 `PanelTab.EPIC7` 对应 pager 页，修复页面映射错位。
- EPIC7 页复用 `TaskListPanel + TaskConfigPanel`，但任务类型列表按当前 Tab 控制。
- 底部按钮显示条件加入 `PanelTab.EPIC7`。
- 底部启动点击在 EPIC7 场景仍走 `viewModel.onStartTasks()`，由 ViewModel 内部识别当前 Tab 决定参数构建分支。

### 3.4 启动与错误处理

- 在 `ExpandedControlPanelViewModel.launchManualStart` 增加按 `currentTab` 分支：
  - `TASKS`：保持现有 `PrepareTaskStartUseCase`。
  - `EPIC7`：从链中过滤启用节点并收集 `config.toTaskParams()`，确保产出至少一条 `CUSTOM` 任务。
- 保持现有失败提示机制（dialog / runtime log）不变，避免引入额外复杂度。

## 4. 关键改动点（文件级）

预计主要涉及：

- `app/src/main/java/com/aliothmoon/maameow/data/model/TaskTypeInfo.kt`
- `app/src/main/java/com/aliothmoon/maameow/data/model/Epic7StartGameConfig.kt`（新增）
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/viewmodel/ExpandedControlPanelViewModel.kt`

必要时微调（以实际编译约束为准）：

- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/TaskConfigPanel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/TaskListPanel.kt`

## 5. 风险与控制

- 风险：通用链路中出现 EPIC7 条件分支，后续可能增加维护成本。
- 控制：本阶段只加最小分支，不做抽象扩张；后续若 EPIC7 任务增多，再统一抽象。

- 风险：EPIC7 与方舟任务类型混用可能带来 UI 误选。
- 控制：按 `currentTab` 控制“新增任务可选集合”。

## 6. 后续演进（非本阶段）

- 拆分 `Epic7TaskChainState` 与独立 DataStore。
- 新建 `Epic7PanelViewModel`，降低 `ExpandedControlPanelViewModel` 职责。
- 引入多 EPIC7 任务类型与参数面板。
