package com.ml.shubham0204.docqa.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ml.shubham0204.docqa.data.ChunkJson

object Utils {
    fun readChunksFromAssets(context: Context, fileName: String): List<ChunkJson> {
        val jsonText = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val gson = Gson()
        val chunkListType = object : TypeToken<List<ChunkJson>>() {}.type
        return gson.fromJson(jsonText, chunkListType)
    }
}