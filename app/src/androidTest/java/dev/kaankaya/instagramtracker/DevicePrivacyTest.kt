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
}
