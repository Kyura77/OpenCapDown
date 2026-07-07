package com.opencapdown.core.downloads

data class DownloadConstraints(
    val maxParallelPages: Int = 3,
    val minDelayMs: Long = 200,
    val maxDelayMs: Long = 800
)
