package app.olauncher.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import app.olauncher.databinding.DialogMilestoneCeremonyBinding
import app.olauncher.helper.MilestoneBadge
import app.olauncher.helper.OlDialog

object MilestoneCeremonyDialog {

    fun show(
        context: Context,
        badge: MilestoneBadge,
        onDismissed: () -> Unit = {}
    ) {
        val dialog = OlDialog(context)
        val binding = DialogMilestoneCeremonyBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        binding.tvCeremonyIcon.text = badge.icon
        binding.tvCeremonyTitle.text = badge.title
        binding.tvCeremonySubtitle.text = "${badge.description} • Added to profile"

        val motto = when (badge.id) {
            "first_dawn" -> "“The journey of a hundred days begins with a single dawn.”"
            "iron_week" -> "“Consistency is the forge of true discipline.”"
            "digital_monk" -> "“Peace of mind begins where distraction ends.”"
            "fortress" -> "“A fortress of focus withstands all digital temptations.”"
            "the_knight" -> "“Mastery achieved. The warrior has conquered himself.”"
            else -> "“Discipline is the bridge between goals and accomplishment.”"
        }
        binding.tvCeremonyMotto.text = motto

        // Trigger celebratory haptic feedback on show
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 100), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 70, 60, 100), -1)
            }
        } catch (_: Exception) {}

        binding.btnClaimCeremony.setOnClickListener {
            dialog.dismiss()
            onDismissed()
        }

        dialog.setOnDismissListener {
            onDismissed()
        }

        dialog.showRespectingStatusBar()
    }
}
