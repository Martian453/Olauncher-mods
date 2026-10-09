package app.olauncher.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogCommandCenterBinding
import app.olauncher.databinding.ItemMilestoneChipBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

object CommandCenterDialog {

    fun show(
        context: Context,
        screenTimeString: String,
        onTasksUpdated: (() -> Unit)? = null,
        onEveningCheckinRequested: (() -> Unit)? = null
    ) {
        val prefs = Prefs(context)
        val dialog = OlDialog(context)
        val binding = DialogCommandCenterBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val currentDay = DisciplineManager.getCurrentChallengeDay(context)

        // 1. Knight Mastery Level & Rank
        val rank = DisciplineManager.getKnightRank(context)
        binding.tvRankTitle.text = "⚔️ Level ${rank.level} • ${rank.title}"
        binding.pbRankProgress.progress = rank.progressPercent
        binding.tvRankSubtitle.text = "Day $currentDay/${prefs.challengeTargetDays} • ${rank.currentXp} XP • ${rank.nextTierTitle}"

        // 2. Daily Discipline Score (0 – 1000 Pts)
        val score = DisciplineManager.calculateDisciplineScore(context, screenTimeString)
        binding.tvScoreNumber.text = score.totalScore.toString()
        binding.tvScoreTierBadge.text = "${score.tierGrade} • ${score.tierTitle}"

        binding.tvScoreHabitsValue.text = "${score.habitPoints} / 500 pts"
        binding.pbScoreHabits.progress = score.habitPoints

        binding.tvScoreScreenTimeValue.text = "${score.screenTimePoints} / 250 pts"
        binding.pbScoreScreenTime.progress = score.screenTimePoints

        binding.tvScorePickupsValue.text = "${score.pickupPoints} / 250 pts"
        binding.pbScorePickups.progress = score.pickupPoints

        // 3. 28-Day Consistency Matrix (GitHub-style Heatmap)
        val consistency = DisciplineManager.getOverallConsistencyPercent(context)
        val streak = DisciplineManager.getCurrentStreak(context)
        val best = DisciplineManager.getBestStreak(context)
        binding.tvMatrixSummary.text = "$consistency% Continuity • Best: $best Days • 🔥 $streak Streak"

        val monthlyDays = DisciplineManager.getMonthlyMatrix(context)
        val matrixWeeks = monthlyDays.chunked(7)
        binding.layoutMatrixGrid.removeAllViews()

        val geistMonoFont = ResourcesCompat.getFont(context, R.font.geist_mono)
        val primaryColor = context.getColorFromAttr(R.attr.primaryColor)
        val primaryTrans80 = context.getColorFromAttr(R.attr.primaryColorTrans80)
        val primaryTrans50 = context.getColorFromAttr(R.attr.primaryColorTrans50)

        matrixWeeks.forEach { week ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.dpToPx()
                    bottomMargin = 4.dpToPx()
                }
            }

            week.forEach { day ->
                val dayTv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = Gravity.CENTER
                    textSize = 14f
                    typeface = geistMonoFont

                    if (day.isToday) {
                        text = "●"
                        setTextColor(primaryColor)
                        alpha = 1.0f
                    } else if (day.isFuture) {
                        text = "·"
                        setTextColor(primaryTrans50)
                        alpha = 0.25f
                    } else if (day.success) {
                        text = "■"
                        setTextColor(primaryColor)
                        alpha = 0.95f
                    } else if (day.completed > 0) {
                        text = "⬝"
                        setTextColor(primaryTrans80)
                        alpha = 0.7f
                    } else {
                        text = "○"
                        setTextColor(primaryTrans50)
                        alpha = 0.35f
                    }
                }
                rowLayout.addView(dayTv)
            }
            binding.layoutMatrixGrid.addView(rowLayout)
        }

        // 4. Milestones & Honors (Achieved Only Ribbon)
        val milestones = DisciplineManager.getMilestones(context)
        binding.layoutMilestonesContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)

        val earnedMilestones = milestones.filter { it.isUnlocked }
        if (earnedMilestones.isEmpty()) {
            val emptyTv = android.widget.TextView(context).apply {
                text = "No honors earned yet. Complete daily habits & streaks to unlock."
                setTextColor(primaryTrans50)
                textSize = 12f
                typeface = binding.tvRankTitle.typeface
                setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            }
            binding.layoutMilestonesContainer.addView(emptyTv)
        } else {
            val milestoneRows = earnedMilestones.chunked(2)
            milestoneRows.forEach { rowItems ->
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 6.dpToPx()
                    }
                }
                rowItems.forEach { m ->
                    val chipBinding = ItemMilestoneChipBinding.inflate(inflater, rowLayout, false)
                    chipBinding.tvMilestoneChip.text = "${m.icon} ${m.title}"
                    chipBinding.tvMilestoneChip.alpha = 1.0f
                    chipBinding.tvMilestoneChip.setTextColor(primaryColor)
                    rowLayout.addView(chipBinding.root)
                }
                binding.layoutMilestonesContainer.addView(rowLayout)
            }
        }

        // 5. Action Triggers
        binding.ivCloseCommandCenter.setOnClickListener {
            dialog.dismiss()
        }

        dialog.showRespectingStatusBar()
    }
}
