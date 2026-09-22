package app.olauncher.ui

import android.content.Context
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogAntiCheatBinding
import app.olauncher.helper.OlDialog

object AntiCheatDialog {

    fun show(
        context: Context,
        onUnlocked: () -> Unit
    ) {
        val prefs = Prefs(context)
        val dialog = OlDialog(context)
        val binding = DialogAntiCheatBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val totalSeconds = prefs.antiCheatCooldownSeconds.coerceAtLeast(10)
        val requiredPledge = prefs.antiCheatPledge.trim()
        binding.tvRequiredPledge.text = "\"$requiredPledge\""

        var secondsRemaining = totalSeconds
        var timer: CountDownTimer? = null

        fun checkCanUnlock() {
            val userText = binding.etPledgeInput.text.toString().trim()
            val textMatches = userText.equals(requiredPledge, ignoreCase = true)
            val timeUp = secondsRemaining <= 0

            if (textMatches && timeUp) {
                binding.btnConfirmAntiCheat.isEnabled = true
                binding.btnConfirmAntiCheat.alpha = 1.0f
            } else {
                binding.btnConfirmAntiCheat.isEnabled = false
                binding.btnConfirmAntiCheat.alpha = 0.4f
            }
        }

        binding.etPledgeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCanUnlock()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        timer = object : CountDownTimer((totalSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = ((millisUntilFinished / 1000) + 1).toInt()
                binding.tvAntiCheatCountdown.text = "${secondsRemaining}s"
                checkCanUnlock()
            }

            override fun onFinish() {
                secondsRemaining = 0
                binding.tvAntiCheatCountdown.text = "0s"
                checkCanUnlock()
            }
        }.start()

        binding.btnCancelAntiCheat.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
        }

        binding.btnConfirmAntiCheat.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
            onUnlocked()
        }

        dialog.setOnDismissListener {
            timer.cancel()
        }

        dialog.showRespectingStatusBar()
    }
}
