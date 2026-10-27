# EchoTimer

语音播报秒表 · 原生 Android（Kotlin + Jetpack Compose）

一个不依赖任何第三方 UI 库 / 依赖注入框架的秒表应用：计时过程中按你设定的时间点用语音报时，
并且可以用「开始」「结束」这类口令直接控制计时。计时结果以多组记录的形式保存在本机。

当前版本：1.5.1

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
- [开源协议](#开源协议)
- [变更记录](#变更记录)

---

## 功能

| 能力 | 说明 |
| --- | --- |
| 秒表计时 | 开始 / 暂停 / 继续 / 计次 / 结束 / 重置。基于单调时钟差分，长时间运行不累积漂移 |
| 定时语音播报 | 单选播报间隔（1s / 2s / 5s / 10s / 15s / 30s / 60s / 2m / 5m / 10m）：选 5 秒就每 5 秒报一次，只读数字（如 `5`、`90`） |
| 语音指令 | 说「开始」开始计时，说「结束」结束并保存；另支持暂停 / 继续 / 计次，中英文口令均可 |
| 离线口令识别 | 设备没有系统语音识别服务时（国行 ColorOS / HyperOS 等），可录制 5 条口令，之后完全在本机匹配，不联网、不依赖任何系统服务 |
| 多组历史记录 | 每次结束自动入库（可关闭），带计次明细（最后一次在最前），支持重命名、删除 |
| 天梯榜 | 自定义多个榜单（名称唯一），输入或选择姓名后开始不计次的挑战（**同样按设定间隔报时**）；支持**手工录入**成绩；只有超过原记录才更新，榜单按秒数降序 |
| 榜单只读 | 设置里「允许修改榜单」默认关闭：关闭时不能手工录入、不能删除成绩或榜单，成绩只能靠真实挑战产生（新建榜单与挑战不受影响） |
| 数据同步（可选） | 把榜单写到自己的 GitHub 仓库：读远端 → 双向合并 → 写回，成绩只增不减，内容没变化不产生提交；**榜单一变就自动同步** |
| 网页展示 | 仓库里的 `docs/index.html` 直接读同步文件渲染榜单，可挂 GitHub Pages 或本地打开 |
| 榜单分享 | 把整个榜单导出成一张 PNG（第一名皇冠、最后一名整行标红，配色跟随当前主题），可分享到任意应用或存进相册 |
| 拟态界面 | 3 套配色（石墨 / 暖沙 / 雾青）× 明暗两态，全局切换立即生效 |
| 关于页 | 应用名、版本号、技术栈、支持范围、权限说明 |

---

## 技术栈

| 项目 | 版本 / 取值 |
| --- | --- |
| 语言 | Kotlin 2.1.0（`allWarningsAsErrors = true`） |
| 界面 | Jetpack Compose，Compose BOM 2024.12.01 + Material3（只用主题与文本样式；对话框、输入框、开关均为自绘拟态组件） |
| 构建 | Gradle 8.11.1 + AGP 8.7.3，JVM 目标 17，构建使用 JDK 21 |
| SDK | minSdk 26（Android 8.0），targetSdk / compileSdk 36（Android 16，API 36） |
| 数据 | 手写 `SQLiteOpenHelper` + 显式版本迁移（未使用 Room） |
| 语音合成 | 系统 `TextToSpeech` |
| 语音识别 | 系统 `SpeechRecognizer`（`RecognitionService`），或自研离线口令匹配 |
| 测试 | JUnit4（158 个 JVM 单测）+ 真实 `sqlite3` 迁移验证脚本 |

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
| 榜单分享图 | 自研 `LeaderboardPoster` | 用 `android.graphics` 按数据排版，不依赖截屏 API，长榜单也能整张导出 |
| 榜单同步 | 自研 `SyncCodec` / `SyncMerger` | NDJSON 编解码与合并策略手写；只用 `HttpsURLConnection` 调 GitHub Contents API，不引 git 库 |
| 数据层 | 自研 `EchoDb` + `Migrations` | 显式迁移，缺失迁移直接抛异常 |
| 语音合成 | 系统 `TextToSpeech` | 平台能力；调度与文本生成是自研的 |
| 语音识别 | 系统 `SpeechRecognizer` | 平台能力，仅作"声音 → 文本"输入；文本如何变成动作是自研的 |
| 离线口令识别 | 自研 `Mfcc` / `Dtw` / `VoiceMatcher` | FFT、Mel 滤波器组、DCT、DTW 全部手写；不引任何语音库，也不调用系统识别 |
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
│   ├── share/                   # 分享图版面常量与文件名规则（纯函数）
│   ├── sync/                    # 榜单同步：NDJSON 编解码、双向合并、GitHub Contents API
│   ├── settings/                # 设置模型、序列化、唯一读写入口
│   ├── theme/                   # 配色规格（纯数据，可单测对比度）
│   ├── time/                    # 秒表内核、播报调度、时间格式化
│   └── voice/                   # 指令解析、TTS、系统识别控制器、设备能力诊断
│       └── offline/             # 离线口令：MFCC、DTW、模板存储、端点检测、采集
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
    ├── components/              # 拟态控件、拟态弹窗/输入框、手绘图标、麦克风权限、语音能力状态
    ├── neumorph/                # 拟态绘制原语
    ├── share/                   # 榜单分享图渲染（android.graphics）+ 分享/存相册
    ├── screens/                 # 计时 / 记录 / 天梯 / 挑战 / 设置 / 关于
    └── theme/                   # Compose 主题适配
app/src/test/java/com/echotimer/ # 158 个 JVM 单测
docs/index.html                  # 读同步文件渲染榜单的静态页（可挂 GitHub Pages）
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
- 列表里第一名带小皇冠，**最后一名整行标红**（行底色、姓名与成绩都用红色；只有一个人上榜时按第一名处理，不标红）。
- 每条成绩可单独删除（右侧垃圾桶图标 + 二次确认），删除后榜单人数与最佳成绩同步刷新。
- 点右上角「分享」可把整个榜单导出成图片：**按数据重新排版**（不是截屏，长榜单也能一张图装下），
  配色跟随当前主题，第一名画皇冠、最后一名整行标红；超过 40 人时只画前 40 名并注明总人数。
  图片可分享到任意应用，Android 10 及以上还可直接存进相册 `Pictures/EchoTimer`。
- 删除榜单会级联删除其全部成绩。
- **修改权限**：设置 →「天梯榜」→「允许修改榜单」默认**关闭**（只读）。
  关闭时：手工录入、删除成绩、删除榜单全部隐藏，榜单详情页显示一行锁定说明；
  新建榜单与「开始挑战」仍然可用——也就是说成绩只能靠真的跑一次来产生，防止手改分数或误删。

## 数据同步与网页（可选）

默认**关闭**，不配置就完全本地运行。开启后把榜单写进你自己的 GitHub 仓库，
再用仓库里的 `docs/index.html` 展示。

### 配置

设置 →「数据同步」：

| 字段 | 说明 |
| --- | --- |
| 仓库 | `owner/name`，也接受粘贴 `https://github.com/owner/name` 或 SSH 地址 |
| 分支 | 默认 `main` |
| 文件路径 | 默认 `echotimer/leaderboard.ndjson`（与网页默认值一致） |
| 访问令牌 | classic token 勾 `repo`；fine-grained token 需要该仓库的 **Contents: Read and write** |

令牌只保存在本机 SharedPreferences，**不会**写进同步文件；仓库若是公开的，成绩也会公开。

### 同步规则

每次点「立即同步」都会：读远端 → 与本地双向合并 → 写回仓库 → 写回本地数据库。

- 身份：榜单按**名称**、成绩按**榜单名 + 玩家名**匹配（名称不区分英文大小写）；
- 成绩**只增不减**：两边取更好的那次，因此删除的记录下次同步会从仓库回来；
- **没有变化就不提交**（否则每点一次同步都会多一个空 commit）；
- 并发写冲突（远端 sha 变了）会重新读取合并再试一次。

### 自动同步

开启同步且配置完整后，**榜单数据一变就会自动同步**，不需要手动点：

| 触发时机 | 说明 |
| --- | --- |
| 挑战结束并写入成绩 | 只有成绩真的变了（新增或刷新）才触发 |
| 手工录入成绩 | 同上，被"只有更大才更新"挡掉时不触发 |
| 删除成绩 / 删除榜单 / 新建榜单 | 操作成功后触发 |

- **防抖**：最后一次改动后等 4 秒再发请求，连删几条只同步一次；
- **排队**：已有同步在跑时，新请求在互斥锁上排队，不会丢；
- **失败不打扰**：自动同步失败只在榜单页顶部显示一行「同步失败 · 点此重试」，不弹窗；
- 没开同步、或仓库/令牌没填完整时**完全不发请求**，本地照常用；
- 手动「立即同步」仍然保留，会取消待执行的自动同步。

### 覆盖模式（可选，默认关闭）

「以本地为准（覆盖仓库）」打开后，同步**不再合并**，直接把本地数据整份写上去：

- 用途：本地是权威数据，仓库只做展示/备份；或者想清掉仓库里多余的记录；
- 保护一：本地一条数据都没有时**拒绝执行**（否则等于清空仓库）；
- 保护二：本地与仓库内容一致时**不提交**；
- 覆盖掉的旧内容仍留在 GitHub 的提交历史里，可以找回。

### 文件格式

NDJSON，每行一个扁平 JSON 对象：

```json
{"type":"meta","version":1,"updatedAt":1700000000000}
{"type":"board","name":"单杠悬垂","createdAt":1700000000000}
{"type":"entry","board":"单杠悬垂","player":"张三","bestMs":63000,"updatedAt":1700000000000}
```

### 网页

`docs/index.html` 是单文件静态页（无任何外部依赖），读取
`https://raw.githubusercontent.com/{repo}/{branch}/{path}` 后渲染：
第一名皇冠、最后一名整行标红、时间格式与 App 一致。

- 直接用浏览器打开也可以（raw.githubusercontent 允许跨域）；
- 想在线访问：仓库 Settings → Pages → Source 选 `Deploy from a branch`，目录选 `/docs`，
  之后访问 `https://{owner}.github.io/{repo}/`；
- 换仓库/分支不用改代码，用查询参数即可：
  `?repo=owner/name&branch=main&path=echotimer/leaderboard.ndjson`。

## 语音指令规则

有两种引擎，设置页可切换（默认「自动」）：

| 引擎 | 依赖 | 适用场景 |
| --- | --- | --- |
| 系统识别 | 设备上有应用实现 `RecognitionService`（通常是 Google 语音服务 + GMS） | 国际版 / 装了 GMS 的机型，能听懂各种说法 |
| 离线口令 | 无（自研） | 国行 ColorOS / HyperOS / HarmonyOS 等没有该接口的机型 |

「自动」的优先级：系统可用就用系统，否则用离线口令。两者都不可用时，语音状态条点开会引导去录制口令。

### 系统识别模式

| 口令（中 / 英） | 动作 |
| --- | --- |
| 开始 / 开始计时 / 启动 / 计时 / start / begin / go | 开始计时（暂停中则继续） |
| 暂停 / pause | 暂停 |
| 继续 / 接着 / resume / continue | 继续 |
| 结束 / 结束计时 / 停止 / 停下 / 停 / 完成 / stop / end / finish / done | 结束并保存 |
| 计次 / 记一圈 / 一圈 / 分段 / lap / split | 记一次计次 |

处理流程（全部自研）：

1. **归一化**：全角转半角 → 转小写 → 只保留字母/数字/汉字 → 去掉「请 / 帮我 / 麻烦 / 一下 / 吧 / 了 / please / now」等语气词。
2. **否定拦截**：出现「不要 / 不用 / 别 / 取消 / not」直接放弃整条指令。
3. **优先级匹配**：暂停 > 继续 > 计次 > 结束 > 开始（保证「暂停」不会被「停」抢走、「结束计时」不会被「计时」抢走）。
4. **去抖**：同一条指令 1.2 秒内只执行一次（部分结果与最终结果常常重复）。
5. **响应时机**：部分识别结果命中即执行，不等最终结果。
6. **与播报互斥**：TTS 出声期间取消监听，结束后冷却 320ms 再重新监听，避免把自己的报时识别成口令。
7. **失败退避**：`NO_MATCH` / `SPEECH_TIMEOUT` 立即重连；`RECOGNIZER_BUSY` 线性退避；连续 5 次异常则停止重试并在界面提示。

### 离线口令模式（自研）

设置页 →「离线口令」→「录制口令」，把 **开始 / 暂停 / 继续 / 结束 / 计次** 各录一遍
（用平时音量、离手机 20~30cm）。**建议每条录 2 遍**：两遍之间的差异用来定匹配门限，
比固定系数可靠得多，识别也更稳。录完即可离线识别，之后不再依赖任何系统服务。

处理链路：

1. **采集**：`AudioRecord` 16kHz / 单声道 / PCM16，音源用 `VOICE_RECOGNITION`（系统降噪）。
2. **端点检测**：启动先静音校准取噪声底，门限 = 噪声底 × 2.5（下限 300 RMS）；
   保留 200ms 前摇，避免把声母切掉；连续静音 400ms 判定说完，超过 2.5 秒强制结束。
   训练时用更宽松的门限（噪声底 × 2.0，下限 250）——用户就在等提示开口，宁可多录一点。
3. **特征**：预加重 → 25ms 分帧 / 10ms 跳 → 汉明窗 → 512 点 FFT → 功率谱 → 26 组 Mel 滤波器 → 取对数 →
   DCT-II 取 13 阶 → **CMN**（减倒谱均值，抵消麦克风频响、音色、音量差异）。
4. **匹配**：与每条模板做 **DTW**（时间轴可拉伸），取距离最小的一条；同一条口令录了多遍就取最近的那遍。
   DTW 带两个约束：**带宽**（对齐路径偏离对角线不超过 30%，防止把无关语音硬拉成一样）
   和**长度闸门**（两段帧数差超过 2.2 倍直接判为不相关）。
5. **门限**：用你自己的模板量出来，而不是拍脑袋的常数——
   - 有类内距离（某条口令录了 2 遍）→ 门限 = min(类内 × 2.0, 类间 × 0.8)；
   - 只有单遍 → 门限 = 类间 × 灵敏度系数（默认 0.55，可调低/中/高）；
   - 只录了 1 条命令 → 兜底门限 1.0（没有第二类可参照，只能偏保守，所以建议录满 5 条）；最终钳制在 0.5~6 之间。
6. **必须明显更近**：最近的距离还要 ≤ 第二像的**不同口令** × 0.72，否则判为"太接近、不敢认"。
   乱说话时各口令的距离会挤在一起（比如 0.87 / 0.95），只看绝对门限很容易误命中，这条是关键防线。
7. **不误触发优先**：任一条件不满足就当作没听懂；同一条指令 1.2 秒内只派发一次。
7. **与播报互斥**：TTS 出声期间丢弃音频，播完自动恢复。

隐私：模板只保存 MFCC 特征（每遍约 2.6KB，Base64 存在 SharedPreferences），**不保存原始音频**。

**出问题怎么看**：训练弹窗现在把关键数字都显示出来，不用猜——

- 录音时实时显示「峰值 dBFS · 触发 dBFS」：峰值到不了触发门限就不会开始录音；
- 结果卡片固定在最上方：录到几遍、多少秒；测试识别则给出命中口令、距离、门限；
- 底部诊断行：模板几条命令几遍、类内距离、类间距离、当前门限。

常见对应关系：峰值一直低于触发门限 → 声音太小或麦克风被占用；距离比门限大很多 → 两次录音差异大，
再录一遍；只差一点 → 把灵敏度调高。

---

## 权限

| 权限 | 用途 |
| --- | --- |
| `RECORD_AUDIO` | 语音指令。系统识别模式只把音频交给系统服务；离线模式只在本机提取特征做匹配。两种模式都不保存音频、不上传音频 |
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

### 单元测试（158 个，全部通过）

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
| `ShareNamingTest` | 5 | 文件名清洗、空名回落、长度截断、时间戳格式 |
| `PosterLayoutTest` | 5 | 版面高度计算、行数上限、位图大小不超安全值 |
| `MfccTest` | 6 | 分帧数量、特征维数、同输入同结果、时序不同则特征不同、静音裁剪 |
| `DtwTest` | 4 | 自距离为 0、对称性、时间拉伸优于内容不同、空输入拒绝 |
| `VoiceTemplateCodecTest` | 8 | 单/多遍往返、v1 旧格式兼容、空/脏数据、版本不符、截断、体积上限 |
| `VoiceMatcherTest` | 12 | 类间/类内距离、门限收紧与钳制、单模板兜底、命中、拒绝无关声音、歧义拒绝、拒绝原因、诊断统计、排序 |
| `MatcherSyntheticSpeechTest` | 3 | 合成语音：5 条口令可识别、白噪声拒绝、没录过的生词拒绝 |
| `UtteranceSegmenterTest` | 5 | 静音不触发、说完即结束、超长强制结束、噪声底自适应、reset |
| `VoiceEngineResolverTest` | 5 | 自动/系统/离线三种偏好的完整组合 |
| `VoiceSettingsCodecTest` | 6 | 引擎与灵敏度往返、脏数据回落、默认值、sanitize 保留 |
| `LeaderboardPermissionTest` | 2 | 榜单默认只读、sanitize 保留修改权限 |
| `JsonTest` | 6 | 转义往返、字符串/数字/布尔、跳过嵌套对象、非法输入、\u 转义 |
| `SyncCodecTest` | 6 | NDJSON 往返、中文与引号、坏行跳过、未知类型、空输入、脏记录过滤 |
| `SyncMergerTest` | 7 | 双向合并、取更好成绩、名称大小写、孤儿成绩补榜单、幂等、内容等价、非法记录 |
| `SyncConfigTest` | 6 | 仓库地址归一化（URL / .git / SSH）、非法输入、默认分支与路径、缺项提示 |
| `SyncPolicyTest` | 4 | 覆盖模式：空本地拒绝、有榜单/成绩可覆盖、默认关闭 |
| `AutoSyncPolicyTest` | 4 | 自动同步开关、配置不完整不发请求、完整配置触发、防抖窗口 |

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
- `aapt2 dump badging`：`package: com.echotimer versionCode='2' versionName='1.0.1'`，`targetSdkVersion:'36'`，
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
- 158 个单元测试全部通过
- 真实 `sqlite3` 上的迁移与数据保留、级联删除
- release APK 构建、签名、清单与权限核对（两条签名通道 + CI 缺签名保护）

**未验证（本机无 Android 设备/模拟器，也没有远端仓库）**

1. **真机运行**：界面渲染效果（拟态阴影、配色观感）、交互手感、冷启动速度均未在设备上验证。
2. **TTS 实际发音**：`TextToSpeech` 的中文语音包、音色、播报时机未在设备上听测。
3. **语音识别命中率**：`SpeechRecognizer` 在不同厂商 ROM 上的识别质量、连续监听的耗电与稳定性未实测。
   离线口令识别的准确率、阈值手感同样需要在真机上用真实嗓音校准（单测只覆盖算法与边界）。
4. **R8 混淆后的运行时行为**：release 已开启 `minifyEnabled` + `shrinkResources`，构建通过、APK 结构正常，
   但混淆后的运行路径未在设备上验证。
5. **分享图的实际渲染**：`LeaderboardPoster` 只做了版面尺寸与文件名的单元测试；
   中文字形、长姓名省略号、深色主题下的观感需要真机看，未在设备上验证。
6. **相册保存**：走 MediaStore（Android 10+），未在真机上确认相册里能看到。
7. **GitHub Actions 工作流**：已实际跑通（v1.0.0 发布成功），本文件记录的是当时那版的流水线。

**产品层面的限制**

- 历史记录最多加载最近 200 条（`HistoryRepository.LIMIT`）。
- 播报间隔上限 3600 秒；间隔为 0（未设置）时不播报。
- 系统识别依赖 `RecognitionService`：国行 ColorOS / HyperOS 等系统不提供，需要装 Google 语音服务（GMS）；
  这类机型改用**离线口令识别**即可，不依赖系统服务。
- 离线口令识别只能认固定 5 条口令，换人说话或换手机需要重新录；录音距离、环境噪声会影响命中率，
  识别不准时可在训练弹窗里调灵敏度（并看「测试识别」显示的距离/门限）。
- 离线识别会持续占用麦克风，耗电高于待机；长时间不用建议关掉语音指令开关。
- 播报间隔为单选；"1s 和 10s 都播"这种组合当前不支持（已改为单选语义，避免歧义）。
- 不支持数据导出/同步，卸载即清除。
- 天梯挑战不计次、不响应语音指令（独立于计时页的秒表），但**会按设定间隔报时**；榜单名与姓名上限 20 个字符。
- 分享图最多画 40 行（更大的榜单会截断并注明总人数），单张 PNG 约 1–24 MB。
- 天梯成绩"越大越好"：只有新成绩严格大于原记录才会更新。
- 横屏/分屏时计时页切换为可滚动布局（表盘缩至 200dp），未针对大屏做专门排版。

---

## 开源协议

本项目以 **Apache License 2.0** 开源，全文见仓库根目录的 [LICENSE](LICENSE)。

- 可自由使用、修改、分发，包括商业用途；
- 分发时需保留版权声明与许可证副本，修改过的文件要标注改动；
- 附带专利授权；同时按 "AS IS" 提供，不承担担保责任。

依赖说明：只使用 AndroidX / Jetpack Compose 等官方库（均为 Apache 2.0），
未引入第三方 UI 库、依赖注入框架或语音识别库；系统语音识别与 TTS 属于平台能力，不随本项目分发。

---

## 变更记录

### v1.5.1

- 补充 **Apache License 2.0**（仓库根目录 `LICENSE`），README 增加「开源协议」一节，
  关于页与网页页脚同步标注。

### v1.5.0

- 榜单变化后**自动同步**：挑战结束、手工录入、删除成绩/榜单、新建榜单都会触发；
  4 秒防抖合并连续操作，同步中排队不丢，失败只在榜单页顶部提示并可点击重试。
- 未开启同步或仓库/令牌不完整时完全不发请求。
- 榜单列表顶部新增一行同步状态（正在同步 / 已同步 · 时间 / 失败可重试）；设置页的结果会标注「自动」。
- 新增 `AutoSyncPolicyTest`，单测 154 → 158。
- 版本号 1.5.0（versionCode 10）。

### v1.4.1

- **修复离线口令"乱说也能匹配到开始"**：
  - 门限下限从 1.0 降到 0.5、单遍系数 0.65 → 0.55。之前下限把"类内 × 2"的数据驱动结果盖掉了，
    噪声距离（0.87）刚好落在门限（1.01）里面。
  - 新增**「必须明显更近」**判据：最近距离还要 ≤ 第二像的不同口令 × 0.72，否则判为"太接近、不敢认"。
  - DTW 增加**带宽约束**（偏离对角线 ≤ 30%）与**长度闸门**（帧数差 > 2.2 倍直接不相关），
    防止把无关语音硬拉成一样。
  - 训练弹窗区分两种失败：距离超门限 / 两个口令太接近，并给出对应建议。
- 新增合成语音回归测试（5 条口令可识别、白噪声与生词必须拒绝），单测 148 → 154。
- 版本号 1.4.1（versionCode 9）。

### v1.4.0

- 数据同步新增**「以本地为准（覆盖仓库）」**开关，默认关闭：打开后同步不再合并，
  直接用本地数据覆盖仓库文件（旧内容仍在提交历史里）；本地为空时拒绝执行，内容一致时不提交。
- 版本号 1.4.0（versionCode 8）。

### v1.3.0

- 新增**可选的数据同步**：把榜单写到自己的 GitHub 仓库（Contents API），
  读远端 → 双向合并 → 写回仓库与本地；成绩只增不减、内容没变化不产生提交、sha 冲突自动重试。
  令牌只存本机，同步文件里不含令牌。
- 新增**网页展示**：`docs/index.html` 单文件静态页，直接读仓库里的 NDJSON 渲染榜单
  （第一名皇冠、最后一名标红），可挂 GitHub Pages 或本地打开。
- 同步文件格式为 NDJSON，编解码与合并策略全部自研并有单测（新增 25 个，总数 119 → 144）。
- 版本号 1.3.0（versionCode 7）。

### v1.2.1

- **修复离线口令"录了但用不了、测试也没反馈"**：
  - 端点检测门限从「噪声底 × 3.5，下限 600 RMS」放宽到「× 2.5，下限 300」，训练时更宽（× 2.0 / 250）——
    之前轻声说话根本触发不了录音；裁剪静音的幅度阈值也从 600 降到 300，避免整段被裁光。
  - 每条口令支持录 **2 遍**，匹配时取最近的一遍；门限改为用模板量出来的
    min(类内 × 2.0, 类间 × 0.8)，不再依赖固定系数。
  - 结果卡片移到弹窗**最上方**（原来在列表底部，经常看不到），并显示峰值/触发门限/距离/门限等数字。
  - 新增诊断行：模板条数与遍数、类内/类间距离、当前门限；录音时实时显示峰值与触发门限。
- 模板编码升级到 v2（多遍），**兼容读取旧的 v1 数据**，已录过口令不用重录。
- 版本号 1.2.1（versionCode 6）。

### v1.2.0

- 新增**榜单修改权限**开关（设置 →「天梯榜」），默认**关闭**（只读）：
  关闭时隐藏手工录入、删除成绩、删除榜单，详情页显示锁定说明；
  新建榜单与「开始挑战」不受影响，成绩只能靠真实挑战产生。
- 版本号 1.2.0（versionCode 5）。

### v1.1.0

- **新增自研离线口令识别**：国行 ColorOS / HyperOS 等没有 `RecognitionService` 的机型，
  可以录 5 条口令（开始 / 暂停 / 继续 / 结束 / 计次），之后完全在本机匹配，不联网、不依赖系统服务。
  链路：16kHz 采集 → 能量端点检测 → MFCC（自写 FFT / Mel / DCT）→ 与模板做 DTW → 自适应门限。
- 设置页新增「识别方式」（自动 / 系统识别 / 离线口令）与「离线口令」训练入口、灵敏度三档；
  训练弹窗带「测试识别」，直接显示命中口令与实际距离/门限。
- 语音状态条在离线模式下显示「离线口令 · 正在听」；两者都不可用时，弹窗主推「录制口令」。
- 语音不可用的说明按设备实际情况分两种（没装服务 / 装了但不可用），不再跳到与识别无关的
  「默认数字助理应用」页面。
- 新增 38 个单元测试（MFCC、DTW、模板编解码、匹配、端点检测、引擎选择、设置编解码）。
- 版本号 1.1.0（versionCode 4）。

### v1.0.2

- 天梯榜最后一名不再显示小菜刀，改成**整行标红**（行底色、名次徽标、姓名与成绩都带红），
  语义更直观；只有一个人上榜时不标红。榜单分享图同步调整。
- 版本号 1.0.2（versionCode 3）。

### v1.0.1

- **弹窗改成拟态风格**：新增 `NeumorphicDialog` / `NeumorphicDialogButton` / `NeumorphicTextField`，
  替换全部 Material3 默认对话框（新建榜单、开始挑战、手工录入、删除榜单/成绩、重命名记录、放弃挑战、语音不可用）。
  原来是白底直角面板 + 描边输入框，跟整体风格割裂；现在是与页面同款的圆角拟态面板、凹陷输入槽、胶囊按钮。
- **榜单可导出图片分享**：详情页右上角「分享」→ 按数据重新排版成 PNG（不是截屏），
  可分享到任意应用；Android 10+ 支持存进相册 `Pictures/EchoTimer`。
- 版本号 1.0.1（versionCode 2）。

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
