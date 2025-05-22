package com.ml.shubham0204.docqa.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.query_expander.QueryExpander
import com.ml.shubham0204.docqa.domain.retrievers.LuceneIndexer
import com.ml.shubham0204.docqa.ui.screens.chat.ChatViewModel
import org.koin.core.annotation.Single
import kotlin.math.ln

// lucene
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.similarities.BM25Similarity
import org.json.JSONObject
import kotlin.math.exp


@Single
class ChunksDB(
    private val sentenceEncoder: SentenceEmbeddingProvider
) {
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)
    private val queryExpander = QueryExpander()

    fun addChunk(chunk: Chunk) {
        chunksBox.put(chunk)
    }

    fun getAllChunk(): List<Chunk> {
        return chunksBox.all
    }

    fun loadStopwordsFromAssets(context: Context): Set<String> {
        val stopwords = mutableSetOf<String>()
        context.assets.open("stopwords.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val word = line.trim()
                if (word.isNotEmpty()) {
                    stopwords.add(word)
                }
            }
        }
        return stopwords
    }

    fun loadThesaurusFromAssets(context: Context): Map<String, List<String>> {
        val jsonString = context.assets.open("dict.json").bufferedReader().use { it.readText() }
        val thesaurusMap = mutableMapOf<String, List<String>>()

        val jsonObject = JSONObject(jsonString)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val entry = jsonObject.getJSONObject(word)
            val synonymsJsonArray = entry.optJSONArray("sinonim") ?: continue
            val synonyms = mutableListOf<String>()
            for (i in 0 until synonymsJsonArray.length()) {
                synonyms.add(synonymsJsonArray.getString(i))
            }
            thesaurusMap[word] = synonyms
        }
        return thesaurusMap
    }

    fun getSimilarChunks(
        query: String,
        n: Int = 5,
    ): List<Pair<Float, Chunk>> {
        val expandedQuery = queryExpander.expandQuery(query)
        Log.d("EXPANDED QUERY", "$expandedQuery")

//        val queryEmbedding = sentenceEncoder.encodeText(expandedQuery)
        val queryEmbedding = sentenceEncoder.encodeText("query: $expandedQuery")
        /*
        Use maxResultCount to set the maximum number of objects to return by the ANN condition.
        Hint: it can also be used as the "ef" HNSW parameter to increase the search quality in combination
        with a query limit. For example, use maxResultCount of 100 with a Query limit of 10 to have 10 results
        that are of potentially better quality than just passing in 10 for maxResultCount
        (quality/performance tradeoff).
         */
        val results = chunksBox
            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, 50))
            .build()
            .find()

        results.forEachIndexed { i, chunk ->
            Log.d("CHUNK_RESULT", "Chunk #$i: ${chunk.chunkText.take(200)}...") // batasi 200 karakter
        }

        val scoredResults = results.map {
            val sim = cosineSimilarity(queryEmbedding, it.chunkEmbedding)
            Pair(sim, it)
        }.sortedByDescending { it.first }
            .take(n)

        return scoredResults

//        return chunksBox
//            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, 25))
//            .build()
//            .findWithScores()
//            .map { Pair(it.score.toFloat(), it.get()) }
//            .subList(0, n)
    }

    fun getSimilarChunksSparse(
        context: Context,
        query: String,
        n: Int = 5
    ): List<Pair<Float, Chunk>> {
        val expandedQuery = queryExpander.expandQuery(query)
        Log.d("EXPANDED QUERY", "$expandedQuery")

        if (!LuceneIndexer.isIndexInitialized()) {
            Log.e("Sparse", "Lucene index not initialized. Make sure to call initializeLuceneIndex() first.")
            return emptyList()
        }

        val stopwords = loadStopwordsFromAssets(context)
//        val thesaurus = loadThesaurusFromAssets(context)

        val queryTerms = expandedQuery
            .lowercase()
            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it !in stopwords }
//
//        val expandedTerms = mutableSetOf<String>()
//        for (term in queryTerms) {
//            expandedTerms.add(term)
//            val synonyms = thesaurus[term]
//            if (synonyms != null) {
//                expandedTerms.addAll(synonyms)
//            }
//        }

        // Log term dan query yang telah dibersihkan
        Log.d("Sparse", "Original query: $expandedQuery")
        Log.d("Sparse", "Cleaned terms: ${queryTerms.joinToString(", ")}")
//        Log.d("LuceneOptimized", "Expanded query: $expandedTerms")

        val analyzer = StandardAnalyzer()
        val queryParser = QueryParser("content", analyzer)
        val luceneQuery = queryParser.parse(queryTerms.joinToString(" "))

        val indexSearcher = LuceneIndexer.indexSearcher
        if (indexSearcher == null) {
            Log.e("Sparse", "IndexSearcher is null. Ensure it is initialized correctly in LuceneIndexer.")
            return emptyList()
        }

        // Set similarity BM25
        val k1 = 1.2f
        val b = 0.75f
        indexSearcher.similarity = BM25Similarity(k1, b)

        val hits = indexSearcher.search(luceneQuery, n)
        val reader = indexSearcher.indexReader
        val scoredChunks = mutableListOf<Pair<Float, Chunk>>()

        for (hit in hits.scoreDocs) {
            val doc = reader.document(hit.doc)
            val chunkId = doc.get("id")?.toLongOrNull() ?: continue
            val chunk = chunksBox.get(chunkId) ?: continue
            scoredChunks.add(Pair(hit.score, chunk))

            Log.d("Sparse", "Found chunk ID: $chunkId | Score: ${hit.score}")
            Log.d("Sparse", "Chunk content: ${chunk.chunkText.take(1000)}")
        }

        return scoredChunks
    }

    // Normalize BM25 and Dense scores separately
//    fun normalizeScores(scores: Map<Long, Float>): Map<Long, Float> {
//        val min = scores.values.minOrNull() ?: 0f
//        val max = scores.values.maxOrNull() ?: 1f
//        val range = if (max - min == 0f) 1f else max - min
//
//        Log.d("Hybrid", "Normalizing scores. Min: $min, Max: $max, Range: $range")
//
//        return scores.mapValues { (chunkId, score) ->
//            val normalized = (score - min) / range
//            Log.d("Hybrid", "Normalized - ID: $chunkId | Raw: $score | Normalized: $normalized")
//            normalized
//        }
//    }

    fun getHybridSimilarChunks(
        context: Context,
        query: String,
        n: Int = 5,
        lambda: Float = 0.5f
    ): List<Pair<Float, Chunk>> {
        if (!LuceneIndexer.isIndexInitialized()) {
            Log.e("Hybrid", "Lucene index not initialized.")
            return emptyList()
        }

        Log.d("Hybrid", "Query: $query")
        Log.d("Hybrid", "Starting retrieval for hybrid chunks.")

        // Retrieve BM25 and Dense results
        val bm25Results = getSimilarChunksSparse(context, query, 10)
        val denseResults = getSimilarChunks(query, 10)

        Log.d("Hybrid", "BM25 result count: ${bm25Results.size}")
        Log.d("Hybrid", "ANN (dense) result count: ${denseResults.size}")

        // Mapping BM25 and Dense results into chunkId to score mapping
        val bm25Raw = bm25Results.associate {
            Log.d("Hybrid", "BM25 raw - ID: ${it.second.chunkId} | Score: ${it.first}")
            it.second.chunkId to it.first
        }

        val denseRaw = denseResults.associate {
            Log.d("Hybrid", "Dense raw - ID: ${it.second.chunkId} | Score: ${it.first}")
            it.second.chunkId to it.first
        }

        // Combine all chunk IDs from both sources
        val allChunkIds = bm25Raw.keys union denseRaw.keys

        // Expand query and get embedding
        val expandedQuery = queryExpander.expandQuery(query)
        val queryEmbedding = sentenceEncoder.encodeText(expandedQuery)

        // Calculate hybrid scores
        val rescored = allChunkIds.mapNotNull { chunkId ->
            val bm25Score = bm25Raw[chunkId] ?: getScoreFromSparseByChunkId(context, expandedQuery, chunkId)
            val denseScore = denseRaw[chunkId] ?: getScoreFromEmbeddingByChunkId(chunkId, queryEmbedding)

            val chunk = bm25Results.find { it.second.chunkId == chunkId }?.second
                ?: denseResults.find { it.second.chunkId == chunkId }?.second

            chunk?.let {
                val hybridScore = lambda * denseScore + (1 - lambda) * bm25Score
                Log.d("Hybrid", "Hybrid Score - ID: $chunkId | Dense: $denseScore | BM25: $bm25Score | Final: $hybridScore")
                Pair(hybridScore, it)
            }
        }

        // Sort by hybrid score descending and take top N
        val topResults = rescored.sortedByDescending { it.first }.take(n)

        topResults.forEachIndexed { index, pair ->
            Log.d("Hybrid", "Top[$index] - ID: ${pair.second.chunkId} | Hybrid Score: ${pair.first}")
        }

        return topResults
    }

    // Mendapatkan skor BM25 untuk ID tertentu dengan membuat ulang query hanya untuk dokumen itu
    fun getScoreFromSparseByChunkId(context: Context, query: String, chunkId: Long): Float {
        val stopwords = loadStopwordsFromAssets(context)

        val queryTerms = query
            .lowercase()
            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it !in stopwords }

        val analyzer = StandardAnalyzer()
        val queryParser = QueryParser("content", analyzer)
        val luceneQuery = queryParser.parse(queryTerms.joinToString(" "))

        val indexSearcher = LuceneIndexer.indexSearcher ?: return 0f
        indexSearcher.similarity = BM25Similarity(1.2f, 0.75f)

        val hits = indexSearcher.search(luceneQuery, 100)
        for (hit in hits.scoreDocs) {
            val doc = indexSearcher.indexReader.document(hit.doc)
            val docId = doc.get("id")?.toLongOrNull()
            if (docId == chunkId) return hit.score
        }
        return 0f
    }

    // Fungsi Cosine Similarity
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = Math.sqrt(a.sumOf { (it * it).toDouble() })
        val normB = Math.sqrt(b.sumOf { (it * it).toDouble() })
        return if (normA == 0.0 || normB == 0.0) 0f else (dotProduct / (normA * normB)).toFloat()
    }

    // Mendapatkan skor cosine similarity untuk embedding ke dokumen tertentu
    fun getScoreFromEmbeddingByChunkId(chunkId: Long, queryEmbedding: FloatArray): Float {
        val chunk = chunksBox.get(chunkId) ?: return 0f
        val docEmbedding = chunk.chunkEmbedding
        return cosineSimilarity(queryEmbedding, docEmbedding)
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
