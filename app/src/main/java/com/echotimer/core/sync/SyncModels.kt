package com.echotimer.core.sync

/** 同步文件里的一条榜单（只带名字，本地 id 不跨设备复用）。 */
data class SyncBoard(val name: String, val createdAt: Long)

/** 同步文件里的一条成绩：以「榜单名 + 玩家名」为身份。 */
data class SyncEntry(
    val board: String,
    val player: String,
    val bestMs: Long,
    val updatedAt: Long,
)

/** 一次同步涉及的全部数据。 */
data class SyncSnapshot(
    val boards: List<SyncBoard> = emptyList(),
    val entries: List<SyncEntry> = emptyList(),
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = boards.isEmpty() && entries.isEmpty()
}
