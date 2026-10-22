package app.olauncher.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.showToast
import java.util.Calendar

class StreakReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STREAK_REMINDER = "app.olauncher.action.STREAK_REMINDER_ALARM"
        const val ACTION_LOG_STREAK = "app.olauncher.action.LOG_STREAK"
        const val ACTION_SKIP_STREAK = "app.olauncher.action.SKIP_STREAK"

        private const val NOTIFICATION_ID = 45301
        private const val CHANNEL_ID = "discipline_streak_reminder"

        private val STOIC_QUOTES = listOf(
            "\"You have power over your mind, not outside events. Realize this, and you will find strength.\" — Marcus Aurelius",
            "\"We suffer more often in imagination than in reality.\" — Seneca",
            "\"No man is free who is not master of himself.\" — Epictetus",
            "\"Step by step, walk the thousand-mile road.\" — Miyamoto Musashi",
            "\"Discipline equals freedom.\" — Jocko Willink",
            "\"Small disciplines repeated with consistency every day lead to great achievements.\" — John Maxwell",
            "\"Do not let the day expire with your commitment unkept. Stand guard.\" — The Knight",
            "\"Doubt is natural, but consistency conquers doubt.\" — The Knight",
            "\"First say to yourself what you would be; and then do what you have to do.\" — Epictetus",
            "\"Waste no more time arguing what a good man should be. Be one.\" — Marcus Aurelius"
        )

        fun scheduleDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, StreakReminderReceiver::class.java).apply {
                action = ACTION_STREAK_REMINDER
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val today = DisciplineManager.getTodayDateString()

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                scheduleDailyReminder(context)
            }

            ACTION_STREAK_REMINDER -> {
                // Smart silence: check if already logged today
                val isLoggedToday = prefs.manualStreakUpdatedDate == today || prefs.habitsAutoCreditedDate == today
                if (isLoggedToday) return

                // Check if user already skipped today
                if (prefs.lastStreakReminderSkippedDate == today) return

                showReminderNotification(context, prefs)
            }

            ACTION_LOG_STREAK -> {
                dismissNotification(context)

                val isLoggedToday = prefs.manualStreakUpdatedDate == today || prefs.habitsAutoCreditedDate == today
                if (isLoggedToday) {
                    context.showToast("Streak already secured for today! 🔥")
                    return
                }

                val newStreak = prefs.habitStreakCount + 1
                prefs.habitStreakCount = newStreak
                if (newStreak > prefs.bestHabitStreak) {
                    prefs.bestHabitStreak = newStreak
                }
                prefs.manualStreakUpdatedDate = today

                context.showToast("Streak secured! Day $newStreak locked in 🔥")
            }

            ACTION_SKIP_STREAK -> {
                dismissNotification(context)

                val isLoggedToday = prefs.manualStreakUpdatedDate == today || prefs.habitsAutoCreditedDate == today
                if (isLoggedToday) {
                    context.showToast("Streak already locked in for today! 🔥")
                    return
                }

                prefs.lastStreakReminderSkippedDate = today
                context.showToast("Tomorrow is Day 1 — Stand guard ⚔️")
            }
        }
    }

    private fun showReminderNotification(context: Context, prefs: Prefs) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Knight Protocol Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nightly reminders to secure your daily discipline streak"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Tapping the notification body opens the app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(context, 1002, openAppIntent, flags)

        // Action 1: +1 Log Streak
        val logIntent = Intent(context, StreakReminderReceiver::class.java).apply {
            action = ACTION_LOG_STREAK
        }
        val logPendingIntent = PendingIntent.getBroadcast(context, 1003, logIntent, flags)

        // Action 2: Skip to Tomorrow
        val skipIntent = Intent(context, StreakReminderReceiver::class.java).apply {
            action = ACTION_SKIP_STREAK
        }
        val skipPendingIntent = PendingIntent.getBroadcast(context, 1004, skipIntent, flags)

        // Pick rotating quote based on day of year
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val quote = STOIC_QUOTES[dayOfYear % STOIC_QUOTES.size]
        val currentStreak = prefs.habitStreakCount

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_streak)
            .setContentTitle("Knight Protocol • Streak at Risk 🔥")
            .setContentText(quote)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$quote\n\nActive streak: $currentStreak ${if (currentStreak == 1) "Day" else "Days"}. Stand guard before midnight.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "🔥 +1 Log Streak", logPendingIntent)
            .addAction(0, "⏭️ Skip to Tomorrow", skipPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
