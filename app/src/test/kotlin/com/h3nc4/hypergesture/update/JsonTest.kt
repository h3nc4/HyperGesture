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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    private fun refuses(text: String, reason: String) {
        val error = assertThrows(Json.MalformedJson::class.java) { Json.parse(text) }
        assertTrue("'$text' failed with '${error.message}'", error.message.startsWith(reason))
    }

    @Test
    fun `scalars parse at the top level`() {
        assertEquals(12.0, Json.parse("12"))
        assertEquals(-0.5, Json.parse(" -0.5 "))
        assertEquals(false, Json.parse("false"))
        assertEquals(null, Json.parse("null"))
        assertEquals("", Json.parse("\"\""))
    }

    @Test
    fun `arrays keep their order and may be empty`() {
        assertEquals(emptyList<Any?>(), Json.parse("[]"))
        assertEquals(emptyList<Any?>(), Json.parse("[ ]"))
        assertEquals(listOf("a", 1.0, null, listOf(true)), Json.parse("""[ "a" , 1 , null , [true] ]"""))
    }

    @Test
    fun `objects keep the later of two equal keys`() {
        assertEquals(mapOf("k" to 2.0), Json.parse("""{"k":1,"k":2}"""))
        assertEquals(mapOf("a" to emptyMap<String, Any?>()), Json.parse(""" { "a" : { } } """))
    }

    @Test
    fun `every escape decodes to its character`() {
        assertEquals("\" \\ / \b \u000C \n \r \t é", Json.parse(""""\" \\ \/ \b \f \n \r \t \u00e9""""))
    }

    @Test
    fun `broken documents name what went wrong`() {
        refuses("", "unexpected end")
        refuses("[1,", "unexpected end")
        refuses("[1", "expected ']'")
        refuses("[1 2]", "expected ']'")
        refuses("""{"a"""", "expected ':'")
        refuses("""{"a" 1}""", "expected ':'")
        refuses("""{"a":1""", "expected '}'")
        refuses("""{"a":1,""", "expected a string")
        refuses("{1:2}", "expected a string")
        refuses("tru", "expected true")
        refuses("nul", "expected null")
        refuses("-", "bad number")
        refuses("1e", "bad number")
        refuses("@", "unexpected '@'")
        refuses("1 2", "trailing content")
    }

    @Test
    fun `broken escapes are refused`() {
        refuses("\"\\", "unterminated escape")
        refuses("\"\\x\"", "bad escape '\\x'")
        refuses("\"\\u12\"", "short unicode escape")
        refuses("\"\\uzzzz\"", "bad unicode escape")
    }

    @Test
    fun `nesting is bounded so a hostile answer cannot overflow the stack`() {
        assertTrue(Json.parse("[".repeat(32) + "]".repeat(32)) is List<*>)
        refuses("[".repeat(33) + "]".repeat(33), "nested too deep")
        refuses("{\"a\":".repeat(10_000), "nested too deep")
    }
}
