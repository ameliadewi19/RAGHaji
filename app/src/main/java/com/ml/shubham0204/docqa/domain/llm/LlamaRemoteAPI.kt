package com.ml.shubham0204.docqa.domain.llm

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
            val modelFile = File(context.filesDir, "models/Llama-3.2-1B-Instruct-Q6_K_L.gguf")
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

            // Inisialisasi model
            initModel()
//            if (!modelInitialized) {
//                Log.e("LLAMA", "Model initialization failed")
//                return@withContext null
//            }

            Log.d("LLAMA", "Prompt given: $prompt")

            // Mengirim prompt ke model
            val result = llamaAndroid?.send(prompt)?.firstOrNull()

            Log.d("LLAMA", "Response: $result")
            return@withContext result

        } catch (e: Exception) {
            Log.e("LLAMA", "Error during prompt execution", e)
            return@withContext null
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