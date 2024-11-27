package com.setvene.jm.pinessys

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.setvene.jm.pinessys.utils.AudioRecorder
import okhttp3.*
import org.json.JSONObject
import java.io.*
import kotlin.math.abs

class InLiveActivity : AppCompatActivity() {

    private lateinit var liveButton: MaterialButton
    private lateinit var vibrator: Vibrator
    private lateinit var webSocket: WebSocket
    private lateinit var client: OkHttpClient
    private var mediaPlayer: MediaPlayer? = null
    private val audioQueue: MutableList<File> = mutableListOf() // Cola de reproducción
    private var isPlayingAudio = false // Estado de reproducción

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_live)

        // Configurar el Vibrator
        val vibratorManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        } else {
            TODO("VERSION.SDK_INT < S")
        }
        liveButton = findViewById(R.id.btn_in_live)
        vibrator = vibratorManager.defaultVibrator

        // Configuración de OkHttpClient y WebSocket
        client = OkHttpClient()
        val request = Request.Builder()
            .url("ws://192.168.1.176:8000/ws/generate_audio/") // URL de tu servidor WebSocket
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Log.d("WebSocket", "Conexión establecida")

                val message = """
                    {
                        "type": "conversation.create"
                    }
                """.trimIndent()

                webSocket.send(message)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d("WebSocket", "Mensaje recibido: $text")
                handleReceivedAudio(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.e("WebSocket", "Error en la conexión: ${t.message}")
            }
        })

        // Manejo del AudioRecorder
        val audioRecorder = AudioRecorder(this)
        val handler = Handler(Looper.getMainLooper())
        var stopRecordingRunnable: Runnable? = null

        var initialX = 0f
        var initialTouchX = 0f
        var initialTouchY = 0f
        val cancelThreshold = 60f
        val cancelVerticalThreshold = 100f

        liveButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = view.x
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    vibrator.vibrate(100) // Vibrar al inicio
                    startRecordingAnimation()
                    audioRecorder.startRecording()
                    stopRecordingRunnable = Runnable {
                        if (audioRecorder.isRecording) {
                            val audioFilePath = audioRecorder.stopRecording()
                            sendAudio(audioFilePath!!)
                            resetUI(view, initialX)
                        }
                    }
                    handler.postDelayed(stopRecordingRunnable!!, 60000) // 60 segundos
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
                                    sendAudio(audioFilePath!!)
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
                    val deltaY = event.rawY - initialTouchY
                    if (deltaY > cancelVerticalThreshold) {
                        if (audioRecorder.isRecording) {
                            audioRecorder.cancelRecording()
                            resetUI(view, initialX)
                        }
                    } else {
                        if (deltaX < 0 && abs(deltaX) < cancelThreshold) {
                            view.x = initialX + deltaX
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecordingAnimation() {
        runOnMainThread {
            liveButton.animate()
                .scaleX(1.6f)
                .scaleY(1.6f)
                .setDuration(500)
                .start()
        }
    }


    private fun resetUI(view: View, initialX: Float) {
        runOnMainThread  {
            liveButton.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .start()

            view.x = initialX
        }
    }

    // Función para convertir audio a Base64
    private fun audioToBase64(filePath: String): String {
        val file = File(filePath)
        val fileInputStream = FileInputStream(file)
        val bytes = fileInputStream.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // Función para enviar audio en Base64 a través del WebSocket
    private fun sendAudio(audioFilePath: String) {
        val audioBase64 = audioToBase64(audioFilePath)

        val message = """
            {
                "type": "conversation.item.create",
                "item": {
                    "type": "message",
                    "role": "user",
                    "content": [
                        {
                            "type": "input_audio",
                            "content": "$audioBase64"
                        }
                    ]
                }
            }
        """.trimIndent()

        webSocket.send(message)
        Log.d("WebSocket", "Audio enviado: $audioBase64")
    }

    private fun handleReceivedAudio(message: String) {
        try {
            val jsonObject = JSONObject(message)

            if (jsonObject.optString("type") == "response.chunk") {
                val audioBase64 = jsonObject.getJSONObject("chunk")
                    .getString("audio")

                val audioBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
                val tempFile = createTempAudioFile()
                val outputStream = FileOutputStream(tempFile)
                outputStream.write(audioBytes)
                outputStream.close()

                // Agregar el archivo a la cola
                audioQueue.add(tempFile)
                runOnMainThread{
                    playNextAudioIfNeeded()
                }
            } else {
                Log.d("WebSocket", "Tipo de mensaje no esperado: ${jsonObject.optString("type")}")
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "Error al manejar el audio recibido: ${e.message}")
        }
    }

    private fun playNextAudioIfNeeded() {
        if (!isPlayingAudio && audioQueue.isNotEmpty()) {
            // Deshabilitar el botón al iniciar la reproducción
            Handler(Looper.getMainLooper()).post {
                liveButton.isEnabled = false
            }

            val nextAudioFile = audioQueue.removeAt(0) // Tomar el primer archivo de la cola
            playAudio(nextAudioFile)
        } else if (audioQueue.isEmpty()) {
            // Habilitar el botón cuando se termina la reproducción
            runOnMainThread  {
                liveButton.isEnabled = true
            }
        }
    }


    private fun playAudio(file: File) {
        isPlayingAudio = true
        mediaPlayer?.release() // Liberar el MediaPlayer anterior si existe
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()

            // Calcular el tiempo para el siguiente audio
            val overlapTime = 250L // 250 ms antes
            val playDuration = duration - overlapTime

            // Programar la reproducción del siguiente audio
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                isPlayingAudio = false
                playNextAudioIfNeeded() // Continuar con el siguiente audio
            }, playDuration.coerceAtLeast(0)) // Asegurarse de no usar tiempos negativos

            setOnCompletionListener {
                // Asegurar que el MediaPlayer actual se libera al terminar
                release()
            }
        }
        Log.d("MediaPlayer", "Reproduciendo audio desde archivo: ${file.absolutePath}")
    }

    private fun createTempAudioFile(): File {
        val tempFile = File.createTempFile("audio_", ".mp3", cacheDir)
        tempFile.deleteOnExit()
        return tempFile
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Cerrando la conexión WebSocket")
        client.dispatcher.executorService.shutdown()
        mediaPlayer?.release() // Liberar el MediaPlayer
    }

    fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post { action() }
        }
    }

}
