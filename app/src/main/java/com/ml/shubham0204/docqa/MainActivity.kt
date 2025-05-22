package com.ml.shubham0204.docqa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.retrievers.LuceneIndexer
import com.ml.shubham0204.docqa.ui.screens.chat.ChatScreen
import com.ml.shubham0204.docqa.ui.screens.docs.DocsScreen
import com.ml.shubham0204.docqa.ui.screens.edit_api_key.EditAPIKeyScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var chunksDB: ChunksDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sentenceEncoder = SentenceEmbeddingProvider(this)
        chunksDB = ChunksDB(sentenceEncoder)

        enableEdgeToEdge()

        setContent {
            var isIndexReady by remember { mutableStateOf(false) }

            // Bangun index saat pertama kali
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    LuceneIndexer.initializeLuceneIndex(this@MainActivity, chunksDB)
                }
                isIndexReady = true // lanjutkan setelah indexing selesai
            }

            if (!isIndexReady) {
                // Tampilkan layar loading
                LoadingScreen()
            } else {
                // Index selesai → tampilkan navigasi utama
                val navHostController = rememberNavController()
                NavHost(
                    navController = navHostController,
                    startDestination = "chat",
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable("docs") {
                        DocsScreen(onBackClick = { navHostController.navigateUp() })
                    }
                    composable("edit-api-key") {
                        EditAPIKeyScreen(onBackClick = { navHostController.navigateUp() })
                    }
                    composable("chat") {
                        ChatScreen(
                            onOpenDocsClick = { navHostController.navigate("docs") },
                            onEditAPIKeyClick = { navHostController.navigate("edit-api-key") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Menyiapkan sistem, mohon tunggu...")
        }
    }
}

//package com.ml.shubham0204.docqa
//
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.compose.animation.fadeIn
//import androidx.compose.animation.fadeOut
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.compose.rememberNavController
//import com.ml.shubham0204.docqa.ui.screens.chat.ChatScreen
//import com.ml.shubham0204.docqa.ui.screens.docs.DocsScreen
//import com.ml.shubham0204.docqa.ui.screens.edit_api_key.EditAPIKeyScreen
////import com.chaquo.python.Python
////import com.chaquo.python.android.AndroidPlatform
//import androidx.activity.compose.setContent
//import com.ml.shubham0204.docqa.ui.screens.chat.ChatViewModel
//import org.koin.androidx.viewmodel.ext.android.viewModel
//
//// llama
//import android.llama.cpp.LLamaAndroid
//import android.util.Log
//import androidx.lifecycle.lifecycleScope
//import com.ml.shubham0204.docqa.data.Chunk
//import com.ml.shubham0204.docqa.data.ChunksDB
//import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
//import com.ml.shubham0204.docqa.domain.readers.Readers
//import com.ml.shubham0204.docqa.domain.splitters.WhiteSpaceSplitter
//import com.ml.shubham0204.docqa.ui.screens.docs.DocsViewModel
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class MainActivity : ComponentActivity() {
//
//    private lateinit var chunksDB: ChunksDB
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        chunksDB = ChunksDB()
//
////        if (! Python.isStarted()) {
////            Python.start(AndroidPlatform(this))
////        }
////        val py = Python.getInstance()
//
//        enableEdgeToEdge()
//
//        // Jalankan indexing & query di background
//        lifecycleScope.launch(Dispatchers.IO) {
////            chunksDB.buildInvertedIndex()
//
////            val query = "Bagaimana bentuk pakaian ihram bagi laki-laki dan perempuan?"
////            Log.d("SearchResult", "Test result")
////
////            val results = chunksDB.getSimilarChunksBM25Optimized(this@MainActivity, query, n = 5)
////
////            // Log hasil di main thread
////            withContext(Dispatchers.Main) {
////                results.forEach { (score, chunk) ->
////                    Log.d("SearchResult", "Score: $score, Text: ${chunk.chunkData}")
////                }
////            }
//            // Build Index
//            chunksDB.initializeLuceneIndex(this@MainActivity)
//
//            // Query yang ingin dicari
//            val query = "Bagaimana bentuk pakaian ihram bagi laki-laki dan perempuan?]"
//
//            // Memanggil fungsi untuk mendapatkan chunk yang relevan
//            val similarChunks = chunksDB.getSimilarChunksLuceneOptimized(this@MainActivity, query)
//
//            // Menampilkan hasil
//            similarChunks.forEach { (score, chunk) ->
//                println("Chunk ID: ${chunk.chunkId}, Score: $score")
//            }
//        }
//
//        setContent {
//            val navHostController = rememberNavController()
//            NavHost(
//                navController = navHostController,
//                startDestination = "chat",
//                enterTransition = { fadeIn() },
//                exitTransition = { fadeOut() },
//            ) {
//                composable("docs") { DocsScreen(onBackClick = { navHostController.navigateUp() }) }
//                composable("edit-api-key") { EditAPIKeyScreen(onBackClick = { navHostController.navigateUp() }) }
//                composable("chat") {
//                    ChatScreen(
//                        onOpenDocsClick = { navHostController.navigate("docs") },
//                        onEditAPIKeyClick = { navHostController.navigate("edit-api-key") },
//                    )
//                }
//            }
//        }
//
////        // Jalankan validasi QnA
////        Log.d("TEST", "Ini adalah permulaan tes")
//
////        val qnaTest = QnATest()
////        qnaTest.runQnAValidation(this, chatViewModel)
//    }
//}