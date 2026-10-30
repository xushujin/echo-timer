package com.echotimer.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.echotimer.BuildConfig
import com.echotimer.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 更新流程的状态机。界面只读这个，不自己维护"是否在下载"之类的布尔量。 */
sealed interface UpdateState {
    data object Idle : UpdateState

    data class Checking(val auto: Boolean) : UpdateState

    /** 已经是最新版本（或远端没有更新的正式版）。 */
    data class UpToDate(val auto: Boolean) : UpdateState

    data class Available(val info: ReleaseInfo, val auto: Boolean) : UpdateState

    /** 缺"允许安装未知应用"权限，已把用户送到授权页。 */
    data class NeedsPermission(val info: ReleaseInfo) : UpdateState

    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState

    /** 已交给系统安装器。 */
    data class Installing(val info: ReleaseInfo) : UpdateState

    /** 下载/校验/安装失败时带上 [info]，这样"重试"不用重新查一次版本。 */
    data class Failed(
        val message: String,
        val auto: Boolean,
        val info: ReleaseInfo? = null,
    ) : UpdateState
}

/** 当前状态对应的版本信息（失败且没有版本时为空）。 */
val UpdateState.releaseInfo: ReleaseInfo?
    get() = when (this) {
        is UpdateState.Available -> info
        is UpdateState.NeedsPermission -> info
        is UpdateState.Downloading -> info
        is UpdateState.Installing -> info
        is UpdateState.Failed -> info
        UpdateState.Idle, is UpdateState.Checking, is UpdateState.UpToDate -> null
    }

/**
 * 应用内在线更新的编排：检查 → 下载 → 校验 → 安装。
 *
 * 与界面生命周期无关（放在 [com.echotimer.AppContainer] 里），
 * 这样旋转屏幕或切页都不会打断下载。
 */
class UpdateManager(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val appContext: Context = context.applicationContext
    private val client = UpdateClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    /** 当前版本（展示用，例如 1.6.0；debug 包会带 -debug 后缀）。 */
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /** 当前版本（比较用，后缀会被忽略）。 */
    val currentVersion: AppVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)

    /** 启动时调用：满足"开启自动检查 + 距上次超过一天"才真正发请求。 */
    fun checkIfDue() {
        val settings = settingsRepository.settings.value
        val due = UpdatePolicy.shouldAutoCheck(
            enabled = settings.autoCheckUpdate,
            lastCheckAt = settings.lastUpdateCheckAt,
            nowMs = System.currentTimeMillis(),
        )
        if (due) check(auto = true)
    }

    fun check(auto: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking(auto)
            val result = withContext(Dispatchers.IO) { client.fetchLatest() }
            when (result) {
                is UpdateClient.Result.Error -> {
                    _state.value = UpdateState.Failed(result.message, auto)
                }

                is UpdateClient.Result.Found -> {
                    val info = result.info
                    // 只在成功时记时间：失败下次启动还能重试，成功则一天内不再打扰。
                    settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
                    _state.value = when {
                        !UpdatePolicy.shouldOffer(info.version, currentVersion) ->
                            UpdateState.UpToDate(auto)

                        info.apk == null ->
                            UpdateState.Failed("v${info.version} 没有提供安装包", auto)

                        else -> UpdateState.Available(info, auto)
                    }
                }
            }
        }
    }

    /** 下载并安装；没有安装权限时先把用户送到授权页。 */
    fun download() {
        val info = state.value.releaseInfo ?: return
        if (job?.isActive == true) return
        if (!ApkInstaller.canInstall(appContext)) {
            _state.value = UpdateState.NeedsPermission(info)
            ApkInstaller.requestInstallPermission(appContext)
            return
        }

        job = scope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            val result = ApkInstaller.download(appContext, info) { progress ->
                _state.value = UpdateState.Downloading(info, progress)
            }
            when (result) {
                is ApkInstaller.DownloadResult.Failed ->
                    _state.value = UpdateState.Failed(result.message, auto = false, info = info)

                is ApkInstaller.DownloadResult.Ready -> {
                    _state.value = if (ApkInstaller.install(appContext, result.file)) {
                        UpdateState.Installing(info)
                    } else {
                        UpdateState.Failed("没有找到系统安装器，请到发布页手动下载", auto = false, info = info)
                    }
                }
            }
        }
    }

    /** 重试：有版本信息就重新下载，否则重新检查一次。 */
    fun retry() {
        if (state.value.releaseInfo != null) download() else check(auto = false)
    }

    /**
     * 用户点了「稍后」：记住这个版本，自动检查不再弹窗。
     * 状态保持 [UpdateState.Available]，设置页仍会显示"有新版本"，随时可以手动更新。
     */
    fun dismiss(info: ReleaseInfo) {
        settingsRepository.update { it.copy(skippedUpdateVersion = info.version.toString()) }
    }

    /** 打开发布页：用户想看完整更新说明或手动下载时用。 */
    fun openReleasesPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateConfig.RELEASES_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 没有浏览器时什么都不做，界面上的其它操作仍然可用。
        }
    }
}
