package com.alecu.balancetycoon.core.save

/**
 * Lector y escritor JSON mínimo, suficiente para la partida guardada.
 * Los valores se leen como Map<String, Any?>, List<Any?>, String, Long, Double, Boolean o null.
 */
object Json {
    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    private fun writeTo(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long -> sb.append(value.toString())
            is Double -> {
                require(!value.isNaN() && !value.isInfinite()) { "Número no válido en JSON: $value" }
                val s = value.toString()
                sb.append(if (s.contains('.') || s.contains('E') || s.contains('e')) s else "$s.0")
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    writeTo(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeTo(sb, v)
                }
                sb.append(']')
            }
            is LongArray -> writeTo(sb, value.toList())
            else -> throw IllegalArgumentException("Tipo no admitido en JSON: ${value::class}")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipWs()
        if (p.pos != text.length) throw IllegalArgumentException("Texto sobrante en JSON en la posición ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (pos >= s.length) throw IllegalArgumentException("JSON incompleto")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Carácter inesperado '$c' en $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw IllegalArgumentException("Se esperaba $word en $pos")
            pos += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++
            skipWs()
            if (s[pos] == '}') { pos++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                if (s[pos] != ':') throw IllegalArgumentException("Se esperaba ':' en $pos")
                pos++
                m[k] = value()
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return m }
                    else -> throw IllegalArgumentException("Se esperaba ',' o '}' en $pos")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++
            skipWs()
            if (s[pos] == ']') { pos++; return l }
            while (true) {
                l.add(value())
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return l }
                    else -> throw IllegalArgumentException("Se esperaba ',' o ']' en $pos")
                }
            }
        }

        private fun str(): String {
            if (s[pos] != '"') throw IllegalArgumentException("Se esperaba una cadena en $pos")
            pos++
            val sb = StringBuilder()
            while (true) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Escape no válido \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(start, pos)
            return if (t.contains('.') || t.contains('e') || t.contains('E')) t.toDouble() else t.toLong()
        }
    }
}
