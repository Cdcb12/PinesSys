package com.setvene.jm.pinessys.utils

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException

@Suppress("DEPRECATION")
class AudioRecorder(private val context: Context) {
    private var audioRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    var isRecording = false

    fun startRecording() {
        if (isRecording) return // Prevent starting if already recording

        audioFile = File(context.externalCacheDir, "audio_${System.currentTimeMillis()}.mp3")
        audioRecorder = MediaRecorder().apply { // Use the default constructor
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: IOException) {
                Log.e("AudioRecorder", "Error preparing or starting recording: ${e.message}", e)
                // Handle error, e.g., display a message to the user
                releaseRecorder()
            }
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return null // Prevent stopping if not recording

        return try {
            audioRecorder?.apply {
                stop()
                releaseRecorder()
            }
            audioFile?.absolutePath
        } catch (e: RuntimeException) {
            Log.e("AudioRecorder", "Error stopping recording: ${e.message}", e)
            // Handle error, e.g., display a message to the user
            releaseRecorder()
            null
        }
    }

    fun cancelRecording() {
        if (!isRecording) return // Prevent canceling if not recording

        try {
            audioRecorder?.apply {
                stop()
                release()
            }
            audioFile?.delete()
            releaseRecorder()
        } catch (e: RuntimeException) {
            Log.e("AudioRecorder", "Error canceling recording: ${e.message}", e)
            // Handle error, e.g., display a message to the user
        }
    }
    private fun releaseRecorder() {
        audioRecorder?.release()
        audioRecorder = null
        isRecording = false
    }
}