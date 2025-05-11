package com.ml.shubham0204.docqa.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ml.shubham0204.docqa.domain.retrievers.LuceneIndexer
import org.koin.core.annotation.Single
import kotlin.math.ln

// lucene
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.similarities.BM25Similarity
import org.json.JSONObject


@Single
class ChunksDB {
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)

//    private val invertedIndex = mutableMapOf<String, MutableSet<Long>>()
//    private var indexBuilt = false

    fun addChunk(chunk: Chunk) {
        chunksBox.put(chunk)
    }

    fun getAllChunk(): List<Chunk> {
        return chunksBox.all
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

//    fun getSimilarChunksLuceneOptimized(
//        context: Context,
//        query: String,
//        n: Int = 5
//    ): List<Pair<Float, Chunk>> {
//        // Pastikan LuceneIndexer sudah terinisialisasi dengan memeriksa statusnya
//        if (!LuceneIndexer.isIndexInitialized()) {
//            Log.e("LuceneOptimized", "Lucene index not initialized. Make sure to call initializeLuceneIndex() first.")
//            return emptyList()
//        }
//
//        val stopwords = loadStopwordsFromAssets(context)
//        val queryTerms = query
//            .lowercase()
//            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "")
//            .split("\\s+".toRegex())
//            .filter { it.isNotBlank() && it !in stopwords }
//
//        val analyzer = StandardAnalyzer()
//        val queryParser = QueryParser("content", analyzer)
//        val luceneQuery = queryParser.parse(queryTerms.joinToString(" "))
//
//        val indexSearcher = LuceneIndexer.indexSearcher
//        if (indexSearcher == null) {
//            Log.e("LuceneOptimized", "IndexSearcher is null. Ensure it is initialized correctly in LuceneIndexer.")
//            return emptyList()
//        }
//
//        val hits = indexSearcher.search(luceneQuery, n)
//        val reader = indexSearcher.indexReader
//        val scoredChunks = mutableListOf<Pair<Float, Chunk>>()
//
//        for (hit in hits.scoreDocs) {
//            val doc = reader.document(hit.doc)
//            val chunkId = doc.get("id")?.toLongOrNull() ?: continue
//            val chunk = chunksBox.get(chunkId) ?: continue
//            scoredChunks.add(Pair(hit.score, chunk))
//            Log.d("LuceneOptimized", "Found chunk ID: $chunkId | Score: ${hit.score}")
//            Log.d("LuceneOptimized", "Chunk content: ${chunk.chunkData.take(500)}")
//        }
//
//        return scoredChunks
//    }

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


    fun getSimilarChunksSparse(
        context: Context,
        query: String,
        n: Int = 5
    ): List<Pair<Float, Chunk>> {
        if (!LuceneIndexer.isIndexInitialized()) {
            Log.e("LuceneOptimized", "Lucene index not initialized. Make sure to call initializeLuceneIndex() first.")
            return emptyList()
        }

        val stopwords = loadStopwordsFromAssets(context)
//        val thesaurus = loadThesaurusFromAssets(context)

        val queryTerms = query
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
        Log.d("LuceneOptimized", "Original query: $query")
        Log.d("LuceneOptimized", "Cleaned terms: ${queryTerms.joinToString(", ")}")
//        Log.d("LuceneOptimized", "Expanded query: $expandedTerms")

        val analyzer = StandardAnalyzer()
        val queryParser = QueryParser("content", analyzer)
        val luceneQuery = queryParser.parse(queryTerms.joinToString(" "))

        val indexSearcher = LuceneIndexer.indexSearcher
        if (indexSearcher == null) {
            Log.e("LuceneOptimized", "IndexSearcher is null. Ensure it is initialized correctly in LuceneIndexer.")
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

            Log.d("LuceneOptimized", "Found chunk ID: $chunkId | Score: ${hit.score}")
            Log.d("LuceneOptimized", "Chunk content: ${chunk.chunkText.take(500)}")
        }

        return scoredChunks
    }

    fun getHybridSimilarChunks(
        context: Context,
        query: String,
        queryEmbedding: FloatArray,
        n: Int = 5,
        lambda: Float = 0.8f
    ): List<Pair<Float, Chunk>> {
        if (!LuceneIndexer.isIndexInitialized()) {
            Log.e("Hybrid", "Lucene index not initialized.")
            return emptyList()
        }

        Log.d("Hybrid", "Starting retrieval for hybrid chunks.")

        // Retrieve BM25 and Dense results
        val bm25Results = getSimilarChunksSparse(context, query, 10)
        val denseResults = getSimilarChunks(queryEmbedding, 10)

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

        // Combine BM25 and Dense results into a unified set of all chunk IDs
        val allChunkIds = bm25Raw.keys.toSet() union denseRaw.keys.toSet()

        // Create a map for all chunk IDs with both BM25 and embedding scores (default 0 for missing scores)
        val allScores = allChunkIds.associateWith { chunkId ->
            val bm25Score = bm25Raw[chunkId] ?: getScoreFromSparseByChunkId(context, query, chunkId)
            val denseScore = denseRaw[chunkId] ?: getScoreFromEmbeddingByChunkId(chunkId, queryEmbedding)
            chunkId to Pair(bm25Score, denseScore)
        }


        // Log all the combined scores for both BM25 and Dense for every chunk ID
        allScores.forEach { (chunkId, scores) ->
            Log.d("Hybrid", "All Scores for ID $chunkId - BM25: ${scores.first}, Dense: ${scores.second}")
        }

        // Normalize BM25 and Dense scores separately
        fun normalizeScores(scores: Map<Long, Float>): Map<Long, Float> {
            val min = scores.values.minOrNull() ?: 0f
            val max = scores.values.maxOrNull() ?: 1f
            val range = if (max - min == 0f) 1f else max - min

            Log.d("Hybrid", "Normalizing scores. Min: $min, Max: $max, Range: $range")

            return scores.mapValues { (chunkId, score) ->
                val normalized = (score - min) / range
                Log.d("Hybrid", "Normalized - ID: $chunkId | Raw: $score | Normalized: $normalized")
                normalized
            }
        }

        // Normalized scores for BM25 and Dense separately
        val bm25Norm = normalizeScores(bm25Raw)
        val denseNorm = normalizeScores(denseRaw)

        // Calculate hybrid score by combining normalized BM25 and Dense scores
        val rescored = allScores.mapNotNull { (chunkId, pair) ->
            val bm25Score = bm25Norm[chunkId] ?: 0f
            val denseScore = denseNorm[chunkId] ?: 0f
            val chunk = bm25Results.find { it.second.chunkId == chunkId }?.second
                ?: denseResults.find { it.second.chunkId == chunkId }?.second

            chunk?.let {
                val hybridScore = lambda * denseScore + (1 - lambda) * bm25Score
                Log.d("HybridLucene", "Hybrid Score - ID: $chunkId | Dense: $denseScore | BM25: $bm25Score | Final: $hybridScore")
                Pair(hybridScore, chunk)
            }
        }

        // Sort the rescored results by hybrid score in descending order and take the top N
        val topResults = rescored.sortedByDescending { it.first }.take(n)

        // Log the top N results
        topResults.forEachIndexed { index, pair ->
            Log.d("HybridLucene", "Top[$index] - ID: ${pair.second.chunkId} | Hybrid Score: ${pair.first}")
        }

        return topResults
    }

    // Mendapatkan skor BM25 untuk ID tertentu dengan membuat ulang query hanya untuk dokumen itu
    fun getScoreFromSparseByChunkId(context: Context, query: String, chunkId: Long): Float {
        val stopwords = loadStopwordsFromAssets(context)
//        val thesaurus = loadThesaurusFromAssets(context)

        val queryTerms = query
            .lowercase()
            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it !in stopwords }

        val analyzer = StandardAnalyzer()
        val queryParser = QueryParser("content", analyzer)
        val luceneQuery = queryParser.parse(query)

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

//    fun getHybridSimilarChunks(
//        context: Context,
//        query: String,
//        queryEmbedding: FloatArray,
//        n: Int = 5,
//        lambda: Float = 0.8f // Bobot untuk model deep embeddings (MiniLM, SPECTER2)
//    ): List<Pair<Float, Chunk>> {
//        if (!LuceneIndexer.isIndexInitialized()) {
//            Log.e("HybridLuceneOptimized", "Lucene index not initialized.")
//            return emptyList()
//        }
//
//        // Step 1: Lucene BM25
//        Log.d("HybridLuceneOptimized", "Starting BM25 retrieval for query: $query")
//        val luceneResults = getSimilarChunksLuceneOptimized(context, query, n)
//        Log.d("HybridLuceneOptimized", "Lucene BM25 retrieval completed. Found ${luceneResults.size} results.")
//
//        // Step 2: Embedding-based nearest neighbors
//        Log.d("HybridLuceneOptimized", "Starting embedding-based retrieval.")
//        val embeddingResults = getSimilarChunks(queryEmbedding, n)
//        Log.d("HybridLuceneOptimized", "Embedding retrieval completed. Found ${embeddingResults.size} results.")
//
//        // Step 3: Gabungkan hasil BM25 dan Embedding (Hybrid)
//        val scoredChunks = mutableMapOf<Long, Pair<Float, Chunk>>()
//
//        // Gabungkan hasil dari Lucene (BM25)
//        Log.d("HybridLuceneOptimized", "Merging BM25 results into scoredChunks map.")
//        luceneResults.forEach {
//            val chunkId = it.second.chunkId // Ambil id dari chunk
//            val luceneScore = it.first
//            if (!scoredChunks.contains(chunkId)) {
//                scoredChunks[chunkId] = Pair(luceneScore, it.second)
//                Log.d("HybridLuceneOptimized", "Added BM25 result: Chunk ID: ${chunkId}, Score: $luceneScore")
//            }
//        }
//
//        // Gabungkan hasil dari Embedding-based retrieval
//        Log.d("HybridLuceneOptimized", "Merging embedding-based results into scoredChunks map.")
//        embeddingResults.forEach {
//            val chunkId = it.second.chunkId // Ambil id dari chunk
//            val embeddingScore = it.first
//            if (!scoredChunks.contains(chunkId)) {
//                scoredChunks[chunkId] = Pair(embeddingScore, it.second)
//                Log.d("HybridLuceneOptimized", "Added embedding result: Chunk ID: ${chunkId}, Score: $embeddingScore")
//            }
//        }
//
//        // Step 4: Hitung skor hybrid untuk setiap chunk
//        Log.d("HybridLuceneOptimized", "Calculating hybrid scores.")
//        val hybridResults = scoredChunks.values.map { (luceneScore, chunk) ->
//            val embeddingScore = embeddingResults.find { it.second.chunkId == chunk.chunkId }?.first ?: 0f
//            val hybridScore = lambda * luceneScore + (1 - lambda) * embeddingScore
//            Log.d("HybridLuceneOptimized", "Chunk ID: ${chunk.chunkId} | BM25 Score: $luceneScore | Embedding Score: $embeddingScore | Hybrid Score: $hybridScore")
//            Pair(hybridScore, chunk)
//        }
//
//        // Step 5: Urutkan berdasarkan skor gabungan
//        Log.d("HybridFinal", "Sorting hybrid results based on hybrid score.")
//        return hybridResults.sortedByDescending { it.first }.take(n).also {
//            Log.d("HybridFinal", "Top $n results:")
//            it.forEach { (score, chunk) ->
//                Log.d("HybridFinal", "Chunk ID: ${chunk.chunkId} | Score: $score | Content: ${chunk.chunkData.take(500)}")
//            }
//        }
//    }

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


//    fun getSimilarChunksBM25Optimized(
//        context: Context,
//        query: String,
//        n: Int = 5
//    ): List<Pair<Float, Chunk>> {
//
//        val stopwords = loadStopwordsFromAssets(context)
//
//        Log.d("BM25", "📥 Query masuk: $query")
//
//        val queryTerms = query
//            .lowercase()
//            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "") // Remove punctuation
//            .split("\\s+".toRegex())
//            .filter { it.isNotBlank() && it !in stopwords }
//
//        Log.d("BM25", "🔍 Query terms: $queryTerms")
//
//        // Initialize document limit per term
//        val initialLimit = 20
//
//        // Get document IDs for each term with initial limit
//        val termDocIds = queryTerms.mapNotNull { term ->
//            invertedIndex[term]?.take(initialLimit)  // Limit the number of documents per term
//        }
//
//        // Show document IDs per term
//        queryTerms.forEachIndexed { index, term ->
//            Log.d("BM25", "Term: '${queryTerms[index]}', Found IDs: ${termDocIds[index]}")
//        }
//
//        // Find the intersection of document IDs for all query terms
//        var commonDocIds = termDocIds
//            .reduceOrNull { acc, ids -> acc.intersect(ids).toList() } ?: emptyList()
//
//        Log.d("BM25", "🔍 Interseksi dokumen untuk query terms: ${commonDocIds.takeIf { it.isNotEmpty() } ?: "Tidak ada interseksi"}")
//
//        // If no common document IDs, try increasing the number of documents per term
//        if (commonDocIds.isEmpty()) {
//            Log.d("BM25", "❌ Tidak ada interseksi. Meningkatkan jumlah dokumen per term...")
//
//            val increasedLimit = 50  // Increase limit to 50 to try again
//            val increasedTermDocIds = queryTerms.mapNotNull { term ->
//                invertedIndex[term]?.take(increasedLimit)
//            }
//
//            // Find the intersection again with increased document limit
//            commonDocIds = increasedTermDocIds
//                .reduceOrNull { acc, ids -> acc.intersect(ids).toList() } ?: emptyList()
//
//            Log.d("BM25", "🔍 Interseksi dokumen setelah meningkatkan jumlah: ${commonDocIds.takeIf { it.isNotEmpty() } ?: "Tidak ada interseksi"}")
//        }
//
//        // If still empty, get documents based on frequency
//        if (commonDocIds.isEmpty()) {
//            Log.d("BM25", "❌ Tidak ada interseksi setelah peningkatan, mencoba frekuensi dokumen...")
//
//            // Get document IDs with the most matches
//            val docIdFrequency = mutableMapOf<Long, Int>()
//            termDocIds.flatten().forEach { docId ->
//                docIdFrequency[docId] = docIdFrequency.getOrDefault(docId, 0) + 1
//            }
//
//            // Sort by number of matches and get top n
//            commonDocIds = docIdFrequency.entries
//                .sortedByDescending { it.value }
//                .take(n)
//                .map { it.key }
//
//        }
//
//        Log.d("BM25", "🧠 Total matched chunk IDs setelah interseksi: ${commonDocIds.size}")
//        if (commonDocIds.isEmpty()) {
//            Log.d("BM25", "❌ Tidak ditemukan chunk yang cocok.")
//            return emptyList()
//        }
//
//        // Get chunks based on the document IDs in the intersection
//        val matchedChunks = chunksBox.get(commonDocIds.toLongArray())  // Convert List<Long> to LongArray
//        Log.d("BM25", "📦 Chunk yang cocok: ${matchedChunks.size}")
//
//        // Calculate the average document length for the matched chunks
//        val avgDocLength = matchedChunks.map {
//            it.chunkData.split("\\s+".toRegex()).size
//        }.average().toFloat()
//
//        Log.d("BM25", "📊 Rata-rata panjang dokumen: $avgDocLength")
//
//        // Calculate BM25 score for each chunk and filter by minimum score
//        val scoredChunks = matchedChunks.map { chunk ->
//            val score = calculateBM25Score(queryTerms, chunk, avgDocLength)
//            Pair(score, chunk)
//        }.filter { it.first > 0.1f } // Remove chunks with too low score
//
//        // Get top n chunks based on BM25 score
//        val topChunks = scoredChunks
//            .sortedByDescending { it.first }
//            .take(n)
//
//        Log.d("BM25", "🏆 Top ${topChunks.size} chunks dikembalikan.")
//        return topChunks
//    }

//    fun getSimilarChunksBM25Optimized(
//        context: Context,
//        query: String,
//        n: Int = 5
//    ): List<Pair<Float, Chunk>> {
//
//        val stopwords = loadStopwordsFromAssets(context)
//
//        Log.d("BM25", "📥 Query masuk: $query")
//
//        val queryTerms = query
//            .lowercase()
//            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "") // Hapus semua tanda baca
//            .split("\\s+".toRegex())
//            .filter { it.isNotBlank() && it !in stopwords }
//
//        Log.d("BM25", "🔍 Query terms: $queryTerms")
//
//        // Dapatkan ID dokumen untuk setiap term
//        val termDocIds = queryTerms.mapNotNull { term ->
//            invertedIndex[term]  // Ambil ID dokumen yang mengandung term tersebut
//        }
//
//        // Menampilkan ID dokumen per term
//        queryTerms.forEachIndexed { index, term ->
//            Log.d("BM25", "Term: '${queryTerms[index]}', Found IDs: ${termDocIds.getOrNull(index)}")
//        }
//
//        // Hitung frekuensi ID dokumen di seluruh query terms
//        val docIdFrequency = mutableMapOf<Long, Int>()
//        termDocIds.flatten().forEach { docId ->
//            docIdFrequency[docId] = docIdFrequency.getOrDefault(docId, 0) + 1
//        }
//
//        // ✅ Tampilkan frekuensi kemunculan setiap chunkId
//        Log.d("BM25", "📈 Frekuensi kemunculan setiap chunkId:")
//        docIdFrequency.entries
//            .sortedByDescending { it.value }
//            .forEach { (docId, count) ->
//                Log.d("BM25", "chunkId: $docId muncul: ${count}x")
//            }
//
//        // ✅ Tampilkan chunkId yang muncul di SEMUA query terms
//        val totalQueryTerms = queryTerms.size
//        val allMatchedChunks = docIdFrequency.filter { it.value == totalQueryTerms }
//
//        Log.d("BM25", "✅ Chunk yang muncul di SEMUA query terms:")
//        if (allMatchedChunks.isEmpty()) {
//            Log.d("BM25", "❌ Tidak ada chunkId yang muncul di semua query terms.")
//        } else {
//            allMatchedChunks.forEach { (docId, count) ->
//                Log.d("BM25", "chunkId: $docId muncul: ${count}x")
//            }
//        }
//
//        // Pilih dokumen yang muncul paling banyak (misalnya minimal 2 kali)
//        val commonDocIds = docIdFrequency
//            .filter { it.value >= 2 } // Ambil yang muncul di lebih dari satu query term
//            .keys
//            .toMutableSet()
//
//        // Jika tidak ada interseksi yang cukup, cari ID dengan kecocokan terbesar
//        if (commonDocIds.isEmpty()) {
//            // Ambil ID dokumen yang paling banyak kecocokannya
//            commonDocIds.addAll(
//                docIdFrequency.entries
//                    .sortedByDescending { it.value }
//                    .take(n) // Ambil sebanyak n dokumen
//                    .map { it.key }
//            )
//        }
//
//        Log.d("BM25", "🔍 Dokumen yang dipilih: ${commonDocIds}")
//
//        // Ambil chunks berdasarkan ID yang ada dalam interseksi
//        val matchedChunks = chunksBox.get(commonDocIds.toLongArray())
//        Log.d("BM25", "📦 Chunk yang cocok: ${matchedChunks.size}")
//
//        // Hitung rata-rata panjang dokumen untuk chunks yang cocok
//        val avgDocLength = matchedChunks.map {
//            it.chunkData.split("\\s+".toRegex()).size
//        }.average().toFloat()
//        Log.d("BM25", "📊 Rata-rata panjang dokumen: $avgDocLength")
//
//        // Hitung skor BM25 untuk setiap chunk dan filter berdasarkan skor minimal
//        val scoredChunks = matchedChunks.map { chunk ->
//            val score = calculateBM25Score(queryTerms, chunk, avgDocLength)
//            Pair(score, chunk)
//        }.filter { it.first > 0.1f } // Buang skor terlalu kecil
//
//        // Ambil top n chunks berdasarkan skor BM25
//        val topChunks = scoredChunks
//            .sortedByDescending { it.first }
//            .take(n)
//
//        Log.d("BM25", "🏆 Top ${topChunks.size} chunks dikembalikan.")
//        return topChunks
//    }

//    fun calculateBM25Score(
//        queryTerms: List<String>,
//        chunk: Chunk,
//        avgDocLength: Float,
//        k1: Float = 1.5f,
//        b: Float = 0.75f
//    ): Float {
//        val docTerms = chunk.chunkData.lowercase().split("\\s+".toRegex())
//        val docLength = docTerms.size.toFloat()
//        val termFrequencies = docTerms.groupingBy { it }.eachCount()
//
//        var score = 0f
//        for (term in queryTerms) {
//            val df = chunksBox.all.count {
//                it.chunkData.contains(term, ignoreCase = true)
//            } + 1 // smoothing
//
//            val idf = ln((chunksBox.count().toFloat() - df + 0.5f) / (df + 0.5f) + 1) // +1 biar idf positif
//            val tf = termFrequencies[term] ?: 0
//            val numerator = tf * (k1 + 1)
//            val denominator = tf + k1 * (1 - b + b * (docLength / avgDocLength))
//
//            score += idf * (numerator / denominator)
//        }
//
//        return score
//    }


//    private fun tokenize(text: String): List<String> {
//        return text.lowercase()
//            .replace(Regex("[^a-z0-9\\s]"), "") // Buang karakter selain huruf/angka
//            .split("\\s+".toRegex())           // Pecah jadi token berdasarkan spasi
//            .filter { it.isNotBlank() }        // Buang token kosong
//    }

//    fun buildInvertedIndex() {
//        if (indexBuilt) {
//            Log.d("InvertedIndex", "📌 Index sudah dibangun, skip.")
//            return
//        }
//
//        Log.d("InvertedIndex", "🚀 Memulai pembangunan index...")
//        invertedIndex.clear()
//
//        val allChunks = chunksBox.all
//        Log.d("InvertedIndex", "📦 Jumlah chunk: ${allChunks.size}")
//
//        allChunks.forEachIndexed { index, chunk ->
//            val tokens = tokenize(chunk.chunkData)
//            for (token in tokens) {
//                invertedIndex.getOrPut(token) { mutableSetOf() }.add(chunk.chunkId)
//            }
//
//            // Tampilkan log progres tiap 100 chunk
//            if (index % 100 == 0) {
//                Log.d("InvertedIndex", "🔄 Memproses chunk ke-$index: ID=${chunk.chunkId}")
//            }
//        }
//
//        indexBuilt = true
//        Log.d("InvertedIndex", "✅ Pembangunan index selesai.")
//    }

}
