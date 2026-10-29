package com.example.identica.ui.audit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.identica.R
import com.example.identica.data.local.entity.AuditLog
import com.example.identica.databinding.ItemAuditLogBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AuditLogAdapter : ListAdapter<AuditLog, AuditLogAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemAuditLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAuditLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val log = getItem(position)
        val views = holder.binding
        val context = holder.itemView.context

        views.auditActionText.text = log.action
        views.auditAdminText.text = "ADMIN: ${log.adminUsername} [#${log.adminId}]"
        views.auditTargetText.text = if (log.targetPersonName == null) {
            "TARGET: --"
        } else {
            "TARGET: ${log.targetPersonName} | ID: ${log.targetNationalIdMasked.orEmpty()}"
        }
        views.auditMatchTypeText.text = "DATA: ${log.matchedDataType}"
        views.auditMatchPercentText.text = "${log.matchPercentage}%"
        views.auditTimeText.text = TIME_FORMAT.format(Instant.ofEpochMilli(log.timestamp))

        val colorRes = when {
            log.matchPercentage >= MATCH_STRONG -> R.color.neon_green
            log.matchPercentage > 0 -> R.color.neon_cyan
            else -> R.color.text_secondary
        }
        views.auditMatchPercentText.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    private companion object {
        const val MATCH_STRONG = 85

        val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        val DIFF = object : DiffUtil.ItemCallback<AuditLog>() {
            override fun areItemsTheSame(oldItem: AuditLog, newItem: AuditLog): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AuditLog, newItem: AuditLog): Boolean =
                oldItem == newItem
        }
    }
}
