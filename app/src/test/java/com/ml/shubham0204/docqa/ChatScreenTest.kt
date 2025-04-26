package com.ml.shubham0204.docqa

import android.os.Build
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ml.shubham0204.docqa.ui.screens.chat.ChatScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P]) // Specify the SDK level for Robolectric if needed
class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        // Use Robolectric to mock Build.FINGERPRINT
        Build.FINGERPRINT = "robolectric_fingerprint"
        Log.d("ChatScreenTest", "Simulated Build.FINGERPRINT: robolectric_fingerprint")
    }

    @Test
    fun testInputQueryAndSend() {
        // Set content untuk menampilkan UI
        composeTestRule.setContent {
            ChatScreen(
                onOpenDocsClick = {},
                onEditAPIKeyClick = {},
            )
        }

        // Temukan TextField berdasarkan testTag
        val textField = composeTestRule.onNodeWithTag("query_input")

        // Pastikan TextField ada
        Log.d("ChatScreenTest", "Verifying TextField exists...")
        textField.assertExists("TextField should exist")

        // Simulasikan input teks
        Log.d("ChatScreenTest", "Simulating text input: Apa itu LLM?")
        textField.performTextInput("Apa itu LLM?")

        // Pastikan input muncul di UI
        Log.d("ChatScreenTest", "Verifying text appears in the UI...")
        composeTestRule.onNodeWithText("Apa itu LLM?").assertExists("Text should appear in the UI")

        // Klik tombol kirim
        Log.d("ChatScreenTest", "Clicking send button...")
        composeTestRule.onNodeWithTag("send_button").performClick()

        // Verifikasi apakah respons sedang diproses dengan adanya LinearProgressIndicator
        Log.d("ChatScreenTest", "Verifying progress indicator exists...")
        composeTestRule.onNode(hasTestTag("linear_progress_indicator"))
            .assertExists("Progress indicator should appear when generating response")

        // Simulasi respons selesai (bisa menggunakan stub atau mocking dalam ViewModel)
        // Misalnya dengan mengubah respons di ViewModel dan memverifikasi
        Log.d("ChatScreenTest", "Verifying context text appears after response...")
        composeTestRule.onNodeWithText("Context").assertExists("Context text should be displayed after response")
    }
}
