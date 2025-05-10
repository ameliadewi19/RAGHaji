package com.ml.shubham0204.docqa.ui.screens.chat

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ml.shubham0204.docqa.R
import com.ml.shubham0204.docqa.ui.components.AppAlertDialog
import com.ml.shubham0204.docqa.ui.components.createAlertDialog
import com.ml.shubham0204.docqa.ui.theme.DocQATheme
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import org.apache.commons.compress.archivers.dump.InvalidFormatException
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDocsClick: (() -> Unit),
    onEditAPIKeyClick: (() -> Unit),
) {
    DocQATheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(text = "Chat", style = MaterialTheme.typography.headlineSmall) },
                    actions = {
                        IconButton(onClick = onOpenDocsClick) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Open Documents",
                            )
                        }
                        IconButton(onClick = onEditAPIKeyClick) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Edit API Key",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->

            val chatViewModel: ChatViewModel = koinViewModel()
            val context = LocalContext.current

            // --- Dropdown state ---
            var selectedIndexType by remember { mutableStateOf("Hybrid") }
            val indexOptions = listOf("Hybrid", "Sparse", "Dense")
            var expanded by remember { mutableStateOf(false) }

            // --- Top K input state ---
            var topKText by remember { mutableStateOf("3") }
            val topK = topKText.toIntOrNull() ?: 3

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Index Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedIndexType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Index Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        indexOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedIndexType = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Top K Input
                OutlinedTextField(
                    value = topKText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            topKText = newValue
                        }
                    },
                    label = { Text("Top K") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                QALayout(chatViewModel)
                Spacer(modifier = Modifier.height(8.dp))

                QueryInput(
                    chatViewModel = chatViewModel,
                    onEditAPIKeyClick = onEditAPIKeyClick,
                    indexType = selectedIndexType,
                    topK = topK
                )
            }

            AppAlertDialog()
        }
    }
}


@Composable
private fun ColumnScope.QALayout(chatViewModel: ChatViewModel) {
    val question by chatViewModel.questionState.collectAsState()
    val response by chatViewModel.responseState.collectAsState()
    val isGeneratingResponse by chatViewModel.isGeneratingResponseState.collectAsState()
    val retrievedContextList by chatViewModel.retrievedContextListState.collectAsState()
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().weight(1f),
    ) {
        if (question.trim().isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(75.dp),
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.LightGray,
                )
                Text(
                    text = "Enter a query to see answers",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                )
            }
        } else {
            LazyColumn {
                item {
                    Text(text = question, style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isGeneratingResponse) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier =
                            Modifier
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(24.dp)
                                .fillMaxWidth(),
                    ) {
                        MarkdownText(
                            modifier = Modifier.fillMaxWidth(),
                            markdown = response,
                            style = TextStyle(
                                color = Color.Black,
                                fontSize = 14.sp,
                            ),
                        )
                        if (!isGeneratingResponse) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = {
                                        val sendIntent: Intent =
                                            Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, response)
                                                type = "text/plain"
                                            }
                                        val shareIntent = Intent.createChooser(sendIntent, null)
                                        context.startActivity(shareIntent)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share the response",
                                        tint = Color.Black,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isGeneratingResponse) {
                    items(retrievedContextList) { retrievedContext ->
                        Column(
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .background(Color.Cyan, RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                        ) {
                            Text(
                                text = "\"${retrievedContext.context}\"",
                                color = Color.Black,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                            )
                            Text(
                                text = retrievedContext.fileName,
                                color = Color.Black,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

//                item {
//                    Text(text = question, style = MaterialTheme.typography.headlineLarge)
//                    if (isGeneratingResponse) {
//                        Spacer(modifier = Modifier.height(4.dp))
//                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
//                    }
//                }
//                item {
//                    if (!isGeneratingResponse) {
//                        Spacer(modifier = Modifier.height(16.dp))
//                        Column(
//                            modifier =
//                                Modifier
//                                    .background(Color.White, RoundedCornerShape(16.dp))
//                                    .padding(24.dp)
//                                    .fillMaxWidth(),
//                        ) {
//                            MarkdownText(
//                                modifier = Modifier.fillMaxWidth(),
//                                markdown = response,
//                                style =
//                                    TextStyle(
//                                        color = Color.Black,
//                                        fontSize = 14.sp,
//                                    ),
//                            )
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.End,
//                            ) {
//                                IconButton(
//                                    onClick = {
//                                        val sendIntent: Intent =
//                                            Intent().apply {
//                                                action = Intent.ACTION_SEND
//                                                putExtra(Intent.EXTRA_TEXT, response)
//                                                type = "text/plain"
//                                            }
//                                        val shareIntent = Intent.createChooser(sendIntent, null)
//                                        context.startActivity(shareIntent)
//                                    },
//                                ) {
//                                    Icon(
//                                        imageVector = Icons.Default.Share,
//                                        contentDescription = "Share the response",
//                                        tint = Color.Black,
//                                    )
//                                }
//                            }
//                        }
//                        Spacer(modifier = Modifier.height(8.dp))
//                        Text(text = "Context", style = MaterialTheme.typography.headlineSmall)
//                        Spacer(modifier = Modifier.height(4.dp))
//                    }
//                }
//                if (!isGeneratingResponse) {
//                    items(retrievedContextList) { retrievedContext ->
//                        Column(
//                            modifier =
//                                Modifier
//                                    .padding(8.dp)
//                                    .background(Color.Cyan, RoundedCornerShape(16.dp))
//                                    .padding(16.dp)
//                                    .fillMaxWidth(),
//                        ) {
//                            Text(
//                                text = "\"${retrievedContext.context}\"",
//                                color = Color.Black,
//                                modifier = Modifier.fillMaxWidth(),
//                                fontSize = 12.sp,
//                                fontStyle = FontStyle.Italic,
//                            )
//                            Text(
//                                text = retrievedContext.fileName,
//                                color = Color.Black,
//                                modifier = Modifier.fillMaxWidth(),
//                                fontSize = 10.sp,
//                            )
//                        }
//                    }
//                }
            }
        }
    }
}

fun loadQuestionsAndAnswersFromXlsx(context: Context): Pair<ArrayList<String>, ArrayList<String>> {
    val questions = ArrayList<String>()
    val correctAnswers = ArrayList<String>()

    try {
        // Membuka file XLSX dari assets
        val inputStream: InputStream = context.assets.open("dataset_pengujian.xlsx")
        val workbook: Workbook = WorkbookFactory.create(inputStream)

        // Mengambil sheet pertama
        val sheet: Sheet = workbook.getSheetAt(0)

        // Membaca setiap baris dalam sheet
        for (rowIndex in 1 until sheet.physicalNumberOfRows) { // Mulai dari 1 untuk melewati header
            val row: Row = sheet.getRow(rowIndex)

            if (row != null) {
                // Mendapatkan kolom pertama (question) dan kedua (correct_answer)
                val question = row.getCell(0)?.toString()?.trim() ?: ""
                val correctAnswer = row.getCell(1)?.toString()?.trim() ?: ""

                // Menambahkan ke list
                questions.add(question)
                correctAnswers.add(correctAnswer)
            }
        }

        workbook.close()
        inputStream.close()

    } catch (e: InvalidFormatException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return Pair(questions, correctAnswers)
}

@Composable
private fun QueryInput(
    chatViewModel: ChatViewModel,
    onEditAPIKeyClick: () -> Unit,
    indexType: String,
    topK: Int
) {
    var questionText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Daftar query yang akan diproses
    val (questions, correctAnswers) = loadQuestionsAndAnswersFromXlsx(context)
    val correctAnswerDump = ""

    for (i in questions.indices) {
        Log.d("QUESTION", questions[i])
        Log.d("ANSWER", correctAnswers[i])
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("query_input"),
            value = questionText,
            onValueChange = { questionText = it },
            shape = RoundedCornerShape(16.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    disabledTextColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            placeholder = { Text(text = "Ask documents...") },
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            modifier = Modifier.testTag("send_button").background(Color.Blue, CircleShape),
            onClick = {
                keyboardController?.hide()

                if (questionText.trim().isEmpty()) {
                    Toast.makeText(context, "Enter a query to execute", Toast.LENGTH_LONG).show()
                    return@IconButton
                }

                try {
                    when (indexType) {
                        "Sparse" -> chatViewModel.getAnswerSparse(
                            questionText,
                            context.getString(R.string.prompt_1),
                            correctAnswerDump,
                            topK
                        )

                        "Dense" -> chatViewModel.getAnswer(
                            questionText,
                            context.getString(R.string.prompt_1),
                            correctAnswerDump,
                            topK
                        )

                        "Hybrid" -> chatViewModel.getAnswerHybrid(
                            context,
                            questionText,
                            correctAnswerDump,
                            topK
                        )
                    }
                } catch (e: Exception) {
                    createAlertDialog(
                        dialogTitle = "Error",
                        dialogText = "An error occurred while generating the response: ${e.message}",
                        dialogPositiveButtonText = "Close",
                        onPositiveButtonClick = {},
                        dialogNegativeButtonText = null,
                        onNegativeButtonClick = {},
                    )
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Send query",
                tint = Color.White,
            )
        }
//        val scope = rememberCoroutineScope()
//
//        IconButton(
//            modifier = Modifier.testTag("send_button").background(Color.Blue, CircleShape),
//            onClick = {
//                keyboardController?.hide()
//
//                if (questionText.trim().isEmpty()) {
//                    Toast.makeText(context, "Enter a query to execute", Toast.LENGTH_LONG).show()
//                    return@IconButton
//                }
//
//                scope.launch {
//                    questions.zip(correctAnswers).forEach { (question, correctAnswer) ->  // Pair question sama answer
//                        chatViewModel.getAnswer(
//                            question,
//                            context.getString(R.string.prompt_1),
//                            correctAnswer // <-- kirim correctAnswer-nya
//                        )
//
//                        while (chatViewModel.isGeneratingResponseState.value) {
//                            delay(100)
//                        }
//                        delay(300)
//                    }
//                }
//            },
//        ) {
//            Icon(
//                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
//                contentDescription = "Send query",
//                tint = Color.White,
//            )
//        }

    }
}
