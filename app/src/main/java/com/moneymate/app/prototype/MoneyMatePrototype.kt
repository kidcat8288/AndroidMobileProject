package com.moneymate.app.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymate.app.report.ReportDateControls
import com.moneymate.app.report.ReportPeriod
import com.moneymate.app.report.reportDateRange
import java.text.NumberFormat
import java.util.Locale

/** Self-contained UI exploration. No camera, OCR, storage, or account data is used here. */
private val Forest = Color(0xFF10261F)
private val Green = Color(0xFF267552)
private val Mint = Color(0xFFDDF5E8)
private val Background = Color(0xFFF7FAF8)
private val Ink = Color(0xFF17251F)
private val Muted = Color(0xFF6A7972)
private val Red = Color(0xFFC6434D)
private val Gold = Color(0xFFF0AF45)
private val Blue = Color(0xFF659DCD)

enum class PrototypePage { Home, Activity, Reports, Budget }

private data class SampleTransaction(
    val title: String,
    val detail: String,
    val amount: String,
    val icon: ImageVector,
    val income: Boolean = false,
    val id: String = title,
    val daysAgo: Int = 0
)

private val sampleTransactions = listOf(
    SampleTransaction("September salary", "Salary · Yesterday", "+$3,200.00", Icons.Default.AccountBalanceWallet, true, daysAgo = 1),
    SampleTransaction("Weekly groceries", "Food · 2 days ago", "−$84.50", Icons.Default.Restaurant, daysAgo = 2),
    SampleTransaction("Metro pass", "Transport · 10 days ago", "−$42.00", Icons.Default.DirectionsCar, daysAgo = 10),
    SampleTransaction("New headphones", "Shopping · 20 days ago", "−$69.99", Icons.Default.ShoppingBag, daysAgo = 20)
)

private fun sampleValue(row: SampleTransaction): Double =
    row.amount.replace("+", "").replace("−", "").replace("$", "").replace(",", "").toDoubleOrNull() ?: 0.0

private fun sampleCurrency(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(value)

@Composable
fun MoneyMatePrototype(initialPage: PrototypePage = PrototypePage.Home) {
    var page by rememberSaveable { mutableIntStateOf(initialPage.ordinal) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val transactions = remember { mutableStateListOf<SampleTransaction>().also { it.addAll(sampleTransactions) } }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            secondary = Green,
            secondaryContainer = Mint,
            onSecondaryContainer = Forest,
            background = Background,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val tabs = listOf(
                        Icons.Default.Home to "Home",
                        Icons.AutoMirrored.Filled.ReceiptLong to "Activity",
                        Icons.Default.PieChart to "Reports",
                        Icons.Default.Savings to "Budget"
                    )
                    tabs.forEachIndexed { index, (icon, label) ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = { page = index },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (page < 2) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingActionButton(
                            onClick = { dialog = "receipt" },
                            shape = CircleShape,
                            containerColor = Mint,
                            contentColor = Forest
                        ) { Icon(Icons.Default.CameraAlt, contentDescription = "Preview receipt review") }
                        FloatingActionButton(
                            onClick = { dialog = "add" },
                            shape = CircleShape,
                            containerColor = Green,
                            contentColor = Color.White
                        ) { Icon(Icons.Default.Add, contentDescription = "Preview add transaction") }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    0 -> PrototypeHome(transactions = transactions, onAdd = { dialog = "add" })
                    1 -> PrototypeActivity(transactions = transactions, onEdit = { editingId = it.id })
                    2 -> PrototypeReports(transactions)
                    else -> PrototypeBudget()
                }
            }
        }
        if (dialog.isNotEmpty()) PrototypeTransactionDialog(
            receipt = dialog == "receipt",
            onDismiss = { dialog = "" }
        )
        transactions.firstOrNull { it.id == editingId }?.let { selected ->
            PrototypeEditTransaction(
                transaction = selected,
                onDismiss = { editingId = null },
                onSave = { updated ->
                    val index = transactions.indexOfFirst { it.id == selected.id }
                    if (index >= 0) transactions[index] = updated
                    editingId = null
                },
                onDeleteExpense = {
                    transactions.removeAll { it.id == selected.id && !it.income }
                    editingId = null
                }
            )
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun PrototypeHome(transactions: List<SampleTransaction>, onAdd: () -> Unit) {
    val income = transactions.filter { it.income }.sumOf(::sampleValue)
    val expenses = transactions.filterNot { it.income }.sumOf(::sampleValue)
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Good morning", "Your money snapshot") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Forest)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("TOTAL BALANCE", color = Color(0xFFBCE0C9), fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(sampleCurrency(income - expenses), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Color(0xFF426355))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BalanceDetail("Money in", sampleCurrency(income), Color(0xFF8DE5B8))
                        BalanceDetail("Money out", sampleCurrency(expenses), Color(0xFFFFA2A7))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Income", sampleCurrency(income), Icons.Default.ArrowDownward, Green, Modifier.weight(1f))
                SummaryTile("Spent", sampleCurrency(expenses), Icons.Default.ArrowUpward, Red, Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent activity", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text("Add new") }
            }
        }
        items(transactions, key = { it.id }) { PrototypeTransactionRow(it) }
    }
}

@Composable
private fun BalanceDetail(label: String, amount: String, color: Color) {
    Column {
        Text(label, color = Color(0xFFBCE0C9), fontSize = 12.sp)
        Text(amount, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryTile(label: String, amount: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, color = Muted, fontSize = 12.sp)
                Text(amount, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PrototypeTransactionRow(transaction: SampleTransaction, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
            Icon(transaction.icon, contentDescription = null, tint = Green)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.title, fontWeight = FontWeight.SemiBold)
            Text(transaction.detail, color = Muted, fontSize = 12.sp)
        }
        Text(transaction.amount, color = if (transaction.income) Green else Red, fontWeight = FontWeight.Bold)
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PrototypeActivity(transactions: List<SampleTransaction>, onEdit: (SampleTransaction) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Activity", "Search and review your transactions") }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search transactions") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Expenses", "Income").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        leadingIcon = if (selectedFilter == filter) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }
        item { Text("Recent transactions", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        items(transactions.filter { row ->
            row.title.contains(search, ignoreCase = true) && when (selectedFilter) {
                "Expenses" -> !row.income
                "Income" -> row.income
                else -> true
            }
        }, key = { it.id }) { row -> PrototypeTransactionRow(row, onClick = { onEdit(row) }) }
    }
}

@Composable
private fun PrototypeReports(transactions: List<SampleTransaction>) {
    var periodName by rememberSaveable { mutableStateOf(ReportPeriod.LAST_WEEK.name) }
    var customStart by rememberSaveable { mutableStateOf(0L) }
    var customEnd by rememberSaveable { mutableStateOf(0L) }
    val period = ReportPeriod.valueOf(periodName)
    val range = reportDateRange(period, customStart, customEnd)
    val categories = transactions.filterNot { it.income }
        .filter { range.contains(System.currentTimeMillis() - it.daysAgo * 86_400_000L) }
        .groupBy { it.detail.substringBefore(" · ") }
        .map { (name, rows) -> Triple(name, rows.sumOf(::sampleValue), when (name) {
            "Food" -> Gold
            "Shopping" -> Red
            "Transport" -> Blue
            else -> Green
        }) }
    val total = categories.sumOf { it.second }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Reports", "A clear view of your spending") }
        item {
            ReportDateControls(period, range) { selected, start, end ->
                periodName = selected.name
                customStart = start
                customEnd = end
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("EXPENSE BREAKDOWN", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(14.dp))
                    Canvas(Modifier.size(172.dp)) {
                        val stroke = 26.dp.toPx()
                        var start = -90f
                        categories.forEach { (_, value, color) ->
                            val sweep = if (total > 0) (value / total * 360f).toFloat() else 0f
                            drawArc(color, start, sweep - 3f, false,
                                Offset(stroke / 2, stroke / 2),
                                Size(size.width - stroke, size.height - stroke),
                                style = Stroke(stroke, cap = StrokeCap.Butt))
                            start += sweep
                        }
                    }
                    Text(sampleCurrency(total), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("total spent", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { Text("By category", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        items(categories) { (name, amount, color) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(sampleCurrency(amount), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (total > 0) (amount / total).toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = color,
                    trackColor = Mint
                )
            }
        }
    }
}

@Composable
private fun PrototypeBudget() {
    var budget by rememberSaveable { mutableStateOf("1500") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageTitle("Budget planner", "See how far your money can go")
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("MONTHLY SPENDING LIMIT", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("$1,233.51 left to spend", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green)
                Text("You have used 18% of this sample budget", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { 0.18f },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                    color = Green, trackColor = Mint)
            }
        }
        Text(
            "This screen uses sample amounts for layout preview.",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Mint).padding(16.dp),
            color = Ink,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PrototypeTransactionDialog(receipt: Boolean, onDismiss: () -> Unit) {
    var description by rememberSaveable { mutableStateOf(if (receipt) "Corner Market" else "") }
    var amount by rememberSaveable { mutableStateOf(if (receipt) "24.50" else "") }
    var category by rememberSaveable { mutableStateOf(if (receipt) "Other" else "Food") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (receipt) "Review receipt" else "Add transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (receipt) Text("Sample receipt preview. Check the merchant and total.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
                OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("$") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Food", "Shopping", "Other").forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrototypeEditTransaction(
    transaction: SampleTransaction,
    onDismiss: () -> Unit,
    onSave: (SampleTransaction) -> Unit,
    onDeleteExpense: () -> Unit
) {
    var description by rememberSaveable(transaction.id) { mutableStateOf(transaction.title) }
    var amount by rememberSaveable(transaction.id) {
        mutableStateOf(transaction.amount.replace("+", "").replace("−", "").replace("$", "").replace(",", ""))
    }
    var category by rememberSaveable(transaction.id) { mutableStateOf(transaction.detail.substringBefore(" · ")) }
    var confirmDelete by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    val choices = if (transaction.income) listOf("Salary", "Other") else listOf("Food", "Transport", "Shopping", "Housing", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction.income) "Edit income" else "Edit expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { character -> character.isDigit() || character == '.' } },
                    Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text(if (transaction.income) "Income category" else "Expense category", color = Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                    }
                }
                if (!transaction.income) TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete expense", color = Red)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && parsedAmount?.let { it.isFinite() && it > 0 } == true,
                onClick = {
                    parsedAmount?.takeIf { it.isFinite() && it > 0 && description.isNotBlank() }?.let { validAmount ->
                        val formatted = NumberFormat.getCurrencyInstance(Locale.US).format(validAmount)
                        onSave(transaction.copy(
                            title = description.trim(),
                            detail = "$category · ${transaction.detail.substringAfter(" · ", "Today")}",
                            amount = (if (transaction.income) "+" else "−") + formatted
                        ))
                    }
                }
            ) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this expense?") },
        text = { Text("${transaction.title} (${transaction.amount}) will be removed from Activity.") },
        confirmButton = { TextButton(onClick = onDeleteExpense) { Text("Delete", color = Red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep expense") } }
    )
}

@Preview(name = "MoneyMate · Home", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HomePrototypePreview() = MoneyMatePrototype(PrototypePage.Home)

@Preview(name = "MoneyMate · Activity", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ActivityPrototypePreview() = MoneyMatePrototype(PrototypePage.Activity)

@Preview(name = "MoneyMate · Reports", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ReportsPrototypePreview() = MoneyMatePrototype(PrototypePage.Reports)

@Preview(name = "MoneyMate · Budget", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun BudgetPrototypePreview() = MoneyMatePrototype(PrototypePage.Budget)

@Preview(name = "MoneyMate · Receipt review", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ReceiptPrototypePreview() {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green)) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            PrototypeTransactionDialog(receipt = true, onDismiss = {})
        }
    }
}
