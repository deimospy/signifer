package org.sarambi.signifer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.sarambi.signifer.R
import org.sarambi.signifer.databinding.SheetScanSettingsBinding
import org.sarambi.signifer.decode.CodeFamily
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.settings.ScanPreferences

/** Los ajustes de lectura. */
class ScanSettingsSheet : BottomSheetDialogFragment() {
    private var binding: SheetScanSettingsBinding? = null
    private lateinit var preferences: ScanPreferences
    private val checkBoxes = mutableMapOf<CodeFormat, CheckBox>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = SheetScanSettingsBinding.inflate(inflater, container, false)
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

        views.presetAll.setOnClickListener { applyPreset(CodeFormat.ALL) }
        views.presetMatrix.setOnClickListener { applyPreset(CodeFormat.MATRIX_ONLY) }
        views.presetQr.setOnClickListener { applyPreset(CodeFormat.QR_ONLY) }

        views.about.setOnClickListener { AboutSheet().show(parentFragmentManager, "about") }

        buildFormatList(views.formats)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (parentFragment as? ScanFragment)?.reloadOptions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        checkBoxes.clear()
        binding = null
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
                box.setOnCheckedChangeListener { _, _ -> storeFormats() }
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
    }

    private fun labelOf(family: CodeFamily): Int = when (family) {
        CodeFamily.MATRIX -> R.string.family_matrix
        CodeFamily.RETAIL -> R.string.family_retail
        CodeFamily.INDUSTRIAL -> R.string.family_industrial
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
