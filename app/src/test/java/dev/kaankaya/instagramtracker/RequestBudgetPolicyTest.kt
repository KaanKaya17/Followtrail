package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RequestBudgetPolicyTest {
    @Test fun fullDeviceDayRoundtripsWithoutTruncatingAccountReservations() {
        val count = RequestDefaults.DEVICE_DAILY
        val times = List(count) { now - 2 * RequestBudgetPolicy.HOUR }
        assertEquals(count, RequestBudgetCodec.times(org.json.JSONArray(times)).size)
        val accounts = mapOf(RequestBudgetPolicy.ownerKey("100") to times.take(count / 2),
            RequestBudgetPolicy.ownerKey("200") to times.drop(count / 2))
        assertEquals(accounts, RequestBudgetCodec.accounts(RequestBudgetCodec.encode(accounts)))
        assertEquals(now + 22 * RequestBudgetPolicy.HOUR, RequestBudgetPolicy.nextAt(times, now))
        assertThrows(Exception::class.java) { RequestBudgetCodec.times(org.json.JSONArray(times + now)) }
    }
    @Test fun mediaLaneHasItsOwnLedgerIndependentOfTheApiBudget() {
        assertTrue(RequestDefaults.MEDIA_MAX_SPACING_MS <= RequestDefaults.MIN_SPACING_MS)
        val fullApiHour = List(RequestDefaults.DEVICE_HOURLY) { now }
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.nextAt(fullApiHour, now))
        assertEquals(0L, RequestBudgetPolicy.mediaNextAt(emptyList(), now))
        val fullMediaHour = List(RequestDefaults.MEDIA_DEVICE_HOURLY) { now }
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.mediaNextAt(fullMediaHour, now))
        assertEquals(0L, RequestBudgetPolicy.mediaNextAt(fullMediaHour.drop(1), now))
        val fullMediaDay = List(RequestDefaults.MEDIA_DEVICE_DAILY) { now - 2 * RequestBudgetPolicy.HOUR }
        assertEquals(now + 22 * RequestBudgetPolicy.HOUR, RequestBudgetPolicy.mediaNextAt(fullMediaDay, now))
        assertEquals(fullMediaDay, RequestBudgetCodec.times(org.json.JSONArray(fullMediaDay)))
    }
    @Test fun automaticProfilesLeaveSharedHeadroomWithoutResettingTheLedger() {
        val count = RequestDefaults.ACCOUNT_HOURLY * RequestDefaults.AUTO_PROFILE_PERCENT / 100
        val times = List(count) { now }
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.profileNextAt(times, now, times))
        assertEquals(0L, RequestBudgetPolicy.nextAt(times, now, times))
        assertEquals(0L, RequestBudgetPolicy.profileNextAt(times, now, emptyList()))
        val shared = List(RequestDefaults.DEVICE_HOURLY * RequestDefaults.AUTO_PROFILE_PERCENT / 100) { now }
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.profileNextAt(shared, now))
        assertEquals(now + RequestBudgetPolicy.HOUR, RequestBudgetPolicy.profileNextAt(shared, now - 30))
        assertEquals(0L, RequestBudgetPolicy.nextAt(shared, now))
        val daily = List(RequestDefaults.ACCOUNT_DAILY * RequestDefaults.AUTO_PROFILE_PERCENT / 100) { now - 2 * RequestBudgetPolicy.HOUR }
        assertEquals(now + 22 * RequestBudgetPolicy.HOUR, RequestBudgetPolicy.profileNextAt(daily, now, daily))
    }
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
        assertEquals(now + RequestBudgetPolicy.HOUR * 22, RequestBudgetPolicy.nextAt(legacy, now, legacy))
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
