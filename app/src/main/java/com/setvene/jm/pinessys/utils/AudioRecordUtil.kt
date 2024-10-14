package com.setvene.jm.pinessys.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.arthenica.ffmpegkit.FFmpegKit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var audioFile: File? = null
    var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    fun startRecording() {
        if (isRecording) return  // Prevenir iniciar si ya está grabando

        audioFile = File(context.externalCacheDir, "audio_${System.currentTimeMillis()}.ogg")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        // Escribir los datos de audio en un archivo OGG en un hilo separado
        Thread {
            val tempFile = File(context.externalCacheDir, "audio_${System.currentTimeMillis()}.pcm")
            val fileOutputStream = FileOutputStream(tempFile)
            val audioData = ByteArray(bufferSize)

            // Grabar los datos en crudo (PCM)
            while (isRecording) {
                val read = audioRecord?.read(audioData, 0, bufferSize)
                if (read != null && read > 0) {
                    fileOutputStream.write(audioData, 0, read)
                }
            }

            fileOutputStream.close()

            // Convertir PCM a OGG usando FFmpeg
            convertPcmToOgg(tempFile, audioFile!!)
            tempFile.delete() // Eliminar archivo PCM temporal
        }.start()
    }

    fun stopRecording(): String? {
        if (!isRecording) return null

        return try {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()

            audioFile?.absolutePath // Devolver la ruta del archivo OGG final
        } catch (e: IOException) {
            Log.e("AudioRecorder", "Error al detener la grabación: ${e.message}", e)
            null
        }
    }

    fun cancelRecording() {
        if (!isRecording) return

        try {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioFile?.delete() // Eliminar archivo OGG si existe
        } catch (e: RuntimeException) {
            Log.e("AudioRecorder", "Error al cancelar la grabación: ${e.message}", e)
        }
    }

    private fun convertPcmToOgg(inputFile: File, outputFile: File) {
        // Comando FFmpeg para convertir PCM a OGG con Opus
        val command = "-y -f s16le -ar 44100 -ac 1 -i ${inputFile.absolutePath} -c:a libopus ${outputFile.absolutePath}"
        FFmpegKit.execute(command)
    }
}