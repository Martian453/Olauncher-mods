package app.olauncher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import app.olauncher.databinding.DialogBackupRestoreBinding
import app.olauncher.helper.DisciplineManager
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showToast

object BackupRestoreDialog {

    fun show(context: Context, onDataRestored: () -> Unit = {}) {
        val dialog = OlDialog(context)
        val binding = DialogBackupRestoreBinding.inflate(LayoutInflater.from(dialog.context))
        dialog.setView(binding.root)

        // Export
        binding.btnExportBackup.setOnClickListener {
            val json = DisciplineManager.exportBackupJson(context)

            // Copy to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Olauncher Discipline Backup", json))

            // Open share chooser
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Olauncher Discipline Backup")
                putExtra(Intent.EXTRA_TEXT, json)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Save or Share Backup"))
            context.showToast("Backup copied to clipboard!")
        }

        // Paste from clipboard
        binding.btnPasteClipboard.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                binding.etImportJson.setText(text)
                context.showToast("Pasted from clipboard")
            } else {
                context.showToast("Clipboard is empty")
            }
        }

        // Restore
        binding.btnRestoreBackup.setOnClickListener {
            val input = binding.etImportJson.text.toString().trim()
            if (input.isBlank()) {
                context.showToast("Please paste valid backup JSON")
                return@setOnClickListener
            }

            val success = DisciplineManager.importBackupJson(context, input)
            if (success) {
                context.showToast("Backup restored successfully!")
                onDataRestored()
                dialog.dismiss()
            } else {
                context.showToast("Failed to parse backup. Check formatting.")
            }
        }

        binding.btnCloseBackup.setOnClickListener {
            dialog.dismiss()
        }

        dialog.showRespectingStatusBar()
    }
}
