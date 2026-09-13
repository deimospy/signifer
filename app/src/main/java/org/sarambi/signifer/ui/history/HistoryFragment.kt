package org.sarambi.signifer.ui.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sarambi.signifer.R
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.databinding.FragmentHistoryBinding
import org.sarambi.signifer.history.HistoryEntry
import org.sarambi.signifer.history.HistoryFilter
import org.sarambi.signifer.history.HistoryOrigin
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.history.Retention
import org.sarambi.signifer.settings.ScanPreferences
import org.sarambi.signifer.ui.ResultSheet
import org.sarambi.signifer.ui.SettingsSheet
import org.sarambi.signifer.ui.titleOf

/** El historial. */
class HistoryFragment : Fragment() {
    private var binding: FragmentHistoryBinding? = null
    private lateinit var store: HistoryStore
    private lateinit var preferences: ScanPreferences
    private lateinit var adapter: HistoryAdapter

    private var filter = HistoryFilter()
    private var reload: Job? = null

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
        store = HistoryStore.get(requireContext())
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
        views.settings.setOnClickListener { SettingsSheet().show(parentFragmentManager, SettingsSheet.TAG) }
    }

    override fun onStart() {
        super.onStart()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                store.applyRetention(Retention.ofDays(preferences.retentionDays))
            }
            refresh()
        }
    }

    /**
     * Los destinos se ocultan en lugar de destruirse, y ocultar no pasa por `onStart`: sin esto, lo
     * leido mientras se miraba otra pestana no aparecia al volver.
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reload?.cancel()
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
        ResultSheet.of(entry.text, entry.format, fromHistory = true)
            .show(parentFragmentManager, "result")
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

    /** La llama la actividad cuando el panel de ajustes cambio algo del historial. */
    fun onSettingsChanged() {
        if (view != null) refresh()
    }

    private companion object {
        /** Un respiro para que escribir cuatro letras consulte una vez, no cuatro. */
        const val SEARCH_DELAY_MILLIS = 180L
    }
}
