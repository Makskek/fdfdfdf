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
