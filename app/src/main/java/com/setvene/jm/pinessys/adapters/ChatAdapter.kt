package com.setvene.jm.pinessys.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.setvene.jm.pinessys.R
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import com.setvene.jm.pinessys.ui.toPx


class ChatAdapter(private val messages: MutableList<ChatMessage>, private val context: Context) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val audioPlaybackStates = mutableMapOf<String, AudioPlaybackState>()

    private var currentlyPlayingAudio: String? = null

    data class AudioPlaybackState(
        var mediaPlayer: MediaPlayer? = null,
        var isPlaying: Boolean = false,
        var currentPosition: Int = 0,
        var playPauseButton: MaterialButton? = null,
        var seekBar: SeekBar? = null
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_body_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message, position)
    }

    override fun getItemCount(): Int = messages.size


    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val clientTextView: TextView = itemView.findViewById(R.id.client)
        private val container: LinearLayout = itemView.findViewById(R.id.container)
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.message_container)


        @SuppressLint("MissingInflatedId")
        fun bind(message: ChatMessage, position: Int) {
            Log.e("ChatAdapter", "Binding message: ${message.sender}")
            var isSameSender = position > 0 && messages[position - 1].sender == message.sender

            if (message.messageType == MessageType.TOOL && position > 0) {
                isSameSender = false
                itemView.post {
                    val previousMessage = messages[position - 1]
                    if (previousMessage.sender == message.sender && previousMessage.messageType != MessageType.TOOL) {
                        messages.removeAt(position - 1)
                        notifyItemRemoved(position - 1)
                    }
                }
            }

            when (message.sender) {
                SenderType.AI -> {
                    if (!isSameSender) {
                        clientTextView.text = "Nubill AI"
                        clientTextView.visibility = View.VISIBLE
                        messageContainer.gravity = Gravity.START  // Asegúrate de que los mensajes de AI estén a la izquierda
                        container.setPaddingRelative(
                            0,
                            container.paddingTop,
                            60.toPx(),
                            container.paddingBottom
                        )
                    } else {
                        clientTextView.visibility = View.GONE
                        messageContainer.gravity = Gravity.START
                    }
                }
                SenderType.USER -> {
                    if (!isSameSender) {
                        clientTextView.text = "User"
                        clientTextView.visibility = View.VISIBLE
                        messageContainer.gravity = Gravity.END  // Asegúrate de que los mensajes de USER estén a la derecha
                        container.setPaddingRelative(
                            60.toPx(),
                            container.paddingTop,
                            0,
                            container.paddingBottom
                        )
                    } else {
                        clientTextView.visibility = View.GONE
                        messageContainer.gravity = Gravity.END
                    }
                }
            }

            container.removeAllViews()

            val layoutInflater = LayoutInflater.from(container.context)
            when (message.messageType) {
                MessageType.TEXT_IMAGE -> {
                    val view = layoutInflater.inflate(R.layout.text_message_item, container, false)
                    val textMessage: TextView = view.findViewById(R.id.textMessage)
                    val imageView: ImageView = view.findViewById(R.id.imageView)

                    if (!message.text.isNullOrEmpty()) {
                        textMessage.text = formatTextWithBold(message.text!!)
                        textMessage.visibility = View.VISIBLE
                    } else {
                        textMessage.visibility = View.GONE
                    }

                    if (message.imageUrl != null) {
                        imageView.setImageBitmap(message.imageUrl)
                        imageView.visibility = View.VISIBLE
                    } else {
                        imageView.visibility = View.GONE
                    }

                    container.addView(view)
                }
                MessageType.AUDIO -> {
                    val view = layoutInflater.inflate(R.layout.audio_message_item, container, false)
                    val playPauseButton: MaterialButton = view.findViewById(R.id.btn_clear_cart)
                    val seekBar: SeekBar = view.findViewById(R.id.audioSeekBar)
                    val audioPath = message.audioPath

                    // Obtener o crear el estado de reproducción para este audio específico
                    val audioState = audioPlaybackStates.getOrPut(audioPath as String) {
                        AudioPlaybackState(
                            playPauseButton = playPauseButton,
                            seekBar = seekBar
                        )
                    }

                    // Actualizar referencias de UI
                    audioState.playPauseButton = playPauseButton
                    audioState.seekBar = seekBar

                    fun stopCurrentlyPlayingAudio() {
                        currentlyPlayingAudio?.let { path ->
                            audioPlaybackStates[path]?.let { state ->
                                state.mediaPlayer?.let { player ->
                                    try {
                                        if (player.isPlaying) {
                                            player.pause()
                                        }
                                    } catch (e: IllegalStateException) {
                                        // Ignore if already released
                                    }
                                    state.isPlaying = false
                                    state.playPauseButton?.setIconResource(R.drawable.ic_play)
                                }
                            }
                        }
                    }

                    fun resetMediaPlayer() {
                        audioState.mediaPlayer?.let { player ->
                            try {
                                if (player.isPlaying) {
                                    player.pause()
                                }
                                player.release()
                            } catch (e: IllegalStateException) {
                                // Ignore if already released
                            }
                        }
                        audioState.mediaPlayer = null
                        audioState.isPlaying = false
                    }

                    fun createMediaPlayer(): MediaPlayer? {
                        return try {
                            MediaPlayer.create(context, Uri.parse(audioPath)).apply {
                                setOnCompletionListener { mp ->
                                    try {
                                        mp.release()
                                    } catch (e: IllegalStateException) {
                                        // Ignore if already released
                                    }

                                    playPauseButton.setIconResource(R.drawable.ic_play)
                                    seekBar.progress = 0

                                    // Resetear el estado de este audio específico
                                    audioState.mediaPlayer = null
                                    audioState.isPlaying = false
                                    audioState.currentPosition = 0

                                    // Limpiar el audio actualmente en reproducción
                                    if (currentlyPlayingAudio == audioPath) {
                                        currentlyPlayingAudio = null
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatAdapter", "Error creating MediaPlayer", e)
                            null
                        }
                    }

                    fun startPlayback() {
                        // Detener el audio que se está reproduciendo actualmente
                        if (currentlyPlayingAudio != audioPath) {
                            stopCurrentlyPlayingAudio()
                        }

                        // Si no hay MediaPlayer o está liberado, crear uno nuevo
                        if (audioState.mediaPlayer == null) {
                            resetMediaPlayer()
                            val mediaPlayer = createMediaPlayer()
                            mediaPlayer?.let { player ->
                                // Configurar seekbar
                                seekBar.max = player.duration

                                // Iniciar reproducción desde la última posición conocida
                                player.seekTo(audioState.currentPosition)
                                player.start()

                                // Actualizar UI
                                playPauseButton.setIconResource(R.drawable.ic_pause)

                                // Actualizar estado
                                audioState.mediaPlayer = player
                                audioState.isPlaying = true
                                currentlyPlayingAudio = audioPath

                                // Configurar actualización de seekbar
                                val handler = Handler(Looper.getMainLooper())
                                val updateSeekBar = object : Runnable {
                                    override fun run() {
                                        try {
                                            if (player.isPlaying) {
                                                val currentPosition = player.currentPosition
                                                seekBar.progress = currentPosition
                                                audioState.currentPosition = currentPosition
                                                handler.postDelayed(this, 100)
                                            }
                                        } catch (e: IllegalStateException) {
                                            handler.removeCallbacks(this)
                                        }
                                    }
                                }
                                handler.post(updateSeekBar)
                            }
                        } else {
                            // Si ya hay un MediaPlayer, alternar entre reproducir y pausar
                            audioState.mediaPlayer?.let { player ->
                                if (!audioState.isPlaying) {
                                    // Detener cualquier otro audio que se esté reproduciendo
                                    stopCurrentlyPlayingAudio()

                                    player.seekTo(audioState.currentPosition)
                                    player.start()
                                    playPauseButton.setIconResource(R.drawable.ic_pause)
                                    audioState.isPlaying = true
                                    currentlyPlayingAudio = audioPath

                                    // Configurar actualización de seekbar
                                    val handler = Handler(Looper.getMainLooper())
                                    val updateSeekBar = object : Runnable {
                                        override fun run() {
                                            try {
                                                if (player.isPlaying) {
                                                    val currentPosition = player.currentPosition
                                                    seekBar.progress = currentPosition
                                                    audioState.currentPosition = currentPosition
                                                    handler.postDelayed(this, 100)
                                                }
                                            } catch (e: IllegalStateException) {
                                                handler.removeCallbacks(this)
                                            }
                                        }
                                    }
                                    handler.post(updateSeekBar)
                                } else {
                                    player.pause()
                                    playPauseButton.setIconResource(R.drawable.ic_play)
                                    audioState.isPlaying = false
                                }
                            }
                        }
                    }

                    // Configurar botón de reproducción
                    playPauseButton.setOnClickListener {
                        try {
                            startPlayback()
                        } catch (e: Exception) {
                            Log.e("ChatAdapter", "Error in playback", e)
                        }
                    }

                    // Configurar seekbar
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                try {
                                    audioState.mediaPlayer?.seekTo(progress)
                                    audioState.currentPosition = progress
                                } catch (e: IllegalStateException) {
                                    // MediaPlayer has been released
                                }
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar) {}
                    })

                    // Restaurar estado previo si existe
                    seekBar.progress = audioState.currentPosition
                    if (audioState.isPlaying) {
                        playPauseButton.setIconResource(R.drawable.ic_pause)
                    }

                    // Reproducir automáticamente si es un mensaje de AI
                    if (message.sender == SenderType.AI) {
                        startPlayback()
                    }

                    container.addView(view)
                }
                MessageType.TOOL -> {
                    val toolView = layoutInflater.inflate(R.layout.call_tool_message_item, container, false)
                    val toolTextView: TextView = toolView.findViewById(R.id.toolName)
                    val progressBar: ProgressBar = toolView.findViewById(R.id.progressBar)
                    toolTextView.text = message.text
                    if (message.toolFinish == true) {
                        progressBar.visibility = View.GONE
                    }
                    message.toolFinishCallback = {
                        Handler(Looper.getMainLooper()).post {
                            progressBar.visibility = View.GONE
                        }
                    }

                    container.addView(toolView)
                }
            }
        }
    }

    private fun formatTextWithBold(text: String): SpannableString {
        // Expresión regular para encontrar texto entre **
        val boldPattern = "\\*\\*(.*?)\\*\\*".toRegex()
        val matches = boldPattern.findAll(text)

        // Crear un nuevo texto sin los ** y calcular las posiciones correctas
        val spannableString = SpannableString(text.replace(boldPattern, "$1"))

        var offset = 0

        matches.forEach { matchResult ->
            val originalStart = matchResult.range.first
            val originalEnd = matchResult.range.last

            // Calcular las posiciones ajustadas quitando los **
            val adjustedStart = originalStart - offset
            val adjustedEnd = originalEnd - offset - 2

            // Aplicar negrita a las posiciones ajustadas
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                adjustedStart,
                adjustedEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Actualizar el offset para mantener las posiciones correctas
            offset += 4 // Dos ** al inicio y dos ** al final
        }

        return spannableString
    }
}