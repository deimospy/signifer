package org.sarambi.signifer.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sarambi.signifer.R
import org.sarambi.signifer.databinding.SheetSettingsBinding
import org.sarambi.signifer.decode.CodeFamily
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.history.HistoryBackup
import org.sarambi.signifer.history.HistoryFilter
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.history.Retention
import org.sarambi.signifer.settings.ScanPreferences

/** Todos los ajustes, en un solo lugar. */
class SettingsSheet : BottomSheetDialogFragment() {
    /** Quien tiene que enterarse de que algo cambio: la camara y la lista. */
    interface Host {
        fun onSettingsChanged()
    }

    private var binding: SheetSettingsBinding? = null
    private lateinit var preferences: ScanPreferences
    private val checkBoxes = mutableMapOf<CodeFormat, CheckBox>()

    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(::writeBackup) }

    private val importBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::readBackup) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = SheetSettingsBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = ScanPreferences(requireContext())
        val views = binding ?: return

        views.tryHarder.isChecked = preferences.tryHarder
        views.tryInvert.isChecked = preferences.tryInvert
        views.vibrate.isChecked = preferences.vibrateOnRead
        views.beep.isChecked = preferences.beepOnRead
        views.saveHistory.isChecked = preferences.saveHistory
        views.saveSensitive.isChecked = preferences.saveSensitive

        views.tryHarder.setOnCheckedChangeListener { _, on -> preferences.tryHarder = on }
        views.tryInvert.setOnCheckedChangeListener { _, on -> preferences.tryInvert = on }
        views.vibrate.setOnCheckedChangeListener { _, on -> preferences.vibrateOnRead = on }
        views.beep.setOnCheckedChangeListener { _, on -> preferences.beepOnRead = on }
        views.saveHistory.setOnCheckedChangeListener { _, on -> preferences.saveHistory = on }
        views.saveSensitive.setOnCheckedChangeListener { _, on -> preferences.saveSensitive = on }

        views.formatsToggle.setOnClickListener {
            views.formatsPanel.isVisible = !views.formatsPanel.isVisible
        }
        views.presetAll.setOnClickListener { applyPreset(CodeFormat.ALL) }
        views.presetMatrix.setOnClickListener { applyPreset(CodeFormat.MATRIX_ONLY) }
        views.presetQr.setOnClickListener { applyPreset(CodeFormat.QR_ONLY) }
        buildFormatList(views.formats)
        paintFormatsSummary()

        paintRetention()
        views.retention.setOnClickListener { chooseRetention() }
        views.exportBackup.setOnClickListener { exportBackup.launch(BACKUP_FILE_NAME) }
        views.importBackup.setOnClickListener {
            importBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        views.clearHistory.setOnClickListener { confirmClear() }

        views.about.setOnClickListener { AboutSheet().show(parentFragmentManager, "about") }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        notifyHost()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        checkBoxes.clear()
        binding = null
    }

    private fun notifyHost() {
        (activity as? Host)?.onSettingsChanged()
    }

    private fun buildFormatList(host: LinearLayout) {
        val active = preferences.formats()
        val inflater = LayoutInflater.from(host.context)

        for (family in CodeFamily.entries) {
            val header = TextView(host.context).apply {
                setText(labelOf(family))
                TextViewCompat.setTextAppearance(
                    this,
                    com.google.android.material.R.style.TextAppearance_Material3_TitleSmall,
                )
                setPadding(0, dp(16), 0, dp(4))
            }
            host.addView(header)

            for (format in CodeFormat.ALL.filter { it.family == family }) {
                val box = inflater.inflate(R.layout.item_format, host, false) as CheckBox
                box.text = format.label
                box.isChecked = format in active
                box.setOnCheckedChangeListener { _, checked ->
                    if (!checked && checkBoxes.values.none { it.isChecked }) {
                        box.isChecked = true
                        Toast.makeText(requireContext(), R.string.settings_min_one_format, Toast.LENGTH_SHORT)
                            .show()
                        return@setOnCheckedChangeListener
                    }
                    storeFormats()
                }
                checkBoxes[format] = box
                host.addView(box)
            }
        }
    }

    private fun applyPreset(formats: Set<CodeFormat>) {
        for ((format, box) in checkBoxes) {
            box.isChecked = format in formats
        }
        storeFormats()
    }

    private fun storeFormats() {
        val selected = checkBoxes.filterValues { it.isChecked }.keys
        preferences.setFormats(selected)
        paintFormatsSummary()
    }

    private fun paintFormatsSummary() {
        val views = binding ?: return
        val active = checkBoxes.values.count { it.isChecked }
        views.formatsToggle.text = getString(R.string.settings_formats_summary, active, CodeFormat.ALL.size)
    }

    private fun labelOf(family: CodeFamily): Int = when (family) {
        CodeFamily.MATRIX -> R.string.family_matrix
        CodeFamily.RETAIL -> R.string.family_retail
        CodeFamily.INDUSTRIAL -> R.string.family_industrial
    }

    private fun paintRetention() {
        val views = binding ?: return
        val label = when (Retention.ofDays(preferences.retentionDays)) {
            Retention.THIRTY_DAYS -> R.string.retention_thirty
            Retention.NINETY_DAYS -> R.string.retention_ninety
            Retention.FOREVER -> R.string.retention_forever
        }
        views.retention.text = getString(R.string.settings_retention_value, getString(label))
    }

    private fun chooseRetention() {
        val options = arrayOf(
            getString(R.string.retention_thirty),
            getString(R.string.retention_ninety),
            getString(R.string.retention_forever),
        )
        val days = listOf(Retention.THIRTY_DAYS, Retention.NINETY_DAYS, Retention.FOREVER)
        val selected = days.indexOf(Retention.ofDays(preferences.retentionDays))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_retention)
            .setSingleChoiceItems(options, selected) { dialog, index ->
                preferences.retentionDays = days[index].days
                dialog.dismiss()
                paintRetention()
                changeHistory { store -> store.applyRetention(days[index]) }
            }
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.history_clear)
            .setMessage(R.string.history_clear_note)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.history_delete) { _, _ ->
                changeHistory { store -> store.clear(keepFavorites = true) }
            }
            .show()
    }

    /** Cambia el historial fuera del hilo principal y avisa a la lista. */
    private fun changeHistory(change: (HistoryStore) -> Unit) {
        val store = HistoryStore.get(requireContext())
        val host = activity as? Host
        requireActivity().lifecycleScope.launch {
            withContext(Dispatchers.IO) { change(store) }
            host?.onSettingsChanged()
        }
    }

    private fun writeBackup(uri: Uri) {
        val context = requireContext().applicationContext
        requireActivity().lifecycleScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val entries = HistoryStore.get(context).entries(HistoryFilter(), limit = Int.MAX_VALUE)
                    val json = HistoryBackup.export(entries)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            report(context, context.getString(if (written) R.string.export_done else R.string.export_failed))
        }
    }

    private fun readBackup(uri: Uri) {
        val context = requireContext().applicationContext
        val host = activity as? Host
        requireActivity().lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        readLimited(stream)?.toString(Charsets.UTF_8)
                    }
                }.getOrNull() ?: return@withContext null

                when (val result = HistoryBackup.import(json)) {
                    is HistoryBackup.Result.Restored ->
                        HistoryStore.get(context).restoreAll(result.entries) to result.digestMatches
                    else -> null
                }
            }

            if (outcome == null) {
                report(context, context.getString(R.string.history_import_failed))
                return@launch
            }
            host?.onSettingsChanged()
            val (added, digestMatches) = outcome
            val plural = if (digestMatches) R.plurals.history_imported else R.plurals.history_imported_altered
            report(context, context.resources.getQuantityString(plural, added, added))
        }
    }

    /** Sobre el propio panel si sigue abierto; si no, donde se vea. */
    private fun report(context: Context, message: String) {
        val root = binding?.root
        if (root != null) {
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /** Lee el archivo elegido, pero no uno cualquiera entero. */
    private fun readLimited(stream: java.io.InputStream): ByteArray? {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return bytes.toByteArray()
            if (bytes.size() + read > MAX_BACKUP_BYTES) return null
            bytes.write(buffer, 0, read)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "settings"

        private const val BACKUP_FILE_NAME = "signifer-historial.json"
        private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
    }
}
