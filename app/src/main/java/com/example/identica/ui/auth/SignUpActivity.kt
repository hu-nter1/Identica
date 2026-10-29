package com.example.identica.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.identica.IdenticaApp
import com.example.identica.data.OpResult
import com.example.identica.databinding.ActivitySignUpBinding
import com.example.identica.util.Validator
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    private val app: IdenticaApp
        get() = application as IdenticaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Re-enable screenshot protection before production.
        // window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signUpButton.setOnClickListener { attemptSignUp() }
        binding.backToLoginButton.setOnClickListener { finish() }
    }

    private fun attemptSignUp() {
        binding.signUpUsernameLayout.error = null
        binding.signUpPasswordLayout.error = null
        binding.signUpConfirmLayout.error = null

        val username = binding.signUpUsernameInput.text?.toString().orEmpty().trim()
        val passwordText = binding.signUpPasswordInput.text?.toString().orEmpty()
        val confirmText = binding.signUpConfirmInput.text?.toString().orEmpty()

        val usernameError = Validator.username(username.lowercase())
        if (usernameError != null) {
            binding.signUpUsernameLayout.error = usernameError
            return
        }
        val password = passwordText.toCharArray()
        val passwordError = Validator.password(password)
        if (passwordError != null) {
            binding.signUpPasswordLayout.error = passwordError
            return
        }
        if (passwordText != confirmText) {
            binding.signUpConfirmLayout.error = "Passwords do not match."
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = app.repository.createAdmin(username, password)
            password.fill('\u0000')
            setLoading(false)
            when (result) {
                is OpResult.Success -> {
                    Toast.makeText(this@SignUpActivity, "Admin account created.", Toast.LENGTH_LONG).show()
                    finish()
                }
                is OpResult.Failure -> binding.signUpUsernameLayout.error = result.message
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.signUpProgress.isVisible = loading
        binding.signUpButton.isEnabled = !loading
    }
}
