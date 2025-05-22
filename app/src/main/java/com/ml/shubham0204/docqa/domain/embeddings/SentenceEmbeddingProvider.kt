package com.ml.shubham0204.docqa.domain.embeddings

import android.content.Context
import android.util.Log
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single

@Single
class SentenceEmbeddingProvider(private val context: Context) {

    private val sentenceEmbedding = SentenceEmbedding()

    init {
        try {
            Log.d("EmbeddingInit", "Mulai load model ONNX dari assets...")
//            val modelBytes = context.assets.open("all-MiniLM-L6-V2.onnx").use { it.readBytes() }
            val modelBytes = context.assets.open("e5_small_quantized.onnx").use { it.readBytes() }
            Log.d("EmbeddingInit", "Model ONNX berhasil di-load, ukuran: ${modelBytes.size} bytes")

            Log.d("EmbeddingInit", "Mulai copy tokenizer ke local storage...")
            val tokenizerBytes = copyToLocalStorage()
            Log.d("EmbeddingInit", "Tokenizer berhasil di-copy, ukuran: ${tokenizerBytes.size} bytes")

            Log.d("EmbeddingInit", "Mulai inisialisasi sentence embedding...")
            runBlocking(Dispatchers.IO) {
                sentenceEmbedding.init(modelBytes, tokenizerBytes)
            }
            Log.d("EmbeddingInit", "Inisialisasi sentence embedding selesai!")
        } catch (e: Exception) {
            Log.e("EmbeddingInit", "Gagal inisialisasi embedding model: ${e.message}", e)
        }
    }

    fun encodeText(text: String): FloatArray =
        runBlocking(Dispatchers.Default) {
            return@runBlocking sentenceEmbedding.encode(text)
        }

    private fun copyToLocalStorage(): ByteArray {
//        val tokenizerBytes = context.assets.open("tokenizer.json").readBytes()
//        val storageFile = File(context.filesDir, "tokenizer.json")
        val tokenizerBytes = context.assets.open("tokenizer_ver2.json").readBytes()
        val storageFile = File(context.filesDir, "tokenizer_ver2.json")
        if (!storageFile.exists()) {
            storageFile.writeBytes(tokenizerBytes)
        }
        return storageFile.readBytes()
    }
}
