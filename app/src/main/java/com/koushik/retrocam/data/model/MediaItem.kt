package com.koushik.retrocam.data.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val isVideo: Boolean = false,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0
)

