package com.ml.shubham0204.docqa.ui.screens.docs

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.ml.shubham0204.docqa.data.Chunk
import com.ml.shubham0204.docqa.data.ChunkNode
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.di.Utils
import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.readers.Readers
import com.ml.shubham0204.docqa.domain.retrievers.LuceneIndexer
import com.ml.shubham0204.docqa.domain.splitters.SlidingWindowChunker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import setProgressDialogText
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID
import kotlin.math.min

@KoinViewModel
class DocsViewModel(
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) : ViewModel() {

    suspend fun addDocumentFromAssets(
        context: Context,
        fileName: String,
    ) = withContext(Dispatchers.IO) {
        chunksDB.clearChunks()

        val chunks = Utils.readChunksFromAssets(context, fileName)

        val newDocId = documentsDB.addDocument(
            Document(
                docText = "",
                docFileName = fileName,
                docAddedTime = System.currentTimeMillis(),
            )
        )

        Log.d("Embedding", "Test Tambah")


        setProgressDialogText("Adding chunks to database...")

        val size = chunks.size
        chunks.forEachIndexed { index, chunkJson ->
            setProgressDialogText("Added ${index + 1}/$size chunk(s) to database...")

//            val embedding = sentenceEncoder.encodeText(chunkJson.chunk_text)
            val embedding = sentenceEncoder.encodeText("passage: $chunkJson.chunk_text")

            // 🔽 Tambahkan log untuk embedding
            Log.d("EmbeddingDebug", "Chunk ${index + 1} embedding size: ${embedding.size}")
            Log.d("EmbeddingDebug", "Chunk ${index + 1} sample: ${embedding.take(5)}") // ambil 5 nilai awal

            chunksDB.addChunk(
                Chunk(
                    Id = chunkJson.id,
                    parentChunkId = chunkJson.parent_id,
                    chunkText = chunkJson.chunk_text,
                    chunkEmbedding = embedding,
                )
            )
        }

        Log.d("ChunkDebug", "=== CHUNK From Assets JSON ===")
        chunks.forEachIndexed { index, chunkJson ->
            Log.d(
                "ChunkDebug",
                "Chunk ${index + 1} \nchunkId=${chunkJson.id}  \n" +
                        "parentId=${chunkJson.parent_id} (size=${chunkJson.chunk_text.length}, text: ${chunkJson.chunk_text})"
            )
        }
        Log.d("ChunkDebug", "=== TOTAL CHUNKS: ${chunks.size} ===")

//    LuceneIndexer.initializeLuceneIndex(context, chunksDB)
    }

    suspend fun rebuildLuceneIndex(context: Context) {
        withContext(Dispatchers.IO) {
            LuceneIndexer.clearIndex()
            LuceneIndexer.initializeLuceneIndex(context, chunksDB)
        }
    }

    fun removeDocument(docId: Long) {
        documentsDB.removeDocument(docId)
        chunksDB.removeChunks(docId)
        LuceneIndexer.clearIndex()
    }

    fun getAllDocuments(): Flow<List<Document>> = documentsDB.getAllDocuments()

       fun getDocsCount(): Long = documentsDB.getDocsCount()

    // Extracts the file name from the URL
    // Source: https://stackoverflow.com/a/11576046/13546426
    private fun getFileNameFromURL(url: String?): String {
        if (url == null) {
            return ""
        }
        try {
            val resource = URL(url)
            val host = resource.host
            if (host.isNotEmpty() && url.endsWith(host)) {
                return ""
            }
        } catch (e: MalformedURLException) {
            return ""
        }
        val startIndex = url.lastIndexOf('/') + 1
        val length = url.length
        var lastQMPos = url.lastIndexOf('?')
        if (lastQMPos == -1) {
            lastQMPos = length
        }
        var lastHashPos = url.lastIndexOf('#')
        if (lastHashPos == -1) {
            lastHashPos = length
        }
        val endIndex = min(lastQMPos.toDouble(), lastHashPos.toDouble()).toInt()
        return url.substring(startIndex, endIndex)
    }
}
