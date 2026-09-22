package app.olauncher.ui

import android.content.Context
import android.view.LayoutInflater
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogEveningCheckinBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showToast

object EveningCheckinDialog {

    fun show(
        context: Context,
        onCompleted: () -> Unit
    ) {
        val dialog = OlDialog(context)
        val binding = DialogEveningCheckinBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        // Pre-fill tomorrow's tasks with default habits or today's tasks
        val currentTasks = DisciplineManager.getDailyTasks(context)
        val defaultHabits = DisciplineManager.getDefaultHabits(context)

        val taskTitles = (0 until 5).map { i ->
            when {
                i < currentTasks.size -> currentTasks[i].title
                i < defaultHabits.size -> defaultHabits[i]
                else -> "Priority Task ${i + 1}"
            }
        }

        binding.etTomorrowTask1.setText(taskTitles.getOrElse(0) { "" })
        binding.etTomorrowTask2.setText(taskTitles.getOrElse(1) { "" })
        binding.etTomorrowTask3.setText(taskTitles.getOrElse(2) { "" })
        binding.etTomorrowTask4.setText(taskTitles.getOrElse(3) { "" })
        binding.etTomorrowTask5.setText(taskTitles.getOrElse(4) { "" })

        binding.btnSaveEveningReview.setOnClickListener {
            val stayedClean = binding.rbCleanYes.isChecked
            val tomorrowTasks = listOf(
                binding.etTomorrowTask1.text.toString().trim().ifBlank { "Task 1" },
                binding.etTomorrowTask2.text.toString().trim().ifBlank { "Task 2" },
                binding.etTomorrowTask3.text.toString().trim().ifBlank { "Task 3" },
                binding.etTomorrowTask4.text.toString().trim().ifBlank { "Task 4" },
                binding.etTomorrowTask5.text.toString().trim().ifBlank { "Task 5" },
            )

            DisciplineManager.recordEveningCheckin(context, stayedClean, tomorrowTasks)
            context.showToast(if (stayedClean) "Day locked in! Detox streak updated 🔥" else "Progress logged. Tomorrow is Day 1 - stay focused!")
            dialog.dismiss()
            onCompleted()
        }

        dialog.showRespectingStatusBar()
    }
}
