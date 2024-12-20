package com.setvene.jm.pinessys.controllers

import com.setvene.jm.pinessys.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

val messages: MutableList<ChatMessage> = mutableListOf()


object MessageHistoryManager {
    private const val MAX_HISTORY_SIZE = 30
    private val messageHistory: MutableList<Map<String, String>> = mutableListOf()

    fun addMessage(role: String, content: String) {
        limitHistorySize()
        messageHistory.add(mapOf("role" to role, "content" to content))
    }

    fun getMessagesJsonArray(): JSONArray {
        return JSONArray().apply {
            messageHistory.forEach { message ->
                put(JSONObject().apply {
                    put("role", message["role"])
                    put("content", message["content"])
                })
            }
        }
    }

    fun clearHistory() {
        messageHistory.clear()
    }

    fun getHistory(): List<Map<String, String>> {
        return messageHistory.toList()
    }

    fun deleteLastValue() {
        if (messageHistory.isNotEmpty()) {
            messageHistory.removeLast()
        }
    }
    private fun limitHistorySize() {
        if (messageHistory.size < MAX_HISTORY_SIZE) return

        var userIndex: Int? = null
        var assistantIndex: Int? = null

        for (i in messageHistory.indices) {
            if (userIndex == null && messageHistory[i]["role"] == "user") {
                userIndex = i
            } else if (assistantIndex == null && messageHistory[i]["role"] == "assistant") {
                assistantIndex = i
            }

            if (userIndex != null && assistantIndex != null) {
                break
            }
        }

        if (userIndex != null && assistantIndex != null) {
            if (userIndex < assistantIndex) {
                messageHistory.removeAt(assistantIndex)
                messageHistory.removeAt(userIndex)
            }
        }
    }
}