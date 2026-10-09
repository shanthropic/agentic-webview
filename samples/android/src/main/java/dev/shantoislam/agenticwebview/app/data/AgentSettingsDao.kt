package dev.shantoislam.agenticwebview.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentSettingsDao {
    @Query("SELECT * FROM agent_settings WHERE id = 0")
    fun getSettings(): Flow<AgentSettingsEntity?>

    @Query("SELECT * FROM agent_settings WHERE id = 0")
    suspend fun getSettingsOnce(): AgentSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AgentSettingsEntity)
}
