package com.ml.shubham0204.docqa.domain.splitters

class SlidingWindowChunker {
    companion object {
        fun createSlidingChunks(
            docText: String,
            chunkSize: Int,
            overlap: Int,
            separatorParagraph: String = "\n\n",
            separator: String = " ",
        ): List<String> {
            val textChunks = mutableListOf<String>()
            val paragraphs = docText.split(separatorParagraph)

            for (paragraph in paragraphs) {
                val words = paragraph.split(separator).filter { it.isNotBlank() }
                val totalWords = words.size
                var start = 0

                while (start < totalWords) {
                    val end = minOf(start + chunkSize, totalWords)
                    val chunk = words.subList(start, end).joinToString(separator)
                    textChunks.add(chunk)

                    if (end == totalWords) {
                        break // sudah sampai akhir, keluar
                    }

                    start += (chunkSize - overlap)
                }
            }

            return textChunks
        }
    }
}


//class SlidingWindowChunker {
//    companion object {
//        fun createSlidingChunks(
//            docText: String,
//            chunkSize: Int,
//            overlap: Int,
//            separatorParagraph: String = "\n\n",
//            separator: String = " ",
//        ): List<String> {
//            val textChunks = ArrayList<String>()
//            docText.split(separatorParagraph).forEach { paragraph ->
//                val words = paragraph.split(separator).filter { it.isNotBlank() }
//                val total = words.size
//                var i = 0
//                while (i + chunkSize <= total) {
//                    val chunk = words.subList(i, i + chunkSize).joinToString(separator)
//                    textChunks.add(chunk)
//                    i += overlap
//                }
//            }
//            return textChunks
//        }
//    }
//}