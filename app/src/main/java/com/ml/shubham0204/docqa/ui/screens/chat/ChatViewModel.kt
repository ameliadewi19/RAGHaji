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

    fun getAnswer(
        query: String,
        prompt: String,
        correctAnswer: String
    ) {
        val retrieveStart = System.currentTimeMillis()

        val llamaRemoteAPI = LlamaRemoteAPI(context)
        _isGeneratingResponseState.value = true
        _questionState.value = query
        _responseState.value = "" // Kosongkan response sebelumnya sebelum mulai

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val queryEmbedding = sentenceEncoder.encodeText(query)
                val retrievedChunks = chunksDB.getSimilarChunks(queryEmbedding, n = 3)

                val retrievedContextList = ArrayList<RetrievedContext>()
                var jointContext = ""

                for ((_, chunk) in retrievedChunks) {
                    Log.d("CHUNK","${chunk.chunkData}")
                    jointContext += " " + chunk.chunkData
                    retrievedContextList.add(RetrievedContext(chunk.docFileName, chunk.chunkData))
                }

                val retrieveDuration = System.currentTimeMillis() - retrieveStart
                Log.d("ChatViewModel", "Waktu retrieve konteks: $retrieveDuration ms")

                val inputPrompt = """
            Konteks:
            $jointContext

            ===
            Query dari konteks diatas yaitu:
            $query

            ===
            Gunakan konteks sebagai jawaban dengan menyimpulkan dari ketiga konteks, tanpa mengulang jawaban yang sama.
            """

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
