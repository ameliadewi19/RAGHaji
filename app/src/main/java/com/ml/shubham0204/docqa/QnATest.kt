package com.ml.shubham0204.docqa

import android.content.Context
import com.ml.shubham0204.docqa.domain.llm.LlamaRemoteAPI
import com.ml.shubham0204.docqa.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.File

class QnATest {

    // Fungsi untuk membaca dataset CSV dan menjalankan uji
    fun runQnAValidation(
        context: Context,  // Dapatkan context untuk mengakses assets
        chatViewModel: ChatViewModel,
        outputCsvPath: String  // Path output di externalFilesDir
    ) {
        // Membaca dataset CSV dari assets
        val inputStream = context.assets.open("dataset_pengujian.csv")
        val data = csvReader().readAllWithHeader(inputStream)  // Gunakan InputStream secara langsung

        val resultList = mutableListOf<List<String>>() // List untuk menyimpan hasil pertanyaan, jawaban yang benar, dan jawaban model

        // Loop untuk setiap pertanyaan dan jawabannya
        CoroutineScope(Dispatchers.IO).launch {
            for (row in data) {
                val query = row["question"] ?: continue  // Mendapatkan query/pertanyaan dari CSV
                val correctAnswer = row["correct_answer"] ?: continue  // Mendapatkan jawaban yang benar dari CSV

                // Menyimpan pertanyaan ke ViewModel
                chatViewModel.getAnswer(query, "$correctAnswer\n\$CONTEXT")

                // Tunggu hingga model selesai merespons
                while (chatViewModel.isGeneratingResponseState.value) {
                    // Tunggu hingga status generating selesai
                }

                // Mendapatkan jawaban dari Model
                val generatedAnswer = chatViewModel.responseState.value

                // Simpan pertanyaan, jawaban yang benar, dan jawaban dari model ke hasil
                resultList.add(listOf(query, correctAnswer, generatedAnswer))
            }

            // Menyimpan hasil ke output file di externalFilesDir
            val outputFile = File(context.getExternalFilesDir(null), "qna_output.csv")
            csvWriter().writeAll(resultList, outputFile)
        }
    }

}