package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val startTime: Long, // milliseconds
    val endTime: Long,   // milliseconds
    val location: String = "Online"
)
