package com.ml.shubham0204.docqa

import android.content.Context
import android.util.Log
import com.ml.shubham0204.docqa.domain.llm.LlamaRemoteAPI
import com.ml.shubham0204.docqa.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class QnATest {

    // Fungsi untuk membaca dataset CSV dan menjalankan uji


//    fun runQnAValidation(
//        context: Context,  // Dapatkan context untuk mengakses assets
//        chatViewModel: ChatViewModel
//    ) {
//        // Membaca dataset CSV dari assets
//        val inputStream = context.assets.open("dataset_pengujian_fixed.csv")
//        val data = csvReader().readAllWithHeader(inputStream)  // Gunakan InputStream secara langsung
//
//        Log.d("QnATest", "Dataset berhasil dibaca, total pertanyaan: ${data.size}")
//
//        val resultList = mutableListOf<List<String>>() // List untuk menyimpan hasil pertanyaan, jawaban yang benar, dan jawaban model
//
//        // Loop untuk setiap pertanyaan dan jawabannya
//        CoroutineScope(Dispatchers.IO).launch {
//            for (row in data) {
//                val query = row["question"] ?: continue  // Mendapatkan query/pertanyaan dari CSV
//                val correctAnswer = row["correct_answer"] ?: continue  // Mendapatkan jawaban yang benar dari CSV
//
//                Log.d("QnATest", "Mengirim pertanyaan: $query")
//
//                // Menyimpan pertanyaan ke ViewModel
//                withContext(Dispatchers.Main) {
//                    chatViewModel.getAnswer(query, "$correctAnswer\n\$CONTEXT")
//                }
//
//                while (chatViewModel.isGeneratingResponseState.value) {
//                    delay(100)
//                }
//
//
//                // Mendapatkan jawaban dari Model
//                val generatedAnswer = chatViewModel.responseState.value
//
//                Log.d("QnATest", "Model memberikan jawaban: $generatedAnswer")
//
//                // Simpan pertanyaan, jawaban yang benar, dan jawaban dari model ke hasil
//                resultList.add(listOf(query, correctAnswer, generatedAnswer))
//
//                // Log hasil untuk setiap pertanyaan
//                Log.d("QnATest", "Hasil: Pertanyaan: $query, Jawaban Benar: $correctAnswer, Jawaban Model: $generatedAnswer")
//            }
//
//            // Jika ingin menampilkan semua hasil setelah semua pengujian selesai
//            Log.d("QnATest", "Semua hasil pengujian:")
//            resultList.forEach { result ->
//                Log.d("QnATest", "Pertanyaan: ${result[0]}, Jawaban Benar: ${result[1]}, Jawaban Model: ${result[2]}")
//            }
//        }
//    }
//
//    // Tambahan ke dalam QnATest.kt
//    fun evaluateAnswers(
//        questions: List<Map<String, String>>,
//        getAnswer: suspend (String, String) -> String
//    ): List<Triple<String, String, String>> {
//        val resultList = mutableListOf<Triple<String, String, String>>()
//
//        runBlocking {
//            for (row in questions) {
//                val question = row["question"] ?: continue
//                val correctAnswer = row["correct_answer"] ?: continue
//                val generatedAnswer = getAnswer(question, "$correctAnswer\n\$CONTEXT")
//                resultList.add(Triple(question, correctAnswer, generatedAnswer))
//            }
//        }
//
//        return resultList
//    }


}
