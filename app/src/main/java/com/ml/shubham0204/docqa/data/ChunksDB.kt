package com.ml.shubham0204.docqa.data

import android.util.Log
import android.widget.Toast
import org.koin.core.annotation.Single
import kotlin.math.ln

@Single
class ChunksDB {
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)

    fun addChunk(chunk: Chunk) {
        chunksBox.put(chunk)
    }

    fun getSimilarChunks(
        queryEmbedding: FloatArray,
        n: Int = 5,
    ): List<Pair<Float, Chunk>> {
        /*
        Use maxResultCount to set the maximum number of objects to return by the ANN condition.
        Hint: it can also be used as the "ef" HNSW parameter to increase the search quality in combination
        with a query limit. For example, use maxResultCount of 100 with a Query limit of 10 to have 10 results
        that are of potentially better quality than just passing in 10 for maxResultCount
        (quality/performance tradeoff).
         */
        return chunksBox
            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, 25))
            .build()
            .findWithScores()
            .map { Pair(it.score.toFloat(), it.get()) }
            .subList(0, n)
    }

    fun getSimilarChunksBM25(
        query: String,
        n: Int = 5
    ): List<Pair<Float, Chunk>> {
        val TAG = "BM25"
        val queryTerms = query.lowercase().split("\\s+".toRegex())
        Log.d(TAG, "🔍 Query Terms: $queryTerms")

        // Pre-calculate average document length
        val allChunks = chunksBox.all
        val avgDocLength = allChunks.map {
            it.chunkData.split("\\s+".toRegex()).size
        }.average().toFloat()
        Log.d(TAG, "📊 Average Document Length: $avgDocLength")

        val scoredChunks = allChunks
            .map { chunk ->
                val score = calculateBM25Score(queryTerms, chunk, avgDocLength)
                Log.d(TAG, "📄 Chunk ID: ${chunk.chunkId}, Score: $score, Preview: ${chunk.chunkData.take(60)}...")
                Pair(score, chunk)
            }
            .sortedByDescending { it.first }
            .take(n)

        Log.d(TAG, "✅ Top $n chunks:")
        scoredChunks.forEachIndexed { index, (score, chunk) ->
            Log.d(TAG, "${index + 1}. [Score: ${"%.4f".format(score)}] ${chunk.chunkData.take(80)}...")
        }

        return scoredChunks
    }

    fun calculateBM25Score(
        queryTerms: List<String>,
        chunk: Chunk,
        avgDocLength: Float,
        k1: Float = 1.5f,
        b: Float = 0.75f
    ): Float {
        val docTerms = chunk.chunkData.lowercase().split("\\s+".toRegex())
        val docLength = docTerms.size.toFloat()
        val termFrequencies = docTerms.groupingBy { it }.eachCount()

        var score = 0f
        for (term in queryTerms) {
            val df = chunksBox.all.count {
                it.chunkData.contains(term, ignoreCase = true)
            } + 1 // smoothing

            val idf = ln((chunksBox.count().toFloat() - df + 0.5f) / (df + 0.5f) + 1) // +1 biar idf positif
            val tf = termFrequencies[term] ?: 0
            val numerator = tf * (k1 + 1)
            val denominator = tf + k1 * (1 - b + b * (docLength / avgDocLength))

            score += idf * (numerator / denominator)
        }

        return score
    }

    fun removeChunks(docId: Long) {
        chunksBox.removeByIds(
            chunksBox
                .query(Chunk_.docId.equal(docId))
                .build()
                .findIds()
                .toList(),
        )
    }

    fun clearChunks() {
        chunksBox.removeAll()
//        Toast.makeText(this, "Semua chunks telah dihapus", Toast.LENGTH_SHORT).show()
    }

}
