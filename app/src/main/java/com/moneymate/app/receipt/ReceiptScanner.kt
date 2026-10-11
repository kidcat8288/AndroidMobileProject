package com.moneymate.app.receipt

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReceiptDraft(
    val merchant: String = "",
    val total: Double? = null,
    val recognizedText: String = ""
) {
    companion object {
        val Saver = listSaver<ReceiptDraft, String>(
            save = { listOf(it.merchant, it.total?.toString().orEmpty(), it.recognizedText) },
            restore = { ReceiptDraft(it[0], it[1].toDoubleOrNull(), it[2]) }
        )
    }
}

class ReceiptFileProvider : FileProvider()

class ReceiptScannerState internal constructor(
    val isBusy: Boolean,
    val isProcessing: Boolean,
    private val launchCamera: () -> Unit
) {
    fun launch() = launchCamera()
}

/** Register once at screen level, even when the FAB is hidden by navigation. */
@Composable
fun rememberReceiptScanner(
    onResult: (ReceiptDraft) -> Unit,
    onError: (String) -> Unit
): ReceiptScannerState {
    val context = LocalContext.current.applicationContext
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var phase by rememberSaveable { mutableStateOf("idle") }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured && pendingUri != null) {
            phase = "processing"
        } else {
            deleteReceipt(context, pendingUri)
            pendingUri = null
            phase = "idle"
            if (captured) currentOnError("The receipt photo was lost. Please scan again.")
        }
    }

    // Restoring the URI and phase restarts interrupted OCR without reopening the camera.
    LaunchedEffect(phase, pendingUri) {
        if (phase != "processing") return@LaunchedEffect
        val uri = pendingUri
        try {
            if (uri == null) throw IOException("Missing receipt image")
            val image = withContext(Dispatchers.IO) {
                InputImage.fromFilePath(context, Uri.parse(uri))
            }
            val text = recognizeReceipt(image)
            coroutineContext.ensureActive()
            if (text.isBlank()) {
                currentOnError("No text found. Try a closer, well-lit photo or add the expense manually.")
            } else {
                currentOnResult(parseReceipt(text))
            }
        } catch (cancelled: CancellationException) {
            // A replacement activity still needs the same file to resume processing.
            throw cancelled
        } catch (_: Exception) {
            currentOnError("Could not read this receipt. Try another photo or add the expense manually.")
        }
        deleteReceipt(context, uri)
        pendingUri = null
        phase = "idle"
    }

    return ReceiptScannerState(
        isBusy = phase != "idle",
        isProcessing = phase == "processing",
        launchCamera = {
            if (phase == "idle") {
                try {
                    val directory = File(context.cacheDir, "receipts")
                    if (!directory.isDirectory && !directory.mkdirs()) {
                        throw IOException("Cannot create receipt cache")
                    }
                    // Remove only abandoned captures from this feature, never an active capture.
                    directory.listFiles()?.filter {
                        it.name.startsWith("receipt_") && it.extension == "jpg" &&
                            System.currentTimeMillis() - it.lastModified() > 86_400_000L
                    }?.forEach { it.delete() }
                    val photo = File.createTempFile("receipt_", ".jpg", directory)
                    val uri = try {
                        FileProvider.getUriForFile(context, "${context.packageName}.receipts", photo)
                    } catch (error: Exception) {
                        photo.delete()
                        throw error
                    }
                    pendingUri = uri.toString()
                    phase = "capturing"
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    deleteReceipt(context, pendingUri)
                    pendingUri = null
                    phase = "idle"
                    currentOnError("No camera app is available. Install or enable one, or add the expense manually.")
                } catch (_: Exception) {
                    deleteReceipt(context, pendingUri)
                    pendingUri = null
                    phase = "idle"
                    currentOnError("Could not open the camera. Please try again or add the expense manually.")
                }
            }
        }
    )
}

@Composable
fun ReceiptScannerFab(scanner: ReceiptScannerState, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = { if (!scanner.isBusy) scanner.launch() },
        modifier = modifier.semantics {
            contentDescription = if (scanner.isProcessing) "Reading receipt" else "Scan receipt"
            if (scanner.isBusy) disabled()
        },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        if (scanner.isProcessing) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
        }
    }
}

private suspend fun recognizeReceipt(image: InputImage): String = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        recognizer.process(image).addOnCompleteListener { task ->
            // ML Kit tasks continue after coroutine cancellation. Close only once work finishes.
            recognizer.close()
            if (continuation.isActive) {
                if (task.isSuccessful) continuation.resume(task.result.text)
                else continuation.resumeWithException(task.exception ?: IOException("OCR failed"))
            }
        }
    } catch (error: Exception) {
        recognizer.close()
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}

private fun deleteReceipt(context: Context, uriString: String?) {
    if (uriString == null) return
    runCatching {
        val uri = Uri.parse(uriString)
        if (uri.authority != "${context.packageName}.receipts") return
        val name = uri.lastPathSegment ?: return
        if (!Regex("receipt_[A-Za-z0-9_-]+\\.jpg").matches(name)) return
        val directory = File(context.cacheDir, "receipts").canonicalFile
        val file = File(directory, name).canonicalFile
        if (file.parentFile == directory) file.delete()
    }
}

private val totalLabel = Regex(
    "^(?:grand\\s+)?total(?:\\s+(?:due|amount|sale))?\\b\\s*[:=]?\\s*(.*)$",
    RegexOption.IGNORE_CASE
)
private val dollarAmount = Regex(
    "^(?:USD\\s*)?\\$?\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{2})?)\\s*(?:USD)?$",
    RegexOption.IGNORE_CASE
)
private val nonMerchant = Regex(
    "^(?:receipt|welcome|thank\\s+you|date|time|tel|phone|cashier|register|order|invoice|" +
        "sub\\s*total|total|grand\\s+total|amount\\s+due|balance\\s+due|tax|cash|change|" +
        "visa|mastercard|credit|debit)\\b",
    RegexOption.IGNORE_CASE
)

/** Only a TOTAL label and amount on the same OCR row qualify; conflicting totals stay blank. */
internal fun parseReceipt(text: String): ReceiptDraft {
    // Bound the saved review text so activity state stays well below Android's Bundle size limit.
    val reviewText = text.take(32_000)
    val lines = reviewText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val totals = lines.mapNotNull { line ->
        val label = totalLabel.matchEntire(line) ?: return@mapNotNull null
        val value = label.groupValues[1]
        dollarAmount.matchEntire(value)?.groupValues?.get(1)?.replace(",", "")
            ?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }.distinct()
    val merchant = lines.take(8).firstOrNull { line ->
        line.count { it.isLetter() } >= 2 &&
            !nonMerchant.containsMatchIn(line) &&
            !Regex("^[0-9]+\\s").containsMatchIn(line) &&
            !Regex("[0-9]+[.,][0-9]{2}\\b").containsMatchIn(line) &&
            !line.contains('@') && !line.contains("www.", true) && !line.contains("http", true)
    }.orEmpty()
    return ReceiptDraft(merchant, totals.singleOrNull(), reviewText)
}
