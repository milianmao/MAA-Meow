# MaaFramework Pipeline 协议参考

本文档基于官方文档 `https://maafw.com/docs/3.1-PipelineProtocol` 整理，目标是为 `MAA-Meow` 后续开发提供一份本地可查的协议速览。内容以官方文档为准；本文偏向开发者视角的归纳总结，不替代原始规范。

## 1. Pipeline 是什么

Pipeline 使用 JSON 描述一组可执行的任务节点（Node）。每个节点通常包含：

- `recognition`：如何识别当前界面或目标
- `action`：识别成功后执行什么动作
- `next`：动作成功后下一步尝试哪些节点
- `on_error`：识别超时或动作失败后转入哪些节点

最基础的结构如下：

```json
{
  "NodeA": {
    "recognition": "OCR",
    "action": "Click",
    "next": ["NodeB", "NodeC"]
  }
}
```

任务通常通过 `tasker.post_task` 指定入口节点启动。

## 2. 执行模型

### 2.1 基本流程

对于当前节点：

1. 顺序检测当前节点的 `next` 列表
2. 依次尝试识别每个候选子节点
3. 本轮只要有一个子节点识别命中，就立即停止继续检测后续节点
4. 执行该命中节点的 `action`
5. 若动作成功，则进入该命中节点的 `next`
6. 若动作失败，则进入该命中节点的 `on_error`
7. 若当前节点的 `next` 在超时前一直未命中，则进入当前节点的 `on_error`

### 2.2 终止条件

满足任一条件时流程结束：

- 当前节点的 `next` 为空
- 当前节点的 `next` 超时且没有可继续的错误路径
- 外部调用 `post_stop`
- 节点动作执行了 `StopTask`

官方特别说明：若存在 `[JumpBack]` 回跳点，`next` 为空时会先尝试执行回跳逻辑，再决定是否终止。

### 2.3 识别轮询

节点识别的核心节奏可近似理解为：

```text
while (!hit && !timeout) {
  foreach(next) {
    // 顺序检测候选节点
  }
  sleep_until(rate_limit)
}
```

这意味着：

- `next` 的顺序非常重要
- 命中第一个候选即短路
- `rate_limit` 会直接影响轮询速度和资源消耗

## 3. 顶层注意事项

官方文档给出的若干重要约束：

- 对于必选字段，JSON 中可以先留空，但执行前必须通过接口补齐
- 以 `.` 开头的文件夹和 JSON 文件不会被读取
- JSON 根对象里，以 `$` 开头的字段不会被解析

## 4. `roi` / `box` / `target` 的区别

这是实现中最容易混淆的部分：

- `roi`：感兴趣区域，决定“在哪里识别”
- `box`：识别命中的结果区域，表示“识别到了哪里”
- `target`：动作目标区域，决定“点哪里 / 滑哪里 / 滚哪里”

补充规则：

- 实际识别范围由 `roi + roi_offset` 决定
- 实际动作目标由 `target + target_offset` 决定
- `target` 默认是 `true`，表示直接使用当前识别得到的 `box`

典型流程是：

1. 在 `roi`（叠加 `roi_offset`）里做识别
2. 识别后得到 `box`
3. 计算 `target`（叠加 `target_offset`）
4. 执行动作

## 5. Pipeline v1 核心节点字段

以下字段为开发中最常见、最重要的部分。

### 5.1 基础流程字段

- `recognition: string`
  - 识别算法类型
  - 可选，默认 `DirectHit`
- `action: string`
  - 动作类型
  - 可选，默认 `DoNothing`
- `next: string | NodeAttr | list<string | NodeAttr>`
  - 成功后继节点列表
  - 按顺序识别，只执行第一个命中的候选
- `on_error: string | NodeAttr | list<string | NodeAttr>`
  - 错误路径节点列表
  - 当前节点 `next` 超时，或命中子节点后动作失败时使用

### 5.2 节奏与时序控制

- `rate_limit: uint`
  - 识别速率限制，默认 `1000ms`
  - 每轮识别最少消耗这么久，不足会 sleep
- `timeout: int`
  - 当前节点 `next` 的识别超时，默认 `20s`
  - `-1` 表示无限等待
- `pre_delay: uint`
  - 识别到后、动作前延迟，默认 `200ms`
- `post_delay: uint`
  - 动作后、开始识别 `next` 前延迟，默认 `200ms`

### 5.3 等待画面静止

- `pre_wait_freezes: uint | object`
  - 识别后动作前，等待画面静止
- `post_wait_freezes: uint | object`
  - 动作后进入 `next` 前，等待画面静止
- `repeat_wait_freezes: uint | object`
  - `repeat > 1` 时，后续重复动作前等待静止

官方给出的整体顺序是：

```text
pre_wait_freezes -> pre_delay -> action -> post_wait_freezes -> post_delay
```

### 5.4 节点可用性与计数

- `enabled: bool`
  - 默认 `true`
  - 为 `false` 时，该节点在其他节点的 `next` 中会被跳过
- `max_hit: uint`
  - 节点最多可成功命中的次数
  - 超过后会被跳过
- `inverse: bool`
  - 反转识别结果
  - 注意：若实际没有识别到目标，`Click` 等依赖“自身识别结果”的动作可能失效，因此通常需要显式设置 `target`

### 5.5 动作重复

- `repeat: uint`
  - 动作重复次数，默认 `1`
- `repeat_delay: uint`
  - 重复动作之间的延迟
- `repeat_wait_freezes: uint | object`
  - 每次重复动作前等待画面静止

官方执行语义近似：

```text
action - [repeat_wait_freezes - repeat_delay - action] × (repeat - 1)
```

### 5.6 附加数据与通知

- `focus: object`
  - 关注节点，可额外产生回调消息
  - 官方注明其依赖上层实现，并非 MaaFramework 原生统一行为
- `attach: object`
  - 附加配置，不影响节点执行逻辑
  - 可通过接口取回
  - 与 `default_pipeline.json` 中的 `attach` 合并时采用字典合并，而不是整体覆盖

### 5.7 锚点

- `anchor: string | list<string> | object`
  - 节点识别命中并执行动作后，会将锚点名映射到对应节点
  - 无论动作成功或失败，都会设置锚点

三种形式：

```json
"anchor": "MyAnchor"
```

表示把 `MyAnchor` 指向当前节点。

```json
"anchor": ["A", "B"]
```

表示把多个锚点都指向当前节点。

```json
"anchor": {"A": "TargetNode", "B": ""}
```

表示：

- 锚点 `A` 指向 `TargetNode`
- 锚点 `B` 被清除（空字符串表示清除）

后续可以在 `next` / `on_error` 中通过 `[Anchor]锚点名` 引用。若锚点不存在或已清除，则该候选节点会被跳过。

### 5.8 已废弃字段

以下字段已在 `v5.1` 废弃：

- `is_sub`
- `interrupt`

官方建议使用节点属性中的 `[JumpBack]` 替代。

## 6. Pipeline v2

MaaFW 自 `v4.4.0` 起支持 Pipeline v2，并兼容 v1。

v2 与 v1 的主要差异：

- `recognition` 改为对象
- `action` 改为对象
- 类型放到 `type`
- 其余参数放到 `param`

示意：

```json
{
  "NodeA": {
    "recognition": {
      "type": "TemplateMatch",
      "param": {
        "template": "A.png",
        "roi": [100, 100, 200, 200]
      }
    },
    "action": {
      "type": "Click",
      "param": {
        "target": true
      }
    },
    "next": ["NodeB"]
  }
}
```

除结构归类不同外，其余语义与 v1 基本一致。若项目后续需要更清晰地组织参数，建议优先考虑 v2 风格。

## 7. 默认值继承：`default_pipeline.json`

官方支持通过 `default_pipeline.json` 定义默认值。可配置三类默认参数：

- `Default`：所有节点通用默认值
- 算法名对象：对应识别算法的默认参数，例如 `TemplateMatch`、`OCR`
- 动作名对象：对应动作的默认参数，例如 `Click`、`Swipe`

优先级从高到低：

1. 节点内直接定义的参数
2. `default_pipeline.json` 中对应算法/动作类型的默认参数
3. `default_pipeline.json` 中 `Default` 的默认参数
4. 框架内置默认值

开发建议：

- 跨节点一致的轮询/延时参数放 `Default`
- 某识别算法的统一阈值、排序逻辑放算法名对象
- 某动作默认压感、接触点等放动作名对象
- 节点里只保留该节点真正特殊的配置

## 8. 识别算法类型

官方列出的 `recognition` 可选值：

- `DirectHit`
- `TemplateMatch`
- `FeatureMatch`
- `ColorMatch`
- `OCR`
- `NeuralNetworkClassify`
- `NeuralNetworkDetect`
- `And`
- `Or`
- `Custom`

下面只整理本地开发最需要记住的共性与关键差异。

### 8.1 `DirectHit`

不做识别，直接命中。

关键字段：

- `roi: array<int,4> | string`
- `roi_offset: array<int,4>`

说明：

- `roi` 为数组时是 `[x, y, w, h]`
- 自 `v5.6` 起支持负数坐标和尺寸
- `roi` 也可以写成节点名，表示在之前某节点识别出的目标区域内识别
- 自 `v5.9` 起也支持 `[Anchor]锚点名`

### 8.2 `TemplateMatch`

找图。

关键字段：

- `roi`
- `roi_offset`
- `template: string | list<string>`
- `threshold: double | list<double>`
- `order_by: string`
- `index: int`
- `method: int`

注意点：

- `template` 路径相对 `image` 文件夹
- 支持填文件夹路径，递归加载图片
- 图片应使用无损原图缩放到 720p 后的裁剪图
- `threshold` 为数组时需与 `template` 数组长度一致

### 8.3 `OCR`

文字识别。

关键字段：

- `roi`
- `roi_offset`
- `expected: string | list<string>`
- `threshold: double`
- `replace: array<string,2> | list<array<string,2>>`
- `order_by: string`
- `index: int`
- `only_rec: bool`
- `color_filter`（`v5.8` 新增）

注意点：

- `expected` 支持正则
- `replace` 可用于 OCR 误识别纠正
- `order_by` 默认 `Horizontal`
- `index` 支持负数，语义类似 Python 下标
- `only_rec=true` 时只做识别，不做检测，因此 `roi` 需要更精准

### 8.4 `And` / `Or`

逻辑组合识别。

文档说明自 `v5.3` 新增，自 `v5.7` 起支持节点名称引用。适合把多个已有识别条件组合成一个更稳定的候选条件。

### 8.5 `Custom`

自定义识别器。

关键字段：

- `custom_recognition: string`
- `custom_recognition_param: any`
- `roi`
- `roi_offset`

该类型依赖外部通过接口注册的识别器实现。

## 9. 动作类型

官方列出的 `action` 可选值：

- `DoNothing`
- `Click`
- `LongPress`
- `Swipe`
- `MultiSwipe`
- `Scroll`
- `ClickKey`
- `LongPressKey`
- `InputText`
- `StartApp`
- `StopApp`
- `StopTask`
- `Command`
- `Shell`
- `Screencap`
- `Custom`

### 9.1 通用目标语义

很多动作都共享：

- `target: true | string | array<int,2> | array<int,4>`
- `target_offset: array<int,4>`

含义：

- `true`：用当前节点刚识别到的区域作为目标
- `string`：引用某个已执行节点识别到的位置，也支持 `[Anchor]锚点名`
- `[x, y]`：固定坐标点
- `[x, y, w, h]`：固定区域

如果引用的前置节点或锚点没有有效识别结果，则动作失败。

### 9.2 `Click`

点击动作。最常用。

常见参数：

- `target`
- `target_offset`
- `contact`（`v5.0` 增强）
- `pressure`（`v5.0` 增强）

### 9.3 `LongPress` / `Swipe` / `MultiSwipe` / `Scroll`

这些是手势类动作，通常也依赖 `target` / `target_offset`。其中：

- `Scroll` 在 `v5.1` 新增
- `Scroll` 在 `v5.5` 新增 `target` / `target_offset`
- `Click` / `LongPress` / `Swipe` / `MultiSwipe` 在 `v5.0` 新增 `contact` / `pressure`

### 9.4 `Shell`

`v5.3` 新增。可执行 shell 相关动作。

官方在 `v5.8` 将其 `timeout` 字段重命名为 `shell_timeout`，开发时需要注意版本差异，避免继续使用旧字段名。

### 9.5 `Screencap`

`v5.8` 新增。用于截图动作。

### 9.6 `Custom`

自定义动作，依赖外部注册实现。

关键字段：

- `custom_action: string`
- `custom_action_param: any`

## 10. 节点属性（NodeAttr）

`next` / `on_error` 中的元素不仅能写节点名，还能写带属性的节点引用。

### 10.1 两种写法

对象形式：

```json
{
  "name": "C",
  "jump_back": true
}
```

前缀形式：

```json
"[JumpBack]C"
```

两者功能等价，且允许在数组里混用。

### 10.2 `jump_back` / `[JumpBack]`

语义：

- 当该候选节点命中后，它的后续节点链执行完毕，系统会回到父节点
- 回到父节点后，继续尝试父节点的 `next` 列表
- 若当前处于 `on_error` 错误路径，则不会执行回跳

适用场景：

- 临时弹窗
- 权限请求
- 网络异常提示
- 插队式处理的中断界面

与旧版 `is_sub` 的区别：

- `jump_back` 只作用于某个候选引用
- `is_sub` 是对整个节点定义生效

### 10.3 `anchor` / `[Anchor]`

语义：

- 在 `NodeAttr` 中，`anchor=true` 时，`name` 字段被解释为锚点名称，而不是节点名
- 运行时会解析为最后设置该锚点的节点
- 若锚点不存在或被清除，该候选会被跳过

适用场景：

- 前序识别路径不固定，但后续动作想引用“最近一次命中的某类节点”
- 多分支流程收敛
- 需要在动作/识别间共享最近命中的上下文节点

## 11. 等待画面静止（`*_wait_freezes`）

该类字段支持简单数值，也支持对象扩展配置。文档中给出的核心参数包括：

- `time`
- `target`
- `target_offset`
- `threshold`
- `method`
- `rate_limit`
- `timeout`

用途是：在某个目标区域持续检测“画面没有较大变化”，达到设定时长后再继续。

很适合：

- 页面转场后的稳定等待
- 滚动结束后的结果区稳定
- 避免在动画中点击

## 12. 版本演进重点

文档列出的重要变更如下：

- `v5.0`
  - 新增 `attach`
  - 新增 `TouchDown` / `TouchMove` / `TouchUp` / `KeyDown` / `KeyUp`
  - `Click` / `LongPress` / `Swipe` / `MultiSwipe` 新增 `contact` / `pressure`
  - `target` 新增 `[x, y]` 二元坐标支持
- `v5.1`
  - 新增 `anchor` / `max_hit`
  - 新增 `Scroll`
  - 新增节点属性 `jump_back` / `anchor`
  - `next` / `on_error` 新增 `NodeAttr` 支持
  - `order_by` 新增 `Expected`
  - `is_sub` / `interrupt` 废弃
- `v5.3`
  - 新增 `repeat` / `repeat_delay` / `repeat_wait_freezes`
  - 新增 `And` / `Or`
  - 新增 `Shell`
  - `TemplateMatch.method` 新增 `10001`
  - 新增默认属性 `default_pipeline.json`
- `v5.5`
  - `timeout` 支持 `-1`
  - `Scroll` 新增 `target` / `target_offset`
- `v5.6`
  - `roi` / `target` 支持负数坐标和尺寸
- `v5.7`
  - `anchor` 新增 object 形式
  - `And` / `Or` 支持节点名称引用
- `v5.8`
  - `OCR` 新增 `color_filter`
  - `Shell.timeout` 重命名为 `shell_timeout`
  - 新增 `Screencap`
- `v5.9`
  - `roi` / `target` 的 string 形式新增 `[Anchor]锚点名` 引用
  - 修复 action 失败时有无 `[JumpBack]` 的行为不一致问题

## 13. 开发时最值得遵守的经验

这些是从官方语义出发，对实际工程实现最有帮助的约束：

### 13.1 `next` 顺序就是优先级

因为候选是顺序检测、首个命中即短路：

- 最具体、最稳定的识别放前面
- 兜底节点放最后
- 临时弹窗处理通常配合 `[JumpBack]` 单独插入高优先级位置

### 13.2 少依赖大延迟，多依赖中间节点和静止检测

官方明确建议：

- 尽量增加中间过程节点
- 少用 `pre_delay` / `post_delay`

理由很直接：

- 纯延迟既慢又不稳定
- 页面转场更适合用识别链或 `*_wait_freezes` 做同步

### 13.3 `inverse=true` 时通常要显式给 `target`

因为反转识别后，“命中”并不意味着真实拿到了有效 `box`。如果动作仍依赖“自身识别结果”，点击位置可能失效。

### 13.4 善用 `anchor`

当流程存在多个入口、多个分支但后续操作需要回指“最近一次命中的上下文节点”时，锚点通常比复制节点或硬编码路径更稳。

### 13.5 把共性参数沉到 `default_pipeline.json`

若后续项目里会维护大量 Pipeline：

- 不要在每个节点重复写 `rate_limit` / `timeout` / `threshold`
- 把共性沉到默认配置里，能显著减少维护成本

## 14. 一个便于实现的心智模型

可以把 Pipeline 看成：

- 节点 = 状态机里的一个候选识别-动作单元
- `next` = 成功转移边
- `on_error` = 失败转移边
- `jump_back` = 子流程返回父流程
- `anchor` = 运行时可变引用
- `roi` = 在哪里找
- `box` = 找到了哪里
- `target` = 操作哪里

这个模型很适合指导 MAA-Meow 里的：

- Pipeline JSON 解析器 / 映射层
- 可视化编辑器或调试 UI
- 节点执行日志与回调展示
- 任务链编排

## 15. 后续如果要在 MAA-Meow 里落地，建议优先关注的实现点

- Pipeline v1/v2 的统一内部表示
- `next` / `on_error` 对 `NodeAttr` 的解析
- `anchor` 的运行时存储与解析
- `jump_back` 的调用栈或父节点返回机制
- `roi` / `target` 字符串引用与 `[Anchor]` 解析
- `default_pipeline.json` 的多层默认值合并
- `attach` 的保留与透传
- `focus` 回调与上层 UI 协议衔接
- `*_wait_freezes` 的统一等待逻辑

---

如果后续要继续做开发，建议下一步把这份文档再拆成两部分：

1. `Pipeline 数据结构映射表`
2. `Pipeline 执行器状态机设计草案`

这样会更方便直接指导 Kotlin/Native 侧实现。