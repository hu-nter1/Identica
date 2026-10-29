package com.example.identica.ui.auth

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.identica.IdenticaApp
import com.example.identica.data.OpResult
import com.example.identica.databinding.ActivityPasswordRecoveryBinding
import com.example.identica.util.Validator
import kotlinx.coroutines.launch

class PasswordRecoveryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPasswordRecoveryBinding
    private val app: IdenticaApp get() = application as IdenticaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPasswordRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.recoverButton.setOnClickListener { beginRecovery() }
    }

    private fun beginRecovery() {
        binding.usernameLayout.error = null
        binding.passwordLayout.error = null
        binding.confirmLayout.error = null

        val username = binding.usernameInput.text?.toString().orEmpty().trim()
        val passwordText = binding.passwordInput.text?.toString().orEmpty()
        val confirmText = binding.confirmInput.text?.toString().orEmpty()

        if (username.isEmpty()) {
            binding.usernameLayout.error = "Username is required."
            return
        }

        val newPassword = passwordText.toCharArray()
        val passwordError = Validator.password(newPassword)
        if (passwordError != null) {
            binding.passwordLayout.error = passwordError
            newPassword.fill('\u0000')
            return
        }
        if (passwordText != confirmText) {
            binding.confirmLayout.error = "Passwords do not match."
            newPassword.fill('\u0000')
            return
        }

        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            newPassword.fill('\u0000')
            Toast.makeText(this, "Set up a screen lock or strong biometric on this device before recovery.", Toast.LENGTH_LONG).show()
            return
        }

        binding.recoverButton.isEnabled = false
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lifecycleScope.launch {
                    val outcome = app.repository.resetAdminPassword(username, newPassword)
                    newPassword.fill('\u0000')
                    binding.recoverButton.isEnabled = true
                    when (outcome) {
                        is OpResult.Success -> {
                            Toast.makeText(this@PasswordRecoveryActivity, "Password reset successfully. Sign in with the new password.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is OpResult.Failure -> binding.usernameLayout.error = outcome.message
                    }
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                newPassword.fill('\u0000')
                binding.recoverButton.isEnabled = true
                Toast.makeText(this@PasswordRecoveryActivity, errString, Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                Toast.makeText(this@PasswordRecoveryActivity, "Authentication not recognized. Try again.", Toast.LENGTH_SHORT).show()
            }
        })

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Recover Identica password")
                .setSubtitle("Verify this device owner before changing the administrator password")
                .setDescription("This recovery is protected by the device's secure lock or biometric authentication.")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}
