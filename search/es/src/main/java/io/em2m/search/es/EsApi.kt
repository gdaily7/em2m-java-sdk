@file:Suppress("unused")

package io.em2m.search.es

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
import com.fasterxml.jackson.databind.node.ObjectNode
import feign.Headers
import feign.Param
import feign.RequestLine
import io.em2m.utils.coerce
import org.locationtech.jts.geom.Envelope
import java.util.*


// models

open class EsQuery() {
}

class EsBoolQuery(
        must: List<EsQuery> = emptyList(),
        filter: List<EsQuery> = emptyList(),
        should: List<EsQuery> = emptyList(),
        mustNot: List<EsQuery> = emptyList()) : EsQuery() {

    val bool: Map<String, MutableList<EsQuery>> = mapOf(
            "must" to must.toMutableList(),
            "filter" to filter.toMutableList(),
            "should" to should.toMutableList(),
            "must_not" to mustNot.toMutableList()
    )

    fun must(query: EsQuery): EsBoolQuery {
        bool["must"]?.add(query)
        return this
    }

    fun filter(query: EsQuery): EsBoolQuery {
        bool["filter"]?.add(query)
        return this
    }

    fun should(query: EsQuery): EsBoolQuery {
        bool["should"]?.add(query)
        return this
    }

    fun mustNot(query: EsQuery): EsBoolQuery {
        bool["must_not"]?.add(query)
        return this
    }
}

class EsMatchAllQuery : EsQuery() {

    val match_all = emptyMap<String, Any>()
}

class EsTypeQuery(type: String) : EsQuery() {

    val type = mapOf("value" to type)
}

class EsRangeQuery(field: String, gte: Any? = null, gt: Any? = null, lte: Any? = null, lt: Any? = null, boost: Double? = null, format: String? = null, timeZone: String? = null) : EsQuery() {

    val range = mapOf(field to mapOf(
            "gte" to gte,
            "gt" to gt,
            "lte" to lte,
            "lt" to lt,
            "boost" to boost,
            "format" to format,
            "time_zone" to timeZone).filter { it.value != null })
}

class EsTermQuery(field: String, value: String, boost: Double? = null) : EsQuery() {
    val term = mapOf(field to mapOf("value" to value, "boost" to boost).filter { it.value != null })
}

class EsTermsQuery(field: String, value: List<String>, boost: Double? = null) : EsQuery() {
    val terms = mapOf(field to value, "boost" to boost).filter { it.value != null }
}

class EsMatchQuery(field: String, value: String, operator: String? = null, boost: Double? = null) : EsQuery() {
    val match = mapOf(field to mapOf("query" to value, "boost" to boost, "operator" to operator).filter { it.value != null })
}

class EsWildcardQuery(field: String, value: String) : EsQuery() {
    val wildcard = mapOf(field to mapOf("value" to value))
}

class EsRegexpQuery(field: String, value: String, boost: Double? = null) : EsQuery() {
    val regexp = mapOf(field to mapOf("value" to value, "boost" to boost).filter { it.value != null })
}

class EsPrefixQuery(field: String, value: String, boost: Double? = null) : EsQuery() {
    val prefix = mapOf(field to mapOf("value" to value, "boost" to boost).filter { it.value != null })
}

class EsQueryStringQuery(query: String, defaultField: String? = null, defaultOperator: String? = null) : EsQuery() {

    @JsonProperty("query_string")
    val queryString: ObjectNode = instance.objectNode()

    init {
        queryString.put("query", query)
        if (defaultField != null) {
            queryString.put("default_field", defaultField)
        }
        if (defaultOperator != null) {
            queryString.put("default_operator", defaultOperator)
        }
    }

}

class EsInlineTemplateQuery(template: String, params: Map<String, Any>) {

    val template = mapOf("inline" to template, "params" to params)
}

class EsStoredTemplateQuery(id: String, params: Map<String, Any>) {

    val template = mapOf("id" to id, "params" to params)
}

class EsGeoBoundingBoxQuery(field: String, bbox: Envelope) : EsQuery() {

    @JsonProperty("geo_bounding_box")
    val geoBoundingBox = mapOf(field to mapOf(
            "top_left" to mapOf("lon" to bbox.minX, "lat" to bbox.maxY),
            "bottom_right" to mapOf("lon" to bbox.maxX, "lat" to bbox.minY)))
}

class EsExistsQuery(field: String) : EsQuery() {
    val exists = mapOf("field" to field)
}


enum class EsSortDirection {
    ASC, DESC
}

enum class EsSortType {
    TERM, COUNT
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class EsAggs {

    @JsonIgnore
    private val aggs: MutableMap<String, ObjectNode> = HashMap()

    @JsonAnyGetter
    fun any(): Map<String, ObjectNode?> {
        return aggs
    }

    fun agg(name: String): ObjectNode {
        val agg = instance.objectNode()
        aggs[name] = agg

        return agg
    }

    fun agg(name: String, value: ObjectNode): ObjectNode {
        aggs[name] = value
        return value
    }

    fun agg(name: String, type: String, subAggs: EsAggs? = null): ObjectNode {
        val agg = agg(name)
        if (subAggs != null) {
            val node: ObjectNode? = subAggs.aggs.coerce()
            if (node is JsonNode) {
                agg.set<JsonNode>("aggs", node)
            }
        }
        return agg.with(type)
    }

    fun term(name: String, field: String, size: Int, sortType: EsSortType, sortDirection: EsSortDirection, missing: Any? = null, subAggs: EsAggs? = null): ObjectNode {
        val body = agg(name, "terms", subAggs)
        body.put("field", field)
        body.put("size", size)
        body.set<JsonNode>("order", toOrder(sortType, sortDirection))
        if (missing != null) {
            body.putPOJO("missing", missing)
        }
        return body
    }

    fun missing(name: String, field: String, subAggs: EsAggs? = null): ObjectNode {
        val body = agg(name, "missing", subAggs)
        body.put("field", field)
        return body
    }

    fun cardinality(name: String, field: String, subAggs: EsAggs? = null): ObjectNode {
        val body = agg(name, "cardinality", subAggs)
        body.put("field", field)
        return body
    }

    fun dateHistogram(name: String, field: String, format: String? = null, interval: String, offset: String?, timeZone: String?, subAggs: EsAggs? = null): ObjectNode {
        val body = agg(name, "date_histogram", subAggs)
        body.put("field", field)
        if (format != null) {
            body.put("format", format)
        }
        body.put("interval", interval)
        if (offset != null) {
            body.put("offset", offset)
        }
        if (timeZone != null) {
            body.put("time_zone", timeZone)
        }
        if (subAggs != null) {
            body.putPOJO("aggs", subAggs.aggs)
        }
        return body
    }

    fun histogram(name: String, field: String, interval: Double, offset: Double?, subAggs: EsAggs? = null): ObjectNode {
        val body = agg(name, "histogram", subAggs)
        body.put("field", field)
        body.put("interval", interval)
        if (offset != null) {
            body.put("offset", offset)
        }
        if (subAggs != null) {
            body.putPOJO("aggs", subAggs.aggs)
        }
        return body
    }

    fun stats(name: String, field: String): ObjectNode {
        val stats = agg(name, "stats")
        stats.put("field", field)
        return stats
    }

    fun filter(name: String, filter: EsQuery, sortType: EsSortType, sortDirection: EsSortDirection, subAggs: EsAggs? = null): ObjectNode {
        val agg = instance.objectNode()
        aggs[name] = agg
        val filterAgg = agg.with("filter")
        filterAgg.putPOJO("query", filter)
        filterAgg.set<JsonNode>("order", toOrder(sortType, sortDirection))
        if (subAggs != null) {
            filterAgg.putPOJO("aggs", subAggs.aggs)
        }
        return filterAgg
    }

    private fun toOrder(type: EsSortType, direction: EsSortDirection): JsonNode {
        val order = instance.objectNode()
        val sortType = if (type == EsSortType.TERM) "_term" else "_count"
        val sortDirection = if (direction == EsSortDirection.ASC) "asc" else "desc"
        order.put(sortType, sortDirection)
        return order
    }
}

class EsSort(val field: String, val asc: Boolean = true)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class EsScrollRequest(@JsonProperty("scroll_id") val scrollId: String,
                      @JsonProperty("scroll") val keepAlive: String,
                      @JsonProperty("size") val size: Int)


@JsonInclude(JsonInclude.Include.NON_EMPTY)
class EsSearchRequest(var from: Long = 0,
                      var size: Long = 50,
                      var query: EsQuery = EsMatchAllQuery(),
                      @JsonProperty("_source")
                      var source: List<String>? = null,
                      var aggs: EsAggs? = null,
                      var sort: List<Map<String, String>>? = mutableListOf(),
                      @JsonProperty("stored_fields")
                      var storedFields: List<String>? = null
)

class EsBucket(var key: String? = null, @JsonProperty("key_as_string") val keyAsString: String? = null, @JsonProperty("doc_count") val docCount: Int?, val from: Any? = null, val to: Any? = null) {

    @JsonIgnore
    val other: MutableMap<String, JsonNode> = HashMap()

    @JsonAnyGetter
    fun any(): Map<String, JsonNode?> = other

    @JsonAnySetter
    fun set(name: String, value: JsonNode?) {
        if (value != null) {
            other.put(name, value)
        }
    }
}

class EsAggResult(@JsonDeserialize(using = EsBucketListDeserializer::class) val buckets: List<EsBucket>? = null,
                  @JsonProperty("doc_count_error_upper_bound") val docCountErrorUpperBound: Int? = null,
                  @JsonProperty("sum_other_doc_count") val sumOtherDocCount: Int? = null) {

    @JsonProperty("doc_count")
    val docCount: Int? = null
    val count: Int? = null
    val min: Double? = null
    val max: Double? = null
    val avg: Double? = null
    val sum: Double? = null

    @JsonIgnore
    val other: MutableMap<String, Any> = HashMap()

    @JsonAnySetter
    fun set(name: String, value: Any) {
        other[name] = value
    }
}

class EsShards(val total: Int, val successful: Int, val failed: Int)

class EsHits(val total: Long, val max_score: Double, val hits: List<EsHit>)

class EsHit(
        @JsonProperty("_index") val index: String,
        @JsonProperty("_type") val type: String,
        @JsonProperty("_id") val id: String,
        @JsonProperty("_score") val score: Double,
        @JsonProperty("_source") val source: ObjectNode? = null,
        val fields: Map<String, List<JsonNode>>? = null) {

    val other = HashMap<String, Any>()

    @JsonAnySetter
    fun set(name: String, value: Any?) {
        if (value != null) {
            other[name] = value
        }

    }
}

class EsSearchResult(
        val took: Long,
        @JsonProperty("timed_out") val timedOut: Boolean,
        @JsonProperty("_shards") val shards: EsShards,
        @JsonProperty("_scroll_id") val scrollId: String?,
        val hits: EsHits) {

    private val other = HashMap<String, Any>()
    val aggregations: Map<String, EsAggResult> = emptyMap()

    @JsonAnySetter
    fun set(name: String, value: Any?) {
        if (value != null) {
            other[name] = value
        }
    }
}

class EsBulkResult(val took: Long, val errors: Boolean, val items: Array<Item>) {
    class Item(val index: ItemData? = null, val delete: ItemData? = null, val create: ItemData? = null, val update: ItemData? = null)
    class ItemData(val _index: String, val _type: String, val _id: String, val _version: Int, val result: String? = null, val status: Int)
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class EsAliasAction(var add: EsAliasDefinition? = null, var remove: EsAliasDefinition? = null)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class EsAliasDefinition(val index: String, val alias: String)

class EsAliasRequest {
    var actions: MutableList<EsAliasAction> = mutableListOf()
}

@Headers("Content-Type: application/json")
interface EsApi {

    @RequestLine(value = "POST /{index}/{type}/_search", decodeSlash = false)
    fun search(@Param("index") index: String, @Param("type") type: String, request: EsSearchRequest): EsSearchResult

    @RequestLine(value = "POST /{index}/{type}/_search?scroll={scroll}", decodeSlash = false)
    fun search(@Param("index") index: String, @Param("type") type: String, @Param("scroll") scroll: String, request: EsSearchRequest): EsSearchResult

    @RequestLine(value = "GET /_search/scroll?scroll={scroll}&scroll_id={scrollId}", decodeSlash = false)
    fun scroll(@Param("scroll") scroll: String, @Param("scrollId") scrollId: String): EsSearchResult

    @RequestLine(value = "PUT /{index}/", decodeSlash = false)
    fun createIndex(@Param("index") index: String)

    @RequestLine(value = "PUT /{index}/", decodeSlash = false)
    fun createIndex(@Param("index") index: String, settings: ObjectNode)

    @RequestLine(value = "DELETE /{index}/", decodeSlash = false)
    fun deleteIndex(@Param("index") index: String)

    @RequestLine(value = "HEAD /{index}/", decodeSlash = false)
    fun indexExists(@Param("index") index: String)

    @RequestLine(value = "GET /{index}/_mapping", decodeSlash = false)
    fun getMappings(@Param("index") index: String): ObjectNode

    @RequestLine(value = "HEAD /{index}/{type}", decodeSlash = false)
    fun mappingExists(@Param("index") index: String, @Param("type") type: String)

    @RequestLine(value = "GET /{index}/_mapping/{type}", decodeSlash = false)
    fun getMapping(@Param("index") index: String, @Param("type") type: String): ObjectNode

    @RequestLine(value = "PUT /{index}/_mapping/{type}", decodeSlash = false)
    fun putMapping(@Param("index") index: String, @Param("type") type: String, mapping: ObjectNode)

    @RequestLine(value = "POST /{index}/{type}/{id}", decodeSlash = false)
    fun put(@Param("index") index: String, @Param("type") type: String, @Param("id") id: String, document: Any)

    @RequestLine(value = "GET /{index}/{type}/{id}", decodeSlash = false)
    fun get(@Param("index") index: String, @Param("type") type: String, @Param("id") id: String): EsHit

    @RequestLine(value = "DELETE /{index}/{type}/{id}", decodeSlash = false)
    fun delete(@Param("index") index: String, @Param("type") type: String, @Param("id") id: String)

    @Headers("Content-Type: application/x-ndjson")
    @RequestLine("POST /_bulk")
    fun bulkUpdate(bulkRequest: String): EsBulkResult

    @RequestLine("POST /_flush")
    fun flush()

    @RequestLine("GET /_cluster/state/metadata")
    fun getMetadata(): ObjectNode

    @RequestLine("POST /_aliases")
    fun putAliases(request: EsAliasRequest)

    @RequestLine("GET /_aliases")
    fun getAliases(): ObjectNode

    /*
    @RequestLine("POST {path}")
    fun post(path: String, body: String): ObjectNode

    fun swapIndex(alias: String, oldIndex: String, newIndex: String): ObjectNode {
        return post("/swapIndex", """
            actions": [
              {"remove": { "index": "${oldIndex}", "alias": "${alias}" }},
              {"add": { "index": "${newIndex}", "alias": "${alias}" }}
            ]
            """)
    }
    */
}

class EsScrollIterator(private val client: EsApi, result: EsSearchResult, private val scroll: String = "1m") : Iterator<EsHit> {

    private var queue: ArrayDeque<EsHit> = ArrayDeque(result.hits.hits)
    private var scrollId = result.scrollId

    override fun next(): EsHit {
        if (!hasNext()) {
            throw NoSuchElementException()
        } else {
            return queue.pop()
        }
    }

    override fun hasNext(): Boolean {
        return if (queue.isNotEmpty()) {
            true
        } else {
            val sid = scrollId
            if (sid != null) {
                val result = client.scroll(scroll, sid)
                queue = ArrayDeque(result.hits.hits)
                // set to null if we're at the end
                scrollId = if (queue.isEmpty()) null else result.scrollId
            }
            !queue.isEmpty()
        }
    }
}

class EsBucketListDeserializer : JsonDeserializer<List<EsBucket>>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): List<EsBucket> {
        val buckets = ArrayList<EsBucket>()

        val current = parser.currentToken

        if (current == JsonToken.START_ARRAY) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                val bucket = parser.readValueAs(EsBucket::class.java)
                buckets.add(bucket)
            }
        } else if (current == JsonToken.START_OBJECT) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val fieldName = parser.currentName
                parser.nextToken()
                val bucket = parser.readValueAs(EsBucket::class.java)
                bucket.key = fieldName
                buckets.add(bucket)
            }
        }

        return buckets
    }
}
