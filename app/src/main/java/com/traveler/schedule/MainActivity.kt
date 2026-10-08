package com.traveler.schedule

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.provider.ContactsContract
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.traveler.schedule.data.FirebaseCloudSync
import com.traveler.schedule.data.ScheduleEntity
import com.traveler.schedule.data.ScheduleRepository
import com.traveler.schedule.data.TaskEntity
import com.traveler.schedule.worker.ReminderWorker
import com.traveler.schedule.widget.TravelerWidgetUtils
import com.traveler.schedule.widget.WidgetUpdateHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import kotlin.math.absoluteValue

private val TravelerRed = Color(0xFFC43130)
private val TravelerBlue = Color(0xFF2C82C9)
private val TravelerBlack = Color(0xFF151515)
private val TravelerGray = Color(0xFF667085)
private val PageBg = Color(0xFFF6F7F9)
private val LightBorder = Color(0xFFE6E8EC)
private val Statuses = listOf("견적", "예약", "확정", "진행중", "완료", "취소")
private val DepartureReminderDays = listOf(30, 14, 7, 3, 1)
private fun departureReminderPrefKey(days: Int) = "departure_reminder_$days"
private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val KoreanDayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

private enum class HomeSummaryKind { THIS_MONTH, UPCOMING, UNDONE_TASKS }
private enum class HomeFilter(val label: String) {
    TODAY("오늘"), THIS_WEEK("이번주"), THIS_MONTH("이번달"), UPCOMING("출발예정"), IN_PROGRESS("진행중"), COMPLETED("완료")
}

private const val APP_LOCK_PREFS = "traveler_app_lock"
private const val RECENT_PREFS = "traveler_recent_schedules"

private fun hashPin(pin: String): String = MessageDigest.getInstance("SHA-256")
    .digest(pin.toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun loadRecentScheduleIds(context: Context): List<Long> = context
    .getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
    .getString("ids", "")
    .orEmpty()
    .split(',')
    .mapNotNull { it.toLongOrNull() }

private fun recordRecentSchedule(context: Context, id: Long): List<Long> {
    val ids = (listOf(id) + loadRecentScheduleIds(context).filter { it != id }).take(5)
    context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        .edit().putString("ids", ids.joinToString(",")).apply()
    return ids
}

private fun scheduleUrgencyDate(item: ScheduleEntity): LocalDate = parseDate(item.startDate) ?: LocalDate.MAX

private fun sortSchedulesForWork(items: List<ScheduleEntity>): List<ScheduleEntity> {
    val today = LocalDate.now()
    return items.sortedWith(
        compareBy<ScheduleEntity> { item ->
            val d = scheduleUrgencyDate(item)
            when {
                item.status == "진행중" -> 0
                !d.isBefore(today) && item.status !in listOf("완료", "취소") -> 1
                item.status == "완료" -> 3
                else -> 2
            }
        }.thenBy { scheduleUrgencyDate(it) }.thenByDescending { it.favorite }
    )
}

private fun launchBiometricUnlock(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                onError(errString.toString())
            }
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("TRAVELER 잠금 해제")
        .setSubtitle("지문 또는 얼굴 인식으로 앱을 엽니다.")
        .setNegativeButtonText("PIN 사용")
        .build()
    prompt.authenticate(info)
}

private data class HomeBanner(
    val imageRes: Int,
    val url: String,
    val contentDescription: String
)

private data class PhoneContact(
    val id: Long,
    val name: String,
    val phone: String
)

private val HangulInitials = charArrayOf(
    'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
)

private fun hangulInitialString(value: String): String = buildString {
    value.forEach { ch ->
        when {
            ch.code in 0xAC00..0xD7A3 -> append(HangulInitials[(ch.code - 0xAC00) / 588])
            ch in 'ㄱ'..'ㅎ' -> append(ch)
        }
    }
}

private fun contactMatchesQuery(contact: PhoneContact, rawQuery: String): Boolean {
    val query = rawQuery.trim().lowercase()
    if (query.isBlank()) return true
    val name = contact.name.lowercase()
    val compactName = name.replace(" ", "")
    val compactQuery = query.replace(" ", "")
    val queryDigits = query.filter(Char::isDigit)
    val phoneDigits = contact.phone.filter(Char::isDigit)

    if (name.contains(query) || compactName.contains(compactQuery)) return true
    if (queryDigits.isNotBlank() && phoneDigits.contains(queryDigits)) return true

    if (compactQuery.all { it in 'ㄱ'..'ㅎ' }) {
        if (hangulInitialString(compactName).contains(compactQuery)) return true
    }
    return false
}

private data class ScheduleVisualOption(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val ScheduleVisualOptions = listOf(
    ScheduleVisualOption("flight", "비행기", Icons.Default.Flight),
    ScheduleVisualOption("hotel", "호텔", Icons.Default.Hotel),
    ScheduleVisualOption("map", "지도", Icons.Default.Map),
    ScheduleVisualOption("luggage", "캐리어", Icons.Default.Luggage),
    ScheduleVisualOption("bus", "버스", Icons.Default.DirectionsBus),
    ScheduleVisualOption("train", "기차", Icons.Default.Train),
    ScheduleVisualOption("ship", "선박", Icons.Default.DirectionsBoat),
    ScheduleVisualOption("passport", "여권", Icons.Default.Badge),
    ScheduleVisualOption("camera", "카메라", Icons.Default.PhotoCamera),
    ScheduleVisualOption("food", "식사", Icons.Default.Restaurant),
    ScheduleVisualOption("golf", "골프", Icons.Default.GolfCourse),
    ScheduleVisualOption("landmark", "랜드마크", Icons.Default.LocationCity)
)

private val ScheduleColorOptions = listOf(
    "blue" to Color(0xFF2C82C9),
    "red" to Color(0xFFC43130),
    "green" to Color(0xFF2E8B57),
    "orange" to Color(0xFFF28C28),
    "purple" to Color(0xFF7A5AF8),
    "teal" to Color(0xFF00897B),
    "pink" to Color(0xFFD85A9E),
    "navy" to Color(0xFF344B8E),
    "gold" to Color(0xFFB88717),
    "brown" to Color(0xFF8D6E63),
    "cyan" to Color(0xFF0E9CB5),
    "gray" to Color(0xFF667085)
)

private fun scheduleVisualIcon(key: String): androidx.compose.ui.graphics.vector.ImageVector =
    ScheduleVisualOptions.firstOrNull { it.key == key }?.icon ?: Icons.Default.Flight

private fun scheduleVisualColor(key: String): Color =
    ScheduleColorOptions.firstOrNull { it.first == key }?.second ?: TravelerBlue

private suspend fun loadPhoneContacts(context: Context): List<PhoneContact> = withContext(Dispatchers.IO) {
    val result = mutableListOf<PhoneContact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val seen = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "이름 없음" }
            val phone = cursor.getString(phoneIndex).orEmpty()
            val key = "$id|${phone.filter(Char::isDigit)}"
            if (phone.isNotBlank() && seen.add(key)) result += PhoneContact(id, name, phone)
        }
    }
    result
}

private data class ExchangeRateSnapshot(
    val usdKrw: Double,
    val jpyKrw: Double,
    val cnyKrw: Double,
    val eurKrw: Double,
    val phpKrw: Double,
    val vndKrw: Double,
    val thbKrw: Double,
    val twdKrw: Double,
    val hkdKrw: Double,
    val mntKrw: Double,
    val referenceDate: String,
    val fetchedAt: Long
)

private const val EXCHANGE_PREFS = "traveler_exchange_rates"

private fun loadCachedExchangeRates(context: Context): ExchangeRateSnapshot? {
    val prefs = context.getSharedPreferences(EXCHANGE_PREFS, Context.MODE_PRIVATE)
    if (!prefs.contains("usd")) return null
    return ExchangeRateSnapshot(
        usdKrw = prefs.getFloat("usd", 0f).toDouble(),
        jpyKrw = prefs.getFloat("jpy", 0f).toDouble(),
        cnyKrw = prefs.getFloat("cny", 0f).toDouble(),
        eurKrw = prefs.getFloat("eur", 0f).toDouble(),
        phpKrw = prefs.getFloat("php", 0f).toDouble(),
        vndKrw = prefs.getFloat("vnd", 0f).toDouble(),
        thbKrw = prefs.getFloat("thb", 0f).toDouble(),
        twdKrw = prefs.getFloat("twd", 0f).toDouble(),
        hkdKrw = prefs.getFloat("hkd", 0f).toDouble(),
        mntKrw = prefs.getFloat("mnt", 0f).toDouble(),
        referenceDate = prefs.getString("reference_date", "") ?: "",
        fetchedAt = prefs.getLong("fetched_at", 0L)
    ).takeIf {
        listOf(
            it.usdKrw, it.jpyKrw, it.cnyKrw, it.eurKrw, it.phpKrw,
            it.vndKrw, it.thbKrw, it.twdKrw, it.hkdKrw, it.mntKrw
        ).all { rate -> rate > 0 }
    }
}

private fun saveExchangeRates(context: Context, snapshot: ExchangeRateSnapshot) {
    context.getSharedPreferences(EXCHANGE_PREFS, Context.MODE_PRIVATE).edit()
        .putFloat("usd", snapshot.usdKrw.toFloat())
        .putFloat("jpy", snapshot.jpyKrw.toFloat())
        .putFloat("cny", snapshot.cnyKrw.toFloat())
        .putFloat("eur", snapshot.eurKrw.toFloat())
        .putFloat("php", snapshot.phpKrw.toFloat())
        .putFloat("vnd", snapshot.vndKrw.toFloat())
        .putFloat("thb", snapshot.thbKrw.toFloat())
        .putFloat("twd", snapshot.twdKrw.toFloat())
        .putFloat("hkd", snapshot.hkdKrw.toFloat())
        .putFloat("mnt", snapshot.mntKrw.toFloat())
        .putString("reference_date", snapshot.referenceDate)
        .putLong("fetched_at", snapshot.fetchedAt)
        .apply()
}

private suspend fun fetchExchangeRates(): ExchangeRateSnapshot = withContext(Dispatchers.IO) {
    // 5분 단위로 갱신되는 공개 환율 API를 사용합니다.
    // USD 기준 환율을 KRW 기준으로 환산합니다.
    val url = URL("https://fxapi.app/api/usd.json")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) error("환율 서버 응답 오류 ($code)")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        val rates = root.getJSONObject("rates")
        val krwPerUsd = rates.getDouble("KRW")
        val jpyPerUsd = rates.getDouble("JPY")
        val cnyPerUsd = rates.getDouble("CNY")
        val eurPerUsd = rates.getDouble("EUR")
        val phpPerUsd = rates.getDouble("PHP")
        val vndPerUsd = rates.getDouble("VND")
        val thbPerUsd = rates.getDouble("THB")
        val twdPerUsd = rates.getDouble("TWD")
        val hkdPerUsd = rates.getDouble("HKD")
        val mntPerUsd = rates.getDouble("MNT")
        if (
            listOf(
                krwPerUsd, jpyPerUsd, cnyPerUsd, eurPerUsd, phpPerUsd,
                vndPerUsd, thbPerUsd, twdPerUsd, hkdPerUsd, mntPerUsd
            ).any { it <= 0.0 }
        ) {
            error("잘못된 환율 데이터")
        }
        val timestampText = root.optString("timestamp")
        val providerEpoch = timestampText.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it }
            ?: runCatching { Instant.parse(timestampText).toEpochMilli() }.getOrNull()
            ?: System.currentTimeMillis()
        ExchangeRateSnapshot(
            usdKrw = krwPerUsd,
            jpyKrw = krwPerUsd / jpyPerUsd,
            cnyKrw = krwPerUsd / cnyPerUsd,
            eurKrw = krwPerUsd / eurPerUsd,
            phpKrw = krwPerUsd / phpPerUsd,
            vndKrw = krwPerUsd / vndPerUsd,
            thbKrw = krwPerUsd / thbPerUsd,
            twdKrw = krwPerUsd / twdPerUsd,
            hkdKrw = krwPerUsd / hkdPerUsd,
            mntKrw = krwPerUsd / mntPerUsd,
            referenceDate = Instant.ofEpochMilli(providerEpoch).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            fetchedAt = providerEpoch
        )
    } finally {
        connection.disconnect()
    }
}

private fun formatKrw(value: Double): String = String.format(Locale.KOREA, "%,.2f원", value)

private fun formatFetchedAt(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}

private data class WidgetNavigation(
    val openCalendar: Boolean = false,
    val selectedDate: LocalDate? = null,
    val autoOpenDate: Boolean = false,
    val dateHasSchedule: Boolean = false,
    val scheduleId: Long? = null
)

class MainActivity : FragmentActivity() {
    private val widgetNavigationState = mutableStateOf<WidgetNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetNavigationState.value = parseWidgetNavigation(intent)
        val app = application as TravelerApplication
        setContent {
            val factory = remember { ScheduleViewModel.Factory(app.repository, this) }
            val vm: ScheduleViewModel = viewModel(factory = factory)
            val uiPrefs = remember { getSharedPreferences("traveler_ui", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(uiPrefs.getString("theme_mode", "system").orEmpty().ifBlank { "system" }) }
            TravelerTheme(themeMode) {
                NotificationPermissionRequester()
                TravelerApp(
                    vm = vm,
                    widgetNavigation = widgetNavigationState.value,
                    onWidgetNavigationConsumed = { widgetNavigationState.value = null },
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        uiPrefs.edit().putString("theme_mode", mode).apply()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetNavigationState.value = parseWidgetNavigation(intent)
    }

    private fun parseWidgetNavigation(intent: Intent?): WidgetNavigation? {
        intent ?: return null
        val date = intent.getStringExtra(TravelerWidgetUtils.EXTRA_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val rawScheduleId = intent.getLongExtra(TravelerWidgetUtils.EXTRA_SCHEDULE_ID, -1L)
        val scheduleId = rawScheduleId.takeIf { it > 0L }
        val openCalendar = intent.getStringExtra(TravelerWidgetUtils.EXTRA_OPEN_TAB) == "calendar"
        val autoOpenDate = intent.getBooleanExtra(TravelerWidgetUtils.EXTRA_AUTO_OPEN_DATE, false)
        val dateHasSchedule = intent.getBooleanExtra(TravelerWidgetUtils.EXTRA_DATE_HAS_SCHEDULE, false)
        if (!openCalendar && date == null && scheduleId == null) return null
        return WidgetNavigation(openCalendar, date, autoOpenDate, dateHasSchedule, scheduleId)
    }
}

@Composable
private fun NotificationPermissionRequester() {
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val activity: ComponentActivity
) : ViewModel() {
    private val cloud = FirebaseCloudSync(repository)

    val schedules = repository.scheduleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allTasks = repository.taskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val trashSchedules = repository.scheduleDao.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var syncStatus by mutableStateOf("로그인 후 동기화")
        private set
    var lastSyncText by mutableStateOf("")
        private set

    fun tasks(scheduleId: Long): Flow<List<TaskEntity>> = repository.taskDao.observeForSchedule(scheduleId)

    fun syncCloud(onDone: ((Boolean, String) -> Unit)? = null) = viewModelScope.launch {
        if (FirebaseAuth.getInstance().currentUser == null) {
            syncStatus = "로그인 필요"
            onDone?.invoke(false, "로그인이 필요합니다.")
            return@launch
        }
        syncStatus = "동기화 중..."
        runCatching { cloud.syncAll() }
            .onSuccess { result ->
                syncStatus = "동기화 완료"
                lastSyncText = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                WidgetUpdateHelper.requestUpdate(activity)
                onDone?.invoke(true, "일정 ${result.schedules}건 · 업무 ${result.tasks}건 동기화")
            }
            .onFailure { e ->
                syncStatus = "동기화 실패"
                onDone?.invoke(false, e.localizedMessage ?: "클라우드 동기화에 실패했습니다.")
            }
    }

    fun clearLocal(onDone: () -> Unit = {}) = viewModelScope.launch {
        repository.taskDao.getAllOnce().forEach { cancelTaskReminder(it) }
        repository.scheduleDao.getAllOnce().forEach { cancelScheduleReminders(it) }
        repository.taskDao.deleteAll()
        repository.scheduleDao.deleteAll()
        WidgetUpdateHelper.requestUpdate(activity)
        onDone()
    }

    fun addSchedule(item: ScheduleEntity) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val prepared = item.copy(
            id = 0,
            cloudId = item.cloudId.ifBlank { UUID.randomUUID().toString() },
            updatedAt = now
        )
        val id = repository.scheduleDao.insert(prepared)
        val saved = prepared.copy(id = id)
        val tasks = defaultChecklist(saved)
        tasks.forEach { task ->
            val taskId = repository.taskDao.insert(task)
            scheduleTaskReminder(task.copy(id = taskId))
        }
        scheduleBusinessReminders(saved)
        WidgetUpdateHelper.requestUpdate(activity)
        if (FirebaseAuth.getInstance().currentUser != null) {
            runCatching {
                cloud.pushSchedule(saved)
                repository.taskDao.getForScheduleOnce(id).forEach { cloud.pushTask(it) }
            }
        }
    }

    fun updateSchedule(item: ScheduleEntity) = viewModelScope.launch {
        val updated = item.copy(
            cloudId = item.cloudId.ifBlank { UUID.randomUUID().toString() },
            updatedAt = System.currentTimeMillis()
        )
        repository.scheduleDao.update(updated)
        scheduleBusinessReminders(updated)
        WidgetUpdateHelper.requestUpdate(activity)
        if (FirebaseAuth.getInstance().currentUser != null) runCatching { cloud.pushSchedule(updated) }
    }

    fun toggleFavorite(item: ScheduleEntity) = viewModelScope.launch {
        val updated = item.copy(
            favorite = !item.favorite,
            cloudId = item.cloudId.ifBlank { UUID.randomUUID().toString() },
            updatedAt = System.currentTimeMillis()
        )
        repository.scheduleDao.update(updated)
        WidgetUpdateHelper.requestUpdate(activity)
        if (FirebaseAuth.getInstance().currentUser != null) runCatching { cloud.pushSchedule(updated) }
    }

    fun duplicateSchedule(sourceId: Long, newItem: ScheduleEntity, includeTasks: Boolean = true) = viewModelScope.launch {
        val source = schedules.value.firstOrNull { it.id == sourceId }
        val now = System.currentTimeMillis()
        val prepared = newItem.copy(id = 0, favorite = false, cloudId = UUID.randomUUID().toString(), updatedAt = now)
        val newId = repository.scheduleDao.insert(prepared)
        val saved = prepared.copy(id = newId)
        val sourceTasks = if (includeTasks) repository.taskDao.getForScheduleOnce(sourceId) else emptyList()
        val shiftDays = if (source != null) {
            val oldStart = parseDate(source.startDate)
            val newStart = parseDate(saved.startDate)
            if (oldStart != null && newStart != null) ChronoUnit.DAYS.between(oldStart, newStart) else 0L
        } else 0L
        sourceTasks.forEach { task ->
            val shiftedDue = parseDate(task.dueDate)?.plusDays(shiftDays)?.toString() ?: task.dueDate
            val copied = task.copy(
                id = 0,
                scheduleId = newId,
                completed = false,
                dueDate = shiftedDue,
                cloudId = UUID.randomUUID().toString(),
                updatedAt = now
            )
            val taskId = repository.taskDao.insert(copied)
            scheduleTaskReminder(copied.copy(id = taskId))
        }
        if (sourceTasks.isEmpty()) {
            defaultChecklist(saved).forEach { task ->
                val taskId = repository.taskDao.insert(task)
                scheduleTaskReminder(task.copy(id = taskId))
            }
        }
        scheduleBusinessReminders(saved)
        WidgetUpdateHelper.requestUpdate(activity)
        if (FirebaseAuth.getInstance().currentUser != null) {
            runCatching {
                cloud.pushSchedule(saved)
                repository.taskDao.getForScheduleOnce(newId).forEach { cloud.pushTask(it) }
            }
        }
    }

    fun moveScheduleToTrash(item: ScheduleEntity, onDone: ((Boolean) -> Unit)? = null) = viewModelScope.launch {
        repository.taskDao.getForScheduleOnce(item.id).forEach { cancelTaskReminder(it) }
        cancelScheduleReminders(item)
        val trashed = item.copy(
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        repository.scheduleDao.update(trashed)
        WidgetUpdateHelper.requestUpdate(activity)
        val cloudOk = if (FirebaseAuth.getInstance().currentUser != null) {
            runCatching { cloud.pushSchedule(trashed) }.isSuccess
        } else true
        onDone?.invoke(cloudOk)
    }

    fun restoreSchedule(item: ScheduleEntity, onDone: ((Boolean) -> Unit)? = null) = viewModelScope.launch {
        val restored = item.copy(
            deletedAt = 0L,
            updatedAt = System.currentTimeMillis()
        )
        repository.scheduleDao.update(restored)
        repository.taskDao.getForScheduleOnce(restored.id).forEach { task ->
            if (!task.completed) scheduleTaskReminder(task)
        }
        scheduleBusinessReminders(restored)
        WidgetUpdateHelper.requestUpdate(activity)
        val cloudOk = if (FirebaseAuth.getInstance().currentUser != null) {
            runCatching { cloud.pushSchedule(restored) }.isSuccess
        } else true
        onDone?.invoke(cloudOk)
    }

    fun permanentlyDeleteSchedule(item: ScheduleEntity, onDone: ((Boolean) -> Unit)? = null) = viewModelScope.launch {
        repository.taskDao.getForScheduleOnce(item.id).forEach { cancelTaskReminder(it) }
        cancelScheduleReminders(item)
        val remoteOk = when {
            item.cloudId.isBlank() -> true
            FirebaseAuth.getInstance().currentUser == null -> false
            else -> runCatching { cloud.deleteSchedule(item) }.isSuccess
        }
        if (remoteOk) {
            repository.scheduleDao.delete(item)
            WidgetUpdateHelper.requestUpdate(activity)
        }
        onDone?.invoke(remoteOk)
    }

    fun emptyTrash(onDone: ((Int, Int) -> Unit)? = null) = viewModelScope.launch {
        val trash = repository.scheduleDao.getTrashOnce()
        var deleted = 0
        var failed = 0
        trash.forEach { item ->
            val remoteOk = when {
                item.cloudId.isBlank() -> true
                FirebaseAuth.getInstance().currentUser == null -> false
                else -> runCatching { cloud.deleteSchedule(item) }.isSuccess
            }
            if (remoteOk) {
                repository.taskDao.getForScheduleOnce(item.id).forEach { cancelTaskReminder(it) }
                cancelScheduleReminders(item)
                repository.scheduleDao.delete(item)
                deleted++
            } else {
                failed++
            }
        }
        WidgetUpdateHelper.requestUpdate(activity)
        onDone?.invoke(deleted, failed)
    }

    fun purgeExpiredTrash() = viewModelScope.launch {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        repository.scheduleDao.getTrashOnce()
            .filter { it.deletedAt > 0L && it.deletedAt <= cutoff }
            .forEach { item ->
                val remoteOk = when {
                    item.cloudId.isBlank() -> true
                    FirebaseAuth.getInstance().currentUser == null -> false
                    else -> runCatching { cloud.deleteSchedule(item) }.isSuccess
                }
                if (remoteOk) repository.scheduleDao.delete(item)
            }
        WidgetUpdateHelper.requestUpdate(activity)
    }

    fun addTask(scheduleId: Long, title: String, dueDate: String = "") = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val task = TaskEntity(
            scheduleId = scheduleId,
            title = title.trim(),
            dueDate = dueDate.trim(),
            cloudId = UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis()
        )
        val id = repository.taskDao.insert(task)
        val saved = task.copy(id = id)
        scheduleTaskReminder(saved)
        if (FirebaseAuth.getInstance().currentUser != null) runCatching { cloud.pushTask(saved) }
    }

    fun toggleTask(item: TaskEntity) = viewModelScope.launch {
        val updated = item.copy(
            completed = !item.completed,
            cloudId = item.cloudId.ifBlank { UUID.randomUUID().toString() },
            updatedAt = System.currentTimeMillis()
        )
        repository.taskDao.update(updated)
        if (updated.completed) cancelTaskReminder(updated) else scheduleTaskReminder(updated)
        if (FirebaseAuth.getInstance().currentUser != null) runCatching { cloud.pushTask(updated) }
    }

    fun deleteTask(item: TaskEntity) = viewModelScope.launch {
        cancelTaskReminder(item)
        if (FirebaseAuth.getInstance().currentUser != null) runCatching { cloud.deleteTask(item) }
        repository.taskDao.delete(item)
    }

    private fun defaultChecklist(item: ScheduleEntity): List<TaskEntity> {
        val start = parseDate(item.startDate) ?: LocalDate.now()
        val now = System.currentTimeMillis()
        fun due(daysBefore: Long) = start.minusDays(daysBefore).toString()
        fun task(title: String, category: String, dueDate: String) = TaskEntity(
            scheduleId = item.id,
            title = title,
            category = category,
            dueDate = dueDate,
            cloudId = UUID.randomUUID().toString(),
            updatedAt = now
        )
        return listOf(
            task("항공 발권", "항공", item.ticketDeadline.ifBlank { due(14) }),
            task("호텔 확정", "호텔", due(14)),
            task("차량 예약", "차량", due(7)),
            task("가이드 확정", "현지", due(7)),
            task("보험 가입", "보험", due(3)),
            task("APIS 입력", "APIS", due(3)),
            task("공문 발송", "공문", due(14)),
            task("명찰 제작", "제작", due(3)),
            task("현수막 제작", "제작", due(3)),
            task("인보이스 확인", "정산", due(7)),
            task("잔금 확인", "정산", item.balanceDueDate.ifBlank { due(7) }),
            task("최종 명단 확인", "명단", due(3))
        )
    }

    private fun scheduleBusinessReminders(item: ScheduleEntity) {
        // V4.5.2부터 출발 D-Day / 주요 기한 개별 알림은 사용하지 않습니다.
        // 이전 버전에서 예약되어 있던 알림만 모두 취소합니다.
        cancelScheduleReminders(item)
    }

    fun setDepartureReminderEnabled(days: Int, enabled: Boolean, items: List<ScheduleEntity>) {
        // 호환용으로 남겨둔 함수입니다.
        // 현재는 항공 발권 / 호텔 확정 / APIS 입력 3개 업무만 알림을 사용합니다.
        items.forEach { item ->
            WorkManager.getInstance(activity).cancelUniqueWork("trip_${item.id}_departure_$days")
        }
    }

    private fun reminderTime(): Pair<Int, Int> {
        val prefs = activity.getSharedPreferences("traveler_feedback", Context.MODE_PRIVATE)
        return prefs.getInt("reminder_hour", 9) to prefs.getInt("reminder_minute", 0)
    }

    private val AllowedNotificationTaskTitles = setOf("항공발권", "호텔확정", "APIS입력", "아피스입력")

    private fun normalizedNotificationTaskTitle(title: String): String =
        title.trim().replace("\\s+".toRegex(), "").uppercase(Locale.KOREA)

    private fun isAllowedNotificationTask(task: TaskEntity): Boolean =
        normalizedNotificationTaskTitle(task.title) in AllowedNotificationTaskTitles

    private fun canonicalNotificationTaskTitle(task: TaskEntity): String = when (normalizedNotificationTaskTitle(task.title)) {
        "항공발권" -> "항공 발권"
        "호텔확정" -> "호텔 확정"
        "APIS입력", "아피스입력" -> "APIS 입력"
        else -> task.title.trim()
    }

private fun taskReminderName(taskId: Long) = "task_${taskId}_due"
    private fun overdueReminderName(task: TaskEntity) = "task_${task.id}_overdue_${task.dueDate}"
    private fun overdueReminderPrefKey(task: TaskEntity) = "overdue_notified_${task.id}_${task.dueDate}"

    private fun scheduleTaskReminder(task: TaskEntity) {
        if (task.id <= 0L || task.completed || task.dueDate.isBlank() || !isAllowedNotificationTask(task)) {
            if (task.id > 0L) cancelTaskReminder(task)
            return
        }

        val date = parseDate(task.dueDate) ?: run {
            cancelTaskReminder(task)
            return
        }
        val canonicalTitle = canonicalNotificationTaskTitle(task)

        if (date.isBefore(LocalDate.now())) {
            WorkManager.getInstance(activity).cancelUniqueWork(taskReminderName(task.id))
            val prefs = activity.getSharedPreferences("traveler_reminder_state", Context.MODE_PRIVATE)
            val prefKey = overdueReminderPrefKey(task)
            if (!prefs.getBoolean(prefKey, false)) {
                val data = Data.Builder()
                    .putString("title", "$canonicalTitle · 기한 초과")
                    .putString("message", "${task.dueDate}까지 완료해야 했던 업무가 아직 미완료입니다.")
                    .build()
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(activity).enqueueUniqueWork(
                    overdueReminderName(task),
                    ExistingWorkPolicy.KEEP,
                    request
                )
                prefs.edit().putBoolean(prefKey, true).apply()
            }
            return
        }

        WorkManager.getInstance(activity).cancelUniqueWork(overdueReminderName(task))
        enqueueReminder(
            taskReminderName(task.id),
            date,
            "$canonicalTitle 알림",
            "${task.dueDate}까지 완료해야 할 업무입니다."
        )
    }

    private fun cancelTaskReminder(task: TaskEntity) {
        if (task.id > 0L) {
            WorkManager.getInstance(activity).cancelUniqueWork(taskReminderName(task.id))
            WorkManager.getInstance(activity).cancelUniqueWork(overdueReminderName(task))
        }
    }

    private fun cancelScheduleReminders(item: ScheduleEntity) {
        DepartureReminderDays.forEach { days ->
            WorkManager.getInstance(activity).cancelUniqueWork("trip_${item.id}_departure_$days")
        }
        listOf("ticket", "hotel_cancel", "balance", "passport").forEach { key ->
            WorkManager.getInstance(activity).cancelUniqueWork("trip_${item.id}_$key")
        }
    }

    fun refreshAllReminders(items: List<ScheduleEntity>, tasks: List<TaskEntity>) {
        // 이전 버전의 출발/주요기한 알림 제거
        items.forEach { cancelScheduleReminders(it) }

        // 허용된 3개 업무만 예약하고 나머지 업무 알림은 모두 제거
        tasks.forEach { task ->
            if (task.completed || !isAllowedNotificationTask(task)) {
                cancelTaskReminder(task)
            } else {
                scheduleTaskReminder(task)
            }
        }
    }

    fun setReminderTime(hour: Int, minute: Int, items: List<ScheduleEntity>, tasks: List<TaskEntity>) {
        activity.getSharedPreferences("traveler_feedback", Context.MODE_PRIVATE)
            .edit().putInt("reminder_hour", hour).putInt("reminder_minute", minute).apply()
        refreshAllReminders(items, tasks)
    }

    fun sendTestReminder() {
        val data = Data.Builder()
            .putString("title", "TRAVELER 알림 테스트")
            .putString("message", "알림이 정상적으로 작동하고 있습니다.")
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(activity).enqueueUniqueWork("traveler_notification_test", ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueReminder(uniqueName: String, date: LocalDate, title: String, message: String) {
        val today = LocalDate.now()
        if (date.isBefore(today)) {
            WorkManager.getInstance(activity).cancelUniqueWork(uniqueName)
            return
        }
        val (hour, minute) = reminderTime()
        var notifyAt = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()
        if (date == today && !notifyAt.isAfter(Instant.now())) {
            notifyAt = Instant.now().plusSeconds(5)
        }
        val delayMs = ChronoUnit.MILLIS.between(Instant.now(), notifyAt).coerceAtLeast(1_000L)
        val data = Data.Builder().putString("title", title).putString("message", message).build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(activity).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val activity: ComponentActivity
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduleViewModel(repository, activity) as T
    }
}

private enum class MainTab { HOME, CALENDAR, SCHEDULES, CONTACTS, SETTINGS }
private enum class CalendarMode { MONTH, WEEK, LIST }
private enum class FeedbackKind { NAVIGATION, SUCCESS, CHECK, DELETE, ERROR, DATE, UNLOCK, REFRESH }

private data class FeedbackOptions(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundIntensity: String = "normal"
)

private class ActionFeedbackController(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sounds = mapOf(
        FeedbackKind.NAVIGATION to soundPool.load(context, R.raw.action_nav, 1),
        FeedbackKind.SUCCESS to soundPool.load(context, R.raw.action_success, 1),
        FeedbackKind.CHECK to soundPool.load(context, R.raw.action_check, 1),
        FeedbackKind.DELETE to soundPool.load(context, R.raw.action_delete, 1),
        FeedbackKind.ERROR to soundPool.load(context, R.raw.action_error, 1),
        FeedbackKind.DATE to soundPool.load(context, R.raw.action_date, 1),
        FeedbackKind.UNLOCK to soundPool.load(context, R.raw.action_unlock, 1),
        FeedbackKind.REFRESH to soundPool.load(context, R.raw.action_refresh, 1)
    )

    fun perform(view: View, options: FeedbackOptions, kind: FeedbackKind) {
        if (options.soundEnabled) {
            val baseVolume = when (kind) {
                FeedbackKind.NAVIGATION -> 0.18f
                FeedbackKind.SUCCESS -> 0.30f
                FeedbackKind.CHECK -> 0.25f
                FeedbackKind.DELETE -> 0.22f
                FeedbackKind.ERROR -> 0.28f
                FeedbackKind.DATE -> 0.18f
                FeedbackKind.UNLOCK -> 0.27f
                FeedbackKind.REFRESH -> 0.18f
            }
            val intensityMultiplier = when (options.soundIntensity) {
                "minimal" -> 0.55f
                "emphasized" -> 1.20f
                else -> 1.0f
            }
            val volume = (baseVolume * intensityMultiplier).coerceIn(0f, 0.45f)
            sounds[kind]?.let { soundPool.play(it, volume, volume, 1, 0, 1f) }
        }

        if (options.vibrationEnabled) {
            when (kind) {
                FeedbackKind.NAVIGATION, FeedbackKind.REFRESH -> Unit
                FeedbackKind.DATE -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                FeedbackKind.SUCCESS, FeedbackKind.CHECK, FeedbackKind.UNLOCK ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                FeedbackKind.DELETE, FeedbackKind.ERROR ->
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    fun release() = soundPool.release()
}

@Composable
private fun Modifier.polishedPress(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = tween(if (pressed) 80 else 150, easing = FastOutSlowInEasing),
        label = "press_scale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
private fun SuccessBannerOverlay(message: String) {
    Popup(alignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(140)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(120))
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1F2937),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF72D38C), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(message, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TravelerApp(
    vm: ScheduleViewModel,
    widgetNavigation: WidgetNavigation?,
    onWidgetNavigationConsumed: () -> Unit,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val systemDark = isSystemInDarkTheme()
    val darkMode = themeMode == "dark" || (themeMode == "system" && systemDark)
    val feedbackController = remember { ActionFeedbackController(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { feedbackController.release() } }
    val prefs = remember { context.getSharedPreferences("traveler_feedback", Context.MODE_PRIVATE) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }
    var soundIntensity by remember { mutableStateOf(prefs.getString("sound_intensity", "normal") ?: "normal") }
    var reminderHour by remember { mutableIntStateOf(prefs.getInt("reminder_hour", 9)) }
    var reminderMinute by remember { mutableIntStateOf(prefs.getInt("reminder_minute", 0)) }
    val reminderEnabled = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            DepartureReminderDays.forEach { days ->
                this[days] = prefs.getBoolean(departureReminderPrefKey(days), true)
            }
        }
    }
    val feedbackOptions = FeedbackOptions(soundEnabled, vibrationEnabled, soundIntensity)
    fun feedback(kind: FeedbackKind) = feedbackController.perform(view, feedbackOptions, kind)
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(splash) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (splash) {
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        } else {
            val barColor = if (darkMode) android.graphics.Color.rgb(24, 26, 29) else android.graphics.Color.WHITE
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkMode
                isAppearanceLightNavigationBars = !darkMode
            }
        }
    }
    LaunchedEffect(Unit) { delay(3000); splash = false }
    if (splash) {
        StartupJingle(soundEnabled)
        SplashScreen()
        return
    }

    // Firebase 로그인 세션은 앱 잠금과 독립적으로 유지합니다.
    // 인증 상태를 잠금 화면보다 먼저 초기화해, 지문/PIN 해제 후 다시 로그인 화면으로
    // 튀는 현상을 방지합니다.
    val auth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember { CredentialManager.create(context) }
    var authUser by remember { mutableStateOf(auth.currentUser) }
    var authInitialized by remember { mutableStateOf(auth.currentUser != null) }
    var sessionRestorePending by remember { mutableStateOf(false) }
    var explicitLogoutRequested by remember { mutableStateOf(false) }
    val authScope = rememberCoroutineScope()
    var authBusy by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val current = firebaseAuth.currentUser
            if (current != null) {
                authUser = current
                authInitialized = true
                sessionRestorePending = false
                explicitLogoutRequested = false
            } else if (explicitLogoutRequested) {
                // 실제 로그아웃 버튼을 눌렀을 때만 로그인 상태를 비웁니다.
                authUser = null
                authInitialized = true
                sessionRestorePending = false
            }
            // 앱 잠금 해제 직후 일시적으로 null 콜백이 와도 기존 세션을 지우지 않습니다.
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val lockPrefs = remember { context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE) }
    var appLockEnabled by remember { mutableStateOf(lockPrefs.getBoolean("enabled", false)) }
    var biometricLockEnabled by remember { mutableStateOf(lockPrefs.getBoolean("biometric", false)) }
    var pinHash by remember { mutableStateOf(lockPrefs.getString("pin_hash", "").orEmpty()) }
    var lockTimeoutMinutes by remember { mutableIntStateOf(lockPrefs.getInt("timeout_minutes", 1)) }
    var appUnlocked by remember { mutableStateOf(!appLockEnabled) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var lastBackgroundAt by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val fragmentActivity = context as? FragmentActivity
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    DisposableEffect(lifecycleOwner, appLockEnabled, lockTimeoutMinutes) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (appLockEnabled && appUnlocked) lastBackgroundAt = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    if (appLockEnabled && appUnlocked && lastBackgroundAt > 0L) {
                        val elapsed = System.currentTimeMillis() - lastBackgroundAt
                        val timeoutMs = lockTimeoutMinutes.coerceAtLeast(0) * 60_000L
                        if (lockTimeoutMinutes == 0 || elapsed >= timeoutMs) appUnlocked = false
                        lastBackgroundAt = 0L
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (appLockEnabled && !appUnlocked) {
        AppLockScreen(
            biometricAvailable = biometricAvailable && biometricLockEnabled && fragmentActivity != null,
            onPinSubmit = { pin ->
                if (hashPin(pin) == pinHash) {
                    // 잠금 해제는 Firebase 로그아웃과 완전히 분리합니다.
                    auth.currentUser?.let { authUser = it }
                    sessionRestorePending = true
                    feedback(FeedbackKind.UNLOCK)
                    authScope.launch {
                        delay(230)
                        appUnlocked = true
                    }
                    true
                } else {
                    feedback(FeedbackKind.ERROR)
                    false
                }
            },
            onBiometric = {
                fragmentActivity?.let { activity ->
                    launchBiometricUnlock(
                        activity,
                        onSuccess = {
                            auth.currentUser?.let { authUser = it }
                            sessionRestorePending = true
                            feedback(FeedbackKind.UNLOCK)
                            authScope.launch {
                                delay(230)
                                appUnlocked = true
                            }
                        },
                        onError = { }
                    )
                }
            }
        )
        return
    }

    LaunchedEffect(appUnlocked, appLockEnabled) {
        if (appUnlocked) {
            sessionRestorePending = true

            // Firebase는 로그인 토큰을 로컬에 유지합니다.
            // 잠금 해제 직후 currentUser 복원이 늦는 기기에서도 로그인 화면이 먼저
            // 나타나지 않도록 짧게 세션 복원을 기다립니다.
            repeat(12) {
                val restored = auth.currentUser
                if (restored != null) {
                    authUser = restored
                    authInitialized = true
                    sessionRestorePending = false
                    return@LaunchedEffect
                }
                delay(125)
            }

            authInitialized = true
            sessionRestorePending = false
        }
    }

    if (!authInitialized || sessionRestorePending) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TravelerRed)
        }
        return
    }

    if (authUser == null) {
        LoginScreen(
            busy = authBusy,
            message = authMessage,
            onLogin = { email, password ->
                authScope.launch {
                    authBusy = true
                    authMessage = null
                    runCatching { auth.signInWithEmailAndPassword(email.trim(), password).await() }
                        .onSuccess {
                            authUser = auth.currentUser
                            authMessage = null
                        }
                        .onFailure { authMessage = firebaseErrorMessage(it) }
                    authBusy = false
                }
            },
            onSignUp = { email, password ->
                authScope.launch {
                    authBusy = true
                    authMessage = null
                    runCatching { auth.createUserWithEmailAndPassword(email.trim(), password).await() }
                        .onSuccess {
                            authUser = auth.currentUser
                            authMessage = "회원가입이 완료되었습니다."
                        }
                        .onFailure { authMessage = firebaseErrorMessage(it) }
                    authBusy = false
                }
            },
            onResetPassword = { email ->
                authScope.launch {
                    if (email.isBlank()) {
                        authMessage = "이메일 주소를 먼저 입력해 주세요."
                        return@launch
                    }
                    authBusy = true
                    runCatching { auth.sendPasswordResetEmail(email.trim()).await() }
                        .onSuccess { authMessage = "비밀번호 재설정 메일을 보냈습니다." }
                        .onFailure { authMessage = firebaseErrorMessage(it) }
                    authBusy = false
                }
            },
            onGoogleSignIn = {
                authScope.launch {
                    authBusy = true
                    authMessage = null
                    runCatching {
                        val clientIdRes = context.resources.getIdentifier(
                            "default_web_client_id",
                            "string",
                            context.packageName
                        )
                        require(clientIdRes != 0) {
                            "Google 로그인 설정이 아직 앱에 반영되지 않았습니다. Firebase에 SHA-1을 등록한 뒤 새 google-services.json으로 교체해 주세요."
                        }
                        val serverClientId = context.getString(clientIdRes)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setServerClientId(serverClientId)
                            .setFilterByAuthorizedAccounts(false)
                            .setAutoSelectEnabled(false)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        val result = credentialManager.getCredential(
                            context = context,
                            request = request
                        )
                        val credential = result.credential
                        require(credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            "Google 계정 정보를 가져오지 못했습니다."
                        }
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                        auth.signInWithCredential(firebaseCredential).await()
                    }.onSuccess {
                        authUser = auth.currentUser
                        authMessage = null
                    }.onFailure {
                        authMessage = firebaseErrorMessage(it)
                    }
                    authBusy = false
                }
            }
        )
        return
    }

    LaunchedEffect(authUser?.uid) {
        if (authUser != null) {
            vm.syncCloud()
            vm.purgeExpiredTrash()
        }
    }

    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val trashSchedules by vm.trashSchedules.collectAsStateWithLifecycle()
    LaunchedEffect(schedules, allTasks, reminderHour, reminderMinute) {
        vm.refreshAllReminders(schedules, allTasks)
    }
    LaunchedEffect(Unit) { WidgetUpdateHelper.requestUpdate(context) }
    var tab by remember { mutableStateOf(MainTab.HOME) }
    val tabHistory = remember { mutableStateListOf<MainTab>() }
    fun navigateToTab(destination: MainTab) {
        if (destination == tab) return
        if (destination == MainTab.HOME) {
            tabHistory.clear()
            tab = MainTab.HOME
        } else {
            tabHistory.add(tab)
            tab = destination
        }
    }
    var selected by remember { mutableStateOf<ScheduleEntity?>(null) }
    var editing by remember { mutableStateOf<ScheduleEntity?>(null) }
    var duplicateSourceId by remember { mutableStateOf<Long?>(null) }
    var duplicateIncludeTasks by remember { mutableStateOf(true) }
    var duplicateRequest by remember { mutableStateOf<ScheduleEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showDashboard by remember { mutableStateOf(false) }
    var showScheduleOverview by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var homeSummaryKind by remember { mutableStateOf<HomeSummaryKind?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var summaryDate by remember { mutableStateOf<LocalDate?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showExchangeSidebar by remember { mutableStateOf(false) }
    var recentScheduleIds by remember { mutableStateOf(loadRecentScheduleIds(context)) }
    var transientSuccessMessage by remember { mutableStateOf<String?>(null) }

    fun showSuccess(message: String) {
        transientSuccessMessage = message
    }

    LaunchedEffect(transientSuccessMessage) {
        if (transientSuccessMessage != null) {
            delay(1500)
            transientSuccessMessage = null
        }
    }

    LaunchedEffect(selected?.id) {
        selected?.id?.takeIf { it > 0 }?.let { recentScheduleIds = recordRecentSchedule(context, it) }
    }

    LaunchedEffect(widgetNavigation, schedules) {
        val navigation = widgetNavigation ?: return@LaunchedEffect

        navigation.scheduleId?.let { scheduleId ->
            val item = schedules.firstOrNull { it.id == scheduleId }
            if (item != null) {
                selected = item
                onWidgetNavigationConsumed()
                return@LaunchedEffect
            }
        }

        if (navigation.openCalendar || navigation.selectedDate != null) {
            tabHistory.clear()
            tab = MainTab.CALENDAR
            navigation.selectedDate?.let { date ->
                selectedDate = date
                month = YearMonth.from(date)
                if (navigation.autoOpenDate) {
                    val daySchedules = schedules.filter { coversDate(it, date) }
                    if (navigation.dateHasSchedule) {
                        if (daySchedules.isNotEmpty()) {
                            summaryDate = date
                            onWidgetNavigationConsumed()
                        }
                    } else {
                        duplicateSourceId = null
                        editing = null
                        showEditor = true
                        onWidgetNavigationConsumed()
                    }
                } else {
                    onWidgetNavigationConsumed()
                }
            } ?: onWidgetNavigationConsumed()
        }
    }

    BackHandler {
        feedback(FeedbackKind.NAVIGATION)
        when {
            showExchangeSidebar -> showExchangeSidebar = false
            showExitConfirm -> showExitConfirm = false
            showEditor -> { showEditor = false; editing = null; duplicateSourceId = null }
            showDashboard -> showDashboard = false
            showScheduleOverview -> showScheduleOverview = false
            showTrash -> showTrash = false
            homeSummaryKind != null -> homeSummaryKind = null
            summaryDate != null -> summaryDate = null
            selected != null -> selected = null
            tab != MainTab.HOME -> {
                tab = if (tabHistory.isNotEmpty()) tabHistory.removeAt(tabHistory.lastIndex) else MainTab.HOME
            }
            else -> showExitConfirm = true
        }
    }

    if (selected != null) {
        val current = schedules.firstOrNull { it.id == selected!!.id } ?: selected!!
        DetailPage(
            item = current,
            tasksFlow = vm.tasks(current.id),
            onBack = { feedback(FeedbackKind.NAVIGATION); selected = null },
            onEdit = { feedback(FeedbackKind.NAVIGATION); duplicateSourceId = null; editing = current; showEditor = true },
            onDuplicate = {
                feedback(FeedbackKind.NAVIGATION)
                duplicateRequest = current
            },
            onToggleFavorite = { feedback(FeedbackKind.CHECK); vm.toggleFavorite(current) },
            onToggleTask = { task -> feedback(FeedbackKind.CHECK); vm.toggleTask(task) },
            onAddTask = { title, due ->
                feedback(FeedbackKind.SUCCESS)
                vm.addTask(current.id, title, due)
                showSuccess("업무가 추가되었습니다")
            },
            onDeleteTask = { task -> feedback(FeedbackKind.DELETE); vm.deleteTask(task) },
            onDeleteSchedule = {
                feedback(FeedbackKind.DELETE)
                vm.moveScheduleToTrash(current) { success ->
                    if (success) showSuccess("일정이 휴지통으로 이동되었습니다")
                }
                selected = null
            },
            onQuickAdd = {
                feedback(FeedbackKind.NAVIGATION)
                duplicateSourceId = null
                duplicateIncludeTasks = true
                editing = null
                selectedDate = LocalDate.now()
                showEditor = true
            },
            onFeedback = { feedback(it) }
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TravelerTopBar(onDashboard = { feedback(FeedbackKind.NAVIGATION); showDashboard = true }) },
            bottomBar = { TravelerBottomBar(tab) { feedback(FeedbackKind.NAVIGATION); navigateToTab(it) } },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        feedback(FeedbackKind.NAVIGATION)
                        duplicateSourceId = null
                        duplicateIncludeTasks = true
                        editing = null
                        if (tab == MainTab.CALENDAR) selectedDate = selectedDate else selectedDate = LocalDate.now()
                        showEditor = true
                    },
                    containerColor = TravelerRed,
                    contentColor = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "새 일정") }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 18 })
                            .togetherWith(
                                fadeOut(tween(140)) +
                                    slideOutVertically(tween(180)) { -it / 22 }
                            )
                    },
                    label = "main_tab_transition"
                ) { activeTab ->
                    when (activeTab) {
                    MainTab.HOME -> HomePage(
                        schedules = schedules,
                        tasks = allTasks,
                        onSchedule = { feedback(FeedbackKind.NAVIGATION); selected = it },
                        onQuickAdd = { feedback(FeedbackKind.NAVIGATION); duplicateSourceId = null; editing = null; showEditor = true },
                        onGoCalendar = { feedback(FeedbackKind.NAVIGATION); navigateToTab(MainTab.CALENDAR) },
                        onDashboard = { feedback(FeedbackKind.NAVIGATION); showDashboard = true },
                        onOverview = { feedback(FeedbackKind.NAVIGATION); showScheduleOverview = true },
                        onTaskSchedule = { scheduleId ->
                            schedules.firstOrNull { it.id == scheduleId }?.let {
                                feedback(FeedbackKind.NAVIGATION)
                                selected = it
                            }
                        },
                        onSummary = { kind ->
                            feedback(FeedbackKind.NAVIGATION)
                            homeSummaryKind = kind
                        },
                        recentScheduleIds = recentScheduleIds
                    )
                    MainTab.CALENDAR -> CalendarPage(
                        month = month,
                        selectedDate = selectedDate,
                        schedules = schedules,
                        onPrev = { feedback(FeedbackKind.NAVIGATION); month = month.minusMonths(1) },
                        onNext = { feedback(FeedbackKind.NAVIGATION); month = month.plusMonths(1) },
                        onMonthSelected = { selectedMonth ->
                            feedback(FeedbackKind.NAVIGATION)
                            month = selectedMonth
                            selectedDate = selectedMonth.atDay(1)
                        },
                        onToday = { feedback(FeedbackKind.NAVIGATION); month = YearMonth.now(); selectedDate = LocalDate.now() },
                        onDate = { date ->
                            feedback(FeedbackKind.NAVIGATION)
                            selectedDate = date
                            val daySchedules = schedules.filter { coversDate(it, date) }
                            if (daySchedules.isEmpty()) {
                                duplicateSourceId = null
                                editing = null
                                showEditor = true
                            } else {
                                summaryDate = date
                            }
                        },
                        onSchedule = { feedback(FeedbackKind.NAVIGATION); selected = it },
                        onFeedback = { feedback(it) }
                    )
                    MainTab.SCHEDULES -> ScheduleListPage(
                        schedules = schedules,
                        tasks = allTasks,
                        trashCount = trashSchedules.size,
                        onSchedule = { feedback(FeedbackKind.NAVIGATION); selected = it },
                        onTrash = { feedback(FeedbackKind.NAVIGATION); showTrash = true }
                    )
                    MainTab.CONTACTS -> ContactsPage()
                    MainTab.SETTINGS -> SettingsPage(
                        accountEmail = authUser?.email.orEmpty(),
                        syncStatus = vm.syncStatus,
                        lastSyncText = vm.lastSyncText,
                        soundEnabled = soundEnabled,
                        vibrationEnabled = vibrationEnabled,
                        soundIntensity = soundIntensity,
                        reminderEnabled = reminderEnabled,
                        reminderHour = reminderHour,
                        reminderMinute = reminderMinute,
                        themeMode = themeMode,
                        onThemeModeChanged = { mode ->
                            onThemeModeChanged(mode)
                            feedback(FeedbackKind.NAVIGATION)
                        },
                        onSoundChanged = { enabled ->
                            soundEnabled = enabled
                            prefs.edit().putBoolean("sound_enabled", enabled).apply()
                            if (enabled) feedbackController.perform(view, FeedbackOptions(true, vibrationEnabled, soundIntensity), FeedbackKind.SUCCESS)
                        },
                        onVibrationChanged = { enabled ->
                            vibrationEnabled = enabled
                            prefs.edit().putBoolean("vibration_enabled", enabled).apply()
                            if (enabled) feedbackController.perform(view, FeedbackOptions(soundEnabled, true, soundIntensity), FeedbackKind.SUCCESS)
                        },
                        onSoundIntensityChanged = { intensity ->
                            soundIntensity = intensity
                            prefs.edit().putString("sound_intensity", intensity).apply()
                            feedbackController.perform(
                                view,
                                FeedbackOptions(soundEnabled, vibrationEnabled, intensity),
                                FeedbackKind.SUCCESS
                            )
                        },
                        onReminderChanged = { days, enabled ->
                            reminderEnabled[days] = enabled
                            vm.setDepartureReminderEnabled(days, enabled, schedules)
                            feedback(FeedbackKind.CHECK)
                        },
                        onReminderTimeChanged = { hour, minute ->
                            reminderHour = hour
                            reminderMinute = minute
                            vm.setReminderTime(hour, minute, schedules, allTasks)
                            feedback(FeedbackKind.SUCCESS)
                        },
                        onTestReminder = { vm.sendTestReminder(); feedback(FeedbackKind.SUCCESS) },
                        onSyncNow = {
                            vm.syncCloud { success, _ ->
                                if (success) {
                                    feedback(FeedbackKind.REFRESH)
                                    showSuccess("클라우드 동기화가 완료되었습니다")
                                } else {
                                    feedback(FeedbackKind.ERROR)
                                }
                            }
                        },
                        appLockEnabled = appLockEnabled,
                        biometricLockEnabled = biometricLockEnabled,
                        biometricAvailable = biometricAvailable,
                        lockTimeoutMinutes = lockTimeoutMinutes,
                        trashCount = trashSchedules.size,
                        onOpenTrash = { feedback(FeedbackKind.NAVIGATION); showTrash = true },
                        onAppLockChanged = { enabled ->
                            if (enabled && pinHash.isBlank()) {
                                showPinSetupDialog = true
                            } else {
                                appLockEnabled = enabled
                                lockPrefs.edit().putBoolean("enabled", enabled).apply()
                            }
                        },
                        onBiometricLockChanged = { enabled ->
                            biometricLockEnabled = enabled && biometricAvailable
                            lockPrefs.edit().putBoolean("biometric", biometricLockEnabled).apply()
                        },
                        onChangePin = { showPinSetupDialog = true },
                        onLockTimeoutChanged = { minutes ->
                            lockTimeoutMinutes = minutes
                            lockPrefs.edit().putInt("timeout_minutes", minutes).apply()
                        },
                        onLogout = {
                            vm.syncCloud { success, _ ->
                                if (success) {
                                    vm.clearLocal {
                                        explicitLogoutRequested = true
                                        auth.signOut()
                                        authScope.launch {
                                            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                                        }
                                        authUser = null
                                        authInitialized = true
                                        sessionRestorePending = false
                                    }
                                }
                            }
                        }
                    )
                    }
                }
            }
        }
    }

    ExchangeRateSidebarHandle(onClick = { feedback(FeedbackKind.NAVIGATION); showExchangeSidebar = true })
    AnimatedVisibility(
        visible = showExchangeSidebar,
        enter = fadeIn(tween(120)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it }
    ) {
        ExchangeRateSidebar(onDismiss = { showExchangeSidebar = false })
    }

    if (showEditor) {
        ScheduleEditorDialog(
            initial = editing,
            initialDate = selectedDate,
            onDismiss = { feedback(FeedbackKind.NAVIGATION); showEditor = false; editing = null; duplicateSourceId = null },
            onSave = { item ->
                feedback(FeedbackKind.SUCCESS)
                val wasNew = item.id == 0L
                val sourceId = duplicateSourceId
                if (sourceId != null) vm.duplicateSchedule(sourceId, item, duplicateIncludeTasks)
                else if (item.id == 0L) vm.addSchedule(item) else vm.updateSchedule(item)
                showEditor = false
                editing = null
                duplicateSourceId = null
                duplicateIncludeTasks = true
                showSuccess(
                    when {
                        sourceId != null -> "일정이 복제되었습니다"
                        wasNew -> "일정이 저장되었습니다"
                        else -> "일정이 수정되었습니다"
                    }
                )
            },
            onFeedback = { feedback(it) }
        )
    }

    if (showDashboard) {
        DashboardDialog(
            schedules = schedules,
            tasks = allTasks,
            onDismiss = { feedback(FeedbackKind.NAVIGATION); showDashboard = false },
            onOpenSchedule = { schedule ->
                feedback(FeedbackKind.NAVIGATION)
                showDashboard = false
                selected = schedule
            }
        )
    }

    if (showScheduleOverview) {
        ScheduleOverviewDialog(
            schedules = schedules,
            tasks = allTasks,
            onDismiss = { feedback(FeedbackKind.NAVIGATION); showScheduleOverview = false },
            onOpenSchedule = { schedule ->
                feedback(FeedbackKind.NAVIGATION)
                showScheduleOverview = false
                selected = schedule
            }
        )
    }

    homeSummaryKind?.let { kind ->
        HomeSummaryDialog(
            kind = kind,
            schedules = schedules,
            tasks = allTasks,
            onDismiss = { feedback(FeedbackKind.NAVIGATION); homeSummaryKind = null },
            onOpenSchedule = { item ->
                feedback(FeedbackKind.NAVIGATION)
                homeSummaryKind = null
                selected = item
            }
        )
    }

    summaryDate?.let { date ->
        val daySchedules = schedules.filter { coversDate(it, date) }
        DateScheduleSummaryDialog(
            date = date,
            schedules = daySchedules,
            onDismiss = { feedback(FeedbackKind.NAVIGATION); summaryDate = null },
            onOpenSchedule = { item ->
                feedback(FeedbackKind.NAVIGATION)
                summaryDate = null
                selected = item
            },
            onAddSchedule = {
                feedback(FeedbackKind.NAVIGATION)
                summaryDate = null
                selectedDate = date
                editing = null
                showEditor = true
            }
        )
    }

    transientSuccessMessage?.let { message ->
        SuccessBannerOverlay(message)
    }

    if (showTrash) {
        TrashDialog(
            items = trashSchedules,
            onDismiss = { showTrash = false },
            onRestore = { item ->
                feedback(FeedbackKind.SUCCESS)
                vm.restoreSchedule(item) { ok ->
                    if (ok) showSuccess("일정이 복원되었습니다")
                }
            },
            onPermanentDelete = { item ->
                feedback(FeedbackKind.DELETE)
                vm.permanentlyDeleteSchedule(item) { ok ->
                    if (ok) showSuccess("일정이 완전히 삭제되었습니다")
                    else feedback(FeedbackKind.ERROR)
                }
            },
            onEmptyTrash = {
                feedback(FeedbackKind.DELETE)
                vm.emptyTrash { deleted, failed ->
                    when {
                        failed == 0 -> showSuccess("휴지통 ${deleted}건을 비웠습니다")
                        deleted > 0 -> showSuccess("${deleted}건 삭제 · ${failed}건은 동기화 후 다시 시도")
                        else -> feedback(FeedbackKind.ERROR)
                    }
                }
            }
        )
    }

    duplicateRequest?.let { source ->
        DuplicateOptionsDialog(
            source = source,
            onDismiss = { duplicateRequest = null },
            onContinue = { newStart, includeTasks, includeFlightHotel ->
                val oldStart = parseDate(source.startDate) ?: LocalDate.now()
                val oldEnd = parseDate(source.endDate) ?: oldStart
                val duration = ChronoUnit.DAYS.between(oldStart, oldEnd).coerceAtLeast(0)
                duplicateSourceId = source.id
                duplicateIncludeTasks = includeTasks
                editing = source.copy(
                    id = 0,
                    title = "${source.title} 복사본",
                    startDate = newStart.toString(),
                    endDate = newStart.plusDays(duration).toString(),
                    flight = if (includeFlightHotel) source.flight else "",
                    hotel = if (includeFlightHotel) source.hotel else "",
                    favorite = false,
                    cloudId = "",
                    updatedAt = 0L
                )
                selectedDate = newStart
                duplicateRequest = null
                showEditor = true
            }
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onDismiss = { showPinSetupDialog = false },
            onSave = { pin ->
                pinHash = hashPin(pin)
                appLockEnabled = true
                lockPrefs.edit().putString("pin_hash", pinHash).putBoolean("enabled", true).apply()
                showPinSetupDialog = false
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("앱 종료") },
            text = { Text("TRAVELER Schedule을 종료할까요?") },
            confirmButton = {
                TextButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                    Text("종료", color = TravelerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun DateScheduleSummaryDialog(
    date: LocalDate,
    schedules: List<ScheduleEntity>,
    onDismiss: () -> Unit,
    onOpenSchedule: (ScheduleEntity) -> Unit,
    onAddSchedule: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(date.format(KoreanDayFormatter), fontWeight = FontWeight.Bold)
                Text("등록 일정 ${schedules.size}건", color = TravelerGray, fontSize = 12.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules, key = { "summary_${it.id}" }) { item ->
                    ScheduleRow(item) { onOpenSchedule(item) }
                }
                item {
                    Text(
                        "일정을 누르면 상세 화면으로 이동합니다.",
                        color = TravelerGray,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = {
            TextButton(onClick = onAddSchedule) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("일정 추가", color = TravelerRed)
            }
        }
    )
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    message: String?,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var signUpMode by remember { mutableStateOf(false) }

    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(90)
        cardVisible = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(22.dp)
    ) {
        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(260)) + slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 8 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 10 }
        ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.traveler_logo),
                        contentDescription = "TRAVELER",
                        modifier = Modifier.fillMaxWidth(.72f).height(54.dp).padding(horizontal = 8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    if (signUpMode) "계정 만들기" else "로그인",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (signUpMode) "계정을 만들면 일정과 업무를 클라우드에 저장할 수 있습니다."
                    else "같은 계정으로 로그인하면 저장된 일정을 불러옵니다.",
                    color = TravelerGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("이메일") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (signUpMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("비밀번호 확인") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
                if (!message.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFFFFF3F2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(message, color = TravelerRed, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                    }
                }
                Button(
                    onClick = {
                        if (signUpMode) onSignUp(email, password) else onLogin(email, password)
                    },
                    enabled = !busy && email.isNotBlank() && password.length >= 6 && (!signUpMode || password == confirmPassword),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(if (signUpMode) "회원가입" else "로그인", fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = LightBorder)
                    Text("또는", color = TravelerGray, fontSize = 12.sp)
                    HorizontalDivider(Modifier.weight(1f), color = LightBorder)
                }
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4))
                    Spacer(Modifier.width(8.dp))
                    Text("Google로 계속하기", fontWeight = FontWeight.Bold, color = TravelerBlack)
                }
                if (!signUpMode) {
                    TextButton(onClick = { onResetPassword(email) }, enabled = !busy) {
                        Text("비밀번호를 잊으셨나요?", color = TravelerGray)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = {
                        signUpMode = !signUpMode
                        confirmPassword = ""
                    },
                    enabled = !busy
                ) {
                    Text(if (signUpMode) "이미 계정이 있습니다 · 로그인" else "처음이신가요? · 회원가입", color = TravelerBlue)
                }
            }
        }
        }
    }
}

private fun firebaseErrorMessage(error: Throwable): String {
    val text = error.localizedMessage.orEmpty()
    return when {
        text.contains("password", ignoreCase = true) && text.contains("invalid", ignoreCase = true) -> "이메일 또는 비밀번호가 올바르지 않습니다."
        text.contains("email", ignoreCase = true) && text.contains("already", ignoreCase = true) -> "이미 가입된 이메일입니다."
        text.contains("network", ignoreCase = true) -> "인터넷 연결을 확인해 주세요."
        text.contains("blocked", ignoreCase = true) || text.contains("disabled", ignoreCase = true) -> "Firebase에서 이메일/비밀번호 로그인이 활성화되어 있는지 확인해 주세요."
        else -> text.ifBlank { "처리 중 오류가 발생했습니다." }
    }
}

@Composable
private fun StartupJingle(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val player = if (enabled) {
            MediaPlayer.create(context, R.raw.startup_jingle)?.apply {
                setVolume(0.28f, 0.28f)
                start()
            }
        } else null

        onDispose {
            player?.let { mediaPlayer ->
                runCatching {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.setVolume(0.12f, 0.12f)
                        mediaPlayer.stop()
                    }
                }
                mediaPlayer.release()
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
        delay(2450)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "splash_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else .94f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 22.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.traveler_splash_logo),
                contentDescription = "TRAVELER",
                modifier = Modifier.fillMaxWidth(0.92f),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(20.dp))
            Text("여행 업무 일정관리", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("일정 · 업무 · 알림을 한곳에서", color = Color.White.copy(alpha = .66f), fontSize = 12.sp)
        }
        Text(
            "TRAVELER · SCHEDULE V4.6.2",
            color = Color.White.copy(alpha = .38f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .alpha(alpha)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelerTopBar(onDashboard: () -> Unit) {
    TopAppBar(
        title = {
            Image(
                painterResource(R.drawable.traveler_logo),
                "TRAVELER",
                Modifier.width(170.dp).height(40.dp),
                contentScale = ContentScale.Fit
            )
        },
        actions = {
            TextButton(onClick = onDashboard) { Text("업무 현황", color = TravelerRed, fontWeight = FontWeight.Bold) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun TravelerBottomBar(current: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        val items = listOf(
            Triple(MainTab.HOME, Icons.Default.Home, "홈"),
            Triple(MainTab.CALENDAR, Icons.Default.CalendarMonth, "캘린더"),
            Triple(MainTab.SCHEDULES, Icons.Default.EventNote, "일정"),
            Triple(MainTab.CONTACTS, Icons.Default.Contacts, "주소록"),
            Triple(MainTab.SETTINGS, Icons.Default.Settings, "설정")
        )
        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        icon,
                        label,
                        modifier = Modifier.size(if (current == tab) 25.dp else 23.dp)
                    )
                },
                label = { Text(label, fontWeight = if (current == tab) FontWeight.Bold else FontWeight.Normal) },
                alwaysShowLabel = current == tab,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TravelerRed,
                    selectedTextColor = TravelerRed,
                    indicatorColor = Color(0xFFFFECEB)
                )
            )
        }
    }
}

@Composable
private fun HomePage(
    schedules: List<ScheduleEntity>,
    tasks: List<TaskEntity>,
    onSchedule: (ScheduleEntity) -> Unit,
    onQuickAdd: () -> Unit,
    onGoCalendar: () -> Unit,
    onDashboard: () -> Unit,
    onOverview: () -> Unit,
    onTaskSchedule: (Long) -> Unit,
    onSummary: (HomeSummaryKind) -> Unit,
    recentScheduleIds: List<Long>
) {
    val today = LocalDate.now()
    val todayItems = schedules.filter { coversDate(it, today) }
    val thisMonth = schedules.filter {
        parseDate(it.startDate)?.let { d -> YearMonth.from(d) == YearMonth.now() } == true
    }
    val upcoming = schedules.count { parseDate(it.startDate)?.let { d -> !d.isBefore(today) } == true && it.status !in listOf("완료", "취소") }
    val undone = tasks.count { !it.completed }
    val favorites = sortSchedulesForWork(schedules.filter { it.favorite && it.status != "취소" })
    val recentSchedules = recentScheduleIds.mapNotNull { id -> schedules.firstOrNull { it.id == id } }.take(3)
    var homeFilter by remember { mutableStateOf(HomeFilter.UPCOMING) }
    val weekEnd = today.plusDays(6)
    val filteredQuickSchedules = sortSchedulesForWork(schedules.filter { item ->
        val start = parseDate(item.startDate)
        val end = parseDate(item.endDate)
        when (homeFilter) {
            HomeFilter.TODAY -> coversDate(item, today)
            HomeFilter.THIS_WEEK -> start != null && !start.isAfter(weekEnd) && (end ?: start) >= today
            HomeFilter.THIS_MONTH -> start?.let { YearMonth.from(it) == YearMonth.now() } == true
            HomeFilter.UPCOMING -> start != null && !start.isBefore(today) && item.status !in listOf("완료", "취소")
            HomeFilter.IN_PROGRESS -> item.status == "진행중" || (start != null && end != null && !today.isBefore(start) && !today.isAfter(end) && item.status !in listOf("완료", "취소"))
            HomeFilter.COMPLETED -> item.status == "완료"
        }
    })
    val todayTasks = tasks.filter { task ->
        !task.completed && parseDate(task.dueDate)?.let { !it.isAfter(today) } == true
    }.sortedWith(compareBy<TaskEntity> { parseDate(it.dueDate) ?: LocalDate.MAX }.thenBy { it.id })

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeBannerCarousel()
        }
        item {
            SectionTitle("업무 요약", YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy년 M월")), action = "대시보드", onAction = onOverview)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("이번 달", "${thisMonth.size}건", Icons.Default.CalendarMonth, Modifier.weight(1f)) { onSummary(HomeSummaryKind.THIS_MONTH) }
                SummaryCard("출발 예정", "${upcoming}건", Icons.Default.FlightTakeoff, Modifier.weight(1f)) { onSummary(HomeSummaryKind.UPCOMING) }
                SummaryCard("미완료 업무", "${undone}건", Icons.Default.CheckCircle, Modifier.weight(1f)) { onSummary(HomeSummaryKind.UNDONE_TASKS) }
            }
        }
        if (recentSchedules.isNotEmpty()) {
            item { SectionTitle("최근 본 일정", "빠른 접근") }
            items(recentSchedules, key = { "recent_${it.id}" }) { ScheduleRow(it, tasks, onSchedule) }
        }
        item {
            SectionTitle("일정 바로보기", homeFilter.label)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeFilter.entries.forEach { filter ->
                    FilterChip(selected = homeFilter == filter, onClick = { homeFilter = filter }, label = { Text(filter.label) })
                }
            }
        }
        if (filteredQuickSchedules.isEmpty()) item { EmptyCard("선택한 조건의 일정이 없습니다.") }
        else items(filteredQuickSchedules.take(5), key = { "filter_${homeFilter.name}_${it.id}" }) { ScheduleRow(it, tasks, onSchedule) }
        if (favorites.isNotEmpty()) {
            item { SectionTitle("중요 일정", "즐겨찾기 ${favorites.size}건") }
            items(favorites.take(3), key = { "fav_${it.id}" }) { ScheduleRow(it, tasks, onSchedule) }
        }
        item { SectionTitle("오늘 일정", today.format(KoreanDayFormatter), action = "캘린더", onAction = onGoCalendar) }
        if (todayItems.isEmpty()) item { EmptyCard("오늘 진행 중인 일정이 없습니다.") }
        else items(todayItems.take(5), key = { it.id }) { ScheduleRow(it, tasks, onSchedule) }
        item { SectionTitle("오늘의 업무", if (todayTasks.isEmpty()) "완료" else "${todayTasks.size}건") }
        if (todayTasks.isEmpty()) {
            item { EmptyCard("오늘까지 처리할 미완료 업무가 없습니다.") }
        } else {
            val groupedTodayTasks = todayTasks.groupBy { it.scheduleId }.entries
                .sortedBy { entry -> schedules.firstOrNull { it.id == entry.key }?.startDate ?: "9999-12-31" }
            items(groupedTodayTasks, key = { "today_group_${it.key}" }) { entry ->
                val schedule = schedules.firstOrNull { it.id == entry.key }
                TodayTaskGroupCard(schedule, entry.value, tasks.filter { it.scheduleId == entry.key }) { onTaskSchedule(entry.key) }
            }
        }
        item {
            Text("빠른 실행", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("새 일정", Icons.Default.Add, Modifier.weight(1f), onQuickAdd)
                QuickAction("캘린더", Icons.Default.CalendarMonth, Modifier.weight(1f), onGoCalendar)
                QuickAction("업무 현황", Icons.Default.CheckCircle, Modifier.weight(1f), onDashboard)
            }
        }
    }
}

@Composable
private fun ExchangeRateSidebarHandle(onClick: () -> Unit) {
    Popup(alignment = Alignment.CenterStart) {
        Surface(
            modifier = Modifier.clickable(onClick = onClick),
            color = TravelerRed,
            shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
            shadowElevation = 7.dp
        ) {
            Column(
                Modifier.padding(horizontal = 7.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("₩", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("환율", color = Color.White, fontSize = 9.sp)
                Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ExchangeRateSidebar(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(onClick = onDismiss)) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.96f)
                    .align(Alignment.CenterStart)
                    .clickable { },
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("실시간 환율", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
                    }
                    Text("10개 통화 / KRW · 사이드바 진입 시 자동 갱신", color = TravelerGray, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    ExchangeRateCard(onRefreshed = { /* sidebar refresh feedback handled locally */ })
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateCard(onRefreshed: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(loadCachedExchangeRates(context)) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var calculatorCurrency by remember { mutableStateOf("USD") }
    var calculatorAmount by remember { mutableStateOf("") }

    fun refreshRates() {
        if (loading) return
        loading = true
        errorMessage = null
        scope.launch {
            runCatching { fetchExchangeRates() }
                .onSuccess { fresh ->
                    snapshot = fresh
                    saveExchangeRates(context, fresh)
                    onRefreshed()
                    val view = (context as? Activity)?.window?.decorView
                    view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                .onFailure { e ->
                    errorMessage = e.localizedMessage ?: "환율을 불러오지 못했습니다."
                }
            loading = false
        }
    }

    // 사이드바를 열 때마다 새 Composable이 생성되므로 항상 최신 환율을 조회합니다.
    LaunchedEffect(Unit) {
        refreshRates()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "환율",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "KRW 기준 · 여행 업무 참고용",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { refreshRates() }, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = TravelerBlue)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "환율 새로고침", tint = TravelerBlue)
                    }
                }
            }

            val data = snapshot
            if (data == null) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        Text("최신 환율을 불러오는 중입니다...", color = TravelerGray, fontSize = 13.sp)
                    }
                } else {
                    Text(errorMessage ?: "환율 정보가 없습니다.", color = TravelerRed, fontSize = 13.sp)
                    OutlinedButton(onClick = { refreshRates() }) { Text("다시 시도") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExchangeRateTile("USD", "1달러", formatKrw(data.usdKrw), Modifier.weight(1f))
                    ExchangeRateTile("JPY", "100엔", formatKrw(data.jpyKrw * 100.0), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExchangeRateTile("CNY", "1위안", formatKrw(data.cnyKrw), Modifier.weight(1f))
                    ExchangeRateTile("EUR", "1유로", formatKrw(data.eurKrw), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExchangeRateTile("PHP", "1페소", formatKrw(data.phpKrw), Modifier.weight(1f))
                    ExchangeRateTile("VND", "1,000동", formatKrw(data.vndKrw * 1000.0), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExchangeRateTile("THB", "1바트", formatKrw(data.thbKrw), Modifier.weight(1f))
                    ExchangeRateTile("TWD", "1대만달러", formatKrw(data.twdKrw), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExchangeRateTile("HKD", "1홍콩달러", formatKrw(data.hkdKrw), Modifier.weight(1f))
                    ExchangeRateTile("MNT", "1,000투그릭", formatKrw(data.mntKrw * 1000.0), Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val ref = data.referenceDate.takeIf { it.isNotBlank() }
                        ?.let { "시장 기준 $it" }
                        ?: formatFetchedAt(data.fetchedAt)
                    Text(
                        ref,
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (errorMessage != null) {
                        Text("마지막 저장값 표시", fontSize = 10.sp, color = TravelerRed)
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))

                ExchangeRateCalculator(
                    data = data,
                    selectedCode = calculatorCurrency,
                    onSelectedCode = { calculatorCurrency = it },
                    amountText = calculatorAmount,
                    onAmountChanged = { input ->
                        calculatorAmount = input.filter { ch -> ch.isDigit() || ch == '.' }
                            .let { filtered ->
                                val dot = filtered.indexOf('.')
                                if (dot >= 0) {
                                    filtered.take(dot + 1) + filtered.drop(dot + 1).replace(".", "")
                                } else filtered
                            }
                    }
                )
            }

            Text(
                "※ 약 5분 단위 시장 참고 환율입니다. 은행 현찰/송금 환율과 차이가 날 수 있습니다.",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class ExchangeCalculatorCurrency(
    val code: String,
    val country: String,
    val currencyName: String
)

private val ExchangeCalculatorCurrencies = listOf(
    ExchangeCalculatorCurrency("USD", "미국", "달러"),
    ExchangeCalculatorCurrency("JPY", "일본", "엔"),
    ExchangeCalculatorCurrency("CNY", "중국", "위안"),
    ExchangeCalculatorCurrency("EUR", "유럽", "유로"),
    ExchangeCalculatorCurrency("PHP", "필리핀", "페소"),
    ExchangeCalculatorCurrency("VND", "베트남", "동"),
    ExchangeCalculatorCurrency("THB", "태국", "바트"),
    ExchangeCalculatorCurrency("TWD", "대만", "대만달러"),
    ExchangeCalculatorCurrency("HKD", "홍콩", "홍콩달러"),
    ExchangeCalculatorCurrency("MNT", "몽골", "투그릭")
)

private fun ExchangeRateSnapshot.krwPerUnit(code: String): Double = when (code) {
    "USD" -> usdKrw
    "JPY" -> jpyKrw
    "CNY" -> cnyKrw
    "EUR" -> eurKrw
    "PHP" -> phpKrw
    "VND" -> vndKrw
    "THB" -> thbKrw
    "TWD" -> twdKrw
    "HKD" -> hkdKrw
    "MNT" -> mntKrw
    else -> 0.0
}

@Composable
private fun ExchangeRateCalculator(
    data: ExchangeRateSnapshot,
    selectedCode: String,
    onSelectedCode: (String) -> Unit,
    amountText: String,
    onAmountChanged: (String) -> Unit
) {
    val selected = ExchangeCalculatorCurrencies.firstOrNull { it.code == selectedCode }
        ?: ExchangeCalculatorCurrencies.first()
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val calculatedKrw = amount * data.krwPerUnit(selected.code)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Calculate, null, tint = TravelerRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text("환율 계산기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "현재 사이드바에서 갱신된 환율을 사용합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ExchangeCalculatorCurrencies, key = { it.code }) { currency ->
                FilterChip(
                    selected = selected.code == currency.code,
                    onClick = { onSelectedCode(currency.code) },
                    label = { Text("${currency.country} ${currency.code}", fontSize = 10.sp) }
                )
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("${selected.country} ${selected.currencyName} 금액") },
            suffix = { Text(selected.code) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = TravelerBlue.copy(alpha = .08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, TravelerBlue.copy(alpha = .18f))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("한국 원화 환산", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                AnimatedContent(
                    targetState = if (amountText.isBlank()) "금액을 입력하세요" else "약 ${formatKrw(calculatedKrw)}",
                    transitionSpec = { fadeIn(tween(160)).togetherWith(fadeOut(tween(100))) },
                    label = "calculator_result"
                ) { resultText ->
                    Text(
                        resultText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TravelerBlue
                    )
                }
                if (amount > 0.0) {
                    Text(
                        "${String.format(Locale.KOREA, "%,.2f", amount)} ${selected.code} × ${formatKrw(data.krwPerUnit(selected.code))}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateTile(
    code: String,
    unit: String,
    wonText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    code,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    unit,
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedContent(
                targetState = wonText,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "exchange_value"
            ) { animatedText ->
                Text(
                    animatedText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TravelerBlue,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeBannerCarousel() {
    val context = LocalContext.current
    val banners = remember {
        listOf(
            HomeBanner(R.drawable.banner_traveler, "https://www.traveler-kr.com/", "TRAVELER 홈페이지"),
            HomeBanner(R.drawable.banner_verygoodtour, "https://www.verygoodtour.com/", "참좋은여행 홈페이지"),
            HomeBanner(R.drawable.banner_skyscanner, "https://www.skyscanner.co.kr/", "스카이스캐너 홈페이지")
        )
    }
    val loopPageCount = Int.MAX_VALUE
    val loopStart = remember {
        val middle = loopPageCount / 2
        middle - (middle % banners.size)
    }
    val pagerState = rememberPagerState(
        initialPage = loopStart,
        pageCount = { loopPageCount }
    )

    LaunchedEffect(pagerState) {
        while (true) {
            delay(4_500)
            pagerState.animateScrollToPage(
                pagerState.currentPage + 1,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val bannerIndex = Math.floorMod(page, banners.size)
            val banner = banners[bannerIndex]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(horizontal = 2.dp)
                    .graphicsLayer {
                        val fraction = pageOffset.coerceIn(0f, 1f)
                        scaleX = 1f - (fraction * .035f)
                        scaleY = 1f - (fraction * .035f)
                        alpha = 1f - (fraction * .18f)
                    }
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(banner.url)))
                        }
                    }
            ) {
                Image(
                    painter = painterResource(banner.imageRes),
                    contentDescription = banner.contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeBanner = Math.floorMod(pagerState.currentPage, banners.size)
            repeat(banners.size) { index ->
                Box(
                    Modifier
                        .size(if (activeBanner == index) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeBanner == index) TravelerRed
                            else Color(0xFFD0D5DD)
                        )
                )
            }
        }
    }
}

@Composable
private fun ScheduleOverviewDialog(
    schedules: List<ScheduleEntity>,
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onOpenSchedule: (ScheduleEntity) -> Unit
) {
    val today = LocalDate.now()
    val reserved = sortSchedulesForWork(
        schedules.filter { item ->
            item.status in listOf("예약", "확정", "진행중") &&
                item.status != "취소" &&
                parseDate(item.endDate)?.let { !it.isBefore(today) } != false
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("예약 일정 요약", fontWeight = FontWeight.Bold)
                Text("예정·확정·진행중 일정을 간단히 확인합니다.", color = TravelerGray, fontSize = 11.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (reserved.isEmpty()) {
                    item { EmptyCard("현재 예약된 일정이 없습니다.") }
                } else {
                    items(reserved, key = { "overview_${it.id}" }) { item ->
                        val scheduleTasks = tasks.filter { it.scheduleId == item.id }
                        val done = scheduleTasks.count { it.completed }
                        val total = scheduleTasks.size
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenSchedule(item) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    AssistChip(
                                        onClick = { onOpenSchedule(item) },
                                        label = { Text(item.status, fontSize = 9.sp) }
                                    )
                                }
                                val meta = listOf(item.groupName, item.location).filter(String::isNotBlank).joinToString(" · ")
                                if (meta.isNotBlank()) Text(meta, color = TravelerGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${item.startDate} ~ ${item.endDate}", color = TravelerGray, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    departureDday(item)?.let { label ->
                                        Text(label, color = ddayColor(item), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (total > 0) {
                                    Text("업무 $done/$total 완료", color = TravelerBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun HomeSummaryDialog(
    kind: HomeSummaryKind,
    schedules: List<ScheduleEntity>,
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onOpenSchedule: (ScheduleEntity) -> Unit
) {
    val today = LocalDate.now()
    val thisMonthSchedules = schedules
        .filter { parseDate(it.startDate)?.let { d -> YearMonth.from(d) == YearMonth.now() } == true }
        .sortedBy { it.startDate }
    val upcomingSchedules = schedules
        .filter {
            parseDate(it.startDate)?.let { d -> !d.isBefore(today) } == true &&
                it.status !in listOf("완료", "취소")
        }
        .sortedBy { it.startDate }
    val undoneTasks = tasks.filter { !it.completed }
        .sortedWith(compareBy<TaskEntity> { parseDate(it.dueDate) ?: LocalDate.MAX }.thenBy { it.title })

    val title = when (kind) {
        HomeSummaryKind.THIS_MONTH -> "이번 달 일정"
        HomeSummaryKind.UPCOMING -> "출발 예정 일정"
        HomeSummaryKind.UNDONE_TASKS -> "미완료 업무"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (kind) {
                    HomeSummaryKind.THIS_MONTH -> {
                        if (thisMonthSchedules.isEmpty()) {
                            item { EmptyCard("이번 달 등록된 일정이 없습니다.") }
                        } else {
                            items(thisMonthSchedules, key = { "month_${it.id}" }) { item ->
                                ScheduleRow(item, tasks) { onOpenSchedule(item) }
                            }
                        }
                    }
                    HomeSummaryKind.UPCOMING -> {
                        if (upcomingSchedules.isEmpty()) {
                            item { EmptyCard("출발 예정 일정이 없습니다.") }
                        } else {
                            items(upcomingSchedules, key = { "upcoming_${it.id}" }) { item ->
                                ScheduleRow(item, tasks) { onOpenSchedule(item) }
                            }
                        }
                    }
                    HomeSummaryKind.UNDONE_TASKS -> {
                        if (undoneTasks.isEmpty()) {
                            item { EmptyCard("미완료 업무가 없습니다.") }
                        } else {
                            items(undoneTasks, key = { "undone_${it.id}" }) { task ->
                                val schedule = schedules.firstOrNull { it.id == task.scheduleId }
                                if (schedule != null) {
                                    Card(
                                        Modifier.fillMaxWidth().clickable { onOpenSchedule(schedule) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                schedule.title,
                                                color = TravelerBlue,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val due = task.dueDate.takeIf { it.isNotBlank() } ?: "기한 없음"
                                            Text("기한 $due · ${schedule.location.ifBlank { "지역 미입력" }}", color = TravelerGray, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "항목을 누르면 해당 여행 일정의 상세 화면으로 이동합니다.",
                        color = TravelerGray,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun DashboardDialog(
    schedules: List<ScheduleEntity>,
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onOpenSchedule: (ScheduleEntity) -> Unit
) {
    val today = LocalDate.now()
    val weekEnd = today.plusDays(7)
    val overdue = tasks.filter { !it.completed && parseDate(it.dueDate)?.isBefore(today) == true }
    val dueSoon = tasks.filter {
        val d = parseDate(it.dueDate)
        !it.completed && d != null && !d.isBefore(today) && !d.isAfter(weekEnd)
    }
    val activeTrips = schedules.filter { it.status in listOf("예약", "확정", "진행중") }

    fun grouped(input: List<TaskEntity>) =
        input.groupBy { it.scheduleId }.entries.sortedBy { entry ->
            schedules.firstOrNull { it.id == entry.key }?.startDate ?: "9999-12-31"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("업무 대시보드", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 560.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniMetric("진행 행사", activeTrips.size.toString(), Modifier.weight(1f))
                        MiniMetric("7일 이내", dueSoon.size.toString(), Modifier.weight(1f))
                        MiniMetric("기한 초과", overdue.size.toString(), Modifier.weight(1f))
                    }
                }

                if (overdue.isNotEmpty()) {
                    item { DashboardPeriodHeader("기한 초과 업무", overdue.size, TravelerRed) }
                    items(grouped(overdue), key = { "overdue_group_${it.key}" }) { entry ->
                        val schedule = schedules.firstOrNull { it.id == entry.key }
                        DashboardScheduleGroupCard(schedule, entry.value) {
                            schedule?.let(onOpenSchedule)
                        }
                    }
                }

                if (dueSoon.isNotEmpty()) {
                    item { DashboardPeriodHeader("7일 이내 업무", dueSoon.size, Color(0xFFF57C00)) }
                    items(grouped(dueSoon), key = { "soon_group_${it.key}" }) { entry ->
                        val schedule = schedules.firstOrNull { it.id == entry.key }
                        DashboardScheduleGroupCard(schedule, entry.value) {
                            schedule?.let(onOpenSchedule)
                        }
                    }
                }

                if (overdue.isEmpty() && dueSoon.isEmpty()) {
                    item { EmptyCard("급한 업무가 없습니다.") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun DashboardPeriodHeader(title: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
        Text("${count}건", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun DashboardScheduleGroupCard(
    schedule: ScheduleEntity?,
    tasks: List<TaskEntity>,
    onClick: () -> Unit
) {
    Card(
        modifier = if (schedule != null) Modifier.fillMaxWidth().polishedPress(onClick) else Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = TravelerRed.copy(alpha = .10f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.EventNote, null, tint = TravelerRed, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(schedule?.title ?: "연결 일정 없음", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val meta = schedule?.let {
                        listOf(it.groupName, it.location, it.startDate)
                            .filter(String::isNotBlank).joinToString(" · ")
                    }.orEmpty()
                    if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                if (schedule != null) Icon(Icons.Default.ChevronRight, null, tint = TravelerGray)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            tasks.sortedBy { parseDate(it.dueDate) ?: LocalDate.MAX }.take(4).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircleOutline, null, tint = TravelerBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(task.title, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (task.dueDate.isNotBlank()) Text(task.dueDate, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
            }
            if (tasks.size > 4) {
                Text("+${tasks.size - 4}개 업무 더 보기", color = TravelerBlue, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color(0xFFF4F6F8), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = TravelerGray)
        }
    }
}

@Composable
private fun DashboardTaskRow(task: TaskEntity, schedules: List<ScheduleEntity>) {
    val trip = schedules.firstOrNull { it.id == task.scheduleId }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(listOfNotNull(trip?.title, task.dueDate.takeIf { it.isNotBlank() }).joinToString(" · "), color = TravelerGray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TodayTaskGroupCard(
    schedule: ScheduleEntity?,
    dueTasks: List<TaskEntity>,
    allScheduleTasks: List<TaskEntity>,
    onClick: () -> Unit
) {
    val done = allScheduleTasks.count { it.completed }
    val total = allScheduleTasks.size
    val progress = if (total > 0) done.toFloat() / total else 0f
    Card(
        modifier = Modifier.fillMaxWidth().polishedPress(onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(38.dp).background(TravelerRed, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            schedule?.title ?: "연결 일정 없음",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        schedule?.let { trip ->
                            departureDday(trip)?.let { label ->
                                Text(label, color = ddayColor(trip), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val identity = schedule?.let {
                        listOf(it.groupName, it.location, "${it.startDate} ~ ${it.endDate}")
                            .filter { value -> value.isNotBlank() }
                            .joinToString(" · ")
                    }.orEmpty()
                    if (identity.isNotBlank()) Text(identity, color = TravelerGray, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)),
                        color = if (progress >= 1f) Color(0xFF2E7D32) else TravelerRed,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$done/$total · ${(progress * 100).toInt()}%", color = TravelerGray, fontSize = 10.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            dueTasks.take(3).forEach { task ->
                val due = parseDate(task.dueDate)
                val overdue = due?.isBefore(LocalDate.now()) == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircleOutline, null, tint = if (overdue) TravelerRed else TravelerBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(task.title, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (overdue) Text("기한 지남", color = TravelerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (dueTasks.size > 3) {
                Text(
                    "미완료 업무 ${dueTasks.size - 3}건 더 보기",
                    color = TravelerBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun TodayTaskRow(task: TaskEntity, schedules: List<ScheduleEntity>, onClick: () -> Unit) {
    val trip = schedules.firstOrNull { it.id == task.scheduleId }
    val due = parseDate(task.dueDate)
    val overdue = due?.isBefore(LocalDate.now()) == true
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (overdue) TravelerRed.copy(alpha = .08f) else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircleOutline, null, tint = if (overdue) TravelerRed else TravelerBlue)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    listOfNotNull(trip?.title, task.dueDate.takeIf { it.isNotBlank() }).joinToString(" · "),
                    color = TravelerGray, fontSize = 11.sp
                )
            }
            if (overdue) Text("기한 지남", color = TravelerRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, sub: String = "", action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(TravelerRed, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(sub, color = TravelerGray, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = TravelerGray, fontSize = 12.sp) }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = TravelerBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = TravelerGray, fontSize = 11.sp)
            val numericTarget = value.filter(Char::isDigit).toIntOrNull()
            if (numericTarget != null) {
                val animatedValue by animateIntAsState(
                    targetValue = numericTarget,
                    animationSpec = tween(550, easing = FastOutSlowInEasing),
                    label = "summary_counter"
                )
                Text("${animatedValue}건", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(3.dp))
            Text("눌러서 보기", color = TravelerBlue, fontSize = 9.sp)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = TravelerGray)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(vertical = 24.dp, horizontal = 18.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = TravelerBlue.copy(alpha = .08f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Luggage, null, tint = TravelerBlue.copy(alpha = .75f))
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScheduleRow(item: ScheduleEntity, tasks: List<TaskEntity> = emptyList(), onClick: (ScheduleEntity) -> Unit) {
    val scheduleTasks = tasks.filter { it.scheduleId == item.id }
    val done = scheduleTasks.count { it.completed }
    val progress = if (scheduleTasks.isNotEmpty()) done.toFloat() / scheduleTasks.size else 0f
    val urgentDdayPulse = remember(item.id) { Animatable(1f) }
    LaunchedEffect(item.id, item.startDate) {
        val start = parseDate(item.startDate)
        val days = start?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
        if (days != null && days in 0..3) {
            urgentDdayPulse.animateTo(1.08f, tween(160))
            urgentDdayPulse.animateTo(1f, tween(220))
        }
    }
    Card(
        Modifier.fillMaxWidth().polishedPress { onClick(item) },
        colors = CardDefaults.cardColors(
            containerColor = if (item.favorite) {
                scheduleVisualColor(item.colorKey).copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface)
            } else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        border = if (item.favorite) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD98A)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val visualColor = scheduleVisualColor(item.colorKey)
            Box(
                Modifier.size(46.dp).background(visualColor.copy(alpha = .16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(scheduleVisualIcon(item.iconKey), null, tint = visualColor, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.favorite) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF4B400), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    departureDday(item)?.let { label ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                                label,
                                color = ddayColor(item),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = urgentDdayPulse.value
                                    scaleY = urgentDdayPulse.value
                                }
                            )
                    }
                }
                val meta = listOf(item.location, item.groupName, if (item.people > 0) "${item.people}명" else "", item.manager)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = TravelerGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.startDate} ~ ${item.endDate}", color = TravelerGray, fontSize = 11.sp)
                if (scheduleTasks.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                        color = TravelerRed,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("체크리스트 $done / ${scheduleTasks.size} 완료", color = TravelerGray, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(item.status)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = statusColor(status)
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(20.dp)) {
        Text(status, color = color, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

private fun statusColor(status: String): Color = when (status) {
    "견적" -> Color(0xFF7A5AF8)
    "예약" -> Color(0xFF2C82C9)
    "확정" -> Color(0xFF0E9F6E)
    "진행중" -> Color(0xFFF59E0B)
    "완료" -> Color(0xFF6B7280)
    "취소" -> Color(0xFFC43130)
    else -> TravelerBlue
}

@Composable
private fun CalendarPage(
    month: YearMonth,
    selectedDate: LocalDate,
    schedules: List<ScheduleEntity>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onToday: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onSchedule: (ScheduleEntity) -> Unit,
    onFeedback: (FeedbackKind) -> Unit
) {
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    var showMonthYearPicker by remember { mutableStateOf(false) }

    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            currentMonth = month,
            onDismiss = { showMonthYearPicker = false },
            onSelect = {
                showMonthYearPicker = false
                onMonthSelected(it)
            }
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = mode == CalendarMode.MONTH) { Icon(Icons.Default.ChevronLeft, "이전") }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = mode == CalendarMode.MONTH) {
                        onFeedback(FeedbackKind.NAVIGATION)
                        showMonthYearPicker = true
                    }
                    .padding(vertical = 9.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onNext, enabled = mode == CalendarMode.MONTH) { Icon(Icons.Default.ChevronRight, "다음") }
            TextButton(onClick = onToday) { Text("오늘") }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModeButton("월간", mode == CalendarMode.MONTH, Modifier.weight(1f)) { onFeedback(FeedbackKind.NAVIGATION); mode = CalendarMode.MONTH }
            ModeButton("주간", mode == CalendarMode.WEEK, Modifier.weight(1f)) { onFeedback(FeedbackKind.NAVIGATION); mode = CalendarMode.WEEK }
            ModeButton("목록", mode == CalendarMode.LIST, Modifier.weight(1f)) { onFeedback(FeedbackKind.NAVIGATION); mode = CalendarMode.LIST }
        }
        when (mode) {
            CalendarMode.MONTH -> MonthCalendar(month, selectedDate, schedules, onDate, onSchedule)
            CalendarMode.WEEK -> WeekCalendar(selectedDate, schedules, onDate, onSchedule)
            CalendarMode.LIST -> CalendarList(schedules, onSchedule)
        }
    }
}

@Composable
private fun MonthYearPickerDialog(
    currentMonth: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit
) {
    var selectedYear by remember(currentMonth) { mutableStateOf(currentMonth.year) }
    var selectedMonthValue by remember(currentMonth) { mutableStateOf(currentMonth.monthValue) }
    val monthNames = listOf("1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("년·월 선택", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedYear -= 1 }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "이전 연도")
                    }
                    Text("${selectedYear}년", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { selectedYear += 1 }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "다음 연도")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    monthNames.chunked(3).forEachIndexed { rowIndex, rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEachIndexed { colIndex, label ->
                                val monthValue = rowIndex * 3 + colIndex + 1
                                val selected = selectedMonthValue == monthValue
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedMonthValue = monthValue },
                                    color = if (selected) TravelerRed else Color(0xFFF2F3F5),
                                    shape = RoundedCornerShape(10.dp),
                                    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, LightBorder)
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) Color.White else TravelerBlack,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        val now = YearMonth.now()
                        selectedYear = now.year
                        selectedMonthValue = now.monthValue
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("이번 달로 이동")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(YearMonth.of(selectedYear, selectedMonthValue)) },
                colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)
            ) {
                Text("이동")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) TravelerRed else Color(0xFFF2F3F5)
    val fg = if (selected) Color.White else TravelerGray
    Surface(modifier.clickable(onClick = onClick), color = bg, shape = RoundedCornerShape(9.dp)) {
        Text(label, color = fg, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    schedules: List<ScheduleEntity>,
    onDate: (LocalDate) -> Unit,
    onSchedule: (ScheduleEntity) -> Unit
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val selectedItems = schedules.filter { coversDate(it, selectedDate) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { idx, name ->
                            Text(name, color = if (idx == 0) TravelerRed else if (idx == 6) TravelerBlue else TravelerGray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(4.dp))
                        }
                    }
                    repeat(6) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { dow ->
                                val index = week * 7 + dow
                                val dayNum = index - offset + 1
                                if (dayNum in 1..days) {
                                    val date = month.atDay(dayNum)
                                    val dayItems = schedules.filter { coversDate(it, date) }
                                    DayCell(date, selectedDate, dayItems, Modifier.weight(1f)) { onDate(date) }
                                } else {
                                    Spacer(Modifier.weight(1f).height(82.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle(selectedDate.format(KoreanDayFormatter), "${selectedItems.size}건") }
        if (selectedItems.isEmpty()) item { EmptyCard("선택한 날짜의 일정이 없습니다.") }
        else items(selectedItems, key = { it.id }) { ScheduleRow(it, onClick = onSchedule) }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selectedDate: LocalDate,
    items: List<ScheduleEntity>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()
    val selected = date == selectedDate
    Column(
        modifier.height(86.dp).padding(1.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) TravelerRed.copy(alpha = .08f) else Color.Transparent)
            .border(if (isToday) 1.dp else 0.dp, if (isToday) TravelerRed else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) TravelerRed else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        items.take(2).forEach { item ->
            val startDate = parseDate(item.startDate)
            val endDate = parseDate(item.endDate)
            val starts = date == startDate
            val ends = date == endDate
            val shape = RoundedCornerShape(
                topStart = if (starts) 5.dp else 1.dp,
                bottomStart = if (starts) 5.dp else 1.dp,
                topEnd = if (ends) 5.dp else 1.dp,
                bottomEnd = if (ends) 5.dp else 1.dp
            )
            Surface(
                color = statusColor(item.status).copy(alpha = .16f),
                shape = shape,
                modifier = Modifier.fillMaxWidth().height(18.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(4.dp).background(statusColor(item.status), CircleShape))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = item.location.ifBlank { item.title },
                        fontSize = 7.5.sp,
                        lineHeight = 8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        if (items.size > 2) Text("+${items.size - 2}", fontSize = 8.sp, color = TravelerGray)
    }
}

@Composable
private fun WeekCalendar(selectedDate: LocalDate, schedules: List<ScheduleEntity>, onDate: (LocalDate) -> Unit, onSchedule: (ScheduleEntity) -> Unit) {
    val start = selectedDate.minusDays((selectedDate.dayOfWeek.value % 7).toLong())
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val selectedItems = schedules.filter { coversDate(it, selectedDate) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEach { date ->
                        val active = date == selectedDate
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (active) TravelerRed else Color(0xFFF5F6F7))
                                .clickable { onDate(date) }.padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.KOREAN), fontSize = 10.sp, color = if (active) Color.White else TravelerGray)
                            Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = if (active) Color.White else TravelerBlack)
                            val count = schedules.count { coversDate(it, date) }
                            if (count > 0) Text("$count", fontSize = 9.sp, color = if (active) Color.White else TravelerRed)
                        }
                    }
                }
            }
        }
        item { SectionTitle(selectedDate.format(KoreanDayFormatter), "${selectedItems.size}건") }
        if (selectedItems.isEmpty()) item { EmptyCard("선택한 날짜의 일정이 없습니다.") }
        else items(selectedItems, key = { it.id }) { ScheduleRow(it, onClick = onSchedule) }
    }
}

@Composable
private fun CalendarList(schedules: List<ScheduleEntity>, onSchedule: (ScheduleEntity) -> Unit) {
    val sorted = schedules.sortedBy { it.startDate }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sorted.isEmpty()) item { EmptyCard("등록된 일정이 없습니다.") }
        else items(sorted, key = { it.id }) { ScheduleRow(it, onClick = onSchedule) }
    }
}

@Composable
private fun ScheduleListPage(
    schedules: List<ScheduleEntity>,
    tasks: List<TaskEntity>,
    trashCount: Int,
    onSchedule: (ScheduleEntity) -> Unit,
    onTrash: () -> Unit
) {
    var statusFilter by remember { mutableStateOf("전체") }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filtered = schedules.filter { item ->
        val statusOk = statusFilter == "전체" || item.status == statusFilter
        val queryOk = q.isBlank() || listOf(
            item.title, item.groupName, item.location, item.manager, item.flight, item.hotel,
            item.guide, item.vehicle, item.landCompany, item.customerPhone, item.status, item.memo
        ).any { it.lowercase().contains(q) }
        statusOk && queryOk
    }.let(::sortSchedulesForWork)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("일정 검색") },
            placeholder = { Text("행사명·단체명·국가·항공·호텔·담당자") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "검색어 지우기") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            singleLine = true
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("전체") + Statuses).forEach { status ->
                FilterChip(selected = statusFilter == status, onClick = { statusFilter = status }, label = { Text(status) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onTrash) {
                Icon(Icons.Default.DeleteSweep, null, tint = TravelerGray, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (trashCount > 0) "휴지통 $trashCount" else "휴지통",
                    color = TravelerGray,
                    fontSize = 12.sp
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item { EmptyCard(if (q.isBlank()) "해당 상태의 일정이 없습니다." else "검색 결과가 없습니다.") }
            else items(filtered, key = { it.id }) { ScheduleRow(it, tasks, onClick = onSchedule) }
        }
    }
}

@Composable
private fun ContactsPage() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<PhoneContact?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loading = true
            contacts = runCatching { loadPhoneContacts(context) }.getOrDefault(emptyList())
            loading = false
        }
    }

    val filtered = remember(query, contacts) {
        contacts
            .filter { contactMatchesQuery(it, query) }
            .sortedWith(compareBy<PhoneContact> { it.name }.thenBy { it.phone })
    }

    Column(Modifier.fillMaxSize()) {
        if (!hasPermission) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(24.dp)) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Contacts, null, tint = TravelerRed, modifier = Modifier.size(42.dp))
                        Text("휴대폰 주소록 연결", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("연락처를 앱에서 검색하고 전화·문자로 연결하려면 연락처 권한이 필요합니다.", color = TravelerGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }, colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)) {
                            Text("연락처 권한 허용")
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("주소록 검색") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true
            )
            if (loading) {
                ContactSkeletonList()
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (filtered.isEmpty()) item { EmptyCard("표시할 연락처가 없습니다.") }
                    else items(filtered, key = { "contact_${it.id}_${it.phone}" }) { contact ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedContact = contact },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Color(0xFFFFECEB), modifier = Modifier.size(42.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = TravelerRed) }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(contact.name, fontWeight = FontWeight.Bold)
                                    Text(contact.phone, color = TravelerGray, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = TravelerGray)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedContact?.let { contact ->
        ContactActionDialog(contact = contact, onDismiss = { selectedContact = null })
    }
}

@Composable
private fun ContactSkeletonList() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(7) { index ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.fillMaxWidth(if (index % 2 == 0) .42f else .58f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(Modifier.fillMaxWidth(.62f).height(9.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactActionDialog(contact: PhoneContact, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contact.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contact.phone, color = TravelerGray)
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(contact.phone)}")))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text("전화") }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.phone)}")))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("문자 메시지") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun SettingsPage(
    accountEmail: String,
    syncStatus: String,
    lastSyncText: String,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    soundIntensity: String,
    reminderEnabled: Map<Int, Boolean>,
    reminderHour: Int,
    reminderMinute: Int,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    appLockEnabled: Boolean,
    biometricLockEnabled: Boolean,
    biometricAvailable: Boolean,
    lockTimeoutMinutes: Int,
    trashCount: Int,
    onOpenTrash: () -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onSoundIntensityChanged: (String) -> Unit,
    onReminderChanged: (Int, Boolean) -> Unit,
    onReminderTimeChanged: (Int, Int) -> Unit,
    onTestReminder: () -> Unit,
    onSyncNow: () -> Unit,
    onAppLockChanged: (Boolean) -> Unit,
    onBiometricLockChanged: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onLockTimeoutChanged: (Int) -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("설정") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = TravelerRed, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("계정", fontWeight = FontWeight.Bold)
                            Text(accountEmail.ifBlank { "로그인 계정" }, color = TravelerGray, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, null, tint = TravelerBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(syncStatus, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            if (lastSyncText.isNotBlank()) {
                                Text("마지막 동기화 $lastSyncText", color = TravelerGray, fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(onClick = onSyncNow) { Text("지금 동기화") }
                    }
                    TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("로그아웃", color = TravelerRed)
                    }
                }
            }
        }
        item {
            ThemeModeCard(
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged
            )
        }
        item {
            ToggleSettingRow(
                icon = Icons.Default.VolumeUp,
                title = "효과음",
                sub = "화면 이동·저장·완료 등 주요 동작에 짧고 조용한 효과음",
                checked = soundEnabled,
                onCheckedChange = onSoundChanged
            )
        }
        item {
            SoundIntensityCard(
                value = soundIntensity,
                enabled = soundEnabled,
                onChanged = onSoundIntensityChanged
            )
        }
        item {
            ToggleSettingRow(
                icon = Icons.Default.Vibration,
                title = "진동",
                sub = "저장·체크 완료·삭제 시 짧은 햅틱 피드백",
                checked = vibrationEnabled,
                onCheckedChange = onVibrationChanged
            )
        }
        item {
            ReminderSettingsCard(
                reminderEnabled = reminderEnabled,
                reminderHour = reminderHour,
                reminderMinute = reminderMinute,
                onReminderChanged = onReminderChanged,
                onReminderTimeChanged = onReminderTimeChanged,
                onTestReminder = onTestReminder
            )
        }
        item {
            ToggleSettingRow(
                icon = Icons.Default.Lock,
                title = "앱 잠금",
                sub = "앱 실행 시 4자리 PIN으로 보호합니다.",
                checked = appLockEnabled,
                onCheckedChange = onAppLockChanged
            )
        }
        if (appLockEnabled) {
            item {
                ToggleSettingRow(
                    icon = Icons.Default.Fingerprint,
                    title = "지문·얼굴 인식",
                    sub = if (biometricAvailable) "지원되는 생체인증으로 빠르게 잠금을 해제합니다." else "이 기기에서 사용 가능한 생체인증을 찾지 못했습니다.",
                    checked = biometricLockEnabled && biometricAvailable,
                    onCheckedChange = { if (biometricAvailable) onBiometricLockChanged(it) }
                )
            }
            item {
                AppLockTimeoutCard(
                    selectedMinutes = lockTimeoutMinutes,
                    onSelected = onLockTimeoutChanged
                )
            }
            item {
                OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Password, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("잠금 PIN 변경")
                }
            }
        }
        item {
            DataManagementCard(
                trashCount = trashCount,
                onOpenTrash = onOpenTrash
            )
        }
        item { SettingRow(Icons.Default.Storage, "저장 방식", "Room 로컬 저장 + Firebase 클라우드 동기화") }
        item { SettingRow(Icons.Default.Info, "앱 정보", "TRAVELER Schedule v4.6.2") }
    }
}

@Composable
private fun SoundIntensityCard(
    value: String,
    enabled: Boolean,
    onChanged: (String) -> Unit
) {
    val options = listOf(
        "minimal" to "최소",
        "normal" to "기본",
        "emphasized" to "강조"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = TravelerBlue)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("효과음 강도", fontWeight = FontWeight.Bold)
                    Text("조작음의 크기와 존재감을 조절합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                options.forEach { (key, label) ->
                    FilterChip(
                        selected = value == key,
                        onClick = { if (enabled) onChanged(key) },
                        enabled = enabled,
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DataManagementCard(
    trashCount: Int,
    onOpenTrash: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = TravelerBlue.copy(alpha = .10f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Storage, null, tint = TravelerBlue, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("데이터 관리", fontWeight = FontWeight.Bold)
                    Text("삭제한 일정은 30일 동안 안전하게 보관됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenTrash).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = TravelerRed.copy(alpha = .08f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteSweep, null, tint = TravelerRed, modifier = Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("휴지통", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (trashCount > 0) "삭제된 일정 ${trashCount}건 · 복원 또는 완전 삭제 가능" else "삭제된 일정이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                if (trashCount > 0) {
                    Surface(color = TravelerRed, shape = CircleShape) {
                        Text(
                            trashCount.coerceAtMost(99).toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(Icons.Default.ChevronRight, null, tint = TravelerGray)
            }
        }
    }
}

@Composable
private fun TrashDialog(
    items: List<ScheduleEntity>,
    onDismiss: () -> Unit,
    onRestore: (ScheduleEntity) -> Unit,
    onPermanentDelete: (ScheduleEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var permanentTarget by remember { mutableStateOf<ScheduleEntity?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                color = TravelerRed.copy(alpha = .09f),
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DeleteSweep, null, tint = TravelerRed)
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("휴지통", fontWeight = FontWeight.Bold)
                Text(
                    if (items.isEmpty()) "삭제된 일정이 없습니다." else "삭제된 일정 ${items.size}건",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Recycling, null, tint = TravelerBlue, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("휴지통이 비어 있습니다.", fontWeight = FontWeight.SemiBold)
                                Text("삭제한 일정은 이곳에서 30일간 복원할 수 있습니다.", color = TravelerGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(items, key = { "trash_${it.id}" }) { item ->
                        val deletedDate = Instant.ofEpochMilli(item.deletedAt)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val elapsed = ChronoUnit.DAYS.between(deletedDate, LocalDate.now()).coerceAtLeast(0)
                        val remaining = (30 - elapsed).coerceAtLeast(0)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = scheduleVisualColor(item.colorKey).copy(alpha = .12f),
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(scheduleVisualIcon(item.iconKey), null, tint = scheduleVisualColor(item.colorKey), modifier = Modifier.size(21.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val meta = listOf(item.groupName, item.location).filter(String::isNotBlank).joinToString(" · ")
                                        if (meta.isNotBlank()) Text(meta, color = TravelerGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Text(
                                    "삭제 ${deletedDate} · 자동 삭제까지 ${remaining}일",
                                    color = if (remaining <= 3) TravelerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { onRestore(item) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.RestoreFromTrash, null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("복원")
                                    }
                                    TextButton(
                                        onClick = { permanentTarget = item },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.DeleteForever, null, tint = TravelerRed, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("완전 삭제", color = TravelerRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = {
            if (items.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) {
                    Icon(Icons.Default.DeleteSweep, null, tint = TravelerRed, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("전체 비우기", color = TravelerRed)
                }
            }
        }
    )

    permanentTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { permanentTarget = null },
            title = { Text("완전히 삭제할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("이 작업은 되돌릴 수 없습니다. 연결된 체크리스트도 함께 삭제됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDelete(target)
                        permanentTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)
                ) { Text("완전 삭제") }
            },
            dismissButton = { TextButton(onClick = { permanentTarget = null }) { Text("취소") } }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("휴지통을 비울까요?", fontWeight = FontWeight.Bold) },
            text = { Text("휴지통의 모든 일정과 연결된 체크리스트가 완전히 삭제되며 복구할 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        confirmEmpty = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)
                ) { Text("전체 삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun ThemeModeCard(
    themeMode: String,
    onThemeModeChanged: (String) -> Unit
) {
    val options = listOf(
        "light" to "라이트",
        "dark" to "다크",
        "system" to "시스템 설정"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = TravelerRed)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("화면 테마", fontWeight = FontWeight.Bold)
                    Text("라이트·다크·시스템 설정 중 선택", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            options.forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { onThemeModeChanged(value) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = themeMode == value, onClick = { onThemeModeChanged(value) })
                    Text(label, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AppLockTimeoutCard(
    selectedMinutes: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(0 to "즉시", 1 to "1분 후", 5 to "5분 후", 15 to "15분 후")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("백그라운드 재잠금", fontWeight = FontWeight.Bold)
            Text(
                "다른 앱을 사용한 뒤 돌아왔을 때 다시 잠그는 시간을 선택합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            options.forEach { (minutes, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { onSelected(minutes) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedMinutes == minutes, onClick = { onSelected(minutes) })
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun ReminderSettingsCard(
    reminderEnabled: Map<Int, Boolean>,
    reminderHour: Int,
    reminderMinute: Int,
    onReminderChanged: (Int, Boolean) -> Unit,
    onReminderTimeChanged: (Int, Int) -> Unit,
    onTestReminder: () -> Unit
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, tint = TravelerRed)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("업무 알림 설정", fontWeight = FontWeight.Bold)
                    Text("중요한 3개 업무만 알림을 받습니다.", color = TravelerGray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("항공 발권", "호텔 확정", "APIS 입력").forEach { title ->
                    AssistChip(
                        onClick = {},
                        label = { Text(title, fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.NotificationsActive,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = TravelerRed
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "출발 D-Day, 차량 예약, 가이드 확정, 보험, 공문, 명찰, 현수막, 인보이스, 잔금, 최종 명단 등은 알림을 보내지 않습니다.",
                color = TravelerGray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("알림 시간", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(String.format(Locale.KOREA, "%02d:%02d", reminderHour, reminderMinute), color = TravelerBlue, fontSize = 13.sp)
                }
                OutlinedButton(onClick = {
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute -> onReminderTimeChanged(hour, minute) },
                        reminderHour,
                        reminderMinute,
                        true
                    ).show()
                }) { Text("시간 변경") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onTestReminder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("알림 테스트 (3초 후)")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "※ 항공 발권·호텔 확정·APIS 입력 3개 업무만 위 시간에 알림됩니다. 오늘 시간이 이미 지난 경우에는 약 5초 뒤 알림됩니다.",
                color = TravelerGray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ToggleSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TravelerRed)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(sub, color = TravelerGray, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TravelerRed)
            Spacer(Modifier.width(14.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(sub, color = TravelerGray, fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DetailPage(
    item: ScheduleEntity,
    tasksFlow: Flow<List<TaskEntity>>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onAddTask: (String, String) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onDeleteSchedule: () -> Unit,
    onQuickAdd: () -> Unit,
    onFeedback: (FeedbackKind) -> Unit
) {
    val tasks by tasksFlow.collectAsState(initial = emptyList())
    var newTask by remember { mutableStateOf("") }
    var newDue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedTaskIds = remember { mutableStateListOf<Long>() }
    var detailEntered by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { detailEntered = true }
    val detailAlpha by animateFloatAsState(
        targetValue = if (detailEntered) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "detail_alpha"
    )
    val detailOffset by animateFloatAsState(
        targetValue = if (detailEntered) 0f else 28f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "detail_offset"
    )
    Scaffold(
        modifier = Modifier.graphicsLayer {
            alpha = detailAlpha
            translationX = detailOffset
        },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("일정 상세", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (item.favorite) "중요 일정 해제" else "중요 일정",
                            tint = if (item.favorite) Color(0xFFF4B400) else TravelerGray
                        )
                    }
                    IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "일정 복제", tint = TravelerGray) }
                    TextButton(onClick = onEdit) { Text("수정", color = TravelerRed, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onQuickAdd, containerColor = TravelerRed, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "새 일정")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    Image(painterResource(R.drawable.traveler_world), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        StatusChip(item.status)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            departureDday(item)?.let { label ->
                                Surface(color = TravelerRed.copy(alpha = .92f), shape = RoundedCornerShape(20.dp)) {
                                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                }
                            }
                        }
                        Text("${item.startDate} ~ ${item.endDate}", color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                        val heroMeta = listOf(
                            item.location,
                            if (item.people > 0) "${item.people}명" else "",
                            item.manager
                        ).filter(String::isNotBlank).joinToString(" · ")
                        if (heroMeta.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(heroMeta, color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Card(Modifier.padding(12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailSectionHeader(Icons.Default.Info, "기본 정보")
                        InfoRow(Icons.Default.Groups, "단체명", item.groupName)
                        InfoRow(Icons.Default.Place, "국가·지역", item.location)
                        InfoRow(Icons.Default.Person, "담당자", item.manager)
                        InfoRow(Icons.Default.Groups, "인원", if (item.people > 0) "${item.people}명" else "")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        DetailSectionHeader(Icons.Default.FlightTakeoff, "교통 · 숙박")
                        InfoRow(Icons.Default.Flight, "항공편", item.flight)
                        InfoRow(Icons.Default.Hotel, "호텔", item.hotel)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        DetailSectionHeader(Icons.Default.Public, "현지 운영")
                        InfoRow(Icons.Default.Person, "현지가이드", item.guide)
                        InfoRow(Icons.Default.DirectionsBus, "차량", item.vehicle)
                        InfoRow(Icons.Default.Business, "랜드사", item.landCompany)
                        QuickContactActions("가이드", item.guide)
                        QuickContactActions("랜드사", item.landCompany)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        DetailSectionHeader(Icons.Default.ContactPhone, "고객 · 메모")
                        InfoRow(Icons.Default.Phone, "고객 연락처", item.customerPhone)
                        QuickContactActions("고객", item.customerPhone)
                        InfoRow(Icons.Default.Description, "메모", item.memo)
                    }
                }
            }
            item {
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("주요 업무 기한", fontWeight = FontWeight.Bold)
                        DeadlineRow("항공권 발권", item.ticketDeadline)
                        DeadlineRow("호텔 취소", item.hotelCancelDeadline)
                        DeadlineRow("잔금 지급", item.balanceDueDate)
                        DeadlineRow("여권 확인", item.passportCheckDate)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("업무 체크리스트", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        val done = tasks.count { it.completed }
                        ProgressRing(done, tasks.size)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { selectionMode = !selectionMode; if (!selectionMode) selectedTaskIds.clear() }) {
                            Text(if (selectionMode) "선택 취소" else "선택", fontSize = 11.sp)
                        }
                    }
                    if (tasks.isNotEmpty()) {
                        val done = tasks.count { it.completed }
                        val progress = done.toFloat() / tasks.size
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(5.dp)),
                            color = TravelerRed,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("진행률 ${(progress * 100).toInt()}%", color = TravelerGray, fontSize = 11.sp)
                    }
                }
            }
            if (selectionMode) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (selectedTaskIds.size == tasks.size) selectedTaskIds.clear() else {
                                selectedTaskIds.clear(); selectedTaskIds.addAll(tasks.map { it.id })
                            }
                        }, modifier = Modifier.weight(1f)) { Text(if (selectedTaskIds.size == tasks.size) "전체 해제" else "전체 선택") }
                        Button(onClick = {
                            tasks.filter { it.id in selectedTaskIds && !it.completed }.forEach(onToggleTask)
                            selectedTaskIds.clear(); selectionMode = false
                        }, enabled = selectedTaskIds.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)) { Text("완료") }
                        OutlinedButton(onClick = {
                            tasks.filter { it.id in selectedTaskIds && it.completed }.forEach(onToggleTask)
                            selectedTaskIds.clear(); selectionMode = false
                        }, enabled = selectedTaskIds.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("되돌리기") }
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                val chosen = task.id in selectedTaskIds
                val taskPulse = remember(task.id) { Animatable(1f) }
                LaunchedEffect(task.completed) {
                    if (task.completed) {
                        taskPulse.animateTo(1.025f, tween(110))
                        taskPulse.animateTo(1f, tween(170))
                    }
                }
                Card(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = taskPulse.value
                            scaleY = taskPulse.value
                            alpha = if (task.completed) 0.78f else 1f
                        }
                        .combinedClickable(
                        onClick = { if (selectionMode) { if (chosen) selectedTaskIds.remove(task.id) else selectedTaskIds.add(task.id) } },
                        onLongClick = { selectionMode = true; if (!chosen) selectedTaskIds.add(task.id) }
                    ),
                    colors = CardDefaults.cardColors(containerColor = if (chosen) TravelerRed.copy(alpha = .10f) else MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = if (selectionMode) chosen else task.completed,
                            onCheckedChange = {
                                if (selectionMode) { if (chosen) selectedTaskIds.remove(task.id) else selectedTaskIds.add(task.id) }
                                else onToggleTask(task)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = TravelerRed)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.Medium)
                            val sub = listOf(task.category, task.dueDate).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, color = TravelerGray, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onDeleteTask(task) }) { Icon(Icons.Default.DeleteOutline, "삭제", tint = TravelerGray) }
                    }
                }
            }
            item {
                Card(Modifier.padding(12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("업무 직접 추가", fontWeight = FontWeight.Bold)
                        OutlinedTextField(newTask, { newTask = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("업무명") })
                        DatePickerField(value = newDue, label = "업무 기한 (선택)", onDateSelected = { newDue = it }, onSelectedFeedback = { onFeedback(FeedbackKind.DATE) })
                        if (newDue.isNotBlank()) {
                            TextButton(onClick = { newDue = "" }) { Text("기한 지우기", color = TravelerGray) }
                        }
                        Button(
                            onClick = { onAddTask(newTask, newDue); newTask = ""; newDue = "" },
                            enabled = newTask.isNotBlank() && (newDue.isBlank() || validDate(newDue)),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)
                        ) { Text("업무 추가") }
                    }
                }
            }
            item { TextButton(onClick = { onFeedback(FeedbackKind.NAVIGATION); confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("휴지통으로 이동", color = TravelerRed) } }
        }
    }
    if (confirmDelete) {
        var deleteDialogEntered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { deleteDialogEntered = true }
        val deleteDialogScale by animateFloatAsState(
            targetValue = if (deleteDialogEntered) 1f else 0.94f,
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            label = "delete_dialog_scale"
        )
        AlertDialog(
            modifier = Modifier.graphicsLayer {
                scaleX = deleteDialogScale
                scaleY = deleteDialogScale
            },
            onDismissRequest = { confirmDelete = false },
            title = { Text("휴지통으로 이동할까요?") },
            text = { Text("일정과 연결된 체크리스트를 30일 동안 휴지통에 보관합니다. 휴지통에서 언제든 복원할 수 있습니다.") },
            confirmButton = { TextButton(onClick = onDeleteSchedule) { Text("휴지통으로 이동", color = TravelerRed) } },
            dismissButton = { TextButton(onClick = { onFeedback(FeedbackKind.NAVIGATION); confirmDelete = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun QuickContactActions(label: String, raw: String) {
    val context = LocalContext.current
    val phone = raw.filter { it.isDigit() || it == '+' }
    if (phone.filter(Char::isDigit).length < 7) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label 바로 연락", color = TravelerGray, fontSize = 11.sp, modifier = Modifier.width(92.dp))
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) {
            Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("전화")
        }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))) }) {
            Icon(Icons.Default.Sms, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("문자")
        }
    }
}

@Composable
private fun DetailSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = TravelerRed.copy(alpha = .08f),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = TravelerRed, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int) {
    val progress = if (total > 0) done.toFloat() / total else 0f
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = if (progress >= 1f) Color(0xFF2E7D32) else TravelerRed,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 5.dp
        )
        Text("${(progress * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = TravelerGray, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TravelerGray, modifier = Modifier.width(82.dp), fontSize = 13.sp)
        Text(value, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeadlineRow(label: String, date: String) {
    if (date.isBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TravelerGray, fontSize = 12.sp, modifier = Modifier.width(100.dp))
        Text(date, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorDialog(
    initial: ScheduleEntity?,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntity) -> Unit,
    onFeedback: (FeedbackKind) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var group by remember(initial) { mutableStateOf(initial?.groupName ?: "") }
    var location by remember(initial) { mutableStateOf(initial?.location ?: "") }
    var people by remember(initial) { mutableStateOf(initial?.people?.takeIf { it > 0 }?.toString() ?: "") }
    var startDate by remember(initial) { mutableStateOf(initial?.startDate ?: initialDate.toString()) }
    var endDate by remember(initial) { mutableStateOf(initial?.endDate ?: initialDate.toString()) }
    var manager by remember(initial) { mutableStateOf(initial?.manager ?: "") }
    var flight by remember(initial) { mutableStateOf(initial?.flight ?: "") }
    var hotel by remember(initial) { mutableStateOf(initial?.hotel ?: "") }
    var guide by remember(initial) { mutableStateOf(initial?.guide ?: "") }
    var vehicle by remember(initial) { mutableStateOf(initial?.vehicle ?: "") }
    var landCompany by remember(initial) { mutableStateOf(initial?.landCompany ?: "") }
    var customerPhone by remember(initial) { mutableStateOf(initial?.customerPhone ?: "") }
    var memo by remember(initial) { mutableStateOf(initial?.memo ?: "") }
    var status by remember(initial) { mutableStateOf(initial?.status?.takeIf { it in Statuses } ?: "견적") }
    var ticketDeadline by remember(initial) { mutableStateOf(initial?.ticketDeadline ?: "") }
    var hotelCancelDeadline by remember(initial) { mutableStateOf(initial?.hotelCancelDeadline ?: "") }
    var balanceDueDate by remember(initial) { mutableStateOf(initial?.balanceDueDate ?: "") }
    var passportCheckDate by remember(initial) { mutableStateOf(initial?.passportCheckDate ?: "") }
    var iconKey by remember(initial) { mutableStateOf(initial?.iconKey ?: "flight") }
    var colorKey by remember(initial) { mutableStateOf(initial?.colorKey ?: "blue") }
    var showAdvanced by remember(initial) { mutableStateOf(false) }

    val dateFieldsValid = listOf(startDate, endDate).all(::validDate) &&
        listOf(ticketDeadline, hotelCancelDeadline, balanceDueDate, passportCheckDate).all { it.isBlank() || validDate(it) }
    val rangeValid = if (validDate(startDate) && validDate(endDate)) !LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate)) else false
    val requiredFieldsValid = title.isNotBlank() && group.isNotBlank() && location.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "새 여행 일정" else "일정 수정", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 600.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("기본 정보", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("* 필수 입력", color = TravelerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item { Field(title, { title = it }, "행사명 *", required = true) }
                item { Field(group, { group = it }, "단체명 여행사 *", required = true) }
                item { Field(location, { location = it }, "국가·지역 *", required = true) }
                item { Field(people, { people = it.filter(Char::isDigit) }, "인원") }
                item { Field(manager, { manager = it }, "담당자") }
                item { DatePickerField(value = startDate, label = "출발일 *", onDateSelected = { startDate = it }, onSelectedFeedback = { onFeedback(FeedbackKind.DATE) }) }
                item { DatePickerField(value = endDate, label = "귀국일 *", onDateSelected = { endDate = it }, onSelectedFeedback = { onFeedback(FeedbackKind.DATE) }) }
                item {
                    Text("일정 상태", fontSize = 12.sp, color = TravelerGray)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Statuses.forEach { value ->
                            FilterChip(selected = status == value, onClick = { onFeedback(FeedbackKind.NAVIGATION); status = value }, label = { Text(value) })
                        }
                    }
                }
                item {
                    Text("일정 아이콘", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScheduleVisualOptions.chunked(6).forEach { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowItems.forEach { option ->
                                    val selected = iconKey == option.key
                                    Surface(
                                        modifier = Modifier.weight(1f).aspectRatio(1f)
                                            .clickable {
                                                onFeedback(FeedbackKind.NAVIGATION)
                                                iconKey = option.key
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) TravelerRed.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selected) TravelerRed else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                option.icon,
                                                option.label,
                                                tint = if (selected) TravelerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(option.label, fontSize = 8.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("일정 배경색", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ScheduleColorOptions.chunked(6).forEach { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowItems.forEach { option ->
                                    val selected = colorKey == option.first
                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                            .background(option.second)
                                            .border(
                                                if (selected) 3.dp else 1.dp,
                                                if (selected) MaterialTheme.colorScheme.onSurface else option.second.copy(alpha = .35f),
                                                CircleShape
                                            )
                                            .clickable {
                                                onFeedback(FeedbackKind.NAVIGATION)
                                                colorKey = option.first
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onFeedback(FeedbackKind.NAVIGATION); showAdvanced = !showAdvanced },
                        color = Color(0xFFF5F6F7),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("상세 정보 및 주요 기한", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Icon(
                                if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAdvanced) "접기" else "펼치기",
                                tint = TravelerGray
                            )
                        }
                    }
                }
                if (showAdvanced) {
                    item { Field(flight, { flight = it }, "항공편 (예: OZ368 / KE651)") }
                    item { Field(hotel, { hotel = it }, "호텔") }
                    item { Field(guide, { guide = it }, "현지가이드") }
                    item { Field(vehicle, { vehicle = it }, "차량") }
                    item { Field(landCompany, { landCompany = it }, "여행사/랜드사") }
                    item { Field(customerPhone, { customerPhone = it }, "고객 연락처") }
                    item { HorizontalDivider(); Text("주요 기한 알림", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                    item { Field(ticketDeadline, { ticketDeadline = it }, "항공권 발권 시한 YYYY-MM-DD") }
                    item { Field(hotelCancelDeadline, { hotelCancelDeadline = it }, "호텔 취소 가능 시한 YYYY-MM-DD") }
                    item { Field(balanceDueDate, { balanceDueDate = it }, "잔금 지급일 YYYY-MM-DD") }
                    item { Field(passportCheckDate, { passportCheckDate = it }, "여권 확인일 YYYY-MM-DD") }
                }
                item { OutlinedTextField(memo, { memo = it }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                item { Text("※ 알림은 항공 발권·호텔 확정·APIS 입력 3개 핵심 업무만 발송됩니다.", color = TravelerGray, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ScheduleEntity(
                            id = initial?.id ?: 0,
                            title = title.trim(),
                            groupName = group.trim(),
                            location = location.trim(),
                            people = people.toIntOrNull() ?: 0,
                            startDate = startDate.trim(),
                            endDate = endDate.trim(),
                            time = initial?.time ?: "",
                            memo = memo.trim(),
                            status = status,
                            priority = initial?.priority ?: "일반",
                            reminderDays = initial?.reminderDays ?: 1,
                            manager = manager.trim(),
                            flight = flight.trim(),
                            hotel = hotel.trim(),
                            guide = guide.trim(),
                            vehicle = vehicle.trim(),
                            landCompany = landCompany.trim(),
                            customerPhone = customerPhone.trim(),
                            ticketDeadline = ticketDeadline.trim(),
                            hotelCancelDeadline = hotelCancelDeadline.trim(),
                            balanceDueDate = balanceDueDate.trim(),
                            passportCheckDate = passportCheckDate.trim(),
                            favorite = initial?.favorite ?: false,
                            iconKey = iconKey,
                            colorKey = colorKey
                        )
                    )
                },
                enabled = requiredFieldsValid && dateFieldsValid && rangeValid,
                colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun AppLockScreen(
    biometricAvailable: Boolean,
    onPinSubmit: (String) -> Boolean,
    onBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var unlockedVisual by remember { mutableStateOf(false) }
    val lockScale by animateFloatAsState(
        targetValue = if (unlockedVisual) 1.12f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "lock_scale"
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                if (unlockedVisual) Icons.Default.LockOpen else Icons.Default.Lock,
                null,
                tint = if (unlockedVisual) Color(0xFF2E8B57) else TravelerRed,
                modifier = Modifier.size(48.dp).graphicsLayer {
                    scaleX = lockScale
                    scaleY = lockScale
                }
            )
            Spacer(Modifier.height(14.dp))
            Text("TRAVELER 잠금", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("4자리 PIN을 입력하세요.", color = TravelerGray, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value -> pin = value.filter(Char::isDigit).take(4); error = false },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                isError = error,
                modifier = Modifier.widthIn(max = 280.dp)
            )
            if (error) Text("PIN이 올바르지 않습니다.", color = TravelerRed, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (onPinSubmit(pin)) {
                        unlockedVisual = true
                    } else {
                        error = true
                        pin = ""
                    }
                },
                enabled = pin.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = TravelerRed),
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
            ) { Text("잠금 해제") }
            if (biometricAvailable) {
                TextButton(onClick = onBiometric) {
                    Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("지문·얼굴 인식")
                }
            }
        }
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = first.length == 4 && first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱 잠금 PIN 설정", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("앱을 열 때 사용할 4자리 숫자를 설정합니다.", color = TravelerGray, fontSize = 12.sp)
                OutlinedTextField(first, { first = it.filter(Char::isDigit).take(4) }, label = { Text("새 PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                OutlinedTextField(second, { second = it.filter(Char::isDigit).take(4) }, label = { Text("PIN 확인") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, isError = second.length == 4 && first != second)
            }
        },
        confirmButton = { Button(onClick = { onSave(first) }, enabled = valid, colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun DuplicateOptionsDialog(
    source: ScheduleEntity,
    onDismiss: () -> Unit,
    onContinue: (LocalDate, Boolean, Boolean) -> Unit
) {
    var startDate by remember(source.id) { mutableStateOf(source.startDate) }
    var includeTasks by remember(source.id) { mutableStateOf(true) }
    var includeFlightHotel by remember(source.id) { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("지난 행사 복제", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source.title, fontWeight = FontWeight.Bold)
                DatePickerField(value = startDate, label = "새 출발일", onDateSelected = { startDate = it })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(includeTasks, { includeTasks = it }); Text("체크리스트 포함") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(includeFlightHotel, { includeFlightHotel = it }); Text("항공편·호텔 정보 포함") }
                Text("기존 여행 기간을 유지한 채 새 출발일 기준으로 귀국일과 업무 기한을 자동 이동합니다.", color = TravelerGray, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { parseDate(startDate)?.let { onContinue(it, includeTasks, includeFlightHotel) } }, enabled = validDate(startDate), colors = ButtonDefaults.buttonColors(containerColor = TravelerRed)) { Text("복제 후 수정") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    label: String,
    onDateSelected: (String) -> Unit,
    onSelectedFeedback: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    val selectedDate = parseDate(value) ?: LocalDate.now()
    val initialMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = "날짜 선택") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        // TextField가 터치 이벤트를 소비하는 문제를 피하기 위해 투명 클릭 영역을 위에 둡니다.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { open = true }
        )
    }

    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        onDateSelected(date.toString())
                        onSelectedFeedback?.invoke()
                    }
                    open = false
                }) { Text("선택", color = TravelerRed) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("취소") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    required: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = required && value.isBlank(),
        supportingText = {
            if (required && value.isBlank()) {
                Text("필수 입력 항목입니다.", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
            }
        }
    )
}

private fun ddayColor(item: ScheduleEntity): Color {
    val start = parseDate(item.startDate) ?: return TravelerGray
    val days = ChronoUnit.DAYS.between(LocalDate.now(), start)
    return when {
        days <= 0 -> TravelerRed
        days <= 3 -> Color(0xFFD32F2F)
        days <= 7 -> Color(0xFFF57C00)
        days <= 14 -> TravelerBlue
        else -> TravelerGray
    }
}

private fun departureDday(item: ScheduleEntity): String? {
    if (item.status == "취소") return null
    val start = parseDate(item.startDate) ?: return null
    val days = ChronoUnit.DAYS.between(LocalDate.now(), start)
    return when {
        days > 0 -> "D-$days"
        days == 0L -> "D-DAY"
        else -> "D+${-days}"
    }
}

private fun coversDate(item: ScheduleEntity, date: LocalDate): Boolean {
    val s = parseDate(item.startDate) ?: return false
    val e = parseDate(item.endDate) ?: return false
    return !date.isBefore(s) && !date.isAfter(e)
}

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim(), DateFormatter) }.getOrNull()
private fun validDate(value: String): Boolean = parseDate(value) != null

@Composable
private fun TravelerTheme(themeMode: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = themeMode == "dark" || (themeMode == "system" && systemDark)
    val scheme = if (useDark) {
        darkColorScheme(
            primary = Color(0xFFFF5A5F),
            secondary = Color(0xFF62A8FF),
            background = Color(0xFF111315),
            surface = Color(0xFF1A1D21),
            surfaceVariant = Color(0xFF262A30),
            onPrimary = Color.White,
            onBackground = Color(0xFFF3F4F6),
            onSurface = Color(0xFFF3F4F6),
            onSurfaceVariant = Color(0xFFB8BEC7),
            outlineVariant = Color(0xFF343942)
        )
    } else {
        lightColorScheme(
            primary = TravelerRed,
            secondary = TravelerBlue,
            background = Color(0xFFF4F5F7),
            surface = Color.White,
            surfaceVariant = Color(0xFFF0F2F5),
            onPrimary = Color.White,
            onSurface = TravelerBlack,
            onSurfaceVariant = TravelerGray,
            outlineVariant = LightBorder
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

