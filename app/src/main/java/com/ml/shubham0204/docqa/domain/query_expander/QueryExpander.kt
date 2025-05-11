package com.ml.shubham0204.docqa.domain.query_expander

class QueryExpander {

    fun expandQuery(userQuery: String): String {
        val query = userQuery.lowercase().trim()

        // -------------------------
        // Faktual / Definisi
        // -------------------------
        if (query.startsWith("apa yang dimaksud dengan") ||
            query.startsWith("apa yang dimaksud") ||
            query.startsWith("apa itu") ||
            query.contains("jelaskan tentang")) {

            val keyword = query.replace("apa yang dimaksud dengan", "")
                .replace("apa itu", "")
                .replace("jelaskan tentang", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            // Template untuk definisi
            val expansions = listOf(
                userQuery,
                "$keyword berasal",
                "Menurut istilah, $keyword",
                "$keyword adalah",
                "$keyword merupakan",
                "$keyword berarti",
                "$keyword menurut"
            )

            return expansions.joinToString(". ") + "."

            // -------------------------
            // Analytical: sebab-akibat, dampak, analisis
            // -------------------------
        } else if (
            query.contains("mengapa") ||
            query.contains("mengapa bisa terjadi") ||
            query.contains("apa penyebab") ||
            query.contains("apa dampak") ||
            query.contains("bagaimana pengaruh") ||
            query.contains("apa akibat")
        ) {
            val keyword = query.replace("mengapa", "")
                .replace("bisa terjadi", "")
                .replace("apa penyebab", "")
                .replace("apa dampak", "")
                .replace("bagaimana pengaruh", "")
                .replace("apa akibat", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            val expansions = listOf(
                userQuery,
                "Alasan di balik $keyword",
                "Faktor yang menyebabkan $keyword",
                "Dampak dari $keyword",
                "Akibat yang ditimbulkan oleh $keyword",
                "Pengaruh $keyword terhadap situasi tertentu"
            )

            return expansions.joinToString(". ") + "."

            // -------------------------
            // Comparative: perbandingan
            // -------------------------
        } else if (
            query.contains("apa perbedaan") ||
            query.contains("apa persamaan") ||
            query.contains("bandingkan antara")
        ) {
            val keyword = query.replace("apa perbedaan", "")
                .replace("apa persamaan", "")
                .replace("bandingkan antara", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            val expansions = listOf(
                userQuery,
                "Persamaan dan perbedaan dari $keyword",
                "Perbandingan karakteristik $keyword",
                "Perbedaan utama dalam $keyword",
                "Kemiripan antara $keyword",
                "Analisis komparatif dari $keyword"
            )

            return expansions.joinToString(". ") + "."

            // -------------------------
            // Tutorial / Prosedural
            // -------------------------
        } else if (
            query.contains("bagaimana cara") ||
            query.contains("langkah-langkah untuk") ||
            query.contains("cara membuat") ||
            query.contains("prosedur untuk")
        ) {
            val keyword = query.replace("bagaimana cara", "")
                .replace("langkah-langkah untuk", "")
                .replace("cara membuat", "")
                .replace("prosedur untuk", "")
                .trim { it <= ' ' || it == '?' || it == '.' }

            val expansions = listOf(
                userQuery,
                "Langkah-langkah melakukan $keyword",
                "Tutorial tentang $keyword",
                "Panduan praktis untuk $keyword",
                "Cara menyelesaikan $keyword",
                "Instruksi lengkap $keyword"
            )

            return expansions.joinToString(". ") + "."

        } else {
            // Default: tidak dikenali, kembalikan apa adanya
            return userQuery
        }
    }

    //    fun expandQueryForBM25(originalQuery: String, context: Context): String {
//        val inputStream = context.assets.open("dict.json")
//        val jsonString = inputStream.bufferedReader().use { it.readText() }
//        val jsonObject = JSONObject(jsonString)
//
//        val expandedTokens = mutableListOf<String>()
//        val tokens = originalQuery.lowercase().split(" ")
//
//        for (token in tokens) {
//            if (jsonObject.has(token)) {
//                val sinonimArray = jsonObject.getJSONObject(token).getJSONArray("sinonim")
//                val sinonimList = List(sinonimArray.length()) { i -> sinonimArray.getString(i) }
//                expandedTokens.addAll(listOf(token) + sinonimList)
//            } else {
//                expandedTokens.add(token)
//            }
//        }
//
//        return expandedTokens.joinToString(" ")
//    }
}