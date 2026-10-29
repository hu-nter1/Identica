package com.example.identica.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.identica.data.local.entity.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: AuditLog): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<AuditLog>>

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}
