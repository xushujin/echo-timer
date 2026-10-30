package com.echotimer.core.leaderboard

/** 挑战中可以被超越的一个对手。 */
data class OvertakeTarget(val player: String, val bestMs: Long)

/**
 * 挑战里的「超越提示」判定。
 *
 * 榜单按成绩降序（数字越大越好），所以随着计时增长，会从**最小的成绩**开始被逐个超过。
 * 这里把对手按成绩升序排好，用一个游标记住已经超过到哪一位，保证三件事：
 *
 * - 每个对手只提示一次（游标只前进，不回退）；
 * - 一次跳跃（界面卡顿、切后台再回来）跨过好几个人时，把中间的按顺序全部补上，不丢；
 * - 只认**严格超过**，与仓库「只有更大才更新」的规则完全一致（打平不算超越）。
 *
 * 自己的旧成绩不参与：自己刷新自己的记录不该弹「超越自己」。
 */
class OvertakeTracker(
    entries: List<LeaderboardEntry>,
    selfName: String,
) {

    private val rivals: List<OvertakeTarget> = entries
        .asSequence()
        .filter { it.player != selfName }
        .map { OvertakeTarget(player = it.player, bestMs = it.bestMs) }
        // 稳定排序：成绩相同时保持榜单原顺序，提示顺序才是可预期的。
        .sortedBy { it.bestMs }
        .toList()

    private var next = 0

    /** 榜单里有几个可超越的对手（不含自己）。 */
    val total: Int get() = rivals.size

    /** 已经超过了几个。 */
    val passedCount: Int get() = next

    fun reset() {
        next = 0
    }

    /** 返回本次新超过的对手（按成绩升序）；没有新超越时返回空列表。 */
    fun poll(elapsedMs: Long): List<OvertakeTarget> {
        if (next >= rivals.size) return emptyList()
        val newly = ArrayList<OvertakeTarget>(2)
        while (next < rivals.size && elapsedMs > rivals[next].bestMs) {
            newly.add(rivals[next])
            next++
        }
        return newly
    }
}
