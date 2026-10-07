package pl.nawyki.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class HabitType { GOOD, BAD }
private enum class DailyStatus { SUCCESS, FAILURE }
private enum class AppScreen { HABITS, STATS, CALENDAR, SETTINGS }

private data class Habit(
    val id: Long,
    val name: String,
    val type: HabitType,
    val createdAt: String
)

private data class StatusKey(val habitId: Long, val date: String)

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0
)

private val AppBackground = Color(0xFF0B0B0F)
private val AppSurface = Color(0xFF17181F)
private val GoodColor = Color(0xFF29D17D)
private val BadColor = Color(0xFFFF6B6B)
private val AccentColor = Color(0xFF7C5CFF)
private val NeutralColor = Color(0xFF404552)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = darkColorScheme(
                primary = AccentColor,
                secondary = GoodColor,
                tertiary = BadColor,
                background = AppBackground,
                surface = AppSurface,
                surfaceVariant = Color(0xFF20222B)
            )
            MaterialTheme(colorScheme = colors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HabitApp(applicationContext)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitApp(context: Context) {
    val repository = remember { HabitRepository(context) }
    val habits: SnapshotStateList<Habit> = remember {
        mutableStateListOf<Habit>().apply { addAll(repository.loadHabits()) }
    }
    val statuses: SnapshotStateMap<StatusKey, DailyStatus> = remember {
        mutableStateMapOf<StatusKey, DailyStatus>().apply { putAll(repository.loadStatuses()) }
    }

    var reminderSettings by remember { mutableStateOf(repository.loadReminderSettings()) }
    var screen by remember { mutableStateOf(AppScreen.HABITS) }
    var filter by remember { mutableStateOf<HabitType?>(null) }
    var showHabitDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    val today = LocalDate.now()
    val todayKey = today.toString()
    val todayMarked = habits.count { statuses[StatusKey(it.id, todayKey)] != null }
    val todaySuccess = habits.count { statuses[StatusKey(it.id, todayKey)] == DailyStatus.SUCCESS }

    fun persistHabitsAndStatuses() {
        repository.saveHabits(habits)
        repository.saveStatuses(statuses)
    }

    fun saveReminder(newValue: ReminderSettings) {
        reminderSettings = newValue
        repository.saveReminderSettings(newValue)
    }

    LaunchedEffect(reminderSettings) {
        ReminderScheduler.update(context, reminderSettings)
    }

    val topTitle = when (screen) {
        AppScreen.HABITS -> "Nawyki"
        AppScreen.STATS -> "Statystyki"
        AppScreen.CALENDAR -> "Kalendarz"
        AppScreen.SETTINGS -> "Ustawienia"
    }

    val topSubtitle = when (screen) {
        AppScreen.HABITS -> "Dzisiaj: $todaySuccess/$todayMarked sukcesów"
        AppScreen.STATS -> "Podsumowanie postępów"
        AppScreen.CALENDAR -> "Przegląd miesiąca"
        AppScreen.SETTINGS -> "Przypomnienia i opcje"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(topTitle, fontWeight = FontWeight.Bold)
                        Text(topSubtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        },
        floatingActionButton = {
            if (screen == AppScreen.HABITS) {
                FloatingActionButton(onClick = {
                    editingHabit = null
                    showHabitDialog = true
                }) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = {
                            Text(
                                when (item) {
                                    AppScreen.HABITS -> "🏠"
                                    AppScreen.STATS -> "📊"
                                    AppScreen.CALENDAR -> "📅"
                                    AppScreen.SETTINGS -> "⚙️"
                                }
                            )
                        },
                        label = {
                            Text(
                                when (item) {
                                    AppScreen.HABITS -> "Nawyki"
                                    AppScreen.STATS -> "Staty"
                                    AppScreen.CALENDAR -> "Kalendarz"
                                    AppScreen.SETTINGS -> "Opcje"
                                }
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (screen) {
                AppScreen.HABITS -> HabitsScreen(
                    habits = habits,
                    statuses = statuses,
                    today = today,
                    filter = filter,
                    onFilterChange = { filter = it },
                    onStatus = { habit, status ->
                        val key = StatusKey(habit.id, todayKey)
                        if (statuses[key] == status) statuses.remove(key) else statuses[key] = status
                        persistHabitsAndStatuses()
                    },
                    onDelete = { habit ->
                        habits.removeAll { it.id == habit.id }
                        statuses.keys.filter { it.habitId == habit.id }.toList().forEach { statuses.remove(it) }
                        persistHabitsAndStatuses()
                    },
                    onEdit = { habit ->
                        editingHabit = habit
                        showHabitDialog = true
                    }
                )

                AppScreen.STATS -> StatsScreen(habits = habits, statuses = statuses, today = today)
                AppScreen.CALENDAR -> CalendarScreen(habits = habits, statuses = statuses, today = today)
                AppScreen.SETTINGS -> SettingsScreen(
                    reminderSettings = reminderSettings,
                    onReminderChange = { saveReminder(it) },
                    onPickTime = {
                        val current = reminderSettings
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                saveReminder(current.copy(hour = hour, minute = minute))
                            },
                            current.hour,
                            current.minute,
                            true
                        ).show()
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }

    if (showHabitDialog) {
        AddOrEditHabitDialog(
            initialHabit = editingHabit,
            onDismiss = {
                showHabitDialog = false
                editingHabit = null
            },
            onSave = { name, type ->
                val trimmed = name.trim()
                if (editingHabit == null) {
                    val nextId = (habits.maxOfOrNull { it.id } ?: 0L) + 1L
                    habits.add(
                        Habit(
                            id = nextId,
                            name = trimmed,
                            type = type,
                            createdAt = todayKey
                        )
                    )
                } else {
                    val index = habits.indexOfFirst { it.id == editingHabit?.id }
                    if (index >= 0) {
                        habits[index] = habits[index].copy(name = trimmed, type = type)
                    }
                }
                persistHabitsAndStatuses()
                editingHabit = null
                showHabitDialog = false
            }
        )
    }
}

@Composable
private fun HabitsScreen(
    habits: List<Habit>,
    statuses: Map<StatusKey, DailyStatus>,
    today: LocalDate,
    filter: HabitType?,
    onFilterChange: (HabitType?) -> Unit,
    onStatus: (Habit, DailyStatus) -> Unit,
    onDelete: (Habit) -> Unit,
    onEdit: (Habit) -> Unit
) {
    val visibleHabits = habits.filter { filter == null || it.type == filter }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OverviewHeader(habits = habits, statuses = statuses, today = today)
        }
        item {
            FilterBar(filter = filter, onFilterChange = onFilterChange)
        }
        if (visibleHabits.isEmpty()) {
            item {
                EmptyState(
                    hasAnyHabits = habits.isNotEmpty(),
                    onAddHint = "Kliknij + aby dodać nowy nawyk"
                )
            }
        } else {
            items(visibleHabits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    statuses = statuses,
                    today = today,
                    onStatus = { onStatus(habit, it) },
                    onDelete = { onDelete(habit) },
                    onEdit = { onEdit(habit) }
                )
            }
        }
    }
}

@Composable
private fun OverviewHeader(
    habits: List<Habit>,
    statuses: Map<StatusKey, DailyStatus>,
    today: LocalDate
) {
    val goodCount = habits.count { it.type == HabitType.GOOD }
    val badCount = habits.count { it.type == HabitType.BAD }
    val bestStreak = habits.maxOfOrNull { currentStreak(it.id, statuses, today) } ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoTile("Dobre", goodCount.toString(), GoodColor, Modifier.weight(1f))
        InfoTile("Złe", badCount.toString(), BadColor, Modifier.weight(1f))
        InfoTile("Najlepsza seria", "$bestStreak d", AccentColor, Modifier.weight(1f))
    }
}

@Composable
private fun InfoTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(filter: HabitType?, onFilterChange: (HabitType?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text("Wszystkie") })
        FilterChip(selected = filter == HabitType.GOOD, onClick = { onFilterChange(HabitType.GOOD) }, label = { Text("Dobre") })
        FilterChip(selected = filter == HabitType.BAD, onClick = { onFilterChange(HabitType.BAD) }, label = { Text("Złe") })
    }
}

@Composable
private fun EmptyState(hasAnyHabits: Boolean, onAddHint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (hasAnyHabits) "Brak nawyków w tej kategorii."
                else "Dodaj pierwszy nawyk i zacznij go kontrolować.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(onAddHint, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    statuses: Map<StatusKey, DailyStatus>,
    today: LocalDate,
    onStatus: (DailyStatus) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val todayStatus = statuses[StatusKey(habit.id, today.toString())]
    val streak = currentStreak(habit.id, statuses, today)
    val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val marked7 = last7.count { statuses[StatusKey(habit.id, it.toString())] != null }
    val success7 = last7.count { statuses[StatusKey(habit.id, it.toString())] == DailyStatus.SUCCESS }
    val accent = if (habit.type == HabitType.GOOD) GoodColor else BadColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(accent, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (habit.type == HabitType.GOOD) "Dobry nawyk" else "Zły nawyk",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
                Row {
                    TextButton(onClick = onEdit) { Text("Edytuj") }
                    TextButton(onClick = onDelete) { Text("Usuń") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Metric("Seria", "$streak dni")
                Metric("7 dni", "$success7/$marked7")
                Metric("Start", habit.createdAt)
            }

            SevenDayStrip(habit.id, statuses, last7)

            Text(
                text = if (habit.type == HabitType.GOOD)
                    "Czy wykonałeś ten nawyk dzisiaj?"
                else
                    "Czy udało Ci się uniknąć tego nawyku dzisiaj?",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusButton(
                    selected = todayStatus == DailyStatus.SUCCESS,
                    text = "✓ Sukces",
                    fillColor = GoodColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onStatus(DailyStatus.SUCCESS) }
                )
                StatusButton(
                    selected = todayStatus == DailyStatus.FAILURE,
                    text = "✕ Porażka",
                    fillColor = BadColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onStatus(DailyStatus.FAILURE) }
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    selected: Boolean,
    text: String,
    fillColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = fillColor)) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SevenDayStrip(habitId: Long, statuses: Map<StatusKey, DailyStatus>, dates: List<LocalDate>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dates.forEach { date ->
            val status = statuses[StatusKey(habitId, date.toString())]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pl"))
                        .take(2)
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = when (status) {
                                DailyStatus.SUCCESS -> GoodColor
                                DailyStatus.FAILURE -> BadColor
                                null -> NeutralColor
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun StatsScreen(habits: List<Habit>, statuses: Map<StatusKey, DailyStatus>, today: LocalDate) {
    val last7 = (0..6).map { today.minusDays(it.toLong()) }
    val last30 = (0..29).map { today.minusDays(it.toLong()) }
    val totalSuccess7 = habits.sumOf { habit -> last7.count { statuses[StatusKey(habit.id, it.toString())] == DailyStatus.SUCCESS } }
    val totalMarked7 = habits.sumOf { habit -> last7.count { statuses[StatusKey(habit.id, it.toString())] != null } }
    val totalSuccess30 = habits.sumOf { habit -> last30.count { statuses[StatusKey(habit.id, it.toString())] == DailyStatus.SUCCESS } }
    val totalMarked30 = habits.sumOf { habit -> last30.count { statuses[StatusKey(habit.id, it.toString())] != null } }
    val bestHabit = habits.maxByOrNull { currentStreak(it.id, statuses, today) }
    val topHabits = habits.sortedByDescending { currentStreak(it.id, statuses, today) }.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                InfoTile("Nawyki", habits.size.toString(), AccentColor, Modifier.weight(1f))
                InfoTile("7 dni", "$totalSuccess7/$totalMarked7", GoodColor, Modifier.weight(1f))
                InfoTile("30 dni", "$totalSuccess30/$totalMarked30", BadColor, Modifier.weight(1f))
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Najmocniejszy nawyk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (bestHabit == null) {
                        Text("Dodaj pierwszy nawyk, aby zobaczyć statystyki.")
                    } else {
                        Text(bestHabit.name, style = MaterialTheme.typography.titleSmall)
                        Text("Aktualna seria: ${currentStreak(bestHabit.id, statuses, today)} dni")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ranking serii", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (topHabits.isEmpty()) {
                        Text("Brak danych.")
                    } else {
                        topHabits.forEachIndexed { index, habit ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${index + 1}. ${habit.name}")
                                Text("${currentStreak(habit.id, statuses, today)} dni")
                            }
                            if (index != topHabits.lastIndex) Divider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(habits: List<Habit>, statuses: Map<StatusKey, DailyStatus>, today: LocalDate) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }

    val firstOfMonth = currentMonth.atDay(1)
    val shift = (firstOfMonth.dayOfWeek.value + 6) % 7
    val daysInMonth = currentMonth.lengthOfMonth()
    val calendarItems = buildList<LocalDate?> {
        repeat(shift) { add(null) }
        for (day in 1..daysInMonth) add(currentMonth.atDay(day))
    }

    val selectedMarked = habits.count { statuses[StatusKey(it.id, selectedDate.toString())] != null }
    val selectedSuccess = habits.count { statuses[StatusKey(it.id, selectedDate.toString())] == DailyStatus.SUCCESS }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("←") }
                        Text(
                            text = monthName(currentMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("→") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd").forEach {
                            Text(it, modifier = Modifier.width(36.dp), color = Color.LightGray)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.height(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false
                    ) {
                        items(calendarItems) { date ->
                            if (date == null) {
                                Box(modifier = Modifier.size(36.dp))
                            } else {
                                val marked = habits.count { statuses[StatusKey(it.id, date.toString())] != null }
                                val success = habits.count { statuses[StatusKey(it.id, date.toString())] == DailyStatus.SUCCESS }
                                val bg = when {
                                    marked == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    success == marked -> GoodColor.copy(alpha = 0.85f)
                                    success == 0 -> BadColor.copy(alpha = 0.85f)
                                    else -> AccentColor.copy(alpha = 0.85f)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (selectedDate == date) Color.White.copy(alpha = 0.18f) else bg,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedDate = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(date.dayOfMonth.toString())
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wybrany dzień", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(selectedDate.toString())
                    Text("Sukcesy: $selectedSuccess / $selectedMarked")
                    Text(
                        when {
                            selectedMarked == 0 -> "Brak wpisów dla tego dnia."
                            selectedSuccess == selectedMarked -> "Świetny dzień — wszystko poszło zgodnie z planem."
                            selectedSuccess == 0 -> "Słabszy dzień — jutro nowa szansa."
                            else -> "Było nieźle — część nawyków zaliczona."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    reminderSettings: ReminderSettings,
    onReminderChange: (ReminderSettings) -> Unit,
    onPickTime: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Codzienne przypomnienie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Włącz przypomnienia")
                            Text("Aplikacja przypomni Ci o wpisaniu postępu.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                        Switch(
                            checked = reminderSettings.enabled,
                            onCheckedChange = { onReminderChange(reminderSettings.copy(enabled = it)) }
                        )
                    }
                    Divider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Godzina przypomnienia")
                            Text(formatTime(reminderSettings.hour, reminderSettings.minute), color = AccentColor, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onPickTime) { Text("Zmień") }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Divider()
                        Button(onClick = onRequestNotifications) {
                            Text("Pozwól na powiadomienia")
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Co nowego w wersji 2.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FeatureTag("Czarne tło")
                    FeatureTag("Edycja i usuwanie nawyków")
                    FeatureTag("Kalendarz miesiąca")
                    FeatureTag("Statystyki 7 i 30 dni")
                    FeatureTag("Codzienne przypomnienia")
                    FeatureTag("Serie dni")
                }
            }
        }
    }
}

@Composable
private fun FeatureTag(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}

@Composable
private fun AddOrEditHabitDialog(
    initialHabit: Habit?,
    onDismiss: () -> Unit,
    onSave: (String, HabitType) -> Unit
) {
    var name by remember(initialHabit) { mutableStateOf(initialHabit?.name ?: "") }
    var type by remember(initialHabit) { mutableStateOf(initialHabit?.type ?: HabitType.GOOD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialHabit == null) "Nowy nawyk" else "Edytuj nawyk") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa, np. 20 min spaceru") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.isNotBlank()) onSave(name, type) }
                    )
                )
                Text("Rodzaj")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == HabitType.GOOD, onClick = { type = HabitType.GOOD })
                    Text("Dobry")
                    Spacer(Modifier.width(18.dp))
                    RadioButton(selected = type == HabitType.BAD, onClick = { type = HabitType.BAD })
                    Text("Zły")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, type) }, enabled = name.isNotBlank()) {
                Text(if (initialHabit == null) "Dodaj" else "Zapisz")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

private fun currentStreak(habitId: Long, statuses: Map<StatusKey, DailyStatus>, today: LocalDate): Int {
    var date = today
    if (statuses[StatusKey(habitId, date.toString())] == null) {
        date = date.minusDays(1)
    }
    var streak = 0
    while (statuses[StatusKey(habitId, date.toString())] == DailyStatus.SUCCESS) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

private fun monthName(month: YearMonth): String {
    val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale("pl")).replaceFirstChar { it.uppercase() }
    return "$monthLabel ${month.year}"
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private class HabitRepository(context: Context) {
    private val prefs = context.getSharedPreferences("nawyki_data", Context.MODE_PRIVATE)

    fun loadHabits(): List<Habit> = runCatching {
        val raw = prefs.getString("habits", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    Habit(
                        id = obj.getLong("id"),
                        name = obj.getString("name"),
                        type = HabitType.valueOf(obj.getString("type")),
                        createdAt = obj.optString("createdAt", LocalDate.now().toString())
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun saveHabits(habits: List<Habit>) {
        val array = JSONArray()
        habits.forEach { habit ->
            array.put(
                JSONObject()
                    .put("id", habit.id)
                    .put("name", habit.name)
                    .put("type", habit.type.name)
                    .put("createdAt", habit.createdAt)
            )
        }
        prefs.edit().putString("habits", array.toString()).apply()
    }

    fun loadStatuses(): Map<StatusKey, DailyStatus> = runCatching {
        val raw = prefs.getString("statuses", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildMap {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                put(
                    StatusKey(
                        habitId = obj.getLong("habitId"),
                        date = obj.getString("date")
                    ),
                    DailyStatus.valueOf(obj.getString("status"))
                )
            }
        }
    }.getOrDefault(emptyMap())

    fun saveStatuses(statuses: Map<StatusKey, DailyStatus>) {
        val array = JSONArray()
        statuses.forEach { (key, status) ->
            array.put(
                JSONObject()
                    .put("habitId", key.habitId)
                    .put("date", key.date)
                    .put("status", status.name)
            )
        }
        prefs.edit().putString("statuses", array.toString()).apply()
    }

    fun loadReminderSettings(): ReminderSettings = runCatching {
        val raw = prefs.getString("reminder", null) ?: return ReminderSettings()
        val obj = JSONObject(raw)
        ReminderSettings(
            enabled = obj.optBoolean("enabled", false),
            hour = obj.optInt("hour", 20),
            minute = obj.optInt("minute", 0)
        )
    }.getOrDefault(ReminderSettings())

    fun saveReminderSettings(settings: ReminderSettings) {
        val obj = JSONObject()
            .put("enabled", settings.enabled)
            .put("hour", settings.hour)
            .put("minute", settings.minute)
        prefs.edit().putString("reminder", obj.toString()).apply()
    }
}
