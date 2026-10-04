package com.courier.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFFACC15)

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); content()
        }
    }
}

@Composable
fun ForecastCard(d: AppData) {
    val (f, cur, perDay) = remember(d) { forecast(d) }
    Section("Прогноз на месяц") {
        Text(f.rub(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text("Сейчас: ${cur.rub()} · темп ${perDay.rub()}/день", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NpdCard(d: AppData) {
    val n = remember(d) { npd(d) }
    val col = when { n.pct >= 90 -> Color(0xFFEF4444); n.pct >= 70 -> Gold; else -> Color(0xFF22C55E) }
    Section("Лимит НПД") {
        LinearProgressIndicator(progress = { (n.pct / 100).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = col)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Заработано"); Text(n.earned.rub()) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Осталось"); Text(n.left.rub()) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Использовано"); Text("%.1f%%".format(n.pct), color = col) }
        Text(n.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WeekdayCard(d: AppData, p: Period) {
    val st = remember(d, p) { weekdayStats(d, p) }; val max = st.maxOf { it.third }.coerceAtLeast(1.0)
    Section("По дням недели (средний доход за смену)") {
        st.forEach { (name, cnt, avg) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, Modifier.width(28.dp))
                LinearProgressIndicator(progress = { (avg / max).toFloat() }, Modifier.weight(1f))
                Text(if (cnt == 0) "—" else avg.rub(), Modifier.width(80.dp), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HeatCard(d: AppData) {
    val r = remember(d) { hourRates(d) }; val max = r.maxOf { it.second }.coerceAtLeast(1.0)
    Section("Тепловая карта по часам (₽/ч)") {
        (0..3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..5).forEach { col ->
                    val h = row * 6 + col; val (cnt, rate) = r[h]
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(Gold.copy(alpha = if (cnt > 0) (rate / max).toFloat().coerceIn(0.1f, 1f) else 0.05f))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val c = if (cnt > 0 && rate / max > 0.5) Color.Black else Color.White
                        Text("%02d".format(h), fontSize = 10.sp, color = c)
                        Text(if (cnt > 0) "${Math.round(rate)}" else "—", fontSize = 11.sp, color = c)
                    }
                }
            }
        }
    }
}
