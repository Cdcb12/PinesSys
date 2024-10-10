package com.setvene.jm.pinessys.model

data class ChatMessage(
    var sender: SenderType,
    var messageType: MessageType,
    var text: String? = null,
    var imageUrl: String? = null,
    var audioPath: String? = null
) {
    fun updateText(newText: String?) {
        text = newText
    }
}

enum class SenderType {
    USER,
    AI
}

enum class MessageType {
    TEXT_IMAGE,
    AUDIO
}