package com.shantoislamdev.agenticwebview.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_settings")
data class AgentSettingsEntity(
    @PrimaryKey val id: Int = 0, // Single row for settings
    val useSimulation: Boolean,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String
)
