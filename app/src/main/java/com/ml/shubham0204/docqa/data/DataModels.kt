package com.ml.shubham0204.docqa.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.UUID

@Entity
data class Chunk(
    @Id var chunkId: Long = 0,
    @Index var docId: Long = 0,
    var docFileName: String = "",
    var chunkData: String = "",
    @HnswIndex(dimensions = 384) var chunkEmbedding: FloatArray = floatArrayOf(),
)

@Entity
data class ChunkNode(
    @Id var localId: Long = 0,
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val size: Int,
    val parentId: String? = null
)

@Entity
data class Document(
    @Id var docId: Long = 0,
    var docText: String = "",
    var docFileName: String = "",
    var docAddedTime: Long = 0,
)

data class RetrievedContext(
    val fileName: String,
    val context: String,
)
