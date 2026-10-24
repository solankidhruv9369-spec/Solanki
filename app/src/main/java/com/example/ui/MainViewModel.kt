package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.ChatMessage
import com.example.data.Meeting
import com.example.data.Task
import com.example.data.SystemLog
import com.example.gemini.Content
import com.example.gemini.GeminiClient
import com.example.gemini.Part
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(private val repository: AppRepository) : ViewModel() {

    // Action Flow for the activity or UI to consume (e.g. Speech, opening apps, dialing numbers, custom simulations)
    private val _assistantActionFlow = MutableSharedFlow<com.example.gemini.AssistantAction>()
    val assistantActionFlow: SharedFlow<com.example.gemini.AssistantAction> = _assistantActionFlow.asSharedFlow()

    // Lists of meetings, tasks, and messages
    val meetings: StateFlow<List<Meeting>> = repository.allMeetings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessage>> = repository.allChatMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val systemLogs: StateFlow<List<SystemLog>> = repository.allSystemLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI States
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _isHandsFreeMode = MutableStateFlow(false)
    val isHandsFreeMode: StateFlow<Boolean> = _isHandsFreeMode.asStateFlow()

    private val _voiceState = MutableStateFlow("IDLE") // "IDLE", "LISTENING", "THINKING", "SPEAKING"
    val voiceState: StateFlow<String> = _voiceState.asStateFlow()

    fun updateAmplitude(value: Float) {
        _audioAmplitude.value = value
    }

    fun updateVoiceState(state: String) {
        _voiceState.value = state
    }

    fun toggleHandsFreeMode() {
        val newValue = !_isHandsFreeMode.value
        _isHandsFreeMode.value = newValue
        viewModelScope.launch {
            repository.insertSystemLog(
                SystemLog(
                    type = "COMMAND_EXEC",
                    title = if (newValue) "Hands-Free Enabled" else "Hands-Free Disabled",
                    content = if (newValue) "Hands-free continuous voice commands are now active. Zoya is listening continuously!" else "Hands-free voice commands deactivated."
                )
            )
        }
    }

    fun selectDate(millis: Long) {
        _selectedDate.value = millis
    }

    // --- Task Actions ---
    fun addTask(title: String, description: String, dueDate: Long, priority: String, category: String) {
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    priority = priority,
                    category = category
                )
            )
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun toggleTaskCompleted(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // --- Meeting Actions ---
    fun addMeeting(title: String, description: String, startTime: Long, endTime: Long, location: String) {
        viewModelScope.launch {
            repository.insertMeeting(
                Meeting(
                    title = title,
                    description = description,
                    startTime = startTime,
                    endTime = endTime,
                    location = location
                )
            )
        }
    }

    fun updateMeeting(meeting: Meeting) {
        viewModelScope.launch {
            repository.updateMeeting(meeting)
        }
    }

    fun deleteMeeting(meeting: Meeting) {
        viewModelScope.launch {
            repository.deleteMeeting(meeting)
        }
    }

    // --- Chat Actions ---
    fun sendMessage(messageText: String) {
        if (messageText.isBlank()) return
        viewModelScope.launch {
            // 1. Save user message to database
            val userMsg = ChatMessage(sender = "user", message = messageText)
            repository.insertChatMessage(userMsg)

            // Log voice input / chat input
            repository.insertSystemLog(
                SystemLog(
                    type = "VOICE_INPUT",
                    title = "User Voice Input",
                    content = messageText
                )
            )

            _isGenerating.value = true
            _voiceState.value = "THINKING"

            try {
                // 2. Prepare chat history
                // Limit history to last 15 messages to keep payload small
                val currentMessages = chatMessages.value
                val messageHistory = currentMessages.takeLast(15).map { msg ->
                    Content(
                        parts = listOf(Part(text = msg.message)),
                        role = if (msg.sender == "user") "user" else "model"
                    )
                }

                // 3. Get current time for Gemini relative time resolution
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val currentTimeStr = sdf.format(Date())

                // 4. Request from Gemini API
                val result = GeminiClient.getAssistantResponse(messageText, messageHistory, currentTimeStr)

                // 5. Save assistant reply to database
                val assistantMsg = ChatMessage(sender = "assistant", message = result.chatResponse)
                repository.insertChatMessage(assistantMsg)

                // Log Zoya verbal response
                repository.insertSystemLog(
                    SystemLog(
                        type = "VOICE_RESPONSE",
                        title = "Zoya Verbal Response",
                        content = result.chatResponse
                    )
                )

                // 6. Execute scheduled actions
                result.actions?.forEach { action ->
                    when (action.type) {
                        "SCHEDULE_MEETING" -> {
                            action.meeting?.let { m ->
                                repository.insertMeeting(
                                    Meeting(
                                        title = m.title,
                                        description = m.description,
                                        startTime = m.startTime,
                                        endTime = m.endTime,
                                        location = m.location
                                    )
                                )
                                // Log action execution
                                repository.insertSystemLog(
                                    SystemLog(
                                        type = "COMMAND_EXEC",
                                        title = "Command: Schedule Meeting",
                                        content = "Successfully scheduled meeting: '${m.title}' at ${m.location}"
                                    )
                                )
                            }
                        }
                        "CREATE_TASK" -> {
                            action.task?.let { t ->
                                repository.insertTask(
                                    Task(
                                        title = t.title,
                                        description = t.description,
                                        dueDate = t.dueDate,
                                        priority = t.priority,
                                        category = t.category
                                    )
                                )
                                // Log action execution
                                repository.insertSystemLog(
                                    SystemLog(
                                        type = "COMMAND_EXEC",
                                        title = "Command: Create Task",
                                        content = "Successfully created task: '${t.title}' with ${t.priority} priority"
                                    )
                                )
                            }
                        }
                        "OPEN_APP" -> {
                            action.openApp?.let { app ->
                                _assistantActionFlow.emit(action)
                                // Log action execution
                                repository.insertSystemLog(
                                    SystemLog(
                                        type = "COMMAND_EXEC",
                                        title = "Command: Open App",
                                        content = "Triggered Android launch intent for app: '${app.appName}'"
                                    )
                                )
                            }
                        }
                        "DIAL_NUMBER" -> {
                            action.dialNumber?.let { dial ->
                                _assistantActionFlow.emit(action)
                                // Log action execution
                                repository.insertSystemLog(
                                    SystemLog(
                                        type = "COMMAND_EXEC",
                                        title = "Command: Dial Number",
                                        content = "Initiated phone dialer for ${dial.phoneNumber}"
                                    )
                                )
                            }
                        }
                        "OPEN_WEBSITE" -> {
                            action.openWebsite?.let { web ->
                                _assistantActionFlow.emit(action)
                                // Log action execution
                                repository.insertSystemLog(
                                    SystemLog(
                                        type = "COMMAND_EXEC",
                                        title = "Command: Open Website",
                                        content = "Opened external browser link to: ${web.url}"
                                    )
                                )
                            }
                        }
                        "BUILD_APP" -> {
                            action.buildApp?.let { build ->
                                _assistantActionFlow.emit(action)
                                // Log action execution
                                repository.insertSystemLog(
                                    SystemLog(
                                        type = "COMMAND_EXEC",
                                        title = "Command: Build App",
                                        content = "Synthesizing virtual app compiler playground for: '${build.appName}'"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Sorry, I had trouble processing that request: ${e.localizedMessage}"
                repository.insertChatMessage(
                    ChatMessage(
                        sender = "assistant",
                        message = errorMsg
                    )
                )
                // Log failed request
                repository.insertSystemLog(
                    SystemLog(
                        type = "COMMAND_EXEC",
                        title = "Command Execution Error",
                        content = "Failure: ${e.localizedMessage}"
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            repository.insertChatMessage(
                ChatMessage(
                    sender = "assistant",
                    message = "Aww, missed my old texts? 😉 History cleared, sweetheart! Chalo, start fresh. What's on your mind now?"
                )
            )
            // Log chat reset
            repository.insertSystemLog(
                SystemLog(
                    type = "COMMAND_EXEC",
                    title = "System: Clear History",
                    content = "User reset conversation thread and cleared all log/chat history."
                )
            )
        }
    }

    fun clearSystemLogs() {
        viewModelScope.launch {
            repository.clearSystemLogs()
        }
    }

    // Initialize with a welcome message if chat is empty
    init {
        viewModelScope.launch {
            repository.allChatMessages.collect { list ->
                if (list.isEmpty()) {
                    repository.insertChatMessage(
                        ChatMessage(
                            sender = "assistant",
                            message = "Hey Dhruv! 😉 Main hoon Zoya, aapki sassy and super-smart AI girlfriend assistant. Ready to manage your meetings, tasks, and GitHub repository (solankidhruv9369-spec/Solanki)! Chalo, tap the mic below or ask me anything, don't be shy! 😘"
                        )
                    )
                }
            }
        }
    }

    // Factory Class for MainViewModel
    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
