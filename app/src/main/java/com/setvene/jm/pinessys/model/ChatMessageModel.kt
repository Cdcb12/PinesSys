package com.setvene.jm.pinessys.model

import android.graphics.Bitmap

data class ChatMessage(
    var sender: SenderType,
    var messageType: MessageType,
    var text: String? = null,
    var imageUrl: Bitmap? = null,
    var audioPath: String? = null,
    var toolFinish: Boolean? = false,
    var toolFinishCallback: (() -> Unit)? = null
) {
    fun updateText(newText: String?) {
        text = newText
    }
    fun finishToolExecution() {
        toolFinish = true
        toolFinishCallback?.invoke()
    }
}

enum class SenderType {
    USER,
    AI
}

enum class MessageType {
    TEXT_IMAGE,
    AUDIO,
    TOOL
}