package com.setvene.jm.pinessys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.controllers.messages
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import com.setvene.jm.pinessys.service.AiService
import com.setvene.jm.pinessys.service.OkHttpServiceResponse
import com.setvene.jm.pinessys.utils.AudioRecorder
import okhttp3.FormBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), AiService.WebSocketClientListener {

    private lateinit var inputMessage: EditText
    private lateinit var multiButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var vibrator: Vibrator
    private lateinit var serviceAI: AiService
    private lateinit var okHttpServiceResponse: OkHttpServiceResponse

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputMessage = findViewById(R.id.input_message)
        multiButton = findViewById(R.id.btn_multi)

        val vibratorManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        } else {
            TODO("VERSION.SDK_INT < S")
        }
        vibrator = vibratorManager.defaultVibrator
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messages, this)
        recyclerView.adapter = chatAdapter
        okHttpServiceResponse = OkHttpServiceResponse(this)

        serviceAI = AiService(chatAdapter, recyclerView, this)
        serviceAI.connect("ws://192.168.0.179:8080")
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
        val handler = Handler(Looper.getMainLooper())
        var stopRecordingRunnable: Runnable? = null

        multiButton.setOnClickListener {
            if (inputMessage.text.isNotEmpty()) {
                sendMessage()
            }
        }

        var initialX = 0f
        var initialTouchX = 0f
        val cancelThreshold = 60f
        multiButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = view.x
                    initialTouchX = event.rawX
                    if (inputMessage.text.isEmpty()) {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                        startRecordingAnimation()
                        audioRecorder.startRecording()
                        stopRecordingRunnable = Runnable {
                            if (audioRecorder.isRecording) {
                                val audioFilePath = audioRecorder.stopRecording()
                                sendAudio(audioFilePath)
                                resetUI(view, initialX)
                            }
                        }
                        handler.postDelayed(stopRecordingRunnable!!, 60000) // 60 seconds
                    }
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - initialTouchX
                    if (audioRecorder.isRecording) {
                        if (abs(deltaX) < cancelThreshold) {
                            stopRecordingRunnable?.let { handler.removeCallbacks(it) }
                            stopRecordingRunnable = Runnable {
                                if (audioRecorder.isRecording) {
                                    val audioFilePath = audioRecorder.stopRecording()
                                    sendAudio(audioFilePath)
                                    resetUI(view, initialX)
                                }
                            }
                            handler.postDelayed(stopRecordingRunnable!!, 100)
                        } else {
                            audioRecorder.cancelRecording()
                            resetUI(view, initialX)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    if (deltaX < 0 && abs(deltaX) < cancelThreshold) {
                        view.x = initialX + deltaX
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun startRecordingAnimation() {
        multiButton.animate()
            .scaleX(1.6f)
            .scaleY(1.6f)
            .setDuration(500)
            .start()
    }

    private fun resetUI(view: View, initialX: Float) {
        multiButton.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .start()

        view.x = initialX
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

            val position = serviceAI.createAIMessage()
            callbackAddMessage(messageText)
            serviceAI.predict(position)

        }
    }


    private fun sendAudio(audioData: String?) {
        if (audioData == null) {
            Log.e("AUDIO_RECORD", "No audio data captured")
            return
        }

        try {
            val audioFile = File(audioData)

            if (!audioFile.exists()) {
                Log.e("AUDIO_RECORD", "Audio file does not exist")
                return
            }

            val audioBase64 = try {
                val fileInputStream = FileInputStream(audioFile)
                val bytes = fileInputStream.readBytes()
                fileInputStream.close()

                Base64.encodeToString(bytes, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e("AUDIO_RECORD", "Error reading audio file", e)
                null
            }

            val newMessage = ChatMessage(
                sender = SenderType.USER,
                messageType = MessageType.AUDIO,
                audioPath = audioData
            )
            messages.add(newMessage)
            chatAdapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)

            if (audioBase64 != null) {
                serviceAI.createAIMessage()
                callbackAddMessage(audioBase64 = audioBase64)
            }

        } catch (e: Exception) {
            Log.e("AUDIO_RECORD", "Error processing audio", e)
        }
    }

    private fun callbackAddMessage(
        messageText: String? = null,
        audioBase64: String?= null,
        image: Any?= null,
        toolName: String? = null,
        toolResult: Any? = null,
        toolError: String? = null,
    ){
        serviceAI.sendMessage(text = messageText,audioBase64 = audioBase64, toolName = toolName, toolResult = toolResult, toolError = toolError , callback = object :
            AiService.MessageResponseCallback() {
            override fun onResponseReceived(response: MessageType, text: String) {
                when (response) {
                    MessageType.TEXT_IMAGE -> {
                        if (image == null) {
                            addMessageToChat(text)
                            return
                        }
                        addMessageToChat(text, image as Bitmap)
                    }
                    MessageType.AUDIO -> handleAudioResponse(text)
                    MessageType.TOOL -> TODO()
                }
            }
        })
    }

    override fun onConversationCreated(conversationId: String) {
        runOnUiThread {
            Toast.makeText(this, "Conversación creada: $conversationId", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResponseReceived(response: JSONObject) {
        runOnUiThread {
            try {
                if (response.has("tools_call")) {
                    val toolsCall = response.getJSONObject("tools_call")
                    val toolType = toolsCall.getString("type")
                    val toolName = toolsCall.getString("tool_name")
                    val params = toolsCall.get("params")

                    handleToolCall(toolType, toolName, params.toString())
                    return@runOnUiThread
                }

                // Procesar la respuesta normal
                val responseContent = response.getJSONArray("response")
                for (i in 0 until responseContent.length()) {
                    val content = responseContent.getJSONObject(i)
                    when (content.getString("type")) {
                        "input_text" -> {
                            val text = content.getString("content")
                            serviceAI.callback?.onResponseReceived(MessageType.TEXT_IMAGE, text)
                        }

                        "input_audio" -> {
                            val audioBase64 = content.getString("content")
                            serviceAI.callback?.onResponseReceived(MessageType.AUDIO, audioBase64)
                        }
                    }
                }


            } catch (e: Exception) {
                Log.e("WebSocket", "Error procesando respuesta", e)
            }
        }
    }

    override fun onConnectionError(throwable: Throwable) {
        runOnUiThread {
            Toast.makeText(this, "Error de conexión: ${throwable.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun addMessageToChat(message: String, image: Bitmap? = null) {
        if (messages.isNotEmpty() &&
            messages.last().sender == SenderType.AI &&
            messages.last().messageType != MessageType.TOOL
        ) {
            val lastMessage = messages.last()
            lastMessage.updateText(message)
            chatAdapter.notifyItemChanged(messages.size - 1)
        } else {
            val chatMessage = ChatMessage(
                sender = SenderType.AI,
                messageType = MessageType.TEXT_IMAGE,
                text = message,
                imageUrl = image
            )

            Log.d("MainActivity", "Imagen: $image")

            messages.add(chatMessage)
            chatAdapter.notifyItemInserted(messages.size - 1)
        }

        recyclerView.scrollToPosition(messages.size - 1)
    }
    private fun handleToolCall(toolType: String, toolName: String, params: String) {

        val chatMessage = ChatMessage(SenderType.AI, MessageType.TOOL, toolName)
        messages.add(chatMessage)
        chatAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        when (toolName) {
            "get_inventory" -> {
                val callback = object : OkHttpServiceResponse.LoginCallback {
                    override fun onSuccess(response: Any) {
                        Log.e("ToolCall", "Response: $response")
                        callbackAddMessage( toolResult = response, toolName = toolName, toolError = null)
                        messages[messages.size - 1].finishToolExecution()
                    }

                    override fun onError(responseCode: Number, message: Any) {
                        Log.e("ToolCall", "Error: $message")
                        callbackAddMessage( toolResult = message, toolName = toolName, toolError = null)
                        messages[messages.size - 1].finishToolExecution()
                    }
                }
                val item = JSONObject(params)
                Log.e("ToolCall", "Params: ${item.getString("product_code")}")

                okHttpServiceResponse.makeRequest(
                    FormBody.Builder(),
                    "http://192.168.0.179/Api/item/quantity/code/${item.getString("product_code")}",
                    callback
                )
            }

            "get_image" -> {
                val item = JSONObject(params)
                okHttpServiceResponse.loadImage("http://192.168.0.179/Api/item/img/code/${item.getString("product_code")}") { success, result ->
                    Log.e("ToolCall", "Image: $result")
                    if (success as Boolean) {
                        callbackAddMessage(
                            image = result as Bitmap,
                            toolName = toolName,
                            toolResult = "Image with product_code ${item.getString("product_code")} was found and is being displayed"
                        )
                    } else {
                        callbackAddMessage(
                            toolResult = result as String,
                            toolName = toolName + " product_code " + item.getString("product_code"),
                            toolError = null
                        )
                    }

                    messages[messages.size - 1].finishToolExecution()
                }

            }
            "modify_packets_inventory" -> {
                Log.e("ToolCall", "Params: $params")
                val callback = object : OkHttpServiceResponse.LoginCallback {
                    override fun onSuccess(response: Any) {
                        Log.e("ToolCall", "Response: $response")
                        callbackAddMessage( toolResult = response, toolName = toolName, toolError = null)
                        messages[messages.size - 1].finishToolExecution()
                    }

                    override fun onError(responseCode: Number, message: Any) {
                        Log.e("ToolCall", "Error: $message")
                        callbackAddMessage( toolResult = message, toolName = toolName, toolError = null)
                        messages[messages.size - 1].finishToolExecution()
                    }
                }
                val item = JSONObject(params)
                Log.e("ToolCall", "Params: ${item.getString("product_code")}")
                val formBody = FormBody.Builder()
                    .add("operation_type", item.getString("operation_type"))
                    .add("packet_id", item.getString("packet_id"))
                    .add("quantity", item.getString("quantity"))

                okHttpServiceResponse.makeRequest(
                    formBody,
                    "http://192.168.0.179/Api/item/packets/quantity/${item.getString("product_code")}",
                    callback
                )


            }

            else -> {
                Log.w("ToolCall", "Tipo de herramienta no reconocido: $toolType")
            }
        }
    }

    private fun handleAudioResponse(audioBase64: String) {
        val thinkingMessage = ChatMessage(SenderType.AI, MessageType.AUDIO)
        messages.add(thinkingMessage)
        val position = messages.size - 1
        chatAdapter.notifyItemInserted(position)
        recyclerView.scrollToPosition(messages.size - 1)

        val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ai_response_$timestamp.wav"

        val audioDir = File(getExternalFilesDir(null), "AudioResponses")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }

        val audioFile = File(audioDir, fileName)

        FileOutputStream(audioFile).use { fos ->
            fos.write(audioBytes)
        }

        messages[position] = ChatMessage(
            sender = SenderType.AI,
            messageType = MessageType.AUDIO,
            audioPath = audioFile.absolutePath
        )

        chatAdapter.notifyItemChanged(position)
    }
}