package com.example.appfabricx.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Executors

@Database(
    entities = [
        Device1RuntimeMetric::class,
        Device2RuntimeMetric::class,
        Device3RuntimeMetric::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runtimeMetricDao(): RuntimeMetricDao

    companion object {
        private const val DB_NAME = "appfabric_runtime_telemetry.db"

        @Volatile
        private var instance: AppDatabase? = null

        private val databaseExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AppFabricRoomDb").apply { isDaemon = true }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .setQueryExecutor(databaseExecutor)
                    .setTransactionExecutor(databaseExecutor)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }

    }
}
