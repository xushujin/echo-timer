package com.echotimer.core.sync

/**
 * 极简 JSON 工具。
 *
 * 为什么自己写：Android 的 `org.json` 在 JVM 单测里是空实现（调用即抛异常），
 * 而同步文件与发布信息的解析必须能被单测覆盖——它们一旦写错，网页端或更新检查就会失效。
 * 这里只支持用得到的子集，越界输入一律返回 null，不做容错猜测。
 *
 * 两种入口：
 * - [parse] 保留嵌套结构（GitHub Releases 响应里的 `assets` 数组必须能读到）；
 * - [parseFlatObject] 只要扁平对象，嵌套值统一替换成 [NULL]（同步文件用这种）。
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

    /** 解析任意 JSON 值：对象 → Map，数组 → List，其余 → String / Long / Double / Boolean / [NULL]。 */
    fun parse(text: String): Any? = Parser(text).parseValue()

    /** 把对象解析成扁平 Map；嵌套的对象/数组统一记为 [NULL]，不是合法对象时返回 null。 */
    fun parseFlatObject(text: String): Map<String, Any>? {
        val root = parse(text) as? Map<*, *> ?: return null
        val result = LinkedHashMap<String, Any>(root.size)
        for ((key, value) in root) {
            val name = key as? String ?: continue
            result[name] = when (value) {
                null, is Map<*, *>, is List<*> -> NULL
                else -> value
            }
        }
        return result
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseValue(): Any? {
            skipWhitespace()
            return readValue()
        }

        private fun parseObject(): Map<String, Any>? {
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

        private fun parseArray(): List<Any>? {
            skipWhitespace()
            if (!consume('[')) return null
            val result = ArrayList<Any>()
            skipWhitespace()
            if (consume(']')) return result
            while (true) {
                skipWhitespace()
                val value = readValue() ?: return null
                result.add(value)
                skipWhitespace()
                when {
                    consume(',') -> continue
                    consume(']') -> return result
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
                '{' -> parseObject()
                '[' -> parseArray()
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
