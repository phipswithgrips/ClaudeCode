package com.rezept.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey val position: Int,
    val name: String
)
