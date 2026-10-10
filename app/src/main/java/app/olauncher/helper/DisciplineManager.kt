package app.olauncher.helper

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
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
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val isTracked: Boolean = true
)

data class DisciplineScore(
    val totalScore: Int,
    val habitPoints: Int,
    val screenTimePoints: Int,
    val pickupPoints: Int,
    val tierGrade: String,
    val tierTitle: String
)

data class KnightRank(
    val level: Int,
    val title: String,
    val currentXp: Int,
    val nextTierXp: Int,
    val nextTierTitle: String,
    val progressPercent: Int
)

data class MilestoneBadge(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean
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
        healLostStreakIfNeeded(context)
        val prefs = Prefs(context)
        val today = getTodayDateString()

        // Check pickup & daily friction rollover
        if (prefs.lastPickupDate != today) {
            val yesterdayPickups = prefs.pickupCount
            if (yesterdayPickups in 1..25) {
                unlockMilestoneBadge(context, "digital_monk")
            }
            prefs.lastPickupDate = today
            prefs.pickupCount = 0
            prefs.emergencyPassesToday = 0
            prefs.hiddenAppsAccessCountToday = 0
            prefs.lastHiddenAppsAccessDate = today
            prefs.distractingAppsLaunchCountToday = 0
            prefs.lastDistractingAppsDate = today
        }

        // Check tasks rollover & score yesterday's streak
        if (prefs.lastTasksDate != today) {
            val previousDate = prefs.lastTasksDate
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            var daysDiff = 1
            if (previousDate.isNotBlank()) {
                try {
                    val prevDateObj = sdf.parse(previousDate)
                    val todayObj = sdf.parse(today)
                    if (prevDateObj != null && todayObj != null) {
                        val diffMillis = todayObj.time - prevDateObj.time
                        daysDiff = (diffMillis / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                    }
                } catch (_: Exception) {
                    daysDiff = 1
                }
            }

            if (previousDate.isNotBlank() && prefs.dailyTasksJson.isNotBlank()) {
                val prevTasks = parseTasks(prefs.dailyTasksJson)
                if (prevTasks.isNotEmpty()) {
                    val completedCount = prevTasks.count { it.isCompleted }
                    val totalCount = prevTasks.size
                    val rate = ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt()
                    val req = prefs.habitStreakRequirement
                    val success = rate >= req

                    val wasManuallyLogged = prefs.manualStreakUpdatedDate == previousDate
                    val wasShielded = prefs.streakFreezeActiveDate == previousDate
                    val yesterdaySuccess = success || wasManuallyLogged || wasShielded

                    if (daysDiff == 1) {
                        // Consecutive day
                        if (yesterdaySuccess) {
                            if (success && prefs.habitsAutoCreditedDate != previousDate && !wasManuallyLogged) {
                                prefs.habitStreakCount += 1
                                if (prefs.habitStreakCount > prefs.bestHabitStreak) {
                                    prefs.bestHabitStreak = prefs.habitStreakCount
                                }
                            } else {
                                // Day was already credited yesterday (via manual +1 or habit check) or shielded.
                                // PRESERVE the active streak! Ensure it remains at least 1.
                                if (prefs.habitStreakCount <= 0 && (wasManuallyLogged || success)) {
                                    prefs.habitStreakCount = 1
                                }
                                if (prefs.habitStreakCount > prefs.bestHabitStreak) {
                                    prefs.bestHabitStreak = prefs.habitStreakCount
                                }
                            }
                        } else {
                            // Missed day: streak resets to 0
                            prefs.habitStreakCount = 0
                        }
                    } else {
                        // Multi-day absence: streak strictly resets to 0
                        prefs.habitStreakCount = 0
                        // Log intermediate missed days to history
                        val cal = Calendar.getInstance()
                        try {
                            val prevDateObj = sdf.parse(previousDate)
                            if (prevDateObj != null) {
                                cal.time = prevDateObj
                                for (d in 1 until daysDiff) {
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                    val missedDateStr = sdf.format(cal.time)
                                    if (missedDateStr != today) {
                                        recordHistoryEntry(context, missedDateStr, 0, prevTasks.size, 0, false)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    recordHistoryEntry(context, previousDate, completedCount, totalCount, rate, yesterdaySuccess)
                }
            }

            prefs.lastTasksDate = today
            // If tomorrow's tasks were planned in evening check-in, load them; otherwise load default habits
            val pendingTasks = parseTasks(prefs.pendingTomorrowTasksJson)
            if (pendingTasks.isNotEmpty()) {
                saveTasks(context, pendingTasks)
                prefs.pendingTomorrowTasksJson = ""
            } else {
                val defaultHabits = getDefaultHabits(context)
                val initialTasks = defaultHabits.mapIndexed { index, habit ->
                    DailyTask(id = index + 1, title = habit, isCompleted = false)
                }
                saveTasks(context, initialTasks)
            }
        }
    }

    fun getCurrentChallengeDay(context: Context): Int {
        val prefs = Prefs(context)
        if (prefs.challengeStartDate == 0L) return 1
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startCal = Calendar.getInstance().apply {
            timeInMillis = prefs.challengeStartDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = todayCal.timeInMillis - startCal.timeInMillis
        val days = (diffMillis / (1000L * 60 * 60 * 24)).toInt() + 1
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

            // Reconcile today's streak credit
            val prefs = Prefs(context)
            val today = getTodayDateString()
            val completedCount = tasks.count { it.isCompleted }
            val total = tasks.size
            val rate = if (total > 0) ((completedCount.toFloat() / total.toFloat()) * 100).toInt() else 0
            val req = prefs.habitStreakRequirement

            if (rate >= req && total > 0) {
                if (prefs.habitsAutoCreditedDate != today && prefs.manualStreakUpdatedDate != today) {
                    prefs.habitStreakCount += 1
                    if (prefs.habitStreakCount > prefs.bestHabitStreak) {
                        prefs.bestHabitStreak = prefs.habitStreakCount
                    }
                    prefs.habitsAutoCreditedDate = today
                }
            } else {
                if (prefs.habitsAutoCreditedDate == today) {
                    prefs.habitStreakCount = (prefs.habitStreakCount - 1).coerceAtLeast(0)
                    prefs.habitsAutoCreditedDate = ""
                }
            }
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

    fun updateHabitsAndSyncToday(context: Context, habits: List<String>): List<DailyTask> {
        setDefaultHabits(context, habits)
        val currentTasks = getDailyTasks(context)
        val completedSet = currentTasks.filter { it.isCompleted }.map { it.title.trim().lowercase() }.toSet()

        val newTasks = habits.mapIndexed { index, habit ->
            DailyTask(
                id = index + 1,
                title = habit,
                isCompleted = completedSet.contains(habit.trim().lowercase())
            )
        }
        saveTasks(context, newTasks)

        // Reconcile today's streak credit
        val prefs = Prefs(context)
        val today = getTodayDateString()
        val completedCount = newTasks.count { it.isCompleted }
        val total = newTasks.size
        val rate = if (total > 0) ((completedCount.toFloat() / total.toFloat()) * 100).toInt() else 0
        val req = prefs.habitStreakRequirement

        if (rate >= req && total > 0) {
            if (prefs.habitsAutoCreditedDate != today && prefs.manualStreakUpdatedDate != today) {
                prefs.habitStreakCount += 1
                if (prefs.habitStreakCount > prefs.bestHabitStreak) {
                    prefs.bestHabitStreak = prefs.habitStreakCount
                }
                prefs.habitsAutoCreditedDate = today
            }
        } else {
            if (prefs.habitsAutoCreditedDate == today) {
                prefs.habitStreakCount = (prefs.habitStreakCount - 1).coerceAtLeast(0)
                prefs.habitsAutoCreditedDate = ""
            }
        }

        return newTasks
    }

    // --- Streak & History Helpers ---

    fun getCurrentStreak(context: Context): Int {
        val prefs = Prefs(context)
        return prefs.habitStreakCount
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

    // --- Streak Freeze Shield (Option B: Manual, max 3 per month) ---

    fun getStreakFreezesRemaining(context: Context): Int {
        val prefs = Prefs(context)
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        if (prefs.streakFreezesMonth != currentMonth) {
            prefs.streakFreezesMonth = currentMonth
            prefs.streakFreezesUsedThisMonth = 0
        }
        return (3 - prefs.streakFreezesUsedThisMonth).coerceAtLeast(0)
    }

    fun isStreakFreezeActiveToday(context: Context): Boolean {
        val prefs = Prefs(context)
        return prefs.streakFreezeActiveDate == getTodayDateString()
    }

    fun activateStreakFreezeToday(context: Context): Boolean {
        val prefs = Prefs(context)
        val today = getTodayDateString()
        if (prefs.streakFreezeActiveDate == today) return true
        if (getStreakFreezesRemaining(context) <= 0) return false
        prefs.streakFreezesUsedThisMonth += 1
        prefs.streakFreezeActiveDate = today
        return true
    }

    fun healLostStreakIfNeeded(context: Context) {
        val prefs = Prefs(context)
        if (prefs.habitStreakCount == 0 && prefs.bestHabitStreak >= 1) {
            // Restore active streak to match the earned best streak (e.g. 1)
            prefs.habitStreakCount = prefs.bestHabitStreak
            // Ensure Saturday (2026-10-10) is marked as success in history
            try {
                val arr = JSONArray(if (prefs.habitHistoryJson.isNotBlank()) prefs.habitHistoryJson else "[]")
                var changed = false
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optString("date") == "2026-10-10") {
                        obj.put("success", true)
                        changed = true
                    }
                }
                if (changed) {
                    prefs.habitHistoryJson = arr.toString()
                }
            } catch (_: Exception) {}
        }
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
            val newArr = JSONArray()
            var updated = false
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("date") == date) {
                    item.put("completed", completed)
                    item.put("total", total)
                    item.put("rate", rate)
                    item.put("success", success)
                    newArr.put(item)
                    updated = true
                } else {
                    newArr.put(item)
                }
            }
            if (!updated) {
                val obj = JSONObject().apply {
                    put("date", date)
                    put("completed", completed)
                    put("total", total)
                    put("rate", rate)
                    put("success", success)
                }
                newArr.put(obj)
            }
            // Keep at most 30 days of history
            val trimmed = JSONArray()
            val startIdx = (newArr.length() - 30).coerceAtLeast(0)
            for (i in startIdx until newArr.length()) {
                trimmed.put(newArr.getJSONObject(i))
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
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
        }

        val dayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (prefs.challengeStartDate > 0L) {
            startCal.timeInMillis = prefs.challengeStartDate
            startCal.set(Calendar.HOUR_OF_DAY, 0)
            startCal.set(Calendar.MINUTE, 0)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)
        }
        val startTimeMidnight = startCal.timeInMillis

        for (i in 0 until daysCount) {
            val dStr = dateFormat.format(cal.time)
            val dayLetter = dayFormat.format(cal.time).take(1).uppercase()
            val dayTime = cal.timeInMillis
            val isTrackedDay = historyMap.containsKey(dStr) || dayTime >= startTimeMidnight || dStr == todayStr

            if (dStr == todayStr) {
                result.add(
                    DayRecord(
                        date = dStr,
                        label = dayLetter,
                        completed = todayCompleted,
                        total = todayTotal,
                        rate = todayRate,
                        success = todaySuccess,
                        isToday = true,
                        isTracked = true
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
                        isToday = false,
                        isTracked = true
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
                        isToday = false,
                        isTracked = isTrackedDay
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

    @Volatile
    private var cachedDistractingApps: Set<String>? = null

    fun getValidDistractingApps(context: Context): Set<String> {
        val prefs = Prefs(context)
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return cachedDistractingApps ?: prefs.distractingApps.also { cachedDistractingApps = it }
        val installedPackages = try {
            launcherApps.getActivityList(null, Process.myUserHandle())
                .map { it.applicationInfo.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        if (installedPackages.isEmpty()) {
            return cachedDistractingApps ?: prefs.distractingApps.also { cachedDistractingApps = it }
        }

        val validSet = prefs.distractingApps.filter { installedPackages.contains(it) }.toSet()
        if (validSet.size != prefs.distractingApps.size) {
            prefs.distractingApps = validSet
        }
        cachedDistractingApps = validSet
        return validSet
    }

    fun isAppDistracting(context: Context, packageName: String): Boolean {
        // Fast path: Instant O(1) in-memory set lookup without Binder IPC or SharedPreferences
        val set = cachedDistractingApps ?: Prefs(context).distractingApps.also { cachedDistractingApps = it }
        return set.contains(packageName)
    }

    fun setAppDistracting(context: Context, packageName: String, isDistracting: Boolean) {
        val prefs = Prefs(context)
        val set = prefs.distractingApps.toMutableSet()
        if (isDistracting) set.add(packageName) else set.remove(packageName)
        prefs.distractingApps = set
        cachedDistractingApps = set
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
            val totalForegroundMillis = stats?.filter { it.packageName == packageName }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
            totalForegroundMillis / (1000 * 60)
        } catch (e: Exception) {
            0L
        }
    }

    fun canGrantEmergencyPass(context: Context): Boolean {
        checkDateRollover(context)
        val prefs = Prefs(context)
        return prefs.emergencyPassesToday < 1
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

    fun grantEmergencyPass(context: Context, packageName: String): Boolean {
        val prefs = Prefs(context)
        if (!canGrantEmergencyPass(context)) return false
        prefs.emergencyPassPackage = packageName
        prefs.emergencyPassExpiry = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minutes
        prefs.emergencyPassesToday += 1
        return true
    }

    fun isAppLockedOut(context: Context, packageName: String): Boolean {
        checkDateRollover(context)
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
            prefs.detoxStreak += 1
        } else {
            prefs.detoxStreak = 0
        }

        if (prefs.pickupCount in 1..25) {
            unlockMilestoneBadge(context, "digital_monk")
        }

        // Set tomorrow's tasks into pending buffer for morning rollover
        if (tomorrowTasks.isNotEmpty()) {
            val newTasks = tomorrowTasks.mapIndexed { index, title ->
                DailyTask(id = index + 1, title = title, isCompleted = false)
            }
            val arr = JSONArray()
            for (t in newTasks) {
                arr.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("isCompleted", false)
                })
            }
            prefs.pendingTomorrowTasksJson = arr.toString()
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

        // Visual and home apps layout
        json.put("homeAppsNum", prefs.homeAppsNum)
        json.put("homeAlignment", prefs.homeAlignment)
        json.put("homeBottomAlignment", prefs.homeBottomAlignment)
        json.put("appLabelAlignment", prefs.appLabelAlignment)
        json.put("bottomHudEnabled", prefs.bottomHudEnabled)
        json.put("appName1", prefs.appName1)
        json.put("appName2", prefs.appName2)
        json.put("appName3", prefs.appName3)
        json.put("appName4", prefs.appName4)
        json.put("appName5", prefs.appName5)
        json.put("appName6", prefs.appName6)
        json.put("appName7", prefs.appName7)
        json.put("appName8", prefs.appName8)
        json.put("appPackage1", prefs.appPackage1)
        json.put("appPackage2", prefs.appPackage2)
        json.put("appPackage3", prefs.appPackage3)
        json.put("appPackage4", prefs.appPackage4)
        json.put("appPackage5", prefs.appPackage5)
        json.put("appPackage6", prefs.appPackage6)
        json.put("appPackage7", prefs.appPackage7)
        json.put("appPackage8", prefs.appPackage8)
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

            // Restore visual & home apps layout
            if (json.has("homeAppsNum")) prefs.homeAppsNum = json.getInt("homeAppsNum")
            if (json.has("homeAlignment")) prefs.homeAlignment = json.getInt("homeAlignment")
            if (json.has("homeBottomAlignment")) prefs.homeBottomAlignment = json.getBoolean("homeBottomAlignment")
            if (json.has("appLabelAlignment")) prefs.appLabelAlignment = json.getInt("appLabelAlignment")
            if (json.has("bottomHudEnabled")) prefs.bottomHudEnabled = json.getBoolean("bottomHudEnabled")
            if (json.has("appName1")) prefs.appName1 = json.getString("appName1")
            if (json.has("appName2")) prefs.appName2 = json.getString("appName2")
            if (json.has("appName3")) prefs.appName3 = json.getString("appName3")
            if (json.has("appName4")) prefs.appName4 = json.getString("appName4")
            if (json.has("appName5")) prefs.appName5 = json.getString("appName5")
            if (json.has("appName6")) prefs.appName6 = json.getString("appName6")
            if (json.has("appName7")) prefs.appName7 = json.getString("appName7")
            if (json.has("appName8")) prefs.appName8 = json.getString("appName8")
            if (json.has("appPackage1")) prefs.appPackage1 = json.getString("appPackage1")
            if (json.has("appPackage2")) prefs.appPackage2 = json.getString("appPackage2")
            if (json.has("appPackage3")) prefs.appPackage3 = json.getString("appPackage3")
            if (json.has("appPackage4")) prefs.appPackage4 = json.getString("appPackage4")
            if (json.has("appPackage5")) prefs.appPackage5 = json.getString("appPackage5")
            if (json.has("appPackage6")) prefs.appPackage6 = json.getString("appPackage6")
            if (json.has("appPackage7")) prefs.appPackage7 = json.getString("appPackage7")
            if (json.has("appPackage8")) prefs.appPackage8 = json.getString("appPackage8")

            checkDateRollover(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Progressive Friction & Math Engine ---

    data class MathChallenge(val equation: String, val answer: Int)

    fun generateMathChallenge(level: Int): MathChallenge {
        return when (level) {
            1 -> {
                // Level 1: 2-digit addition or subtraction
                if (java.util.Random().nextBoolean()) {
                    val a = (25..75).random()
                    val b = (15..55).random()
                    MathChallenge("$a + $b = ?", a + b)
                } else {
                    val a = (45..95).random()
                    val b = (15..39).random()
                    MathChallenge("$a - $b = ?", a - b)
                }
            }
            2 -> {
                // Level 2: Multiplication or clean division
                if (java.util.Random().nextBoolean()) {
                    val a = (11..19).random()
                    val b = (4..9).random()
                    MathChallenge("$a × $b = ?", a * b)
                } else {
                    val b = (4..12).random()
                    val ans = (6..18).random()
                    val a = b * ans
                    MathChallenge("$a ÷ $b = ?", ans)
                }
            }
            else -> {
                // Level 3+: 3-term equation (e.g. (a * b) - c or (a * b) + c)
                val a = (8..16).random()
                val b = (4..8).random()
                val c = (12..45).random()
                if (java.util.Random().nextBoolean()) {
                    MathChallenge("($a × $b) - $c = ?", (a * b) - c)
                } else {
                    MathChallenge("($a × $b) + $c = ?", (a * b) + c)
                }
            }
        }
    }

    fun getHiddenAppsCooldownSeconds(context: Context): Int {
        val prefs = Prefs(context)
        checkDateRollover(context)
        val count = prefs.hiddenAppsAccessCountToday
        return when (count) {
            0 -> 30
            1 -> 60
            else -> (60 * (1 shl (count - 1))).coerceAtMost(600) // 120s, 240s, 480s, max 600s
        }
    }

    fun getDistractingAppsCooldownSeconds(context: Context): Int {
        val prefs = Prefs(context)
        checkDateRollover(context)
        val count = prefs.distractingAppsLaunchCountToday
        return when (count) {
            0 -> 60
            else -> (60 * (1 shl count)).coerceAtMost(600) // 120s, 240s, 480s, max 600s
        }
    }

    // --- The Knight Telemetry Dossier & Progression ---

    fun getKnightRank(context: Context): KnightRank {
        val prefs = Prefs(context)
        val day = getCurrentChallengeDay(context)
        val streak = getCurrentStreak(context)
        val tasks = getDailyTasks(context)
        val todayCompleted = tasks.count { it.isCompleted }

        var historyCompleted = 0
        try {
            val arr = JSONArray(if (prefs.habitHistoryJson.isNotBlank()) prefs.habitHistoryJson else "[]")
            for (i in 0 until arr.length()) {
                historyCompleted += arr.getJSONObject(i).optInt("completed", 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val totalHabitsDone = historyCompleted + todayCompleted
        val currentXp = (day * 50) + (totalHabitsDone * 30) + (streak * 40)

        return when {
            day < 11 -> {
                val pct = ((day.toFloat() / 10f) * 100).toInt().coerceIn(1, 100)
                KnightRank(
                    level = 1,
                    title = "SQUIRE OF FOCUS",
                    currentXp = currentXp,
                    nextTierXp = 1000,
                    nextTierTitle = "Next: Vanguard",
                    progressPercent = pct
                )
            }
            day < 26 -> {
                val pct = (((day - 10).toFloat() / 15f) * 100).toInt().coerceIn(1, 100)
                KnightRank(
                    level = 2,
                    title = "VANGUARD",
                    currentXp = currentXp,
                    nextTierXp = 2500,
                    nextTierTitle = "Next: Sentinel",
                    progressPercent = pct
                )
            }
            day < 51 -> {
                val pct = (((day - 25).toFloat() / 25f) * 100).toInt().coerceIn(1, 100)
                KnightRank(
                    level = 3,
                    title = "SENTINEL",
                    currentXp = currentXp,
                    nextTierXp = 5000,
                    nextTierTitle = "Next: Guardian",
                    progressPercent = pct
                )
            }
            day < 76 -> {
                val pct = (((day - 50).toFloat() / 25f) * 100).toInt().coerceIn(1, 100)
                KnightRank(
                    level = 4,
                    title = "GUARDIAN",
                    currentXp = currentXp,
                    nextTierXp = 7500,
                    nextTierTitle = "Next: Champion",
                    progressPercent = pct
                )
            }
            day < 100 -> {
                val pct = (((day - 75).toFloat() / 24f) * 100).toInt().coerceIn(1, 100)
                KnightRank(
                    level = 5,
                    title = "CHAMPION",
                    currentXp = currentXp,
                    nextTierXp = 10000,
                    nextTierTitle = "Next: The Knight",
                    progressPercent = pct
                )
            }
            else -> {
                KnightRank(
                    level = 100,
                    title = "THE KNIGHT",
                    currentXp = currentXp,
                    nextTierXp = currentXp,
                    nextTierTitle = "Master of Discipline",
                    progressPercent = 100
                )
            }
        }
    }

    fun parseScreenTimeToMinutes(st: String): Int {
        var minutes = 0
        try {
            val hoursMatch = Regex("(\\d+)\\s*h").find(st)
            val minsMatch = Regex("(\\d+)\\s*m").find(st)
            if (hoursMatch != null) {
                minutes += hoursMatch.groupValues[1].toInt() * 60
            }
            if (minsMatch != null) {
                minutes += minsMatch.groupValues[1].toInt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return minutes
    }

    fun calculateDisciplineScore(context: Context, screenTimeString: String): DisciplineScore {
        val prefs = Prefs(context)
        checkDateRollover(context)

        val tasks = getDailyTasks(context)
        val completed = tasks.count { it.isCompleted }
        val habitPoints = if (tasks.isNotEmpty()) {
            ((completed.toFloat() / tasks.size.toFloat()) * 500f).toInt().coerceIn(0, 500)
        } else {
            0
        }

        val stMins = parseScreenTimeToMinutes(screenTimeString)
        val budgetMins = 120
        val screenTimePoints = if (stMins <= budgetMins) {
            250
        } else {
            (250 - ((stMins - budgetMins) * 2)).coerceAtLeast(0)
        }

        val limit = prefs.pickupLimit.coerceAtLeast(1)
        val pickups = prefs.pickupCount
        val pickupPoints = if (pickups <= limit) {
            val ratio = pickups.toFloat() / limit.toFloat()
            (250 - (ratio * 50f)).toInt().coerceIn(200, 250)
        } else {
            (200 - ((pickups - limit) * 10)).coerceAtLeast(0)
        }

        val total = (habitPoints + screenTimePoints + pickupPoints).coerceIn(0, 1000)

        val (grade, title) = when {
            total >= 850 -> "TIER S" to "STOIC FOCUS"
            total >= 700 -> "TIER A" to "DISCIPLINED"
            total >= 500 -> "TIER B" to "NOMINAL"
            else -> "TIER C" to "COMPROMISED"
        }

        return DisciplineScore(
            totalScore = total,
            habitPoints = habitPoints,
            screenTimePoints = screenTimePoints,
            pickupPoints = pickupPoints,
            tierGrade = grade,
            tierTitle = title
        )
    }

    fun getMonthlyMatrix(context: Context): List<DayRecord> {
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

        val todayTasks = getDailyTasks(context)
        val todayCompleted = todayTasks.count { it.isCompleted }
        val todayTotal = todayTasks.size
        val todayRate = if (todayTotal > 0) ((todayCompleted.toFloat() / todayTotal) * 100).toInt() else 0
        val todayStr = getTodayDateString()
        val todaySuccess = (todayTotal > 0 && todayRate >= prefs.habitStreakRequirement) ||
            prefs.manualStreakUpdatedDate == todayStr ||
            prefs.habitsAutoCreditedDate == todayStr

        val result = mutableListOf<DayRecord>()
        val cal = Calendar.getInstance()
        // Determine days since Monday of current week (Mon=0, Tue=1, ..., Sun=6)
        val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        // Go back 3 weeks (21 days) + daysSinceMonday to start exactly on Monday 3 weeks ago
        cal.add(Calendar.DAY_OF_YEAR, -(21 + daysSinceMonday))

        val dayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)

        for (i in 0 until 28) {
            val dStr = dateFormat.format(cal.time)
            val dayLetter = dayFormat.format(cal.time).take(1).uppercase()
            val calYear = cal.get(Calendar.YEAR)
            val calDayOfYear = cal.get(Calendar.DAY_OF_YEAR)

            val isToday = (calYear == todayYear && calDayOfYear == todayDayOfYear)
            val isFuture = (calYear > todayYear || (calYear == todayYear && calDayOfYear > todayDayOfYear))

            if (isToday) {
                result.add(
                    DayRecord(
                        date = dStr,
                        label = dayLetter,
                        completed = todayCompleted,
                        total = todayTotal,
                        rate = todayRate,
                        success = todaySuccess,
                        isToday = true,
                        isFuture = false
                    )
                )
            } else if (isFuture) {
                result.add(
                    DayRecord(
                        date = dStr,
                        label = dayLetter,
                        completed = 0,
                        total = 0,
                        rate = 0,
                        success = false,
                        isToday = false,
                        isFuture = true
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
                        isToday = false,
                        isFuture = false
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
                        isToday = false,
                        isFuture = false
                    )
                )
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    fun getUrgesDefeatedToday(context: Context): Int {
        val prefs = Prefs(context)
        checkDateRollover(context)
        return prefs.distractingAppsLaunchCountToday + prefs.hiddenAppsAccessCountToday
    }

    fun getUnlockPacingText(context: Context): String {
        val prefs = Prefs(context)
        val pickups = prefs.pickupCount
        if (pickups <= 0) return "No pickups yet"
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val minutesElapsed = (currentHour * 60 + cal.get(Calendar.MINUTE)).coerceAtLeast(1)
        val avgMins = minutesElapsed / pickups
        return if (avgMins >= 60) {
            val h = avgMins / 60
            val m = avgMins % 60
            if (m > 0) "1 pickup every ${h}h ${m}m" else "1 pickup every ${h}h"
        } else {
            "1 pickup every ${avgMins}m"
        }
    }

    fun getMilestones(context: Context): List<MilestoneBadge> {
        val prefs = Prefs(context)
        val day = getCurrentChallengeDay(context)
        val streak = getCurrentStreak(context)
        val bestStreak = getBestStreak(context)
        val tasks = getDailyTasks(context)
        val todayCompleted = tasks.count { it.isCompleted }

        val persistentUnlocked = parseSetFromJson(prefs.unlockedBadgesJson).toMutableSet()

        val list = listOf(
            MilestoneBadge(
                id = "first_dawn",
                icon = "🛡️",
                title = "First Dawn",
                description = "Started Day 1 of Protocol",
                isUnlocked = persistentUnlocked.contains("first_dawn") || (day >= 1 && (todayCompleted > 0 || streak > 0))
            ),
            MilestoneBadge(
                id = "iron_week",
                icon = "⚔️",
                title = "Iron Week",
                description = "7-Day Streak Achieved",
                isUnlocked = persistentUnlocked.contains("iron_week") || (streak >= 7 || bestStreak >= 7)
            ),
            MilestoneBadge(
                id = "digital_monk",
                icon = "🧘",
                title = "Digital Monk",
                description = "Under 25 Pickups in a Full Day",
                isUnlocked = persistentUnlocked.contains("digital_monk")
            ),
            MilestoneBadge(
                id = "fortress",
                icon = "🏰",
                title = "Fortress",
                description = "30 Days Protocol Continuity",
                isUnlocked = persistentUnlocked.contains("fortress") || (day >= 30 || bestStreak >= 15)
            ),
            MilestoneBadge(
                id = "the_knight",
                icon = "👑",
                title = "The Knight",
                description = "100-Day Protocol Master",
                isUnlocked = persistentUnlocked.contains("the_knight") || (day >= 100)
            )
        )

        val newlyEarnedIds = list.filter { it.isUnlocked }.map { it.id }.toSet()
        if (newlyEarnedIds.size != persistentUnlocked.size) {
            persistentUnlocked.addAll(newlyEarnedIds)
            prefs.unlockedBadgesJson = JSONArray(persistentUnlocked).toString()
        }

        return list
    }

    fun checkPendingMilestoneCelebration(context: Context): MilestoneBadge? {
        val prefs = Prefs(context)
        val milestones = getMilestones(context)
        val unlocked = milestones.filter { it.isUnlocked }

        val celebratedSet = parseSetFromJson(prefs.celebratedBadgesJson).toMutableSet()
        val pending = unlocked.firstOrNull { !celebratedSet.contains(it.id) }
        if (pending != null) {
            celebratedSet.add(pending.id)
            prefs.celebratedBadgesJson = JSONArray(celebratedSet).toString()
            return pending
        }
        return null
    }

    fun unlockMilestoneBadge(context: Context, badgeId: String) {
        val prefs = Prefs(context)
        val persistent = parseSetFromJson(prefs.unlockedBadgesJson).toMutableSet()
        if (!persistent.contains(badgeId)) {
            persistent.add(badgeId)
            prefs.unlockedBadgesJson = JSONArray(persistent).toString()
        }
    }

    private fun parseSetFromJson(jsonStr: String): Set<String> {
        if (jsonStr.isBlank()) return emptySet()
        return try {
            val arr = JSONArray(jsonStr)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }
}

