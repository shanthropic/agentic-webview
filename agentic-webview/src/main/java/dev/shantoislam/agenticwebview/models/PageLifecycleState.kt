package dev.shantoislam.agenticwebview.models

import kotlinx.serialization.Serializable

@Serializable
enum class PageLifecycleState {
    IDLE,
    LOADING,
    INTERACTIVE,
    COMPLETE,
    ERROR,
    CRASHED
}
