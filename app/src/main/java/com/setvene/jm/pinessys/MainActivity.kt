package com.setvene.jm.pinessys

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.controllers.MessageHistoryManager
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.EventStreamChunk
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import com.setvene.jm.pinessys.service.OkHttpServiceResponse
import com.setvene.jm.pinessys.utils.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var inputMessage: EditText
    private lateinit var multiButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messages: MutableList<ChatMessage> = mutableListOf()
    private lateinit var service: OkHttpServiceResponse

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        service = OkHttpServiceResponse(this)
        inputMessage = findViewById(R.id.input_message)

        multiButton = findViewById(R.id.btn_multi)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter(messages, this)
        recyclerView.adapter = chatAdapter

        initListeners()
    }

    private fun initListeners() {
        inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    multiButton.setIconResource(R.drawable.ic_send)
                } else {
                    multiButton.setIconResource(R.drawable.ic_mic)
                }
            }
        })

        val audioRecorder = AudioRecorder(this)

        multiButton.setOnClickListener {
            if (inputMessage.text.isNotEmpty()) {
                sendMessage()
            }
        }

        var initialX = 0f
        var initialTouchX = 0f
        val cancelThreshold = 30f // Set a threshold of 30px for canceling the audio

        multiButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = view.x
                    initialTouchX = event.rawX
                    if (inputMessage.text.isEmpty()) {
                        audioRecorder.startRecording()
                        multiButton.animate()
                            .scaleX(1.5f)
                            .scaleY(1.5f)
                            .setDuration(500)
                            .start()
                    }
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - initialTouchX
                    if (audioRecorder.isRecording) {
                        if (abs(deltaX) < cancelThreshold) {
                            val audioFilePath = audioRecorder.stopRecording()
                            sendAudio(audioFilePath)
                        } else {
                            audioRecorder.cancelRecording() // Cancel recording if moved too far
                        }

                        multiButton.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(500)
                            .start()
                    }
                    // Reset button position
                    view.x = initialX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    if (abs(deltaX) < cancelThreshold) {
                        view.x = initialX + deltaX
                    }
                    true
                }
                else -> false
            }
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    private fun sendMessage() {
        val messageText = inputMessage.text.toString().trim()

        if (messageText.isNotEmpty()) {
            val newMessage = ChatMessage(SenderType.USER, MessageType.TEXT_IMAGE, messageText)
            messages.add(newMessage)
            chatAdapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)
            inputMessage.text.clear()
            MessageHistoryManager.addMessage("user", messageText)
            simulateBotResponse()
        }
    }


    private fun sendAudio(audioData: String?) {
        if (audioData == null) {
            Log.e("AUDIO_RECORD", "No audio data captured")
            return
        }

        val newMessage = ChatMessage(
            sender = SenderType.USER,
            messageType = MessageType.AUDIO,
            audioPath = audioData
        )
        messages.add(newMessage)
        chatAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
        inputMessage.text.clear()
        simulateBotResponse()
    }

    private fun simulateBotResponse() {
        val thinkingMessage = ChatMessage(SenderType.AI, MessageType.TEXT_IMAGE, "Pensando...")
        messages.add(thinkingMessage)
        val position = messages.size - 1

        chatAdapter.notifyItemInserted(position)
        recyclerView.scrollToPosition(messages.size - 1)

        lifecycleScope.launch(Dispatchers.Main) {

            val jsonObject = JSONObject().apply {
                put("messages", MessageHistoryManager.getMessagesJsonArray())
                put("model", "llama-3.1-70b-8k")
                put("temperature", 0.4)
                put("max_tokens", 1024)
                put("stream", true)
                put("top_p", 1)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)



            val animate = recyclerView.itemAnimator

            val callback = object : OkHttpServiceResponse.StreamCallback {
                private val accumulatedContent = StringBuilder()

                override fun onStartStream() {
                    recyclerView.itemAnimator = null
                }

                override fun onChunkReceived(chunk: EventStreamChunk) {
                    runOnUiThread {
                        accumulatedContent.append(chunk.choices.delta.content)
                        messages[position].updateText(accumulatedContent.toString())
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(position)
                    }
                }

                override fun onFinallyStream() {
                    runOnUiThread {
                        val finalContent = accumulatedContent.toString()
                        MessageHistoryManager.addMessage("assistant", finalContent)
                        chatAdapter.notifyItemChanged(position)
                    }
                    recyclerView.itemAnimator = animate

                }

                override fun onError(responseCode: Number, message: Any) {
                    Log.d("Eror", message.toString())
                    runOnUiThread {
                        messages[position].updateText("Error: $message")
                        MessageHistoryManager.deleteLastValue()
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
            service.makeStreamRequestAI(
                requestBody,
                "http://192.168.0.175:3000/v1/chat/completions",
                callback
            )
        }
    }
}