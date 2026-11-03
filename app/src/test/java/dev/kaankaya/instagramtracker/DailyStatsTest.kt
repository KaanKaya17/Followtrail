package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DailyStatsTest {
    private fun fixture(): JSONObject = JSONObject(listOf(File("../test-fixtures/daily-stats.json"),File("test-fixtures/daily-stats.json"))
        .first { it.isFile }.readText())
    private fun documents(): List<JSONObject> {
        val f=fixture();val rows=f.getJSONArray("observations");val years=linkedMapOf<Int,JSONObject>()
        for(i in 0 until rows.length()) {
            val r=rows.getJSONObject(i);val at=r.getLong("at")
            val p=Profile("200","qa.person",r.getLong("followers"),r.getLong("following"),at,if(r.isNull("posts"))null else r.getLong("posts"))
            DailyStats.observe(years.getOrPut(DailyStats.year(at)){DailyStats.empty("100","200",DailyStats.year(at))},p)
        }
        return years.values.toList()
    }
    @Test fun sharedUtcLeapDayDuplicateUnknownPostsAndYearRollover() {
        val documents=documents();documents.forEach {DailyStats.validate(it,"100","200")}
        assertEquals(listOf(2024,2025),documents.map {it.getInt("year")})
        val actual=documents.flatMap {doc->doc.getJSONObject("days").let {days->days.keys().asSequence().map(days::getJSONObject).toList()}}.sortedBy {it.getLong("day")}
        val expected=fixture().getJSONArray("expectedRows")
        assertEquals(expected.length(),actual.size)
        for(i in actual.indices) {
            val wanted=expected.getJSONObject(i)
            assertEquals(wanted.keys().asSequence().toSet(),actual[i].keys().asSequence().toSet())
            for(key in wanted.keys()) {
                if(key in DailyStats.metrics) assertEquals((0..5).map { wanted.getJSONArray(key).getLong(it) },(0..5).map { actual[i].getJSONArray(key).getLong(it) })
                else assertEquals(wanted.getLong(key),actual[i].getLong(key))
            }
        }
        val result=DailyStats.series(documents,"followers",0,1735732800000)
        assertEquals(4,result.observedDays);assertEquals(6,result.samples)
        assertTrue(result.points.zipWithNext().any {(a,b)->b.bucket-a.bucket>1})
    }
    @Test fun clearWatermarkSkipsOldFactsButAllowsNewObservationsInSameDay() {
        val day=1709164800000L;val doc=DailyStats.empty("100","200",2024)
        assertFalse(DailyStats.observe(doc,Profile("200","qa",20,1,day+10),day+20))
        assertTrue(DailyStats.observe(doc,Profile("200","qa",30,2,day+30),day+20))
        val result=DailyStats.series(listOf(doc),"followers",0,day+40,day+20)
        assertEquals(1L,result.samples);assertEquals(30L,result.points.single().minimum)
    }
    @Test fun backwardObservationDoesNotChangeRecordedDayAndUnknownPostsAreNotZero() {
        val doc=DailyStats.empty("100","200",2024);val at=1709168400000L
        DailyStats.observe(doc,Profile("200","qa",20,1,at,5));DailyStats.observe(doc,Profile("200","qa",25,2,at+1000,null))
        val before=doc.toString();assertFalse(DailyStats.observe(doc,Profile("200","qa",999,2,at-1000)));assertEquals(before,doc.toString())
        val point=DailyStats.series(listOf(doc),"posts",0,at+1000).points.single();assertEquals(5L,point.value);assertEquals(at,point.at)
    }
    @Test fun rangeAndLargePeriodBoundedTo720PointsWithoutLosingCoverage() {
        val first=1709164800000L;val years=linkedMapOf<Int,JSONObject>()
        repeat(1440){i->val at=first+i*DailyStats.DAY+1;val year=DailyStats.year(at)
            DailyStats.observe(years.getOrPut(year){DailyStats.empty("100","200",year)},Profile("200","qa",i.toLong(),2,at))}
        val all=DailyStats.series(years.values.toList(),"followers",0,first+1440*DailyStats.DAY)
        assertTrue(all.points.size<=720);assertEquals(1440,all.observedDays);assertEquals(1439L,all.points.last().value)
        assertEquals(7,DailyStats.series(years.values.toList(),"followers",7,first+1439*DailyStats.DAY).observedDays)
    }
    @Test fun malformedAndForeignDocumentsAreRejected() {
        val doc=documents().first()
        for(change in listOf<(JSONObject)->Unit>({it.put("owner","300")},{it.put("year",2025)},{it.put("version",true)},
            {it.getJSONObject("days").getJSONObject(it.getJSONObject("days").keys().next()).put("samples",-1)})) {
            val bad=JSONObject(doc.toString());change(bad)
            assertThrows(IllegalArgumentException::class.java){DailyStats.validate(bad,"100","200")}
        }
    }
    @Test fun manyYearsRemainSmallWithoutBiographyAvatarOrSessionData() {
        val doc=DailyStats.empty("100","200",2024);val start=1704067200000L
        repeat(366){i->DailyStats.observe(doc,Profile("200","qa",100,50,start+i*DailyStats.DAY+1,10))}
        DailyStats.validate(doc,"100","200")
        assertTrue(doc.toString().toByteArray().size<160000)
        for(secret in listOf("cookie","username","biography","avatar"))assertFalse(doc.toString().contains(secret))
    }
}
