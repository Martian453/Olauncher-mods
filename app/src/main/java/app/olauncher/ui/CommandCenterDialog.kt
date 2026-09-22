package app.olauncher.ui

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.widget.TextView
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogCommandCenterBinding
import app.olauncher.databinding.ItemDailyTaskBinding
import app.olauncher.helper.DailyTask
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog

object CommandCenterDialog {

    fun show(
        context: Context,
        screenTimeString: String,
        onTasksUpdated: () -> Unit,
        onEveningCheckinRequested: () -> Unit
    ) {
        val prefs = Prefs(context)
        val dialog = OlDialog(context)
        val binding = DialogCommandCenterBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val currentDay = DisciplineManager.getCurrentChallengeDay(context)
        val daysRemaining = DisciplineManager.getDaysRemaining(context)
        val progress = DisciplineManager.getChallengeProgressPercent(context)

        binding.tvChallengeDay.text = "Day $currentDay of ${prefs.challengeTargetDays}"
        binding.tvDaysRemaining.text = "$daysRemaining Days Left"
        binding.pbChallenge.progress = progress

        val st = if (screenTimeString.isNotBlank()) screenTimeString else "0m"
        binding.tvRealityStats.text = "📱 Pickups: ${prefs.pickupCount} / ${prefs.pickupLimit}   •   ⏳ Screen Time: $st"

        fun renderTasks() {
            binding.layoutTasksContainer.removeAllViews()
            val tasks = DisciplineManager.getDailyTasks(context)
            val inflater = LayoutInflater.from(context)

            tasks.forEach { task ->
                val itemBinding = ItemDailyTaskBinding.inflate(inflater, binding.layoutTasksContainer, false)
                itemBinding.cbTask.isChecked = task.isCompleted
                itemBinding.tvTaskTitle.text = "${task.id}. ${task.title}"

                if (task.isCompleted) {
                    itemBinding.tvTaskTitle.paintFlags = itemBinding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    itemBinding.tvTaskTitle.alpha = 0.5f
                } else {
                    itemBinding.tvTaskTitle.paintFlags = itemBinding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    itemBinding.tvTaskTitle.alpha = 1.0f
                }

                itemBinding.layoutTaskItem.setOnClickListener {
                    DisciplineManager.toggleTask(context, task.id)
                    renderTasks()
                    onTasksUpdated()
                }

                binding.layoutTasksContainer.addView(itemBinding.root)
            }
        }

        renderTasks()

        binding.ivCloseCommandCenter.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnEveningCheckin.setOnClickListener {
            dialog.dismiss()
            onEveningCheckinRequested()
        }

        dialog.showRespectingStatusBar()
    }
}
