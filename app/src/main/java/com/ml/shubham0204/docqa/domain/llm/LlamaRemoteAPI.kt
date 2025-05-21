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
import kotlinx.coroutines.flow.toList
import java.io.FileReader
import java.io.FileWriter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LlamaRemoteAPI(private val context: Context) {

    private var llamaAndroid: LLamaAndroid? = null
    private var isModelLoaded = false

//    private var modelNameFile = "qwen2-1_5b-instruct-q4_0.gguf"
//    private var modelNameFile = "Llama-3.2-1B-Instruct-Q6_K_L.gguf"
    private var modelNameFile = "gemma-2-2b-it-Q4_K_M.gguf"

    val tokensHajiUmrah = listOf(
        // Rukun Haji/Umrah
        "ihram", "thawaf", "sa'i", "wukuf", "mabit", "lontar_jumrah", "tahalul",
        "miqat", "tawaf_qudum", "tawaf_ifadhah", "tawaf_wada",

        // Lokasi
        "arafah", "mina", "muzdalifah", "jamarat", "jamrah_ula", "jamrah_wustha",
        "jamrah_aqabah", "hajar_aswad", "maqam_ibrahim", "multazam", "hijr_ismail",

        // Aktivitas
        "talbiyah", "niat_ihram", "doa_thawaf", "lempar_jumrah", "mabit_muzdalifah",
        "potong_rambut", "dam", "dam_tamattu", "dam_qiran",

        // Pakaian & Perlengkapan
        "kain_ihram", "idhtiba", "raml",

        // Waktu
        "tarwiyah", "yaum_arafah", "nafar_awal", "nafar_tsani",

        // Istilah Fikih
        "wajib_haji", "sunah_haji", "larangan_ihram", "fidyah", "haji_tamattu",
        "haji_qiran", "haji_ifrad"
    )

    suspend fun initModel(forceReload: Boolean = false): Boolean = withContext(Dispatchers.IO) {

        if (isModelLoaded && !forceReload) {
            Log.d("LLAMA", "Model already initialized.")
            return@withContext true
        }

        return@withContext try {
            if (forceReload) {
                llamaAndroid?.unload() // <-- Tambah baris ini kalau ada dukungan unload
                isModelLoaded = false
            }

            // Pastikan model sudah disalin ke filesDir
            copyGGUFModelFromAssets(context)

            llamaAndroid = LLamaAndroid.instance()
            val modelFile = File(context.filesDir, "models/$modelNameFile")
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

//            // Tambahkan token khusus setelah model loaded
//            addCustomTokens(tokensHajiUmrah)

            Log.d("LLAMA", "Model loaded from $modelPath")
            isModelLoaded = true
            true
        } catch (e: Exception) {
            Log.e("LLAMA", "Failed to load model", e)
            false
        }
    }

    // Fungsi untuk menambahkan token ke vocab model
//    private fun addCustomTokens(tokens: List<String>) {
//        try {
//            // Pastikan model sudah di-load
//            if (llamaAndroid == null) throw IllegalStateException("Model not loaded")
//
//            // Dapatkan native context dari binding JNI
//            val ctx = llamaAndroid?.getNativeContext() ?: return
//
//            // Tambahkan setiap token
//            tokens.forEach { token ->
//                // Gunakan JNI call ke native Llama.cpp function
//                llama_model_add_token(
//                    ctx,
//                    token,
//                    false // false = bukan special token
//                )
//            }
//
//            Log.d("LLAMA", "${tokens.size} tokens added to vocab")
//        } catch (e: Exception) {
//            Log.e("LLAMA", "Failed to add tokens", e)
//        }
//    }

    suspend fun getResponsePerToken(
        prompt: String,
        query: String,             // <-- Tambah query
        correctAnswer: String,     // <-- Tambah correctAnswer
        retrieveDuration: Long,
        onToken: suspend (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val totalStart = System.currentTimeMillis()
            initModel(forceReload = true) // <-- ini bikin selalu fresh

            val inputTokens = prompt.split("\\s+".toRegex()).size

            Log.d("PROMPT", "$prompt")

            val resultTokens = StringBuilder()
            val inferenceStart = System.currentTimeMillis()
            var firstTokenTime: Long? = null

            llamaAndroid?.send(prompt)?.collect { token ->
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

        val logFile = File(downloadsFolder, "gemma_150_256_sparse.json")
//        val logFile = File(downloadsFolder, "hasil_final_chunk_150_256_hybrid.json")
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

//    suspend fun getResponse(prompt: String): String? = withContext(Dispatchers.IO) {
//        try {
//            // Inisialisasi model (pastikan ini hanya dijalankan sekali)
//            initModel()
//
//
//            Log.d("LLAMA", "Prompt length (chars): ${context.length}")
//            Log.d("LLAMA", "Prompt content: \n$context")
//            val result = llamaAndroid?.send(prompt)?.toList()?.joinToString("")?.trim()
//
//            Log.d("LLamaResult", "Result: $result")
//
//            Log.d("LLAMA", "Response: $result")
//            return@withContext result
//
//        } catch (e: Exception) {
//            Log.e("LLAMA", "Error during prompt execution", e)
//            return@withContext null
//        }
//    }

    // Fungsi untuk menyalin model GGUF dari assets ke filesDir
    fun copyGGUFModelFromAssets(context: Context) {
        val modelName = modelNameFile
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
            }
        } else {
            Log.d("MODEL_COPY", "Model already exists at: ${modelFile.absolutePath}")
        }
    }

}