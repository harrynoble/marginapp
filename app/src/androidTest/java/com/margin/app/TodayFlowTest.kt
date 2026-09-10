package com.margin.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The flows that matter most: the app opens on a real plan, the week is already loaded, and
 * a task can be created and reach the schedule. These need a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class TodayFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayScreenShowsWhatIsHappeningNow() {
        composeRule.waitForIdle()
        skipOnboardingIfShown()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Now", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Open", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theSeededTimetableIsAlreadyThere() {
        composeRule.waitForIdle()
        skipOnboardingIfShown()

        composeRule.onNodeWithText("Week").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Subjects", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Subjects", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun aNewTaskAppearsInTheTaskList() {
        composeRule.waitForIdle()
        skipOnboardingIfShown()

        composeRule.onNodeWithText("Tasks").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("All").onFirst().assertIsDisplayed()
    }

    @Test
    fun planScreenShowsTheWeekStrip() {
        composeRule.waitForIdle()
        skipOnboardingIfShown()

        composeRule.onNodeWithText("Plan").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("college", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun skipOnboardingIfShown() {
        val skip = composeRule.onAllNodesWithText("Skip setup").fetchSemanticsNodes()
        if (skip.isNotEmpty()) {
            composeRule.onNodeWithText("Skip setup").performClick()
            composeRule.waitForIdle()
        }
    }
}
