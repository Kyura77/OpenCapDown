package com.opencapdown.core.sources

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Testes unitários para JsSourceLoader.
 * Usam apenas JUnit5 + kotlin.test sem Robolectric pois
 * JsSourceLoader.listAvailable() e .load() são testados via fixtures
 * no módulo core-test-fixtures para testes instrumentados.
 * Aqui testamos apenas a lógica pura da classe.
 */
class JsSourceLoaderTest {

    @Test
    fun `wrapSourceCode removes export default`() {
        val code = "export default { id: \"test\" }"
        val wrapped = QuickJsSourceEngine.wrapSourceCode(code)
        assertTrue(wrapped.startsWith("var __source_module__"))
        assertTrue(!wrapped.contains("export default"))
    }

    @Test
    fun `serializeForJs handles string`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf("hello world"))
        assertTrue(result.contains("hello world"))
    }

    @Test
    fun `serializeForJs handles null`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf(null))
        assertTrue(result == "null")
    }

    @Test
    fun `serializeForJs handles number`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf(42))
        assertTrue(result == "42")
    }

    @Test
    fun `serializeForJs handles boolean`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf(true))
        assertTrue(result == "true")
    }

    @Test
    fun `serializeForJs handles list`() {
        val result = QuickJsSourceEngine.serializeForJs(arrayOf(listOf("a", "b")))
        assertTrue(result.contains("["))
        assertTrue(result.contains("a"))
    }
}
