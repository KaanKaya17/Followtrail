package dev.kaankaya.instagramtracker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

/** Android 6–7 Keystore AES-GCM corrupts single large operations; every record size must round-trip. */
class LocalCipherSizesTest {
    @Test fun everySupportedRecordSizeRoundTripsAndKeepsTheEnvelopeFormat() {
        val cipher = LocalCipher("instagramtracker.test.sizes.v1", SessionVault.ACCOUNT_BYTES)
        try {
            for (size in listOf(1, 16 * 1024, 64 * 1024, 128 * 1024, 512 * 1024, SessionVault.ACCOUNT_BYTES)) {
                val data = ByteArray(size) { (it % 251).toByte() }
                val sealed = cipher.seal(data, "record".toByteArray())
                assertEquals("size $size", 12 + size + 16, sealed.size)
                assertArrayEquals("size $size", data, cipher.open(sealed, "record".toByteArray()))
                assertThrows(AEADBadTagException::class.java) { cipher.open(sealed, "other".toByteArray()) }
            }
        } finally { cipher.destroy() }
    }
}
