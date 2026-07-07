package com.opencapdown.core.sources

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RobolectricExtension
import org.robolectric.RuntimeEnvironment

@ExtendWith(RobolectricExtension::class)
class JsSourceLoaderTest {

    @Test
    fun `listAvailable returns files from assets`() {
        val loader = JsSourceLoader(RuntimeEnvironment.getApplication())
        val sources = loader.listAvailable()
        assertTrue(sources.contains("test_source"))
    }

    @Test
    fun `load reads a file from assets`() {
        val loader = JsSourceLoader(RuntimeEnvironment.getApplication())
        val content = loader.load("test_source")
        assertTrue(content.contains("export default"))
        assertTrue(content.contains("Test Source"))
    }

    @Test
    fun `load throws for missing source`() {
        val loader = JsSourceLoader(RuntimeEnvironment.getApplication())
        assertThrows<Exception> {
            loader.load("non_existent_source")
        }
    }
}
