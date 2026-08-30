package com.atlasreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlasreader.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: the real app (with its production Hilt graph) launches, the
 * adaptive shell renders, and the empty library state is shown.
 */
@RunWith(AndroidJUnit4::class)
class LibrarySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_showsLibrary() {
        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()
    }
}
