package app.olauncher.ui

import android.content.Context
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogFrictionGateBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showToast

object FrictionGateDialog {

    enum class GateType {
        HIDDEN_APPS,
        DISTRACTING_APP
    }

    fun show(
        context: Context,
        gateType: GateType,
        targetName: String,
        onUnlocked: () -> Unit
    ) {
        val prefs = Prefs(context)
        DisciplineManager.checkDateRollover(context)
        val dialog = OlDialog(context)
        val binding = DialogFrictionGateBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val countToday = if (gateType == GateType.HIDDEN_APPS) {
            prefs.hiddenAppsAccessCountToday
        } else {
            prefs.distractingAppsLaunchCountToday
        }

        val totalSeconds = if (gateType == GateType.HIDDEN_APPS) {
            DisciplineManager.getHiddenAppsCooldownSeconds(context)
        } else {
            DisciplineManager.getDistractingAppsCooldownSeconds(context)
        }

        val mathRequired = countToday >= 1
        val mathLevel = countToday.coerceAtLeast(1)
        val mathChallenge = if (mathRequired) DisciplineManager.generateMathChallenge(mathLevel) else null

        binding.tvGateTargetName.text = targetName
        binding.tvGateAccessInfo.text = "Access #${countToday + 1} today • ${totalSeconds}s cooldown"

        if (mathChallenge != null) {
            binding.layoutGateMath.visibility = View.VISIBLE
            binding.tvGateEquation.text = mathChallenge.equation
        } else {
            binding.layoutGateMath.visibility = View.GONE
        }

        var secondsRemaining = totalSeconds
        var timer: CountDownTimer? = null

        fun checkCanUnlock() {
            val timeUp = secondsRemaining <= 0
            val mathSolved = if (mathChallenge != null) {
                val inputStr = binding.etGateMathAnswer.text.toString().trim()
                val userAns = inputStr.toIntOrNull()
                userAns != null && userAns == mathChallenge.answer
            } else {
                true
            }

            if (timeUp && mathSolved) {
                binding.btnGateUnlock.isEnabled = true
                binding.btnGateUnlock.alpha = 1.0f
                binding.btnGateUnlock.text = "Unlock & Open"
            } else {
                binding.btnGateUnlock.isEnabled = false
                binding.btnGateUnlock.alpha = 0.4f
                binding.btnGateUnlock.text = if (!timeUp) "Wait for Timer (${secondsRemaining}s)" else "Enter Correct Solution"
            }
        }

        if (mathRequired) {
            binding.etGateMathAnswer.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    checkCanUnlock()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        timer = object : CountDownTimer((totalSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = ((millisUntilFinished / 1000) + 1).toInt()
                binding.tvGateSecondsRemaining.text = "${secondsRemaining}s"
                val percent = ((secondsRemaining.toFloat() / totalSeconds.toFloat()) * 100).toInt()
                binding.progressGateCountdown.progress = percent
                checkCanUnlock()
            }

            override fun onFinish() {
                secondsRemaining = 0
                binding.tvGateSecondsRemaining.text = "0s"
                binding.progressGateCountdown.progress = 0
                checkCanUnlock()
            }
        }.start()

        binding.btnGatePutDown.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
        }

        binding.btnGateUnlock.setOnClickListener {
            if (mathChallenge != null) {
                val inputStr = binding.etGateMathAnswer.text.toString().trim()
                val userAns = inputStr.toIntOrNull()
                if (userAns == null || userAns != mathChallenge.answer) {
                    context.showToast("Incorrect answer. Please solve carefully.")
                    return@setOnClickListener
                }
            }

            timer.cancel()
            if (gateType == GateType.HIDDEN_APPS) {
                prefs.hiddenAppsAccessCountToday += 1
            } else {
                prefs.distractingAppsLaunchCountToday += 1
            }
            dialog.dismiss()
            onUnlocked()
        }

        dialog.setOnDismissListener {
            timer.cancel()
        }

        dialog.showRespectingStatusBar()
    }
}
