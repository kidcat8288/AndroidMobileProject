package com.moneymate.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionEditingDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val prefs get() = compose.activity.getSharedPreferences("editing_test_moneymate", Context.MODE_PRIVATE)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        val isolatedContext = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("editing_test_$name", mode)
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides isolatedContext) { MoneyMate() }
        }
        compose.onNodeWithText("Activity").performClick()
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test fun editExpenseUpdatesExistingRecordAndReports() {
        compose.onNodeWithText("Weekly groceries").performClick()
        compose.onNodeWithText("Description").performTextReplacement("Groceries edited")
        compose.onNodeWithText("Amount").performTextReplacement("95.25")
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Groceries edited").assertIsDisplayed()
        compose.onNodeWithText("Weekly groceries").assertDoesNotExist()
        compose.onNodeWithText("−$95.25").assertIsDisplayed()
        compose.runOnIdle {
            val rows = prefs.getString("items", "")!!.split("\\n")
            assertEquals(5, rows.size)
            assertEquals(1, rows.count { it.startsWith("groceries|Groceries edited|Food|95.25|EXPENSE|") })
        }
        compose.onNodeWithText("Reports").performClick()
        compose.onNodeWithText("$95.25").assertIsDisplayed()
    }

    @Test fun deletingExpenseRequiresConfirmationAndRemovesOnlyThatRecord() {
        compose.onNodeWithText("Weekly groceries").performClick()
        compose.onNodeWithText("Delete expense").performScrollTo().performClick()
        compose.onNodeWithText("Keep expense").performClick()
        compose.runOnIdle { assertFalse(prefs.contains("items")) }
        compose.onNodeWithText("Delete expense").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Weekly groceries").assertDoesNotExist()
        compose.runOnIdle {
            val rows = prefs.getString("items", "")!!.split("\\n")
            assertEquals(4, rows.size)
            assertFalse(rows.any { it.startsWith("groceries|") })
            assertTrue(rows.any { it.startsWith("salary|") })
        }
    }

    @Test fun editSalaryKeepsIncomeAndDoesNotOfferDelete() {
        compose.onNodeWithText("September salary").performClick()
        compose.onNodeWithText("Delete expense").assertDoesNotExist()
        compose.onNodeWithText("Description").performTextReplacement("October salary")
        compose.onNodeWithText("Amount").performTextReplacement("0")
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithText("Amount").performTextReplacement("3400")
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("October salary").assertIsDisplayed()
        compose.onNodeWithText("+$3,400.00").assertIsDisplayed()
        compose.runOnIdle {
            val rows = prefs.getString("items", "")!!.split("\\n")
            assertEquals(5, rows.size)
            assertEquals(1, rows.count { it.startsWith("salary|October salary|Salary|3400.0|INCOME|") })
        }
    }

    @Test fun reportPeriodsCanChangeAndCustomRangeOpensDatePicker() {
        compose.onNodeWithText("Reports").performClick()
        compose.onNodeWithText("Last week").assertIsSelected()
        compose.onNodeWithText("Two weeks").performClick()
        compose.onNodeWithText("Two weeks").assertIsSelected()
        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithText("Month").assertIsSelected()
        compose.onNodeWithText("Custom range").performClick()
        Espresso.onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        Espresso.pressBack()
        compose.onNodeWithText("Month").assertIsSelected()
        compose.onNodeWithText("Custom range").performClick()
        Espresso.onView(withId(android.R.id.button1)).perform(click())
        Espresso.onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        Espresso.onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText("Custom range").assertIsSelected()
    }
}
