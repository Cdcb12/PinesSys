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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.setvene.jm.pinessys.R
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import com.setvene.jm.pinessys.ui.toPx


class ChatAdapter(private val messages: List<ChatMessage>, private val context: Context) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_body_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val clientTextView: TextView = itemView.findViewById(R.id.client)
        private val container: LinearLayout = itemView.findViewById(R.id.container)
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.message_container)

        @SuppressLint("MissingInflatedId")
        fun bind(message: ChatMessage) {
            // Set sender name and adjust gravity
            when (message.sender) {
                SenderType.AI -> {
                    clientTextView.text = "Nubill AI"
                    messageContainer.gravity = Gravity.START
                    container.setPaddingRelative(
                        0,
                        container.paddingTop,
                        60.toPx(),
                        container.paddingBottom
                    )
                }
                SenderType.USER -> {
                    clientTextView.text = "User"
                    messageContainer.gravity = Gravity.END
                    container.setPaddingRelative(
                        60.toPx(),
                        container.paddingTop,
                        0,
                        container.paddingBottom
                    )
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

                    if (!message.imageUrl.isNullOrEmpty()) {
                        Glide.with(view.context).load(message.imageUrl).into(imageView)
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

                    var mediaPlayer: MediaPlayer? = null
                    val handler = Handler(Looper.getMainLooper())
                    val updateSeekBar: Runnable = object : Runnable {
                        override fun run() {
                            mediaPlayer?.let {
                                if (it.isPlaying) {
                                    seekBar.progress = it.currentPosition
                                    handler.postDelayed(this, 100)
                                }
                            }
                        }
                    }

                    playPauseButton.setOnClickListener {
                        if (mediaPlayer == null) {
                            mediaPlayer = MediaPlayer.create(context, Uri.parse(audioPath)).apply {
                                setOnCompletionListener { mp ->
                                    mp.release()
                                    mediaPlayer = null
                                    handler.removeCallbacks(updateSeekBar)
                                    seekBar.progress = 0 // Reset seek bar to the start
                                    playPauseButton.setIconResource(R.drawable.ic_play) // Reset icon to play
                                }
                                seekBar.max = duration
                                seekTo(seekBar.progress) // Start from the current seek bar position
                                start()
                                handler.post(updateSeekBar)
                                playPauseButton.setIconResource(R.drawable.ic_pause) // Change icon to pause
                            }
                        } else if (mediaPlayer!!.isPlaying) {
                            mediaPlayer?.pause()
                            playPauseButton.setIconResource(R.drawable.ic_play) // Change icon to play
                        } else {
                            mediaPlayer?.seekTo(seekBar.progress) // Ensure it starts from the seek bar position
                            mediaPlayer?.start()
                            handler.post(updateSeekBar)
                            playPauseButton.setIconResource(R.drawable.ic_pause) // Change icon to pause
                        }
                    }

                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser:  Boolean) {
                            if (fromUser  && mediaPlayer != null) {
                                mediaPlayer?.seekTo(progress)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) {
                            // Optional: Add logic if needed when the user starts touching the seek bar
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar) {
                            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                                mediaPlayer?.start() // Start playing if it was paused
                                handler.post(updateSeekBar)
                                playPauseButton.setIconResource(R.drawable.ic_pause) // Change icon to pause
                            }
                        }
                    })

                    container.addView(view)
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