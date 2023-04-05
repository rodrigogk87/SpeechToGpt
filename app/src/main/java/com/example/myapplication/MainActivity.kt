package com.example.myapplication;

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var responseTextView: TextView
    private lateinit var speechButton: Button
    private lateinit var tts: TextToSpeech
    private val speechToTextResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            responseTextView.text = "You said: $spokenText"
            if (spokenText != null) {
                sendToChatGPT(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        responseTextView = findViewById(R.id.responseTextView)
        speechButton = findViewById(R.id.speechButton)
        speechButton.setOnClickListener {
            startSpeechToText()
        }

    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        speechToTextResult.launch(intent)
    }

    private fun sendToChatGPT(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val mediaType = "application/json".toMediaTypeOrNull()
            val body = ("{\"prompt\": \"$text, respondeme usando un maximo de 30 palabras\",\n" +
                    "\"max_tokens\": 100}").toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/engines/text-davinci-003/completions")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer APIKEY")
                .build()
            try {
                val response = client.newCall(request).execute()
                val responseText = response.body?.string()
                responseText?.let { respondWithSpeech(it) }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun respondWithSpeech(responseString: String) {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("es", "AR")
                val responseJson = JSONObject(responseString)
                val choicesArray = responseJson.getJSONArray("choices")
                if (choicesArray.length() > 0) {
                    val choiceObject = choicesArray.getJSONObject(0)
                    val spokenText = choiceObject.getString("text")
                    tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String) {
                tts.shutdown()
            }

            override fun onError(utteranceId: String) {}
            override fun onStart(utteranceId: String) {}
        })
    }
}