# MAA-Meow Claude 项目指南

## 项目概览
MAA-Meow 是一款在 Android 设备上原生运行 **MAA (MaaAssistantArknights)** 核心的应用程序。它利用图像识别技术实现《明日方舟》任务自动化，无需 PC 或模拟器。

- **技术栈**: Kotlin, Jetpack Compose, Koin (依赖注入), C++ (JNI 用于 MAA 核心桥接), Gradle (KTS)。
- **核心组件**: 
  - `app`: 主 Android 应用程序。
  - `annotation-api` & `ksp-processor`: 用于代码生成的自定义注解和 KSP 处理器。
  - `hidden-api`: Android 内部 API 封装（用于 Shizuku/Root 功能）。
  - `native`: JNI 桥接代码 (`app/src/main/native`)。

## 关键技术与概念
- **MAA 核心集成**: 通过 JNI 将 Kotlin 与 MAA C++ 核心桥接。使用 `MaaCoreLibrary` 进行底层调用。
- **权限方案**:
  - **Shizuku/Root**: 用于获取高级权限，如无感输入注入、屏幕截图。
  - **无障碍服务 (Accessibility)**: 作为备用的点击/输入方案。
- **运行模式**: 
  - **悬浮窗模式**: 前台显示控制悬浮窗 (`overlay` 模块)。
  - **后台模式**: 基于虚拟显示器的无界面运行。
- **依赖注入**: 全面使用 Koin 进行组件解耦。

## 开发工作流
- **环境初始化**: 运行 `python scripts/setup_maa_core.py` 下载原生库 (`.so`) 和资源文件 (`MaaSync`)。
- **构建**: 使用 JDK 21 和 Android Studio。

## 固定环境路径
- **JDK 路径**: `D:\tool\JDK21`
- **Android SDK 路径**: `D:\tool\SDK`
- **执行规则**: 运行 Gradle、Android 构建、测试、Lint、SDK 相关命令时，优先使用上述固定路径，不要自行探测或猜测系统中的 JDK / SDK 路径。

- **代码规范**: 
  - **提交信息**: 遵循 [Conventional Commits](https://www.conventionalcommits.org/) (例如: `feat:`, `fix:`, `docs:`, `chore:`)。
  - **文档**: 使用 KDoc 对公共方法和复杂逻辑进行注释。
  - **UI**: 使用 Jetpack Compose，遵循 MVVM 架构。

## 目录结构
- `app/src/main/java/com/aliothmoon/maameow/`:
  - `bridge/`: JNI/原生桥接接口实现。
  - `maa/`: MAA 核心管理逻辑。
    - `callback/`: 处理来自 MAA 核心的回调信息。
    - `task/`: 具体自动化任务的逻辑封装。
  - `presentation/`: 界面层，包含 `viewmodel/`, `view/` (Compose UI), `navigation/`。
  - `service/`: 系统服务，如 `AccessibilityHelperService`。
  - `overlay/`: 悬浮窗逻辑控制。
  - `koin/`: DI 模块定义。
  - `root/`: Root/Shizuku 权限请求与处理。
- `app/src/main/native/`: C++ JNI 实现代码（截图、输入、帧缓冲处理）。

## 常用命令
- `./gradlew assembleDebug`: 构建调试版 APK。
- `./gradlew installDebug`: 安装调试版到连接的设备。
- `./gradlew test`: 运行单元测试。
- `python scripts/setup_maa_core.py`: 同步 MAA 原生资产和 so 库。
- `./gradlew :app:lintDebug`: 运行 Lint 检查。


# 行为准则：旨在减少大语言模型（LLM）常见的编码错误。可根据具体项目需求合并指令。
权衡： 这些准则倾向于“谨慎”而非“速度”。对于琐碎的任务，请自行判断。

1. 编码前先思考
   不要假设，不要隐藏困惑，要明确权衡。
   在实现之前：

明确陈述你的假设。 如果不确定，请询问。

如果存在多种解释，请全部列出 —— 不要默默地替用户做选择。

如果存在更简单的方法，请指出。 在必要时提出异议。

如果某些地方不清楚，请停止。 说明困惑点并提问。

2. 简约至上
   用最少的代码解决问题，杜绝投机性编码。

不要实现需求之外的功能。

不要为仅使用一次的代码编写抽象层。

不要提供未要求的“灵活性”或“可配置性”。

不要为不可能发生的场景编写错误处理。

如果你写了 200 行但 50 行就能解决，请重写。

问自己： “资深工程师会觉得这太复杂了吗？” 如果是，请简化。

3. 外科手术式修改
   只触碰必须修改的部分。只清理自己造成的混乱。
   编辑现有代码时：

不要“优化”相邻的代码、注释或格式。

不要重构没坏的代码。

匹配现有风格，即使你习惯的写法不同。

如果你发现了无关的死代码，请指出 —— 但不要擅自删除。
当你的修改产生了冗余时：

删除由于你的修改而导致不再使用的导入、变量或函数。

除非被要求，否则不要删除原有的死代码。

测试标准： 每一行改动都应能直接追溯到用户的需求。

4. 目标导向执行
   定义成功标准，循环尝试直到验证通过。
   将任务转化为可验证的目标：

“添加验证” → “为非法输入编写测试，然后让其通过。”

“修复 Bug” → “编写一个能复现该 Bug 的测试，然后让其通过。”

“重构 X” → “确保重构前后的测试都能通过。”
对于多步骤任务，请陈述简要计划：

[步骤] → 验证：[检查项]

[步骤] → 验证：[检查项]

[步骤] → 验证：[检查项]

明确的成功标准能让你独立完成循环。模糊的标准（如“让它跑通”）则需要不断地确认。



