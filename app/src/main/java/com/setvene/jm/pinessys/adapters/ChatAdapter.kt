package com.setvene.jm.pinessys.adapters

import android.annotation.SuppressLint
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


class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

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
                        container.paddingStart,
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
                        container.paddingEnd,
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
                        textMessage.text = message.text
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
                    val playButton: MaterialButton = view.findViewById(R.id.btn_clear_cart)
                    val seekBar: SeekBar = view.findViewById(R.id.audioSeekBar)

                    // Setup audio playback logic here

                    container.addView(view)
                }
            }
        }
    }
}