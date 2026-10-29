package com.example.identica.ui.audit

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.identica.databinding.ActivityAuditLogBinding
import com.example.identica.ui.common.BaseSecureActivity
import kotlinx.coroutines.launch

class AuditLogActivity : BaseSecureActivity() {

    private lateinit var binding: ActivityAuditLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuditLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val logAdapter = AuditLogAdapter()
        binding.auditRecycler.layoutManager = LinearLayoutManager(this)
        binding.auditRecycler.adapter = logAdapter

        binding.deleteLogsButton.setOnClickListener { confirmDeleteLogs() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.observeAuditLogs().collect { logs ->
                    logAdapter.submitList(logs)
                    binding.emptyText.isVisible = logs.isEmpty()
                }
            }
        }
    }

    private fun confirmDeleteLogs() {
        AlertDialog.Builder(this)
            .setTitle("Delete logs")
            .setMessage("This will permanently remove all audit logs. Continue?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Final warning")
                    .setMessage("All audit history will be deleted and cannot be restored.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> deleteLogs() }
                    .show()
            }.show()
    }

    private fun deleteLogs() {
        val root = binding.root
        val animation = AlphaAnimation(0.2f, 1f).apply { duration = 220; repeatMode = AlphaAnimation.REVERSE; repeatCount = 5 }
        root.setBackgroundColor(Color.rgb(120, 0, 0))
        root.startAnimation(animation)
        lifecycleScope.launch {
            app.repository.deleteAllAuditLogs()
            root.clearAnimation()
            root.setBackgroundColor(Color.TRANSPARENT)
            Toast.makeText(this@AuditLogActivity, "Logs deleted", Toast.LENGTH_SHORT).show()
        }
    }
}
