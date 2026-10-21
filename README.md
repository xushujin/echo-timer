# EchoTimer

语音播报秒表 · 原生 Android（Kotlin + Jetpack Compose）

一个不依赖任何第三方 UI 库 / 依赖注入框架的秒表应用：计时过程中按你设定的时间点用语音报时，
并且可以用「开始」「结束」这类口令直接控制计时。计时结果以多组记录的形式保存在本机。

当前版本：1.0.0

---

## 目录

- [功能](#功能)
- [技术栈](#技术栈)
- [自研边界：哪些是自己写的，哪些是平台能力](#自研边界哪些是自己写的哪些是平台能力)
- [目录结构](#目录结构)
- [构建](#构建)
- [签名（两条通道）](#签名两条通道)
- [发版流程（打 tag 自动发布）](#发版流程打-tag-自动发布)
- [数据与迁移](#数据与迁移)
- [播报规则](#播报规则)
- [语音指令规则](#语音指令规则)
- [权限](#权限)
- [质量与验证](#质量与验证)
- [已知限制与未验证项](#已知限制与未验证项)
- [变更记录](#变更记录)

---

## 功能

| 能力 | 说明 |
| --- | --- |
| 秒表计时 | 开始 / 暂停 / 继续 / 计次 / 结束 / 重置。基于单调时钟差分，长时间运行不累积漂移 |
| 定时语音播报 | 单选播报间隔（1s / 2s / 5s / 10s / 15s / 30s / 60s / 2m / 5m / 10m）：选 5 秒就每 5 秒报一次，只读数字（如 `5`、`90`） |
| 语音指令 | 说「开始」开始计时，说「结束」结束并保存；另支持暂停 / 继续 / 计次，中英文口令均可 |
| 多组历史记录 | 每次结束自动入库（可关闭），带计次明细（最后一次在最前），支持重命名、删除 |
| 天梯榜 | 自定义多个榜单（名称唯一），输入或选择姓名后开始不计次的挑战（**同样按设定间隔报时**）；支持**手工录入**成绩；只有超过原记录才更新，榜单按秒数降序 |
| 拟态界面 | 3 套配色（石墨 / 暖沙 / 雾青）× 明暗两态，全局切换立即生效 |
| 关于页 | 应用名、版本号、技术栈、支持范围、权限说明 |

---

## 技术栈

| 项目 | 版本 / 取值 |
| --- | --- |
| 语言 | Kotlin 2.1.0（`allWarningsAsErrors = true`） |
| 界面 | Jetpack Compose，Compose BOM 2024.12.01 + Material3（仅用主题、对话框、输入框等基础组件） |
| 构建 | Gradle 8.11.1 + AGP 8.7.3，JVM 目标 17，构建使用 JDK 21 |
| SDK | minSdk 26（Android 8.0），targetSdk / compileSdk 36（Android 16，API 36） |
| 数据 | 手写 `SQLiteOpenHelper` + 显式版本迁移（未使用 Room） |
| 语音合成 | 系统 `TextToSpeech` |
| 语音识别 | 系统 `SpeechRecognizer`（`RecognitionService`） |
| 测试 | JUnit4（64 个 JVM 单测）+ 真实 `sqlite3` 迁移验证脚本 |

版本号在构建脚本里只有一处定义（`app/build.gradle.kts` 的 `appVersionName` / `appVersionCode`），
关于页通过 `BuildConfig` 读取；`tools/check_consistency.py` 会核对构建脚本、README 与关于页三者是否一致。

---

## 自研边界：哪些是自己写的，哪些是平台能力

"核心能力自己实现、不用现成库"这条约束，本项目的落地口径是：
**业务逻辑、调度、解析、存储、视觉全部自研；声学模型这类需要训练数据的系统能力调用平台 API。**

| 能力 | 实现 | 说明 |
| --- | --- | --- |
| 计时内核 | 自研 `StopwatchEngine` | 记录单调时钟读数，查询时差分；不靠定时器累加 |
| 播报调度 | 自研 `AnnouncementScheduler` | 单一间隔的绝对时间点、同一时间点只播一次、跳跃不补播 |
| 进度环数学 | 自研 `IntervalMath` | 与调度器共享同一套"绝对时间点"语义 |
| 语音指令解析 | 自研 `VoiceCommandParser` | 全角转半角、去语气词、否定词拦截、优先级匹配 |
| 麦克风会话管理 | 自研 `SpeechCommandController` | 连续监听重启、错误退避、播报期间让麦、结果去抖 |
| 拟态视觉 | 自研 `Modifier.neumorphicFace` | 多层低 alpha 圆角矩形叠加模拟软阴影，API 26 起表现一致 |
| 主题系统 | 自研 `Palettes` + `CompositionLocal` | 3 套配色 × 明暗，单一入口全局生效 |
| 图标 | 自研 `GlyphIcon` | Canvas 手绘，不引图标库 |
| 数据层 | 自研 `EchoDb` + `Migrations` | 显式迁移，缺失迁移直接抛异常 |
| 语音合成 | 系统 `TextToSpeech` | 平台能力；调度与文本生成是自研的 |
| 语音识别 | 系统 `SpeechRecognizer` | 平台能力，仅作"声音 → 文本"输入；文本如何变成动作是自研的 |
| 界面基础 | Jetpack Compose + Material3 | 官方库，非第三方 UI 库 |

---

## 目录结构

```
app/src/main/java/com/echotimer/
├── AppContainer.kt              # 手写依赖容器（无 DI 框架）
├── EchoTimerApplication.kt
├── MainActivity.kt
├── core/
│   ├── leaderboard/             # 天梯榜模型与规则（纯函数）
│   ├── settings/                # 设置模型、序列化、唯一读写入口
│   ├── theme/                   # 配色规格（纯数据，可单测对比度）
│   ├── time/                    # 秒表内核、播报调度、时间格式化
│   └── voice/                   # 指令解析、TTS、语音识别控制器、设备能力诊断
├── data/
│   ├── db/                      # SQLiteOpenHelper + 迁移定义
│   ├── HistoryRepository.kt
│   ├── HistoryModels.kt
│   └── LeaderboardRepository.kt # 榜单/成绩读写（唯一约束、只增不减）
└── ui/
    ├── AppRoot.kt               # 主题入口 + 底部导航
    ├── TimerViewModel.kt        # 计时编排
    ├── HistoryViewModel.kt
    ├── LeaderboardViewModel.kt  # 榜单/挑战编排
    ├── DateFormats.kt           # 历史记录日期格式化
    ├── components/              # 拟态控件、手绘图标、麦克风权限、语音能力状态与提示弹窗
    ├── neumorph/                # 拟态绘制原语
    ├── screens/                 # 计时 / 记录 / 天梯 / 挑战 / 设置 / 关于
    └── theme/                   # Compose 主题适配
app/src/test/java/com/echotimer/ # 64 个 JVM 单测
tools/
├── verify_migrations.py         # 用真实 sqlite3 验证迁移
└── check_consistency.py         # 文档 / 构建脚本 / 代码一致性检查
.github/workflows/release.yml    # 打 tag 触发构建并发布 Release
```

---

## 构建

### 环境要求

- JDK 17–21（本次验证使用 JDK 21；Gradle 8.11 尚不支持 JDK 25，系统自带的 25 需显式切换）
- Android SDK：`platforms;android-36`、`build-tools;36.0.0`、`platform-tools`
- 无需 Android Studio，命令行即可完成全部构建

### 配置 SDK 路径

```bash
cp local.properties.example local.properties
# 编辑 local.properties：sdk.dir=/你的/Android/Sdk
```

`local.properties` 已被 `.gitignore` 忽略。

### 常用命令

```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:lintDebug              # 静态检查（warningsAsErrors）
./gradlew :app:distReleaseApk         # 签名 release 包 → build/dist/EchoTimer-v1.0.0.apk
./gradlew :app:distDebugApk           # 调试包 → build/dist/debug/EchoTimer-v1.0.0-debug.apk
python3 tools/verify_migrations.py    # 用真实 sqlite3 跑迁移
python3 tools/check_consistency.py    # 文档与代码一致性核对
```

产物文件名**带版本号**：`distReleaseApk` 输出 `build/dist/EchoTimer-v1.0.0.apk`（CI 直接上传），
`distDebugApk` 输出 `build/dist/debug/EchoTimer-v1.0.0-debug.apk`。两个任务都用 Sync，
且 release 任务带一个 `prepareDistDir` 前置任务（清旧包 + 校验签名配置），
所以切换版本或漏配签名时，dist 里不会留下可被误发的旧包。

---

## 签名（两条通道）

签名材料**不入库**：`keystore.properties`、`*.jks`、`release-keystore/` 都在 `.gitignore` 中。
两条通道共用 `app/build.gradle.kts` 里同一段逻辑，只是取值来源不同；**本地文件优先于环境变量**。

### 通道一：本地 `keystore.properties`

```bash
mkdir -p release-keystore
keytool -genkeypair -v \
  -keystore release-keystore/echotimer-release.jks \
  -alias echotimer -keyalg RSA -keysize 2048 -validity 10000
# 按提示输入口令

cp keystore.properties.example keystore.properties
# 填写 storeFile / storePassword / keyAlias / keyPassword
```

然后 `./gradlew :app:distReleaseApk` 即可产出已签名 APK。

### 通道二：CI 环境变量

| 变量 | 含义 |
| --- | --- |
| `ECHO_STORE_FILE` | keystore 文件路径（CI 上由 secret 解码得到） |
| `ECHO_STORE_PASSWORD` | keystore 口令 |
| `ECHO_KEY_ALIAS` | key 别名 |
| `ECHO_KEY_PASSWORD` | key 口令 |

GitHub 上需要配置 4 个 secret：`ECHO_KEYSTORE_BASE64`、`ECHO_STORE_PASSWORD`、`ECHO_KEY_ALIAS`、`ECHO_KEY_PASSWORD`。
其中 `ECHO_KEYSTORE_BASE64` 由 keystore 文件转 base64 得到：

```bash
base64 -i release-keystore/echotimer-release.jks | tr -d '\n' > keystore.b64
```

工作流会把 base64 解码到 `release-keystore/echotimer-release.jks`，再以 `ECHO_STORE_FILE` 传入。

### 无签名时的行为

- 本地：`assembleRelease` 仍会产出 `app-release-unsigned.apk` 供检查；但 `distReleaseApk` 会**直接失败**，
  并且先清掉 `build/dist` 里的旧 APK —— 避免把未签名包、或上一次构建的旧包当成新产物发出去。
- CI（检测到 `CI` 环境变量且构建 release）：在配置阶段就**直接失败**并给出缺失项，避免产出不可安装的包。

### 校验签名

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs build/dist/EchoTimer-v1.0.0.apk
```

### GitHub 仓库需要配置什么

| 位置 | 配置项 | 值 |
| --- | --- | --- |
| Settings → Secrets and variables → Actions | `ECHO_KEYSTORE_BASE64` | release keystore 的 base64（单行） |
| 同上 | `ECHO_STORE_PASSWORD` | keystore 口令 |
| 同上 | `ECHO_KEY_ALIAS` | key 别名（如 `echotimer`） |
| 同上 | `ECHO_KEY_PASSWORD` | key 口令（用 keytool 生成时一般与 keystore 口令相同） |
| Settings → Actions → General → Workflow permissions | 权限 | **Read and write permissions**（`gh release create` 需要） |
| Settings → Actions → General → Actions permissions | 允许的动作 | 允许 `actions/checkout`、`actions/setup-java`、`softprops/action-gh-release` |

生成 keystore 与 base64（不要提交 `.jks`，务必备份）：

```bash
keytool -genkeypair -v -keystore echotimer-release.jks \
  -alias echotimer -keyalg RSA -keysize 2048 -validity 10000
base64 -i echotimer-release.jks | tr -d '\n' > echotimer-release.b64   # Linux 用 base64 -w0
```

---

## 发版流程（打 tag 自动发布）

1. 修改版本号：`app/build.gradle.kts` 中的 `appVersionName`（如 `1.0.0`）与 `appVersionCode`（递增）。
2. 更新本文档末尾的「变更记录」。
3. 提交后打 tag 并推送：

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

4. 只有**推送 tag** 才会触发 `.github/workflows/release.yml`；推分支、开 PR 都不会跑这个工作流，也不会产出 APK。
   工作流依次执行：
   - 校验 tag 与 `versionName` 一致（`v1.0.0` ↔ `1.0.0`，不一致直接失败）
   - 解码 keystore，注入签名环境变量
   - `testReleaseUnitTest` → `lintRelease` → `distReleaseApk`
   - `apksigner verify` + `aapt2 dump badging` 校验签名与清单
   - `gh release create` 创建 Release，上传 APK 与 SHA-256 校验文件

本地想复现 CI 的全部步骤（不含上传）：

```bash
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:distReleaseApk
```

---

## 数据与迁移

### 表结构（当前 v3）

```sql
sessions(id, started_at, ended_at, total_ms, note, created_at)
laps(id, session_id → sessions.id ON DELETE CASCADE, lap_index, lap_ms, total_ms)
leaderboards(id, name UNIQUE NOCASE, created_at)
leaderboard_entries(id, board_id → leaderboards.id ON DELETE CASCADE,
                    player NOCASE, best_ms, updated_at, UNIQUE(board_id, player))

CREATE INDEX idx_sessions_started_at ON sessions(started_at DESC);
CREATE INDEX idx_laps_session_id ON laps(session_id, lap_index);
CREATE INDEX idx_leaderboard_entries_board ON leaderboard_entries(board_id, best_ms DESC);
```

### 版本历史

| 版本 | 变更 |
| --- | --- |
| v1 | 建 `sessions` / `laps` 两张表与两个索引 |
| v2 | `sessions` 增加 `note` 列（历史记录重命名） |
| v3 | 新增 `leaderboards` / `leaderboard_entries` 两张表与榜单索引（天梯榜） |

### 迁移规则（硬约束）

- 首次安装执行 `1..VERSION` 的全部迁移，升级安装只执行 `oldVersion+1..newVersion`，
  因此"新装"与"升级"的 schema 必然一致。
- **新增版本必须同时补一条 `Migration(toVersion = N, ...)`**；迁移链有缺口时 `EchoDb` 抛
  `IllegalStateException`，不会静默建出一张缺列的表。
- 迁移语句中**禁止** `DROP TABLE` / `DELETE FROM` / 重建表等破坏性操作，`MigrationsTest` 会静态拦截。
- `onDowngrade` 直接抛异常，不做 destructive migration。
- 外键约束在 `onConfigure` 里通过 `setForeignKeyConstraintsEnabled(true)` 开启（必须在事务外，等价于 `PRAGMA foreign_keys = ON`）。
- 计次在库内按 `lap_index` 升序存储；界面统一按「最后一次在最前」展示（计时页计次条、历史记录计次明细）。

---

## 播报规则

播报间隔是**单选**，语义是"每隔 N 秒播报一次当前时长"：

- 选 `1s` → 每秒播报一次：1s、2s、3s…
- 选 `5s` → 每 5 秒播报一次：5s、10s、15s…
- 选 `60s` → 每分钟播报一次：1 分、2 分…

时间点按**绝对时间**计算（是间隔的整数倍），不是"距上次播报过了多久"。其余规则：

- 同一个时间点只播报一次。
- 时间大幅跳跃（主线程卡顿、切后台回来）只播最新到点的那个，不补播中间所有点。
- 暂停期间累计时长不变，因此不播报；继续后从当前时间点接着算。
- 关闭播报时调度器仍会推进，重新打开不会把过去的时间点一次性补播。
- 运行中改间隔，按新间隔的整数倍继续播报；已经播过的时间点不会重播。

**播报文本只读数字**，不带"秒"：`5s → "5"`、`90s → "90"`、`3600s → "3600"`。
开始 / 暂停 / 结束的提示语仍是完整句子（如「计时结束，总时长 1 分」），不属于定时报时。

---

## 天梯榜规则

- 榜单可自定义多个，**名称唯一**（不区分大小写，数据库 `UNIQUE` + `NOCASE` 兜底，重名会被拒绝）。
- 进入某个榜单后，输入或从已有选手里选择姓名，即可开始挑战。
- 挑战是**不带计次**的秒表（没有「计次」按钮，也没有计次条），不响应语音指令，成绩只写入榜单、不写历史记录。
- 挑战**会报时**：与计时页共用同一份播报设置（开关、间隔），到点播报当前秒数（只读数字）。
- 支持**手工录入**：在榜单详情点「手工录入」，填姓名 + 成绩（`12.34`、`1:23.45`、`1:02:03.5` 均可），
  与挑战走完全相同的落库规则（只有严格更大才更新），录入结果直接显示在对话框里。
- 结束后按姓名落库：
  - 该姓名没有记录 → 新增；
  - 新成绩**严格大于**原记录 → 更新；
  - 否则保持原记录（页面会提示「未超过最佳」）。
- 榜单成绩按 `best_ms` **降序**排列；同分时更新时间早的在前。
- 列表里第一名带小皇冠、最后一名带小菜刀（只有一个人时按第一名显示皇冠）。
- 每条成绩可单独删除（右侧垃圾桶图标 + 二次确认），删除后榜单人数与最佳成绩同步刷新。
- 删除榜单会级联删除其全部成绩。

## 语音指令规则

| 口令（中 / 英） | 动作 |
| --- | --- |
| 开始 / 开始计时 / 启动 / 计时 / start / begin / go | 开始计时（暂停中则继续） |
| 暂停 / pause | 暂停 |
| 继续 / 接着 / resume / continue | 继续 |
| 结束 / 结束计时 / 停止 / 停下 / 停 / 完成 / stop / end / finish / done | 结束并保存 |
| 计次 / 记一圈 / 一圈 / 分段 / lap / split | 记一次计次 |

**前提**：语音识别依赖设备上的系统识别服务（`RecognitionService`，例如 Google 语音服务或厂商自带语音服务）。
设备没有这类服务时，语音指令不可用 —— 界面会显示「未检测到语音识别服务」，点击可查看检测结果
（找到了几个服务、系统是否报告可用），并提供跳转到系统语音设置的入口。此时按钮操作完全不受影响。

处理流程（全部自研）：

1. **归一化**：全角转半角 → 转小写 → 只保留字母/数字/汉字 → 去掉「请 / 帮我 / 麻烦 / 一下 / 吧 / 了 / please / now」等语气词。
2. **否定拦截**：出现「不要 / 不用 / 别 / 取消 / not」直接放弃整条指令。
3. **优先级匹配**：暂停 > 继续 > 计次 > 结束 > 开始（保证「暂停」不会被「停」抢走、「结束计时」不会被「计时」抢走）。
4. **去抖**：同一条指令 1.2 秒内只执行一次（部分结果与最终结果常常重复）。
5. **响应时机**：部分识别结果命中即执行，不等最终结果。
6. **与播报互斥**：TTS 出声期间取消监听，结束后冷却 320ms 再重新监听，避免把自己的报时识别成口令。
7. **失败退避**：`NO_MATCH` / `SPEECH_TIMEOUT` 立即重连；`RECOGNIZER_BUSY` 线性退避；连续 5 次异常则停止重试并在界面提示。

---

## 权限

| 权限 | 用途 |
| --- | --- |
| `RECORD_AUDIO` | 语音指令。仅用于识别「开始」「结束」等口令；应用不录音、不保存音频、不上传音频 |
| `INTERNET` | 系统语音识别服务进行在线识别时需要联网；应用本身不上传任何数据 |

清单中还有一条由 AndroidX 自动合并的 `com.echotimer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（应用私有签名级权限，非用户可见权限）。

历史记录只存在本机 SQLite，没有账号体系、云端同步与统计埋点。

---

## 质量与验证

### 编译零警告

- Kotlin：`compilerOptions { allWarningsAsErrors.set(true) }`，任何警告即构建失败。
- Lint：`warningsAsErrors = true`、`abortOnError = true`，`lintDebug` / `lintRelease` 均输出 `No issues found.`
- `app/lint.xml` 有两条显式忽略，都写明原因：
  - `ObsoleteSdkInt`（仅 `mipmap-anydpi-v26`）：AAPT 要求 `<adaptive-icon>` 放在带版本限定的目录，该提示属于误报。
  - `OldTargetApi`：CI 镜像会随平台更新预装更新的 Android platform，而 lint 在"未使用最新 API"时报错，
    会让发版被环境升级卡住。这里忽略该检查（项目当前 targetSdk = 36），改版时按需手动检查目标 API。

### 单元测试（64 个，全部通过）

| 测试类 | 用例数 | 覆盖点 |
| --- | --- | --- |
| `StopwatchEngineTest` | 10 | 开始/暂停/继续/结束/重置/计次、暂停期间不累计、重复 start 不清零 |
| `AnnouncementSchedulerTest` | 9 | 1s 每秒播、5s 只在整数倍播、去重、跳跃只播最新、时间回退重同步、运行中改间隔 |
| `ElapsedFormatterTest` | 6 | `mm:ss.SS` / `h:mm:ss.SS`、负值兜底、播报只读数字、结束提示省略 0 位 |
| `IntervalMathTest` | 5 | 下一个播报点、按所选间隔推进、进度环比例 |
| `VoiceCommandParserTest` | 9 | 中英文口令、全角标点、否定词、优先级、无关文本 |
| `SettingsCodecTest` | 5 | 主题模式往返、配色回落、间隔钳制、默认值合法性 |
| `PaletteContrastTest` | 3 | 6 套配色的文字对比度 ≥ 4.5:1、阴影方向正确 |
| `MigrationsTest` | 7 | 版本连续、无破坏性语句、v1 建表完整、v2 加列带默认值、v3 天梯表 |
| `LeaderboardRulesTest` | 5 | 名称归一化/空值/超长、首次上榜、只有严格更大才更新 |
| `TimeInputParserTest` | 5 | 纯秒数 / 分:秒 / 时:分:秒、全角输入、非法与超范围输入 |

### 真实引擎验证（迁移）

`python3 tools/verify_migrations.py` 把 `Migrations.kt` 里的 SQL 原文抽出来交给真实 `sqlite3` 执行，实际输出：

```
PASS  能解析出迁移定义
PASS  Migrations.VERSION 等于最大迁移版本
PASS  迁移版本从 1 开始且连续
PASS  新装与升级后的 schema 完全一致
PASS  sessions 表包含 note 列
PASS  laps 表存在
PASS  索引齐全
PASS  升级后原有数据仍在且新列取默认值
PASS  升级后计次数据仍在
PASS  删除 session 时级联删除 laps
PASS  note 列 NOT NULL 约束生效
PASS  天梯榜可写入榜单与成绩
PASS  榜单名唯一约束生效
PASS  榜单名唯一性不区分大小写（NOCASE）
PASS  同榜同名唯一且不区分大小写
PASS  成绩按 best_ms 降序
PASS  删除榜单时级联删除成绩

全部通过：3 条迁移，数据库版本 v3
```

### APK 产物验证

- `apksigner verify`：`Verifies`，APK Signature Scheme v2 通过，签名者 1 个。
- `aapt2 dump badging`：`package: com.echotimer versionCode='2' versionName='1.0.1'`，`targetSdkVersion:'35'`，
  权限只有 `RECORD_AUDIO` 与 `INTERNET`（另有 AndroidX 自动合并的私有权限），启动 Activity 为 `com.echotimer.MainActivity`。
- 签名两条通道都已实测：`keystore.properties` 与 `ECHO_*` 环境变量分别产出的 APK 证书不同，
  证明取值来源确实切换了；CI 环境下缺签名材料时构建按预期失败。

### 文档一致性

`python3 tools/check_consistency.py` 核对：

- 技术栈版本：根构建脚本 / `BuildConfig` 注入 / 本 README 三处一致
- 版本号、minSdk / targetSdk：构建脚本 / README / 关于页文案一致
- 字符串资源：代码引用的都存在，且没有未使用项
- 权限：清单声明的都在 README 与关于页里说明
- 测试数量：README 表格里每个测试类的用例数 == 源码 `@Test` 数量，总数 == 各文件之和
- 数据库版本：`Migrations.VERSION` == README「当前 vN」，版本历史表覆盖每个版本
- 产物命名：构建脚本的 `EchoTimer.apk` == README 里的产物路径

---

## 已知限制与未验证项

明确区分"已验证"与"未验证"，不把没跑过的说成通过：

**已验证**

- 编译（debug / release）、Kotlin 零警告、lint 零问题
- 64 个单元测试全部通过
- 真实 `sqlite3` 上的迁移与数据保留、级联删除
- release APK 构建、签名、清单与权限核对（两条签名通道 + CI 缺签名保护）

**未验证（本机无 Android 设备/模拟器，也没有远端仓库）**

1. **真机运行**：界面渲染效果（拟态阴影、配色观感）、交互手感、冷启动速度均未在设备上验证。
2. **TTS 实际发音**：`TextToSpeech` 的中文语音包、音色、播报时机未在设备上听测。
3. **语音识别命中率**：`SpeechRecognizer` 在不同厂商 ROM 上的识别质量、连续监听的耗电与稳定性未实测。
4. **R8 混淆后的运行时行为**：release 已开启 `minifyEnabled` + `shrinkResources`，构建通过、APK 结构正常，
   但混淆后的运行路径未在设备上验证。
5. **GitHub Actions 工作流**：本机没有远端仓库，未实际触发；已用与工作流相同的命令序列在本地复现通过。

**产品层面的限制**

- 历史记录最多加载最近 200 条（`HistoryRepository.LIMIT`）。
- 播报间隔上限 3600 秒；间隔为 0（未设置）时不播报。
- 语音识别依赖系统识别服务：设备未安装此类服务时语音指令不可用（界面会说明原因并提供跳转设置入口），
  离线能力也取决于该服务；这两种情况都可以继续用按钮操作。
- 播报间隔为单选；"1s 和 10s 都播"这种组合当前不支持（已改为单选语义，避免歧义）。
- 不支持数据导出/同步，卸载即清除。
- 天梯挑战不计次、不播报、不响应语音指令（独立于计时页的秒表）；榜单名与姓名上限 20 个字符。
- 天梯成绩"越大越好"：只有新成绩严格大于原记录才会更新。
- 横屏/分屏时计时页切换为可滚动布局（表盘缩至 200dp），未针对大屏做专门排版。

---

## 变更记录

### v1.0.0（首次发布）

- 秒表：开始 / 暂停 / 继续 / 计次 / 结束 / 重置，基于单调时钟差分，长时间运行不漂移。
- 定时语音播报：单选间隔（1s–10m），按绝对时间点播报，**只读数字**；开始/暂停/结束有提示语。
- 语音指令：说「开始」「结束」等口令控制计时，中英文均可；无识别服务的设备会给出可诊断提示。
- 历史记录：每次结束自动入库（可关），带计次明细（最后一次在前），支持重命名、删除。
- 天梯榜：自定义多个榜单（名称唯一）、输入或选择姓名开始**不计次**但会报时的挑战；
  支持手工录入成绩与单条删除；成绩只有严格更大才更新，列表按秒数降序，第一名小皇冠、最后一名小菜刀。
- 拟态界面：3 套配色（石墨 / 暖沙 / 雾青）× 明暗两态，全局切换立即生效。
- 数据：SQLite v3（显式迁移，v1 计时记录 → v2 备注列 → v3 天梯榜）。
- 发布：推送 `v*` tag 触发 GitHub Actions，签名构建 APK（文件名带版本号）并创建 Release。
  目标 API 为 36（Android 16），最低支持 26（Android 8.0）。
