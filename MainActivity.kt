package com.courier.analytics

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
fun FormDialog(title: String, labels: List<String>, onDismiss: () -> Unit, init: List<String> = emptyList(), onOk: (List<String>) -> Unit) {
    val v = remember { mutableStateListOf(*Array(labels.size) { i -> init.getOrElse(i) { "" } }) }
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
fun ShiftCard(shiftStart: Long, onFinish: () -> Unit) {
    val running = shiftStart > 0
    val now by produceState(System.currentTimeMillis(), running) {
        while (running) { value = System.currentTimeMillis(); delay(1000) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (running) "Смена идёт" else "Смена не начата", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sec = if (running) (now - shiftStart) / 1000 else 0
            Text("%02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            if (running) Button(onFinish, Modifier.fillMaxWidth()) { Text("Завершить смену") }
            else Button({ Store.update { it.copy(shiftStart = System.currentTimeMillis()) } }, Modifier.fillMaxWidth()) { Text("Начать смену") }
        }
    }
}

@Composable
fun Dashboard() {
    val d = Store.data
    var period by remember { mutableStateOf(Period.WEEK) }
    var finishing by remember { mutableStateOf(false) }
    val sum = remember(d, period) { summarize(d, period) }
    val top = remember(d, period) { topRestaurants(d, period) }
    Screen("Courier Analytics") {
        item(key = "shift") { ShiftCard(d.shiftStart) { finishing = true } }
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
        item(key = "fc") { ForecastCard(d) }
        item(key = "npd") { NpdCard(d) }
        item(key = "wd") { WeekdayCard(d, period) }
        item(key = "heat") { HeatCard(d) }
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

fun Double.s(): String = if (this == 0.0) "" else toString().removeSuffix(".0")

@Composable
fun Slots() {
    var editing by remember { mutableStateOf<Slot?>(null) }
    var adding by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var sortBy by remember { mutableIntStateOf(0) }
    val all = Store.data.slots
    val list = remember(all, q, sortBy) {
        all.filter { q.isBlank() || listOf(it.date, it.transport, it.type).any { x -> x.contains(q, true) } }
            .let { l -> when (sortBy) { 0 -> l.sortedByDescending { it.date + it.start }; 1 -> l.sortedByDescending { it.income }; else -> l.sortedByDescending { it.net } } }
    }
    Screen("Слоты", { IconButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") } }) {
        item { OutlinedTextField(q, { q = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Дата", "Доход", "Чистыми").forEachIndexed { i, l -> FilterChip(sortBy == i, { sortBy = i }, { Text(l) }) } } }
        items(list, key = { it.id }) { s ->
            Card(Modifier.clickable { editing = s }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("${s.date}  ${s.start}–${s.end}", fontWeight = FontWeight.SemiBold)
                    Text("${s.type} · ${s.transport} · ${s.orders} зак. · %.1f км · %.1f ч".format(s.dist, hoursOf(s)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val fuel = s.dist * costPerKm(Store.data.operating, s.transport)
                    Text("Чистыми ${s.net.rub()} · чаевые ${s.tips.rub()}" + if (fuel > 0) " · топливо/энергия ${fuel.rub()}" else "", color = Accent)
                    Row {
                        TextButton({ Store.update { d -> d.copy(slots = d.slots + s.copy(id = System.currentTimeMillis())) } }) { Text("Копия") }
                        TextButton({ Store.update { d -> d.copy(slots = d.slots.filter { it.id != s.id }) } }) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val e = editing
        FormDialog(if (e == null) "Новый слот" else "Слот", listOf("Дата (ГГГГ-ММ-ДД)", "Начало (ЧЧ:ММ)", "Конец (ЧЧ:ММ)", "Тип",
            "Транспорт (авто/мото/электровело/вело)", "Заказы", "Доход", "Чаевые", "Налог", "Км", "Часы (пусто = авто)", "Бонус", "Бонус за заказы"),
            { adding = false; editing = null },
            listOf(e?.date ?: LocalDate.now().toString(), e?.start ?: "", e?.end ?: "", e?.type ?: "Плановый", e?.transport ?: "",
                (e?.orders ?: 0).toString(), (e?.income ?: 0.0).s(), (e?.tips ?: 0.0).s(), (e?.tax ?: 0.0).s(), (e?.dist ?: 0.0).s(),
                (e?.hours ?: 0.0).s(), (e?.bonus ?: 0.0).s(), (e?.orderBonus ?: 0.0).s())) { f ->
            var n = (e ?: Slot()).copy(date = f[0], start = f[1], end = f[2], type = f[3], transport = f[4], orders = f[5].num().toInt(),
                income = f[6].num(), tips = f[7].num(), tax = f[8].num(), dist = f[9].num(), hours = f[10].num(), bonus = f[11].num(), orderBonus = f[12].num())
            val h = hoursOf(n); n = n.copy(hours = h, rph = if (h > 0) n.income / h else 0.0)
            Store.update { d -> d.copy(slots = if (e == null) d.slots + n else d.slots.map { if (it.id == e.id) n else it }) }
            adding = false; editing = null
        }
    }
}

@Composable
fun Expenses() {
    var adding by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<String?>(null) }
    val d = Store.data
    val list = remember(d.expenses, filter) { d.expenses.filter { filter == null || it.category == filter }.sortedByDescending { it.date } }
    Screen("Расходы", { IconButton({ adding = true }) { Icon(Icons.Default.Add, "Добавить") } }) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("Все") }) }
                items(d.categories) { c -> FilterChip(filter == c, { filter = c }, { Text(c) }) }
                item { AssistChip({ newCat = true }, { Text("+ категория") }) }
            }
        }
        if (filter != null) item { TextButton({ val c = filter; Store.update { it.copy(categories = it.categories.filter { x -> x != c }) }; filter = null }) { Text("Удалить категорию «$filter»") } }
        item { Tile("Всего", list.sumOf { it.amount }.rub(), Modifier.fillMaxWidth()) }
        if (filter == null) items(list.groupBy { it.category }.map { it.key to it.value.sumOf { e -> e.amount } }.sortedByDescending { it.second }) { (c, v) ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(c.ifBlank { "Без категории" }); Text(v.rub(), color = Accent) }
        }
        items(list, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${e.category} · ${e.amount.rub()}", fontWeight = FontWeight.SemiBold)
                    Text("${e.date} ${e.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ Store.update { x -> x.copy(expenses = x.expenses.filter { it.id != e.id }) } }) { Icon(Icons.Default.Delete, "Удалить") }
            }
        }
    }
    if (adding) FormDialog("Новый расход", listOf("Дата (ГГГГ-ММ-ДД)", "Категория", "Сумма", "Заметка"), { adding = false },
        listOf(LocalDate.now().toString(), filter ?: d.categories.lastOrNull().orEmpty())) { f ->
        Store.update { it.copy(expenses = it.expenses + Expense(date = f[0].ifBlank { LocalDate.now().toString() }, category = f[1], amount = f[2].num(), note = f[3]),
            categories = if (f[1].isBlank() || f[1] in it.categories) it.categories else it.categories + f[1]) }
        adding = false
    }
    if (newCat) FormDialog("Новая категория", listOf("Название"), { newCat = false }) { f ->
        if (f[0].isNotBlank()) Store.update { it.copy(categories = (it.categories + f[0].trim()).distinct()) }
        newCat = false
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
    val d = Store.data
    var msg by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    var opDialog by remember { mutableStateOf(false) }
    var npdDialog by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching {
            val t = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            Store.import(t, replace); "Импорт выполнен"
        }.getOrElse { "Ошибка импорта: ${it.message}" }
    }
    @Composable fun writer(mime: String, text: () -> String) = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(text()) }; "Экспорт выполнен"
        }.getOrElse { "Ошибка экспорта: ${it.message}" }
    }
    val exporter = writer("application/json") { Store.export() }
    val csvExporter = writer("text/csv") { csv(Store.data) }
    val o = d.operating
    Screen("Настройки и данные") {
        item { Section("Эксплуатационные расходы") {
            Text("Топливо ${o.fuelPrice.s()} ₽/л · авто ${o.carConsumption.s()} л/100 · мото ${o.motoConsumption.s()} л/100", fontSize = 12.sp)
            Text("Электричество ${o.electricityPrice.s()} ₽/кВт·ч · э-вело ${o.ebikeWh.s()} Вт·ч/км · вело ${o.bikeCost.s()} ₽/км", fontSize = 12.sp)
            Button({ opDialog = true }) { Text("Изменить") }
        } }
        item { Section("Лимит НПД") { Text(d.npdLimit.rub()); Button({ npdDialog = true }) { Text("Изменить") } } }
        item { Button({ replace = false; importer.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Импорт JSON (добавить)") } }
        item { OutlinedButton({ replace = true; importer.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Импорт JSON (заменить всё)") } }
        item { OutlinedButton({ exporter.launch("courier_backup.json") }, Modifier.fillMaxWidth()) { Text("Экспорт JSON") } }
        item { OutlinedButton({ csvExporter.launch("courier_slots.csv") }, Modifier.fillMaxWidth()) { Text("Экспорт слотов в CSV") } }
        if (msg.isNotEmpty()) item { Text(msg, color = Accent) }
    }
    if (opDialog) FormDialog("Эксплуатационные расходы", listOf("Топливо, ₽/л", "Авто, л/100 км", "Мото, л/100 км", "Электричество, ₽/кВт·ч", "Э-вело, Вт·ч/км", "Вело, ₽/км"),
        { opDialog = false }, listOf(o.fuelPrice, o.carConsumption, o.motoConsumption, o.electricityPrice, o.ebikeWh, o.bikeCost).map { it.s() }) { f ->
        Store.update { it.copy(operating = Operating(f[0].num(), f[1].num(), f[2].num(), f[3].num(), f[4].num(), f[5].num())) }
        opDialog = false
    }
    if (npdDialog) FormDialog("Лимит НПД, ₽", listOf("Лимит"), { npdDialog = false }, listOf(d.npdLimit.s())) { f ->
        Store.update { it.copy(npdLimit = f[0].num().takeIf { v -> v > 0 } ?: it.npdLimit) }; npdDialog = false
    }
}
