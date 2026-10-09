package dev.shantoislam.agenticwebview.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_settings")
data class AgentSettingsEntity(
    @PrimaryKey
    val id: Int = 0,
    val useSimulation: Boolean = true,
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
)
