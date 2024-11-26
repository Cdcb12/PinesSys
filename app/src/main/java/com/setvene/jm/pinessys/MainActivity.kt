package com.setvene.jm.pinessys

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.controllers.messages
import com.setvene.jm.pinessys.controllers.MessageHistoryManager
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import com.setvene.jm.pinessys.service.AiService
import com.setvene.jm.pinessys.utils.AudioRecorder
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var inputMessage: EditText
    private lateinit var multiButton: MaterialButton
    private lateinit var liveButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var vibrator: Vibrator
    private lateinit var serviceAI: AiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputMessage = findViewById(R.id.input_message)
        multiButton = findViewById(R.id.btn_multi)
        liveButton = findViewById(R.id.btn_live)

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

        serviceAI = AiService(this, chatAdapter, recyclerView)
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
        liveButton.setOnClickListener {
            val liveIntent = Intent(this, InLiveActivity::class.java)
            startActivity(liveIntent)
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
            MessageHistoryManager.addMessage("user", messageText)
            val position = serviceAI.createAIMessage()
            serviceAI.predict(position)
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
        val position = serviceAI.createAIMessage()
        serviceAI.listen(audioData, position) { response ->
            response?.let { MessageHistoryManager.addMessage("user", it) }
            serviceAI.predict(position)
        }
    }
}