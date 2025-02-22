package io.em2m.search.es

import io.em2m.geo.feature.Feature
import io.em2m.search.core.model.*
import org.junit.Before
import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import kotlin.properties.Delegates

class EsSearchDaoAggTest : FeatureTestBase() {

    private var searchDao: SyncDao<Feature> by Delegates.notNull()

    @Before
    override fun before() {
        super.before()
        searchDao = EsSyncDao(esClient, index, type, Feature::class.java, idMapper)
    }

    @Test
    fun testTerm() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(TermsAgg("properties.status", key = "statuses", missing = "Missing")))
        val result = searchDao.search(request)
        assertEquals(46, result.totalItems)
        assertEquals(0, result.items?.size)
        val statuses = result.aggs["statuses"] ?: error("statuses should not be null")
        val buckets = statuses.buckets ?: error("buckets should not be null")
        assertEquals(2, buckets.size)
        assertEquals("reviewed", buckets[0].key)
        assertEquals("automatic", buckets[1].key)
        assertEquals(46, buckets[0].count + buckets[1].count)
    }

    @Test
    fun testMinDocCount() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(TermsAgg("properties.status", key = "statuses", missing = "Missing", minDocCount = 4)))
        val result = searchDao.search(request)
        assertEquals(46, result.totalItems)
        assertEquals(0, result.items?.size)
        val statuses = result.aggs["statuses"] ?: error("statuses should not be null")
        val buckets = statuses.buckets ?: error("buckets should not be null")
        assertEquals(1, buckets.size)
        assertEquals("reviewed", buckets[0].key)
    }

    @Test
    fun testMissingTerms() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(TermsAgg("properties.alert", key = "alerts", missing = "No Alert")))
        val result = searchDao.search(request)
        assertEquals(46, result.totalItems)
        assertEquals(0, result.items?.size)
        val alerts = result.aggs["alerts"] ?: error("alerts should not be null")
        val buckets = alerts.buckets ?: error("buckets should not be null")
        assertEquals(2, buckets.size)
        assertEquals("No Alert", buckets[0].key)
        assertEquals("green", buckets[1].key)
        assertEquals(46, buckets[0].count + buckets[1].count)
    }

    @Test
    fun testCardinality() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(CardinalityAgg("properties.tz", key = "tz")))
        val result = searchDao.search(request)
        val tz = result.aggs["tz"] ?: error("tz should not be null")
        val buckets = tz.buckets ?: error("buckets should not be null")
        assertEquals(46, result.totalItems)
        assertEquals(0, result.items?.size)
        assertEquals(11, buckets[0].count)
    }

    @Test
    fun testFilters() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(FiltersAgg(mapOf("green_alerts" to TermQuery("properties.alert", "green")), "test")))
        val result = searchDao.search(request)
        val agg = result.aggs["test"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(1, buckets.size)
        assertEquals(3, buckets[0].count)
    }

    @Test
    fun testHistogram() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(HistogramAgg("properties.mag", null, 1.0, key = "magnitude")))
        val result = searchDao.search(request)
        val missing = result.aggs["magnitude"] ?: error("agg should not be null")
        val buckets = missing.buckets ?: error("buckets should not be null")
        assertEquals(4, buckets.size)
        assertEquals(46, buckets.map { it.count }.sum())
    }

    @Test
    fun testMissing() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(MissingAgg("properties.alert", key = "missing")))
        val result = searchDao.search(request)
        val missing = result.aggs["missing"] ?: error("agg should not be null")
        val buckets = missing.buckets ?: error("buckets should not be null")
        assertEquals(1, buckets.size)
        assertEquals(43, buckets[0].count)
    }

    @Test
    fun testRange() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(RangeAgg("properties.mag", key = "magnitude",
                ranges = listOf(Range(from = 4.0, key = "4+"), Range(from = 0, to = 4.0, key = "0-4")))))
        val result = searchDao.search(request)
        val agg = result.aggs["magnitude"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(2, buckets.size)
        assertEquals(46, buckets.map { it.count }.sum())
        assertEquals("4+", buckets[0].key)
        assertEquals(4.0, buckets[0].from)
    }

    @Test
    fun testStats() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(StatsAgg("properties.mag", key = "magnitude")))
        val result = searchDao.search(request)
        val missing = result.aggs["magnitude"] ?: error("agg should not be null")
        val buckets = missing.buckets ?: error("buckets should not be null")
        assertEquals(1, buckets.size)
        assertEquals(2.5, buckets[0].stats?.min)
        assertEquals(46L, buckets[0].stats?.count)
    }

    @Test
    fun testDateRange() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(DateRangeAgg("properties.time", key = "magnitude",
                ranges = listOf(Range(from = "1408447319000", to = "now")))))
        val result = searchDao.search(request)
        val agg = result.aggs["magnitude"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(1, buckets.size)
        assertEquals(21, buckets.map { it.count }.sum())
    }

    @Test
    fun testDateRangeTZ() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(DateRangeAgg("properties.time", key = "magnitude",
                ranges = listOf(Range(from = "1408447319000", to = "now")), timeZone = "+:05:00")))
        val result = searchDao.search(request)
        val agg = result.aggs["magnitude"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(1, buckets.size)
        assertEquals(21, buckets.map { it.count }.sum())
    }

    @Test
    fun testDateHistogram() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(DateHistogramAgg("properties.time", interval = "hour", key = "time")))
        val result = searchDao.search(request)
        val agg = result.aggs["time"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(23, buckets.size)
        assertEquals(46, buckets.map { it.count }.sum())
    }

    @Test
    fun testGeoDistance() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(GeoDistanceAgg("geometry.coordinates", key = "test",
                origin = Coordinate(-79.0, 38.0), ranges = listOf(Range(from = 0, to = 1000, key = "<1000miles"), Range(from = 1000)))))
        val result = searchDao.search(request)
        val agg = result.aggs["test"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(2, buckets.size)
        assertEquals(46, buckets.map { it.count }.sum())
        assertEquals("<1000miles", buckets[0].key)
    }

    @Test
    fun testGeoHash() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(GeoHashAgg("geometry.coordinates", precision = 1, key = "geohash")))
        val result = searchDao.search(request)
        val agg = result.aggs["geohash"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(11, buckets.size)
        assertEquals(46, buckets.map { it.count }.sum())
    }

    @Test
    fun testGeoBounds() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(GeoBoundsAgg("geometry.coordinates", key = "geo_bounds")))
        val result = searchDao.search(request)
        val agg = result.aggs["geo_bounds"] ?: error("agg should not be null")
        agg.value as Envelope? ?: error("Envelope should not be null")
    }

    @Test
    fun testGeoCentroid() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(GeoCentroidAgg("geometry.coordinates", key = "centroid")))
        val result = searchDao.search(request)
        val agg = result.aggs["centroid"] ?: error("agg should not be null")
        agg.value as Coordinate? ?: error("Coordinate should not be null")
    }

    @Test
    fun testNestedAggs() {
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(
                GeoHashAgg("geometry.coordinates", precision = 1, key = "geohash", aggs = listOf(GeoCentroidAgg("geometry.coordinates", key = "centroid")))))
        val result = searchDao.search(request)
        val agg = result.aggs["geohash"] ?: error("agg should not be null")
        val buckets = agg.buckets ?: error("buckets should not be null")
        assertEquals(11, buckets.size)
        assertEquals(46, buckets.map { it.count }.sum())
    }

    @Test
    fun testNative() {
        val native = """{ "percentiles": { "field": "properties.mag"}} """
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(NativeAgg(native, key = "test")))
        val result = searchDao.search(request)
        val agg = result.aggs["test"] ?: error("agg should not be null")
        val value = agg.value as Map<*, *>? ?: error("Envelope should not be null")
        assertTrue("Should contain key 'values'", value.containsKey("values"))
    }

    @Test
    fun testSubAgg() {
        val native = """
{
  "geohash_grid" : {
    "field" : "geometry.coordinates",
    "precision" : 3
  },
  "aggs" : {
        "bounds" : {
            "geo_bounds" : { "field" : "geometry.coordinates" }
         }
     }
  }
}"""
        val request = SearchRequest(0, 0, MatchAllQuery(), aggs = listOf(NativeAgg(native, key = "test")))
        val result = searchDao.search(request)
        val agg = result.aggs["test"] ?: error("agg should not be null")
        //val value = agg.value as Map<*, *>? ?: error("Envelope should not be null")
        //assertTrue("Should contain key 'values'", value.containsKey("values"))
        assertNotNull(agg)
    }


}
