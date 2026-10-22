// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.h3nc4.hypergesture.update

// A small JSON reader, since android's org.json is a stub under JVM unit tests and the
// release parser has to run there against a saved API response.
internal object Json {

    class MalformedJson(override val message: String) : Exception(message)

    /** Returns a map for an object, a list for an array and a double for a number. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw reader.error("trailing content")
        return value
    }

    private class Reader(private val text: String) {
        private var index = 0
        private var depth = 0

        fun atEnd() = index >= text.length

        fun error(what: String) = MalformedJson("$what at offset $index")

        fun skipWhitespace() {
            while (!atEnd() && text[index].isWhitespace()) index++
        }

        fun value(): Any? {
            skipWhitespace()
            if (atEnd()) throw error("unexpected end")
            return when (val c = text[index]) {
                '{' -> nested(::obj)
                '[' -> nested(::array)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw error("unexpected '$c'")
            }
        }

        // Each level is a stack frame, so a deep enough document would overflow the stack.
        private fun <T> nested(read: () -> T): T {
            if (++depth > MAX_DEPTH) throw error("nested too deep")
            return read().also { depth-- }
        }

        private fun expect(c: Char) {
            skipWhitespace()
            if (atEnd() || text[index] != c) throw error("expected '$c'")
            index++
        }

        private fun peekIs(c: Char): Boolean {
            skipWhitespace()
            return !atEnd() && text[index] == c
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            if (peekIs('}')) {
                index++
                return result
            }
            do {
                skipWhitespace()
                val key = string()
                expect(':')
                result[key] = value()
            } while (consumeComma())
            expect('}')
            return result
        }

        private fun array(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            if (peekIs(']')) {
                index++
                return result
            }
            do {
                result.add(value())
            } while (consumeComma())
            expect(']')
            return result
        }

        private fun consumeComma(): Boolean {
            if (!peekIs(',')) return false
            index++
            return true
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, index)) throw error("expected $word")
            index += word.length
            return result
        }

        private fun number(): Double {
            val start = index
            while (!atEnd() && text[index] in NUMBER_CHARS) index++
            return text.substring(start, index).toDoubleOrNull() ?: throw error("bad number")
        }

        private fun string(): String {
            if (atEnd() || text[index] != '"') throw error("expected a string")
            index++
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw error("unterminated string")
                val c = text[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escape())
                    else -> out.append(c)
                }
            }
        }

        private fun escape(): Char {
            if (atEnd()) throw error("unterminated escape")
            return when (val c = text[index++]) {
                '"', '\\', '/' -> c
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) throw error("short unicode escape")
                    val code = text.substring(index, index + 4).toIntOrNull(16)
                        ?: throw error("bad unicode escape")
                    index += 4
                    code.toChar()
                }
                else -> throw error("bad escape '\\$c'")
            }
        }

        private companion object {
            const val NUMBER_CHARS = "0123456789+-.eE"
            const val MAX_DEPTH = 32
        }
    }
}
