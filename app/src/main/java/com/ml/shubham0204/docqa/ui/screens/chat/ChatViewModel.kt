package com.ml.shubham0204.docqa.ui.screens.chat

import androidx.lifecycle.ViewModel
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.data.GeminiAPIKey
import com.ml.shubham0204.docqa.data.RetrievedContext
import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.llm.GeminiRemoteAPI
import com.ml.shubham0204.docqa.domain.llm.LlamaRemoteAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import android.content.Context
import android.util.Log
import com.ml.shubham0204.docqa.domain.retrievers.LuceneIndexer
import org.json.JSONObject

import org.json.JSONArray
import java.io.File
import android.os.Environment

import kotlinx.coroutines.delay

@KoinViewModel
class ChatViewModel(
    private val context: Context,
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val geminiAPIKey: GeminiAPIKey,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) : ViewModel() {
    private val _questionState = MutableStateFlow("")
    val questionState: StateFlow<String> = _questionState

    private val _responseState = MutableStateFlow("")
    val responseState: StateFlow<String> = _responseState

    private val _isGeneratingResponseState = MutableStateFlow(false)
    val isGeneratingResponseState: StateFlow<Boolean> = _isGeneratingResponseState

    private val _retrievedContextListState = MutableStateFlow(emptyList<RetrievedContext>())
    val retrievedContextListState: StateFlow<List<RetrievedContext>> = _retrievedContextListState

    private val luceneIndexer = LuceneIndexer

    private val jsonFileNameRetrieval = "retrieval.json"

    fun mergeChunksWithOverlap(chunks: List<String>): String {
        val merged = chunks.toMutableList()

        while (true) {
            var maxOverlap = 0
            var bestPair: Pair<Int, Int>? = null
            var mergedResult = ""

            // Cari pasangan dengan overlap terpanjang
            for (i in merged.indices) {
                for (j in merged.indices) {
                    if (i == j) continue

                    val aWords = merged[i].split(" ")
                    val bWords = merged[j].split(" ")
                    val maxK = minOf(aWords.size, bWords.size)

                    for (k in maxK downTo 5) {
                        val aEnd = aWords.takeLast(k).joinToString(" ")
                        val bStart = bWords.take(k).joinToString(" ")

                        if (aEnd == bStart && k > maxOverlap) {
                            maxOverlap = k
                            bestPair = i to j
                            mergedResult = (aWords + bWords.drop(k)).joinToString(" ")
                        }

                        val bEnd = bWords.takeLast(k).joinToString(" ")
                        val aStart = aWords.take(k).joinToString(" ")

                        if (bEnd == aStart && k > maxOverlap) {
                            maxOverlap = k
                            bestPair = j to i // arah dibalik
                            mergedResult = (bWords + aWords.drop(k)).joinToString(" ")
                        }
                    }
                }
            }

            if (bestPair != null && maxOverlap > 0) {
                val (i, j) = bestPair
                val newMerged = mutableListOf<String>()

                for (k in merged.indices) {
                    if (k != i && k != j) newMerged.add(merged[k])
                }

                newMerged.add(mergedResult)
                merged.clear()
                merged.addAll(newMerged)
            } else {
                break // tidak ada lagi yang bisa digabung
            }
        }

        return merged.joinToString(" ")
    }


    fun saveJson(
        context: Context,
        query: String,
        chunks: List<Map<String, String?>>,  // izinkan nullable String  // List objek dengan key id dan chunk_text
        filename: String
    ) {
        try {
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsFolder.exists()) downloadsFolder.mkdirs()

            val file = File(downloadsFolder, filename)

            // Baca file dulu jika ada, parse jadi JSONArray
            val jsonArray = if (file.exists()) {
                val existingContent = file.readText()
                if (existingContent.isNotBlank()) JSONArray(existingContent) else JSONArray()
            } else {
                JSONArray()
            }

            // Buat JSONObject baru
            val newObject = JSONObject().apply {
                put("query", query)
                put("retrieved_chunks", JSONArray().apply {
                    chunks.forEach { chunkMap ->
                        val chunkObj = JSONObject()
                        chunkObj.put("id", chunkMap["id"])
                        chunkObj.put("chunk_text", chunkMap["chunk_text"])
                        put(chunkObj)
                    }
                })
            }

            // Tambah objek baru ke array
            jsonArray.put(newObject)

            // Simpan ulang file dengan array lengkap
            file.writeText(jsonArray.toString(4))  // Indent 4 spasi
            Log.d("saveJson", "File saved as JSON array at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("saveJson", "Failed to save JSON: ${e.message}")
        }
    }

    fun getAnswer(
        query: String,
        prompt: String,
        correctAnswer: String,
        topK: Int,
        jsonFileName: String,
//        jsonFileNameRetrieval: String
    ) {
        val retrieveStart = System.currentTimeMillis()

        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = "" // Kosongkan response sebelumnya sebelum mulai


        CoroutineScope(Dispatchers.IO).launch {
            val localQuery = query  // Lock value of query

            try {
                val retrievedChunks = chunksDB.getSimilarChunks(query, n = topK)

                val allChunksText = retrievedChunks.joinToString(separator = "\n\n") { chunk ->
                    "Chunk:\n$chunk"
                }

                Log.d("RETRIEVED CHUNK", allChunksText)

                val chunkList = retrievedChunks.map { it.second.chunkText }


                val chunkListID = retrievedChunks.map {
                    mapOf(
                        "id" to it.second.Id,
                        "chunk_text" to it.second.chunkText
                    )
                }
                if (chunkList.isNotEmpty()) {
                    saveJson(context, localQuery, chunkListID, jsonFileNameRetrieval)
                }

                val jointContext = mergeChunksWithOverlap(chunkList)

                val retrievedContextList = retrievedChunks.map {
                    RetrievedContext(it.second.docFileName, it.second.chunkText)
                }
//
//                val retrievedContextList = ArrayList<RetrievedContext>()
//                var jointContext = ""
//
//                for ((_, chunk) in retrievedChunks) {
//                    Log.d("CHUNK","${chunk.chunkText}")
//                    jointContext += " " + chunk.chunkText
//                    retrievedContextList.add(RetrievedContext(chunk.docFileName, chunk.chunkText))
//                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks: $retrieveDuration ms")

//                val inputPrompt = """
//<|begin_of_text|><|start_header_id|>system<|end_header_id|>
//
//Cutting Knowledge Date: December 2023
//Today Date: 26 Jul 2024
//
//Jawab berdasarkan konteks yang diberikan. Ambil informasi yang relevan dan boleh disusun ulang tanpa mengubah makna. Hindari tambahan informasi yang tidak ada di konteks.
//
//<|eot_id|><|start_header_id|>user<|end_header_id|>
//
//=== Konteks ===
//$jointContext
//===============
//
//Pertanyaan: $query
//
//Jawaban:
//<|eot_id|><|start_header_id|>assistant<|end_header_id|>
//""".trimIndent()

//                val inputPrompt = """
//<|im_start|>system
//
//Tugas Anda:
//- Pilih hanya bagian konteks yang relevan dengan pertanyaan.
//- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
//- Jangan menambahkan informasi atau opini baru yang tidak ada dalam konteks.
//- Jawaban harus singkat dan langsung ke inti pertanyaan.
//- Hindari pengulangan dan penambahan penjelasan lain.
//- Jangan memberikan kalimat tambahan yang tidak ada dalam konteks.
//- Jawaban hanya untuk menjawab pertanyaan yang diajukan.
//- Jangan membuat penutup, kesimpulan tambahan, atau rekomendasi.
//
//<|im_end|>
//<|im_start|>user
//=== Konteks ===
//$jointContext
//===============
//
//Pertanyaan: $query
//
//Jawaban:
//<|im_end|>
//<|im_start|>assistant
//""".trimIndent()

                val inputPrompt = """
<bos><start_of_turn>user
Tugas Anda:
- Pilih bagian konteks yang relevan dengan pertanyaan.
- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
- Anda boleh menjelaskan secara wajar agar jawaban mudah dipahami, selama tetap berdasarkan konteks.
- Jangan menambahkan informasi atau opini yang tidak ada dalam konteks.
- Jawaban harus jelas, relevan, dan cukup untuk menjawab pertanyaan.
- Gunakan format kalimat yang rapi dan mudah dibaca.
- Perhatikan spasi, tanda baca, dan baris baru jika diperlukan.
- Hindari pengulangan yang tidak perlu.
- Tidak perlu membuat kesimpulan atau rekomendasi kecuali disebut dalam konteks.
- Akhiri jawaban dengan token </s>.

=== Konteks ===
$jointContext
===============

Pertanyaan: $query

Jawaban:
<start_of_turn>model
""".trimIndent()

                val tokenWindow = ArrayDeque<String>() // Jendela untuk cek <|im_end|
                val streamBuffer = ArrayDeque<String>() // Buffer streaming satu langkah tertunda
                val maxWindowSize = 30

                llamaRemoteAPI.getResponsePerToken(inputPrompt, query, correctAnswer, retrieveDuration, jsonFileName) { token ->
                    tokenWindow.addLast(token)
                    if (tokenWindow.size > maxWindowSize) tokenWindow.removeFirst()

                    streamBuffer.addLast(token)

                    val currentStream = tokenWindow.joinToString("")

                    if (currentStream.contains("</")) {
                        // Drop semua token yang sedang di-buffer dan window
                        streamBuffer.clear()
                        Log.d("STREAM TOKEN", "[DROPPED] $token")
                    }
                    else if (streamBuffer.size > 1) {
                        // Ambil dan tampilkan token pertama dari buffer jika aman
                        val nextToken = streamBuffer.removeFirst()
                        _responseState.value += nextToken
                        Log.d("STREAM TOKEN", nextToken)
                    }
                }

                _retrievedContextListState.value = retrievedContextList
            } catch (e: Exception) {
                _responseState.value = "Error: ${e.message}"
                _retrievedContextListState.value = emptyList()
            } finally {
                _isGeneratingResponseState.value = false
            }
        }
    }

    fun getAnswerSparse(
        query: String,
        prompt: String,
        correctAnswer: String,
        topK: Int,
        jsonFileName: String,
//        jsonFileNameRetrieval: String
    ) {
        val retrieveStart = System.currentTimeMillis()

        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = ""

        CoroutineScope(Dispatchers.IO).launch {
            val localQuery = query  // Lock value of query

            try {
                // Ambil N chunk terdekat dari Lucene
                val retrievedChunks = chunksDB.getSimilarChunksSparse(context, query, n = topK)
                Log.d("RETRIEVED CHUNK", "$retrievedChunks")

//                val retrievedContextList = ArrayList<RetrievedContext>()
//                var jointContext = ""

//                var infoCounter = 1
//                for ((_, chunk) in retrievedChunks) {
//                    val infoText = "Informasi $infoCounter:\n${chunk.chunkText}\n---"
//                    Log.d("CHUNK", infoText)
//                    jointContext += "\n$infoText\n"
//                    retrievedContextList.add(RetrievedContext(chunk.docFileName, chunk.chunkText))
//                    infoCounter++
//                }

                val chunkList = retrievedChunks.map { it.second.chunkText }

                val chunkListID = retrievedChunks.map {
                    mapOf(
                        "id" to it.second.Id,
                        "chunk_text" to it.second.chunkText
                    )
                }
                if (chunkList.isNotEmpty()) {
                    saveJson(context, localQuery, chunkListID, jsonFileNameRetrieval)
                }

                val jointContext = mergeChunksWithOverlap(chunkList)

                val retrievedContextList = retrievedChunks.map {
                    RetrievedContext(it.second.docFileName, it.second.chunkText)
                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks (Lucene): $retrieveDuration ms")

                val inputPrompt = """
<bos><start_of_turn>user
Tugas Anda:
- Pilih bagian konteks yang relevan dengan pertanyaan.
- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
- Anda boleh menjelaskan secara wajar agar jawaban mudah dipahami, selama tetap berdasarkan konteks.
- Jangan menambahkan informasi atau opini yang tidak ada dalam konteks.
- Jawaban harus jelas, relevan, dan cukup untuk menjawab pertanyaan.
- Gunakan format kalimat yang rapi dan mudah dibaca.
- Perhatikan spasi, tanda baca, dan baris baru jika diperlukan.
- Hindari pengulangan yang tidak perlu.
- Tidak perlu membuat kesimpulan atau rekomendasi kecuali disebut dalam konteks.
- Akhiri jawaban dengan token </s>.

=== Konteks ===
$jointContext
===============

Pertanyaan: $query

Jawaban:
<start_of_turn>model
""".trimIndent()

                val tokenWindow = ArrayDeque<String>() // Jendela untuk cek <|im_end|
                val streamBuffer = ArrayDeque<String>() // Buffer streaming satu langkah tertunda
                val maxWindowSize = 30

                llamaRemoteAPI.getResponsePerToken(inputPrompt, query, correctAnswer, retrieveDuration, jsonFileName) { token ->
                    tokenWindow.addLast(token)
                    if (tokenWindow.size > maxWindowSize) tokenWindow.removeFirst()

                    streamBuffer.addLast(token)

                    val currentStream = tokenWindow.joinToString("")

                    if (currentStream.contains("</")) {
                        // Drop semua token yang sedang di-buffer dan window
                        streamBuffer.clear()
                        Log.d("STREAM TOKEN", "[DROPPED] $token")
                    }
                    else if (streamBuffer.size > 1) {
                        // Ambil dan tampilkan token pertama dari buffer jika aman
                        val nextToken = streamBuffer.removeFirst()
                        _responseState.value += nextToken
                        Log.d("STREAM TOKEN", nextToken)
                    }
                }

                _retrievedContextListState.value = retrievedContextList

            } catch (e: Exception) {
                _responseState.value = "Error: ${e.message}"
                _retrievedContextListState.value = emptyList()
            } finally {
                _isGeneratingResponseState.value = false
            }
        }
    }

    fun getAnswerHybrid(
        context: Context,
        query: String,
        correctAnswer: String,
        topK: Int,
        lambda: Float = 0.5f,
        jsonFileName: String,
//        jsonFileNameRetrieval: String
    ) {
        val retrieveStart = System.currentTimeMillis()
        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = ""

        CoroutineScope(Dispatchers.IO).launch {
            val localQuery = query  // Lock value of query

            try {
                // Ambil chunk
                val retrievedChunks = chunksDB.getHybridSimilarChunks(
                    context = context,
                    query = query,
                    n = topK,
                    lambda = lambda
                )

                Log.d("RETRIEVED CHUNK", "$retrievedChunks")

                val chunkList = retrievedChunks.map { it.second.chunkText }

                val chunkListID = retrievedChunks.map {
                    mapOf(
                        "id" to it.second.Id,
                        "chunk_text" to it.second.chunkText
                    )
                }
                if (chunkList.isNotEmpty()) {
                    saveJson(context, localQuery, chunkListID, jsonFileNameRetrieval)
                }

                val jointContext = mergeChunksWithOverlap(chunkList)

                val retrievedContextList = retrievedChunks.map {
                    RetrievedContext(it.second.docFileName, it.second.chunkText)
                }

//                val retrievedContextList = ArrayList<RetrievedContext>()
//                var jointContext = ""
//
//                for ((_, chunk) in retrievedChunks) {
//                    Log.d("CHUNK","${chunk.chunkText}")
//                    jointContext += " " + chunk.chunkText
//                    retrievedContextList.add(RetrievedContext(chunk.docFileName, chunk.chunkText))
//                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks (Hybrid): $retrieveDuration ms")

                val inputPrompt = """
<bos><start_of_turn>user
Tugas Anda:
- Pilih bagian konteks yang relevan dengan pertanyaan.
- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
- Anda boleh menjelaskan secara wajar agar jawaban mudah dipahami, selama tetap berdasarkan konteks.
- Jangan menambahkan informasi atau opini yang tidak ada dalam konteks.
- Jawaban harus jelas, relevan, dan cukup untuk menjawab pertanyaan.
- Gunakan format kalimat yang rapi dan mudah dibaca.
- Perhatikan spasi, tanda baca, dan baris baru jika diperlukan.
- Hindari pengulangan yang tidak perlu.
- Tidak perlu membuat kesimpulan atau rekomendasi kecuali disebut dalam konteks.
- Akhiri jawaban dengan token </s>.

=== Konteks ===
$jointContext
===============

Pertanyaan: $query

Jawaban:
<start_of_turn>model
""".trimIndent()

//                val inputPrompt = """
//            Berikut adalah beberapa potong teks konteks yang berisi informasi terkait pertanyaan berikut:
//
//            === Konteks ===
//            $jointContext
//            ===============
//
//            Pertanyaan: $query
//
//            Tugas Anda adalah menjawab pertanyaan di atas hanya berdasarkan informasi yang terdapat dalam konteks.
//            Perhatikan jenis pertanyaannya, dan jawab sesuai dengan jenis tersebut:
//
//            - Jika pertanyaan menanyakan **pengertian/definisi**, berikan hanya pengertian tanpa penjelasan tambahan.
//            - Jika pertanyaan menanyakan **tujuan, manfaat, hukum, atau tata cara**, berikan jawaban yang relevan dan ringkas dari konteks.
//            - Jangan menyisipkan opini, interpretasi tambahan, atau informasi di luar konteks.
//
//            Jawaban:
//            """

                val tokenWindow = ArrayDeque<String>() // Jendela untuk cek <|im_end|
                val streamBuffer = ArrayDeque<String>() // Buffer streaming satu langkah tertunda
                val maxWindowSize = 30

                llamaRemoteAPI.getResponsePerToken(inputPrompt, query, correctAnswer, retrieveDuration, jsonFileName) { token ->
                    tokenWindow.addLast(token)
                    if (tokenWindow.size > maxWindowSize) tokenWindow.removeFirst()

                    streamBuffer.addLast(token)

                    val currentStream = tokenWindow.joinToString("")

                    if (currentStream.contains("</")) {
                        // Drop semua token yang sedang di-buffer dan window
                        streamBuffer.clear()
                        Log.d("STREAM TOKEN", "[DROPPED] $token")
                    }
                    else if (streamBuffer.size > 1) {
                        // Ambil dan tampilkan token pertama dari buffer jika aman
                        val nextToken = streamBuffer.removeFirst()
                        _responseState.value += nextToken
                        Log.d("STREAM TOKEN", nextToken)
                    }
                }

                _retrievedContextListState.value = retrievedContextList

            } catch (e: Exception) {
                _responseState.value = "Error: ${e.message}"
                _retrievedContextListState.value = emptyList()
            } finally {
                _isGeneratingResponseState.value = false
            }
        }
    }

    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0

    fun checkValidAPIKey(): Boolean = geminiAPIKey.getAPIKey() != null
}
