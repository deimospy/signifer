package org.sarambi.signifer

import android.app.Application
import com.google.android.material.color.DynamicColors

/** Arranque de la aplicacion. */
class SigniferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
