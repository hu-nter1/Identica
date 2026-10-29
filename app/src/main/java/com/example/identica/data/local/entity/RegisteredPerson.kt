package com.example.identica.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "registered_persons",
    indices = [Index(value = ["nationalIdHash"], unique = true)]
)
data class RegisteredPerson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fullName: String,
    val age: Int,
    val sex: String,
    val nationalIdHash: String,
    val nationalIdMasked: String,
    val documentDetails: String,
    val photoPath: String,
    val photoHash: String,
    val fingerprintHash: String?,
    val registeredByAdminId: Long,
    val createdAt: Long
)
