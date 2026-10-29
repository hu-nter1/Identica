package com.example.identica

import android.app.Application
import com.example.identica.data.IdenticaRepository
import com.example.identica.data.local.AppDatabase
import com.example.identica.security.SessionManager

class IdenticaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val sessionManager: SessionManager by lazy { SessionManager(this) }

    val repository: IdenticaRepository by lazy { IdenticaRepository(database, sessionManager) }
}
