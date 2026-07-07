package com.opencapdown.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenCapDownCoreTest {
    @Test
    fun `factory version matches current release`() {
        assertEquals("1.0.4", OpenCapDownCoreFactory.VERSION)
    }
}
