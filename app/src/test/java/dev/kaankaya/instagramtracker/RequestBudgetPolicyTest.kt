package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RequestBudgetPolicyTest {
    private val now = 2_000_000_000_000L
    @Test fun oneAccountBudgetDoesNotSpendAnotherAccountsReservationsButDeviceStillBoundsBoth() {
        val first = List(RequestDefaults.ACCOUNT_HOURLY) { now }
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.nextAt(first, now, first))
        assertEquals(0L, RequestBudgetPolicy.nextAt(first, now, emptyList()))
        val device = first + first
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.nextAt(device, now, emptyList()))
    }
    @Test fun largerLegacyHistoryRemainsCountedAfterLoweringTheQuota() {
        val legacy = List(4000) { now - RequestBudgetPolicy.HOUR * 2 }
        assertEquals(now + RequestBudgetPolicy.HOUR * 22, RequestBudgetPolicy.nextAt(legacy, now))
        assertEquals(legacy.size + 1, RequestBudgetPolicy.reserve(legacy, now).size)
        assertEquals(emptyList<Long>(), RequestBudgetPolicy.reserve(legacy, now + RequestBudgetPolicy.DAY).dropLast(1))
    }
    @Test fun codecRejectsMalformedIdentityAndTimeDataAndRoundtripsOnlyOpaqueOwners() {
        val key = RequestBudgetPolicy.ownerKey("123")
        assertEquals(64, key.length); assertNotEquals(key, RequestBudgetPolicy.ownerKey("124"))
        val data = mapOf(key to listOf(now, now + 1))
        assertEquals(data, RequestBudgetCodec.accounts(RequestBudgetCodec.encode(data)))
        for (raw in listOf("{\"123\":[1]}", JSONObject().put(key, org.json.JSONArray(listOf(2, 1))).toString(), "[]"))
            assertThrows(Exception::class.java) { RequestBudgetCodec.accounts(raw) }
        for (raw in listOf("[true]", "[-1]", "[1.5]", "[2,1]", "[\"1\"]"))
            assertThrows(Exception::class.java) { RequestBudgetCodec.times(raw) }
    }
}
