package com.ml.shubham0204.docqa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ml.shubham0204.docqa.ui.screens.chat.ChatScreen
import com.ml.shubham0204.docqa.ui.screens.docs.DocsScreen
import com.ml.shubham0204.docqa.ui.screens.edit_api_key.EditAPIKeyScreen
//import com.chaquo.python.Python
//import com.chaquo.python.android.AndroidPlatform
import androidx.activity.compose.setContent
import com.ml.shubham0204.docqa.ui.screens.chat.ChatViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

// llama
import android.llama.cpp.LLamaAndroid
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.ml.shubham0204.docqa.data.Chunk
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.domain.embeddings.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.readers.Readers
import com.ml.shubham0204.docqa.domain.splitters.WhiteSpaceSplitter
import com.ml.shubham0204.docqa.ui.screens.docs.DocsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

//    private val chatViewModel: ChatViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        if (! Python.isStarted()) {
//            Python.start(AndroidPlatform(this))
//        }
//        val py = Python.getInstance()
        enableEdgeToEdge()

        setContent {
            val navHostController = rememberNavController()
            NavHost(
                navController = navHostController,
                startDestination = "chat",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable("docs") { DocsScreen(onBackClick = { navHostController.navigateUp() }) }
                composable("edit-api-key") { EditAPIKeyScreen(onBackClick = { navHostController.navigateUp() }) }
                composable("chat") {
                    ChatScreen(
                        onOpenDocsClick = { navHostController.navigate("docs") },
                        onEditAPIKeyClick = { navHostController.navigate("edit-api-key") },
                    )
                }
            }
        }

//        // Jalankan validasi QnA
//        Log.d("TEST", "Ini adalah permulaan tes")

//        val qnaTest = QnATest()
//        qnaTest.runQnAValidation(this, chatViewModel)
    }
}