package com.example.identica.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "admins",
    indices = [Index(value = ["username"], unique = true)]
)
data class Admin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val iterations: Int,
    val createdAt: Long
)
