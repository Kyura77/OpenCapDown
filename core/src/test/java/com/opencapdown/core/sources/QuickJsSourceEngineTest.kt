package com.opencapdown.core.sources

import app.cash.quickjs.QuickJs
import com.opencapdown.core.common.Logger
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QuickJsSourceEngineTest {

    private val httpBridge = mockk<HttpBridge>(relaxed = true)
    private val htmlParserBridge = mockk<HtmlParserBridge>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `wrapSourceCode strips export default and assigns variable`() {
        val code = """
            export default {
                id: "test",
                name: "Test",
                lang: "en",
                search: function(q) { return []; }
            }
        """.trimIndent()
        val wrapped = QuickJsSourceEngine.wrapSourceCode(code)
        assertTrue(wrapped.startsWith("var __source_module__ = {"))
        assertTrue(wrapped.contains("search: function(q) { return []; }"))
    }

    @Test
    fun `wrapSourceCode handles export default with leading whitespace`() {
        val code = "  export default   { id: 'test' }"
        val wrapped = QuickJsSourceEngine.wrapSourceCode(code)
        assertEquals("var __source_module__ = { id: 'test' }", wrapped)
    }

    @Test
    fun `wrapSourceCode handles code without export default`() {
        val code = "var x = 1;"
        val wrapped = QuickJsSourceEngine.wrapSourceCode(code)
        assertEquals("var __source_module__ = var x = 1;", wrapped)
    }

    @Test
    fun `serializeForJs handles strings`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf("hello"))
        assertEquals("\"hello\"", result)
    }

    @Test
    fun `serializeForJs handles strings with escaping`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf("hello \"world\" \\ test"))
        assertEquals("\"hello \\\"world\\\" \\\\ test\"", result)
    }

    @Test
    fun `serializeForJs handles numbers`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf(42))
        assertEquals("42", result)
    }

    @Test
    fun `serializeForJs handles booleans`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf(true))
        assertEquals("true", result)
    }

    @Test
    fun `serializeForJs handles null`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf<Any?>(null))
        assertEquals("null", result)
    }

    @Test
    fun `serializeForJs handles multiple args`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf("a", 1, true))
        assertEquals("\"a\", 1, true", result)
    }

    @Test
    fun `engine evaluates synchronous code and invokes function`() {
        if (!isQuickJsAvailable()) return

        val engine = QuickJsSourceEngine(httpBridge, htmlParserBridge, logger)
        engine.use {
            it.loadSource("""
                var __source_module__ = {
                    greet: function(name) {
                        return "Hello, " + name + "!";
                    }
                }
            """.trimIndent())
            val result = it.invoke<String>("greet", "World")
            assertEquals("Hello, World!", result)
        }
    }

    @Test
    fun `engine evaluates code and returns number`() {
        if (!isQuickJsAvailable()) return

        val engine = QuickJsSourceEngine(httpBridge, htmlParserBridge, logger)
        engine.use {
            it.loadSource("""
                var __source_module__ = {
                    add: function(a, b) {
                        return a + b;
                    }
                }
            """.trimIndent())
            val result = it.invoke<Int>("add", 3, 4)
            assertEquals(7, result)
        }
    }

    @Test
    fun `engine evaluates code and returns object with fields`() {
        if (!isQuickJsAvailable()) return

        val engine = QuickJsSourceEngine(httpBridge, htmlParserBridge, logger)
        engine.use {
            it.loadSource("""
                var __source_module__ = {
                    getInfo: function() {
                        return { title: "Test", count: 42 };
                    }
                }
            """.trimIndent())
            val result = it.invoke<Map<String, Any?>>("getInfo")
            @Suppress("UNCHECKED_CAST")
            val map = result as Map<String, Any?>
            assertEquals("Test", map["title"])
            assertEquals(42, (map["count"] as Number).toInt())
        }
    }

    @Test
    fun `invoke missing function throws`() {
        if (!isQuickJsAvailable()) return

        val engine = QuickJsSourceEngine(httpBridge, htmlParserBridge, logger)
        engine.use {
            it.loadSource("var __source_module__ = { existing: function() { return 1; } }")
            assertThrows<Exception> {
                engine.invoke<Any>("nonExistent")
            }
        }
    }

    private fun isQuickJsAvailable(): Boolean {
        return try {
            QuickJs.create().close()
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
