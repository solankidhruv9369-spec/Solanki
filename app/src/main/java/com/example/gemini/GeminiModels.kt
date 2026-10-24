package com.example.gemini

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val parts: List<PartResponse>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponse
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

// --- Action Parsing Data Classes ---

@JsonClass(generateAdapter = true)
data class AssistantMeetingAction(
    val title: String,
    val description: String = "",
    val startTime: Long, // epoch millis
    val endTime: Long,   // epoch millis
    val location: String = "Online"
)

@JsonClass(generateAdapter = true)
data class AssistantTaskAction(
    val title: String,
    val description: String = "",
    val dueDate: Long, // epoch millis
    val priority: String = "Medium", // High, Medium, Low
    val category: String = "General"  // Work, Personal, etc.
)

@JsonClass(generateAdapter = true)
data class AssistantOpenAppAction(
    val appName: String // "youtube", "whatsapp", "settings", "calendar", "maps", "gmail", "browser", "call", "builder"
)

@JsonClass(generateAdapter = true)
data class AssistantDialNumberAction(
    val phoneNumber: String
)

@JsonClass(generateAdapter = true)
data class AssistantOpenWebsiteAction(
    val url: String
)

@JsonClass(generateAdapter = true)
data class AssistantBuildAppAction(
    val appName: String,
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class AssistantAction(
    val type: String, // "SCHEDULE_MEETING", "CREATE_TASK", "OPEN_APP", "DIAL_NUMBER", "OPEN_WEBSITE", "BUILD_APP"
    val meeting: AssistantMeetingAction? = null,
    val task: AssistantTaskAction? = null,
    val openApp: AssistantOpenAppAction? = null,
    val dialNumber: AssistantDialNumberAction? = null,
    val openWebsite: AssistantOpenWebsiteAction? = null,
    val buildApp: AssistantBuildAppAction? = null
)

@JsonClass(generateAdapter = true)
data class AssistantResult(
    val chatResponse: String,
    val actions: List<AssistantAction>? = emptyList()
)
