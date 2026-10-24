package com.example

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    private val viewModel: MainViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(database.appDao())
        MainViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onSpeak = { text -> speakText(text) },
                        onStartListening = { startSpeechToText() },
                        onStopListening = { stopSpeechToText() }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            // Pitch 1.12f makes it sassy/female/girlfriend tone
            tts?.setPitch(1.15f)
            tts?.setSpeechRate(1.02f)
            isTtsReady = true

            // Set up listener to monitor when TTS finishes speaking
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    viewModel.updateVoiceState("SPEAKING")
                }

                override fun onDone(utteranceId: String?) {
                    viewModel.updateVoiceState("IDLE")
                    if (viewModel.isHandsFreeMode.value) {
                        runOnUiThread {
                            startSpeechToText()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    viewModel.updateVoiceState("IDLE")
                    if (viewModel.isHandsFreeMode.value) {
                        runOnUiThread {
                            startSpeechToText()
                        }
                    }
                }
            })
        } else {
            Log.e("MainActivity", "TTS Initialization failed")
        }
    }

    private fun speakText(text: String) {
        if (isTtsReady) {
            // Strip markdown style like *asterisks*, emoji characters to keep text spoken clean
            var filteredText = text.replace(Regex("[\\*\\_\\#]"), "")
            filteredText = filteredText.replace(Regex("[\\p{So}\\p{Cn}]"), "") // remove emojis
            viewModel.updateVoiceState("SPEAKING")
            tts?.speak(filteredText, TextToSpeech.QUEUE_FLUSH, null, "ZoyaSpeechId")
        }
    }

    private fun startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            Toast.makeText(this, "Microphone permission is required for Voice interactions with Zoya", Toast.LENGTH_LONG).show()
            return
        }
        runOnUiThread {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.d("STT", "Ready for speech")
                                viewModel.updateVoiceState("LISTENING")
                            }

                            override fun onBeginningOfSpeech() {
                                Log.d("STT", "Beginning of speech")
                                viewModel.updateVoiceState("LISTENING")
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                viewModel.updateAmplitude(rmsdB)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                Log.d("STT", "End of speech")
                                viewModel.updateAmplitude(0f)
                                viewModel.updateVoiceState("IDLE")
                            }

                            override fun onError(error: Int) {
                                Log.e("STT", "Speech recognition error: $error")
                                viewModel.updateAmplitude(0f)
                                viewModel.updateVoiceState("IDLE")
                                if (viewModel.isHandsFreeMode.value) {
                                    // If hands-free is enabled, retry after a tiny delay for typical timeout/no-match errors
                                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                                        error == SpeechRecognizer.ERROR_CLIENT ||
                                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                                        window.decorView.postDelayed({
                                            if (viewModel.isHandsFreeMode.value) {
                                                startSpeechToText()
                                            }
                                        }, 1500)
                                    }
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val speechText = matches[0]
                                    Log.d("STT", "Recognized speech: $speechText")
                                    viewModel.updateVoiceState("IDLE")
                                    viewModel.sendMessage(speechText)
                                } else {
                                    viewModel.updateVoiceState("IDLE")
                                    if (viewModel.isHandsFreeMode.value) {
                                        startSpeechToText()
                                    }
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                }

                viewModel.updateVoiceState("LISTENING")
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "STT Error: ${e.message}", Toast.LENGTH_SHORT).show()
                viewModel.updateVoiceState("IDLE")
            }
        }
    }

    private fun stopSpeechToText() {
        runOnUiThread {
            speechRecognizer?.stopListening()
            viewModel.updateAmplitude(0f)
            viewModel.updateVoiceState("IDLE")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
