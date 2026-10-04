package com.courier.analytics

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Accent = Color(0xFFFACC15)
private val Scheme = darkColorScheme(
    primary = Accent, onPrimary = Color.Black, background = Color(0xFF09090B),
    surface = Color(0xFF18181B), onSurface = Color(0xFFFAFAFA), onBackground = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF27272A), onSurfaceVariant = Color(0xFFA1A1AA)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent { MaterialTheme(colorScheme = Scheme) { Root() } }
    }
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    val items = listOf(
        "Главная" to Icons.Default.Home, "Слоты" to Icons.Default.Menu,
        "Расходы" to Icons.Default.ShoppingCart, "Цели" to Icons.Default.Star,
        "Ещё" to Icons.Default.Settings
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (name, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, name) }, label = { Text(name, fontSize = 10.sp) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Dashboard(); 1 -> Slots(); 2 -> Expenses(); 3 -> Goals(); else -> More()
            }
        }
    }
}

@Composable
fun Tile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FormDialog(title: String, labels: List<String>, onDismiss: () -> Unit, onOk: (List<String>) -> Unit) {
    val v = remember { mutableStateListOf(*Array(labels.size) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(v[i], { v[i] = it }, label = { Text(l) }, singleLine = true)
                }
            }
        },
        confirmButton = { TextButton({ onOk(v.toList()) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun Screen(title: String, action: (@Composable () -> Unit)? = null, content: LazyListScopeFn) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                action?.invoke()
            }
        }
        content()
        item { Spacer(Modifier.height(16.dp)) }
    }
}
typealias LazyListScopeFn = androidx.compose.foundation.lazy.LazyListScope.() -> Unit

@Composable
fun Dashboard() {
    val d = Store.data
    var period by remember { mutableStateOf(Period.WEEK) }
    var finishing by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    val sum = summarize(d, period)
    val running = d.shiftStart > 0
    Screen("Courier Analytics") {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (running) "Смена идёт" else "Смена не начата", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val sec = if (running) (now - d.shiftStart) / 1000 else 0
                    Text("%02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    if (running) Button({ finishing = true }, Modifier.fillMaxWidth()) { Text("Завершить смену") }
                    else Button({ Store.update { it.copy(shiftStart = System.currentTimeMillis()) } }, Modifier.fillMaxWidth()) { Text("Начать смену") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.values().forEach {
                    FilterChip(period == it, { period = it }, { Text(it.label) })
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("Доход", sum.income.rub(), Modifier.weight(1f)); Tile("Чаевые", sum.tips.rub(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("Бонусы", sum.bonus.rub(), Modifier.weight(1f)); Tile("Налог", sum.tax.rub(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("Расходы", sum.expenses.rub(), Modifier.weight(1f)); Tile("Чистыми", sum.net.rub(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("Заказы", "${sum.orders}", Modifier.weight(1f)); Tile("Часы", "%.1f".format(sum.hours), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("Км", "%.1f".format(sum.dist), Modifier.weight(1f)); Tile("₽/час", sum.perHour.rub(), Modifier.weight(1f))
                }
            }
        }
        val top = topRestaurants(d, period)
        if (top.isNotEmpty()) {
            item { Text("Рестораны", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            items(top) { (name, cnt, sumAmt) ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("$name · $cnt"); Text(sumAmt.rub(), color = Accent)
                }
            }
        }
    }
    if (finishing) FormDialog("Итоги смены", listOf("Заказы", "Доход, ₽", "Чаевые, ₽", "Дистанция, км"),
        { finishing = false }) { f ->
        val zone = ZoneId.systemDefault()
        val st = Instant.ofEpochMilli(d.shiftStart).atZone(zone)
        val en = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)
        val hours = (System.currentTimeMillis() - d.shiftStart) / 3_600_000.0
        val inc = f[1].num()
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        Store.update {
            it.copy(
                shiftStart = 0,
                slots = it.slots + Slot(
                    date = st.toLocalDate().toString(), start = st.format(fmt), end = en.format(fmt),
                    orders = f[0].num().toInt(), income = inc, tips = f[2].num(), dist = f[3].num(),
                    hours = hours, rph = if (hours > 0) inc / hours else 0.0
                )
            )
        }
        finishing = false
    }
}

@Composable
fun Slots() {
    var adding by remember { mutableStateOf(false) }
    val list = Store.data.slots.sortedByDescending { it.date + it.start }
    Screen("Слоты", { IconButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") } }) {
        items(list, key = { it.id }) { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.date}  ${s.start}–${s.end}", fontWeight = FontWeight.SemiBold)
                        Text("${s.type} · ${s.transport} · ${s.orders} зак. · %.1f км".format(s.dist),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Чистыми ${s.net.rub()} · чаевые ${s.tips.rub()}", color = Accent)
                    }
                    IconButton({ Store.update { d -> d.copy(slots = d.slots.filter { it.id != s.id }) } }) {
                        Icon(Icons.Default.Delete, "Удалить")
                    }
                }
            }
        }
    }
    if (adding) FormDialog("Новый слот", listOf("Дата (ГГГГ-ММ-ДД)", "Начало (ЧЧ:ММ)", "Конец (ЧЧ:ММ)",
        "Транспорт", "Заказы", "Доход", "Чаевые", "Км", "Часы"), { adding = false }) { f ->
        val h = f[8].num()
        Store.update {
            it.copy(slots = it.slots + Slot(
                date = f[0].ifBlank { LocalDate.now().toString() }, start = f[1], end = f[2], transport = f[3],
                orders = f[4].num().toInt(), income = f[5].num(), tips = f[6].num(), dist = f[7].num(),
                hours = h, rph = if (h > 0) f[5].num() / h else 0.0
            ))
        }
        adding = false
    }
}

@Composable
fun Expenses() {
    var adding by remember { mutableStateOf(false) }
    val list = Store.data.expenses.sortedByDescending { it.date }
    Screen("Расходы", { IconButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") } }) {
        item { Tile("Всего", list.sumOf { it.amount }.rub(), Modifier.fillMaxWidth()) }
        items(list, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${e.category} · ${e.amount.rub()}", fontWeight = FontWeight.SemiBold)
                    Text("${e.date} ${e.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ Store.update { d -> d.copy(expenses = d.expenses.filter { it.id != e.id }) } }) {
                    Icon(Icons.Default.Delete, "Удалить")
                }
            }
        }
    }
    if (adding) FormDialog("Новый расход", listOf("Дата (ГГГГ-ММ-ДД)", "Категория", "Сумма", "Заметка"),
        { adding = false }) { f ->
        Store.update {
            it.copy(expenses = it.expenses + Expense(
                date = f[0].ifBlank { LocalDate.now().toString() }, category = f[1], amount = f[2].num(), note = f[3]
            ))
        }
        adding = false
    }
}

@Composable
fun Goals() {
    var adding by remember { mutableStateOf(false) }
    val d = Store.data
    Screen("Цели", { IconButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") } }) {
        items(d.goals, key = { it.id }) { g ->
            val p = goalProgress(d, g)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(g.title, fontWeight = FontWeight.SemiBold)
                        IconButton({ Store.update { s -> s.copy(goals = s.goals.filter { it.id != g.id }) } }) {
                            Icon(Icons.Default.Delete, "Удалить")
                        }
                    }
                    LinearProgressIndicator(progress = { if (g.target > 0) (p / g.target).toFloat().coerceIn(0f, 1f) else 0f },
                        Modifier.fillMaxWidth())
                    Text("${p.rub()} из ${g.target.rub()}  (${g.from} – ${g.to})", fontSize = 12.sp)
                }
            }
        }
    }
    if (adding) FormDialog("Новая цель", listOf("Название", "Сумма, ₽", "С (ГГГГ-ММ-ДД)", "По (ГГГГ-ММ-ДД)"),
        { adding = false }) { f ->
        Store.update { it.copy(goals = it.goals + Goal(title = f[0], target = f[1].num(), from = f[2], to = f[3])) }
        adding = false
    }
}

@Composable
fun More() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var msg by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching {
            val t = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            Store.import(t, replace); "Импорт выполнен"
        }.getOrElse { "Ошибка импорта: ${it.message}" }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(Store.export()) }; "Экспорт выполнен"
        }.getOrElse { "Ошибка экспорта: ${it.message}" }
    }
    Screen("Данные") {
        item { Button({ replace = false; importer.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Импорт JSON (добавить)") } }
        item { OutlinedButton({ replace = true; importer.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Импорт JSON (заменить всё)") } }
        item { OutlinedButton({ exporter.launch("courier_backup.json") }, Modifier.fillMaxWidth()) { Text("Экспорт JSON") } }
        if (msg.isNotEmpty()) item { Text(msg, color = Accent) }
    }
}
