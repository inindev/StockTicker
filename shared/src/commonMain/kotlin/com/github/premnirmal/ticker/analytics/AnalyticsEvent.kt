package com.github.premnirmal.ticker.analytics

/**
 * Platform-neutral analytics event model: an event [name] plus an accumulating map of string
 * [properties]. Lives in 'commonMain' alongside the shared [Analytics] contract and
 * [GeneralProperties]; each platform supplies a sink (Firebase on Android prod, 'AnalyticsSink' on
 * iOS, no-ops elsewhere).
 */
sealed class AnalyticsEvent(val name: String) {

    val properties: Map<String, String>
        get() = _properties
    private val _properties = HashMap<String, String>()

    open fun addProperty(key: String, value: String) = apply {
        _properties[key] = value
    }
}

class GeneralEvent(name: String) : AnalyticsEvent(name) {
    override fun addProperty(key: String, value: String) = apply {
        super.addProperty(key, value)
    }
}

class ClickEvent(name: String) : AnalyticsEvent(name) {
    override fun addProperty(key: String, value: String) = apply {
        super.addProperty(key, value)
    }
}
