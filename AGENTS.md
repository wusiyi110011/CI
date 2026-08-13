# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目定位

「复利（Compound Interest）」——游戏化 AI 学习日程 Android 应用，单人自用，目标设备是三星 Galaxy Tab S11 Ultra 平板（横屏、左侧导航栏布局）。三件事合一：AI 自动排程 + 游戏化经济 + 数据复盘。完整产品方案见 [docs/产品与技术方案.md](docs/产品与技术方案.md)，含开发路线图 M1~M5。

产品 Slogan：把学习变成由 AI 驱动、可持续积累、即时获得反馈与回报的个人复利游戏，让每一次专注都转化为长期成长。

## 常用命令

本机 `./gradlew` 直接跑会报 "Unable to locate a Java Runtime"，必须带 `JAVA_HOME`；`adb` 也不在 PATH 上。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
```

跑单个测试类或单个用例（测试方法名是反引号包裹的中文）：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest --tests "com.wsy.ci.core.scheduler.SchedulerTest"
```

装到平板（Android SDK 装在 homebrew 的 `android-commandlinetools` 下，不是常见的 `~/Library/Android/sdk`）：

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

重新生成启动图标资源（源图 `icon/gemini.jpeg`）：

```bash
/opt/miniconda3/envs/Codex/bin/python icon/generate.py
```

## 架构

单模块 `:app`，Kotlin + Compose + Room，`core/`（可复用）与 `feature/`（按屏幕分包）两层。

### 依赖注入

没有 Hilt。`CiApp.container`（[CiApp.kt](app/src/main/java/com/wsy/ci/CiApp.kt)）是手工 DI 容器，持有 db 和各 Repository。ViewModel 一律继承 `AndroidViewModel`，用 `(app as CiApp).container` 取依赖。加新 Repository 就往 `AppContainer` 里加一行。

### 数据流

Room DAO 暴露 `Flow` → ViewModel 用 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 默认值)` 转 `StateFlow` → Compose 收集。UI 不直接碰 DAO 以外的持久化。

数据表关系：`domains`（领域，承载头衔经验）→ `quests`（主线 MAIN / 支线 SIDE）→ `tasks`（排程出的时间块实例）→ `sessions`（实际计时记录）→ `ledger`（CI 币流水）。旁支：`shop_items` / `daily_picks` / `purchases` / `blockers`（占位事件）。

时间的表达约定（贯穿全工程，改动前务必对齐）：
- 「哪一天」用 `epochDay: Long`
- 「当天的几点」用 `startMinute` / `endMinute: Int`（当日 0 点起的分钟数，区间左闭右开）
- 「真实时刻」用 `Long` 毫秒时间戳（`sessions.startAt`、`ledger.at`）

Room `version = 5` 且 `exportSchema = false`，已提供 `MIGRATION_1_2` 至 `MIGRATION_4_5`。以后修改 Entity 必须同步提升版本并补 migration，不能依赖卸载重装。

### 排程引擎

[Scheduler.kt](app/src/main/java/com/wsy/ci/core/scheduler/Scheduler.kt) 是**纯 Kotlin 确定性贪心算法，刻意不依赖 LLM**——断网也必须能排程，LLM 只负责自然语言解析和建议解释。它是无状态 `object`，不碰 db，因此可直接单测。

规则：锁定块（`locked`）和非 PLANNED 状态的任务固定不动；blockers 与日窗口外不可用；可移动任务按「主线截止近者优先 → 原起始时间」排序，优先保持原位（最小扰动），塞不下的进 `unplaced` 交 UI 提示，不静默丢弃。

### 经济系统

[Economy.kt](app/src/main/java/com/wsy/ci/core/economy/Economy.kt) 是纯函数集合，所有公式和阈值都在这里，**不要把奖励计算散到 Repository 或 UI 里**。

- CI 币 = 加权分钟 × 难度系数 × 专注系数 × (1 + 连击加成)，向下取整
- 加权分钟按单次专注时长分段累加：前 30 分钟 ×1、30–60 分钟 ×1.5、超过 60 分钟 ×2（`Economy.weightedMinutes`）
- 经验 = **实际**分钟 × 难度系数（不吃时长阶梯，否则等级门槛失真；与 CI 币双轨，只增不减）
- 锚定汇率 1 元 ≈ 20 CI

结算的落地在 [TimerRepository.stopSession](app/src/main/java/com/wsy/ci/core/data/TimerRepository.kt)：一次调用里同时写 session、更新任务状态、记流水、结算领域经验与升级奖励、维护支线连击。

### LLM 接入

抽象是 [LlmGateway](app/src/main/java/com/wsy/ci/llm/LlmGateway.kt) 接口。调用方不选模型，而是声明 `LlmTaskType`（学习路线生成 / 复盘分析 / 自然语言解析 / 商品估价 / 头衔生成 / 图片理解），由 `LlmSettings.resolveEndpoint` 按 tier 路由到具体端点。

关键约定：**`resolveEndpoint` 返回 null 是正常路径**，表示用户关闭了该任务或没配 Key，调用方必须有离线兜底，不能报错了事。

API Key 存 `EncryptedSharedPreferences`（Keystore 加密），路由表存普通 SharedPreferences，两者分开，见 [LlmSettings.kt](app/src/main/java/com/wsy/ci/llm/LlmSettings.kt)。

### 设计系统（约束较硬）

- **`Color.kt` 是全工程唯一允许写字面量颜色的地方。** 其余任何位置一律走 `MaterialTheme.colorScheme` 或 `CiTheme.colors`。
- 不占用 M3 角色的语义色（收入/支出/品质/难度/任务四态/热力色阶）定义在 [CiColors.kt](app/src/main/java/com/wsy/ci/core/designsystem/CiColors.kt)，全部由 `CiColors.from(scheme, isDark)` 从当前 ColorScheme 派生，亮/暗自动跟随。取用方式 `CiTheme.colors.income`。
- 间距、圆角、尺寸同理走 `CiSpacing` / `CiShapes` / `CiSizes`，不要写裸 `dp`。
- 小组件（Glance）另有一份 [WidgetColors.kt](app/src/main/java/com/wsy/ci/widget/WidgetColors.kt)，接的是同一份 ColorScheme，改配色时两边都要顾到。
- 导航栏图标是手绘 PNG（`drawable-xxxhdpi` + `drawable-night-xxxhdpi` 两套亮/暗），不是 vector，`MainActivity` 里用 `Image(painterResource(...))` 而非 `Icon`。

### 小组件与前台服务

计时走前台服务 [TimerService](app/src/main/java/com/wsy/ci/widget/TimerService.kt)（`foregroundServiceType="specialUse"`），保证不被系统杀。两个 Glance 小组件（今日时间线、当前任务计时）。

**任何改动 task / session 数据的地方，结束后要调 `CiWidgetUpdater.updateAll(context)`**，否则桌面小组件不刷新。

[DailyRefreshWorker](app/src/main/java/com/wsy/ci/work/DailyRefreshWorker.kt) 在每日 00:00 附近跑，目前只刷新商店精选。

## 语言约定

代码注释、KDoc、UI 文案、测试方法名一律中文。测试方法名用反引号包裹的中文短句描述行为，例如 ``fun `blocker占用后任务顺延到最近空档`()``。

## 测试范围

只有 `src/test`（JVM 单元测试，JUnit4），没有 instrumented 测试。已覆盖的是不依赖 Android 框架的纯逻辑：`Scheduler`、`Economy`、`DailyShop`、`CiImport`。新增纯算法逻辑应放进 `core/` 并配单测；UI 和 Repository 目前无测试覆盖。
