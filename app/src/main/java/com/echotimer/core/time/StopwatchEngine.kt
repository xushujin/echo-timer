package com.echotimer.core.time

/** 单调时钟抽象：生产环境用 [SystemClock.elapsedRealtime]，测试用可控假时钟。 */
fun interface MonotonicClock {
    fun nowMs(): Long
}

/** 秒表状态机。 */
enum class StopwatchState {
    /** 未开始 / 已重置。 */
    IDLE,

    /** 正在计时。 */
    RUNNING,

    /** 暂停中，累计时长保留。 */
    PAUSED,

    /** 已结束，累计时长保留用于展示，下次 start 会清零。 */
    FINISHED,
}

/** 单次计次（lap / split）记录。 */
data class LapSplit(
    /** 从 1 开始的序号。 */
    val index: Int,
    /** 与上一次计次的差值（保留用于统计，界面不再展示）。 */
    val lapMs: Long,
    /** 按下计次那一刻的累计总时长——这才是界面上显示的值。 */
    val totalMs: Long,
)

/**
 * 秒表核心：纯 Kotlin 实现，不依赖 Android，可完全在 JVM 上做单元测试。
 *
 * 计时不依赖定时器累加（那样会漂移），而是记录"本次启动时的单调时钟读数"，
 * 每次查询用 `now - startedAt` 现算，因此长时间运行也不会累积误差。
 */
class StopwatchEngine(private val clock: MonotonicClock) {

    var state: StopwatchState = StopwatchState.IDLE
        private set

    private var accumulatedMs: Long = 0L
    private var runningSinceMs: Long = 0L
    private val lapSplits = mutableListOf<LapSplit>()

    /** 当前累计时长（含正在跑的这一段）。 */
    fun elapsedMs(): Long = when (state) {
        StopwatchState.RUNNING -> accumulatedMs + (clock.nowMs() - runningSinceMs).coerceAtLeast(0L)
        else -> accumulatedMs
    }

    /** 开始或继续。IDLE / FINISHED 状态下开始意味着开启新一轮计时。 */
    fun start() {
        if (state == StopwatchState.RUNNING) return
        if (state == StopwatchState.IDLE || state == StopwatchState.FINISHED) {
            accumulatedMs = 0L
            lapSplits.clear()
        }
        runningSinceMs = clock.nowMs()
        state = StopwatchState.RUNNING
    }

    /** 暂停；非运行状态调用无副作用。 */
    fun pause() {
        if (state != StopwatchState.RUNNING) return
        accumulatedMs = elapsedMs()
        state = StopwatchState.PAUSED
    }

    /** 结束计时并返回总时长。 */
    fun finish(): Long {
        if (state == StopwatchState.RUNNING) {
            accumulatedMs = elapsedMs()
        }
        state = StopwatchState.FINISHED
        return accumulatedMs
    }

    /** 清零回到 IDLE，丢弃本次所有计次。 */
    fun reset() {
        accumulatedMs = 0L
        runningSinceMs = 0L
        lapSplits.clear()
        state = StopwatchState.IDLE
    }

    /** 记一次计次；仅在运行/暂停时可记，返回 null 表示当前状态不可计次。 */
    fun addLap(): LapSplit? {
        if (state != StopwatchState.RUNNING && state != StopwatchState.PAUSED) return null
        val total = elapsedMs()
        val previousTotal = lapSplits.lastOrNull()?.totalMs ?: 0L
        val split = LapSplit(
            index = lapSplits.size + 1,
            lapMs = (total - previousTotal).coerceAtLeast(0L),
            totalMs = total,
        )
        lapSplits.add(split)
        return split
    }

    fun laps(): List<LapSplit> = lapSplits.toList()

    fun lapCount(): Int = lapSplits.size
}
