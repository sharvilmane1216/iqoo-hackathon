package com.aasra.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: MedicineNote): Long

    @Query("SELECT * FROM medicine_notes ORDER BY createdAtMillis DESC")
    fun all(): Flow<List<MedicineNote>>

    @Query("SELECT * FROM medicine_notes ORDER BY createdAtMillis DESC")
    suspend fun list(): List<MedicineNote>

    @Query("DELETE FROM medicine_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
