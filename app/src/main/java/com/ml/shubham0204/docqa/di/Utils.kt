package com.ml.shubham0204.docqa.di

import android.content.Context
import com.google.gson.Gson
import android.util.Log
import com.google.gson.reflect.TypeToken
import com.ml.shubham0204.docqa.data.ChunkJson

object Utils {
    fun readChunksFromAssets(context: Context, fileName: String): List<ChunkJson> {
        Log.d("ReadChunks", "Mulai membaca file $fileName dari assets")

        val jsonText = context.assets.open(fileName).bufferedReader().use { it.readText() }
        Log.d("ReadChunks", "Isi file JSON (panjang: ${jsonText.length}): ${jsonText.take(300)}...") // hanya tampilkan 300 karakter pertama

        val gson = Gson()
        val chunkListType = object : TypeToken<List<ChunkJson>>() {}.type
        val chunks: List<ChunkJson> = gson.fromJson(jsonText, chunkListType)

        Log.d("ReadChunks", "Total chunk terbaca: ${chunks.size}")
        chunks.forEachIndexed { index, chunk ->
            Log.d(
                "ReadChunks",
                "Chunk ${index + 1}: id=${chunk.id}, parent_id=${chunk.parent_id}, panjang teks=${chunk.chunk_text.length}"
            )
        }

        return chunks
    }
}