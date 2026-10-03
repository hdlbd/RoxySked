package com.roxysked.app

import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.Calendar

private val Ink = Color(0xFF27313D)
private val Muted = Color(0xFF7A8491)
private val Page = Color(0xFFF5F6F8)
private val Soft = Color(0xFFE7EBF0)

enum class Bucket { TODAY, TODO, DONE, TRASH, PAST }
data class Task(
    val id: String = UUID.randomUUID().toString(), val title: String, val description: String = "",
    val priority: Int = 1, val date: String = LocalDate.now().toString(), val time: String = "",
    val bucket: Bucket = Bucket.TODAY, val completedAt: String = "", val createdAt: Long = System.currentTimeMillis()
)

class TaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("roxy_sked", Context.MODE_PRIVATE)
    fun load(): List<Task> = runCatching {
        val a = JSONArray(prefs.getString("tasks", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Task(
            o.getString("id"), o.getString("title"), o.optString("description"), o.optInt("priority", 1),
            o.optString("date", LocalDate.now().toString()), o.optString("time"), Bucket.valueOf(o.optString("bucket", "TODAY")),
            o.optString("completedAt"), o.optLong("createdAt")
        ) }
    }.getOrDefault(emptyList())
    fun save(tasks: List<Task>) { val a = JSONArray(); tasks.forEach { t -> a.put(JSONObject().apply {
        put("id", t.id); put("title", t.title); put("description", t.description); put("priority", t.priority); put("date", t.date); put("time", t.time); put("bucket", t.bucket.name); put("completedAt", t.completedAt); put("createdAt", t.createdAt)
    }) }; prefs.edit().putString("tasks", a.toString()).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { RoxySkedApp() } }
}

@Composable
fun RoxySkedApp() {
    val context = LocalContext.current
    val store = remember { TaskStore(context) }
    val initialTasks = remember { store.load().map { if (it.bucket == Bucket.TODAY && it.date < LocalDate.now().toString()) it.copy(bucket = Bucket.PAST) else it }.also(store::save) }
    var tasks by remember { mutableStateOf(initialTasks) }
    var tab by remember { mutableStateOf(Bucket.TODAY) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    fun commit(next: List<Task>) { tasks = next; store.save(next) }
    MaterialTheme(colorScheme = lightColorScheme(background = Page, surface = Color.White, primary = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Page) {
            Column(Modifier.fillMaxSize()) {
                Header(tab, tasks, onSettings = { showSettings = true })
                AnimatedContent(targetState = tab, label = "page", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        Bucket.TODAY -> TodayPage(tasks.filter { it.bucket == Bucket.TODAY && it.date == LocalDate.now().toString() },
                            onAdd = { editing = null; showEditor = true },
                            onEdit = { editing = it; showEditor = true },
                            onMove = { t, b -> commit(tasks.map { if (it.id == t.id) t.copy(bucket = b) else it }) },
                            onComplete = { t -> if (context.getSharedPreferences("roxy_sked", 0).getBoolean("vibrate", false)) vibrateOnce(context); commit(tasks.map { if (it.id == t.id) t.copy(bucket = Bucket.DONE, completedAt = LocalDateTime.now().toString()) else it }) })
                        else -> ListPage(current, tasks.filter { it.bucket == current }, onEdit = { editing = it; showEditor = true }, onRestore = { t -> commit(tasks.map { if (it.id == t.id) t.copy(bucket = Bucket.TODAY, date = LocalDate.now().toString()) else it }) }, onDelete = { t -> commit(tasks.filterNot { it.id == t.id }) })
                    }
                }
                BottomBar(tab) { tab = it }
            }
        }
        if (showEditor) TaskEditor(editing, onDismiss = { showEditor = false }, onSave = { value -> commit(if (editing == null) tasks + value else tasks.map { if (it.id == value.id) value else it }); showEditor = false })
        if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
    }
}

@Composable private fun Header(tab: Bucket, tasks: List<Task>, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Roxy sked", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(if (tab == Bucket.TODAY) LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · 今日计划")) else bucketName(tab), color = Muted, fontSize = 14.sp)
        }
        Text("● ${tasks.count { it.bucket == Bucket.DONE }}", color = Color(0xFFC18A22), fontWeight = FontWeight.Bold)
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置", tint = Muted) }
    }
}

@Composable private fun TodayPage(items: List<Task>, onAdd: () -> Unit, onEdit: (Task) -> Unit, onMove: (Task, Bucket) -> Unit, onComplete: (Task) -> Unit) {
    var index by remember(items) { mutableIntStateOf(0) }
    val ordered = items.sortedWith(compareBy<Task> { it.time.isBlank() }.thenBy { it.time }.thenBy { it.createdAt })
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Text("${ordered.size} 件计划", color = Muted, modifier = Modifier.weight(1f)); Button(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("新建") }
        }
        if (ordered.isEmpty()) EmptyState(onAdd) else {
            val shown = ordered[index.coerceIn(0, ordered.lastIndex)]
            SwipeTaskCard(shown, onEdit, onComplete, onMove)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = index > 0, onClick = { index-- }) { Icon(Icons.Default.KeyboardArrowUp, "上一件") }
                Text("${index + 1} / ${ordered.size}", color = Muted)
                IconButton(enabled = index < ordered.lastIndex, onClick = { index++ }) { Icon(Icons.Default.KeyboardArrowDown, "下一件") }
            }
            Text("长按卡片进入环形编辑模式 · 左右滑动执行操作", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable private fun SwipeTaskCard(task: Task, onEdit: (Task) -> Unit, onComplete: (Task) -> Unit, onMove: (Task, Bucket) -> Unit) {
    var offsetX by remember(task.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(task.id) { mutableFloatStateOf(0f) }
    var ringMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val scale = if (ringMode) .94f else 1f
    Box(Modifier.fillMaxWidth().height(410.dp).scale(scale).pointerInput(task.id) {
        detectDragGestures(onDrag = { change, amount -> change.consume(); offsetX += amount.x; offsetY += amount.y }, onDragEnd = {
            val action = when { offsetY < -130 -> "done"; offsetX < -130 -> "trash"; offsetX > 130 -> "todo"; else -> "none" }
            if (action == "done") onComplete(task) else if (action == "trash") onMove(task, Bucket.TRASH) else if (action == "todo") onMove(task, Bucket.TODO)
            scope.launch { Animatable(offsetX).animateTo(0f, tween(260)) }; offsetX = 0f; offsetY = 0f
        })
    }.combinedClickable(onClick = {}, onLongClick = { ringMode = true; onEdit(task) })) {
        if (offsetX < -60) Icon(Icons.Default.DeleteOutline, "删除", tint = Color(0xFFB76D6D), modifier = Modifier.align(Alignment.Center).size(72.dp))
        else if (offsetX > 60) Icon(Icons.Default.MenuBook, "待办", tint = Color(0xFF71839A), modifier = Modifier.align(Alignment.Center).size(72.dp))
        else if (offsetY < -60) Icon(Icons.Default.Star, "完成", tint = Color(0xFFC18A22), modifier = Modifier.align(Alignment.Center).size(72.dp))
        Card(Modifier.fillMaxSize().offset(x = offsetX.dp, y = offsetY.dp), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) { PriorityDot(task.priority); Spacer(Modifier.width(8.dp)); Text(if (task.time.isBlank()) "未安排时间" else task.time, color = Muted); Spacer(Modifier.weight(1f)); Icon(Icons.Default.AccessTime, null, tint = Muted) }
                Column { Text(task.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink); if (task.description.isNotBlank()) Text(task.description, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, null, tint = Color(0xFF6B9272)); Text("上划完成", color = Muted, fontSize = 13.sp); Text("左划删除  ·  右划待办", color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun ListPage(bucket: Bucket, items: List<Task>, onEdit: (Task) -> Unit, onRestore: (Task) -> Unit, onDelete: (Task) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("${items.size} 件", color = Muted, modifier = Modifier.padding(bottom = 10.dp))
        if (items.isEmpty()) EmptyState(null) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(items, key = { it.id }) { t ->
            Card(onClick = { onEdit(t) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PriorityDot(t.priority); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t.title, color = Ink, fontWeight = FontWeight.SemiBold); Text(t.description.ifBlank { if (t.time.isBlank()) "未安排时间" else t.time }, color = Muted, fontSize = 13.sp) }
                if (bucket == Bucket.TRASH || bucket == Bucket.DONE || bucket == Bucket.PAST) IconButton(onClick = onRestore.bind(t)) { Icon(Icons.Default.Restore, "恢复") } else IconButton(onClick = onDelete.bind(t)) { Icon(Icons.Default.DeleteOutline, "删除") }
            } }
        } }
    }
}

private fun <T> ((T) -> Unit).bind(value: T): () -> Unit = { this(value) }

@Composable private fun EmptyState(onAdd: (() -> Unit)?) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.EventNote, null, tint = Muted, modifier = Modifier.size(54.dp)); Text("这里还没有事项", color = Muted, modifier = Modifier.padding(12.dp)); if (onAdd != null) OutlinedButton(onClick = onAdd) { Text("创建第一件事") } } }
@Composable private fun PriorityDot(priority: Int) { Box(Modifier.size(11.dp).background(listOf(Color(0xFF9AA5B1), Color(0xFF6E8298), Color(0xFFBD6C68)).getOrElse(priority.coerceIn(0, 2)) { Color.Gray }, CircleShape)) }
private fun bucketName(b: Bucket) = when (b) { Bucket.TODAY -> "今日计划"; Bucket.TODO -> "待办列表"; Bucket.DONE -> "已完成"; Bucket.TRASH -> "回收箱"; Bucket.PAST -> "过去" }

@Composable private fun BottomBar(current: Bucket, onSelect: (Bucket) -> Unit) { NavigationBar(containerColor = Color.White) { listOf(Bucket.TODAY to Icons.Default.Today, Bucket.TODO to Icons.Default.Menu, Bucket.DONE to Icons.Default.CheckCircle, Bucket.TRASH to Icons.Default.DeleteOutline).forEach { (b, icon) -> NavigationBarItem(selected = current == b, onClick = { onSelect(b) }, icon = { Icon(icon, null) }, label = { Text(bucketName(b).take(4)) }) } } }

@Composable private fun TaskEditor(task: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf(task?.title ?: "") }; var desc by remember { mutableStateOf(task?.description ?: "") }; var time by remember { mutableStateOf(task?.time ?: "") }; var priority by remember { mutableIntStateOf(task?.priority ?: 1) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (task == null) "新建事项" else "编辑事项") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
        OutlinedTextField(desc, { desc = it }, label = { Text("描述（选填）") }, minLines = 2)
        OutlinedTextField(time, { time = it }, label = { Text("时间（如 09:30，可留空）") }, singleLine = true)
        Text("优先级", color = Muted); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("低", "中", "高").forEachIndexed { i, s -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(s) }) } }
    } }, confirmButton = { Button(enabled = title.isNotBlank(), onClick = { onSave((task ?: Task(title = title)).copy(title = title, description = desc, time = time, priority = priority)) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("roxy_sked", 0) }; var vibrate by remember { mutableStateOf(prefs.getBoolean("vibrate", false)) }; var evening by remember { mutableStateOf(prefs.getBoolean("evening", false)) }; var time by remember { mutableStateOf(prefs.getString("eveningTime", "21:00") ?: "21:00") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("设置") }, text = { Column { Text("行为", fontWeight = FontWeight.Bold, color = Ink); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("完成时震动", Modifier.weight(1f)); Switch(vibrate, { vibrate = it; prefs.edit().putBoolean("vibrate", it).apply() }) }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("晚间检查提醒", Modifier.weight(1f)); Switch(evening, { evening = it; prefs.edit().putBoolean("evening", it).apply(); if (it) scheduleEveningReminder(context, time) else cancelEveningReminder(context) }) }; if (evening) { TextButton(onClick = { val parts = time.split(":"); TimePickerDialog(context, { _, h, m -> time = "%02d:%02d".format(h, m); prefs.edit().putString("eveningTime", time).apply(); scheduleEveningReminder(context, time) }, parts[0].toInt(), parts[1].toInt(), true).show() }) { Text("提醒时间：$time") } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

private fun scheduleEveningReminder(context: Context, time: String) {
    val parts = time.split(":"); val now = Calendar.getInstance(); val trigger = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, parts[0].toInt()); set(Calendar.MINUTE, parts[1].toInt()); set(Calendar.SECOND, 0); if (before(now)) add(Calendar.DAY_OF_YEAR, 1) }
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; val intent = PendingIntent.getBroadcast(context, 7, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, AlarmManager.INTERVAL_DAY, intent)
}
private fun cancelEveningReminder(context: Context) { val intent = PendingIntent.getBroadcast(context, 7, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(intent) }

private fun vibrateOnce(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    else (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
}
