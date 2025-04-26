package com.ml.shubham0204.docqa.domain.llm

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import com.chaquo.python.PyObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.flow.toList

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

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
            val modelFile = File(context.filesDir, "models/Llama-3.2-3B-Instruct-Q4_K_L.gguf")
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

    suspend fun getResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            // Inisialisasi model (pastikan ini hanya dijalankan sekali)
            initModel()

            // Tambahkan konteks sebelum prompt utama
            val context = """
            $prompt
        """.trimIndent()

            Log.d("LLAMA", "Prompt length (chars): ${context.length}")
            Log.d("LLAMA", "Prompt content: \n$context")

            val compare = """
                Mabit di Muzdalifah waktunya mulai setelah Maghrib sampai terbit fajar 10 Dzulhijjah dan boleh sesaat asal sudah lewat tengah malam.
            """.trimIndent()
            val result = llamaAndroid?.send(context)?.toList()?.joinToString("")?.trim()

            Log.d("LLamaResult", "Result: $result")
            Log.d("Evaluation", "Halo")

            val py = Python.getInstance()

            val bm25Module = py.getModule("evaluation")

            val hasil = bm25Module.callAttr("evaluate", compare, result.toString())
            val hasilMap = hasil.asMap() as Map<String, Any>  // Tambahkan casting eksplisit

            val bleu = hasilMap["bleu"]?.toString()?.toDouble()  // Pastikan nilai yang diambil adalah angka
            val rougeL = hasilMap["rouge_l"]?.toString()?.toDouble()

            Log.d("Evaluation", "BLEU: $bleu, ROUGE-L: $rougeL")

            Log.d("LLAMA", "Response: $result")
            return@withContext result

        } catch (e: Exception) {
            Log.e("LLAMA", "Error during prompt execution", e)
            return@withContext null
        }
    }

    suspend fun getStreamingResponse(prompt: String, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            initModel()
            val context = prompt.trimIndent()
            Log.d("LLAMA", "Streaming prompt:\n$context")

            llamaAndroid?.send(context)?.collect { token ->
                onToken(token)
            }
        } catch (e: Exception) {
            Log.e("LLAMA", "Error streaming response", e)
            onToken("\n[Error: ${e.message}]")
        }
    }

    // Fungsi untuk menyalin model GGUF dari assets ke filesDir
    fun copyGGUFModelFromAssets(context: Context) {
        val modelName = "Llama-3.2-1B-Instruct-Q6_K_L.gguf"
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