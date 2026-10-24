package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_logs")
data class SystemLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "VOICE_INPUT", "VOICE_RESPONSE", "COMMAND_EXEC"
    val title: String, // e.g. "User Voice Input", "Zoya Verbal Response", "Command Executed"
    val content: String, // The actual text or command detail
    val timestamp: Long = System.currentTimeMillis()
)
