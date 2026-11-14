package dev.kaankaya.instagramtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.Base64
import javax.crypto.AEADBadTagException

class PortableBackupTest {
    @Test fun readsWindowsArchiveAndRejectsWrongPasswordOrTampering() {
        val fixture = "VFJLQkFLMDGjHOYEO+QlCXVL29tcSYCA0Z61nyYOML2oc8uTmBK8SOGzhBXF2Gl8p/MGzVZRFTcKMo0PYv260emJ1u1TCwJCpO4ZFttS99QXVKj9c3DDZjgnIW2r3fmdBhga1387H8XnKvBeIYqrxR6czzTo78736klr138a3kMR3oeUI0YVjYwS+MP4jRcUAbqGm/LsoJqmvtBhlLcrNaRquSK1YaCDxsW7AiGqadlgK0Q0Y8R4ITTC0xb6be68Z+f5EG+qvfV5AIUxa9J3iMfCOBauDk5VMC0se/8YoAQw9T/CMpbbhQcGyrsNdUVCCO/QsuRPlPtY9Kfynn72H+JaMqnaAZTIEEUQJeGYT1c3iSNICr682U2a9/nVAZ46OmOn+xJl"
        val bytes = Base64.getDecoder().decode(fixture)
        val password = "örnek parola 🌿 123"
        val archive = PortableBackup.open(bytes.inputStream(), password)
        assertEquals("Türkçe 🌿", archive.getJSONArray("people").getJSONObject(0).getString("note"))
        assertThrows(AEADBadTagException::class.java) { PortableBackup.open(bytes.inputStream(), "wrong password") }
        val changed = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { PortableBackup.open(changed.inputStream(), password) }
        val person = archive.getJSONArray("people").getJSONObject(0)
        val longNote = "ş".repeat(1100) + "a".repeat(899) + "🌿"
        person.put("note", longNote)
        val exported = PortableBackup.seal(archive, password)
        assertEquals(archive.toString(), PortableBackup.open(exported.inputStream(), password).toString())
        person.put("note", longNote + "ş")
        assertThrows(IllegalArgumentException::class.java) { PortableBackup.validate(archive) }
        // Optional handoff to the Windows integration check.
        System.getenv("TRACKER_PORTABLE_QA_OUTPUT")?.let { File(it).writeBytes(exported) }
    }
}
