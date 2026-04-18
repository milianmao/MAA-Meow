# 计划：以独立任务链方案接入第七史诗 (EPIC7)

## Context
用户确认采用**方案 A**：Epic7 不复用明日方舟现有的 `TaskTypeInfo` / `TaskChainState` / `TaskConfigPanel` 的具体实现，而是新增一套 **Epic7 独立任务链**，功能体验对齐"一键长草"：
- 可勾选启用/禁用任务
- 可拖拽排序
- 可新增/删除/重命名任务
- 可编辑每个任务的配置
- 可管理多 Profile
- 最终转换为 `MaaTaskParams(MaaTaskType.CUSTOM, json)` 交给 MaaCore

这样可以避免改动现有明日方舟链路，降低风险；Epic7 后续只需要维护自己的任务类型、配置面板和持久化数据。

---

## 设计原则

1. **不动现有明日方舟任务链**
   - 不修改 `TaskTypeInfo`
   - 不修改现有 `TaskChainState` 的行为
   - 不修改现有 `TaskConfigPanel` 的任务类型分发逻辑

2. **Epic7 单独一套状态与 UI**
   - 单独的任务类型枚举
   - 单独的配置数据类
   - 单独的任务链状态类
   - 单独的配置面板与任务编辑面板

3. **UI 接入继续走 `PanelTab.EPIC7`**
   - 与现有 `TASKS`、`AUTO_BATTLE`、`TOOLS` 平级
   - 在 `ExpandedControlPanel` 中补齐 `EPIC7` 页面

---

## 需要新增/修改的文件

### 新增：Epic7 数据层
- `app/src/main/java/com/aliothmoon/maameow/data/model/epic7/Epic7TaskTypeInfo.kt`
- `app/src/main/java/com/aliothmoon/maameow/data/model/epic7/Epic7TaskChainNode.kt`
- `app/src/main/java/com/aliothmoon/maameow/data/model/epic7/Epic7TaskProfile.kt`
- `app/src/main/java/com/aliothmoon/maameow/data/model/epic7/Epic7TaskParamProvider.kt`
- `app/src/main/java/com/aliothmoon/maameow/data/model/epic7/...Config.kt`（每种 Epic7 任务一个配置类）

### 新增：Epic7 状态与启动逻辑
- `app/src/main/java/com/aliothmoon/maameow/data/preferences/Epic7TaskChainState.kt`
- `app/src/main/java/com/aliothmoon/maameow/domain/usecase/PrepareEpic7TaskStartUseCase.kt`
- `app/src/main/java/com/aliothmoon/maameow/domain/model/Epic7TaskChainPlan.kt`（如果需要独立 plan 结构）

### 新增：Epic7 UI
- `app/src/main/java/com/aliothmoon/maameow/presentation/viewmodel/Epic7PanelViewModel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/epic7/Epic7TaskListPanel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/epic7/Epic7TaskConfigPanel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/epic7/Epic7ProfileManagementPanel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/epic7/Epic7Panel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/epic7/...ConfigPanel.kt`（每种任务对应一个配置面板）

### 修改：现有接入层
- `app/src/main/java/com/aliothmoon/maameow/presentation/viewmodel/ExpandedControlPanelViewModel.kt`
- `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt`
- `app/src/main/java/com/aliothmoon/maameow/koin/AppModule.kt`
- `app/src/main/java/com/aliothmoon/maameow/koin/ViewModelModule.kt`
- `app/src/main/java/com/aliothmoon/maameow/koin/UseCaseModule.kt`

---

## 任务进度表

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

## 实现步骤

### Step 1 — 定义 Epic7 任务模型
目标：先把 Epic7 的任务链数据结构独立出来。

实现内容：
- 定义 `Epic7TaskParamProvider` 接口，职责等同现有 `TaskParamProvider`
- 定义 `Epic7TaskTypeInfo`，保存：显示名、默认配置构造器
- 定义 `Epic7TaskChainNode`，字段对齐现有 `TaskChainNode`：
  - `id`
  - `name`
  - `enabled`
  - `order`
  - `config: Epic7TaskParamProvider`
- 定义 `Epic7TaskProfile`，字段对齐现有 `TaskProfile`

说明：
- 这一步先用占位任务类型即可，例如：
  - `WAKE_UP`
  - `BATTLE`
  - `ARENA`
  - `CLAIM_REWARD`
- 真实任务节点名后续替换，不影响整体结构搭建

验证：
- 数据类可以正常序列化/反序列化
- 默认任务链能构造出来

### Step 2 — 定义 Epic7 配置类与 JSON 转换
目标：让每个 Epic7 任务都能转成 MaaCore 可消费的 `Custom` 任务 JSON。

实现内容：
- 为每种 Epic7 任务创建 `...Config.kt`
- 每个配置类实现 `Epic7TaskParamProvider`
- 在配置类里提供类似 `toTaskParams()` 的能力，输出：
  - `MaaTaskParams(MaaTaskType.CUSTOM, paramsJson)`
- `paramsJson` 的基础结构统一为：
```json
{
  "task_names": ["Epic7@SomeTask"],
  "...custom_params": "..."
}
```

说明：
- `task_names` 对应 pipeline 节点名
- 额外字段由各配置类自己决定
- 如果后续发现所有 Epic7 任务都只是 `task_names` 不同，也可以把 JSON 拼装收敛到 use case 层

验证：
- 每种配置都能稳定输出 JSON 字符串
- 生成结果满足 MaaCore `AppendTask(type, params)` 的调用格式

### Step 3 — 实现 Epic7TaskChainState
目标：复制现有 `TaskChainState` 的交互体验，但用独立存储。

实现内容：
- 新建 `Epic7TaskChainState`
- 结构和方法基本对齐现有 `TaskChainState`：
  - `chain: StateFlow<List<Epic7TaskChainNode>>`
  - `profiles: StateFlow<List<Epic7TaskProfile>>`
  - `activeProfileId`
  - `isLoaded`
  - `addNode()` / `removeNode()` / `renameNode()`
  - `setNodeEnabled()` / `updateNodeConfig()` / `reorderNodes()`
  - `switchProfile()` / `createProfile()` / `deleteProfile()` / `renameProfile()` / `duplicateProfile()`
- DataStore 使用独立名字，例如：`epic7_task_chain`
- key 也独立，例如：
  - `chain`
  - `profiles`
  - `active_profile_id`

说明：
- 独立存储的意义是避免和明日方舟链路串数据
- 由于 Epic7 的 `config` 类型不同，不能直接复用现有 `TaskChainNode`

验证：
- 任务链增删改查后能立即反映到 StateFlow
- 关闭应用后重启仍能恢复 Epic7 Profile 和任务链

### Step 4 — 实现 PrepareEpic7TaskStartUseCase
目标：把 Epic7 任务链转换成启动所需的 `List<MaaTaskParams>`。

实现内容：
- 新建 `PrepareEpic7TaskStartUseCase`
- 输入：`List<Epic7TaskChainNode>`
- 输出：
  - 已启用任务列表
  - `List<MaaTaskParams>`
  - Epic7 的 clientType / packageName（如有需要）
- 复用现有启动结果结构即可；如耦合过深，再新增 `Epic7TaskChainPlan`

说明：
- 这一层只负责把任务链整理成可执行参数
- 如果 Epic7 也有"必须先唤醒"之类规则，可以在这一层做校验
- 如果没有，就保持最小逻辑：过滤 enabled，逐个 `toTaskParams()`

验证：
- 给定启用的节点列表，能稳定产出正确顺序的 `MaaTaskParams`
- 空任务链时能返回可提示 UI 的错误结果

### Step 5 — 实现 Epic7PanelViewModel
目标：给 `PanelTab.EPIC7` 提供一套独立的页面状态与交互入口。

实现内容：
- 新建 `Epic7PanelViewModel`
- 职责对齐 `ExpandedControlPanelViewModel` 中 TASKS 那部分：
  - 当前选中的节点 id
  - 是否编辑模式
  - 是否新增任务模式
  - 是否 Profile 管理模式
  - 节点操作
  - Profile 操作
  - 启动 / 停止任务
- 内部依赖：
  - `Epic7TaskChainState`
  - `PrepareEpic7TaskStartUseCase`
  - `MaaCompositionService`
  - `MaaSessionLogger`（如需要）

说明：
- 不建议继续把 Epic7 状态塞进 `ExpandedControlPanelViewModel`
- 更合适的是给 EPIC7 一个独立 ViewModel，像一个独立页面那样管理

验证：
- ViewModel 能完整驱动节点编辑、Profile 切换和任务启动

### Step 6 — 实现 Epic7Panel UI
目标：把"一键长草"的体验平移到 Epic7。

实现内容：
- 新建 `Epic7Panel` 作为入口容器
- 布局仿照 TASKS：左侧列表、右侧配置
- 新建 `Epic7TaskListPanel`
  - 复刻 `TaskListPanel` 的交互：勾选、选中、拖拽、编辑模式、Profile 模式、新增任务
- 新建 `Epic7TaskConfigPanel`
  - 复刻 `TaskConfigPanel` 的分流逻辑：
    - Profile 管理模式
    - 编辑模式 / 新增任务
    - 编辑模式 / 任务管理
    - 普通模式 / 配置表单
- 新建 `Epic7ProfileManagementPanel`
  - 可以先复制现有 `ProfileManagementPanel`，后续再提炼共用组件
- 为每种 Epic7 配置新增对应 `...ConfigPanel.kt`

说明：
- 这一阶段优先保证功能跑通，不急着抽共用组件
- 等 Epic7 跑稳定后，再考虑是否把明日方舟和 Epic7 的重复 UI 合并

验证：
- `PanelTab.EPIC7` 页面能完整完成：增删改查、勾选、拖拽、配置编辑、Profile 管理

### Step 7 — 接入 ExpandedControlPanel
目标：把 EPIC7 页面真正接到浮动面板里。

实现内容：
- 在 `ExpandedControlPanel` 的 pager 中补齐 `PanelTab.EPIC7` 对应内容
- 修正 pager page 与 `PanelTab.ordinal` 的错位问题：
  - `0 -> TASKS`
  - `1 -> EPIC7`
  - `2 -> AUTO_BATTLE`
  - `3 -> TOOLS`
  - `4 -> LOG`
- 底部按钮显示条件补上 `PanelTab.EPIC7`
- 底部按钮的 `onStart()` / `onStop()` 路由到 `Epic7PanelViewModel`

验证：
- 点击顶部 Tab 中的"第七史诗"可以进入 Epic7 页面
- 启动/停止按钮能正确作用于 Epic7 任务链

### Step 8 — 注册依赖并完成验证
目标：让整条链路可运行。

实现内容：
- 在 Koin 中注册：
  - `Epic7TaskChainState`
  - `PrepareEpic7TaskStartUseCase`
  - `Epic7PanelViewModel`
- 执行最小范围验证

验证命令：
- `./gradlew assembleDebug`

功能验证：
- EPIC7 Tab 正常显示
- 可创建 / 删除 / 重命名 / 复制 Profile
- 可新增 / 删除 / 重命名 / 排序 Epic7 任务
- 可勾选启用任务
- 可编辑每个任务的参数
- 点击启动后日志中出现 `Custom` 任务参数 JSON
- 停止功能正常

---

## 风险与取舍

### 优点
- 不影响现有明日方舟链路
- Epic7 可快速独立演进
- 数据存储完全隔离，排查更简单

### 代价
- 会有一定代码重复（尤其是 TaskList / ProfileManagement / ConfigPanel 框架）
- 如果后面接入第三个游戏，可能还要再复制一套

### 当前建议
- 先接受重复，优先把 Epic7 功能闭环做出来
- 等确认第二个/第三个游戏也要接入时，再做真正的多游戏通用抽象
