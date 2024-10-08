package com.setvene.jm.pinessys.controllers

import org.json.JSONArray
import org.json.JSONObject

object MessageHistoryManager {

    private val messageHistory: MutableList<Map<String, String>> = mutableListOf()

    fun addMessage(role: String, content: String) {
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
}