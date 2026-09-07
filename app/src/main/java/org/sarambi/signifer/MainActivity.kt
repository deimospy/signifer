package org.sarambi.signifer

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.history.HistoryOrigin
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.databinding.ActivityMainBinding
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.decode.DecodedCode
import org.sarambi.signifer.settings.ScanPreferences
import org.sarambi.signifer.ui.create.CreateFragment
import org.sarambi.signifer.ui.history.HistoryFragment
import org.sarambi.signifer.ui.ResultSheet
import org.sarambi.signifer.ui.ScanFragment

/** La unica actividad. */
class MainActivity : AppCompatActivity(), ScanFragment.CodeSink, ResultSheet.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: ScanPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = ScanPreferences(this)

        binding.navigation.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.destination_create -> TAG_CREATE
                    R.id.destination_history -> TAG_HISTORY
                    else -> TAG_SCAN
                },
            )
            true
        }

        if (savedInstanceState == null) {
            binding.navigation.selectedItemId = R.id.destination_scan
        }
    }

    /** Cambia de destino sin destruir el anterior. */
    private fun show(tag: String) {
        val manager = supportFragmentManager
        val current = manager.fragments.firstOrNull { !it.isHidden && it.tag != null }
        if (current?.tag == tag) return

        val transaction = manager.beginTransaction()
        transaction.setReorderingAllowed(true)
        current?.let { transaction.hide(it) }

        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.container, create(tag), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()
    }

    private fun create(tag: String): Fragment = when (tag) {
        TAG_CREATE -> CreateFragment()
        TAG_HISTORY -> HistoryFragment()
        else -> ScanFragment()
    }

    override fun onCodeRead(code: DecodedCode) {
        if (supportFragmentManager.findFragmentByTag(TAG_RESULT) != null) return

        if (preferences.vibrateOnRead) vibrate()
        remember(code)
        ResultSheet.of(code.text, code.format).show(supportFragmentManager, TAG_RESULT)
    }

    /** Guarda la lectura, si toca. */
    private fun remember(code: DecodedCode) {
        if (!preferences.saveHistory) return
        val content = parseContent(code.text)
        if (content.isSensitive && !preferences.saveSensitive) return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val store = HistoryStore(applicationContext)
                store.save(code.text, code.format, HistoryOrigin.SCANNED)
                store.close()
            }
        }
    }

    override fun onResultDismissed() {
        scanFragment()?.resumeScanning()
    }

    override fun onSaveRequested(content: CodeContent, format: CodeFormat) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val store = HistoryStore(applicationContext)
                store.save(content.encode(), format, HistoryOrigin.SCANNED)
                store.close()
            }
        }
    }

    private fun scanFragment(): ScanFragment? =
        supportFragmentManager.findFragmentByTag(TAG_SCAN) as? ScanFragment

    /** Un toque corto al leer. */
    private fun vibrate() {
        runCatching { doVibrate() }
    }

    private fun doVibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_MILLIS, DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HAPTIC_MILLIS)
        }
    }

    private companion object {
        const val TAG_SCAN = "scan"
        const val TAG_CREATE = "create"
        const val TAG_HISTORY = "history"
        const val TAG_RESULT = "result"

        const val HAPTIC_MILLIS = 40L
        const val DEFAULT_AMPLITUDE = VibrationEffect.DEFAULT_AMPLITUDE
    }
}
