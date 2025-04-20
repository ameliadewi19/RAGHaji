package com.ml.shubham0204.docqa.domain.splitters

import com.ml.shubham0204.docqa.data.ChunkNode
import java.util.UUID

class Small2BigChunker {
    companion object {
        fun createMultiSizeChunks(
            docText: String,
            baseChunkSize: Int = 1024,
            subChunkSizes: List<Int> = listOf(128, 256, 512),
            separator: String = " "
        ): List<ChunkNode> {
            val words = docText.split(separator).filter { it.isNotBlank() }
            val nodes = mutableListOf<ChunkNode>()

            var idx = 0
            while (idx < words.size) {
                val end = minOf(idx + baseChunkSize, words.size)
                val baseWords = words.subList(idx, end)
                val baseText = baseWords.joinToString(" ")
                val baseId = UUID.randomUUID().toString()

                // Tambahkan parent chunk
                nodes.add(ChunkNode(id = baseId, text = baseText, size = baseWords.size))

                // Buat child chunks
                for (subSize in subChunkSizes) {
                    val subStep = baseWords.size / (baseChunkSize / subSize)
                    for (i in 0 until baseWords.size step subStep) {
                        val j = minOf(i + subSize, baseWords.size)
                        if (j > i) {
                            val subText = baseWords.subList(i, j).joinToString(" ")
                            nodes.add(
                                ChunkNode(
                                    id = UUID.randomUUID().toString(),
                                    text = subText,
                                    size = j - i,
                                    parentId = baseId
                                )
                            )
                        }
                    }
                }

                idx += baseChunkSize
            }

            return nodes
        }
    }
}