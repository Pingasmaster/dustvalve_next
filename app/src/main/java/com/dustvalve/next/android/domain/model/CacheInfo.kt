package com.dustvalve.next.android.domain.model

data class CacheInfo(
    val totalSizeBytes: Long,
    val limitBytes: Long,
    val audioSizeBytes: Long,
    val imageSizeBytes: Long,
    val downloadSizeBytes: Long,
    val usagePercent: Float,
)
