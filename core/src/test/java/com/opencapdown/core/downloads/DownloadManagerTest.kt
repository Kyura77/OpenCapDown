package com.opencapdown.core.downloads

import com.opencapdown.core.domain.models.DownloadJob
import com.opencapdown.core.domain.models.DownloadStatus
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File

class DownloadManagerTest {
    @Test
    fun `enqueue creates queued job`() = runTest {
        val repository = mockk<DownloadRepository>(relaxed = true)
        val manager = DownloadManagerImpl(
            imageDownloader = mockk(relaxed = true),
            repository = repository,
            cacheDir = File("/tmp")
        )
        manager.enqueueChapter("m1", "c1")
        coVerify { repository.createJob("c1") }
    }
}
