package com.example.identica.ui.person

import android.animation.ValueAnimator
import android.os.Bundle
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.identica.R
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.identica.biometric.FingerprintTemplateEngine
import com.example.identica.data.OpResult
import com.example.identica.data.PersonDraft
import com.example.identica.databinding.ActivityRegisterPersonBinding
import com.example.identica.ui.common.BaseSecureActivity
import com.example.identica.ui.common.CameraCapture
import com.example.identica.util.BitmapUtils
import com.example.identica.util.Validator
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File

class RegisterPersonActivity : BaseSecureActivity() {

    private val fingerprintEngine = FingerprintTemplateEngine()

    private lateinit var binding: ActivityRegisterPersonBinding

    private var photoFile: File? = null
    private var fingerprintHash: String? = null
    private var fingerprintAnimator: ValueAnimator? = null

    private val fingerprintFilePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importFingerprintFile(uri)
    }

    private val camera = CameraCapture(
        activity = this,
        targetFile = { File(File(filesDir, "photos"), "person_${System.currentTimeMillis()}.jpg") },
        onCaptured = ::onPhotoCaptured,
        onFailed = ::showMessage
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.capturePhotoButton.setOnClickListener { camera.capture() }
        binding.enrollFingerprintButton.setOnClickListener { enrollFingerprint() }
        binding.uploadFingerprintButton.setOnClickListener { fingerprintFilePicker.launch("image/*") }
        binding.saveProfileButton.setOnClickListener { saveProfile() }
    }

    private fun onPhotoCaptured(file: File) {
        photoFile?.delete()
        photoFile = file
        binding.photoPreview.setImageBitmap(BitmapUtils.decodeSampled(file, PREVIEW_MAX_EDGE))
        binding.formErrorText.isVisible = false
    }

    private fun importFingerprintFile(uri: Uri) {
        try {
            val dir = File(cacheDir, "fingerprints").apply { mkdirs() }
            val file = File(dir, "fingerprint_${System.currentTimeMillis()}.bin")
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                ?: throw IllegalStateException("Unable to open file")
            fingerprintHash = com.example.identica.biometric.HashUtil.sha256Hex(file.readBytes())
            binding.fingerprintProgress.progress = 100
            binding.fingerprintPercentText.text = "100%"
            binding.fingerprintStatusText.text = "FINGERPRINT FILE LOADED"
            binding.formErrorText.isVisible = false
        } catch (e: Exception) {
            showMessage("Could not load the fingerprint file.")
        }
    }

    private fun enrollFingerprint() {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.fingerprintStatusText.text = "BIOMETRIC SENSOR UNAVAILABLE"
            binding.formErrorText.text = "Set up a fingerprint or other biometric in Android settings."
            binding.formErrorText.isVisible = true
            return
        }

        binding.enrollFingerprintButton.isEnabled = false
        binding.fingerprintStatusText.text = "SCANNING FINGERPRINT"
        binding.fingerprintProgress.progress = 0
        binding.fingerprintPercentText.text = "0%"
        binding.fingerprintScanView.setProgress(0)
        binding.fingerprintScanView.startScan()
        fingerprintAnimator?.cancel()
        fingerprintAnimator = ValueAnimator.ofInt(0, 92).apply {
            duration = 2600L
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.fingerprintProgress.progress = value
                binding.fingerprintPercentText.text = "$value%"
                binding.fingerprintScanView.setProgress(value)
            }
            start()
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    fingerprintHash = fingerprintEngine.createTemplate()
                    fingerprintAnimator?.cancel()
                    binding.fingerprintProgress.progress = 100
                    binding.fingerprintPercentText.text = "100%"
                    binding.fingerprintScanView.setProgress(100)
                    binding.fingerprintScanView.stopScan()
                    binding.fingerprintStatusText.text = "FINGERPRINT CAPTURE COMPLETE"
                    binding.formErrorText.isVisible = false
                    binding.enrollFingerprintButton.isEnabled = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    fingerprintAnimator?.cancel()
                    binding.fingerprintScanView.stopScan()
                    binding.fingerprintProgress.progress = 0
                    binding.fingerprintPercentText.text = "0%"
                    binding.fingerprintStatusText.text = "FINGERPRINT SCAN CANCELLED"
                    binding.formErrorText.text = errString
                    binding.formErrorText.isVisible = true
                    binding.enrollFingerprintButton.isEnabled = true
                }

                override fun onAuthenticationFailed() {
                    binding.fingerprintStatusText.text = "FINGERPRINT NOT RECOGNIZED — TRY AGAIN"
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

    private fun saveProfile() {
        listOf(
            binding.nameLayout,
            binding.ageLayout,
            binding.nationalIdLayout,
            binding.documentLayout
        ).forEach { it.error = null }

        val name = binding.nameInput.text?.toString().orEmpty().trim()
        val ageText = binding.ageInput.text?.toString().orEmpty().trim()
        val nationalId = binding.nationalIdInput.text?.toString().orEmpty().trim()
        val documentDetails = binding.documentDetailsInput.text?.toString().orEmpty().trim()
        val sex: String? = when (binding.sexGroup.checkedRadioButtonId) {
            R.id.sexMale -> "Male"
            R.id.sexFemale -> "Female"
            R.id.sexOther -> "Other"
            else -> null
        }

        var fieldsValid = true
        fun check(layout: TextInputLayout, error: String?) {
            if (error != null) {
                layout.error = error
                fieldsValid = false
            }
        }
        check(binding.nameLayout, Validator.fullName(name))
        check(binding.ageLayout, Validator.age(ageText))
        check(binding.nationalIdLayout, Validator.nationalId(nationalId))
        check(binding.documentLayout, Validator.documentDetails(documentDetails))

        val formError = Validator.sex(sex)
            ?: Validator.photo(photoFile)
        binding.formErrorText.text = formError.orEmpty()
        binding.formErrorText.isVisible = formError != null

        if (!fieldsValid || formError != null) return

        val draft = PersonDraft(
            fullName = name,
            age = ageText.toInt(),
            sex = sex ?: return,
            nationalId = nationalId,
            documentDetails = documentDetails,
            photoFile = photoFile ?: return,
            fingerprintHash = fingerprintHash
        )

        setLoading(true)
        lifecycleScope.launch {
            val result = app.repository.registerPerson(draft)
            setLoading(false)
            when (result) {
                is OpResult.Success -> {
                    showMessage("Profile registered.")
                    finish()
                }
                is OpResult.Failure -> {
                    binding.formErrorText.text = result.message
                    binding.formErrorText.isVisible = true
                }
            }
        }
    }

    override fun onDestroy() {
        fingerprintAnimator?.cancel()
        binding.fingerprintScanView.stopScan()
        super.onDestroy()
    }

    private fun setLoading(loading: Boolean) {
        binding.registerProgress.isVisible = loading
        binding.saveProfileButton.isEnabled = !loading
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 768
    }
}
