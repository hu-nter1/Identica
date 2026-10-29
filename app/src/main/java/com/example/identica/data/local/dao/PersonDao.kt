package com.example.identica.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.identica.data.local.entity.RegisteredPerson

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(person: RegisteredPerson): Long

    @Query("SELECT * FROM registered_persons ORDER BY createdAt DESC")
    suspend fun getAll(): List<RegisteredPerson>

    @Query("SELECT COUNT(*) FROM registered_persons")
    suspend fun count(): Int

    @Query("SELECT * FROM registered_persons WHERE nationalIdHash = :hash LIMIT 1")
    suspend fun findByNationalIdHash(hash: String): RegisteredPerson?

    @Query("SELECT * FROM registered_persons WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): RegisteredPerson?
}
