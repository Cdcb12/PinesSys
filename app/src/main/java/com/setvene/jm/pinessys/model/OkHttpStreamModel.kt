package com.setvene.jm.pinessys.model

class OkHttpStreamModel {
}

data class EventStreamChunk(
    val choices: Choice
)

data class Choice(
    val index: Int,
    val delta: Delta,
    val finishReason: Any?
)

data class Delta(
    val role: String,
    val content: String
)