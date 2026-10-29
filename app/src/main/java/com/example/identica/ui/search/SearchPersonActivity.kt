package com.example.identica.ui.search

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.net.Uri
import android.view.animation.DecelerateInterpolator
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.identica.R
import com.example.identica.data.MatchType
import com.example.identica.databinding.ActivitySearchPersonBinding
import com.example.identica.ui.common.BaseSecureActivity
import com.example.identica.ui.common.CameraCapture
import com.example.identica.ui.scanner.LaserScanController
import com.example.identica.util.BitmapUtils
import kotlinx.coroutines.launch
import java.io.File

class SearchPersonActivity : BaseSecureActivity() {

    private lateinit var binding: ActivitySearchPersonBinding
    private lateinit var laserController: LaserScanController

    private val viewModel: SearchViewModel by viewModels { SearchViewModel.factory(app.repository) }

    private var probeFile: File? = null
    private var displayedMetric: Int = 0
    private var metricAnimator: ValueAnimator? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importProbeFile(uri)
    }

    private val camera = CameraCapture(
        activity = this,
        targetFile = { File(File(cacheDir, "probes"), "probe_${System.currentTimeMillis()}.jpg") },
        onCaptured = ::onProbePhotoCaptured,
        onFailed = ::showMessage
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        laserController = LaserScanController(binding.scannerOverlay)

        binding.scanTabs.addTab(binding.scanTabs.newTab().setText("FACE SCAN"))
        binding.scanTabs.addTab(binding.scanTabs.newTab().setText("FINGERPRINT"))
        binding.scanTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                binding.probePreview.setImageDrawable(null)
                val fingerprintMode = tab.position == 1
                binding.fingerprintScanView.isVisible = fingerprintMode
                if (fingerprintMode) binding.fingerprintScanView.startScan() else binding.fingerprintScanView.stopScan()
                viewModel.selectMode(if (fingerprintMode) MatchType.FINGERPRINT else MatchType.PHOTO)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
        binding.captureProbeButton.setOnClickListener {
            if (viewModel.state.value.mode == MatchType.PHOTO) {
                camera.capture()
            } else {
                authenticateFingerprint()
            }
        }
        binding.startScanButton.setOnClickListener { viewModel.startScan() }
        binding.uploadProbeButton.setOnClickListener { filePicker.launch(if (viewModel.state.value.mode == MatchType.PHOTO) "image/*" else "image/*") }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.state.value.scanning) laserController.start()
    }

    override fun onPause() {
        laserController.stop()
        super.onPause()
    }

    override fun onDestroy() {
        metricAnimator?.cancel()
        probeFile?.delete()
        super.onDestroy()
    }

    private fun importProbeFile(uri: Uri) {
        try {
            val dir = File(cacheDir, "probes").apply { mkdirs() }
            val ext = if (viewModel.state.value.mode == MatchType.FINGERPRINT) "png" else "jpg"
            val file = File(dir, "uploaded_${System.currentTimeMillis()}.$ext")
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                ?: throw IllegalStateException("Unable to open file")
            probeFile?.delete()
            probeFile = file
            binding.probePreview.setImageBitmap(BitmapUtils.decodeSampled(file, PREVIEW_MAX_EDGE))
            if (viewModel.state.value.mode == MatchType.FINGERPRINT) viewModel.onFingerprintFile(file) else viewModel.onPhotoProbe(file)
        } catch (e: Exception) { showMessage("Could not load the selected file.") }
    }

    private fun onProbePhotoCaptured(file: File) {
        probeFile?.delete()
        probeFile = file
        binding.probePreview.setImageBitmap(BitmapUtils.decodeSampled(file, PREVIEW_MAX_EDGE))
        viewModel.onPhotoProbe(file)
    }

    private fun authenticateFingerprint() {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.statusText.text = "FINGERPRINT SENSOR UNAVAILABLE"
            binding.errorText.isVisible = true
            binding.errorText.text = "Set up a fingerprint or other biometric in Android settings."
            return
        }

        binding.statusText.text = "VERIFYING FINGERPRINT"
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.fingerprintVerified()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    binding.statusText.text = "FINGERPRINT VERIFICATION CANCELLED"
                    binding.errorText.isVisible = true
                    binding.errorText.text = errString
                }

                override fun onAuthenticationFailed() {
                    binding.statusText.text = "FINGERPRINT NOT RECOGNIZED"
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Identica fingerprint")
                .setSubtitle("Verify the fingerprint enrolled on this device")
                .setNegativeButtonText("Cancel")
                .build()
        )
    }

    private fun render(state: SearchUiState) {
        val photoMode = state.mode == MatchType.PHOTO
        binding.statusText.text = state.message
        binding.probeLabelText.text = state.probeLabel
        binding.scanProgressBar.progress = state.sweepProgress
        binding.captureProbeButton.text = if (photoMode) "SCAN FACE / CAPTURE PORTRAIT" else "SCAN FINGERPRINT"
        binding.fingerprintScanView.isVisible = !photoMode
        binding.captureProbeButton.isEnabled = !state.scanning
        binding.startScanButton.isEnabled = state.probeReady && !state.scanning

        if (state.scanning) laserController.start() else laserController.stop()
        binding.fingerprintScanView.setProgress(state.sweepProgress)

        val error = state.error
        binding.errorText.isVisible = error != null
        binding.errorText.text = error.orEmpty()

        val outcome = state.outcome
        binding.resultPanel.isVisible = outcome != null
        if (outcome != null) {
            val person = outcome.person
            if (person != null) {
                binding.resultPhoto.setImageBitmap(BitmapUtils.decodeSampled(File(person.photoPath), PREVIEW_MAX_EDGE))
                binding.resultTitleText.text = person.fullName.uppercase()
                binding.resultDetailText.text =
                    "FULL NAME: ${person.fullName}\n" +
                    "AGE: ${person.age}\n" +
                    "SEX: ${person.sex}\n" +
                    "NATIONAL ID: ${person.nationalIdMasked}\n" +
                    "DOCUMENT: ${person.documentDetails}\n" +
                    "SCAN TYPE: ${if (outcome.matchType == MatchType.PHOTO) "FACE" else "FINGERPRINT"}\n" +
                    "MATCH: ${outcome.percent}%\n" +
                    "STATUS: ${if (outcome.isMatch) "IDENTITY CONFIRMED" else "NO CONFIDENT MATCH"}"
            } else {
                binding.resultTitleText.text = "NO CONFIDENT MATCH"
                binding.resultDetailText.text =
                    "Closest candidate scored ${outcome.percent}% (threshold ${SearchViewModel.MATCH_THRESHOLD_PERCENT}%)."
            }
        }

        animateMetric(outcome?.percent ?: state.liveMetric)
    }

    private fun animateMetric(target: Int) {
        if (target == displayedMetric) return
        metricAnimator?.cancel()
        metricAnimator = ValueAnimator.ofInt(displayedMetric, target).apply {
            duration = METRIC_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                displayedMetric = value
                val color = colorForMetric(value)
                binding.matchProgressBar.progress = value
                binding.matchProgressBar.progressTintList = ColorStateList.valueOf(color)
                binding.matchPercentText.text = "$value% MATCH"
                binding.matchPercentText.setTextColor(color)
            }
            start()
        }
    }

    private fun colorForMetric(value: Int): Int {
        val colorRes = when {
            value >= SearchViewModel.MATCH_THRESHOLD_PERCENT -> R.color.neon_green
            value >= LOW_METRIC_PERCENT -> R.color.neon_cyan
            else -> R.color.alert_red
        }
        return ContextCompat.getColor(this, colorRes)
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 1024
        const val METRIC_ANIMATION_MS = 700L
        const val LOW_METRIC_PERCENT = 40
    }
}
