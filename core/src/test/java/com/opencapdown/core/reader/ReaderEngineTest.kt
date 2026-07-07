package com.opencapdown.core.reader

import com.opencapdown.core.database.daos.ChapterDao
import com.opencapdown.core.database.daos.PageDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReaderEngineTest {
    @Test
    fun `markAsRead calls chapterDao updateRead`() = runTest {
        val chapterDao = mockk<ChapterDao>(relaxed = true)
        val engine = ReaderEngineImpl(
            chapterDao = chapterDao,
            pageDao = mockk(relaxed = true),
            pageResolver = mockk(relaxed = true),
            progressTracker = mockk(relaxed = true)
        )
        engine.markAsRead("c1")
        coVerify { chapterDao.updateRead("c1", true) }
    }
}
