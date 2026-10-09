package app.olauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogLaunchFrictionBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showToast

object LaunchFrictionDialog {

    fun show(
        context: Context,
        appName: String,
        packageName: String,
        onProceedLaunch: () -> Unit
    ) {
        DisciplineManager.checkDateRollover(context)
        val dialog = OlDialog(context)
        val binding = DialogLaunchFrictionBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        binding.tvFrictionAppName.text = appName

        // Check if app has exceeded daily limit
        if (DisciplineManager.isAppLockedOut(context, packageName)) {
            val limit = DisciplineManager.getAppDailyLimitMinutes(context, packageName)
            val canBypass = DisciplineManager.canGrantEmergencyPass(context)
            binding.layoutFocusNotice.visibility = View.GONE
            binding.layoutEmergencyForm.visibility = View.GONE
            binding.layoutHardLockout.visibility = View.VISIBLE

            if (canBypass) {
                binding.btnEmergencyBypass.visibility = View.VISIBLE
                binding.tvLockoutMessage.text = "You have used your entire ${limit}m budget for $appName today.\nStay focused on your protocol."
                binding.btnEmergencyBypass.setOnClickListener {
                    binding.layoutHardLockout.visibility = View.GONE
                    binding.layoutEmergencyForm.visibility = View.VISIBLE
                    binding.etEmergencyReason.requestFocus()
                }
            } else {
                binding.btnEmergencyBypass.visibility = View.GONE
                binding.tvLockoutMessage.text = "You have used your entire ${limit}m budget for $appName today.\n\nEmergency bypass already exhausted for today. Access locked until tomorrow."
            }

            binding.btnLockoutExit.setOnClickListener { dialog.dismiss() }
            binding.btnCancelEmergency.setOnClickListener {
                binding.layoutEmergencyForm.visibility = View.GONE
                binding.layoutHardLockout.visibility = View.VISIBLE
            }
            binding.btnConfirmEmergency.setOnClickListener {
                val reason = binding.etEmergencyReason.text.toString().trim()
                if (reason.length < 10) {
                    context.showToast("Please state a genuine reason (min 10 characters)")
                } else {
                    val granted = DisciplineManager.grantEmergencyPass(context, packageName)
                    if (granted) {
                        context.showToast("5-minute emergency pass granted. Make it count!")
                        dialog.dismiss()
                        onProceedLaunch()
                    } else {
                        context.showToast("Emergency bypass limit reached for today.")
                        dialog.dismiss()
                    }
                }
            }
            dialog.showRespectingStatusBar()
            return
        }

        // App is within budget: show clean Protocol Notice (no wait timer, immediate conscious choice)
        val limit = DisciplineManager.getAppDailyLimitMinutes(context, packageName)
        val used = DisciplineManager.getAppUsageTodayMinutes(context, packageName).toInt()
        val remaining = (limit - used).coerceAtLeast(0)

        binding.layoutHardLockout.visibility = View.GONE
        binding.layoutEmergencyForm.visibility = View.GONE
        binding.layoutFocusNotice.visibility = View.VISIBLE

        binding.tvAllowanceNotice.text = "${remaining}m Remaining Today"
        binding.tvAllowanceDetail.text = "${used}m used of ${limit}m daily allowance"
        binding.progressAllowance.progress = if (limit > 0) {
            ((used.toFloat() / limit) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        binding.btnReturnToFocus.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnProceedLaunch.text = "Open App (${remaining}m Left)"
        binding.btnProceedLaunch.setOnClickListener {
            val prefs = Prefs(context)
            prefs.distractingAppsLaunchCountToday += 1
            dialog.dismiss()
            onProceedLaunch()
        }

        dialog.showRespectingStatusBar()
    }
}

