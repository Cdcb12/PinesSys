package com.setvene.jm.pinessys.service

import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.controllers.MessageHistoryManager
import com.setvene.jm.pinessys.controllers.messages
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.EventStreamChunk
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class AiService(
    private val activity: Activity,
    private val chatAdapter: ChatAdapter,
    private val recyclerView: RecyclerView,
) {
    private var service = OkHttpServiceResponse(activity)


    fun listen(path: String, position: Int,callback: (String?) -> Unit) {
        val audioFile = File(path)

        println(audioFile)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/ogg".toMediaTypeOrNull()))
            .addFormDataPart("model", "whisper-large")
            .addFormDataPart("language", "es")
            .build()

        val contentLength = requestBody.contentLength()
        Log.d("Request", "Content length: $contentLength")

        service.makeRequest(requestBody, "http://192.168.1.175:3000/v1/audio/transcriptions", object : OkHttpServiceResponse.RequestCallback{
            override fun onSuccess(response: Any) {
                callback(response.toString())
            }

            override fun onError(responseCode: Number, message: Any) {
                super.onError(responseCode, message)
                Log.d("Eror", message.toString())
                activity.runOnUiThread {
                    messages[position].updateText("Error: $message")
                    MessageHistoryManager.deleteLastValue()
                    chatAdapter.notifyItemChanged(position)
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        })
    }

    fun createAIMessage(): Int {
        val thinkingMessage = ChatMessage(SenderType.AI, MessageType.TEXT_IMAGE, "Pensando...")
        messages.add(thinkingMessage)
        val position = messages.size - 1
        chatAdapter.notifyItemInserted(position)
        recyclerView.scrollToPosition(messages.size - 1)

        return position
    }

    fun predict(position: Int) {
        (activity as? AppCompatActivity)?.lifecycleScope?.launch(Dispatchers.Main) {
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
                    activity.runOnUiThread {
                        accumulatedContent.append(chunk.choices.delta.content)
                        messages[position].updateText(accumulatedContent.toString())
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(position)
                    }
                }

                override fun onFinallyStream() {
                    activity.runOnUiThread {
                        val finalContent = accumulatedContent.toString()
                        MessageHistoryManager.addMessage("assistant", finalContent)
                        chatAdapter.notifyItemChanged(position)
                    }
                    recyclerView.itemAnimator = animate

                }

                override fun onError(responseCode: Number, message: Any) {
                    Log.d("Eror", message.toString())
                    activity.runOnUiThread {
                        messages[position].updateText("Error: $message")
                        MessageHistoryManager.deleteLastValue()
                        chatAdapter.notifyItemChanged(position)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
            service.makeStreamRequestAI(
                requestBody,
                "http://192.168.1.175:3000/v1/chat/completions",
                callback
            )
        }
    }
}