package org.sarambi.signifer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.sarambi.signifer.R
import org.sarambi.signifer.databinding.SheetAboutBinding

/** La ficha de la aplicacion. */
class AboutSheet : BottomSheetDialogFragment() {
    private var binding: SheetAboutBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = SheetAboutBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.close).setOnClickListener { dismiss() }
        val views = binding ?: return

        views.version.text = getString(R.string.about_version, versionName())

        for (promise in PROMISES) {
            views.promises.addView(row(views.promises, promise.first, promise.second, R.drawable.ic_check))
        }
        for (fact in FACTS) {
            views.facts.addView(row(views.facts, fact.title, fact.body, fact.icon))
        }

        views.github.setOnClickListener { openGitHub() }
        views.licenses.setOnClickListener { showLicenses() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private data class Fact(@StringRes val title: Int, @StringRes val body: Int, @DrawableRes val icon: Int)

    private fun row(
        host: LinearLayout,
        @StringRes title: Int,
        @StringRes body: Int,
        @DrawableRes icon: Int,
    ): View {
        val view = layoutInflater.inflate(R.layout.item_promise, host, false)
        view.findViewById<TextView>(R.id.title).setText(title)
        view.findViewById<TextView>(R.id.body).setText(body)
        val image = view.findViewById<ImageView>(R.id.icon)
        image.setImageResource(icon)
        image.setColorFilter(ContextCompat.getColor(requireContext(), R.color.risk_none))
        return view
    }

    /** Las licencias del software que la aplicacion enlaza. */
    /** Lo abre el navegador o la aplicacion de GitHub; la app no necesita permiso de red. */
    private fun openGitHub() {
        val intent = Intent(Intent.ACTION_VIEW, getString(R.string.about_github_url).toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.action_no_handler, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLicenses() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about_licenses)
            .setMessage(resources.openRawResource(R.raw.third_party_licenses).bufferedReader().use { it.readText() })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun versionName(): String = runCatching {
        requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")

    companion object {
        const val TAG = "about"

        /** Lo que la aplicacion promete, con el motivo por el que se comprueba. */
        private val PROMISES = listOf(
            R.string.about_offline to R.string.about_offline_body,
            R.string.about_no_tracking to R.string.about_no_tracking_body,
            R.string.about_no_ads to R.string.about_no_ads_body,
            R.string.about_no_storage to R.string.about_no_storage_body,
        )

        /** Quien la hizo y bajo que licencia. */
        private val FACTS = listOf(
            Fact(R.string.about_author, R.string.about_author_name, R.drawable.ic_author),
            Fact(R.string.about_license, R.string.about_license_name, R.drawable.ic_license),
        )
    }
}
