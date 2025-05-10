package com.ml.shubham0204.docqa.domain.llm

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.os.Environment

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.FileReader
import java.io.FileWriter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LlamaRemoteAPI(private val context: Context) {

    private var llamaAndroid: LLamaAndroid? = null
    private var isModelLoaded = false

    suspend fun initModel(): Boolean = withContext(Dispatchers.IO) {

        if (isModelLoaded) {
            Log.d("LLAMA", "Model already initialized.")
            return@withContext true
        }

        return@withContext try {
            // Pastikan model sudah disalin ke filesDir
            copyGGUFModelFromAssets(context)

            llamaAndroid = LLamaAndroid.instance()
            val modelFile = File(context.filesDir, "models/qwen2-1_5b-instruct-q4_0.gguf")
            val modelPath = modelFile.absolutePath

            if (!modelFile.exists()) {
                Log.e("LLAMA", "Model file does not exist at: $modelPath")
                return@withContext false
            }

            llamaAndroid?.load(
                pathToModel = modelPath,
                userThreads = 4,
                topK = 40,
                topP = 0.9f,
                temp = 0.8f
            )

            Log.d("LLAMA", "Model loaded from $modelPath")
            isModelLoaded = true
            true
        } catch (e: Exception) {
            Log.e("LLAMA", "Failed to load model", e)
            false
        }
    }

    suspend fun getResponsePerToken(
        prompt: String,
        query: String,             // <-- Tambah query
        correctAnswer: String,     // <-- Tambah correctAnswer
        retrieveDuration: Long,
        onToken: suspend (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val totalStart = System.currentTimeMillis()
            initModel()

            val inputTokens = prompt.split("\\s+".toRegex()).size

            Log.d("PROMPT", "Prompt: $prompt")

            val resultTokens = StringBuilder()
            val inferenceStart = System.currentTimeMillis()
            var firstTokenTime: Long? = null

            val konteks = """
             $prompt
         """.trimIndent()

            llamaAndroid?.send(konteks)?.collect { token ->
                if (firstTokenTime == null) {
                    firstTokenTime = System.currentTimeMillis()
                }
                resultTokens.append(token)
                Log.d("TOKEN", "Received token: $token")

                // Kirim ke UI/WebSocket/dsb via callback
                withContext(Dispatchers.Main) {
                    onToken(token)
                }
            }

            val inferenceEnd = System.currentTimeMillis()
            val resultText = resultTokens.toString().trim()

            val ttft = (firstTokenTime ?: inferenceStart) - inferenceStart
            val outputTokens = resultText.split("\\s+".toRegex()).size

            val itps = inputTokens / ((inferenceStart - totalStart).coerceAtLeast(1) / 1000.0)
            val otps = outputTokens / ((inferenceEnd - inferenceStart).coerceAtLeast(1) / 1000.0)
            val oet = (inferenceEnd - inferenceStart) / 1000.0
            val totalTime = (System.currentTimeMillis() - totalStart) / 1000.0

            Log.d("LLAMA", "Query: $query")
            Log.d("LLAMA", "Correct_Answer: $correctAnswer")
            Log.d("LLAMA", "Waktu_Retrieve: $retrieveDuration")
            Log.d("LLAMA", "Result: $resultText")
            Log.d("LLAMA", "TTFT: ${ttft / 1000.0} sec")
            Log.d("LLAMA", "ITPS: ${"%.2f".format(itps)} tokens/sec")
            Log.d("LLAMA", "OTPS: ${"%.2f".format(otps)} tokens/sec")
            Log.d("LLAMA", "OET: $oet sec")
            Log.d("LLAMA", "Waktu_Generate: $totalTime sec")

            // Menyimpan log ke CSV
            val logData = mapOf(
                "query" to query,
                "correct_answer" to correctAnswer,
                "waktu_retrieve" to "$retrieveDuration",
                "prompt" to prompt,
                "result" to resultText,
                "ttft" to "${ttft / 1000.0}",
                "itps" to "%.2f".format(itps),
                "otps" to "%.2f".format(otps),
                "oet" to "$oet",
                "waktu_generate" to "$totalTime"
            )

            // Menyimpan log ke json dengan tambahan prompt
//            writeLogToCSV(context, logData, prompt)
            writeLogToJson(context, logData)

            return@withContext resultText

        } catch (e: Exception) {
            Log.e("LLAMA", "Error during prompt execution", e)
            return@withContext null
        }
    }

//    suspend fun getFullResponse(prompt: String): String? = withContext(Dispatchers.IO) {
//        val result = StringBuilder()
//        getResponsePerToken(prompt) { token ->
//            result.append(token)
//        }
//        return@withContext result.toString()
//    }

//    suspend fun getStreamingResponse(prompt: String, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
//        try {
//            initModel()
//            val context = prompt.trimIndent()
//            Log.d("LLAMA", "Streaming prompt:\n$context")
//
//            llamaAndroid?.send(context)?.collect { token ->
//                onToken(token)
//            }
//        } catch (e: Exception) {
//            Log.e("LLAMA", "Error streaming response", e)
//            onToken("\n[Error: ${e.message}]")
//        }
//    }

//    fun writeLogToCSV(context: Context, logData: Map<String, String>, prompt: String) {
//        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//
//        if (!downloadsFolder.exists()) {
//            downloadsFolder.mkdirs()
//        }
//
//        val logFile = File(downloadsFolder, "log_hasil_generate.csv")
//
//        try {
//            val fileWriter = FileWriter(logFile, true)
//
//            if (logFile.length() == 0L) {
//                val headers = "prompt;bleu;rouge_l;ttft;itps;otps;oet;total_time"
//                fileWriter.appendLine(headers)
//            }
//
//            // Escape tanda kutip di prompt
//            val safePrompt = prompt.replace("\"", "\"\"")
//
//            // Bungkus prompt dengan tanda kutip
//            val quotedPrompt = "\"$safePrompt\""
//
//            val csvLine = logData.entries.joinToString(";") { "${it.key}=${it.value}" } + ";$quotedPrompt"
//
//            fileWriter.appendLine(csvLine)
//            fileWriter.flush()
//            fileWriter.close()
//
//            Log.d("LLAMA", "Log saved to ${logFile.absolutePath}")
//
//        } catch (e: IOException) {
//            Log.e("LLAMA", "Error writing log to CSV", e)
//        }
//    }

    fun writeLogToJson(context: Context, logData: Map<String, String>) {
        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!downloadsFolder.exists()) {
            downloadsFolder.mkdirs()
        }

        val logFile = File(downloadsFolder, "log_hasil_1.2.2.json")
        val gson = GsonBuilder().setPrettyPrinting().create()

        try {
            // Baca data lama (kalau ada)
            val logs: MutableList<Map<String, String>> = if (logFile.exists() && logFile.length() > 0) {
                val reader = FileReader(logFile)
                val existingLogs: MutableList<Map<String, String>> = gson.fromJson(reader, object : TypeToken<MutableList<Map<String, String>>>() {}.type)
                reader.close()
                existingLogs
            } else {
                mutableListOf()
            }

            // Tambahkan timestamp ke log baru
            val currentTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            val logWithTimestamp = logData.toMutableMap()
            logWithTimestamp["timestamp"] = currentTimestamp

            // Tambahkan log baru
            logs.add(logWithTimestamp)

            // Tulis ulang semua data
            val writer = FileWriter(logFile, false) // false = overwrite
            gson.toJson(logs, writer)
            writer.flush()
            writer.close()

            Log.d("LLAMA", "Log saved to ${logFile.absolutePath}")

        } catch (e: IOException) {
            Log.e("LLAMA", "Error writing log to JSON", e)
        }
    }

    // Fungsi untuk menyalin model GGUF dari assets ke filesDir
    fun copyGGUFModelFromAssets(context: Context) {
        val modelName = "qwen2-1_5b-instruct-q4_0.gguf"
        val modelFile = File(context.filesDir, "models/$modelName")

        // Jika model belum ada di filesDir, salin dari assets
        if (!modelFile.exists()) {
            try {
                // Cek apakah asset model ada
                val assetInputStream = context.assets.open(modelName)
                Log.d("MODEL_COPY", "Asset file found: $modelName")

                // Buat folder jika belum ada
                val modelDirectory = File(context.filesDir, "models")
                if (!modelDirectory.exists()) {
                    modelDirectory.mkdirs()
                    Log.d("MODEL_COPY", "Created models directory.")
                }

                // Salin file dari assets ke filesDir
                FileOutputStream(modelFile).use { outputStream ->
                    assetInputStream.copyTo(outputStream)
                    Log.d("MODEL_COPY", "Model copied to: ${modelFile.absolutePath}")
                }
            } catch (e: IOException) {
                Log.e("MODEL_COPY", "Error copying model from assets", e)
            } catch (e: Exception) {
                Log.e("MODEL_COPY", "Unexpected error during model copy", e)
            }
        } else {
            Log.d("MODEL_COPY", "Model already exists at: ${modelFile.absolutePath}")
        }
    }
}