# 复利（CIM）

把学习变成由 AI 驱动、可持续积累、即时获得反馈与回报的个人复利游戏。

复利（Compound Interest，CIM）是一款面向个人使用的游戏化 AI 学习日程 Android 应用。它把学习任务、自动排程、专注计时、CI 币经济系统和数据复盘放在同一个本地优先的工作流里，目标设备是横屏使用的三星 Galaxy Tab S11 Ultra 平板。

> 当前应用版本：`0.1.0`
> 当前状态：个人使用的持续开发版本，尚未发布到应用商店。

![复利应用图标](icon/play_store_icon.png)

## 功能概览

应用通过左侧导航栏提供六个主要页面：

| 页面 | 能力 |
| --- | --- |
| 今日 | 查看当天时间线、开始/结束专注、处理任务完成状态 |
| 日程 | 查看和调整日程，支持周视图与任务时间块编辑 |
| 任务 | 管理领域、主线、支线和任务；生成学习路线；导入任务数据 |
| 商城 | 管理奖励商品、每日精选、购买和赎回记录 |
| 复盘 | 查看专注时长、领域分布、完成率、计划与实际偏差、热力图、CI 收支和任务明细；可导出 CSV |
| 设置 | 切换主题，配置 LLM API Key、任务路由、本地模型和数据备份 |

### AI 排程

排程的核心由本地纯 Kotlin 确定性算法完成，不依赖网络或大模型：

- 锁定的时间块以及非 `PLANNED` 状态的任务固定不动；
- 临时占位事件、日窗口之外和已经过去的时间不可用；
- 可移动任务按主线截止日期、原起始时间和任务 id 排序；
- 优先保留原位置，只有发生冲突时才移动任务，减少日程扰动；
- 无法放置的任务会进入 `unplaced` 结果并交给界面提示，不会静默丢失。

LLM 用于学习路线生成、复盘分析、自然语言解析、商品估价、头衔生成和图片理解等辅助任务。未配置模型时，核心排程和本地统计仍可正常使用。

### 游戏化经济

完成专注后会同时结算 CI 币和领域经验：

- CI 币按加权分钟、难度、专注结果和支线连击计算；
- 单次专注前 30 分钟按 1 倍、30–60 分钟按 1.5 倍、超过 60 分钟的部分按 2 倍计算 CI 币；
- 经验按实际专注分钟和难度计算，不使用时长阶梯；
- 支线支持连续打卡、连击加成和每日打卡奖励；
- 领域经验用于头衔升级，商城消费记录进入 CI 币流水账本；
- 现实奖励使用约 `1 元 ≈ 20 CI` 的锚定汇率，可配合品质和折扣形成储蓄目标。

### 专注计时与桌面小组件

- 专注计时通过 Android 前台服务运行，降低后台被系统回收的风险；
- 提供“今日时间线”和“当前任务计时”两个 Glance 小组件；
- 小组件可以快捷开始和结束任务；
- 任务、session 或流水发生变化后会刷新小组件。

### 本地模型与云端模型

应用支持按任务选择模型来源：

- 云端：预置 DeepSeek Pro、DeepSeek Flash 和 MiMo 的 OpenAI 兼容端点；
- 本地：可下载并运行固定版本的 `Qwen3.5-2B · MNN`，包含文本生成和图片理解能力；
- 本地模型文件约 `1.39 GB`，按固定 revision、文件大小和 SHA-256 校验；
- API Key 使用 Android Keystore 加密存储，路由设置与 Key 分开保存；
- 选择本地路由时，本地推理失败不会静默回退到云端；
- 关闭模型或未配置 Key 时，调用方应使用离线兜底，而不是阻塞核心功能。

## 技术架构

```text
Kotlin + Jetpack Compose + Glance
                │
        单模块 Android 应用 :app
                │
        MVVM + Repository + 手工 DI
                │
 Room / SQLite ──┼── 前台计时服务
                │
  纯 Kotlin 排程与经济算法
                │
 LlmGateway ─── 云端 OpenAI 兼容 API / MNN 本地运行时
```

主要技术选型：

- Kotlin、Jetpack Compose、Material 3、Glance；
- Room + SQLite，数据库当前版本为 `5`，已提供版本迁移；
- `StateFlow` 驱动的 ViewModel 与 Compose UI；
- WorkManager 执行每日刷新任务；
- OkHttp 实现 OpenAI 兼容 API 请求；
- `kotlinx.serialization` 负责 JSON 数据处理；
- MNN JNI 运行时支持 `arm64-v8a` 本地模型推理；
- JUnit4 JVM 单元测试覆盖排程、经济、导入导出、商城、复盘聚合、LLM 路由和本地模型下载逻辑。

### 数据流

```text
Room DAO Flow
    → ViewModel StateFlow
    → Compose 收集并展示
    → Repository 统一写入数据库
```

核心数据关系如下：

```text
domains → quests → tasks → sessions → ledger
                         ├→ shop_items / daily_picks / purchases
                         └→ blockers
```

时间字段约定：`epochDay: Long` 表示日期，`startMinute/endMinute: Int` 表示当天分钟区间，真实发生时间使用毫秒时间戳。

## 环境要求

- macOS 或能够运行 Android Gradle 构建链的环境；
- JDK 17；
- Android SDK，`compileSdk = 35`；
- Android NDK 和 CMake 仅在重新构建 MNN JNI 时需要；
- 目标设备需要 `arm64-v8a`，最低支持 Android 12（API 31）。

本仓库约定使用以下本机工具路径：

- JDK：`/opt/homebrew/opt/openjdk@17`
- Android `adb`：`/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
- Python：`/opt/miniconda3/envs/Codex/bin/python`

如果本机路径不同，请替换命令中的路径。

Gradle 构建前还需要让 Android 插件找到 SDK：在仓库根目录创建 `local.properties` 写入 `sdk.dir=<你的SDK路径>`（Android Studio 打开项目时会自动生成），或设置 `ANDROID_HOME` 环境变量。`local.properties` 已加入 `.gitignore`，不会误提交。

## 构建与测试

首次 clone 后需先拉取语音识别用的 sherpa-onnx AAR（不在 Maven Central，走 GitHub Release 分发）。脚本优先用 curl 直连 GitHub CDN，失败时自动改用 `gh release download`（需要已安装 gh CLI 并登录）：

```bash
voice/fetch_sherpa.sh
```

然后在仓库根目录执行：

```bash
# 构建 Debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# 运行全部 JVM 单元测试
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest
```

生成的 Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

运行单个测试类：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest \
  --tests "com.wsy.ci.core.scheduler.SchedulerTest"
```

当前仓库只有 JVM 单元测试，没有 instrumented 测试。测试方法名按项目约定使用中文反引号命名。

## 安装到 Android 平板

先打开设备的开发者选项与 USB 调试，然后执行：

```bash
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

应用针对横屏平板设计，左侧为导航栏，主内容区用于展示时间线、卡片和统计信息。

## 首次使用

1. 构建并安装 Debug APK。
2. 在“任务”页面创建领域、主线或支线，再创建当天任务。
3. 在“今日”页面开始专注，结束后选择专注结果并结算 CI 币与经验。
4. 在“商城”页面维护想兑换的现实奖励或休息奖励。
5. 在“复盘”页面查看时间分配、计划与实际偏差和 CI 币流水。
6. 如需使用 AI，在“设置”中配置对应 API Key，并为任务选择模型路由。
7. 如需使用本地 AI，在“设置”中下载约 1.39 GB 的模型，完成校验后启动本地服务。

## 数据与隐私

- 学习任务、专注记录、经济流水和设置默认保存在本机；
- API Key 使用 `EncryptedSharedPreferences` 与 Android Keystore 加密保存；
- 支持创建和导入应用数据备份；
- 复盘明细支持导出 CSV；
- 云端 LLM 仅在用户配置并启用相应路由后调用；
- 本地模型路由不会把推理失败的请求自动发送到云端。

## 项目结构

```text
.
├── app/src/main/java/com/wsy/ci/
│   ├── core/                 # 数据库、Repository、排程、经济、导入导出、设计系统
│   ├── feature/              # 今日、日程、任务、商城、复盘、设置页面
│   ├── llm/                  # LLM 抽象、路由、云端 API、本地模型网关
│   ├── localmodel/            # 本地模型下载、校验和运行时控制
│   ├── widget/               # Glance 小组件与前台计时服务
│   └── work/                 # WorkManager 后台任务
├── app/src/test/              # JVM 单元测试
├── native/mnn/                # MNN JNI 构建脚本和 C++ 桥接代码
└── THIRD_PARTY_NOTICES.md     # 第三方组件与模型许可说明
```

## 当前限制与后续方向

当前实现仍处于持续开发阶段，以下事项需要在后续迭代中继续完善：

- 复盘页面的部分图表仍为自绘或占位，尚未接入规划中的 Vico 图表库；
- 本地模型已接入下载、校验、运行与路由，但仍需完成更多长时间运行、连续推理、取消和性能耐久验收；
- 当前仅提供 `arm64-v8a` 原生库，不支持其他 ABI；
- 目前没有 instrumented 测试，真机 UI 和系统行为需要手动验收；
- 应用尚未发布到 Google Play 或其他应用商店。

## 许可与第三方声明

项目自身的许可策略尚未单独声明。仓库中使用的 MNN、Qwen3.5-2B 及模型转换相关组件，其归属、许可证和注意事项见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。发布或分发 APK 前，请确认所有第三方组件与模型的使用条件，并将相关声明同步到应用内的开源许可页面。
