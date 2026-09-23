package app.olauncher.helper

import android.app.usage.UsageStatsManager
import android.content.Context
import app.olauncher.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyTask(
    val id: Int,
    var title: String,
    var isCompleted: Boolean
)

data class DayRecord(
    val date: String,
    val label: String,
    val completed: Int,
    val total: Int,
    val rate: Int,
    val success: Boolean,
    val isToday: Boolean = false
)

object DisciplineManager {

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun initializeIfNeeded(context: Context) {
        val prefs = Prefs(context)
        if (prefs.challengeStartDate == 0L) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            prefs.challengeStartDate = calendar.timeInMillis
        }
        if (prefs.defaultHabitsJson.isBlank()) {
            val defaults = listOf(
                "Morning Workout (45 min)",
                "Read 20 Pages",
                "Deep Work Session (2 hr)",
                "Meditation & Reflection",
                "No Phone After 10:30 PM"
            )
            setDefaultHabits(context, defaults)
        }
        checkDateRollover(context)
    }

    fun checkDateRollover(context: Context) {
        val prefs = Prefs(context)
        val today = getTodayDateString()

        // Check pickup rollover
        if (prefs.lastPickupDate != today) {
            prefs.lastPickupDate = today
            prefs.pickupCount = 0
            prefs.emergencyPassesToday = 0
        }

        // Check tasks rollover & score yesterday's streak
        if (prefs.lastTasksDate != today) {
            val previousDate = prefs.lastTasksDate
            if (previousDate.isNotBlank() && prefs.dailyTasksJson.isNotBlank()) {
                val prevTasks = parseTasks(prefs.dailyTasksJson)
                if (prevTasks.isNotEmpty()) {
                    val completedCount = prevTasks.count { it.isCompleted }
                    val totalCount = prevTasks.size
                    val rate = ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt()
                    val req = prefs.habitStreakRequirement
                    val success = rate >= req

                    if (success) {
                        prefs.habitStreakCount += 1
                        if (prefs.habitStreakCount > prefs.bestHabitStreak) {
                            prefs.bestHabitStreak = prefs.habitStreakCount
                        }
                    } else {
                        prefs.habitStreakCount = 0
                    }

                    recordHistoryEntry(context, previousDate, completedCount, totalCount, rate, success)
                }
            }

            prefs.lastTasksDate = today
            // Reset today's tasks from default habits
            val defaultHabits = getDefaultHabits(context)
            val initialTasks = defaultHabits.mapIndexed { index, habit ->
                DailyTask(id = index + 1, title = habit, isCompleted = false)
            }
            saveTasks(context, initialTasks)
        }
    }

    fun getCurrentChallengeDay(context: Context): Int {
        val prefs = Prefs(context)
        if (prefs.challengeStartDate == 0L) return 1
        val diffMillis = System.currentTimeMillis() - prefs.challengeStartDate
        val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt() + 1
        return days.coerceAtLeast(1)
    }

    fun getDaysRemaining(context: Context): Int {
        val prefs = Prefs(context)
        val currentDay = getCurrentChallengeDay(context)
        val target = prefs.challengeTargetDays
        return (target - currentDay).coerceAtLeast(0)
    }

    fun getChallengeProgressPercent(context: Context): Int {
        val prefs = Prefs(context)
        val current = getCurrentChallengeDay(context)
        val target = prefs.challengeTargetDays.coerceAtLeast(1)
        return ((current.toFloat() / target) * 100).toInt().coerceIn(1, 100)
    }

    fun onPhoneUnlocked(context: Context): Boolean {
        checkDateRollover(context)
        val prefs = Prefs(context)
        prefs.pickupCount += 1
        return prefs.pickupSlapEnabled && prefs.pickupCount > prefs.pickupLimit
    }

    private fun parseTasks(jsonStr: String): List<DailyTask> {
        return try {
            val list = mutableListOf<DailyTask>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DailyTask(
                        id = obj.getInt("id"),
                        title = obj.getString("title"),
                        isCompleted = obj.getBoolean("isCompleted")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDailyTasks(context: Context): List<DailyTask> {
        checkDateRollover(context)
        val prefs = Prefs(context)
        val jsonStr = prefs.dailyTasksJson
        if (jsonStr.isBlank()) {
            val defaultHabits = getDefaultHabits(context)
            val tasks = defaultHabits.mapIndexed { index, habit ->
                DailyTask(id = index + 1, title = habit, isCompleted = false)
            }
            saveTasks(context, tasks)
            return tasks
        }
        val list = parseTasks(jsonStr)
        return if (list.isEmpty()) {
            val defaultHabits = getDefaultHabits(context)
            val tasks = defaultHabits.mapIndexed { index, habit ->
                DailyTask(id = index + 1, title = habit, isCompleted = false)
            }
            saveTasks(context, tasks)
            tasks
        } else list
    }

    fun toggleTask(context: Context, taskId: Int): List<DailyTask> {
        val tasks = getDailyTasks(context).toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            val current = tasks[index]
            tasks[index] = current.copy(isCompleted = !current.isCompleted)
            saveTasks(context, tasks)
        }
        return tasks
    }

    fun saveTasks(context: Context, tasks: List<DailyTask>) {
        val prefs = Prefs(context)
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("title", task.title)
            obj.put("isCompleted", task.isCompleted)
            jsonArray.put(obj)
        }
        prefs.dailyTasksJson = jsonArray.toString()
    }

    fun getDefaultHabits(context: Context): List<String> {
        val prefs = Prefs(context)
        val jsonStr = prefs.defaultHabitsJson
        if (jsonStr.isBlank()) {
            return listOf(
                "Morning Workout (45 min)",
                "Read 20 Pages",
                "Deep Work Session (2 hr)",
                "Meditation & Reflection",
                "No Phone After 10:30 PM"
            )
        }
        return try {
            val list = mutableListOf<String>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setDefaultHabits(context: Context, habits: List<String>) {
        val prefs = Prefs(context)
        val jsonArray = JSONArray()
        for (h in habits) {
            jsonArray.put(h)
        }
        prefs.defaultHabitsJson = jsonArray.toString()
    }

    // --- Streak & History Helpers ---

    fun getCurrentStreak(context: Context): Int {
        val prefs = Prefs(context)
        val baseStreak = prefs.habitStreakCount
        val tasks = getDailyTasks(context)
        val completed = tasks.count { it.isCompleted }
        val total = tasks.size
        val rate = if (total > 0) ((completed.toFloat() / total) * 100).toInt() else 0
        val isTodayPassing = total > 0 && rate >= prefs.habitStreakRequirement
        return if (isTodayPassing) baseStreak + 1 else baseStreak
    }

    fun getBestStreak(context: Context): Int {
        val prefs = Prefs(context)
        val current = getCurrentStreak(context)
        return maxOf(prefs.bestHabitStreak, current)
    }

    fun getStreakRequirement(context: Context): Int {
        return Prefs(context).habitStreakRequirement
    }

    fun setStreakRequirement(context: Context, percent: Int) {
        Prefs(context).habitStreakRequirement = percent
    }

    private fun recordHistoryEntry(
        context: Context,
        date: String,
        completed: Int,
        total: Int,
        rate: Int,
        success: Boolean
    ) {
        val prefs = Prefs(context)
        try {
            val arr = JSONArray(if (prefs.habitHistoryJson.isNotBlank()) prefs.habitHistoryJson else "[]")
            val obj = JSONObject().apply {
                put("date", date)
                put("completed", completed)
                put("total", total)
                put("rate", rate)
                put("success", success)
            }
            arr.put(obj)
            // Keep at most 30 days of history
            val trimmed = JSONArray()
            val startIdx = (arr.length() - 30).coerceAtLeast(0)
            for (i in startIdx until arr.length()) {
                trimmed.put(arr.getJSONObject(i))
            }
            prefs.habitHistoryJson = trimmed.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecentHistory(context: Context, daysCount: Int = 7): List<DayRecord> {
        checkDateRollover(context)
        val prefs = Prefs(context)
        val historyMap = mutableMapOf<String, JSONObject>()
        try {
            val jsonArray = JSONArray(if (prefs.habitHistoryJson.isNotBlank()) prefs.habitHistoryJson else "[]")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                historyMap[obj.getString("date")] = obj
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val todayStr = getTodayDateString()
        val todayTasks = getDailyTasks(context)
        val todayCompleted = todayTasks.count { it.isCompleted }
        val todayTotal = todayTasks.size
        val todayRate = if (todayTotal > 0) ((todayCompleted.toFloat() / todayTotal) * 100).toInt() else 0
        val todaySuccess = todayTotal > 0 && todayRate >= prefs.habitStreakRequirement

        val result = mutableListOf<DayRecord>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1))

        val dayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (i in 0 until daysCount) {
            val dStr = dateFormat.format(cal.time)
            val dayLetter = dayFormat.format(cal.time).take(1).uppercase()
            if (dStr == todayStr) {
                result.add(
                    DayRecord(
                        date = dStr,
                        label = dayLetter,
                        completed = todayCompleted,
                        total = todayTotal,
                        rate = todayRate,
                        success = todaySuccess,
                        isToday = true
                    )
                )
            } else if (historyMap.containsKey(dStr)) {
                val obj = historyMap[dStr]!!
                val comp = obj.optInt("completed", 0)
                val tot = obj.optInt("total", 0)
                val r = obj.optInt("rate", 0)
                val s = obj.optBoolean("success", false)
                result.add(
                    DayRecord(
                        date = dStr,
                        label = dayLetter,
                        completed = comp,
                        total = tot,
                        rate = r,
                        success = s,
                        isToday = false
                    )
                )
            } else {
                result.add(
                    DayRecord(
                        date = dStr,
                        label = dayLetter,
                        completed = 0,
                        total = 0,
                        rate = 0,
                        success = false,
                        isToday = false
                    )
                )
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    fun getOverallConsistencyPercent(context: Context): Int {
        val prefs = Prefs(context)
        return try {
            val arr = JSONArray(if (prefs.habitHistoryJson.isNotBlank()) prefs.habitHistoryJson else "[]")
            if (arr.length() == 0) {
                val todayTasks = getDailyTasks(context)
                val todayCompleted = todayTasks.count { it.isCompleted }
                val todayTotal = todayTasks.size
                if (todayTotal == 0) 100 else ((todayCompleted.toFloat() / todayTotal) * 100).toInt()
            } else {
                var wins = 0
                for (i in 0 until arr.length()) {
                    if (arr.getJSONObject(i).optBoolean("success", false)) wins++
                }
                ((wins.toFloat() / arr.length()) * 100).toInt()
            }
        } catch (e: Exception) {
            100
        }
    }

    // --- Distracting Apps & Friction Gate ---

    fun isAppDistracting(context: Context, packageName: String): Boolean {
        val prefs = Prefs(context)
        return prefs.distractingApps.contains(packageName)
    }

    fun setAppDistracting(context: Context, packageName: String, isDistracting: Boolean) {
        val prefs = Prefs(context)
        val set = prefs.distractingApps.toMutableSet()
        if (isDistracting) set.add(packageName) else set.remove(packageName)
        prefs.distractingApps = set
    }

    fun getAppDailyLimitMinutes(context: Context, packageName: String): Int {
        val prefs = Prefs(context)
        val jsonStr = prefs.appTimeLimits
        if (jsonStr.isBlank()) return 30
        return try {
            val obj = JSONObject(jsonStr)
            obj.optInt(packageName, 30)
        } catch (e: Exception) {
            30
        }
    }

    fun setAppDailyLimitMinutes(context: Context, packageName: String, minutes: Int) {
        val prefs = Prefs(context)
        val jsonStr = prefs.appTimeLimits
        val obj = try {
            if (jsonStr.isNotBlank()) JSONObject(jsonStr) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        obj.put(packageName, minutes)
        prefs.appTimeLimits = obj.toString()
    }

    fun getAppUsageTodayMinutes(context: Context, packageName: String): Long {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = cal.timeInMillis
            val endTime = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val appStat = stats?.firstOrNull { it.packageName == packageName }
            (appStat?.totalTimeInForeground ?: 0L) / (1000 * 60)
        } catch (e: Exception) {
            0L
        }
    }

    fun hasActiveEmergencyPass(context: Context, packageName: String): Boolean {
        val prefs = Prefs(context)
        return prefs.emergencyPassPackage == packageName && System.currentTimeMillis() < prefs.emergencyPassExpiry
    }

    fun getEmergencyPassRemainingSeconds(context: Context, packageName: String): Long {
        val prefs = Prefs(context)
        if (prefs.emergencyPassPackage != packageName) return 0L
        val diff = prefs.emergencyPassExpiry - System.currentTimeMillis()
        return (diff / 1000).coerceAtLeast(0L)
    }

    fun grantEmergencyPass(context: Context, packageName: String) {
        val prefs = Prefs(context)
        prefs.emergencyPassPackage = packageName
        prefs.emergencyPassExpiry = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minutes
        prefs.emergencyPassesToday += 1
    }

    fun isAppLockedOut(context: Context, packageName: String): Boolean {
        if (hasActiveEmergencyPass(context, packageName)) return false
        if (!isAppDistracting(context, packageName)) return false
        val limitMinutes = getAppDailyLimitMinutes(context, packageName)
        if (limitMinutes <= 0) return false
        val usedMinutes = getAppUsageTodayMinutes(context, packageName)
        return usedMinutes >= limitMinutes
    }

    fun recordEveningCheckin(
        context: Context,
        stayedClean: Boolean,
        tomorrowTasks: List<String>
    ) {
        val prefs = Prefs(context)
        val today = getTodayDateString()
        prefs.detoxLastCheckDate = today

        if (stayedClean) {
            prefs.habitStreakCount += 1
            if (prefs.habitStreakCount > prefs.bestHabitStreak) {
                prefs.bestHabitStreak = prefs.habitStreakCount
            }
        } else {
            prefs.habitStreakCount = 0
        }

        // Set tomorrow's tasks
        if (tomorrowTasks.isNotEmpty()) {
            val newTasks = tomorrowTasks.mapIndexed { index, title ->
                DailyTask(id = index + 1, title = title, isCompleted = false)
            }
            saveTasks(context, newTasks)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            prefs.lastTasksDate = tomorrow
        }
    }

    fun exportBackupJson(context: Context): String {
        val prefs = Prefs(context)
        val json = JSONObject()
        json.put("version", 2)
        json.put("timestamp", System.currentTimeMillis())
        json.put("challengeStartDate", prefs.challengeStartDate)
        json.put("challengeTargetDays", prefs.challengeTargetDays)
        json.put("habitStreakCount", prefs.habitStreakCount)
        json.put("bestHabitStreak", prefs.bestHabitStreak)
        json.put("habitStreakRequirement", prefs.habitStreakRequirement)
        json.put("habitHistoryJson", prefs.habitHistoryJson)
        json.put("pickupLimit", prefs.pickupLimit)
        json.put("pickupSlapEnabled", prefs.pickupSlapEnabled)
        json.put("distractingApps", JSONArray(prefs.distractingApps.toList()))
        json.put("appTimeLimits", prefs.appTimeLimits)
        json.put("defaultHabitsJson", prefs.defaultHabitsJson)
        json.put("dailyTasksJson", prefs.dailyTasksJson)
        json.put("antiCheatCooldownSeconds", prefs.antiCheatCooldownSeconds)
        json.put("antiCheatPledge", prefs.antiCheatPledge)
        return json.toString(2)
    }

    fun importBackupJson(context: Context, jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            val prefs = Prefs(context)
            if (json.has("challengeStartDate")) prefs.challengeStartDate = json.getLong("challengeStartDate")
            if (json.has("challengeTargetDays")) prefs.challengeTargetDays = json.getInt("challengeTargetDays")
            if (json.has("habitStreakCount")) prefs.habitStreakCount = json.getInt("habitStreakCount")
            if (json.has("bestHabitStreak")) prefs.bestHabitStreak = json.getInt("bestHabitStreak")
            if (json.has("habitStreakRequirement")) prefs.habitStreakRequirement = json.getInt("habitStreakRequirement")
            if (json.has("habitHistoryJson")) prefs.habitHistoryJson = json.getString("habitHistoryJson")
            if (json.has("pickupLimit")) prefs.pickupLimit = json.getInt("pickupLimit")
            if (json.has("pickupSlapEnabled")) prefs.pickupSlapEnabled = json.getBoolean("pickupSlapEnabled")
            if (json.has("distractingApps")) {
                val arr = json.getJSONArray("distractingApps")
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                prefs.distractingApps = set
            }
            if (json.has("appTimeLimits")) prefs.appTimeLimits = json.getString("appTimeLimits")
            if (json.has("defaultHabitsJson")) prefs.defaultHabitsJson = json.getString("defaultHabitsJson")
            if (json.has("dailyTasksJson")) prefs.dailyTasksJson = json.getString("dailyTasksJson")
            if (json.has("antiCheatCooldownSeconds")) prefs.antiCheatCooldownSeconds = json.getInt("antiCheatCooldownSeconds")
            if (json.has("antiCheatPledge")) prefs.antiCheatPledge = json.getString("antiCheatPledge")
            checkDateRollover(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
