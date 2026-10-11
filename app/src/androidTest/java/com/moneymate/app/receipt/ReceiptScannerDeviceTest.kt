package com.moneymate.app.receipt

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneymate.app.MoneyMate
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a deterministic camera image but the real bundled ML Kit recognizer on Android. */
@RunWith(AndroidJUnit4::class)
class ReceiptScannerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var camera: FixtureCamera
    private lateinit var restoration: StateRestorationTester

    @Before fun setUp() {
        compose.activity.getSharedPreferences("receipt_test_moneymate", Context.MODE_PRIVATE).edit().clear().commit()
        camera = FixtureCamera(compose.activity)
        val isolatedContext = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("receipt_test_$name", mode)
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = camera
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides owner,
                LocalContext provides isolatedContext
            ) { MoneyMate() }
        }
    }

    @After fun tearDown() {
        compose.activity.getSharedPreferences("receipt_test_moneymate", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun receiptCaptureRestoresAndReviewSavesExactlyOneEditedExpense() {
        compose.onNodeWithContentDescription("Scan receipt").performClick()
        compose.onNodeWithContentDescription("Scan receipt").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { camera.completeCapture() }
        waitForReview()
        compose.onNodeWithText("Description").assertTextContains("EXAMPLE MARKET")
        compose.onNodeWithText("Amount").assertTextContains("24.50")
        compose.onNodeWithText("Other").assertIsSelected()
        compose.onNodeWithText("Description").performTextReplacement("Receipt test purchase")
        compose.onNodeWithText("Amount").performTextReplacement("27.50")
        compose.onNodeWithText("Food").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Description").assertTextContains("Receipt test purchase")
        compose.onNodeWithText("Amount").assertTextContains("27.50")
        compose.onNodeWithText("Food").assertIsSelected()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Review receipt").assertDoesNotExist()
        compose.onAllNodesWithText("Receipt test purchase").assertCountEquals(1)
        compose.onNodeWithText("−$27.50").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Receipt test purchase").assertCountEquals(1)
        compose.runOnIdle { assertFalse(camera.photoFile().exists()) }
    }

    @Test fun cancelCaptureAndCancelReviewDoNotSaveAnything() {
        compose.onNodeWithContentDescription("Scan receipt").performClick()
        compose.runOnIdle { camera.completeCapture(captured = false) }
        compose.onNodeWithText("Review receipt").assertDoesNotExist()
        compose.runOnIdle { assertFalse(camera.photoFile().exists()) }
        compose.onNodeWithContentDescription("Scan receipt").performClick()
        compose.runOnIdle { camera.completeCapture() }
        waitForReview()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("EXAMPLE MARKET").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(compose.activity.getSharedPreferences("receipt_test_moneymate", 0).contains("items"))
        }
    }

    @Test fun unreadableReceiptShowsErrorAndAllowsRetry() {
        compose.onNodeWithContentDescription("Scan receipt").performClick()
        compose.runOnIdle { camera.completeCapture(blank = true) }
        val message = "No text found. Try a closer, well-lit photo or add the expense manually."
        compose.waitUntil(60_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Review receipt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Scan receipt").performClick()
        compose.runOnIdle { camera.completeCapture() }
        waitForReview()
        assertEquals(2, camera.launchCount)
    }

    @Test fun manualAddStillWorksAndRejectsZero() {
        compose.onNodeWithContentDescription("Add transaction").performClick()
        compose.onNodeWithText("Description").performTextReplacement("Manual test purchase")
        compose.onNodeWithText("Amount").performTextReplacement("0")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Amount").performTextReplacement("8.25")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Manual test purchase").assertIsDisplayed()
        compose.onNodeWithText("−$8.25").assertIsDisplayed()
    }

    @Test fun recognizedTextCanBeExpandedForReview() {
        compose.onNodeWithContentDescription("Scan receipt").performClick()
        compose.runOnIdle { camera.completeCapture() }
        waitForReview()
        compose.onNodeWithText("Show receipt text").performScrollTo().performClick()
        compose.onNodeWithText("SUBTOTAL", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun waitForReview() {
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("Review receipt").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class FixtureCamera(private val context: Context) : ActivityResultRegistry() {
        private var requestCode = 0
        private lateinit var photoUri: Uri
        var launchCount = 0
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            this.requestCode = requestCode
            photoUri = input as Uri
            launchCount++
        }

        fun photoFile() = File(context.cacheDir, "receipts/${photoUri.lastPathSegment}")

        fun completeCapture(captured: Boolean = true, blank: Boolean = false) {
            if (captured) {
                val bitmap = Bitmap.createBitmap(1200, 1400, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                if (!blank) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 52f }
                    listOf("EXAMPLE MARKET", "123 Main Street", "Coffee 22.00", "SUBTOTAL 22.00", "TAX 2.50", "TOTAL $24.50")
                        .forEachIndexed { index, line -> canvas.drawText(line, 70f, 140f + index * 120f, paint) }
                }
                context.contentResolver.openOutputStream(photoUri)!!.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                bitmap.recycle()
            }
            dispatchResult(requestCode, captured)
        }
    }
}
