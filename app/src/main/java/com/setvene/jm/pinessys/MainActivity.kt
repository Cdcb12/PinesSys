package com.setvene.jm.pinessys

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.controllers.MessageHistoryManager
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.EventStreamChunk
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import com.setvene.jm.pinessys.service.OkHttpServiceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var inputMessage: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messages: MutableList<ChatMessage> = mutableListOf()
    private lateinit var service: OkHttpServiceResponse
    private val messagesJsonArray = MessageHistoryManager.getMessagesJsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        service = OkHttpServiceResponse(this)

        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter(messages)
        recyclerView.adapter = chatAdapter

        sendButton.setOnClickListener {
            sendMessage()
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

            // Add the user's message to the message history
            MessageHistoryManager.addMessage("user", messageText)

            simulateBotResponse()
        }
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
                put("model", "llama-3.1-8b-12k")
                put("temperature", 0.4)
                put("max_tokens", 1024)
                put("stream", true)
                put("top_p", 1)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)



            val callback = object : OkHttpServiceResponse.StreamCallback {
                private val accumulatedContent = StringBuilder()

                override fun onStartStream() {
                    runOnUiThread {
                        messages[position].updateText("  ")
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }

                override fun onChunkReceived(chunk: EventStreamChunk) {
                    runOnUiThread {
                        accumulatedContent.append(chunk.choices.delta.content)
                        messages[position].updateText(accumulatedContent.toString())
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }

                override fun onFinallyStream() {
                    runOnUiThread {
                        val finalContent = accumulatedContent.toString()
                        MessageHistoryManager.addMessage("assistant", finalContent)
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }

                override fun onError(responseCode: Number, message: Any) {
                    Log.d("Eror", message.toString())
                    runOnUiThread {
                        messages[position].updateText("Error: $message")
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
            service.makeStreamRequest(
                requestBody,
                "http://192.168.0.175:3000/v1/chat/completions",
                callback
            )
        }
    }
}