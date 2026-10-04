package com.courier.analytics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.coroutines.*

@Serializable
data class OrderDetail(
    val time: String = "", val restaurant: String = "", val amount: Double = 0.0,
    val bonusPercent: Double = 0.0, val orderBonus: Double = 0.0
)

@Serializable
data class Slot(
    val id: Long = System.currentTimeMillis(), val date: String = "",
    val start: String = "", val end: String = "", val type: String = "Плановый",
    val transport: String = "", val orders: Int = 0, val income: Double = 0.0,
    val tips: Double = 0.0, val tax: Double = 0.0, val dist: Double = 0.0,
    val hours: Double = 0.0, val rph: Double = 0.0, val bonus: Double = 0.0,
    val orderBonus: Double = 0.0, val ordersDetail: List<OrderDetail> = emptyList()
) {
    val net: Double get() = income + tips + bonus + orderBonus - tax
}

@Serializable
data class Expense(
    val id: Long = System.currentTimeMillis(), val date: String = "",
    val category: String = "", val amount: Double = 0.0, val note: String = ""
)

@Serializable
data class Goal(
    val id: Long = System.currentTimeMillis(), val title: String = "",
    val target: Double = 0.0, val from: String = "", val to: String = ""
)

@Serializable
data class Operating(
    val fuelPrice: Double = 60.0, val carConsumption: Double = 8.0, val motoConsumption: Double = 4.0,
    val electricityPrice: Double = 7.0, val ebikeWh: Double = 18.0, val bikeCost: Double = 0.0
)

@Serializable
data class AppData(
    val slots: List<Slot> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val shiftStart: Long = 0L,
    val operating: Operating = Operating(),
    val npdLimit: Double = 2_400_000.0,
    val categories: List<String> = listOf("Топливо", "Зарядка", "Ремонт", "Обслуживание", "Прочее")
)

object Store {
    private val json = Json {
        ignoreUnknownKeys = true; coerceInputValues = true
        isLenient = true; encodeDefaults = true; prettyPrint = true
    }
    private lateinit var file: File
    var data by mutableStateOf(AppData())
        private set

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "data.json")
        if (file.exists()) runCatching { data = json.decodeFromString<AppData>(file.readText()) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    fun update(f: (AppData) -> AppData) {
        data = f(data)
        val snapshot = data
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(json.encodeToString(snapshot))
            tmp.renameTo(file)
        }
    }

    fun import(text: String, replace: Boolean) {
        val d = json.decodeFromString<AppData>(text)
        update {
            if (replace) d.copy(shiftStart = it.shiftStart)
            else it.copy(
                slots = (it.slots + d.slots).distinctBy { s -> s.id },
                expenses = (it.expenses + d.expenses).distinctBy { e -> e.id },
                goals = (it.goals + d.goals).distinctBy { g -> g.id }
            )
        }
    }

    fun export(): String = json.encodeToString(data)
}
