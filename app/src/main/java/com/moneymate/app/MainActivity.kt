package com.moneymate.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.moneymate.app.receipt.ReceiptDraft
import com.moneymate.app.receipt.ReceiptScannerFab
import com.moneymate.app.receipt.rememberReceiptScanner
import com.moneymate.app.report.ReportDateControls
import com.moneymate.app.report.ReportPeriod
import com.moneymate.app.report.reportDateRange
import kotlinx.coroutines.launch

private val Forest = Color(0xFF10261F)
private val Green = Color(0xFF267552)
private val Mint = Color(0xFFDDF5E8)
private val Background = Color(0xFFF7FAF8)
private val Ink = Color(0xFF17251F)
private val Muted = Color(0xFF6A7972)
private val Red = Color(0xFFC6434D)
private val Gold = Color(0xFFF0AF45)
private val Blue = Color(0xFF659DCD)

enum class TransactionKind { INCOME, EXPENSE }

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val amount: Double,
    val kind: TransactionKind,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MoneyMate() }
    }
}

@Composable
internal fun MoneyMate() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { TransactionStore(context) }
    val transactions = remember { mutableStateListOf<Transaction>().also { it.addAll(store.load()) } }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var receiptDraft by rememberSaveable(stateSaver = ReceiptDraft.Saver) { mutableStateOf(ReceiptDraft()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scanner = rememberReceiptScanner(
        onResult = { draft -> receiptDraft = draft; adding = true },
        onError = { message -> scope.launch { snackbar.showSnackbar(message, withDismissAction = true) } }
    )
    val openManual: () -> Unit = {
        if (!scanner.isBusy) {
            receiptDraft = ReceiptDraft()
            adding = true
        }
    }
    val colors = lightColorScheme(primary = Green, secondary = Green, background = Background, surface = Color.White, onSurface = Ink)

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = Background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val tabs = listOf(Icons.Default.Home to "Home", Icons.Default.ReceiptLong to "Activity", Icons.Default.PieChart to "Reports", Icons.Default.Savings to "Budget")
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(tab.first, tab.second) }, label = { Text(tab.second) })
                    }
                }
            },
            floatingActionButton = {
                if (page < 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                        ReceiptScannerFab(scanner)
                        FloatingActionButton(onClick = openManual, containerColor = Green, contentColor = Color.White, shape = CircleShape) {
                            Icon(Icons.Default.Add, "Add transaction")
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    0 -> Dashboard(transactions, onAdd = openManual)
                    1 -> ActivityPage(
                        transactions = transactions,
                        onUpdate = { updated ->
                            val index = transactions.indexOfFirst { it.id == updated.id }
                            if (index >= 0) {
                                transactions[index] = updated
                                store.save(transactions)
                            }
                        },
                        onDeleteExpense = { id ->
                            val index = transactions.indexOfFirst { it.id == id && it.kind == TransactionKind.EXPENSE }
                            if (index >= 0) {
                                transactions.removeAt(index)
                                store.save(transactions)
                            }
                        }
                    )
                    2 -> ReportsPage(transactions)
                    else -> BudgetPage(transactions)
                }
            }
        }
        if (adding) AddTransaction(
            receiptDraft = receiptDraft.takeIf { it.recognizedText.isNotBlank() },
            onDismiss = { adding = false; receiptDraft = ReceiptDraft() },
            onSave = { transaction ->
                if (adding) {
                    adding = false
                    transactions.add(0, transaction)
                    store.save(transactions)
                    receiptDraft = ReceiptDraft()
                }
            }
        )
    }
}

@Composable
private fun Dashboard(transactions: List<Transaction>, onAdd: () -> Unit) {
    val income = transactions.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amount }
    val expenses = transactions.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Title("Good morning", "Your money snapshot") }
        item { BalanceCard(income - expenses, income, expenses) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Summary("Income", income, Green, Icons.Default.ArrowDownward, Modifier.weight(1f))
                Summary("Spent", expenses, Red, Icons.Default.ArrowUpward, Modifier.weight(1f))
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Recent activity", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f)); TextButton(onClick = onAdd) { Text("Add new") } } }
        items(transactions.take(5), key = { it.id }) { TransactionRow(it) }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column { Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink); Text(subtitle, color = Muted, fontSize = 14.sp) }
}

@Composable
private fun BalanceCard(balance: Double, income: Double, expenses: Double) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Forest)) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("TOTAL BALANCE", color = Color(0xFFBCE0C9), letterSpacing = 1.sp, fontSize = 12.sp)
            Text(currency(balance), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp, modifier = Modifier.padding(top = 4.dp))
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Color(0xFF426355))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniValue("Money in", income, Color(0xFF8DE5B8)); MiniValue("Money out", expenses, Color(0xFFFFA2A7))
            }
        }
    }
}

@Composable
private fun MiniValue(label: String, amount: Double, color: Color) { Column { Text(label, color = Color(0xFFBCE0C9), fontSize = 12.sp); Text(currency(amount), color = color, fontWeight = FontWeight.SemiBold) } }

@Composable
private fun Summary(label: String, amount: Double, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color); Spacer(Modifier.width(8.dp)); Column { Text(label, color = Muted, fontSize = 12.sp); Text(currency(amount), fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun ActivityPage(
    transactions: List<Transaction>,
    onUpdate: (Transaction) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var expensesOnly by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = transactions.filter { (it.name.contains(search, true) || it.category.contains(search, true)) && (!expensesOnly || it.kind == TransactionKind.EXPENSE) }.sortedByDescending { it.timestamp }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("Activity", "Search, filter, and sort your records") }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search transactions") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
        item {
            FilterChip(
                selected = expensesOnly,
                onClick = { expensesOnly = !expensesOnly },
                label = { Text("Expenses only") },
                leadingIcon = {
                    if (expensesOnly) {
                        Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                    }
                }
            )
        }
        if (filtered.isEmpty()) item { Empty("No matching transactions", "Try another search or add a record.") }
        items(filtered, key = { it.id }) { transaction ->
            TransactionRow(transaction, onClick = { editingId = transaction.id })
        }
    }
    transactions.firstOrNull { it.id == editingId }?.let { selected ->
        EditTransaction(
            transaction = selected,
            onDismiss = { editingId = null },
            onSave = { updated -> onUpdate(updated); editingId = null },
            onDeleteExpense = { onDeleteExpense(selected.id); editingId = null }
        )
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onClick: (() -> Unit)? = null) {
    val symbol = when (transaction.category) { "Food" -> Icons.Default.Restaurant; "Transport" -> Icons.Default.DirectionsCar; "Housing" -> Icons.Default.Home; "Shopping" -> Icons.Default.ShoppingBag; "Salary" -> Icons.Default.AccountBalanceWallet; else -> Icons.Default.Payments }
    val amountColor = if (transaction.kind == TransactionKind.INCOME) Green else Red
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) { Icon(symbol, null, tint = Green) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(transaction.name, fontWeight = FontWeight.SemiBold); Text("${transaction.category} · ${displayDate(transaction.timestamp)}", color = Muted, fontSize = 12.sp) }
        Text((if (transaction.kind == TransactionKind.INCOME) "+" else "−") + currency(transaction.amount), color = amountColor, fontWeight = FontWeight.Bold)
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTransaction(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDeleteExpense: () -> Unit
) {
    var name by rememberSaveable(transaction.id) { mutableStateOf(transaction.name) }
    var amount by rememberSaveable(transaction.id) { mutableStateOf(transaction.amount.toString()) }
    var category by rememberSaveable(transaction.id) { mutableStateOf(transaction.category) }
    var confirmDelete by rememberSaveable(transaction.id) { mutableStateOf(false) }
    var submitted by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    val choices = if (transaction.kind == TransactionKind.INCOME) {
        listOf("Salary", "Other")
    } else {
        listOf("Food", "Transport", "Shopping", "Housing", "Other")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction.kind == TransactionKind.INCOME) "Edit income" else "Edit expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { character -> character.isDigit() || character == '.' } },
                    Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text(if (transaction.kind == TransactionKind.INCOME) "Income category" else "Expense category", color = Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { choice ->
                        FilterChip(selected = category == choice, onClick = { category = choice }, label = { Text(choice) })
                    }
                }
                if (transaction.kind == TransactionKind.EXPENSE) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !submitted) {
                        Text("Delete expense", color = Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitted && name.isNotBlank() && parsedAmount?.let { it.isFinite() && it > 0 } == true,
                onClick = {
                    amount.toDoubleOrNull()?.takeIf { !submitted && name.isNotBlank() && it.isFinite() && it > 0 }
                        ?.let { validAmount ->
                            submitted = true
                            onSave(transaction.copy(name = name.trim(), amount = validAmount, category = category))
                        }
                }
            ) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this expense?") },
            text = { Text("${transaction.name} (${currency(transaction.amount)}) will be removed from Activity and your totals.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!submitted) {
                        submitted = true
                        onDeleteExpense()
                    }
                }) { Text("Delete", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep expense") } }
        )
    }
}

@Composable
private fun ReportsPage(transactions: List<Transaction>) {
    var periodName by rememberSaveable { mutableStateOf(ReportPeriod.LAST_WEEK.name) }
    var customStart by rememberSaveable { mutableLongStateOf(0L) }
    var customEnd by rememberSaveable { mutableLongStateOf(0L) }
    val period = ReportPeriod.valueOf(periodName)
    val range = reportDateRange(period, customStart, customEnd)
    val grouping = transactions.filter { it.kind == TransactionKind.EXPENSE && range.contains(it.timestamp) }
        .groupBy { it.category }.mapValues { it.value.sumOf { row -> row.amount } }.toList().sortedByDescending { it.second }
    val total = grouping.sumOf { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Title("Reports", "A clear view of your spending") }
        item {
            ReportDateControls(period, range) { selected, start, end ->
                periodName = selected.name
                customStart = start
                customEnd = end
            }
        }
        item { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("EXPENSE BREAKDOWN", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp); Spacer(Modifier.height(12.dp)); Donut(grouping, total); Text(currency(total), fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("total spent", color = Muted, fontSize = 12.sp) } } }
        item { Text("By category", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        if (grouping.isEmpty()) item { Empty("No expenses yet", "Your spending report will appear here.") }
        items(grouping) { item -> CategoryBar(item.first, item.second, total) }
    }
}

@Composable
private fun Donut(items: List<Pair<String, Double>>, total: Double) {
    Canvas(Modifier.size(175.dp)) {
        val width = 26.dp.toPx(); var start = -90f
        if (total == 0.0) drawCircle(Mint, style = Stroke(width))
        items.forEach { item ->
            val sweep = (item.second / total * 360).toFloat()
            drawArc(categoryColor(item.first), start, sweep - 3f, false, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun CategoryBar(category: String, amount: Double, total: Double) {
    val progress = if (total == 0.0) 0f else (amount / total).toFloat()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(category, fontWeight = FontWeight.SemiBold); Text(currency(amount), fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = categoryColor(category), trackColor = Mint) }
}

@Composable
private fun BudgetPage(transactions: List<Transaction>) {
    var input by rememberSaveable { mutableStateOf("1500") }
    val budget = input.toDoubleOrNull() ?: 0.0
    val spent = transactions.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amount }
    val remaining = budget - spent
    val ratio = if (budget == 0.0) 0f else (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val advice = when { budget == 0.0 -> "Set your monthly spending limit to begin estimating."; remaining < 0 -> "You are over budget. Consider postponing optional spending."; ratio > .8f -> "You are close to your spending limit. Plan the rest of the month carefully."; else -> "You are pacing well. Keep monitoring your biggest expense category." }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Title("Budget planner", "See how far your money can go")
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(20.dp)) { Text("MONTHLY SPENDING LIMIT", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp); OutlinedTextField(input, { input = it.filter { character -> character.isDigit() || character == '.' } }, Modifier.fillMaxWidth().padding(top = 12.dp), prefix = { Text("$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(20.dp)); Text("${currency(kotlin.math.abs(remaining))} ${if (remaining >= 0) "left to spend" else "over budget"}", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if (remaining >= 0) Green else Red); Text("You have used ${(ratio * 100).toInt()}% of this budget", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(14.dp)); LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), color = if (ratio > .8f) Gold else Green, trackColor = Mint) } }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Mint).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null, tint = Green); Spacer(Modifier.width(12.dp)); Text(advice, fontSize = 14.sp, color = Ink) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTransaction(
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    receiptDraft: ReceiptDraft? = null
) {
    var name by rememberSaveable { mutableStateOf(receiptDraft?.merchant?.ifBlank { "Scanned receipt" }.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(receiptDraft?.total?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(if (receiptDraft == null) "Food" else "Other") }
    var kind by rememberSaveable { mutableStateOf(TransactionKind.EXPENSE) }
    var showRecognizedText by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val categories = listOf("Food", "Transport", "Shopping", "Housing", "Salary", "Other")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (receiptDraft == null) "Add transaction" else "Review receipt", fontWeight = FontWeight.Bold) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (receiptDraft != null) {
            Text("Check the merchant and total before saving. You can edit every field.", fontSize = 13.sp, color = Muted)
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, singleLine = true)
        OutlinedTextField(amount, { amount = it.filter { character -> character.isDigit() || character == '.' } }, Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TransactionKind.entries.forEach { choice -> FilterChip(selected = kind == choice, onClick = { kind = choice }, label = { Text(choice.name.lowercase().replaceFirstChar { it.titlecase() }) }) } }
        Text("Category", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { choice -> FilterChip(selected = category == choice, onClick = { category = choice }, label = { Text(choice) }) }
        }
        if (receiptDraft != null) {
            TextButton(onClick = { showRecognizedText = !showRecognizedText }) {
                Text(if (showRecognizedText) "Hide receipt text" else "Show receipt text")
            }
            if (showRecognizedText) SelectionContainer { Text(receiptDraft.recognizedText, fontSize = 13.sp) }
        }
    } }, confirmButton = {
        TextButton(
            enabled = !submitted && amount.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true,
            onClick = {
                amount.toDoubleOrNull()?.takeIf { !submitted && it.isFinite() && it > 0 }?.let { parsedAmount ->
                    submitted = true
                    onSave(
                        Transaction(
                            name = name.ifBlank { category },
                            category = category,
                            amount = parsedAmount,
                            kind = kind
                        )
                    )
                }
            }
        ) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun Empty(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox, null, tint = Muted, modifier = Modifier.size(38.dp)); Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)); Text(subtitle, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center) } }

private fun categoryColor(category: String) = when (category) { "Food" -> Gold; "Transport" -> Blue; "Shopping" -> Red; "Housing" -> Green; else -> Color(0xFF9B78BF) }
private fun currency(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(value)
private fun displayDate(value: Long): String = SimpleDateFormat("MMM d", Locale.US).format(Date(value))

private class TransactionStore(context: Context) {
    private val prefs = context.getSharedPreferences("moneymate", Context.MODE_PRIVATE)
    fun load(): List<Transaction> {
        val saved = prefs.getString("items", null) ?: return samples()
        return saved.split("\\n").mapNotNull { line ->
            val p = line.split("|", limit = 6)
            if (p.size == 6) runCatching { Transaction(p[0], p[1], p[2], p[3].toDouble(), TransactionKind.valueOf(p[4]), p[5].toLong()) }.getOrNull() else null
        }
    }
    fun save(items: List<Transaction>) { prefs.edit().putString("items", items.joinToString("\\n") { "${it.id}|${it.name.replace("|", " ")}|${it.category}|${it.amount}|${it.kind}|${it.timestamp}" }).apply() }
}

private fun samples() = listOf(
    Transaction("salary", "September salary", "Salary", 3200.0, TransactionKind.INCOME, System.currentTimeMillis() - 86_400_000L),
    Transaction("groceries", "Weekly groceries", "Food", 84.50, TransactionKind.EXPENSE, System.currentTimeMillis() - 172_800_000L),
    Transaction("metro", "Metro pass", "Transport", 42.0, TransactionKind.EXPENSE, System.currentTimeMillis() - 259_200_000L),
    Transaction("rent", "Apartment rent", "Housing", 950.0, TransactionKind.EXPENSE, System.currentTimeMillis() - 345_600_000L),
    Transaction("headphones", "Headphones", "Shopping", 69.99, TransactionKind.EXPENSE, System.currentTimeMillis() - 432_000_000L)
)
