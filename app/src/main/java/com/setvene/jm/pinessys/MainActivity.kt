package com.setvene.jm.pinessys

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.components.SetVeneEditText
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
    private lateinit var sendButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messages: MutableList<ChatMessage> = mutableListOf()
    private lateinit var service: OkHttpServiceResponse
    private val messagesJsonArray = MessageHistoryManager.getMessagesJsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        service = OkHttpServiceResponse(this)
        inputMessage = findViewById(R.id.input_message)
        sendButton = findViewById(R.id.btn_send)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter(messages)
        recyclerView.adapter = chatAdapter

        sendButton.setOnClickListener {
            sendMessage()
        }

        val constraintLayout = findViewById<ConstraintLayout>(R.id.constraint_layout)
        val textInputLayout = findViewById<TextInputLayout>(R.id.text_input_layout)
        val buttonLayout = findViewById<LinearLayout>(R.id.btn_layout)

        inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val constraintSet = ConstraintSet()
                constraintSet.clone(constraintLayout)

                if (s != null && s.length >= 20) {
                    // Expand the TextInputLayout to occupy the upper part
                    constraintSet.connect(textInputLayout.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
                    constraintSet.connect(textInputLayout.id, ConstraintSet.BOTTOM, buttonLayout.id, ConstraintSet.TOP)
                    constraintSet.connect(textInputLayout.id, ConstraintSet.END, constraintLayout.id, ConstraintSet.END)
                    constraintSet.connect(textInputLayout.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)

                    // Move the button to the bottom
                    constraintSet.connect(buttonLayout.id, ConstraintSet.TOP, textInputLayout.id, ConstraintSet.BOTTOM)
                    constraintSet.connect(buttonLayout.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
                } else {
                    // Restore original constraints
                    constraintSet.connect(textInputLayout.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
                    constraintSet.connect(textInputLayout.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
                    constraintSet.connect(textInputLayout.id, ConstraintSet.END, buttonLayout.id, ConstraintSet.START)
                    constraintSet.connect(textInputLayout.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)

                    constraintSet.connect(buttonLayout.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
                    constraintSet.connect(buttonLayout.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
                }

                constraintSet.applyTo(constraintLayout)
            }
        })

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