package com.example.identica.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.identica.IdenticaApp
import com.example.identica.security.Session
import com.example.identica.ui.auth.LoginActivity

abstract class BaseSecureActivity : AppCompatActivity() {

    protected val app: IdenticaApp
        get() = application as IdenticaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        if (app.sessionManager.currentSession() == null) {
            redirectToLogin()
        } else {
            app.sessionManager.touch()
        }
    }

    protected fun activeSession(): Session? {
        val session = app.sessionManager.currentSession()
        if (session == null) redirectToLogin()
        return session
    }

    protected fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    protected fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
