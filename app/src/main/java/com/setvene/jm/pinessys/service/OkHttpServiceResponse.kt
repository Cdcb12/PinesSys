package com.setvene.jm.pinessys.service

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import com.setvene.jm.pinessys.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    private fun getUserAgent(): String {
        return try {
            val webSettings = WebSettings.getDefaultUserAgent(context)
            webSettings
        } catch (e: Exception) {
            "Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID}"
        }
    }


    interface LoginCallback {

        fun onSuccess(response: Any)
        fun onError(responseCode: Number, message: Any) {}
    }

    fun loadImage(url: String, callback: (Any?, Any?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer NS20gEo80zV6F3WoxFOR5UKgztqilJ63")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ImageLoad", "Error al cargar la imagen", e)
                callback(false, "Error to Load Image: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.let { inputStream ->
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            callback(true, bitmap)
                        } ?: callback(false, "Empty response body")
                    } else {
                        // Intenta leer el cuerpo del error como un string
                        val errorBody = response.body?.string() ?: "No error details"
                        Log.e("ImageLoad", "Error en la respuesta: ${response.code} - $errorBody")
                        callback(false, errorBody)
                    }
                } catch (e: Exception) {
                    Log.e("ImageLoad", "Unexpected error", e)
                    callback(false, "Unexpected error: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }

    fun makeRequest(formBody: FormBody.Builder, url: String, callback: LoginCallback, dialogShow: Boolean = true) {
        val client = OkHttpClient()
        val requestBody = formBody.build()

        val userAgent = getUserAgent()

        val request = Request.Builder()
            .url( url )
            .post(requestBody)
            .addHeader("Authorization", "Bearer NS20gEo80zV6F3WoxFOR5UKgztqilJ63")
            .addHeader("User-Agent", userAgent)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMessage = getErrorMessage(e)
                callback.onError(400, errorMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val inquiryResponse = jsonObject.get("response")

                        when (response.code) {
                            200 -> {
                                context.runOnUiThread {
                                    callback.onSuccess(inquiryResponse)
                                }
                            }
                            else -> {
                                callback.onError(response.code, inquiryResponse)
                            }
                        }

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