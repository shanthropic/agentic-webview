package dev.shantoislam.agenticwebview.app.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AgentSettingsEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun agentSettingsDao(): AgentSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agentic_webview_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
