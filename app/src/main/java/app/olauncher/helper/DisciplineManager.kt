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
        if (prefs.detoxStartDate == 0L) {
            prefs.detoxStartDate = System.currentTimeMillis()
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

        // Check tasks rollover
        if (prefs.lastTasksDate != today) {
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

        return try {
            val list = mutableListOf<DailyTask>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DailyTask(
                        id = obj.optInt("id", i + 1),
                        title = obj.optString("title", "Task ${i + 1}"),
                        isCompleted = obj.optBoolean("isCompleted", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toggleTask(context: Context, taskId: Int): List<DailyTask> {
        val tasks = getDailyTasks(context).toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            val task = tasks[index]
            tasks[index] = task.copy(isCompleted = !task.isCompleted)
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
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
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
        if (jsonStr.isBlank()) return 30 // Default 30 minutes budget
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
        if (limitMinutes <= 0) return false // 0 or negative = unlimited
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
            prefs.detoxStartDate = System.currentTimeMillis()
        }

        // Set tomorrow's tasks
        if (tomorrowTasks.isNotEmpty()) {
            val newTasks = tomorrowTasks.mapIndexed { index, title ->
                DailyTask(id = index + 1, title = title, isCompleted = false)
            }
            saveTasks(context, newTasks)
            // Mark last tasks date as tomorrow so it doesn't get overridden tonight
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            prefs.lastTasksDate = tomorrow
        }
    }

    fun exportBackupJson(context: Context): String {
        val prefs = Prefs(context)
        val json = JSONObject()
        json.put("version", 1)
        json.put("timestamp", System.currentTimeMillis())
        json.put("challengeStartDate", prefs.challengeStartDate)
        json.put("challengeTargetDays", prefs.challengeTargetDays)
        json.put("detoxStreak", prefs.detoxStreak)
        json.put("detoxStartDate", prefs.detoxStartDate)
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
            if (json.has("detoxStreak")) prefs.detoxStreak = json.getInt("detoxStreak")
            if (json.has("detoxStartDate")) prefs.detoxStartDate = json.getLong("detoxStartDate")
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
