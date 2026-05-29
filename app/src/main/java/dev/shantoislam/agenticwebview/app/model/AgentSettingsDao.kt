package dev.shantoislam.agenticwebview.app.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentSettingsDao {
    @Query("SELECT * FROM agent_settings WHERE id = 0")
    fun getSettings(): Flow<AgentSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AgentSettingsEntity)
}
