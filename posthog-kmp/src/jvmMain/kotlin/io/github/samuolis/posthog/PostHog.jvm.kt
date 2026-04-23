package io.github.samuolis.posthog

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JVM implementation using Ktor HTTP client.
 *
 * This implementation is suitable for server-side Kotlin applications
 * and uses direct HTTP API calls to PostHog.
 */

private var config: PostHogConfig? = null
private var distinctId: String? = null
private var anonymousId: String? = null
private var sessionId: String? = null
private val superProperties = ConcurrentHashMap<String, Any?>()
private val eventQueue = CopyOnWriteArrayList<PostHogJvmEvent>()
private val featureFlags = ConcurrentHashMap<String, Any?>()
private var isOptedOut = false
private var httpClient: HttpClient? = null
private var flushJob: Job? = null
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private data class PostHogJvmEvent(
    val event: String,
    val properties: Map<String, Any?>?,
    val timestamp: String
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

@Suppress("UNUSED_PARAMETER")
internal actual fun platformSetup(config: PostHogConfig, context: PostHogContext) {
    io.github.samuolis.posthog.config = config

    httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    anonymousId = UUID.randomUUID().toString()
    distinctId = anonymousId
    sessionId = UUID.randomUUID().toString()

    if (config.preloadFeatureFlags) {
        scope.launch { loadFeatureFlags() }
    }

    startFlushTimer(config.flushIntervalSeconds)
}

internal actual fun platformCapture(event: String, properties: Map<String, Any?>?) {
    if (isOptedOut) return

    val eventProperties = buildMap {
        put("\$lib", "posthog-kmp")
        put("\$lib_version", "0.2.0")
        superProperties.forEach { (key, value) -> put(key, value) }
        properties?.forEach { (key, value) -> put(key, value) }
        put("distinct_id", distinctId ?: anonymousId ?: "unknown")
    }

    val postHogEvent = PostHogJvmEvent(
        event = event,
        properties = eventProperties,
        timestamp = getCurrentISOTimestamp()
    )

    eventQueue.add(postHogEvent)

    config?.let { cfg ->
        if (eventQueue.size >= cfg.flushAt) {
            scope.launch { flushEvents() }
        }
    }
    println("PosthogAnalyticsManager platformCapture: Sent event $event $eventProperties")
}

internal actual fun platformScreen(screenName: String, properties: Map<String, Any?>?) {
    val screenProperties = buildMap {
        put("\$screen_name", screenName)
        properties?.forEach { (key, value) -> put(key, value) }
    }
    platformCapture("\$screen", screenProperties)
}

internal actual fun platformIdentify(
    distinctId: String,
    userProperties: Map<String, Any?>?,
    userPropertiesSetOnce: Map<String, Any?>?
) {
    val previousDistinctId = io.github.samuolis.posthog.distinctId
    io.github.samuolis.posthog.distinctId = distinctId

    val identifyProperties = buildMap {
        put("\$anon_distinct_id", previousDistinctId)
        if (!userProperties.isNullOrEmpty()) {
            put("\$set", userProperties)
        }
        if (!userPropertiesSetOnce.isNullOrEmpty()) {
            put("\$set_once", userPropertiesSetOnce)
        }
    }

    platformCapture("\$identify", identifyProperties)

    // Reload feature flags for identified user
    scope.launch { loadFeatureFlags() }
}

internal actual fun platformAlias(alias: String) {
    val aliasProperties = buildMap {
        put("distinct_id", distinctId)
        put("alias", alias)
    }
    platformCapture("\$create_alias", aliasProperties)
}

internal actual fun platformReset() {
    anonymousId = UUID.randomUUID().toString()
    distinctId = anonymousId
    superProperties.clear()
    featureFlags.clear()
}

internal actual fun platformGetDistinctId(): String? {
    return distinctId
}

internal actual fun platformRegister(key: String, value: Any?) {
    superProperties[key] = value
}

internal actual fun platformUnregister(key: String) {
    superProperties.remove(key)
}

internal actual fun platformGroup(
    type: String,
    key: String,
    groupProperties: Map<String, Any?>?
) {
    val groupIdentifyProperties = buildMap {
        put("\$group_type", type)
        put("\$group_key", key)
        if (!groupProperties.isNullOrEmpty()) {
            put("\$group_set", groupProperties)
        }
    }
    platformCapture("\$groupidentify", groupIdentifyProperties)

    // Add to super properties for future events
    @Suppress("UNCHECKED_CAST")
    val groups = (superProperties["\$groups"] as? MutableMap<String, String>) ?: mutableMapOf()
    groups[type] = key
    superProperties["\$groups"] = groups
}

internal actual fun platformIsFeatureEnabled(key: String, defaultValue: Boolean): Boolean {
    return when (val flag = featureFlags[key]) {
        is Boolean -> flag
        is String -> flag.lowercase() != "false"
        null -> defaultValue
        else -> true
    }
}

internal actual fun platformGetFeatureFlag(key: String): Any? {
    return featureFlags[key]
}

internal actual fun platformGetFeatureFlagPayload(key: String): Any? {
    return featureFlags["${key}_payload"]
}

internal actual fun platformReloadFeatureFlags(callback: (() -> Unit)?) {
    scope.launch {
        loadFeatureFlags()
        callback?.invoke()
    }
}

internal actual fun platformSetPersonProperties(properties: Map<String, Any?>?, propertiesSetOnce: Map<String, Any?>?) {
    val props = buildMap<String, Any?> {
        properties?.let { put("\$set", it) }
        propertiesSetOnce?.let { put("\$set_once", it) }
    }
    if (props.isNotEmpty()) platformCapture("\$set", props)
}

internal actual fun platformGetAnonymousId(): String? {
    return anonymousId
}

internal actual fun platformGetSessionId(): String? {
    // JVM doesn't have built-in session tracking, return a generated session ID
    return sessionId
}

internal actual fun platformOptOut() {
    isOptedOut = true
}

internal actual fun platformOptIn() {
    isOptedOut = false
}

internal actual fun platformIsOptedOut(): Boolean {
    return isOptedOut
}

internal actual fun platformFlush() {
    scope.launch { flushEvents() }
}

internal actual fun platformClose() {
    runBlocking {
        flushEvents()
    }
    flushJob?.cancel()
    scope.cancel()
    httpClient?.close()
    httpClient = null
    config = null
    distinctId = null
    superProperties.clear()
    eventQueue.clear()
    featureFlags.clear()
}

internal actual fun platformSetDebug(enabled: Boolean) {
    config = config?.copy(debug = enabled)
}

// ==================== Helper Functions ====================

private suspend fun flushEvents() {
    val cfg = config ?: return
    val client = httpClient ?: return

    if (eventQueue.isEmpty()) return

    val eventsToSend = eventQueue.toList()
    eventQueue.clear()

    val batch = eventsToSend.map { event ->
        mapOf(
            "event" to event.event,
            "properties" to event.properties,
            "timestamp" to event.timestamp
        )
    }

    val body = mapOf(
        "api_key" to cfg.apiKey,
        "batch" to batch
    )

    val requestBody = json.encodeToString(mapSerializer, body)

    try {
        val response = client.post("${cfg.host}/batch") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (cfg.debug) {
            println("[PostHog] Flushed ${eventsToSend.size} events: ${response.status}\n${if (!response.status.isSuccess()) requestBody+"\n"+response else ""}")
        }
    } catch (e: Exception) {
        if (cfg.debug) {
            println("[PostHog] Flush error: ${e.message}")
        }
        // Re-add events to queue on failure
        eventQueue.addAll(0, eventsToSend)
    }
}

private suspend fun loadFeatureFlags() {
    val cfg = config ?: return
    val client = httpClient ?: return

    val body = buildMap {
        put("api_key", cfg.apiKey)
        put("distinct_id", distinctId ?: anonymousId ?: "unknown")
        superProperties["\$groups"]?.let { put("groups", it) }
    }

    try {
        val response = client.post("${cfg.host}/decide?v=3") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(mapSerializer, body))
        }

        if (response.status.isSuccess()) {
            val responseText = response.bodyAsText()
            val responseJson = json.parseToJsonElement(responseText).jsonObject

            responseJson["featureFlags"]?.jsonObject?.forEach { (key, value) ->
                featureFlags[key] = when (value) {
                    is JsonPrimitive -> when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        else -> value.content
                    }
                    else -> value.toString()
                }
            }

            responseJson["featureFlagPayloads"]?.jsonObject?.forEach { (key, value) ->
                featureFlags["${key}_payload"] = value.toString()
            }
        }
    } catch (e: Exception) {
        if (cfg.debug) {
            println("[PostHog] Feature flags load error: ${e.message}")
        }
    }
}

private fun startFlushTimer(intervalSeconds: Int) {
    flushJob?.cancel()
    flushJob = scope.launch {
        while (isActive) {
            delay(intervalSeconds * 1000L)
            flushEvents()
        }
    }
}

private fun getCurrentISOTimestamp(): String {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
}

// Custom serializer for Map<String, Any?>
@OptIn(ExperimentalSerializationApi::class)
private object MapStringAnySerializer : KSerializer<Map<String, Any?>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MapStringAny")

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is List<*> -> buildJsonArray {
            this@toJsonElement.forEach { item ->
                add(item.toJsonElement())
            }
        }
        is Map<*, *> -> buildJsonObject {
            this@toJsonElement.forEach { (key, value) ->
                put(key.toString(), value.toJsonElement())
            }
        }
        else -> JsonPrimitive(this.toString())
    }

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonObject = buildJsonObject {
            value.forEach { (key, v) ->
                put(key, v.toJsonElement())
            }
        }
        encoder.encodeSerializableValue(JsonObject.serializer(), jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        // For PostHog we mainly need serialization, but provide basic deserialization
        val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())
        return jsonObject.mapValues { (_, v) ->
            when (v) {
                is JsonPrimitive -> when {
                    v.isString -> v.content
                    v.booleanOrNull != null -> v.boolean
                    v.intOrNull != null -> v.int
                    v.longOrNull != null -> v.long
                    v.floatOrNull != null -> v.float
                    v.doubleOrNull != null -> v.double
                    else -> v.content
                }
                is JsonNull -> null
                else -> v.toString()
            }
        }
    }
}

// Custom map serializer for the body
private val mapSerializer = MapStringAnySerializer
