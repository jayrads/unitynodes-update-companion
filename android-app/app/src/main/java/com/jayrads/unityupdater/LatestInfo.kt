package com.jayrads.unityupdater

data class LatestInfo(
    val versionName: String,
    val apkUrl: String,
    val fileName: String,
    val publishedAt: String,
    val sizeBytes: Long,
    val sha256: String
)
