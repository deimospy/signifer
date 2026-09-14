package org.sarambi.signifer.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import org.sarambi.signifer.R
import org.sarambi.signifer.databinding.ItemHistoryBinding
import org.sarambi.signifer.history.HistoryEntry
import org.sarambi.signifer.ui.titleOf

/** La lista del historial. */
class HistoryAdapter(
    private val onOpen: (HistoryEntry) -> Unit,
    private val onFavorite: (HistoryEntry) -> Unit,
    private val onLongPress: (HistoryEntry) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {
    private var entries: List<HistoryEntry> = emptyList()

    fun submit(next: List<HistoryEntry>) {
        val difference = DiffUtil.calculateDiff(Difference(entries, next))
        entries = next
        difference.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(entries[position])
    }

    inner class Holder(private val views: ItemHistoryBinding) :
        RecyclerView.ViewHolder(views.root) {
        fun bind(entry: HistoryEntry) {
            views.text.text = entry.text.lineSequence().first().take(200)
            views.meta.text = listOfNotNull(
                views.root.context.getString(titleOf(entry.kind)),
                entry.format.label.takeIf { it.isNotEmpty() },
                shortDate(entry.createdAt),
                if (entry.times > 1) "×${entry.times}" else null,
            ).joinToString(" · ")

            views.favorite.setIconResource(if (entry.favorite) R.drawable.ic_star_filled else R.drawable.ic_star)
            views.favorite.alpha = if (entry.favorite) 1f else 0.6f
            views.favorite.setOnClickListener { onFavorite(entry) }
            views.root.setOnClickListener { onOpen(entry) }
            views.root.setOnLongClickListener {
                onLongPress(entry)
                true
            }
        }
    }

    /** Fecha corta. */
    private fun shortDate(millis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private class Difference(
        private val before: List<HistoryEntry>,
        private val after: List<HistoryEntry>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = before.size
        override fun getNewListSize(): Int = after.size
        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            before[oldPosition].id == after[newPosition].id
        override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            before[oldPosition] == after[newPosition]
    }
}
