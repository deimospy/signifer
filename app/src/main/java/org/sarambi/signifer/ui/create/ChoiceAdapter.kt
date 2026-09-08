package org.sarambi.signifer.ui.create

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.sarambi.signifer.R

/** Un desplegable que explica cada opcion en lugar de nombrarla. */
class ChoiceAdapter(
    context: Context,
    private val choices: List<Choice>,
) : ArrayAdapter<ChoiceAdapter.Choice>(context, 0, choices) {
    data class Choice(
        @StringRes val title: Int,
        @StringRes val subtitle: Int,
        @DrawableRes val icon: Int,
    )

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent)

    private fun bind(position: Int, reused: View?, parent: ViewGroup): View {
        val view = reused ?: LayoutInflater.from(context)
            .inflate(R.layout.item_choice, parent, false)
        val choice = choices[position]
        view.findViewById<ImageView>(R.id.icon).setImageResource(choice.icon)
        view.findViewById<TextView>(R.id.title).setText(choice.title)
        view.findViewById<TextView>(R.id.subtitle).setText(choice.subtitle)
        return view
    }

    /** Lo que se escribe en el campo cuando se elige: solo el titulo. */
    fun titleOf(position: Int): Int = choices[position].title
}
