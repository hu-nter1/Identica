package com.example.identica.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["adminId"]), Index(value = ["timestamp"])]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val adminId: Long,
    val adminUsername: String,
    val action: String,
    val targetPersonId: Long?,
    val targetPersonName: String?,
    val targetNationalIdMasked: String?,
    val matchedDataType: String,
    val matchPercentage: Int,
    val timestamp: Long
)
