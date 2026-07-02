package me.talula.riftwake.events

enum class RemoveReason {
    UNLOADED,
    DIED,
    DISCONNECTED,
    TIMED_OUT,
    ERRONEOUS_STATE,
    KICKED,
    PLUGIN_DISABLE,
    REGISTRY_RELOAD
}