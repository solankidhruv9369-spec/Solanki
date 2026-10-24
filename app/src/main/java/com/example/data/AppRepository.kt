package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    // Tasks
    val allTasks: Flow<List<Task>> = appDao.getAllTasks()
    suspend fun insertTask(task: Task): Long = appDao.insertTask(task)
    suspend fun updateTask(task: Task) = appDao.updateTask(task)
    suspend fun deleteTask(task: Task) = appDao.deleteTask(task)
    suspend fun deleteTaskById(taskId: Int) = appDao.deleteTaskById(taskId)
    suspend fun getTaskById(taskId: Int): Task? = appDao.getTaskById(taskId)

    // Meetings
    val allMeetings: Flow<List<Meeting>> = appDao.getAllMeetings()
    suspend fun insertMeeting(meeting: Meeting): Long = appDao.insertMeeting(meeting)
    suspend fun updateMeeting(meeting: Meeting) = appDao.updateMeeting(meeting)
    suspend fun deleteMeeting(meeting: Meeting) = appDao.deleteMeeting(meeting)
    suspend fun deleteMeetingById(meetingId: Int) = appDao.deleteMeetingById(meetingId)
    suspend fun getMeetingById(meetingId: Int): Meeting? = appDao.getMeetingById(meetingId)

    // Chat History
    val allChatMessages: Flow<List<ChatMessage>> = appDao.getAllChatMessages()
    suspend fun insertChatMessage(message: ChatMessage): Long = appDao.insertChatMessage(message)
    suspend fun clearChatHistory() = appDao.clearChatHistory()

    // System Logs
    val allSystemLogs: Flow<List<SystemLog>> = appDao.getAllSystemLogs()
    suspend fun insertSystemLog(log: SystemLog): Long = appDao.insertSystemLog(log)
    suspend fun clearSystemLogs() = appDao.clearSystemLogs()
}
