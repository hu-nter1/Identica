package com.example.identica.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.identica.data.local.entity.Admin

@Dao
interface AdminDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(admin: Admin): Long

    @Query("SELECT * FROM admins WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): Admin?

    @Query("SELECT COUNT(*) FROM admins")
    suspend fun count(): Int

    @Query("UPDATE admins SET passwordHash = :passwordHash, salt = :salt, iterations = :iterations WHERE id = :id")
    suspend fun updatePassword(id: Long, passwordHash: String, salt: String, iterations: Int)

    @Query("SELECT * FROM admins ORDER BY createdAt DESC")
    suspend fun getAll(): List<Admin>
}
