package com.ml.shubham0204.docqa.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.koin.core.annotation.Single
import kotlin.math.ln

@Single
class ChunksDB {
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)

    private val invertedIndex = mutableMapOf<String, MutableSet<Long>>()
    private var indexBuilt = false

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

    fun getSimilarChunksBM25Optimized(
        context: Context,
        query: String,
        n: Int = 5
    ): List<Pair<Float, Chunk>> {

        val stopwords = loadStopwordsFromAssets(context)

        Log.d("BM25", "📥 Query masuk: $query")

        val queryTerms = query
            .lowercase()
            .replace("[^\\p{L}\\p{Nd}\\s]".toRegex(), "") // Hapus semua tanda baca
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it !in stopwords }

        Log.d("BM25", "🔍 Query terms: $queryTerms")

        // Dapatkan ID dokumen untuk setiap term
        val termDocIds = queryTerms.mapNotNull { term ->
            invertedIndex[term]  // Ambil ID dokumen yang mengandung term tersebut
        }

        // Menampilkan ID dokumen per term
        queryTerms.forEachIndexed { index, term ->
            Log.d("BM25", "Term: '${queryTerms[index]}', Found IDs: ${termDocIds.getOrNull(index)}")
        }

        // Hitung frekuensi ID dokumen di seluruh query terms
        val docIdFrequency = mutableMapOf<Long, Int>()
        termDocIds.flatten().forEach { docId ->
            docIdFrequency[docId] = docIdFrequency.getOrDefault(docId, 0) + 1
        }

        // ✅ Tampilkan frekuensi kemunculan setiap chunkId
        Log.d("BM25", "📈 Frekuensi kemunculan setiap chunkId:")
        docIdFrequency.entries
            .sortedByDescending { it.value }
            .forEach { (docId, count) ->
                Log.d("BM25", "chunkId: $docId muncul: ${count}x")
            }

        // ✅ Tampilkan chunkId yang muncul di SEMUA query terms
        val totalQueryTerms = queryTerms.size
        val allMatchedChunks = docIdFrequency.filter { it.value == totalQueryTerms }

        Log.d("BM25", "✅ Chunk yang muncul di SEMUA query terms:")
        if (allMatchedChunks.isEmpty()) {
            Log.d("BM25", "❌ Tidak ada chunkId yang muncul di semua query terms.")
        } else {
            allMatchedChunks.forEach { (docId, count) ->
                Log.d("BM25", "chunkId: $docId muncul: ${count}x")
            }
        }

        // Pilih dokumen yang muncul paling banyak (misalnya minimal 2 kali)
        val commonDocIds = docIdFrequency
            .filter { it.value >= 2 } // Ambil yang muncul di lebih dari satu query term
            .keys
            .toMutableSet()

        // Jika tidak ada interseksi yang cukup, cari ID dengan kecocokan terbesar
        if (commonDocIds.isEmpty()) {
            // Ambil ID dokumen yang paling banyak kecocokannya
            commonDocIds.addAll(
                docIdFrequency.entries
                    .sortedByDescending { it.value }
                    .take(n) // Ambil sebanyak n dokumen
                    .map { it.key }
            )
        }

        Log.d("BM25", "🔍 Dokumen yang dipilih: ${commonDocIds}")

        // Ambil chunks berdasarkan ID yang ada dalam interseksi
        val matchedChunks = chunksBox.get(commonDocIds.toLongArray())
        Log.d("BM25", "📦 Chunk yang cocok: ${matchedChunks.size}")

        // Hitung rata-rata panjang dokumen untuk chunks yang cocok
        val avgDocLength = matchedChunks.map {
            it.chunkData.split("\\s+".toRegex()).size
        }.average().toFloat()
        Log.d("BM25", "📊 Rata-rata panjang dokumen: $avgDocLength")

        // Hitung skor BM25 untuk setiap chunk dan filter berdasarkan skor minimal
        val scoredChunks = matchedChunks.map { chunk ->
            val score = calculateBM25Score(queryTerms, chunk, avgDocLength)
            Pair(score, chunk)
        }.filter { it.first > 0.1f } // Buang skor terlalu kecil

        // Ambil top n chunks berdasarkan skor BM25
        val topChunks = scoredChunks
            .sortedByDescending { it.first }
            .take(n)

        Log.d("BM25", "🏆 Top ${topChunks.size} chunks dikembalikan.")
        return topChunks
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
//            Log.d("BM25", "Term: '${queryTerms[index]}', Found IDs: ${termDocIds[index]}")
//        }
//
//        // Temukan interseksi ID dokumen yang cocok di semua query terms
//        var commonDocIds = termDocIds.reduceOrNull { acc, ids -> acc.intersect(ids).toMutableSet() }
//
//        // Log interseksi
//        Log.d("BM25", "🔍 Interseksi dokumen untuk query terms: ${commonDocIds ?: "Tidak ada interseksi"}")
//
//        // Jika interseksi kosong, cari ID dokumen yang paling banyak kecocokannya
//        if (commonDocIds == null || commonDocIds.isEmpty()) {
//            // Ambil ID dokumen yang paling banyak kecocokannya
//            val docIdFrequency = mutableMapOf<Long, Int>()
//            termDocIds.flatten().forEach { docId ->
//                docIdFrequency[docId] = docIdFrequency.getOrDefault(docId, 0) + 1
//            }
//            // Urutkan berdasarkan jumlah term yang cocok
//            commonDocIds = docIdFrequency.entries
//                .sortedByDescending { it.value }
//                .take(n) // Ambil sebanyak n dokumen
//                .map { it.key }
//                .toMutableSet()
//        }
//
//        Log.d("BM25", "🧠 Total matched chunk IDs setelah interseksi: ${commonDocIds.size}")
//        if (commonDocIds.isEmpty()) {
//            Log.d("BM25", "❌ Tidak ditemukan chunk yang cocok.")
//            return emptyList()
//        }
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
//        val matchedIds = queryTerms
//            .mapNotNull { term ->
//                val ids = invertedIndex[term]
//                Log.d("BM25", "Term: '$term', Found IDs: $ids")
//                ids
//            }
//            .flatten()
//            .toSet()
//
//        Log.d("BM25", "🧠 Total matched chunk IDs: ${matchedIds.size}")
//        if (matchedIds.isEmpty()) {
//            Log.d("BM25", "❌ Tidak ditemukan chunk yang cocok.")
//            return emptyList()
//        }
//
//        val matchedChunks = chunksBox.get(matchedIds.toLongArray())
//        Log.d("BM25", "📦 Chunk yang cocok: ${matchedChunks.size}")
//
//        val avgDocLength = matchedChunks.map {
//            it.chunkData.split("\\s+".toRegex()).size
//        }.average().toFloat()
//        Log.d("BM25", "📊 Rata-rata panjang dokumen: $avgDocLength")
//
//        val scoredChunks = matchedChunks.map { chunk ->
//            val score = calculateBM25Score(queryTerms, chunk, avgDocLength)
//            Pair(score, chunk)
//        }.filter { it.first > 0.1f } // Buang skor terlalu kecil
//
//        val topChunks = scoredChunks
//            .sortedByDescending { it.first }
//            .take(n)
//
//        Log.d("BM25", "🏆 Top ${topChunks.size} chunks dikembalikan.")
//        return topChunks
//    }

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

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Buang karakter selain huruf/angka
            .split("\\s+".toRegex())           // Pecah jadi token berdasarkan spasi
            .filter { it.isNotBlank() }        // Buang token kosong
    }

    fun buildInvertedIndex() {
        if (indexBuilt) {
            Log.d("InvertedIndex", "📌 Index sudah dibangun, skip.")
            return
        }

        Log.d("InvertedIndex", "🚀 Memulai pembangunan index...")
        invertedIndex.clear()

        val allChunks = chunksBox.all
        Log.d("InvertedIndex", "📦 Jumlah chunk: ${allChunks.size}")

        allChunks.forEachIndexed { index, chunk ->
            val tokens = tokenize(chunk.chunkData)
            for (token in tokens) {
                invertedIndex.getOrPut(token) { mutableSetOf() }.add(chunk.chunkId)
            }

            // Tampilkan log progres tiap 100 chunk
            if (index % 100 == 0) {
                Log.d("InvertedIndex", "🔄 Memproses chunk ke-$index: ID=${chunk.chunkId}")
            }
        }

        indexBuilt = true
        Log.d("InvertedIndex", "✅ Pembangunan index selesai.")
    }

    fun searchChunksByTokens(query: String, topN: Int = 5, useAnd: Boolean = false): List<Pair<Float, Chunk>> {
        buildInvertedIndex()

        val tokens = tokenize(query)
        val idSets = tokens.mapNotNull { invertedIndex[it] }

        val matchedIds: Set<Long> = when {
            idSets.isEmpty() -> emptySet()
            useAnd -> idSets.reduce { acc, set -> acc.intersect(set) as MutableSet<Long> }
            else -> idSets.flatten().toSet()
        }

        // Ambil semua Chunk yang cocok
        val matchedChunks = chunksBox.get(matchedIds.toLongArray())

        // Hitung skor (jumlah token yang cocok)
        val scored = matchedChunks.map { chunk ->
            val chunkTokens = tokenize(chunk.chunkData)
            val common = tokens.intersect(chunkTokens)
            val score = common.size.toFloat() / tokens.size // normalized match
            Pair(score, chunk)
        }

        return scored.sortedByDescending { it.first }.take(topN)
    }


}
