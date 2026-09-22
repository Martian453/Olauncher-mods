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

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activities = launcherApps.getActivityList(null, Process.myUserHandle())
        val appItems = activities.map {
            AppItem(it.label.toString(), it.applicationInfo.packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

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

            holder.binding.cbDistractingApp.setOnCheckedChangeListener { _, isChecked ->
                DisciplineManager.setAppDistracting(context, item.packageName, isChecked)
            }

            holder.binding.tvLimitLabel.setOnClickListener {
                val input = EditText(context)
                input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                input.setText(limitMinutes.toString())
                AlertDialog.Builder(context)
                    .setTitle("Daily Limit (minutes)")
                    .setMessage("Set maximum daily minutes for ${item.label}:")
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

        override fun getItemCount(): Int = items.size
    }
}
