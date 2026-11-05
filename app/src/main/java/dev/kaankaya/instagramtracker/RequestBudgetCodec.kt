package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** One bounded interpretation of timestamp-only reservations from older and current stores. */
internal object RequestBudgetCodec {
    private const val MAX_JSON = 256 * 1024
    fun times(value: JSONArray): List<Long> {
        require(value.length() <= RequestBudgetPolicy.MAX_STORED)
        val result = (0 until value.length()).map { index ->
            val entry = value.get(index); require(entry is Long || entry is Int)
            (entry as Number).toLong().also { require(it >= 0) }
        }
        require(result == result.sorted())
        return result
    }
    fun times(raw: String): List<Long> { require(raw.length <= MAX_JSON); return times(JSONArray(raw)) }
    fun accounts(raw: String): Map<String, List<Long>> {
        require(raw.length <= MAX_JSON)
        val value = JSONObject(raw); require(value.length() <= RequestBudgetPolicy.MAX_STORED)
        val result = value.keys().asSequence().associateWith { key ->
            require(key.matches(Regex("[a-f0-9]{64}"))); times(value.getJSONArray(key))
        }
        require(result.values.sumOf { it.size } <= RequestBudgetPolicy.MAX_STORED)
        return result
    }
    fun encode(accounts: Map<String, List<Long>>): String = JSONObject().apply {
        accounts.toSortedMap().forEach { (key, times) -> put(key, JSONArray(times)) }
    }.toString().also { accounts(it) }
}
