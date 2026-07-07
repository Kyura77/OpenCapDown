package com.opencapdown.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenCapDownCoreTest {
    @Test
    fun `factory version is 1 0 0`() {
        assertEquals("1.0.0", OpenCapDownCoreFactory.VERSION)
    }
}
