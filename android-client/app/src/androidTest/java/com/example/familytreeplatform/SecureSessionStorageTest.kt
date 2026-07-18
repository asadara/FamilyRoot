package com.example.familytreeplatform

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SecureSessionStorageTest {
    @Test
    fun encryptsRoundTripsAndClearsSessionValues() {
        val id = UUID.randomUUID().toString()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefsName = "secure-session-test-$id"
        val storage = SecureSessionStorage(context, prefsName, "secure-session-test-key-$id")

        try {
            storage.put("refresh_token", "refresh-secret-value")
            assertEquals("refresh-secret-value", storage.get("refresh_token"))

            val raw = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
                .getString("refresh_token", null)
            assertFalse(raw.orEmpty().contains("refresh-secret-value"))

            storage.clear()
            assertNull(storage.get("refresh_token"))
        } finally {
            storage.destroy()
        }
    }
}
