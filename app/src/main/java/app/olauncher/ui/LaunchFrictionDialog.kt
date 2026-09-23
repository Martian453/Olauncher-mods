package app.olauncher.ui

import android.content.Context
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
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
        val dialog = OlDialog(context)
        val binding = DialogLaunchFrictionBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        binding.tvFrictionAppName.text = appName

        // Check if app has exceeded daily limit
        if (DisciplineManager.isAppLockedOut(context, packageName)) {
            val limit = DisciplineManager.getAppDailyLimitMinutes(context, packageName)
            binding.layoutBreathingCountdown.visibility = View.GONE
            binding.layoutMathGate.visibility = View.GONE
            binding.layoutEmergencyForm.visibility = View.GONE
            binding.layoutHardLockout.visibility = View.VISIBLE
            binding.tvLockoutMessage.text = "You have used your entire ${limit}m budget for $appName today.\nStay focused on your protocol."
            binding.btnLockoutExit.setOnClickListener { dialog.dismiss() }
            binding.btnEmergencyBypass.setOnClickListener {
                binding.layoutHardLockout.visibility = View.GONE
                binding.layoutEmergencyForm.visibility = View.VISIBLE
                binding.etEmergencyReason.requestFocus()
            }
            binding.btnCancelEmergency.setOnClickListener {
                binding.layoutEmergencyForm.visibility = View.GONE
                binding.layoutHardLockout.visibility = View.VISIBLE
            }
            binding.btnConfirmEmergency.setOnClickListener {
                val reason = binding.etEmergencyReason.text.toString().trim()
                if (reason.length < 10) {
                    context.showToast("Please state a genuine reason (min 10 characters)")
                } else {
                    DisciplineManager.grantEmergencyPass(context, packageName)
                    context.showToast("5-minute emergency pass granted. Make it count!")
                    dialog.dismiss()
                    onProceedLaunch()
                }
            }
            dialog.showRespectingStatusBar()
            return
        }

        // Start 10s breathing countdown
        binding.layoutBreathingCountdown.visibility = View.VISIBLE
        binding.layoutMathGate.visibility = View.GONE
        binding.layoutHardLockout.visibility = View.GONE
        binding.layoutEmergencyForm.visibility = View.GONE

        var timer: CountDownTimer? = null
        val num1 = (14..48).random()
        val num2 = (13..49).random()
        val expectedAnswer = num1 + num2

        timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                binding.tvCountdownSeconds.text = seconds.toString()
            }

            override fun onFinish() {
                binding.layoutBreathingCountdown.visibility = View.GONE
                binding.layoutMathGate.visibility = View.VISIBLE
                binding.tvMathProblem.text = "$num1 + $num2 = ?"
                binding.etMathAnswer.requestFocus()
            }
        }.start()

        binding.btnPutPhoneDown.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
        }

        binding.btnCancelGate.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnUnlockApp.setOnClickListener {
            val userInput = binding.etMathAnswer.text.toString().trim()
            if (userInput == expectedAnswer.toString()) {
                dialog.dismiss()
                onProceedLaunch()
            } else {
                context.showToast("Incorrect answer. Take a moment to reflect!")
                binding.etMathAnswer.text.clear()
            }
        }

        dialog.setOnDismissListener {
            timer.cancel()
        }

        dialog.showRespectingStatusBar()
    }
}
