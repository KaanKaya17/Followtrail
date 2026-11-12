package dev.kaankaya.instagramtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstagramArchiveTest {
    @get:Rule val folder = TemporaryFolder()
    private val prefix = "connections/followers_and_following/"

    private fun read(vararg entries: Pair<String, String>): InstagramArchive {
        val file = folder.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, value) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file.inputStream().use { InstagramArchive.read(it, folder.root) }
    }

    @Test fun readsSplitRelationshipFilesAndIgnoresOtherCategories() {
        val lists = read(
            prefix + "followers_1.json" to """[{"string_list_data":[{"value":"Alice"}]}]""",
            prefix + "followers_2.json" to """[{"string_list_data":[{"value":"alice"}]},{"string_list_data":[{"value":"carol"}]}]""",
            prefix + "following.json" to """{"relationships_following":[{"title":"alice","string_list_data":[{"href":"https://www.instagram.com/_u/alice"}]},{"string_list_data":[{"href":"https://www.instagram.com/_u/bob"}]}]}""",
            "messages/inbox/ignored.json" to "invalid JSON",
        )
        assertEquals(listOf("alice", "carol"), lists.followers)
        assertEquals(listOf("alice", "bob"), lists.following)
        assertEquals(1, folder.root.listFiles()?.size)
    }

    @Test fun distinguishesEmptyAndMissingAndRejectsMissingParts() {
        val lists = read(prefix + "followers_1.json" to "[]")
        assertEquals(emptyList<String>(), lists.followers)
        assertNull(lists.following)
        assertThrows(IllegalArgumentException::class.java) { read(prefix + "followers_2.json" to "[]") }
        assertThrows(IllegalArgumentException::class.java) {
            read(prefix + "followers_1.json" to "[]", "other/" + prefix + "following.json" to "[]")
        }
        assertThrows(IllegalArgumentException::class.java) {
            read(prefix + "followers_1.json" to """[{"title":"alice","string_list_data":[{"value":"bob"}]}]""")
        }
    }
}
