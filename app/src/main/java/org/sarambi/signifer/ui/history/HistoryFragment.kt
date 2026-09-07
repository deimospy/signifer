package org.sarambi.signifer.ui.history

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sarambi.signifer.R
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.databinding.FragmentHistoryBinding
import org.sarambi.signifer.history.HistoryBackup
import org.sarambi.signifer.history.HistoryEntry
import org.sarambi.signifer.history.HistoryFilter
import org.sarambi.signifer.history.HistoryOrigin
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.history.Retention
import org.sarambi.signifer.settings.ScanPreferences
import org.sarambi.signifer.ui.ResultSheet
import org.sarambi.signifer.ui.titleOf

/** El historial. */
class HistoryFragment : Fragment() {
    private var binding: FragmentHistoryBinding? = null
    private lateinit var store: HistoryStore
    private lateinit var preferences: ScanPreferences
    private lateinit var adapter: HistoryAdapter

    private var filter = HistoryFilter()
    private var reload: Job? = null

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
        val views = FragmentHistoryBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = HistoryStore(requireContext())
        preferences = ScanPreferences(requireContext())
        val views = binding ?: return

        adapter = HistoryAdapter(
            onOpen = ::open,
            onFavorite = ::toggleFavorite,
            onLongPress = ::confirmDelete,
        )
        views.list.layoutManager = LinearLayoutManager(requireContext())
        views.list.adapter = adapter

        buildFilterChips(views)
        views.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(text: Editable?) {
                filter = filter.copy(query = text?.toString().orEmpty())
                refresh(SEARCH_DELAY_MILLIS)
            }
        })
        views.menu.setOnClickListener { showMenu() }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                store.applyRetention(Retention.ofDays(preferences.retentionDays))
            }
            refresh()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reload?.cancel()
        store.close()
        binding = null
    }

    private fun refresh(delayMillis: Long = 0) {
        reload?.cancel()
        reload = viewLifecycleOwner.lifecycleScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val current = filter
            val entries = withContext(Dispatchers.IO) { store.entries(current) }
            val views = binding ?: return@launch
            adapter.submit(entries)
            val emptiness = if (entries.isEmpty()) View.VISIBLE else View.GONE
            views.empty.visibility = emptiness
            views.emptyMark.visibility = emptiness
            views.empty.setText(
                if (current.isEmpty) R.string.history_empty else R.string.history_no_matches,
            )
        }
    }

    private fun buildFilterChips(views: FragmentHistoryBinding) {
        val favorites = Chip(requireContext()).apply {
            setText(R.string.history_only_favorites)
            isCheckable = true
            setOnCheckedChangeListener { _, checked ->
                filter = filter.copy(onlyFavorites = checked)
                refresh()
            }
        }
        views.filters.addView(favorites)

        for ((label, origin) in listOf(
            R.string.history_only_scanned to HistoryOrigin.SCANNED,
            R.string.history_only_created to HistoryOrigin.CREATED,
        )) {
            val chip = Chip(requireContext()).apply {
                setText(label)
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    filter = filter.copy(
                        origins = if (checked) filter.origins + origin else filter.origins - origin,
                    )
                    refresh()
                }
            }
            views.filters.addView(chip)
        }

        for (kind in ContentKind.entries) {
            val chip = Chip(requireContext()).apply {
                setText(titleOf(kind))
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    filter = filter.copy(
                        kinds = if (checked) filter.kinds + kind else filter.kinds - kind,
                    )
                    refresh()
                }
            }
            views.filters.addView(chip)
        }
    }

    private fun open(entry: HistoryEntry) {
        ResultSheet.of(entry.text, entry.format).show(parentFragmentManager, "result")
    }

    private fun toggleFavorite(entry: HistoryEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { store.setFavorite(entry.id, !entry.favorite) }
            refresh()
        }
    }

    private fun confirmDelete(entry: HistoryEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.history_delete_one)
            .setMessage(entry.text.take(120))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.history_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.delete(entry.id) }
                    refresh()
                }
            }
            .show()
    }

    private fun showMenu() {
        val options = arrayOf(
            getString(R.string.history_export),
            getString(R.string.history_import),
            getString(R.string.history_retention),
            getString(R.string.history_clear),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_filter)
            .setItems(options) { _, index ->
                when (index) {
                    0 -> exportBackup.launch("signifer-historial.json")
                    1 -> importBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                    2 -> chooseRetention()
                    3 -> confirmClear()
                }
            }
            .show()
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
            .setMessage(R.string.retention_note)
            .setSingleChoiceItems(options, selected) { dialog, index ->
                preferences.retentionDays = days[index].days
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.applyRetention(days[index]) }
                    refresh()
                }
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
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.clear(keepFavorites = true) }
                    refresh()
                }
            }
            .show()
    }

    private fun writeBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val entries = store.entries(HistoryFilter(), limit = Int.MAX_VALUE)
                    val json = HistoryBackup.export(entries)
                    requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            report(if (written) R.string.export_done else R.string.export_failed)
        }
    }

    private fun readBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val json = runCatching {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull() ?: return@withContext null

                when (val result = HistoryBackup.import(json)) {
                    is HistoryBackup.Result.Restored -> {
                        var added = 0
                        for (entry in result.entries) {
                            if (store.contains(entry.text, entry.format)) continue
                            store.restore(entry)
                            added += 1
                        }
                        added to result.digestMatches
                    }
                    else -> null
                }
            }

            if (outcome == null) {
                report(R.string.history_import_failed)
                return@launch
            }
            refresh()
            val (added, digestMatches) = outcome
            val views = binding ?: return@launch
            val plural = if (digestMatches) {
                R.plurals.history_imported
            } else {
                R.plurals.history_imported_altered
            }
            val message = resources.getQuantityString(plural, added, added)
            Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun report(message: Int) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    private companion object {
        /** Un respiro para que escribir cuatro letras consulte una vez, no cuatro. */
        const val SEARCH_DELAY_MILLIS = 180L
    }
}
