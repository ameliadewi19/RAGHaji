package com.ml.shubham0204.docqa.domain.retrievers

import android.content.Context
import android.util.Log
import com.ml.shubham0204.docqa.data.ChunksDB
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.ByteBuffersDirectory

object LuceneIndexer {
    private var indexInitialized = false
    private var indexDirectory: ByteBuffersDirectory? = null
    private var indexSearcherInternal: IndexSearcher? = null

    val indexSearcher: IndexSearcher?
        get() = indexSearcherInternal

    // Method untuk menginisialisasi index menggunakan chunks yang diambil dari ChunksDB
    fun initializeLuceneIndex(context: Context, chunksDB: ChunksDB) {
        if (indexInitialized) {
            Log.d("LuceneInit", "Index already initialized. Skipping...")
            return
        }

        val analyzer = StandardAnalyzer()
        val config = IndexWriterConfig(analyzer)

        indexDirectory = ByteBuffersDirectory()
        val writer = IndexWriter(indexDirectory!!, config)

        val chunkDocuments = chunksDB.getAllChunk() // Mengambil semua chunk dari ChunksDB
        Log.d("LuceneInit", "Indexing ${chunkDocuments.size} chunks")

        chunkDocuments.forEach { chunk ->
            val doc = Document().apply {
                add(StringField("id", chunk.chunkId.toString(), Field.Store.YES))
                add(TextField("content", chunk.chunkData, Field.Store.YES))
            }
            writer.addDocument(doc)
        }

        writer.close()

        val reader = DirectoryReader.open(indexDirectory!!)
        indexSearcherInternal = IndexSearcher(reader)

        indexInitialized = true
        Log.d("LuceneInit", "Lucene index initialized successfully.")
    }

    // Optional: Fungsikan untuk memastikan index sudah ada sebelum digunakan
    fun isIndexInitialized(): Boolean {
        return indexInitialized
    }
}
