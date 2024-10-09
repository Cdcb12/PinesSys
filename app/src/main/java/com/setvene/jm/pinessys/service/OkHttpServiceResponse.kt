package com.setvene.jm.pinessys.service

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.setvene.jm.pinessys.R
import com.setvene.jm.pinessys.model.Choice
import com.setvene.jm.pinessys.model.Delta
import com.setvene.jm.pinessys.model.EventStreamChunk
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class OkHttpServiceResponse(private val context: Activity) {
    interface StreamCallback {
        fun onStartStream() {}
        fun onChunkReceived(chunk: EventStreamChunk) {}
        fun onError(responseCode: Number, message: Any) {}
        fun onFinallyStream() {}
    }

    fun makeStreamRequestAI(formBody: RequestBody, url: String, callback: StreamCallback) {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMessage = getErrorMessage(e)
                callback.onError(400, errorMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    println("Response Body: $responseBody")  // Log the full response body

                    try {
                        val lines = responseBody.split("\n")
                        val choicesList = mutableListOf<Choice>()
                        callback.onStartStream()

                        for (line in lines) {
                            if (line.startsWith("data: ")) {
                                val jsonData = line.removePrefix("data: ").trim()

                                if (jsonData == "[DONE]") {
                                    break
                                }

                                val jsonObject = JSONObject(jsonData)
                                val choicesArray = jsonObject.getJSONArray("choices")
                                val choice = choicesArray.getJSONObject(0)
                                val delta = choice.getJSONObject("delta")
                                val content = delta.optString("content", "")

                                val eventStreamChunk = EventStreamChunk(
                                    Choice(
                                        choice.getInt("index"),
                                        Delta(
                                            delta.optString("role", ""),
                                            content
                                        ),
                                        choice.opt("finish_reason")
                                    )
                                )
                                Thread.sleep(10)
                                callback.onChunkReceived(eventStreamChunk)

                            }
                        }

                        callback.onFinallyStream()

                    } catch (e: JSONException) {
                        callback.onError(422, "Error parsing JSON response: $e")
                    }
                } else {
                    callback.onError(503, "Error getting response body")
                }
            }
        })
    }

    private fun getErrorMessage(e: IOException): String {
        return when (e) {
            is ConnectException -> context.getString(R.string.okhttp_error_ConnectException)
            is UnknownHostException -> context.getString(R.string.okhttp_error_UnknownHostException)
            is SocketTimeoutException -> context.getString(R.string.okhttp_error_SocketTimeoutException)
            is SSLHandshakeException -> context.getString(R.string.okhttp_error_SSLHandshakeException)
            is MalformedURLException -> context.getString(R.string.okhttp_error_MalformedURLException)
            else -> e.toString()
        }
    }
}