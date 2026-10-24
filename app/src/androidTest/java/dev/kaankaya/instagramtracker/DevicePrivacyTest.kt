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
            val account = SavedAccount(session, Profile("123", "synthetic", 0, 7, 1000))
            vault.write(account)
            assertEquals(account, vault.read())
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("synthetic-private-token"))
            val data = file.readBytes(); data[data.lastIndex] = (data.last().toInt() xor 1).toByte(); file.writeBytes(data)
            try { vault.read(); fail("Tampered data must fail") } catch (_: java.security.GeneralSecurityException) { }
            vault.clear(); assertFalse(vault.exists())
        } finally { vault.clear() }
    }
    @Test fun productionManifestHasNoBackupOrExtraPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertEquals(setOf("android.permission.INTERNET"), info.requestedPermissions.orEmpty().toSet())
        assertEquals(0, info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
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
            recreated.begin(now + 120001)
        } finally { settings.edit().clear().commit() }
    }
}
