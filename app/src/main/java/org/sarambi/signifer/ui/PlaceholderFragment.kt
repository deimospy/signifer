package org.sarambi.signifer.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Hueco de una pantalla que todavia no existe. */
class PlaceholderFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = TextView(requireContext()).apply {
        gravity = Gravity.CENTER
        text = requireArguments().getString(ARGUMENT_LABEL).orEmpty()
    }

    companion object {
        private const val ARGUMENT_LABEL = "label"

        fun of(label: String): PlaceholderFragment = PlaceholderFragment().apply {
            arguments = Bundle().apply { putString(ARGUMENT_LABEL, label) }
        }
    }
}
