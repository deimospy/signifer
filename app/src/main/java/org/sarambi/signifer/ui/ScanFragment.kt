package org.sarambi.signifer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import org.sarambi.signifer.R
import org.sarambi.signifer.camera.CameraSession
import org.sarambi.signifer.databinding.FragmentScanBinding
import org.sarambi.signifer.decode.DecodedCode
import org.sarambi.signifer.decode.ScanOptions
import org.sarambi.signifer.decode.ZxingCppScanner
import org.sarambi.signifer.settings.ScanPreferences

/** La pantalla de lectura. */
class ScanFragment : Fragment() {
    private var binding: FragmentScanBinding? = null
    private var session: CameraSession? = null
    private lateinit var preferences: ScanPreferences

    private val scanner = ZxingCppScanner()

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCamera() else showPermissionPanel(denied = true)
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::decodeImage) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = FragmentScanBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = ScanPreferences(requireContext())
        val views = binding ?: return

        views.torch.setOnClickListener {
            val on = session?.toggleTorch() ?: false
            views.torch.isSelected = on
        }
        views.pickImage.setOnClickListener { launchPicker() }
        views.permissionPickImage.setOnClickListener { launchPicker() }
        views.settings.setOnClickListener { ScanSettingsSheet().show(childFragmentManager, null) }
        views.permissionGrant.setOnClickListener {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) startCamera() else requestPermissionOnce()
    }

    override fun onStop() {
        super.onStop()
        session?.stop()
        session = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /** Vuelve a analizar. */
    fun resumeScanning() {
        session?.resumeAnalysis()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissionOnce() {
        showPermissionPanel(denied = false)
        requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val views = binding ?: return
        views.permissionPanel.visibility = View.GONE
        views.frame.visibility = View.VISIBLE

        scanner.options = preferences.scanOptions()
        val camera = CameraSession(
            context = requireContext().applicationContext,
            scanner = scanner,
            onCode = ::onCodeRead,
        )
        session = camera
        camera.start(viewLifecycleOwner, views.preview) { failure ->
            binding?.let {
                Snackbar.make(it.root, R.string.camera_unavailable, Snackbar.LENGTH_LONG).show()
            }
            failure.printStackTrace()
        }
        views.torch.isEnabled = true
    }

    private fun showPermissionPanel(denied: Boolean) {
        val views = binding ?: return
        views.permissionPanel.visibility = View.VISIBLE
        views.frame.visibility = View.GONE
        views.permissionBody.setText(
            if (denied) R.string.scan_permission_denied else R.string.scan_permission_body,
        )
    }

    private fun launchPicker() {
        pickImage.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build(),
        )
    }

    private fun decodeImage(uri: Uri) {
        val views = binding ?: return
        val bitmap = runCatching {
            requireContext().contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()

        if (bitmap == null) {
            Snackbar.make(views.root, R.string.image_unreadable, Snackbar.LENGTH_LONG).show()
            return
        }

        val stillScanner = ZxingCppScanner(ScanOptions.STILL.copy(formats = preferences.formats()))
        val codes = stillScanner.decode(bitmap)
        bitmap.recycle()

        if (codes.isEmpty()) {
            Snackbar.make(views.root, R.string.image_without_code, Snackbar.LENGTH_LONG).show()
        } else {
            onCodeRead(codes.first())
        }
    }

    private fun onCodeRead(code: DecodedCode) {
        session?.pauseAnalysis()
        (activity as? CodeSink)?.onCodeRead(code)
    }

    /** Quien recibe una lectura. */
    interface CodeSink {
        fun onCodeRead(code: DecodedCode)
    }
}
