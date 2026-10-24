package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Task Queries ---
    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Int)

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?


    // --- Meeting Queries ---
    @Query("SELECT * FROM meetings ORDER BY startTime ASC")
    fun getAllMeetings(): Flow<List<Meeting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: Meeting): Long

    @Update
    suspend fun updateMeeting(meeting: Meeting)

    @Delete
    suspend fun deleteMeeting(meeting: Meeting)

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteMeetingById(meetingId: Int)

    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    suspend fun getMeetingById(meetingId: Int): Meeting?


    // --- Chat Messages Queries ---
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    // --- System Logs Queries ---
    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC")
    fun getAllSystemLogs(): Flow<List<SystemLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSystemLog(log: SystemLog): Long

    @Query("DELETE FROM system_logs")
    suspend fun clearSystemLogs()
}
