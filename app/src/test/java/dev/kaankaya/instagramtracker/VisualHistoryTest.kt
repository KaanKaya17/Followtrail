package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class VisualHistoryTest {
    private fun profile(at:Long,bio:ProfileText=ProfileText.known("old"))=Profile("200","qa.person",1,2,at,details=ProfileDetails(biography=bio,pictureId=ProfileText.known("one")))
    @Test fun optInChangeOnlyAndMissingVsEmpty() {
        val d=VisualHistory.empty("100");VisualHistory.observe(d,profile(1));assertEquals(0,d.getJSONObject("profiles").length())
        VisualHistory.target(d,"200").put("enabled",true)
        VisualHistory.observe(d,profile(2));VisualHistory.observe(d,profile(3,ProfileText()))
        val a=VisualHistory.target(d,"200").getJSONArray("entries");assertEquals(1,a.length());assertEquals("old",a.getJSONObject(0).getString("bio"))
        VisualHistory.observe(d,profile(4,ProfileText.known("")));assertEquals(2,a.length());VisualHistory.validate(d)
    }
    @Test fun archiveSurvivesCountRotationAndStopsAtVisibleLimit() {
        val d=VisualHistory.empty("100");VisualHistory.target(d,"200").put("enabled",true)
        for(i in 1..400) VisualHistory.observe(d,profile(i.toLong(),ProfileText.known("bio$i")))
        val t=VisualHistory.target(d,"200");assertEquals(200,t.getJSONArray("entries").length());assertTrue(t.getBoolean("full"));assertEquals("bio1",t.getJSONArray("entries").getJSONObject(0).getString("bio"));VisualHistory.validate(d)
    }
    @Test fun sameImageDeduplicatesAndClearStopsCapture() {
        val d=VisualHistory.empty("100");VisualHistory.target(d,"200").put("enabled",true)
        val hash="a".repeat(64);VisualHistory.observe(d,profile(1),hash to "synthetic");VisualHistory.observe(d,profile(2),hash to "synthetic")
        assertEquals(1,d.getJSONObject("images").length());assertEquals(1,VisualHistory.target(d,"200").getJSONArray("entries").length())
        VisualHistory.clear(d,"200");VisualHistory.observe(d,profile(3));assertEquals(0,d.getJSONObject("images").length());assertEquals(0,d.getJSONObject("profiles").length())
    }
    @Test fun diffPreservesBothTextsIncludingUnicodeAndLongFallback() {
        for ((a,b) in listOf("İstanbul ☕\nhttps://a.test" to "Ankara ☕\nhttps://b.test", "x ".repeat(2000) to "y ".repeat(2000), "" to "new")) {
            val p=BiographyDiff.compare(a,b)
            assertEquals(a,p.filter { it.kind!="add" }.joinToString("") { it.text });assertEquals(b,p.filter { it.kind!="remove" }.joinToString("") { it.text })
        }
    }
}
