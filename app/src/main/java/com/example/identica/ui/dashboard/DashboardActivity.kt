package com.example.identica.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.identica.databinding.ActivityDashboardBinding
import com.example.identica.ui.audit.AuditLogActivity
import com.example.identica.ui.auth.SignUpActivity
import com.example.identica.ui.common.BaseSecureActivity
import com.example.identica.ui.person.RegisterPersonActivity
import com.example.identica.ui.registry.RegistryActivity
import com.example.identica.ui.search.SearchPersonActivity
import kotlinx.coroutines.launch

class DashboardActivity : BaseSecureActivity() {
    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tabs = listOf("HOME", "REGISTER", "SCAN", "REGISTRY", "AUDIT", "ADMIN")
        tabs.forEach { binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText(it)) }
        binding.dashboardTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> Unit
                    1 -> startActivity(Intent(this@DashboardActivity, RegisterPersonActivity::class.java))
                    2 -> startActivity(Intent(this@DashboardActivity, SearchPersonActivity::class.java))
                    3 -> startActivity(Intent(this@DashboardActivity, RegistryActivity::class.java))
                    4 -> startActivity(Intent(this@DashboardActivity, AuditLogActivity::class.java))
                    5 -> startActivity(Intent(this@DashboardActivity, SignUpActivity::class.java))
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                if (tab.position != 0) onTabSelected(tab)
            }
        })
        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch { app.repository.logout(); redirectToLogin() }
        }
    }

    override fun onResume() {
        super.onResume()
        val session = app.sessionManager.currentSession() ?: return
        binding.dashboardWelcomeText.text = "Operator: ${session.username.uppercase()}"
        lifecycleScope.launch {
            binding.personCountText.text = app.repository.getPersonCount().toString()
            binding.adminCountText.text = app.repository.getAdminCount().toString()
        }
    }
}
