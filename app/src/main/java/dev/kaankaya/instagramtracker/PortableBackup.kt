package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object PortableBackup {
    private val magic = "TRKBAK01".toByteArray(Charsets.US_ASCII)
    private const val headerSize = 36
    private const val limit = 16 * 1024 * 1024
    private const val iterations = 600_000
    private const val maxTime = 253402300799999L

    // RFC 8018, one SHA-256 block. Android 24/25 expose HmacSHA256 but
    // do not expose the PBKDF2 SHA-256 SecretKeyFactory.
    private fun key(password: String, salt: ByteArray): ByteArray {
        val bytes = password.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..512)
        val mac = Mac.getInstance("HmacSHA256")
        try { mac.init(SecretKeySpec(bytes, "HmacSHA256")) } finally { bytes.fill(0) }
        val result = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        var previous = result.copyOf()
        var next = ByteArray(32)
        repeat(iterations - 1) {
            mac.update(previous)
            mac.doFinal(next, 0)
            for (index in result.indices) result[index] = (result[index].toInt() xor next[index].toInt()).toByte()
            val swap = previous
            previous = next
            next = swap
        }
        previous.fill(0)
        next.fill(0)
        return result
    }

    private fun cipher(mode: Int, password: String, header: ByteArray): Cipher {
        val key = key(password, header.copyOfRange(8, 24))
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(24, headerSize)))
                updateAAD(header)
            }
        } finally { key.fill(0) }
    }

    fun seal(value: JSONObject, password: String): ByteArray {
        require(password.codePointCount(0, password.length) >= 12)
        val plain = validate(value).toString().toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= limit)
            val header = magic + ByteArray(28).also(SecureRandom()::nextBytes)
            return header + cipher(Cipher.ENCRYPT_MODE, password, header).doFinal(plain)
        } finally { plain.fill(0) }
    }

    fun open(input: InputStream, password: String): JSONObject {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit + headerSize + 16)
            output.write(buffer, 0, count)
        }
        val raw = output.toByteArray()
        require(raw.size > headerSize + 16 && raw.copyOfRange(0, 8).contentEquals(magic))
        val plain = cipher(Cipher.DECRYPT_MODE, password, raw.copyOf(headerSize)).doFinal(raw, headerSize, raw.size - headerSize)
        try {
            val decoder = org.json.JSONTokener(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(plain)).toString())
            val value = decoder.nextValue() as JSONObject
            require(decoder.nextClean() == '\u0000')
            return validate(value)
        } finally { plain.fill(0) }
    }

    fun validate(value: JSONObject): JSONObject {
        fun fields(row: JSONObject, required: Set<String>, optional: Set<String> = emptySet()) {
            val actual = row.keys().asSequence().toSet()
            require(actual.containsAll(required) && (required + optional).containsAll(actual))
        }
        fun string(row: JSONObject, key: String, max: Int): String =
            (row.get(key) as String).also { require(it.toByteArray(Charsets.UTF_8).size <= max) }
        fun number(row: JSONObject, key: String, max: Long = 9007199254740991L): Long {
            val item = row.get(key)
            require(item is Int || item is Long)
            return (item as Number).toLong().also { require(it in 0..max) }
        }
        fun identity(row: JSONObject): String {
            if (row.has("name")) string(row, "name", 8192)
            if (row.has("username")) require(ProfileInput.validUsername(string(row, "username", 30)))
            return InstagramIdentity.id(string(row, "id", 30))
        }
        val metrics = listOf("followers", "following", "posts")
        fun profile(row: JSONObject): String {
            fields(row, setOf("id", "username", "at"), setOf("name", "bio", "private") + metrics)
            number(row, "at", maxTime)
            if (row.has("bio")) string(row, "bio", 8192)
            if (row.has("private")) require(row.get("private") is Boolean)
            for (metric in metrics) if (row.has(metric)) number(row, metric)
            return identity(row)
        }
        fields(value, setOf("version", "account", "people", "history", "lists", "daily"))
        require(number(value, "version") == 1L)
        val owner = profile(value.getJSONObject("account"))
        val targets = mutableSetOf(owner)
        for (row in value.getJSONArray("people").objects(200)) {
            fields(row, setOf("profile", "note", "group", "favorite"))
            require(targets.add(profile(row.getJSONObject("profile"))) && row.get("favorite") is Boolean)
            // Portable files may contain notes and groups authored on Windows.
            val note = row.get("note") as String
            val group = row.get("group") as String
            require(note.codePointCount(0, note.length) <= 2000)
            require(group.codePointCount(0, group.length) <= 80)
        }
        for (row in value.getJSONArray("history").objects(74000)) require(profile(row) in targets)
        for (row in value.getJSONArray("lists").objects(2412)) {
            fields(row, setOf("target", "direction", "at", "members"))
            require(row.get("target") in targets && row.get("direction") in metrics.take(2))
            number(row, "at", maxTime)
            val ids = mutableSetOf<String>()
            for (member in row.getJSONArray("members").objects(20000)) {
                fields(member, setOf("id"), setOf("username", "name"))
                require(ids.add(identity(member)))
            }
        }
        for (row in value.getJSONArray("daily").objects(10000)) {
            fields(row, setOf("version", "owner", "target", "year", "days"))
            val target = string(row, "target", 30)
            require(target in targets)
            DailyStats.validate(row, owner, target)
        }
        return value
    }

    fun JSONArray.objects(max: Int): Sequence<JSONObject> {
        require(length() <= max)
        return (0 until length()).asSequence().map(::getJSONObject)
    }

    fun snapshot(context: Context, expected: Session): JSONObject = synchronized(AccountCatalog.lock) {
        val vault = SessionVault(context)
        val saved = vault.read()?.takeIf { it.session == expected } ?: error("Account changed")
        val owner = saved.profile.userId
        val targets = listOf(owner) + saved.people.map { it.latest.userId }
        val archive = FollowListVault(context, owner = owner).archive(expected)
        fun profile(row: Profile) = JSONObject().put("id", row.userId).put("username", row.username)
            .put("at", row.observedAt).put("followers", row.followers).put("following", row.following).apply {
                row.name?.let { put("name", it) }
                row.posts?.let { put("posts", it) }
                row.details.biography.value?.let { put("bio", it) }
                row.details.privateAccount?.let { put("private", it) }
            }
        val people = JSONArray()
        val history = JSONArray()
        for (person in saved.people) {
            people.put(JSONObject().put("profile", profile(person.latest)).put("note", person.note)
                .put("group", person.group).put("favorite", person.favorite))
            person.history.forEach { history.put(profile(it)) }
        }
        val lists = JSONArray()
        for (entry in archive.targets.filter { it.target in targets }) {
            for (direction in FollowDirection.entries) {
                val runs = listOfNotNull(entry.previous?.run(direction), entry.snapshot?.run(direction))
                    .distinctBy { it.latest.observedAt }
                for (run in runs.filter { it.comparable() }) {
                    val members = JSONArray()
                    run.users.forEach { member ->
                        members.put(JSONObject().put("id", member.id).apply {
                            member.username?.let { put("username", it) }
                            member.name?.let { put("name", it) }
                        })
                    }
                    lists.put(JSONObject().put("target", entry.target).put("direction", direction.path)
                        .put("at", run.latest.observedAt).put("members", members))
                }
            }
        }
        JSONObject().put("version", 1).put("account", profile(saved.profile)).put("people", people)
            .put("history", history).put("lists", lists).put("daily", JSONArray().apply {
                vault.daily.exportForBackup(owner, targets) { put(it) }
            })
    }
}
