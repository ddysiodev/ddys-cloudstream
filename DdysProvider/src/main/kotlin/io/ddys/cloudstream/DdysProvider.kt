package io.ddys.cloudstream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLDecoder

class DdysProvider(private val settings: DdysSettings) : MainAPI() {
    override var mainUrl = DdysSettings.DEFAULT_SITE_BASE
    override var name = "DDYS"
    override var lang = "zh"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Documentary,
        TvType.Others,
    )

    override val mainPage = mainPageOf(
        "latest" to "最新更新",
        "hot" to "热门内容",
        "movie" to "电影",
        "series" to "剧集",
        "anime" to "动漫",
        "variety" to "综艺",
        "documentary" to "纪录片",
    )

    private val mapper = jacksonObjectMapper()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val result = when (request.data) {
            "latest" -> {
                if (page > 1) MoviePage(emptyList(), false)
                else fetchMovieList("/latest", mapOf("limit" to settings.homeLimit().toString()), settings.homeLimit())
            }
            "hot" -> {
                if (page > 1) MoviePage(emptyList(), false)
                else fetchMovieList("/hot", mapOf("limit" to settings.homeLimit().toString()), settings.homeLimit())
            }
            else -> fetchMoviePage(
                "/movies",
                mapOf(
                    "type" to request.data,
                    "page" to page.toString(),
                    "per_page" to settings.pageSize().toString(),
                )
            )
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    request.name.ifBlank { name },
                    result.items.map { it.toSearchResponse() },
                    request.horizontalImages,
                )
            ),
            result.hasNext,
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val result = fetchMoviePage(
            "/search",
            mapOf(
                "q" to query,
                "page" to page.toString(),
                "per_page" to settings.pageSize().toString(),
            )
        )
        return newSearchResponseList(result.items.map { it.toSearchResponse() }, result.hasNext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query, 1).items
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = extractSlug(url) ?: return null
        val movie = fetchMovie(slug)
        val type = movie.tvType()
        val resources = safeSources(slug).flatMap { it.items }.filterPlayable()
        val related = safeRelated(slug).map { it.toSearchResponse() }

        return if (type == TvType.Movie || type == TvType.AnimeMovie) {
            newMovieLoadResponse(movie.title, movie.url(settings.siteBase()), type, DdysPlayData(slug = slug)) {
                posterUrl = movie.poster
                backgroundPosterUrl = movie.fanart
                plot = movie.overview
                year = movie.yearInt()
                tags = movie.tags
                recommendations = related
            }
        } else {
            val episodes = resources.mapIndexed { index, resource ->
                newEpisode(DdysPlayData(slug = slug, resource = resource)) {
                    name = resource.displayName(index)
                    episode = resource.episodeNumber(index)
                    season = resource.groupIndex + 1
                    posterUrl = movie.poster
                    description = resource.url
                }
            }
            newTvSeriesLoadResponse(movie.title, movie.url(settings.siteBase()), type, episodes) {
                posterUrl = movie.poster
                backgroundPosterUrl = movie.fanart
                plot = movie.overview
                year = movie.yearInt()
                tags = movie.tags
                recommendations = related
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val payload = runCatching { mapper.readValue(data, DdysPlayData::class.java) }.getOrNull()
            ?: DdysPlayData(slug = data.removePrefix("ddys:").ifBlank { null })

        val resources = when {
            payload.resource != null -> listOf(payload.resource)
            !payload.slug.isNullOrBlank() -> safeSources(payload.slug).flatMap { it.items }.filterPlayable()
            else -> emptyList()
        }

        var emitted = false
        for ((index, resource) in resources.withIndex()) {
            emitted = emitResourceLink(resource, index, subtitleCallback, callback) || emitted
        }
        return emitted
    }

    private suspend fun emitResourceLink(
        resource: DdysResource,
        index: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val url = resource.url.trim()
        if (url.isBlank()) return false

        if (!resource.isDirect && !resource.isMagnet) {
            val extracted = if (settings.includeExternal()) {
                runCatching { loadExtractor(url, subtitleCallback, callback) }.getOrDefault(false)
            } else {
                false
            }
            if (extracted) return true
            if (settings.directOnly()) return false
        }

        callback(
            newExtractorLink(
                source = name,
                name = resource.displayName(index),
                url = url,
                type = resource.linkType(),
            ) {
                quality = resource.quality()
                referer = resource.headers["Referer"] ?: resource.headers["referer"] ?: settings.siteBase()
                headers = resource.headers
            }
        )
        return true
    }

    private suspend fun fetchMovieList(path: String, query: Map<String, String?>, limit: Int): MoviePage {
        val root = requestJson(path, query)
        val data = root.dataNode()
        val items = data.arrayItems().mapNotNull { it.toMovie() }.take(limit)
        return MoviePage(items, false)
    }

    private suspend fun fetchMoviePage(path: String, query: Map<String, String?>): MoviePage {
        val root = requestJson(path, query)
        val items = root.dataNode().arrayItems().mapNotNull { it.toMovie() }
        val meta = root.firstObject("meta", "pagination")
        val current = meta?.firstInt("current_page", "currentPage", "page") ?: (query["page"]?.toIntOrNull() ?: 1)
        val totalPages = meta?.firstInt("total_pages", "totalPages", "last_page", "lastPage", "pages")
        val hasNext = when {
            totalPages != null -> current < totalPages
            else -> items.size >= settings.pageSize()
        }
        return MoviePage(items, hasNext)
    }

    private suspend fun fetchMovie(slug: String): DdysMovie {
        val root = requestJson("/movies/${slug.encodeUri()}", emptyMap())
        return root.dataNode().toMovie() ?: DdysMovie(slug = slug, title = slug)
    }

    private suspend fun safeSources(slug: String): List<DdysSourceGroup> {
        return runCatching {
            parseSourceGroups(requestJson("/movies/${slug.encodeUri()}/sources", emptyMap()).dataNode())
        }.getOrDefault(emptyList())
    }

    private suspend fun safeRelated(slug: String): List<DdysMovie> {
        return runCatching {
            requestJson("/movies/${slug.encodeUri()}/related", emptyMap())
                .dataNode()
                .arrayItems()
                .mapNotNull { it.toMovie() }
                .take(12)
        }.getOrDefault(emptyList())
    }

    private suspend fun requestJson(path: String, query: Map<String, String?>): JsonNode {
        val url = buildUrl(settings.apiBase(), path, query)
        val headers = mutableMapOf("Accept" to "application/json")
        settings.apiKey().takeIf { it.isNotBlank() }?.let { headers["Authorization"] = "Bearer $it" }
        val text = app.get(url, headers = headers).text
        val root = mapper.readTree(text.ifBlank { "{}" })
        if (root.path("success").isBoolean && !root.path("success").asBoolean()) {
            throw RuntimeException(root.firstText("message", "error", "msg") ?: "DDYS API request failed")
        }
        return root
    }

    private fun DdysMovie.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(title, url(settings.siteBase()), tvType()) {
            posterUrl = poster
            year = yearInt()
        }
    }

    private fun JsonNode?.toMovie(): DdysMovie? {
        if (this == null || !isObject) return null
        val slug = firstText("slug", "id", "vod_id", "key", "code", "video_id").orEmpty()
        val title = firstText("title", "name", "vod_name", "title_cn") ?: slug
        if (title.isBlank()) return null
        return DdysMovie(
            slug = slug,
            title = title,
            originalTitle = firstText("original_title", "originalTitle", "title_en"),
            poster = absoluteUrl(firstText("poster", "cover", "pic", "vod_pic", "image", "thumbnail"), settings.siteBase()),
            fanart = absoluteUrl(firstText("fanart", "backdrop", "background", "vod_pic_slide"), settings.siteBase()),
            year = firstText("year", "release_year", "vod_year", "date", "release_date"),
            region = joinedText("region", "area", "vod_area"),
            typeName = joinedText("type_name", "typeName", "type", "category", "vod_class"),
            overview = firstText("overview", "intro", "description", "summary", "content", "vod_content"),
            remarks = joinedText("remarks", "vod_remarks", "episode", "episode_text", "score", "rate"),
            url = absoluteUrl(firstText("url", "link", "href"), settings.siteBase()),
            tags = stringList("tags", "genres", "genre", "category", "vod_class"),
        )
    }

    private fun parseSourceGroups(node: JsonNode?): List<DdysSourceGroup> {
        if (node == null || node.isNull) return emptyList()
        if (node.isArray) {
            val groups = mutableListOf<DdysSourceGroup>()
            node.forEachIndexed { index, item ->
                if (item.isObject && item.hasAnyArray(resourceArrayKeys)) {
                    val items = item.collectResourceArrays(index)
                    if (items.isNotEmpty()) {
                        groups.add(
                            DdysSourceGroup(
                                name = item.firstText("name", "title", "label", "source", "type") ?: "线路 ${index + 1}",
                                index = index,
                                items = items,
                            )
                        )
                    }
                } else {
                    item.toResource(0, "资源", index, 0)?.let {
                        groups.add(DdysSourceGroup("资源", index, listOf(it)))
                    }
                }
            }
            return groups
        }
        if (node.isObject && node.hasAnyArray(resourceArrayKeys)) {
            val items = node.collectResourceArrays(0)
            return if (items.isEmpty()) emptyList() else listOf(DdysSourceGroup("资源", 0, items))
        }
        return node.toResource(0, "资源", 0, 0)?.let { listOf(DdysSourceGroup("资源", 0, listOf(it))) } ?: emptyList()
    }

    private fun JsonNode.collectResourceArrays(groupIndex: Int): List<DdysResource> {
        val groupName = firstText("name", "title", "label", "source", "type") ?: "线路 ${groupIndex + 1}"
        val out = mutableListOf<DdysResource>()
        for (key in resourceArrayKeys) {
            val array = get(key)
            if (array != null && array.isArray) {
                array.forEachIndexed { itemIndex, item ->
                    item.toResource(out.size, groupName, groupIndex, itemIndex)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    private fun JsonNode.toResource(index: Int, groupName: String, groupIndex: Int, itemIndex: Int): DdysResource? {
        if (isTextual) {
            val url = asText().trim()
            if (url.isBlank()) return null
            return DdysResource(
                name = "资源 ${index + 1}",
                url = url,
                kind = resourceKind(url),
                isDirect = isDirectMedia(url),
                isMagnet = isMagnet(url),
                groupName = groupName,
                groupIndex = groupIndex,
                itemIndex = itemIndex,
            )
        }
        if (!isObject) return null
        val url = firstText("url", "link", "href", "play_url", "playUrl", "download_url", "downloadUrl", "magnet", "ed2k").orEmpty()
        if (url.isBlank()) return null
        val code = firstText("extract_code", "extractCode", "code", "password", "passcode")
        val label = joinedText("name", "title", "label", "episode", "episode_name", "quality", "format")
            ?: "资源 ${index + 1}"
        return DdysResource(
            name = if (!code.isNullOrBlank() && !label.contains(code)) "$label 提取码 $code" else label,
            url = url,
            kind = resourceKind(url),
            isDirect = isDirectMedia(url),
            isMagnet = isMagnet(url),
            headers = readHeaders(firstObject("headers", "header")),
            groupName = groupName,
            groupIndex = groupIndex,
            itemIndex = itemIndex,
        )
    }

    private fun List<DdysResource>.filterPlayable(): List<DdysResource> {
        return filter { resource ->
            resource.url.isNotBlank() &&
                (!settings.directOnly() || resource.isDirect) &&
                (settings.includeExternal() || resource.isDirect || resource.isMagnet)
        }.take(80)
    }

    private fun buildUrl(baseUrl: String, path: String, query: Map<String, String?>): String {
        val prefix = baseUrl.trimEnd('/')
        val route = if (path.startsWith('/')) path else "/$path"
        val params = query
            .filterValues { !it.isNullOrBlank() }
            .map { (key, value) -> "${key.encodeUri()}=${value.orEmpty().encodeUri()}" }
            .joinToString("&")
        return if (params.isBlank()) "$prefix$route" else "$prefix$route?$params"
    }

    private fun extractSlug(url: String): String? {
        val text = url.trim()
        if (text.startsWith("ddys:")) return text.removePrefix("ddys:").ifBlank { null }
        val match = Regex("""/movie/([^/?#]+)""").find(text)?.groupValues?.getOrNull(1)
            ?: text.substringAfterLast('/').substringBefore('?').substringBefore('#')
        return match.takeIf { it.isNotBlank() }?.let { URLDecoder.decode(it, "UTF-8") }
    }

    private fun absoluteUrl(value: String?, siteBase: String): String? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        return when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            text.startsWith("//") -> "https:$text"
            text.startsWith("/") -> "${siteBase.trimEnd('/')}$text"
            else -> "${siteBase.trimEnd('/')}/$text"
        }
    }

    private fun resourceKind(url: String): String {
        val text = url.trim()
        return when {
            isDirectMedia(text) -> "direct"
            isMagnet(text) -> "magnet"
            text.startsWith("ed2k:", ignoreCase = true) -> "ed2k"
            Regex("""pan\.|aliyundrive|quark|baidu|115\.com|123pan|drive\.google|mega\.nz""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "cloud"
            text.startsWith("http://", true) || text.startsWith("https://", true) -> "web"
            else -> "unknown"
        }
    }

    private fun isDirectMedia(url: String): Boolean =
        Regex("""\.(m3u8|mp4|m4v|mkv|mov|flv|avi|ts|webm|mpd)(\?|#|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    private fun isMagnet(url: String): Boolean = url.startsWith("magnet:?", ignoreCase = true)

    private fun readHeaders(node: JsonNode?): Map<String, String> {
        if (node == null || !node.isObject) return emptyMap()
        return node.fields().asSequence()
            .mapNotNull { (key, value) -> value.textValue()?.let { key to it } }
            .toMap()
    }

    private fun JsonNode.dataNode(): JsonNode = if (has("data")) get("data") else this

    private fun JsonNode?.arrayItems(): List<JsonNode> {
        if (this == null || !isArray) return emptyList()
        return map { it }
    }

    private fun JsonNode.firstText(vararg names: String): String? {
        for (name in names) {
            val value = findField(name) ?: continue
            val text = when {
                value.isTextual || value.isNumber || value.isBoolean -> value.asText()
                else -> null
            }?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun JsonNode.firstInt(vararg names: String): Int? {
        for (name in names) {
            val value = findField(name) ?: continue
            val number = when {
                value.isInt || value.isLong -> value.asInt()
                value.isTextual -> value.asText().toIntOrNull()
                else -> null
            }
            if (number != null) return number
        }
        return null
    }

    private fun JsonNode.firstObject(vararg names: String): JsonNode? {
        for (name in names) {
            val value = findField(name)
            if (value != null && value.isObject) return value
        }
        return null
    }

    private fun JsonNode.findField(name: String): JsonNode? {
        get(name)?.let { return it }
        return fields().asSequence().firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    private fun JsonNode.joinedText(vararg names: String): String? {
        for (name in names) {
            val value = findField(name) ?: continue
            val text = when {
                value.isArray -> value.mapNotNull { it.asScalarText() }.joinToString(" / ")
                value.isTextual || value.isNumber || value.isBoolean -> value.asText()
                else -> null
            }?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun JsonNode.stringList(vararg names: String): List<String> {
        val value = names.firstNotNullOfOrNull { findField(it) } ?: return emptyList()
        val raw = when {
            value.isArray -> value.mapNotNull { it.asScalarText() }
            value.isTextual -> value.asText().split('/', ',', ';', '|')
            else -> emptyList()
        }
        return raw.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(12)
    }

    private fun JsonNode.asScalarText(): String? {
        return when {
            isTextual || isNumber || isBoolean -> asText()
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun JsonNode.hasAnyArray(keys: List<String>): Boolean =
        keys.any { key -> get(key)?.isArray == true }

    private fun JsonNode.forEachIndexed(block: (Int, JsonNode) -> Unit) {
        var index = 0
        forEach {
            block(index, it)
            index += 1
        }
    }

    private data class MoviePage(
        val items: List<DdysMovie>,
        val hasNext: Boolean,
    )

    private data class DdysMovie(
        val slug: String,
        val title: String,
        val originalTitle: String? = null,
        val poster: String? = null,
        val fanart: String? = null,
        val year: String? = null,
        val region: String? = null,
        val typeName: String? = null,
        val overview: String? = null,
        val remarks: String? = null,
        val url: String? = null,
        val tags: List<String> = emptyList(),
    ) {
        fun url(siteBase: String): String = url?.takeIf { it.isNotBlank() } ?: "${siteBase.trimEnd('/')}/movie/${slug.encodeUri()}"
        fun yearInt(): Int? = year?.take(4)?.toIntOrNull()
        fun tvType(): TvType {
            val text = listOf(typeName, remarks, originalTitle, title).filterNotNull().joinToString(" ").lowercase()
            return when {
                "anime" in text || "动漫" in text || "动画" in text -> TvType.Anime
                "documentary" in text || "纪录" in text -> TvType.Documentary
                "series" in text || "tv" in text || "剧集" in text || "电视剧" in text -> TvType.TvSeries
                "variety" in text || "综艺" in text -> TvType.Others
                else -> TvType.Movie
            }
        }
    }

    private data class DdysSourceGroup(
        val name: String,
        val index: Int,
        val items: List<DdysResource>,
    )

    data class DdysPlayData(
        val slug: String? = null,
        val resource: DdysResource? = null,
    )

    data class DdysResource(
        val name: String = "",
        val url: String = "",
        val kind: String = "unknown",
        val isDirect: Boolean = false,
        val isMagnet: Boolean = false,
        val headers: Map<String, String> = emptyMap(),
        val groupName: String = "资源",
        val groupIndex: Int = 0,
        val itemIndex: Int = 0,
    ) {
        fun displayName(index: Int): String =
            listOf(groupName, name.ifBlank { "资源 ${index + 1}" })
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" - ")

        fun episodeNumber(index: Int): Int =
            Regex("""(?:第)?(\d{1,4})(?:集|话|期)?""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: (index + 1)

        fun linkType(): ExtractorLinkType = when {
            url.contains(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
            url.contains(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
            isMagnet -> ExtractorLinkType.MAGNET
            else -> ExtractorLinkType.VIDEO
        }

        fun quality(): Int = getQualityFromName("$name $url").takeIf { it != Qualities.Unknown.value }
            ?: Qualities.Unknown.value
    }

    companion object {
        private val resourceArrayKeys = listOf("items", "resources", "episodes", "playlist", "play", "urls", "list")
    }
}
