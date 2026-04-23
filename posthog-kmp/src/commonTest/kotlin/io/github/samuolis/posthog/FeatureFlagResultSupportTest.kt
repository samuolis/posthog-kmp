package io.github.samuolis.posthog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFlagResultTest {

    @Test
    fun `isEnabled returns false for null value`() {
        val result = FeatureFlagResult(key = "flag", value = null, reason = FeatureFlagReason.DISABLED)
        assertFalse(result.isEnabled)
    }

    @Test
    fun `isEnabled returns false for boolean false`() {
        val result = FeatureFlagResult(key = "flag", value = false, reason = FeatureFlagReason.DISABLED)
        assertFalse(result.isEnabled)
    }

    @Test
    fun `isEnabled returns true for boolean true`() {
        val result = FeatureFlagResult(key = "flag", value = true, reason = FeatureFlagReason.MATCHED)
        assertTrue(result.isEnabled)
    }

    @Test
    fun `isEnabled returns true for string variant`() {
        val result = FeatureFlagResult(key = "flag", value = "control", reason = FeatureFlagReason.MATCHED)
        assertTrue(result.isEnabled)
    }

    @Test
    fun `result carries payload`() {
        val payload = mapOf("max" to 10)
        val result = FeatureFlagResult(key = "flag", value = true, payload = payload, reason = FeatureFlagReason.MATCHED)
        assertEquals(payload, result.payload)
    }
}
