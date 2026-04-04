package com.openkiosk.domain.model

data class PlaylistItem(
    val id: Long = 0,
    val url: String,
    val durationSeconds: Int = 30,
    val position: Int = 0
)
