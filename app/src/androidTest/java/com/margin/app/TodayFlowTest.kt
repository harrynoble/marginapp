package com.margin.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The flows that matter most: the app opens on a real plan, the seeded week is already
 * there, and every tab reaches a screen that has rendered rather than an empty frame.
 *
 * Assertions use ignoreCase because section headers are uppercased for display, and they
 * match on content the screens genuinely own rather than on chrome shared between tabs.
 */
@RunWith(AndroidJUnit4::class)
class TodayFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun openTab(label: String) {
        composeRule.onAllNodesWithText(label, ignoreCase = true).onFirst().performClick()
        composeRule.waitForIdle()
    }

    /** Setup only appears on a fresh install, so both paths have to be handled. */
    private fun skipOnboardingIfShown() {
        composeRule.waitForIdle()
        if (exists("Skip setup")) {
            composeRule.onAllNodesWithText("Skip setup", ignoreCase = true).onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun todayScreenAnswersWhatIsHappeningNow() {
        skipOnboardingIfShown()

        // The Now card renders in one of two forms: something is scheduled, or the day is
        // open. Either proves the plan was generated and the screen composed.
        composeRule.waitUntil(15_000) {
            exists("Now") || exists("Ahead") || exists("In progress")
        }
        composeRule.onAllNodesWithText("Now", substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
            .let { nodes ->
                if (nodes.isEmpty()) {
                    composeRule.onAllNodesWithText("Ahead", substring = true, ignoreCase = true)
                        .onFirst()
                        .assertIsDisplayed()
                }
            }
    }

    @Test
    fun theSeededTimetableIsAlreadyThere() {
        skipOnboardingIfShown()
        openTab("Week")

        // The subject list is unique to this screen and comes straight from the seed.
        awaitText("Review weight")
        composeRule.onAllNodesWithText("Review weight", substring = true, ignoreCase = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun tasksScreenOffersAWayToAddWork() {
        skipOnboardingIfShown()
        openTab("Tasks")

        awaitText("Projects")
        composeRule.onAllNodesWithText("Projects", substring = true, ignoreCase = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun planScreenShowsTheShapeOfTheDay() {
        skipOnboardingIfShown()
        openTab("Plan")

        awaitText("college")
        composeRule.onAllNodesWithText("college", substring = true, ignoreCase = true)
            .onFirst()
            .assertIsDisplayed()
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
