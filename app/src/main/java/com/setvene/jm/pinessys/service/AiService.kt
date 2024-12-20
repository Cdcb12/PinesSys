package com.setvene.jm.pinessys.service

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.setvene.jm.pinessys.adapters.ChatAdapter
import com.setvene.jm.pinessys.controllers.messages
import com.setvene.jm.pinessys.model.ChatMessage
import com.setvene.jm.pinessys.model.MessageType
import com.setvene.jm.pinessys.model.SenderType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiService(
    private val chatAdapter: ChatAdapter,
    private val recyclerView: RecyclerView,
    private val listener: WebSocketClientListener
) {
    private var webSocket: WebSocket? = null
    private var conversationId: String? = null
    var callback: MessageResponseCallback? = null

    fun createAIMessage(): Int {
        val thinkingMessage = ChatMessage(SenderType.AI, MessageType.TEXT_IMAGE, "Pensando...")
        messages.add(thinkingMessage)
        val position = messages.size - 1
        chatAdapter.notifyItemInserted(position)
        recyclerView.scrollToPosition(messages.size - 1)

        return position
    }

    fun predict(position: Int) {

    }


    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(serverUrl: String) {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Conexión establecida")
                createConversation()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Mensaje recibido: $text")
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "Mensaje de bytes recibido")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Cerrando conexión: $code - $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error en la conexión", t)
                listener.onConnectionError(t)
            }
        })
    }

    private fun createConversation() {
        val conversationPayload = JSONObject().apply {
            put("type", "conversation.create")
            put("response", JSONObject().apply {
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "get_inventory")
                        put("description", "Check product inventory in warehouses")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("product_code", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The unique code of the product to check in inventory")
                                })
                                put("warehouse", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The warehouse location to check inventory (optional)")
                                    put("optional", true)
                                })
                            })
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "get_image")
                        put("description", "Retrieve product image based on product code")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("product_code", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The unique code of the product to retrieve image")
                                })
                            })
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "modify_packets_inventory")
                        put("description", "Modify the package inventory with several operations")
                        put("delicate", true)
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("product_code", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The unique code of the product")
                                })
                                put("operation_type", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "Type of Operation to perform on the packet")
                                    put("enum", JSONArray().apply {
                                        put("establish")
                                        put("subtract")
                                        put("add")
                                        put("delete")
                                        put("create")
                                    })
                                })
                                put("packet_id", JSONObject().apply {
                                    put("type", "number")
                                    put("description", "Identification of the packet to be modified")
                                })
                                put("quantity", JSONObject().apply {
                                    put("type", "number")
                                    put("description", "Quantity to modify the packet")
                                    put("optional", true)
                                })
                            })
                        })
                    })
                })
            })
        }

        webSocket?.send(conversationPayload.toString())
    }

    fun sendMessage(
        text: String? = null,
        audioBase64: String? = null,
        toolName: String? = null,
        toolResult: Any? = null,
        toolError: String? = null,
        callback: MessageResponseCallback? = null
    ) {
        conversationId?.let { id ->
            val messagePayload = JSONObject().apply {
                // Determine message type based on parameters
                put("type", when {
                    toolName != null -> "tools.response"
                    else -> "conversation.item.create"
                })
                put("id", id)

                // Handle tool response
                if (toolName != null) {
                    put("tool_name", toolName)

                    toolResult?.let {
                        put("result", when (it) {
                            is JSONObject -> it
                            is JSONArray -> it
                            else -> JSONObject().put("data", it.toString())
                        })
                    }

                    toolError?.let {
                        put("error", it)
                    }
                }

                // Handle conversation item creation
                if (text != null || audioBase64 != null) {
                    put("item", JSONObject().apply {
                        put("content", JSONArray().apply {
                            text?.let {
                                put(JSONObject().apply {
                                    put("type", "input_text")
                                    put("text", it)
                                })
                            }
                            audioBase64?.let {
                                put(JSONObject().apply {
                                    put("type", "input_audio")
                                    put("audio", it)
                                })
                            }
                        })
                    })
                }
            }

            this.callback = callback

            webSocket?.send(messagePayload.toString())
        }
    }

    private fun createResponse() {
        conversationId?.let { id ->
            val responsePayload = JSONObject().apply {
                put("type", "response.create")
                put("id", id)
            }

            webSocket?.send(responsePayload.toString())
        }
    }

    interface WebSocketClientListener {
        fun onConversationCreated(conversationId: String)
        fun onResponseReceived(response: JSONObject)
        fun onConnectionError(throwable: Throwable)

    }
    abstract class MessageResponseCallback {
        open fun onToolResponse(toolName: String, result: Any?) {
        }
        abstract fun onResponseReceived(response: MessageType, text: String)
    }

    private fun handleMessage(message: String) {
        try {
            val jsonMessage = JSONObject(message)
            when (jsonMessage.getString("type")) {
                "conversation.create.response" -> {
                    if (jsonMessage.getJSONObject("response").getString("status") == "success") {
                        conversationId = jsonMessage.getString("id")
                        Log.d("WebSocket", "Conversación creada con ID: $conversationId")
                        listener.onConversationCreated(conversationId!!)
                    }
                }
                "conversation.item.created" -> {
                    Log.d("WebSocket", "Mensaje enviado")
                    createResponse()
                }
                "response.created" -> {
                    listener.onResponseReceived(jsonMessage)
                }
                "error" -> {
                    Log.e("WebSocket", "Error: ${jsonMessage.getString("message")}")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "Error procesando mensaje", e)
        }
    }

    fun close() {
        webSocket?.close(1000, "Cerrando conexión")
    }
}