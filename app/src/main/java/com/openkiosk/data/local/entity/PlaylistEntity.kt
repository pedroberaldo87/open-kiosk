package com.openkiosk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val durationSeconds: Int = 30,
    val position: Int = 0
)
