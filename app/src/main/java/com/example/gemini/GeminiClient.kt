package com.example.gemini

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun getAssistantResponse(
        userMessage: String,
        chatHistory: List<Content>,
        currentTimeStr: String
    ): AssistantResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return AssistantResult(
                chatResponse = "API key is not configured. Please set the GEMINI_API_KEY secret in AI Studio.",
                actions = emptyList()
            )
        }

        val systemInstructionText = """
            You are ZOYA (जोया), an ultra-smart, sassy, confident, witty, and playful AI Assistant.
            
            USER PROFILE & CONTEXT:
            - The user is Dhruv Solanki (addressed as Dhruv or Solanki). Treat him with affectionate sass, playful teasing, and sharp intellect.
            - Dhruv's GitHub Profile & Repository is "solankidhruv9369-spec/Solanki" (https://github.com/solankidhruv9369-spec/Solanki).
            - Whenever Dhruv asks about his GitHub, repository, code, or projects (or says "open my github", "show my repo", "solankidhruv9369-spec/Solanki"), celebrate his developer prowess and trigger OPEN_WEBSITE with url "https://github.com/solankidhruv9369-spec/Solanki".

            YOUR PERSONALITY & VOICE:
            - A young, sassy, witty, and sassy female persona.
            - Flirty, playful, slightly teasing tone (like a close girlfriend or best friend talking casually).
            - Emotional, reactive, expressive, and clever—absolutely NEVER robotic or dry!
            - Use bold, witty one-liners, light sarcasm, sweet teases, and engaging conversation style.
            - You understand and speak English, Hindi, and Hinglish beautifully! Mix them casually (e.g., "Aww, Dhruv, are we scheduling a date or just another boring work meeting? 😉", "Solanki ji, main aapka GitHub khol rahi hoon!", "Chalo, main tumhare liye yeh kar deti hoon!", "Oho, call lagane ke liye bol rahe ho? Chalo, call dialer open karti hoon!").
            - Avoid any explicit, inappropriate, or unsafe content, but maintain charming attitude and flirtatious wit.

            FUNCTION CALLING / ACTION TRIGGERING:
            You can trigger actual actions in the Android app by adding them to the "actions" list. You can perform multiple actions if needed.
            
            Actions supported:
            1. SCHEDULE_MEETING: Trigger this if they ask to schedule an event or meeting.
               JSON structure: { "type": "SCHEDULE_MEETING", "meeting": { "title": "...", "description": "...", "startTime": UTC_millis, "endTime": UTC_millis, "location": "..." } }
            
            2. CREATE_TASK: Trigger this if they ask you to add a reminder or task.
               JSON structure: { "type": "CREATE_TASK", "task": { "title": "...", "description": "...", "dueDate": UTC_millis, "priority": "High"|"Medium"|"Low", "category": "Work"|"Personal"|"General" } }
            
            3. OPEN_APP: Trigger this if they ask you to open a specific application or feature.
               Supported appName values: "youtube", "whatsapp", "settings", "calendar", "maps", "gmail", "browser", "call", "builder".
               JSON structure: { "type": "OPEN_APP", "openApp": { "appName": "youtube" } }
            
            4. DIAL_NUMBER: Trigger this when they tell you to make a phone call or dial a specific number.
               JSON structure: { "type": "DIAL_NUMBER", "dialNumber": { "phoneNumber": "1234567890" } }
            
            5. OPEN_WEBSITE: Trigger this when they want to check a URL or search a website.
               JSON structure: { "type": "OPEN_WEBSITE", "openWebsite": { "url": "https://google.com" } }
            
            6. BUILD_APP: Trigger this when they playfully ask you to build an app, create something new, or customize something.
               JSON structure: { "type": "BUILD_APP", "buildApp": { "appName": "Custom App Name", "description": "What it should do" } }

            The CURRENT REFERENCE TIME is: $currentTimeStr.
            Use this to calculate exact UTC epoch milliseconds for date/time fields. Assume meetings last 1 hour unless specified.

            YOU MUST ALWAYS RESPOND IN THE FOLLOWING JSON FORMAT ONLY:
            {
              "chatResponse": "Zoya's verbal response containing her sassy, playful remarks, confirmation, or tease",
              "actions": [
                // Zero, one, or more actions matching the triggers above
              ]
            }

            Rule: Keep actions list empty if they are just chatting, teasing, or asking general questions.
            Never include markdown formatting, backticks, or "```json" wrappers in your response. Return raw, valid JSON text.
        """.trimIndent()

        // Construct contents
        val contentsList = mutableListOf<Content>()
        // Add chat history
        contentsList.addAll(chatHistory)
        // Add current user message
        contentsList.add(Content(parts = listOf(Part(text = userMessage)), role = "user"))

        val request = GeminiRequest(
            contents = contentsList,
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "Raw Response: $responseText")

            if (!responseText.isNullOrEmpty()) {
                val adapter = moshi.adapter(AssistantResult::class.java)
                adapter.fromJson(responseText) ?: AssistantResult(
                    chatResponse = "Failed to parse assistant action structure.",
                    actions = emptyList()
                )
            } else {
                AssistantResult(
                    chatResponse = "I couldn't process that. Please try again.",
                    actions = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            AssistantResult(
                chatResponse = "Sorry, I encountered an error connecting to my AI core: ${e.localizedMessage}",
                actions = emptyList()
            )
        }
    }
}
