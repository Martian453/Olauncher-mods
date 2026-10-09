package app.olauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogTrackDataBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

object TrackDataDialog {

    fun show(context: Context) {
        val dialog = OlDialog(context)
        val binding = DialogTrackDataBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val prefs = Prefs(context)
        val primaryColor = context.getColorFromAttr(R.attr.primaryColor)
        val primaryTrans50 = context.getColorFromAttr(R.attr.primaryColorTrans50)
        val primaryTrans80 = context.getColorFromAttr(R.attr.primaryColorTrans80)

        // 1. CHART 1: 7-Day Habit Continuity Bars
        val history = DisciplineManager.getRecentHistory(context, 7)
        val currentStreak = DisciplineManager.getCurrentStreak(context)
        val bestStreak = DisciplineManager.getBestStreak(context)

        val trackedDays = history.filter { it.isTracked }
        val validRates = trackedDays.map { it.rate }
        val avgRate = if (validRates.isNotEmpty()) validRates.sum() / validRates.size else 0

        binding.tvTrackContinuitySummary.text = "🔥 $currentStreak Streak • $avgRate% Avg Completion"

        binding.layoutHabitBars.removeAllViews()
        val barMaxHeightPx = 64.dpToPx()
        val barWidthPx = 14.dpToPx()
        val cornerRadiusPx = 4.dpToPx().toFloat()

        history.forEach { record ->
            val colLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Outer bar track container
            val trackLayout = FrameLayout(context).apply {
                val params = LinearLayout.LayoutParams(barWidthPx, barMaxHeightPx)
                layoutParams = params
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusPx
                    setColor(if (record.isTracked) Color.parseColor("#1FFFFFFF") else Color.parseColor("#0AFFFFFF"))
                }
            }

            // Inner fill bar
            val fillHeight = if (record.rate > 0) {
                ((record.rate.toFloat() / 100f) * barMaxHeightPx).toInt().coerceAtLeast(3.dpToPx())
            } else {
                0
            }

            val fillView = View(context).apply {
                val fillParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    fillHeight
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                layoutParams = fillParams
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusPx
                    val fillColor = if (record.isToday) {
                        primaryColor
                    } else if (record.success) {
                        primaryTrans80
                    } else {
                        Color.parseColor("#40FFFFFF")
                    }
                    setColor(fillColor)
                }
            }
            trackLayout.addView(fillView)
            colLayout.addView(trackLayout)

            // Day label
            val dayTv = TextView(context).apply {
                text = record.label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (record.isToday) primaryColor else if (record.isTracked) primaryTrans50 else Color.parseColor("#40FFFFFF"))
                typeface = Typeface.MONOSPACE
                setPadding(0, 4.dpToPx(), 0, 0)
            }
            colLayout.addView(dayTv)

            // Percentage label
            val rateTv = TextView(context).apply {
                text = if (record.isTracked) "${record.rate}%" else "—"
                textSize = 8.5f
                gravity = Gravity.CENTER
                setTextColor(if (record.isTracked) primaryTrans50 else Color.parseColor("#30FFFFFF"))
                typeface = Typeface.MONOSPACE
            }
            colLayout.addView(rateTv)

            binding.layoutHabitBars.addView(colLayout)
        }

        // 2. CHART 2: Stats Breakdown
        binding.tvAvgCompletionValue.text = "$avgRate%"
        binding.pbAvgCompletion.progress = avgRate

        val cleanDays = trackedDays.count { it.success }
        val missedDays = trackedDays.count { !it.success && !it.isToday }
        binding.tvCleanDaysCount.text = cleanDays.toString()
        binding.tvMissedDaysCount.text = missedDays.toString()
        binding.tvBestStreakCount.text = "$bestStreak Days"

        // 3. CHART 3: Pickups & Velocity
        val pickups = prefs.pickupCount
        val limit = prefs.pickupLimit
        val pacing = DisciplineManager.getUnlockPacingText(context)

        binding.tvPacingStatus.text = pacing
        binding.tvPickupProgressText.text = "$pickups / $limit Pickups"
        binding.pbPickupsTrack.max = limit.coerceAtLeast(1)
        binding.pbPickupsTrack.progress = pickups.coerceAtMost(limit)

        // 4. CHART 4: Guarded Apps Usage Breakdown
        val guardedPackages = DisciplineManager.getValidDistractingApps(context).toList()
        binding.layoutGuardedAppsList.removeAllViews()

        if (guardedPackages.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "No distracting apps currently tracked.\nLong-press any app in Index to set a daily focus guard."
                textSize = 12f
                setTextColor(primaryTrans50)
                setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            }
            binding.layoutGuardedAppsList.addView(emptyTv)
        } else {
            val pm = context.packageManager
            guardedPackages.forEach { pkg ->
                val appName = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }

                val usedMinutes = DisciplineManager.getAppUsageTodayMinutes(context, pkg).toInt()
                val limitMinutes = DisciplineManager.getAppDailyLimitMinutes(context, pkg)
                val isLocked = usedMinutes >= limitMinutes && limitMinutes > 0

                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 10.dpToPx()
                    }
                }

                // Row header
                val headerRow = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val titleTv = TextView(context).apply {
                    text = if (isLocked) "🔒 $appName (LOCKED)" else appName
                    textSize = 12f
                    setTextColor(if (isLocked) Color.parseColor("#FF6B6B") else primaryColor)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.START
                    }
                }

                val statTv = TextView(context).apply {
                    text = "${usedMinutes}m / ${limitMinutes}m"
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(primaryTrans80)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END
                    }
                }

                headerRow.addView(titleTv)
                headerRow.addView(statTv)
                itemLayout.addView(headerRow)

                // Progress Bar
                val pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        3.dpToPx()
                    ).apply {
                        topMargin = 4.dpToPx()
                    }
                    max = limitMinutes.coerceAtLeast(1)
                    progress = usedMinutes.coerceAtMost(limitMinutes)
                }
                itemLayout.addView(pb)

                binding.layoutGuardedAppsList.addView(itemLayout)
            }
        }

        binding.btnTrackDataClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.showRespectingStatusBar()
    }
}
