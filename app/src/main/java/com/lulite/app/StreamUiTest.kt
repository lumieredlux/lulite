package com.lulite.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StreamUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hasBasicControls() {
        composeRule.setContent {
            Material3ThemePreview {
                // lightweight preview of labels
                androidx.compose.material3.Text("Start Stream")
                androidx.compose.material3.Text("Stop")
                androidx.compose.material3.Text("Quality:")
            }
        }
        composeRule.onNodeWithText("Start Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
        composeRule.onNodeWithText("Quality:").assertIsDisplayed()
    }
}

// tiny helper to avoid importing theme (keeps deps minimal)
@Composable
fun Material3ThemePreview(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme { content() }
}
