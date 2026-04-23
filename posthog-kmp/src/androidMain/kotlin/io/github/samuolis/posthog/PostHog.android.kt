package io.github.samuolis.posthog

import android.app.Application
import com.posthog.PostHogInterface
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.android.replay.PostHogSessionReplayConfig

private var postHogInstance: PostHogInterface? = null

@Deprecated(
    message = "Use PostHog.setup(config, PostHogContext(application)) instead",
    replaceWith = ReplaceWith("PostHog.setup(config, PostHogContext(context))")
)
public fun PostHog.setApplicationContext(context: Application) {}

internal actual fun platformSetup(config: PostHogConfig, context: PostHogContext) {
    val androidConfig = PostHogAndroidConfig(
        apiKey = config.apiKey,
        host = config.host
    ).apply {
        debug = config.debug
        captureApplicationLifecycleEvents = config.captureApplicationLifecycleEvents
        captureScreenViews = config.captureScreenViews
        captureDeepLinks = config.captureDeepLinks
        sendFeatureFlagEvent = config.sendFeatureFlagEvent
        preloadFeatureFlags = config.preloadFeatureFlags
        flushAt = config.flushAt
        flushIntervalSeconds = config.flushIntervalSeconds
        maxQueueSize = config.maxQueueSize
        maxBatchSize = config.maxBatchSize
        optOut = config.optOut

        config.sessionRecording?.let { sessionConfig ->
            sessionReplay = sessionConfig.enabled
            sessionReplayConfig = PostHogSessionReplayConfig(
                maskAllTextInputs = sessionConfig.maskAllTextInputs,
                maskAllImages = sessionConfig.maskAllImages,
                captureLogcat = sessionConfig.captureLogcat,
                screenshot = sessionConfig.screenshot,
                debouncerDelayMs = sessionConfig.debouncerDelayMs
            )
        }
    }

    postHogInstance = PostHogAndroid.with(context.application, androidConfig)
}

internal actual fun platformCapture(event: String, properties: Map<String, Any?>?) {
    postHogInstance?.capture(event = event, properties = properties?.toPostHogProperties())
}

internal actual fun platformScreen(screenName: String, properties: Map<String, Any?>?) {
    postHogInstance?.screen(screenTitle = screenName, properties = properties?.toPostHogProperties())
}

internal actual fun platformIdentify(
    distinctId: String,
    userProperties: Map<String, Any?>?,
    userPropertiesSetOnce: Map<String, Any?>?
) {
    postHogInstance?.identify(
        distinctId = distinctId,
        userProperties = userProperties?.toPostHogProperties(),
        userPropertiesSetOnce = userPropertiesSetOnce?.toPostHogProperties()
    )
}

internal actual fun platformAlias(alias: String) {
    postHogInstance?.alias(alias)
}

internal actual fun platformReset() {
    postHogInstance?.reset()
}

internal actual fun platformGetDistinctId(): String? {
    return postHogInstance?.distinctId()
}

internal actual fun platformRegister(key: String, value: Any?) {
    value?.let { postHogInstance?.register(key, it) }
}

internal actual fun platformUnregister(key: String) {
    postHogInstance?.unregister(key)
}

internal actual fun platformGroup(type: String, key: String, groupProperties: Map<String, Any?>?) {
    postHogInstance?.group(
        type = type,
        key = key,
        groupProperties = groupProperties?.toPostHogProperties()
    )
}

internal actual fun platformIsFeatureEnabled(key: String, defaultValue: Boolean): Boolean {
    return postHogInstance?.isFeatureEnabled(key, defaultValue) ?: defaultValue
}

internal actual fun platformGetFeatureFlag(key: String): Any? {
    return postHogInstance?.getFeatureFlag(key)
}

internal actual fun platformGetFeatureFlagPayload(key: String): Any? {
    return postHogInstance?.getFeatureFlagPayload(key)
}

internal actual fun platformReloadFeatureFlags(callback: (() -> Unit)?) {
    val instance = postHogInstance
    if (instance == null) {
        callback?.invoke()
        return
    }
    if (callback != null) {
        instance.reloadFeatureFlags { callback() }
    } else {
        instance.reloadFeatureFlags()
    }
}

internal actual fun platformSetPersonProperties(properties: Map<String, Any?>?, propertiesSetOnce: Map<String, Any?>?) {
    postHogInstance?.setPersonProperties(
        userPropertiesToSet = properties?.toPostHogProperties(),
        userPropertiesToSetOnce = propertiesSetOnce?.toPostHogProperties()
    )
}

internal actual fun platformGetAnonymousId(): String? {
    return postHogInstance?.distinctId()
}

internal actual fun platformGetSessionId(): String? {
    return postHogInstance?.getSessionId()?.toString()
}

internal actual fun platformOptOut() {
    postHogInstance?.optOut()
}

internal actual fun platformOptIn() {
    postHogInstance?.optIn()
}

internal actual fun platformIsOptedOut(): Boolean {
    return postHogInstance?.isOptOut() ?: false
}

internal actual fun platformFlush() {
    postHogInstance?.flush()
}

internal actual fun platformClose() {
    postHogInstance?.close()
    postHogInstance = null
}

internal actual fun platformSetDebug(enabled: Boolean) {
    postHogInstance?.debug(enabled)
}

private fun Map<String, Any?>.toPostHogProperties(): Map<String, Any> {
    return filterValues { it != null }.mapValues { it.value!! }
}
