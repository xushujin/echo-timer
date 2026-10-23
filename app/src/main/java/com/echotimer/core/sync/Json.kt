package com.echotimer.core.sync

/**
 * 极简 JSON 工具：只处理"扁平对象"（键值对，值是字符串/数字/布尔/null）。
 *
 * 为什么自己写：Android 的 `org.json` 在 JVM 单测里是空实现（调用即抛异常），
 * 而同步文件的编解码必须能被单测覆盖——它一旦写错，网页端就会解析失败。
 * 这里只支持同步文件用得到的子集，越界输入一律返回 null，不做容错猜测。
 */
object Json {

    /** 转义成 JSON 字符串字面量内容（不含首尾引号）。 */
    fun escape(value: String): String {
        val builder = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else -> if (ch < ' ') {
                    builder.append("\\u").append(String.format("%04x", ch.code))
                } else {
                    builder.append(ch)
                }
            }
        }
        return builder.toString()
    }

    /** 把扁平对象解析成 Map；不是合法扁平对象时返回 null。 */
    fun parseFlatObject(text: String): Map<String, Any>? {
        val parser = Parser(text)
        return parser.parseObject()
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseObject(): Map<String, Any>? {
            skipWhitespace()
            if (!consume('{')) return null
            val result = LinkedHashMap<String, Any>()
            skipWhitespace()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                val key = readString() ?: return null
                skipWhitespace()
                if (!consume(':')) return null
                skipWhitespace()
                val value = readValue() ?: return null
                result[key] = value
                skipWhitespace()
                when {
                    consume(',') -> continue
                    consume('}') -> return result
                    else -> return null
                }
            }
        }

        private fun readValue(): Any? {
            if (index >= text.length) return null
            return when (text[index]) {
                '"' -> readString()
                't' -> if (consumeWord("true")) true else null
                'f' -> if (consumeWord("false")) false else null
                'n' -> if (consumeWord("null")) NULL else null
                '{', '[' -> {
                    // 嵌套结构（GitHub API 响应里的 _links / git 等）不关心，跳过即可。
                    if (skipNested()) NULL else null
                }

                else -> readNumber()
            }
        }

        private fun readString(): String? {
            if (!consume('"')) return null
            val builder = StringBuilder()
            while (index < text.length) {
                val ch = text[index++]
                when (ch) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (index >= text.length) return null
                        when (val escaped = text[index++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) return null
                                val hex = text.substring(index, index + 4)
                                val code = hex.toIntOrNull(16) ?: return null
                                builder.append(code.toChar())
                                index += 4
                            }

                            else -> return null
                        }
                    }

                    else -> builder.append(ch)
                }
            }
            return null
        }

        private fun readNumber(): Any? {
            val start = index
            if (index < text.length && (text[index] == '-' || text[index] == '+')) index++
            while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
            if (start == index) return null
            val raw = text.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()
        }

        /** 跳过一个完整的嵌套对象/数组（字符串里的括号不算）。 */
        private fun skipNested(): Boolean {
            var depth = 0
            var inString = false
            var escaped = false
            while (index < text.length) {
                val ch = text[index++]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                    continue
                }
                when (ch) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) return true
                    }
                }
            }
            return false
        }

        private fun consume(ch: Char): Boolean {
            if (index < text.length && text[index] == ch) {
                index++
                return true
            }
            return false
        }

        private fun consumeWord(word: String): Boolean {
            if (text.startsWith(word, index)) {
                index += word.length
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }

    /** `null` 的占位符：Map 的 value 不能是 null，用一个哨兵对象表示。 */
    object NULL
}
