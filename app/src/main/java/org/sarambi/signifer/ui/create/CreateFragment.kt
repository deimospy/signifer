package org.sarambi.signifer.ui.create

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sarambi.signifer.R
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.content.validate
import org.sarambi.signifer.databinding.FragmentCreateBinding
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.encode.BitmapRenderer
import org.sarambi.signifer.encode.Correction
import org.sarambi.signifer.encode.WRITABLE_FORMATS
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter
import org.sarambi.signifer.encode.quietModulesFor
import org.sarambi.signifer.encode.toSvg
import org.sarambi.signifer.history.HistoryOrigin
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.settings.ScanPreferences
import org.sarambi.signifer.ui.titleOf

/** La pantalla de creacion. */
class CreateFragment : Fragment() {
    private var binding: FragmentCreateBinding? = null
    private val writer = ZxingCoreWriter()

    private var kind = ContentKind.WEBSITE
    private var format = CodeFormat.QR_CODE
    private var correction = Correction.MEDIUM
    private val values = mutableMapOf<String, String>()

    private var current: CodeContent? = null
    private var currentMatrix: org.sarambi.signifer.encode.CodeMatrix? = null

    private val exportPng = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> uri?.let { writePng(it, pendingSize) } }

    private val exportSvg = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/svg+xml"),
    ) { uri -> uri?.let { writeSvg(it) } }

    private var pendingSize = EXPORT_SIZES.first()
    private var preview: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = FragmentCreateBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return

        buildKindChips(views)
        buildFormatChooser(views)
        buildCorrectionChooser(views)
        rebuildForm()

        views.share.setOnClickListener { share() }
        views.export.setOnClickListener { chooseExport() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun buildKindChips(views: FragmentCreateBinding) {
        for (candidate in ContentKind.entries) {
            val chip = Chip(requireContext()).apply {
                setText(titleOf(candidate))
                isCheckable = true
                isChecked = candidate == kind
                setOnClickListener {
                    if (kind != candidate) {
                        kind = candidate
                        values.clear()
                        rebuildForm()
                    }
                    isChecked = true
                }
            }
            views.kinds.addView(chip)
        }
    }

    private fun buildFormatChooser(views: FragmentCreateBinding) {
        val labels = WRITABLE_FORMATS.map { it.label }
        views.format.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels),
        )
        views.format.setText(format.label, false)
        views.format.setOnItemClickListener { _, _, position, _ ->
            format = WRITABLE_FORMATS[position]
            refreshCorrectionVisibility()
            refresh()
        }
    }

    private fun buildCorrectionChooser(views: FragmentCreateBinding) {
        val labels = CORRECTIONS.map { getString(it.second) }
        views.correction.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels),
        )
        views.correction.setText(getString(CORRECTIONS[1].second), false)
        views.correction.setOnItemClickListener { _, _, position, _ ->
            correction = CORRECTIONS[position].first
            refresh()
        }
        refreshCorrectionVisibility()
    }

    /** Solo QR y Aztec tienen nivel de correccion que elegir. */
    private fun refreshCorrectionVisibility() {
        val supported = format == CodeFormat.QR_CODE || format == CodeFormat.AZTEC
        binding?.correctionLayout?.visibility = if (supported) View.VISIBLE else View.GONE
    }

    private fun rebuildForm() {
        val views = binding ?: return
        views.form.removeAllViews()
        for (spec in fieldsFor(kind)) {
            if (!isVisible(spec, values)) continue
            views.form.addView(buildField(views.form, spec))
        }
        refresh()
    }

    private fun buildField(host: LinearLayout, spec: FieldSpec): View = when (spec.type) {
        FieldType.SWITCH -> MaterialSwitch(host.context).apply {
            setText(spec.label)
            minHeight = dp(48)
            isChecked = values[spec.key] == "true"
            setOnCheckedChangeListener { _, checked ->
                values[spec.key] = checked.toString()
                rebuildForm()
            }
        }

        FieldType.CHOICE -> inflateText(host, spec).also { layout ->
            val input = layout.editText as MaterialAutoCompleteTextView
            val labels = spec.choices.map { getString(it) }
            input.setAdapter(
                ArrayAdapter(host.context, android.R.layout.simple_list_item_1, labels),
            )
            val selected = values[spec.key]?.toIntOrNull() ?: 0
            input.setText(labels.getOrElse(selected) { "" }, false)
            input.setOnItemClickListener { _, _, position, _ ->
                values[spec.key] = position.toString()
                rebuildForm()
            }
        }

        FieldType.DATE, FieldType.TIME -> inflateText(host, spec).also { layout ->
            val input = layout.editText ?: return@also
            input.setText(values[spec.key].orEmpty())
            input.isFocusable = false
            input.setOnClickListener {
                if (spec.type == FieldType.DATE) pickDate(spec) else pickTime(spec)
            }
        }

        else -> inflateText(host, spec).also { layout ->
            val input = layout.editText ?: return@also
            input.inputType = inputTypeOf(spec.type)
            if (spec.type == FieldType.MULTILINE) {
                input.setLines(3)
                input.maxLines = 6
            }
            input.setText(values[spec.key].orEmpty())
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(text: Editable?) {
                    values[spec.key] = text?.toString().orEmpty()
                    refresh()
                }
            })
        }
    }

    private fun inflateText(host: LinearLayout, spec: FieldSpec): TextInputLayout {
        val resource = when (spec.type) {
            FieldType.CHOICE, FieldType.DATE, FieldType.TIME -> R.layout.item_field_choice
            else -> R.layout.item_field_text
        }
        val layout = layoutInflater.inflate(resource, host, false) as TextInputLayout
        layout.hint = getString(spec.label)
        layout.tag = spec.key
        return layout
    }

    private fun inputTypeOf(type: FieldType): Int = when (type) {
        FieldType.MULTILINE ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        FieldType.URL -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        FieldType.EMAIL -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        FieldType.PHONE -> InputType.TYPE_CLASS_PHONE
        FieldType.NUMBER -> InputType.TYPE_CLASS_NUMBER
        FieldType.DECIMAL ->
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        FieldType.PASSWORD ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else -> InputType.TYPE_CLASS_TEXT
    }

    private fun pickDate(spec: FieldSpec) {
        val now = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                values[spec.key] = "%04d-%02d-%02d".format(year, month + 1, day)
                rebuildForm()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun pickTime(spec: FieldSpec) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                values[spec.key] = "%02d:%02d".format(hour, minute)
                rebuildForm()
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true,
        ).show()
    }

    /** Vuelve a dibujar la vista previa. */
    private fun refresh() {
        val views = binding ?: return
        val content = contentFrom(kind, values)
        current = content

        preview?.cancel()
        if (content.validate().isNotEmpty()) {
            showProblem(views, getString(R.string.create_incomplete))
            return
        }

        val payload = content.encode()
        val chosenFormat = format
        val chosenCorrection = correction

        preview = viewLifecycleOwner.lifecycleScope.launch {
            delay(PREVIEW_DELAY_MILLIS)
            val result = withContext(Dispatchers.Default) {
                writer.write(chosenFormat, payload, chosenCorrection)
            }
            val current = binding ?: return@launch
            when (result) {
                is WriteResult.Written -> {
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapRenderer.render(result.matrix, chosenFormat, PREVIEW_PIXELS)
                    }
                    currentMatrix = result.matrix
                    current.problem.visibility = View.GONE
                    current.previewPlaceholder.visibility = View.GONE
                    current.preview.visibility = View.VISIBLE
                    current.preview.setImageBitmap(bitmap)
                }
                is WriteResult.Rejected -> showProblem(current, describe(result))
                WriteResult.Failed -> showProblem(current, getString(R.string.create_failed))
            }
        }
    }

    private fun showProblem(views: FragmentCreateBinding, message: String) {
        currentMatrix = null
        views.preview.setImageDrawable(null)
        views.preview.visibility = View.GONE
        views.previewPlaceholder.visibility = View.VISIBLE
        views.problem.visibility = View.VISIBLE
        views.problem.text = message
    }

    private fun describe(rejected: WriteResult.Rejected): String {
        val lengths = rejected.check.expectedLengths.joinToString(getString(R.string.list_or))
        return when (rejected.check.problem) {
            org.sarambi.signifer.encode.PayloadProblem.EMPTY ->
                getString(R.string.create_incomplete)
            org.sarambi.signifer.encode.PayloadProblem.NOT_NUMERIC ->
                getString(R.string.problem_not_numeric, format.label)
            org.sarambi.signifer.encode.PayloadProblem.WRONG_LENGTH ->
                getString(R.string.problem_wrong_length, format.label, lengths)
            org.sarambi.signifer.encode.PayloadProblem.ODD_LENGTH ->
                getString(R.string.problem_odd_length)
            org.sarambi.signifer.encode.PayloadProblem.UNSUPPORTED_CHARACTER ->
                getString(R.string.problem_unsupported_character, format.label)
            org.sarambi.signifer.encode.PayloadProblem.BAD_CHECK_DIGIT ->
                getString(R.string.problem_check_digit)
            org.sarambi.signifer.encode.PayloadProblem.TOO_LONG ->
                getString(R.string.problem_too_long, format.label)
            org.sarambi.signifer.encode.PayloadProblem.NOT_WRITABLE, null ->
                getString(R.string.create_failed)
        }
    }

    private fun chooseExport() {
        if (currentMatrix == null) return
        val options = EXPORT_SIZES.map { getString(R.string.export_png, it) } +
            getString(R.string.export_svg)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_export)
            .setItems(options.toTypedArray()) { _, index ->
                if (index < EXPORT_SIZES.size) {
                    pendingSize = EXPORT_SIZES[index]
                    exportPng.launch(fileName("png"))
                } else {
                    exportSvg.launch(fileName("svg"))
                }
            }
            .show()
    }

    /** Nombre del archivo. */
    private fun fileName(extension: String): String {
        val stamp = Calendar.getInstance().let {
            "%04d%02d%02d-%02d%02d%02d".format(
                it.get(Calendar.YEAR),
                it.get(Calendar.MONTH) + 1,
                it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.HOUR_OF_DAY),
                it.get(Calendar.MINUTE),
                it.get(Calendar.SECOND),
            )
        }
        return "signifer-${format.name.lowercase()}-$stamp.$extension"
    }

    private fun writePng(uri: Uri, size: Int) {
        val matrix = currentMatrix ?: return
        val bitmap = BitmapRenderer.render(matrix, format, size)
        val written = runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: false
        }.getOrDefault(false)
        bitmap.recycle()
        report(written)
    }

    private fun writeSvg(uri: Uri) {
        val matrix = currentMatrix ?: return
        val svg = matrix.toSvg(quietModules = quietModulesFor(format))
        val written = runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(svg.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        }.getOrDefault(false)
        report(written)
    }

    private fun report(written: Boolean) {
        val views = binding ?: return
        val message = if (written) R.string.export_done else R.string.export_failed
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
        if (written) remember()
    }

    /** Guarda en el historial lo que se acaba de crear. */
    private fun remember() {
        val content = current ?: return
        val preferences = ScanPreferences(requireContext())
        if (!preferences.saveHistory) return
        if (content.isSensitive && !preferences.saveSensitive) return

        val payload = content.encode()
        val chosenFormat = format
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val store = HistoryStore(requireContext().applicationContext)
                store.save(payload, chosenFormat, HistoryOrigin.CREATED)
                store.close()
            }
        }
    }

    /** Compartir. */
    private fun share() {
        val matrix = currentMatrix ?: return
        val bitmap = BitmapRenderer.render(matrix, format, EXPORT_SIZES.first())
        val uri = SharedImages.write(requireContext(), bitmap, "signifer.png")
        bitmap.recycle()

        if (uri == null) {
            report(false)
            return
        }
        SharedImages.share(requireContext(), uri)
        remember()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREVIEW_PIXELS = 512

        /** Respiro antes de redibujar. */
        const val PREVIEW_DELAY_MILLIS = 120L
        val EXPORT_SIZES = listOf(512, 1024, 2048)

        val CORRECTIONS = listOf(
            Correction.LOW to R.string.correction_low,
            Correction.MEDIUM to R.string.correction_medium,
            Correction.QUARTILE to R.string.correction_quartile,
            Correction.HIGH to R.string.correction_high,
        )
    }
}
