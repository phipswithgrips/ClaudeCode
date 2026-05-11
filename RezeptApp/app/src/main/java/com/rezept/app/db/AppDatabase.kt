package com.rezept.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(entities = [Medication::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val DEFAULT_MEDICATIONS = listOf(
            Medication(1, "Ramipril 5mg"),
            Medication(2, "Torasemid 2,5mg"),
            Medication(3, "Lercanidipin 10mg"),
            Medication(4, "Doxazosin 1mg"),
            Medication(5, "Rosuvastatin 10mg"),
            Medication(6, "Bisoprolol 2,5mg")
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rezept_db"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Executors.newSingleThreadExecutor().execute {
                            getInstance(context).medicationDao().insertAll(DEFAULT_MEDICATIONS)
                        }
                    }
                })
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
