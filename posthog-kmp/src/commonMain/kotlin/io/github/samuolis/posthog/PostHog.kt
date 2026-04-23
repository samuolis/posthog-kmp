package io.github.samuolis.posthog

public object PostHog {

    private var isInitialized: Boolean = false
    private var config: PostHogConfig? = null

    public fun setup(config: PostHogConfig, context: PostHogContext) {
        if (isInitialized) return
        this.config = config
        platformSetup(config, context)
        isInitialized = true
    }

    public fun setup(config: PostHogConfig) {
        setup(config, PostHogContext())
    }

    public fun isSetup(): Boolean = isInitialized

    public fun capture(event: String, properties: Map<String, Any?>? = null) {
        if (!isInitialized) return
        try {
            platformCapture(event, properties)
        } catch (e: Throwable) {
            logError("capture", e)
        }
    }

    public fun screen(screenName: String, properties: Map<String, Any?>? = null) {
        if (!isInitialized) return
        try {
            platformScreen(screenName, properties)
        } catch (e: Throwable) {
            logError("screen", e)
        }
    }

    public fun identify(
        distinctId: String,
        userProperties: Map<String, Any?>? = null,
        userPropertiesSetOnce: Map<String, Any?>? = null
    ) {
        if (!isInitialized) return
        try {
            platformIdentify(distinctId, userProperties, userPropertiesSetOnce)
        } catch (e: Throwable) {
            logError("identify", e)
        }
    }

    public fun alias(alias: String) {
        if (!isInitialized) return
        try {
            platformAlias(alias)
        } catch (e: Throwable) {
            logError("alias", e)
        }
    }

    public fun reset() {
        if (!isInitialized) return
        try {
            platformReset()
        } catch (e: Throwable) {
            logError("reset", e)
        }
    }

    public fun getDistinctId(): String? {
        if (!isInitialized) return null
        return try {
            platformGetDistinctId()
        } catch (e: Throwable) {
            logError("getDistinctId", e)
            null
        }
    }

    public fun register(key: String, value: Any?) {
        if (!isInitialized) return
        try {
            platformRegister(key, value)
        } catch (e: Throwable) {
            logError("register", e)
        }
    }

    public fun unregister(key: String) {
        if (!isInitialized) return
        try {
            platformUnregister(key)
        } catch (e: Throwable) {
            logError("unregister", e)
        }
    }

    public fun group(type: String, key: String, groupProperties: Map<String, Any?>? = null) {
        if (!isInitialized) return
        try {
            platformGroup(type, key, groupProperties)
        } catch (e: Throwable) {
            logError("group", e)
        }
    }

    public fun isFeatureEnabled(key: String, defaultValue: Boolean = false): Boolean {
        if (!isInitialized) return defaultValue
        return try {
            platformIsFeatureEnabled(key, defaultValue)
        } catch (e: Throwable) {
            logError("isFeatureEnabled", e)
            defaultValue
        }
    }

    public fun getFeatureFlag(key: String): Any? {
        if (!isInitialized) return null
        return try {
            platformGetFeatureFlag(key)
        } catch (e: Throwable) {
            logError("getFeatureFlag", e)
            null
        }
    }

    public fun getFeatureFlagPayload(key: String): Any? {
        if (!isInitialized) return null
        return try {
            platformGetFeatureFlagPayload(key)
        } catch (e: Throwable) {
            logError("getFeatureFlagPayload", e)
            null
        }
    }

    public fun getFeatureFlagResult(key: String): FeatureFlagResult {
        if (!isInitialized) return FeatureFlagResult(key = key, value = null, reason = FeatureFlagReason.ERROR)
        return try {
            val value = platformGetFeatureFlag(key)
            val payload = platformGetFeatureFlagPayload(key)
            val reason = when {
                value == null -> FeatureFlagReason.DISABLED
                value is Boolean && !value -> FeatureFlagReason.DISABLED
                else -> FeatureFlagReason.MATCHED
            }
            FeatureFlagResult(key = key, value = value, payload = payload, reason = reason)
        } catch (e: Throwable) {
            logError("getFeatureFlagResult", e)
            FeatureFlagResult(key = key, value = null, reason = FeatureFlagReason.ERROR)
        }
    }

    public fun reloadFeatureFlags(callback: (() -> Unit)? = null) {
        if (!isInitialized) {
            callback?.invoke()
            return
        }
        try {
            platformReloadFeatureFlags(callback)
        } catch (e: Throwable) {
            logError("reloadFeatureFlags", e)
            callback?.invoke()
        }
    }

    public fun setPersonProperties(
        properties: Map<String, Any?>? = null,
        propertiesSetOnce: Map<String, Any?>? = null
    ) {
        if (!isInitialized) return
        try {
            platformSetPersonProperties(properties, propertiesSetOnce)
        } catch (e: Throwable) {
            logError("setPersonProperties", e)
        }
    }

    public fun getAnonymousId(): String? {
        if (!isInitialized) return null
        return try {
            platformGetAnonymousId()
        } catch (e: Throwable) {
            logError("getAnonymousId", e)
            null
        }
    }

    public fun getSessionId(): String? {
        if (!isInitialized) return null
        return try {
            platformGetSessionId()
        } catch (e: Throwable) {
            logError("getSessionId", e)
            null
        }
    }

    public fun captureException(
        throwable: Throwable,
        level: ExceptionLevel = ExceptionLevel.ERROR,
        additionalProperties: Map<String, Any?>? = null
    ) {
        if (!isInitialized) return
        try {
            val properties = buildMap {
                put("\$exception_type", throwable::class.simpleName ?: "Unknown")
                put("\$exception_message", throwable.message ?: "No message")
                put("\$exception_stacktrace", throwable.stackTraceToString())
                put("\$exception_level", level.name.lowercase())
                additionalProperties?.forEach { (key, value) -> put(key, value) }
            }
            capture("\$exception", properties)
        } catch (e: Throwable) {
            logError("captureException", e)
        }
    }

    public fun optOut() {
        if (!isInitialized) return
        try {
            platformOptOut()
        } catch (e: Throwable) {
            logError("optOut", e)
        }
    }

    public fun optIn() {
        if (!isInitialized) return
        try {
            platformOptIn()
        } catch (e: Throwable) {
            logError("optIn", e)
        }
    }

    public fun isOptedOut(): Boolean {
        if (!isInitialized) return false
        return try {
            platformIsOptedOut()
        } catch (e: Throwable) {
            logError("isOptedOut", e)
            false
        }
    }

    public fun flush() {
        if (!isInitialized) return
        try {
            platformFlush()
        } catch (e: Throwable) {
            logError("flush", e)
        }
    }

    public fun close() {
        if (!isInitialized) return
        try {
            platformClose()
            isInitialized = false
            config = null
        } catch (e: Throwable) {
            logError("close", e)
        }
    }

    public fun setDebug(enabled: Boolean) {
        if (!isInitialized) return
        platformSetDebug(enabled)
    }

    private fun logError(method: String, error: Throwable) {
        if (config?.debug == true) {
            println("[PostHog] Error in $method: ${error.message}")
        }
    }
}

internal expect fun platformSetup(config: PostHogConfig, context: PostHogContext)
internal expect fun platformCapture(event: String, properties: Map<String, Any?>?)
internal expect fun platformScreen(screenName: String, properties: Map<String, Any?>?)
internal expect fun platformIdentify(distinctId: String, userProperties: Map<String, Any?>?, userPropertiesSetOnce: Map<String, Any?>?)
internal expect fun platformAlias(alias: String)
internal expect fun platformReset()
internal expect fun platformGetDistinctId(): String?
internal expect fun platformRegister(key: String, value: Any?)
internal expect fun platformUnregister(key: String)
internal expect fun platformGroup(type: String, key: String, groupProperties: Map<String, Any?>?)
internal expect fun platformIsFeatureEnabled(key: String, defaultValue: Boolean): Boolean
internal expect fun platformGetFeatureFlag(key: String): Any?
internal expect fun platformGetFeatureFlagPayload(key: String): Any?
internal expect fun platformReloadFeatureFlags(callback: (() -> Unit)?)
internal expect fun platformSetPersonProperties(properties: Map<String, Any?>?, propertiesSetOnce: Map<String, Any?>?)
internal expect fun platformGetAnonymousId(): String?
internal expect fun platformGetSessionId(): String?
internal expect fun platformOptOut()
internal expect fun platformOptIn()
internal expect fun platformIsOptedOut(): Boolean
internal expect fun platformFlush()
internal expect fun platformClose()
internal expect fun platformSetDebug(enabled: Boolean)
