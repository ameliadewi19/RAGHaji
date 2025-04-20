package com.ml.shubham0204.docqa.domain.splitters

class SlidingWindowChunker {
    companion object {
        fun createSlidingChunks(
            docText: String,
            windowSize: Int,
            step: Int,
            separatorParagraph: String = "\n\n",
            separator: String = " ",
        ): List<String> {
            val textChunks = ArrayList<String>()
            docText.split(separatorParagraph).forEach { paragraph ->
                val words = paragraph.split(separator).filter { it.isNotBlank() }
                val total = words.size
                var i = 0
                while (i + windowSize <= total) {
                    val chunk = words.subList(i, i + windowSize).joinToString(separator)
                    textChunks.add(chunk)
                    i += step
                }
            }
            return textChunks
        }
    }
}