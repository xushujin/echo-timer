package com.echotimer.data

/** 一次已保存的计时记录。 */
data class TimingSession(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val totalMs: Long,
    val note: String,
    val laps: List<LapRecord>,
)

/** 记录内的一次计次。 */
data class LapRecord(
    val index: Int,
    val lapMs: Long,
    val totalMs: Long,
)

/** 待写入的计时记录（尚未分配 id）。 */
data class SessionDraft(
    val startedAt: Long,
    val endedAt: Long,
    val totalMs: Long,
    val note: String = "",
    val laps: List<LapRecord> = emptyList(),
)
