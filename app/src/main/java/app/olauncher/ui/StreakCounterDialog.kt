package app.olauncher.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogStreakCounterBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StreakCounterDialog {

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun show(
        context: Context,
        onStreakUpdated: () -> Unit
    ) {
        val prefs = Prefs(context)
        val dialog = OlDialog(context)
        val binding = DialogStreakCounterBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val today = getTodayDateString()
        var currentStreak = prefs.habitStreakCount

        fun updateUI() {
            currentStreak = prefs.habitStreakCount
            val bestStreak = maxOf(prefs.bestHabitStreak, currentStreak)
            binding.tvStreakCountLarge.text = "🔥 $currentStreak ${if (currentStreak == 1) "Day" else "Days"}"
            binding.tvStreakBest.text = "Personal Best: $bestStreak Days"

            val isLoggedToday = prefs.manualStreakUpdatedDate == today || prefs.habitsAutoCreditedDate == today
            if (isLoggedToday) {
                binding.btnTapCounterPlus.isEnabled = false
                binding.btnTapCounterPlus.alpha = 0.4f
                binding.btnTapCounterPlus.text = "✓"
                binding.tvStreakStatusMessage.text = "Streak logged for today. Resets tomorrow."
            } else {
                binding.btnTapCounterPlus.isEnabled = true
                binding.btnTapCounterPlus.alpha = 1.0f
                binding.btnTapCounterPlus.text = "+1"
                binding.tvStreakStatusMessage.text = "Tap once to restore or increment today's streak."
            }

            // Streak Freeze Shield status
            val freezesRemaining = DisciplineManager.getStreakFreezesRemaining(context)
            val isShieldActive = DisciplineManager.isStreakFreezeActiveToday(context)

            if (isShieldActive) {
                binding.tvFreezeStatus.text = "🛡️ Shield Active Today • Protected at midnight\n($freezesRemaining freezes left this month)"
                binding.btnActivateFreeze.isEnabled = false
                binding.btnActivateFreeze.alpha = 0.5f
                binding.btnActivateFreeze.text = "✓ Shield Active Today"
            } else if (freezesRemaining <= 0) {
                binding.tvFreezeStatus.text = "0 / 3 freezes remaining this month"
                binding.btnActivateFreeze.isEnabled = false
                binding.btnActivateFreeze.alpha = 0.4f
                binding.btnActivateFreeze.text = "No Freezes Remaining"
            } else {
                binding.tvFreezeStatus.text = "$freezesRemaining / 3 freezes available this month"
                binding.btnActivateFreeze.isEnabled = true
                binding.btnActivateFreeze.alpha = 1.0f
                binding.btnActivateFreeze.text = "🛡️ Activate Shield for Today"
            }
        }

        updateUI()

        binding.btnTapCounterPlus.setOnClickListener {
            // Trigger haptic feedback
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            } catch (_: Exception) {}

            val newStreak = prefs.habitStreakCount + 1
            prefs.habitStreakCount = newStreak
            if (newStreak > prefs.bestHabitStreak) {
                prefs.bestHabitStreak = newStreak
            }
            prefs.manualStreakUpdatedDate = today

            updateUI()
            onStreakUpdated()
        }

        binding.btnActivateFreeze.setOnClickListener {
            val success = DisciplineManager.activateStreakFreezeToday(context)
            if (success) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(70)
                    }
                } catch (_: Exception) {}
                context.showToast("Streak Freeze Shield activated for today! 🛡️")
                updateUI()
            }
        }

        binding.btnStreakClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.showRespectingStatusBar()
    }
}
