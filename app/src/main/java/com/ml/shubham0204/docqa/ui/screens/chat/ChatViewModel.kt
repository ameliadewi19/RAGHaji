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

//    fun getAnswer(
//        query: String,
//        prompt: String,
//    ) {
//        val apiKey = geminiAPIKey.getAPIKey() ?: throw Exception("Gemini API key is null")
//        val geminiRemoteAPI = GeminiRemoteAPI(apiKey)
//        _isGeneratingResponseState.value = true
//        _questionState.value = query
//        try {
//            var jointContext = ""
//            val retrievedContextList = ArrayList<RetrievedContext>()
//            val queryEmbedding = sentenceEncoder.encodeText(query)
//            chunksDB.getSimilarChunks(queryEmbedding, n = 5).forEach {
//                jointContext += " " + it.second.chunkData
//                retrievedContextList.add(RetrievedContext(it.second.docFileName, it.second.chunkData))
//            }
//            val inputPrompt = prompt.replace("\$CONTEXT", jointContext).replace("\$QUERY", query)
//            CoroutineScope(Dispatchers.IO).launch {
//                geminiRemoteAPI.getResponse(inputPrompt)?.let { llmResponse ->
//                    _responseState.value = llmResponse
//                    _isGeneratingResponseState.value = false
//                    _retrievedContextListState.value = retrievedContextList
//                }
//            }
//        } catch (e: Exception) {
//            _isGeneratingResponseState.value = false
//            _questionState.value = ""
//            throw e
//        }
//    }

//    fun getAnswer(
//        query: String,
//        prompt: String,
//    ) {
//        val inputPrompt = "Here is the user's query: $query" // Hanya ganti QUERY, karena tidak pakai CONTEXT
//
//        val llamaRemoteAPI = LlamaRemoteAPI(context)
//        _isGeneratingResponseState.value = true
//        _questionState.value = query
//
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                llamaRemoteAPI.getResponse(inputPrompt)?.let { llmResponse ->
//                    _responseState.value = llmResponse
//                }
//            } catch (e: Exception) {
//                _responseState.value = "Error: ${e.message}"
//            } finally {
//                _isGeneratingResponseState.value = false
//                _retrievedContextListState.value = emptyList() // kosongin context
//            }
//        }
//    }

    fun expandQuery(userQuery: String): String {
        val query = userQuery.lowercase().trim()

        // -------------------------
        // Faktual / Definisi
        // -------------------------
        if (query.startsWith("apa yang dimaksud dengan") ||
            query.startsWith("apa yang dimaksud") ||
            query.startsWith("apa itu") ||
            query.contains("jelaskan tentang")) {

            val keyword = query.replace("apa yang dimaksud dengan", "")
                .replace("apa itu", "")
                .replace("jelaskan tentang", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            // Template untuk definisi
            val expansions = listOf(
                userQuery,
                "$keyword berasal",
                "Menurut istilah, $keyword",
                "$keyword adalah",
                "$keyword merupakan",
                "$keyword berarti",
                "$keyword menurut"
            )

            return expansions.joinToString(". ") + "."

            // -------------------------
            // Analytical: sebab-akibat, dampak, analisis
            // -------------------------
        } else if (
            query.contains("mengapa") ||
            query.contains("mengapa bisa terjadi") ||
            query.contains("apa penyebab") ||
            query.contains("apa dampak") ||
            query.contains("bagaimana pengaruh") ||
            query.contains("apa akibat")
        ) {
            val keyword = query.replace("mengapa", "")
                .replace("bisa terjadi", "")
                .replace("apa penyebab", "")
                .replace("apa dampak", "")
                .replace("bagaimana pengaruh", "")
                .replace("apa akibat", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            val expansions = listOf(
                userQuery,
                "Alasan di balik $keyword",
                "Faktor yang menyebabkan $keyword",
                "Dampak dari $keyword",
                "Akibat yang ditimbulkan oleh $keyword",
                "Pengaruh $keyword terhadap situasi tertentu"
            )

            return expansions.joinToString(". ") + "."

            // -------------------------
            // Comparative: perbandingan
            // -------------------------
        } else if (
            query.contains("apa perbedaan") ||
            query.contains("apa persamaan") ||
            query.contains("bandingkan antara")
        ) {
            val keyword = query.replace("apa perbedaan", "")
                .replace("apa persamaan", "")
                .replace("bandingkan antara", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            val expansions = listOf(
                userQuery,
                "Persamaan dan perbedaan dari $keyword",
                "Perbandingan karakteristik $keyword",
                "Perbedaan utama dalam $keyword",
                "Kemiripan antara $keyword",
                "Analisis komparatif dari $keyword"
            )

            return expansions.joinToString(". ") + "."

            // -------------------------
            // Tutorial / Prosedural
            // -------------------------
        } else if (
            query.contains("bagaimana cara") ||
            query.contains("langkah-langkah untuk") ||
            query.contains("cara membuat") ||
            query.contains("prosedur untuk")
        ) {
            val keyword = query.replace("bagaimana cara", "")
                .replace("langkah-langkah untuk", "")
                .replace("cara membuat", "")
                .replace("prosedur untuk", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            val expansions = listOf(
                userQuery,
                "Langkah-langkah melakukan $keyword",
                "Tutorial tentang $keyword",
                "Panduan praktis untuk $keyword",
                "Cara menyelesaikan $keyword",
                "Instruksi lengkap $keyword"
            )

            return expansions.joinToString(". ") + "."

        } else {
            // Default: tidak dikenali, kembalikan apa adanya
            return userQuery
        }
    }

    fun getAnswer(
        query: String,
        prompt: String,
        correctAnswer: String,
        topK: Int
    ) {
        val retrieveStart = System.currentTimeMillis()

        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = "" // Kosongkan response sebelumnya sebelum mulai

        CoroutineScope(Dispatchers.IO).launch {
            try {
//                val queryDump = "Apa yang dimaksud dengan ihram? Kata Ihram berasal dari kata, Sedangkan menurut istilah, ihrām"
                val expandedQuery = expandQuery(query)
                Log.d("EXPANDED QUERY", "$expandedQuery")
                val queryEmbedding = sentenceEncoder.encodeText(expandedQuery)
                val retrievedChunks = chunksDB.getSimilarChunks(queryEmbedding, n = topK)

                Log.d("RETRIEVED CHUNK", "$retrievedChunks")

                val chunkList = retrievedChunks.map { it.second.chunkText }
                val jointContext = mergeChunksWithOverlap(chunkList)

                val retrievedContextList = retrievedChunks.map {
                    RetrievedContext(it.second.docFileName, it.second.chunkText)
                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks: $retrieveDuration ms")

//                val inputPrompt = """
//Berikut adalah tiga potong teks konteks yang berisi informasi terkait pertanyaan berikut:
//
//=== Konteks ===
//$jointContext
//===============
//
//Pertanyaan: $query
//
//Tugas Anda adalah menjawab pertanyaan di atas hanya berdasarkan informasi yang terdapat dalam konteks.
//Perhatikan jenis pertanyaannya, dan jawab sesuai dengan jenis tersebut:
//
//- Jika pertanyaan menanyakan **pengertian/definisi**, berikan hanya pengertian tanpa penjelasan tambahan.
//- Jika pertanyaan menanyakan **tujuan, manfaat, hukum, atau tata cara**, berikan jawaban yang relevan dan ringkas dari konteks.
//- Jangan menyisipkan opini, interpretasi tambahan, atau informasi di luar konteks.
//
//Jawaban:
//"""

//                val inputPrompt = """
//            Berikut adalah konteks yang berisi informasi tentang ihram:
//
//            === Konteks ===
//            $jointContext
//            ===============
//
//            Pertanyaan: $query
//
//            Jawablah dengan mengambil $query saja dari konteks di atas. Jangan sertakan informasi lain seperti larangan, sunah, atau panduan ritual selain pengertian ihram.
//
//            Jawaban:
//
//            """

                val inputPrompt = """
<|im_start|>system

Tugas Anda:
- Pilih hanya bagian konteks yang relevan dengan pertanyaan.
- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
- Jangan menambahkan informasi atau opini baru yang tidak ada dalam konteks.
- Jawaban harus singkat dan langsung ke inti pertanyaan.
- Hindari pengulangan dan penambahan penjelasan lain.
- Jangan memberikan kalimat tambahan yang tidak ada dalam konteks.
- Jawaban hanya untuk menjawab pertanyaan yang diajukan.

<|im_end|>
<|im_start|>user
=== Konteks ===
$jointContext
===============

Pertanyaan: $query

Jawaban:
<|im_end|>
<|im_start|>assistant
""".trimIndent()


//                val inputPrompt = """
//            Konteks:
//            $jointContext
//
//            ===
//            Query dari konteks diatas yaitu:
//            $query
//
//            ===
//            Gunakan konteks sebagai jawaban dengan menyimpulkan dari ketiga konteks, tanpa mengulang jawaban yang sama.
//            """

//                val inputPrompt = """
//            Berikut adalah tiga potong teks konteks yang berisi informasi tentang ihram:
//
//            === Konteks ===
//            $jointContext
//            ===============
//
//            Pertanyaan: $query
//
//            Tugas Anda adalah menjawab pertanyaan di atas hanya berdasarkan informasi yang terdapat dalam konteks.
//
//            Jawaban:
//
//            """


//                Gunakan konteks sebagai jawaban dengan menyimpulkan dari ketiga konteks, tanpa mengulang jawaban yang sama.

                // Start streaming response
                llamaRemoteAPI.getResponsePerToken(inputPrompt, query, correctAnswer, retrieveDuration) { token ->
                    // Update response state per token
                    _responseState.value += token
                    Log.d("STREAM TOKEN", "$token")
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

    fun expandQueryForBM25(originalQuery: String, context: Context): String {
        val inputStream = context.assets.open("dict.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)

        val expandedTokens = mutableListOf<String>()
        val tokens = originalQuery.lowercase().split(" ")

        for (token in tokens) {
            if (jsonObject.has(token)) {
                val sinonimArray = jsonObject.getJSONObject(token).getJSONArray("sinonim")
                val sinonimList = List(sinonimArray.length()) { i -> sinonimArray.getString(i) }
                expandedTokens.addAll(listOf(token) + sinonimList)
            } else {
                expandedTokens.add(token)
            }
        }

        return expandedTokens.joinToString(" ")
    }

    fun getAnswerSparse(
        query: String,
        prompt: String,
        correctAnswer: String,
        topK: Int
    ) {
        val retrieveStart = System.currentTimeMillis()

        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val expandedQuery = expandQuery(query)
                Log.d("EXPANDED QUERY", "$expandedQuery")

                // Ambil N chunk terdekat dari Lucene
                val retrievedChunks = chunksDB.getSimilarChunksSparse(context, expandedQuery, n = topK)

//                val retrievedContextList = ArrayList<RetrievedContext>()
//                var jointContext = ""
//
//                for ((_, chunk) in retrievedChunks) {
//                    Log.d("CHUNK","${chunk.chunkData}")
//                    jointContext += " " + chunk.chunkData
//                    retrievedContextList.add(RetrievedContext(chunk.docFileName, chunk.chunkData))
//                }

                val chunkList = retrievedChunks.map { it.second.chunkText }
                val jointContext = mergeChunksWithOverlap(chunkList)

                val retrievedContextList = retrievedChunks.map {
                    RetrievedContext(it.second.docFileName, it.second.chunkText)
                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks (Lucene): $retrieveDuration ms")

                val inputPrompt = """
<|im_start|>system

Tugas Anda:
- Pilih hanya bagian konteks yang relevan dengan pertanyaan.
- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
- Jangan menambahkan informasi atau opini baru yang tidak ada dalam konteks.
- Jawaban harus singkat dan langsung ke inti pertanyaan.
- Hindari pengulangan dan penambahan penjelasan lain.
- Jangan memberikan kalimat tambahan yang tidak ada dalam konteks.
- Jawaban hanya untuk menjawab pertanyaan yang diajukan.

<|im_end|>
<|im_start|>user
=== Konteks ===
$jointContext
===============

Pertanyaan: $query

Jawaban:
<|im_end|>
<|im_start|>assistant
""".trimIndent()

                llamaRemoteAPI.getResponsePerToken(inputPrompt, query, correctAnswer, retrieveDuration) { token ->
                    _responseState.value += token
                    Log.d("STREAM TOKEN", "$token")
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
        lambda: Float = 0.8f
    ) {
        val retrieveStart = System.currentTimeMillis()
        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Proses ekspansi query (jika ada), misal gunakan query langsung
                val expandedQuery = query // Atau bisa pakai teknik query expansion kalau ada

                // Encode embedding di dalam fungsi
                val queryEmbedding = sentenceEncoder.encodeText(expandedQuery)

                // Ambil chunk
                val retrievedChunks = chunksDB.getHybridSimilarChunks(
                    context = context,
                    query = query,
                    queryEmbedding = queryEmbedding,
                    n = topK,
                    lambda = lambda
                )

                val chunkList = retrievedChunks.map { it.second.chunkText }
                val jointContext = mergeChunksWithOverlap(chunkList)

                val retrievedContextList = retrievedChunks.map {
                    RetrievedContext(it.second.docFileName, it.second.chunkText)
                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks (Hybrid): $retrieveDuration ms")

                val inputPrompt = """
<|im_start|>system

Tugas Anda:
- Pilih hanya bagian konteks yang relevan dengan pertanyaan.
- Gunakan kalimat dari konteks secara langsung tanpa mengubah makna.
- Jangan menambahkan informasi atau opini baru yang tidak ada dalam konteks.
- Jawaban harus singkat dan langsung ke inti pertanyaan.
- Hindari pengulangan dan penambahan penjelasan lain.
- Jangan memberikan kalimat tambahan yang tidak ada dalam konteks.
- Jawaban hanya untuk menjawab pertanyaan yang diajukan.

<|im_end|>
<|im_start|>user
=== Konteks ===
$jointContext
===============

Pertanyaan: $query

Jawaban:
<|im_end|>
<|im_start|>assistant
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

                llamaRemoteAPI.getResponsePerToken(inputPrompt, query, correctAnswer, retrieveDuration) { token ->
                    _responseState.value += token
                    Log.d("STREAM TOKEN", "$token")
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

//    fun getAnswer(
//        query: String,
//        prompt: String,
//    ) {
//        val retrieveStart = System.currentTimeMillis()
//
//        val llamaRemoteAPI = LlamaRemoteAPI(context)
//        _isGeneratingResponseState.value = true
//        _questionState.value = query
//        _responseState.value = "" // Kosongkan response sebelumnya sebelum mulai
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val queryEmbedding = sentenceEncoder.encodeText(query)
//                val retrievedChunks = chunksDB.getSimilarChunksBM25(query, n = 3)
//
//                val retrievedContextList = ArrayList<RetrievedContext>()
//                var jointContext = ""
//
//                for ((_, chunk) in retrievedChunks) {
//                    Log.d("CHUNK","${chunk.chunkData}")
//                    jointContext += " " + chunk.chunkData
//                    retrievedContextList.add(RetrievedContext(chunk.docFileName, chunk.chunkData))
//                }
//
//                val retrieveDuration = System.currentTimeMillis() - retrieveStart
//                Log.d("ChatViewModel", "Waktu retrieve konteks: $retrieveDuration ms")
//
//                val inputPrompt = """
//            Konteks:
//            $jointContext
//
//            ===
//            Query dari konteks diatas yaitu:
//            $query
//
//            ===
//            Gunakan konteks sebagai jawaban dengan menyimpulkan dari ketiga konteks, tanpa mengulang jawaban yang sama.
//            """
//
//                // Start streaming response
//                llamaRemoteAPI.getResponsePerToken(inputPrompt) { token ->
//                    // Update response state per token
//                    _responseState.value += token
//                    Log.d("STREAM TOKEN", "$token")
//                }
//
//                _retrievedContextListState.value = retrievedContextList
//
//            } catch (e: Exception) {
//                _responseState.value = "Error: ${e.message}"
//                _retrievedContextListState.value = emptyList()
//            } finally {
//                _isGeneratingResponseState.value = false
//            }
//        }
//    }

    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0

    fun checkValidAPIKey(): Boolean = geminiAPIKey.getAPIKey() != null
}
