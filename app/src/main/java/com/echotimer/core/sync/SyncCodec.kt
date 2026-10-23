package com.echotimer.core.sync

/**
 * 同步文件的编解码。
 *
 * 格式是 **NDJSON**（每行一个扁平 JSON 对象），原因：
 * - 手机端只需处理扁平对象，解析器可以做得很小且严格（见 [Json]）；
 * - 网页端 `line.split` + `JSON.parse` 就能读，不需要额外库；
 * - 以后要追加记录时天然是"加一行"，diff 也可读。
 *
 * 行类型：
 * ```
 * {"type":"meta","version":1,"updatedAt":1700000000000}
 * {"type":"board","name":"单杠悬垂","createdAt":1700000000000}
 * {"type":"entry","board":"单杠悬垂","player":"张三","bestMs":63000,"updatedAt":1700000000000}
 * ```
 *
 * 解析对脏数据是"跳过该行"而不是整体失败——仓库里的文件可能被人手改过，
 * 少一条记录好过整份同步失败。
 */
object SyncCodec {

    const val VERSION = 1

    fun encode(snapshot: SyncSnapshot): String {
        val builder = StringBuilder()
        builder.append("{\"type\":\"meta\",\"version\":").append(VERSION)
            .append(",\"updatedAt\":").append(snapshot.updatedAt).append("}\n")
        for (board in snapshot.boards.sortedBy { it.name.lowercase() }) {
            builder.append("{\"type\":\"board\",\"name\":\"")
                .append(Json.escape(board.name))
                .append("\",\"createdAt\":").append(board.createdAt).append("}\n")
        }
        for (entry in snapshot.entries.sortedWith(compareBy({ it.board.lowercase() }, { -it.bestMs }))) {
            builder.append("{\"type\":\"entry\",\"board\":\"")
                .append(Json.escape(entry.board))
                .append("\",\"player\":\"")
                .append(Json.escape(entry.player))
                .append("\",\"bestMs\":").append(entry.bestMs)
                .append(",\"updatedAt\":").append(entry.updatedAt).append("}\n")
        }
        return builder.toString()
    }

    fun decode(text: String): SyncSnapshot {
        val boards = ArrayList<SyncBoard>()
        val entries = ArrayList<SyncEntry>()
        var updatedAt = 0L

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || !line.startsWith("{")) continue
            val fields = Json.parseFlatObject(line) ?: continue
            when (fields.string("type")) {
                "meta" -> updatedAt = maxOf(updatedAt, fields.long("updatedAt") ?: 0L)

                "board" -> {
                    val name = fields.string("name")?.trim().orEmpty()
                    if (name.isNotEmpty()) {
                        boards.add(SyncBoard(name, fields.long("createdAt") ?: 0L))
                    }
                }

                "entry" -> {
                    val board = fields.string("board")?.trim().orEmpty()
                    val player = fields.string("player")?.trim().orEmpty()
                    val bestMs = fields.long("bestMs")
                    if (board.isNotEmpty() && player.isNotEmpty() && bestMs != null && bestMs > 0L) {
                        entries.add(
                            SyncEntry(
                                board = board,
                                player = player,
                                bestMs = bestMs,
                                updatedAt = fields.long("updatedAt") ?: 0L,
                            ),
                        )
                    }
                }
            }
        }
        return SyncSnapshot(boards = boards, entries = entries, updatedAt = updatedAt)
    }

    private fun Map<String, Any>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any>.long(key: String): Long? = when (val value = this[key]) {
        is Long -> value
        is Double -> value.toLong()
        else -> null
    }
}
