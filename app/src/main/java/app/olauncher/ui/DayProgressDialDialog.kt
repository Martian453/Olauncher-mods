package app.olauncher.ui

import android.content.Context
import android.view.LayoutInflater
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogDayDialBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog

object DayProgressDialDialog {

    fun show(context: Context) {
        val prefs = Prefs(context)
        val dialog = OlDialog(context)
        val binding = DialogDayDialBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val currentDay = DisciplineManager.getCurrentChallengeDay(context)
        val targetDays = prefs.challengeTargetDays
        val daysRemaining = DisciplineManager.getDaysRemaining(context)
        val progressPercent = DisciplineManager.getChallengeProgressPercent(context)

        binding.tvDialDayNumber.text = "DAY $currentDay"
        binding.tvDialDaysRemaining.text = "$daysRemaining Days Left"
        binding.dialProgress.progress = progressPercent
        binding.tvDialPercentCompleted.text = "$progressPercent% of $targetDays-Day Protocol completed"

        binding.btnDialClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.showRespectingStatusBar()
    }
}
