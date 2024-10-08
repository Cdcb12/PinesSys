package com.setvene.jm.pinessys.model

data class ChatMessage(
    val sender: SenderType,
    val messageType: MessageType,
    val text: String,
)

enum class SenderType {
    USER,
    AI
}

enum class MessageType {
    TEXT_IMAGE,
    AUDIO
}