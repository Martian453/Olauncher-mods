package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogManageDistractingAppsBinding
import app.olauncher.databinding.ItemDistractingAppBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog

object ManageDistractingAppsDialog {

    data class AppItem(val label: String, val packageName: String)

    fun show(context: Context, onSaved: () -> Unit) {
        val dialog = OlDialog(context)
        val binding = DialogManageDistractingAppsBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        val validDistractingApps = DisciplineManager.getValidDistractingApps(context)
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activities = launcherApps.getActivityList(null, Process.myUserHandle())
        val appItems = activities.map {
            AppItem(it.label.toString(), it.applicationInfo.packageName)
        }.distinctBy { it.packageName }.sortedWith(
            compareByDescending<AppItem> { validDistractingApps.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )

        val adapter = DistractingAppsAdapter(context, appItems)
        binding.rvDistractingApps.layoutManager = LinearLayoutManager(context)
        binding.rvDistractingApps.adapter = adapter

        binding.btnDoneDistractingApps.setOnClickListener {
            dialog.dismiss()
            onSaved()
        }

        dialog.showRespectingStatusBar()
    }

    private class DistractingAppsAdapter(
        private val context: Context,
        private val items: List<AppItem>
    ) : RecyclerView.Adapter<DistractingAppsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemDistractingAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDistractingAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isDistracting = DisciplineManager.isAppDistracting(context, item.packageName)
            val limitMinutes = DisciplineManager.getAppDailyLimitMinutes(context, item.packageName)

            holder.binding.tvDistractingAppName.text = item.label
            holder.binding.cbDistractingApp.isChecked = isDistracting
            holder.binding.tvLimitLabel.text = "${limitMinutes}m limit"

            holder.binding.cbDistractingApp.setOnClickListener {
                val currentlyGuarded = DisciplineManager.isAppDistracting(context, item.packageName)
                if (currentlyGuarded) {
                    // Revert visually until confirmed
                    holder.binding.cbDistractingApp.isChecked = true
                    AlertDialog.Builder(context)
                        .setTitle("⚠️ Protocol Warning")
                        .setMessage("Removing \"${item.label}\" weakens your focus barrier.\n\nPenalty: Breaking this discipline commitment will immediately reset your focus streak to 0.\n\nAre you sure you want to proceed?")
                        .setPositiveButton("Proceed & Reset Streak") { _, _ ->
                            DisciplineManager.setAppDistracting(context, item.packageName, false)
                            val prefs = Prefs(context)
                            prefs.habitStreakCount = 0
                            prefs.manualStreakUpdatedDate = ""
                            notifyItemChanged(position)
                        }
                        .setNegativeButton("Cancel (Keep Guarded)", null)
                        .show()
                } else {
                    DisciplineManager.setAppDistracting(context, item.packageName, true)
                    notifyItemChanged(position)
                }
            }

            holder.binding.tvDistractingAppName.setOnClickListener {
                holder.binding.cbDistractingApp.performClick()
            }

            holder.binding.tvLimitLabel.setOnClickListener {
                val durations = arrayOf("15 mins", "30 mins (Recommended)", "45 mins", "60 mins", "Custom...")
                val values = arrayOf(15, 30, 45, 60)
                AlertDialog.Builder(context)
                    .setTitle("Daily Limit for ${item.label}")
                    .setItems(durations) { _, which ->
                        if (which < values.size) {
                            DisciplineManager.setAppDailyLimitMinutes(context, item.packageName, values[which])
                            notifyItemChanged(position)
                        } else {
                            val input = EditText(context).apply {
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                                setText(limitMinutes.toString())
                            }
                            AlertDialog.Builder(context)
                                .setTitle("Custom Daily Limit (minutes)")
                                .setView(input)
                                .setPositiveButton(R.string.okay) { _, _ ->
                                    val mins = input.text.toString().toIntOrNull() ?: 30
                                    DisciplineManager.setAppDailyLimitMinutes(context, item.packageName, mins.coerceAtLeast(5))
                                    notifyItemChanged(position)
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
