package com.courier.analytics

import java.time.LocalDate

enum class Period(val label: String) { DAY("День"), WEEK("Неделя"), MONTH("Месяц"), ALL("Всё") }

fun String.num(): Double = replace(',', '.').trim().toDoubleOrNull() ?: 0.0
fun Double.rub(): String = "%,.0f ₽".format(this).replace(',', ' ')

fun inPeriod(date: String, p: Period, today: LocalDate = LocalDate.now()): Boolean {
    if (p == Period.ALL) return true
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
    return when (p) {
        Period.DAY -> d == today
        Period.WEEK -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today)
        Period.MONTH -> d.year == today.year && d.month == today.month
        Period.ALL -> true
    }
}

data class Summary(
    val income: Double, val tips: Double, val bonus: Double, val tax: Double,
    val expenses: Double, val orders: Int, val hours: Double, val dist: Double
) {
    val net get() = income + tips + bonus - tax - expenses
    val perHour get() = if (hours > 0) (income + tips + bonus) / hours else 0.0
    val avgOrder get() = if (orders > 0) income / orders else 0.0
}

fun summarize(d: AppData, p: Period): Summary {
    val s = d.slots.filter { inPeriod(it.date, p) }
    val e = d.expenses.filter { inPeriod(it.date, p) }
    return Summary(
        s.sumOf { it.income }, s.sumOf { it.tips }, s.sumOf { it.bonus + it.orderBonus },
        s.sumOf { it.tax }, e.sumOf { it.amount }, s.sumOf { it.orders },
        s.sumOf { it.hours }, s.sumOf { it.dist }
    )
}

fun topRestaurants(d: AppData, p: Period): List<Triple<String, Int, Double>> =
    d.slots.filter { inPeriod(it.date, p) }.flatMap { it.ordersDetail }
        .filter { it.restaurant.isNotBlank() }.groupBy { it.restaurant }
        .map { (k, v) -> Triple(k, v.size, v.sumOf { it.amount }) }
        .sortedByDescending { it.third }.take(5)

fun goalProgress(d: AppData, g: Goal): Double = d.slots
    .filter { it.date >= g.from && it.date <= g.to }.sumOf { it.net }

fun costPerKm(o: Operating, transport: String): Double = when (transport.lowercase()) {
    "авто" -> o.fuelPrice * o.carConsumption / 100
    "мото" -> o.fuelPrice * o.motoConsumption / 100
    "электровело" -> o.electricityPrice * o.ebikeWh / 1000
    else -> o.bikeCost
}

fun hoursOf(s: Slot): Double {
    if (s.hours > 0) return s.hours
    val a = s.start.split(":").mapNotNull { it.toIntOrNull() }
    val b = s.end.split(":").mapNotNull { it.toIntOrNull() }
    if (a.size < 2 || b.size < 2) return 0.0
    var m = (b[0] * 60 + b[1]) - (a[0] * 60 + a[1])
    if (m < 0) m += 1440
    return m / 60.0
}

/** Прогноз на месяц: (прогноз, сейчас, в день) */
fun forecast(d: AppData, today: LocalDate = LocalDate.now()): Triple<Double, Double, Double> {
    val net = summarize(d, Period.MONTH).net
    val days = today.dayOfMonth
    val perDay = net / days
    return Triple(perDay * today.lengthOfMonth(), net, perDay)
}

data class Npd(val earned: Double, val limit: Double, val note: String) {
    val pct get() = if (limit > 0) earned / limit * 100 else 0.0
    val left get() = maxOf(0.0, limit - earned)
}

fun npd(d: AppData, today: LocalDate = LocalDate.now()): Npd {
    val earned = d.slots.filter { it.date.startsWith(today.year.toString()) }.sumOf { it.income + it.tips + it.bonus }
    val perDay = earned / maxOf(1, today.dayOfYear)
    val note = when {
        d.npdLimit > 0 && earned >= d.npdLimit -> "Лимит превышен на ${(earned - d.npdLimit).rub()}."
        earned > 0 && perDay * 365 > d.npdLimit ->
            "При текущем темпе лимит закончится примерно ${today.plusDays(Math.ceil((d.npdLimit - earned) / perDay).toLong())}."
        earned > 0 -> "За год выйдет около ${(perDay * 365).rub()} — в пределах лимита."
        else -> "В этом году доходов пока нет."
    }
    return Npd(earned, d.npdLimit, note)
}

private val WD = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** (день, число смен, средний доход за смену) */
fun weekdayStats(d: AppData, p: Period): List<Triple<String, Int, Double>> {
    val by = d.slots.filter { inPeriod(it.date, p) }.mapNotNull { s ->
        runCatching { LocalDate.parse(s.date).dayOfWeek.value - 1 to s }.getOrNull()
    }.groupBy({ it.first }, { it.second })
    return WD.mapIndexed { i, n -> val l = by[i].orEmpty(); Triple(n, l.size, if (l.isEmpty()) 0.0 else l.sumOf { it.income } / l.size) }
}

/** по часу начала слота: (число слотов, ₽/ч чистыми) */
fun hourRates(d: AppData): List<Pair<Int, Double>> {
    val net = DoubleArray(24); val h = DoubleArray(24); val c = IntArray(24)
    d.slots.forEach { s ->
        val hr = s.start.split(":").firstOrNull()?.toIntOrNull() ?: return@forEach
        if (hr in 0..23) { net[hr] += s.income + s.tips + s.bonus - Math.abs(s.tax); h[hr] += hoursOf(s); c[hr]++ }
    }
    return (0..23).map { c[it] to (if (h[it] > 0) net[it] / h[it] else 0.0) }
}

fun csv(d: AppData): String = buildString {
    appendLine("date;start;end;type;transport;orders;income;tips;tax;dist;hours;bonus;orderBonus;net")
    d.slots.sortedBy { it.date + it.start }.forEach {
        appendLine(listOf(it.date, it.start, it.end, it.type, it.transport, it.orders, it.income, it.tips, it.tax,
            it.dist, hoursOf(it), it.bonus, it.orderBonus, it.net).joinToString(";"))
    }
}
