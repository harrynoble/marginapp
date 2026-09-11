package com.margin.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The flows that matter most: the app opens on a real plan, the seeded timetable is already
 * there without being asked for, and every tab reaches a screen that has rendered rather than
 * an empty frame.
 *
 * The timetable is no longer a tab. It lives under Settings, so the test goes there the way a
 * person would.
 */
@RunWith(AndroidJUnit4::class)
class TodayFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) { exists(text) }
    }

    private fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun describedExists(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun openTab(label: String) {
        composeRule.onAllNodesWithText(label).onFirst().performClick()
        composeRule.waitForIdle()
    }

    /** Setup only appears on a fresh install, so both paths have to be handled. */
    private fun skipOnboardingIfShown() {
        composeRule.waitForIdle()
        if (exists("Set Up Later")) {
            composeRule.onAllNodesWithText("Set Up Later").onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun todayScreenAnswersWhatIsHappeningNow() {
        skipOnboardingIfShown()

        // The Now card always renders in one of these forms, whatever the time of day.
        composeRule.waitUntil(15_000) {
            exists("Now") || exists("Ahead") || exists("In progress") || exists("At college")
        }
        composeRule.onAllNodesWithText("Today").onFirst().assertIsDisplayed()
    }

    @Test
    fun theSeededTimetableIsAlreadyThere() {
        skipOnboardingIfShown()
        composeRule.waitUntil(15_000) { describedExists("Settings") }
        composeRule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("college")
        composeRule.onAllNodesWithText("Timetable").onFirst().performClick()
        composeRule.waitForIdle()

        // The subject list comes straight from the seed; nothing was entered by hand.
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("subjects")
        awaitText("Revision weight")
        composeRule.onAllNodesWithText("Revision weight", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun tasksScreenOffersAWayToAddWork() {
        skipOnboardingIfShown()
        openTab("Tasks")

        awaitText("Show Done")
        composeRule.onAllNodesWithText("Show Done").onFirst().assertIsDisplayed()
    }

    @Test
    fun planScreenShowsTheShapeOfTheDay() {
        skipOnboardingIfShown()
        openTab("Plan")

        composeRule.waitUntil(15_000) { describedExists("Previous week") }
        composeRule.onAllNodesWithContentDescription("Previous week").onFirst().assertIsDisplayed()
    }

    @Test
    fun insightsScreenRendersWithoutHistory() {
        skipOnboardingIfShown()
        openTab("Insights")

        // With no history it must say so rather than showing an empty frame.
        composeRule.waitUntil(15_000) {
            exists("Not enough history") || exists("Follow through")
        }
    }
}
