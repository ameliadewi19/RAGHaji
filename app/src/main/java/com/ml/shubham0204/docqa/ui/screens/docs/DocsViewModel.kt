package com.ml.shubham0204.docqa.ui.screens.docs

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.ml.shubham0204.docqa.data.Chunk
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.di.Utils
import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.readers.Readers
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
    suspend fun addDocument(
        inputStream: InputStream,
        fileName: String,
        documentType: Readers.DocumentType,
    ) = withContext(Dispatchers.IO) {
        chunksDB.clearChunks()
        val text =
            Readers.getReaderForDocType(documentType).readFromInputStream(inputStream)
                ?: return@withContext
        val newDocId =
            documentsDB.addDocument(
                Document(
                    docText = text,
                    docFileName = fileName,
                    docAddedTime = System.currentTimeMillis(),
                ),
            )

        //default fixed size
//        setProgressDialogText("Creating chunks...")
//        val chunks = WhiteSpaceSplitter.createChunks(text, chunkSize = 512, chunkOverlap = 20)
//        setProgressDialogText("Adding chunks to database...")
//        val size = chunks.size
//        chunks.forEachIndexed { index, s ->
//            setProgressDialogText("Added ${index + 1}/$size chunk(s) to database...")
//            val embedding = sentenceEncoder.encodeText(s)
//            chunksDB.addChunk(
//                Chunk(
//                    docId = newDocId,
//                    docFileName = fileName,
//                    chunkData = s,
//                    chunkEmbedding = embedding,
//                ),
//            )
//        }
//
//        Log.d("ChunkDebug", "=== CHUNK Default ===")
//        chunks.forEachIndexed { index, chunkText ->
//            Log.d(
//                "ChunkDebug",
//                "Chunk $index (size=${chunkText.length}, text: ${chunkText}"
//            )
//        }
//        Log.d("ChunkDebug", "=== TOTAL CHUNKS: ${chunks.size} ===")

        //small2big
//        setProgressDialogText("Creating chunks...")
//        val chunkNodes = Small2BigChunker.createMultiSizeChunks(
//            docText = text,
//            baseChunkSize = 1024,
//            subChunkSizes = listOf(128, 256, 512)
//        )
//        setProgressDialogText("Adding chunks to database...")
//        val size = chunkNodes.size
//        chunkNodes.forEachIndexed { index, node ->
//            setProgressDialogText("Added ${index + 1}/$size chunk(s) to database...")
//
//            val embedding = sentenceEncoder.encodeText(node.text)
//
//            chunksDB.addChunk(
//                Chunk(
//                    docId = newDocId,
//                    docFileName = fileName,
//                    chunkData = node.text,
//                    chunkEmbedding = embedding,
//                    chunkSize = node.size,
//                    chunkUuid = node.id,
//                    parentChunkId = node.parentId
//                )
//            )
//        }
//        Log.d("ChunkDebug", "=== CHUNK HASIL SMALL2BIG ===")
//        chunkNodes.forEachIndexed { index, chunk ->
//            Log.d(
//                "ChunkDebug",
//                "Chunk $index (size=${chunk.size}, parent=${chunk.parentId ?: "ROOT"}): ${chunk.text}..."
//            )
//        }
//        Log.d("ChunkDebug", "=== TOTAL CHUNKS: ${chunkNodes.size} ===")


        //sliding window
        setProgressDialogText("Creating chunks...")
        val chunks = SlidingWindowChunker.createSlidingChunks(
            docText = text,
            chunkSize = 128,
            overlap = 20
        )

        Log.d("ChunkDebug", "=== CHUNK HASIL SLIDING WINDOW ===")
        chunks.forEachIndexed { index, chunkText ->
            Log.d(
                "ChunkDebug",
                "Chunk $index (size=${chunkText.length}, parent=${"ROOT"}): ${chunkText}..."
            )
        }
        Log.d("ChunkDebug", "=== TOTAL CHUNKS: ${chunks.size} ===")

        setProgressDialogText("Adding chunks to database...")
        val size = chunks.size
        chunks.forEachIndexed { index, chunkText ->
            setProgressDialogText("Added ${index + 1}/$size chunk(s) to database...")

            val embedding = sentenceEncoder.encodeText(chunkText)

            chunksDB.addChunk(
                Chunk(
                    docId = newDocId,
                    docFileName = fileName,
                    chunkData = chunkText,
                    chunkEmbedding = embedding,
                )
            )
        }

    }

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

        setProgressDialogText("Adding chunks to database...")

        val size = chunks.size
        chunks.forEachIndexed { index, chunkJson ->
            setProgressDialogText("Added ${index + 1}/$size chunk(s) to database...")

            val embedding = sentenceEncoder.encodeText(chunkJson.chunk)

            chunksDB.addChunk(
                Chunk(
                    docId = newDocId,
                    docFileName = fileName,
                    chunkData = chunkJson.chunk,
                    chunkEmbedding = embedding,
                    chunkSize = chunkJson.chunk.length,
                    chunkUuid = UUID.randomUUID().toString(),
                    parentChunkId = null
                )
            )
        }

        Log.d("ChunkDebug", "=== CHUNK From Assets JSON ===")
        chunks.forEachIndexed { index, chunkJson ->
            Log.d(
                "ChunkDebug",
                "Chunk ${index + 1} (size=${chunkJson.chunk.length}, text: ${chunkJson.chunk})"
            )
        }
        Log.d("ChunkDebug", "=== TOTAL CHUNKS: ${chunks.size} ===")
    }

//    suspend fun addDocument(context: Context) = withContext(Dispatchers.IO) {
//        val fileName = "split_haji_1-1.pdf"
//        val documentType = Readers.DocumentType.PDF
//
//        // Skip jika sudah pernah di-chunk
//        if (chunksDB.hasChunks(fileName)) {
//            Log.i("DocsViewModel", "Chunks untuk dokumen sudah ada, skip.")
//            return@withContext
//        }
//
//        // Baca dokumen dari assets
//        val inputStream = context.assets.open(fileName)
//        val text = Readers.getReaderForDocType(documentType).readFromInputStream(inputStream)
//            ?: return@withContext
//
//        // (Opsional) Simpan dokumen ke DB kalau masih butuh catatan dokumentasi
//        documentsDB.addDocument(
//            Document(
//                docText = text,
//                docFileName = fileName,
//                docAddedTime = System.currentTimeMillis(),
//            ),
//        )
//
//        // Proses chunking
//        val chunks = WhiteSpaceSplitter.createChunks(text, chunkSize = 500, chunkOverlap = 50)
//        val size = chunks.size
//        chunks.forEachIndexed { index, chunkText ->
//            val embedding = sentenceEncoder.encodeText(chunkText)
//            chunksDB.addChunk(
//                Chunk(
//                    docFileName = fileName,
//                    chunkData = chunkText,
//                    chunkEmbedding = embedding,
//                )
//            )
//            Log.i("DocsViewModel", "Added ${index + 1}/$size chunk(s)")
//        }
//
//        Log.i("DocsViewModel", "Chunking dan penyimpanan selesai.")
//    }

    suspend fun addDocumentFromUrl(
        url: String,
        context: Context,
        onDownloadComplete: (Boolean) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val fileName = getFileNameFromURL(url)
                val file = File(context.cacheDir, fileName)

                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                // Determine the document type based on the file extension
                // Add handle for unknown types if supported
                val documentType =
                    when (fileName.substringAfterLast(".", "").lowercase()) {
                        "pdf" -> Readers.DocumentType.PDF
                        "docx" -> Readers.DocumentType.MS_DOCX
                        "doc" -> Readers.DocumentType.MS_DOCX
                        else -> Readers.DocumentType.UNKNOWN
                    }

                // Pass file to your document handling logic
                addDocument(file.inputStream(), fileName, documentType)

                withContext(Dispatchers.Main) {
                    onDownloadComplete(true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onDownloadComplete(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onDownloadComplete(false)
            }
        }
    }

    fun getAllDocuments(): Flow<List<Document>> = documentsDB.getAllDocuments()

    fun removeDocument(docId: Long) {
        documentsDB.removeDocument(docId)
        chunksDB.removeChunks(docId)
    }

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
