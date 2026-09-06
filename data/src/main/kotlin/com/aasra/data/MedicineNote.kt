package com.aasra.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_notes")
data class MedicineNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timeText: String,
    val spoken: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
