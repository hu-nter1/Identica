package com.example.identica.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.identica.IdenticaApp
import com.example.identica.data.OpResult
import com.example.identica.databinding.ActivityLoginBinding
import com.example.identica.ui.dashboard.DashboardActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val app: IdenticaApp
        get() = application as IdenticaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val launchTime = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - launchTime < SPLASH_DURATION_MS
        }

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { attemptLogin() }
        binding.goToSignUpButton.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
        binding.forgotPasswordButton.setOnClickListener {
            startActivity(Intent(this, PasswordRecoveryActivity::class.java))
        }

        if (app.sessionManager.currentSession() != null) openDashboard()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            binding.goToSignUpButton.isVisible = !app.repository.hasAnyAdmin()
        }
    }

    private fun attemptLogin() {
        binding.loginUsernameLayout.error = null
        binding.loginPasswordLayout.error = null

        val username = binding.loginUsernameInput.text?.toString().orEmpty().trim()
        val passwordText = binding.loginPasswordInput.text?.toString().orEmpty()

        if (username.isEmpty()) {
            binding.loginUsernameLayout.error = "Username is required."
            return
        }
        if (passwordText.isEmpty()) {
            binding.loginPasswordLayout.error = "Password is required."
            return
        }

        val password = passwordText.toCharArray()
        setLoading(true)
        lifecycleScope.launch {
            val result = app.repository.login(username, password)
            password.fill('\u0000')
            setLoading(false)
            when (result) {
                is OpResult.Success -> {
                    binding.loginPasswordInput.text?.clear()
                    openDashboard()
                }
                is OpResult.Failure -> binding.loginPasswordLayout.error = result.message
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loginProgress.isVisible = loading
        binding.loginButton.isEnabled = !loading
    }

    private fun openDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1500L
    }
}
