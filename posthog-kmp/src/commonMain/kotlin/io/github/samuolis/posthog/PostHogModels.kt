package io.github.samuolis.posthog

public enum class ExceptionLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL
}

public data class FeatureFlagResult(
    val key: String,
    val value: Any?,
    val payload: Any? = null,
    val reason: FeatureFlagReason = FeatureFlagReason.UNKNOWN,
) {
    val isEnabled: Boolean get() = when (value) {
        is Boolean -> value
        is String -> true
        null -> false
        else -> true
    }
}

public enum class FeatureFlagReason {
    MATCHED,
    DISABLED,
    ERROR,
    UNKNOWN
}

