# EPIC7 Skeleton Custom Start Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `PanelTab.EPIC7` 中复用完整任务链交互，并能启动/停止一条占位 `CUSTOM` 任务（`task_names=["EPIC7@StartGame"]`）。

**Architecture:** 采用轻分支方案：继续复用现有 `TaskChainState`、`ExpandedControlPanelViewModel` 与 `TaskListPanel/TaskConfigPanel`，只在任务类型来源、配置面板分发、启动参数组装、Pager 映射四个点增加 EPIC7 分支。EPIC7 本轮不做独立 DataStore，仅保证交互闭环与 `CUSTOM` 启动闭环。

**Tech Stack:** Kotlin, Jetpack Compose, Koin, kotlinx.serialization, JUnit4, Gradle

---

### Task 1: Add EPIC7 placeholder config and task type

**Files:**
- Modify: `app/src/main/java/com/aliothmoon/maameow/data/model/StartGame.kt`
- Modify: `app/src/main/java/com/aliothmoon/maameow/data/model/TaskTypeInfo.kt`
- Test: `app/src/test/java/com/aliothmoon/maameow/data/model/StartGameConfigTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGameConfigTest {

    @Test
    fun toTaskParams_returnsCustomWithEpic7StartGameTaskName() {
        val config = StartGame()

        val result = config.toTaskParams()

        assertEquals(MaaTaskType.CUSTOM, result.type)
        val json = Json.parseToJsonElement(result.params).jsonObject
        val taskNames = json["task_names"]?.jsonArray
        assertTrue(taskNames != null)
        assertEquals("EPIC7@StartGame", taskNames!![0].jsonPrimitive.content)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.data.model.StartGameConfigTest"`
Expected: FAIL，提示 `StartGame` 未实现 `toTaskParams()` 或断言不成立。

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

@Serializable
data class StartGame(
    val enabled: Boolean = true,
) : TaskParamProvider {
    override fun toTaskParams(): MaaTaskParams {
        val paramsJson = buildJsonObject {
            putJsonArray("task_names") {
                add("EPIC7@StartGame")
            }
            put("enabled", enabled)
        }
        return MaaTaskParams(MaaTaskType.CUSTOM, paramsJson.toString())
    }
}
```

`TaskTypeInfo.kt` 增加 EPIC7 占位任务类型（放在枚举末尾，避免破坏现有默认链顺序语义）：

```kotlin
EPIC7_START_GAME("EPIC7 启动占位", { StartGame() }),
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.data.model.StartGameConfigTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aliothmoon/maameow/data/model/StartGame.kt \
  app/src/main/java/com/aliothmoon/maameow/data/model/TaskTypeInfo.kt \
  app/src/test/java/com/aliothmoon/maameow/data/model/StartGameConfigTest.kt
git commit -m "feat: add epic7 placeholder custom task config"
```

### Task 2: Route task gallery options by current tab

**Files:**
- Modify: `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ConfigurationPanel.kt`
- Modify: `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt`
- Test: `app/src/test/java/com/aliothmoon/maameow/presentation/view/panel/TaskGalleryFilterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.aliothmoon.maameow.presentation.view.panel

import com.aliothmoon.maameow.data.model.TaskTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskGalleryFilterTest {

    @Test
    fun epic7Tab_onlyShowsEpic7TaskTypes() {
        val result = availableTaskTypesForTab(PanelTab.EPIC7)

        assertEquals(listOf(TaskTypeInfo.EPIC7_START_GAME), result)
    }

    @Test
    fun tasksTab_excludesEpic7TaskTypes() {
        val result = availableTaskTypesForTab(PanelTab.TASKS)

        assertTrue(TaskTypeInfo.EPIC7_START_GAME !in result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.presentation.view.panel.TaskGalleryFilterTest"`
Expected: FAIL，提示 `availableTaskTypesForTab` 未定义。

- [ ] **Step 3: Write minimal implementation**

在 `ConfigurationPanel.kt` 增加可测试函数与参数化画廊：

```kotlin
internal fun availableTaskTypesForTab(tab: PanelTab): List<TaskTypeInfo> {
    return when (tab) {
        PanelTab.EPIC7 -> listOf(TaskTypeInfo.EPIC7_START_GAME)
        else -> TaskTypeInfo.entries.filter { it != TaskTypeInfo.EPIC7_START_GAME }
    }
}

@Composable
fun TaskConfigPanel(
    // ...existing params
    availableTaskTypes: List<TaskTypeInfo> = TaskTypeInfo.entries,
    modifier: Modifier = Modifier
) { /* ... */ }

@Composable
private fun TaskGalleryView(
    availableTaskTypes: List<TaskTypeInfo>,
    onAddNode: (TaskTypeInfo) -> Unit
) {
    // items(availableTaskTypes) { typeInfo -> ... }
}
```

在 `ExpandedControlPanel.kt` 调用时注入：

```kotlin
val availableTaskTypes = availableTaskTypesForTab(uiState.currentTab)

TaskConfigPanel(
    // ...existing args
    availableTaskTypes = availableTaskTypes,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.presentation.view.panel.TaskGalleryFilterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ConfigurationPanel.kt \
  app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt \
  app/src/test/java/com/aliothmoon/maameow/presentation/view/panel/TaskGalleryFilterTest.kt
git commit -m "feat: filter task gallery by selected panel tab"
```

### Task 3: Fix EPIC7 pager mapping and show bottom actions

**Files:**
- Modify: `app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt`
- Test: `app/src/test/java/com/aliothmoon/maameow/presentation/view/panel/PanelPageMappingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.aliothmoon.maameow.presentation.view.panel

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelPageMappingTest {

    @Test
    fun pageMapping_matchesPanelTabOrderWithEpic7Page() {
        assertEquals(PanelTab.TASKS, panelTabForPage(0))
        assertEquals(PanelTab.EPIC7, panelTabForPage(1))
        assertEquals(PanelTab.AUTO_BATTLE, panelTabForPage(2))
        assertEquals(PanelTab.TOOLS, panelTabForPage(3))
        assertEquals(PanelTab.LOG, panelTabForPage(4))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.presentation.view.panel.PanelPageMappingTest"`
Expected: FAIL，提示 `panelTabForPage` 未定义。

- [ ] **Step 3: Write minimal implementation**

在 `ExpandedControlPanel.kt` 提取页面映射函数并修正 `when (page)`：

```kotlin
internal fun panelTabForPage(page: Int): PanelTab = when (page) {
    0 -> PanelTab.TASKS
    1 -> PanelTab.EPIC7
    2 -> PanelTab.AUTO_BATTLE
    3 -> PanelTab.TOOLS
    else -> PanelTab.LOG
}
```

并将 pager 内容改成 5 页映射：

```kotlin
when (page) {
    0, 1 -> { /* TASKS 与 EPIC7 复用任务链 UI */ }
    2 -> AutoBattlePanel(...)
    3 -> ToolboxPanel(...)
    else -> LogPanel(...)
}
```

底部按钮显示条件统一为：

```kotlin
if (PanelTab.canShowTaskActions(uiState.currentTab)) {
    BottomButtons(...)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.presentation.view.panel.PanelPageMappingTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt \
  app/src/test/java/com/aliothmoon/maameow/presentation/view/panel/PanelPageMappingTest.kt
git commit -m "fix: align expanded panel pager mapping with epic7 tab"
```

### Task 4: Add EPIC7 start-path branching in ViewModel

**Files:**
- Modify: `app/src/main/java/com/aliothmoon/maameow/presentation/viewmodel/ExpandedControlPanelViewModel.kt`
- Test: `app/src/test/java/com/aliothmoon/maameow/presentation/viewmodel/Epic7StartPlanTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.StartGame
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.maa.task.MaaTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Epic7StartPlanTest {

    @Test
    fun buildEpic7Params_keepsEnabledCustomTasksOnly() {
        val chain = listOf(
            TaskChainNode(name = "EPIC7", enabled = true, order = 0, config = StartGame()),
            TaskChainNode(name = "Award", enabled = true, order = 1, config = AwardConfig()),
            TaskChainNode(name = "Disabled", enabled = false, order = 2, config = StartGame()),
        )

        val params = buildEpic7Params(chain)

        assertEquals(1, params.size)
        assertEquals(MaaTaskType.CUSTOM, params.first().type)
        assertTrue(params.first().params.contains("EPIC7@StartGame"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.presentation.viewmodel.Epic7StartPlanTest"`
Expected: FAIL，提示 `buildEpic7Params` 未定义。

- [ ] **Step 3: Write minimal implementation**

在 `ExpandedControlPanelViewModel.kt` 增加可测试函数：

```kotlin
internal fun buildEpic7Params(chain: List<TaskChainNode>) = chain
    .filter { it.enabled }
    .map { it.config.toTaskParams() }
    .filter { it.type == MaaTaskType.CUSTOM }
```

在 `launchManualStart` 内按 tab 分支：

```kotlin
val currentTab = state.value.currentTab
if (currentTab == PanelTab.EPIC7) {
    val params = buildEpic7Params(chainState.chain.value)
    if (params.isEmpty()) {
        showDialog(
            PanelDialogUiState(
                type = PanelDialogType.WARNING,
                title = "提示",
                message = "请先选择要执行的任务",
                confirmText = "知道了",
                confirmAction = PanelDialogConfirmAction.DISMISS_ONLY,
            )
        )
        return@launch
    }

    val result = compositionService.start(
        tasks = params,
        clientType = chainState.getClientTypeOrNull() ?: "epic7",
    )
    // 复用现有成功/失败反馈
    // ...沿用现有 message + dialog/Toast 逻辑
    return@launch
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.presentation.viewmodel.Epic7StartPlanTest"`
Expected: PASS

- [ ] **Step 5: Run focused regression tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.aliothmoon.maameow.domain.usecase.AnalyzeTaskChainUseCaseTest" --tests "com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCaseTest"`
Expected: PASS，确认原 TASKS 启动逻辑未回归。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/aliothmoon/maameow/presentation/viewmodel/ExpandedControlPanelViewModel.kt \
  app/src/test/java/com/aliothmoon/maameow/presentation/viewmodel/Epic7StartPlanTest.kt
git commit -m "feat: route epic7 tab start to placeholder custom tasks"
```

### Task 5: Final verification and manual acceptance checklist

**Files:**
- Modify: none
- Test: existing tests + manual flow

- [ ] **Step 1: Run combined targeted tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.aliothmoon.maameow.data.model.StartGameConfigTest" \
  --tests "com.aliothmoon.maameow.presentation.view.panel.TaskGalleryFilterTest" \
  --tests "com.aliothmoon.maameow.presentation.view.panel.PanelPageMappingTest" \
  --tests "com.aliothmoon.maameow.presentation.viewmodel.Epic7StartPlanTest"
```

Expected: PASS

- [ ] **Step 2: Manual EPIC7 acceptance checks**

在应用中验证：

1. 切换到 `PanelTab.EPIC7` 后可看到底部启动/停止按钮。
2. 进入编辑模式可新增 `EPIC7 启动占位` 任务。
3. 可对 EPIC7 任务执行启用/禁用、拖拽排序、重命名、删除。
4. Profile 管理（创建/切换/复制/删除）可用。
5. 点击启动后日志中出现 `MaaTaskType.CUSTOM` 且参数含 `"task_names":["EPIC7@StartGame"]`。
6. 点击停止后任务终止，UI 状态回收正常。

- [ ] **Step 3: Final commit (if Task 1-4 were squashed policy-free)**

```bash
# 如果前面已按任务提交，这一步跳过
# 如果尚未提交，按功能一次性提交
git add app/src/main/java/com/aliothmoon/maameow/data/model/StartGame.kt \
  app/src/main/java/com/aliothmoon/maameow/data/model/TaskTypeInfo.kt \
  app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ConfigurationPanel.kt \
  app/src/main/java/com/aliothmoon/maameow/presentation/view/panel/ExpandedControlPanel.kt \
  app/src/main/java/com/aliothmoon/maameow/presentation/viewmodel/ExpandedControlPanelViewModel.kt \
  app/src/test/java/com/aliothmoon/maameow/data/model/StartGameConfigTest.kt \
  app/src/test/java/com/aliothmoon/maameow/presentation/view/panel/TaskGalleryFilterTest.kt \
  app/src/test/java/com/aliothmoon/maameow/presentation/view/panel/PanelPageMappingTest.kt \
  app/src/test/java/com/aliothmoon/maameow/presentation/viewmodel/Epic7StartPlanTest.kt
git commit -m "feat: add epic7 skeleton task-chain custom start path"
```
