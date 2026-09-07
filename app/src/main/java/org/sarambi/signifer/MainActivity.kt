package org.sarambi.signifer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.databinding.ActivityMainBinding
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.decode.DecodedCode
import org.sarambi.signifer.history.HistoryOrigin
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.settings.ScanPreferences
import org.sarambi.signifer.system.LegacyScanIntent
import org.sarambi.signifer.ui.ResultSheet
import org.sarambi.signifer.ui.ScanFragment
import org.sarambi.signifer.ui.create.CreateFragment
import org.sarambi.signifer.ui.history.HistoryFragment

/** La unica actividad. */
class MainActivity : AppCompatActivity(), ScanFragment.CodeSink, ResultSheet.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: ScanPreferences

    /** Si la lectura hay que devolverla a quien la pidio. */
    private var returningResult = false

    /** El destino que se esta viendo. */
    private var currentTag: String? = null

    override var requestedFormats: Set<CodeFormat>? = null
        private set

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

        currentTag = savedInstanceState?.getString(STATE_TAG)
        if (savedInstanceState == null) {
            binding.navigation.selectedItemId = R.id.destination_scan
            handle(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAG, currentTag)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Que hacer con quien nos abre. */
    private fun handle(intent: Intent?) {
        if (intent == null) return

        if (LegacyScanIntent.matches(intent)) {
            returningResult = true
            requestedFormats = LegacyScanIntent.requestedFormats(intent)
            binding.navigation.selectedItemId = R.id.destination_scan
            binding.navigation.visibility = View.GONE
            return
        }

        returningResult = false
        requestedFormats = null
        binding.navigation.visibility = View.VISIBLE

        when (intent.action) {
            ACTION_CREATE -> binding.navigation.selectedItemId = R.id.destination_create
            ACTION_HISTORY -> binding.navigation.selectedItemId = R.id.destination_history
            ACTION_SCAN -> binding.navigation.selectedItemId = R.id.destination_scan
            Intent.ACTION_SEND, Intent.ACTION_VIEW -> sharedImage(intent)?.let { uri ->
                binding.navigation.selectedItemId = R.id.destination_scan
                binding.container.post { scanFragment()?.decodeUri(uri) }
            }
        }
    }

    private fun sharedImage(intent: Intent): Uri? {
        if (intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: intent.data
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
        }
    }

    /** Cambia de destino sin destruir el anterior. */
    private fun show(tag: String) {
        if (currentTag == tag) return
        val manager = supportFragmentManager

        val transaction = manager.beginTransaction()
        transaction.setReorderingAllowed(true)
        currentTag?.let { previous ->
            manager.findFragmentByTag(previous)?.let(transaction::hide)
        }

        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.container, create(tag), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()
        currentTag = tag
    }

    private fun create(tag: String): Fragment = when (tag) {
        TAG_CREATE -> CreateFragment()
        TAG_HISTORY -> HistoryFragment()
        else -> ScanFragment()
    }

    override fun onCodeRead(code: DecodedCode) {
        if (preferences.vibrateOnRead) vibrate()

        if (returningResult) {
            setResult(RESULT_OK, LegacyScanIntent.result(code.text, code.format, code.bytes))
            finish()
            return
        }

        if (supportFragmentManager.findFragmentByTag(TAG_RESULT) != null) return
        remember(code)
        ResultSheet.of(code.text, code.format).show(supportFragmentManager, TAG_RESULT)
    }

    /** Guarda la lectura, si toca. */
    private fun remember(code: DecodedCode) {
        if (!preferences.saveHistory) return
        val content = parseContent(code.text)
        if (content.isSensitive && !preferences.saveSensitive) return
        store(code.text, code.format)
    }

    override fun onResultDismissed() {
        scanFragment()?.resumeScanning()
    }

    override fun onSaveRequested(content: CodeContent, format: CodeFormat) {
        store(content.encode(), format)
    }

    private fun store(text: String, format: CodeFormat) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val store = HistoryStore(applicationContext)
                store.save(text, format, HistoryOrigin.SCANNED)
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
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    HAPTIC_MILLIS,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HAPTIC_MILLIS)
        }
    }

    companion object {
        const val ACTION_SCAN = "org.sarambi.signifer.action.SCAN"
        const val ACTION_CREATE = "org.sarambi.signifer.action.CREATE"
        const val ACTION_HISTORY = "org.sarambi.signifer.action.HISTORY"

        private const val TAG_SCAN = "scan"
        private const val TAG_CREATE = "create"
        private const val TAG_HISTORY = "history"
        private const val TAG_RESULT = "result"
        private const val STATE_TAG = "destination"

        private const val HAPTIC_MILLIS = 40L
    }
}
