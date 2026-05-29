package dev.shantoislam.agenticwebview.models

import kotlinx.serialization.Serializable

@Serializable
data class DropdownOption(
    val value: String,
    val text: String,
    val index: Int
)
