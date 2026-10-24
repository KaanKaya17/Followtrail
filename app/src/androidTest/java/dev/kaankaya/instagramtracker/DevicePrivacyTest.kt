package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DevicePrivacyTest {
    @Test fun keystoreEncryptionRoundtripTamperAndRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SessionVault(context, "testvault")
        val file = File(context.noBackupFilesDir, "testvault.enc")
        try {
            val session = SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic-private-token", "test-UA")!!
            val account = SavedAccount(session, Profile("123", "synthetic", 0, 7, 1000),
                listOf(TrackedPerson(listOf(Profile("456", "synthetic_person", 12, 34, 1000, 0)), "private-note", "group")))
            vault.write(account)
            assertEquals(account, SessionVault(context, "testvault").read())
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("synthetic-private-token"))
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("private-note"))
            val firstWrite = file.readBytes()
            vault.write(account)
            assertFalse(firstWrite.contentEquals(file.readBytes()))
            val data = file.readBytes(); data[data.lastIndex] = (data.last().toInt() xor 1).toByte(); file.writeBytes(data)
            try { vault.read(); fail("Tampered data must fail") } catch (_: java.security.GeneralSecurityException) { }
            vault.clear(); assertFalse(vault.exists()); assertNull(SessionVault(context, "testvault").read())
        } finally { vault.clear() }
    }
    @Test fun productionManifestHasNoBackupOrExtraPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.POST_NOTIFICATIONS"), info.requestedPermissions.orEmpty().toSet())
        assertEquals(0, info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
        val service = context.packageManager.getServiceInfo(android.content.ComponentName(context, MonitorJobService::class.java), 0)
        assertEquals("android.permission.BIND_JOB_SERVICE", service.permission)
        val lab = context.packageManager.getActivityInfo(android.content.ComponentName(context, ConnectionLabActivity::class.java), 0)
        assertFalse(lab.exported)
    }
    @Test fun pauseAndMinimumSpacingSurviveNewGateInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = context.getSharedPreferences("test_gate", android.content.Context.MODE_PRIVATE)
        settings.edit().clear().commit()
        try {
            val gate = RequestGate(context, "test_gate")
            val now = System.currentTimeMillis()
            gate.begin(now)
            val recreated = RequestGate(context, "test_gate")
            try { recreated.begin(now + 1000); fail("Repeat must be blocked") }
            catch (failure: ApiFailure) { assertEquals(ApiFailure.Kind.TOO_SOON, failure.kind) }
            gate.limited("120", now)
            val session = SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic", "test-UA")!!
            try { InstagramApi(RequestGate(context, "test_gate")).read(session); fail("API must reject before opening a connection") }
            catch (failure: ApiFailure) { assertEquals(ApiFailure.Kind.LIMITED, failure.kind) }
            try { RequestGate(context, "test_gate").begin(now + 16000); fail("Limit must survive restart") }
            catch (failure: ApiFailure) { assertEquals(ApiFailure.Kind.LIMITED, failure.kind) }
            // Expiry allows a user-triggered request, without automatically sending one.
            recreated.begin(gate.blockedUntil() + 1)
            assertTrue(recreated.manualOnly())
            recreated.succeeded(); assertFalse(recreated.manualOnly())
        } finally { settings.edit().clear().commit() }
    }
    @Test fun repeatedRestrictionsEscalateAndExpiredPauseNeverEnablesAutomaticRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = context.getSharedPreferences("escalation_test", 0)
        settings.edit().clear().commit()
        try {
            val gate = RequestGate(context, "escalation_test")
            val now = System.currentTimeMillis()
            gate.limited(null, now, "HTTP 429")
            val firstEnd = gate.blockedUntil()
            assertEquals(now + 3600000, firstEnd)
            assertEquals(now, gate.lastLimitedAt()); assertEquals("HTTP 429", gate.limitReason())
            gate.limited(null, firstEnd + 1)
            assertEquals(firstEnd + 1 + 7200000, gate.blockedUntil())
            val profile = Profile("123", "synthetic", 1, 2, 1000)
            assertFalse(RequestGate(context, "escalation_test").autoAllowed(profile, gate.blockedUntil() + 1))
            assertFalse(gate.reserveAutomatic(gate.blockedUntil() + 1))
            gate.succeeded()
            assertTrue(gate.reserveAutomatic(now)); assertFalse(gate.reserveAutomatic(now + 16000))
            assertTrue(gate.reserveAutomatic(now + RequestGate.AUTO_INTERVAL_MS))
        } finally { settings.edit().clear().commit() }
    }
}
