package com.rezept.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY position ASC")
    fun getAll(): List<Medication>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(medications: List<Medication>)
}
