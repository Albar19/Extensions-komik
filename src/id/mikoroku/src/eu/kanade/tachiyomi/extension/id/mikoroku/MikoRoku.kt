package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

/*
 * Sumber asli: Keiyoushi/tachiyomi-extensions (https://github.com/keiyoushi/tachiyomi-extensions)
 * Repo modifikasi: Albar19/Extensions-komik (https://github.com/Albar19/Extensions-komik)
 *
 * Modifikasi dilakukan untuk memperbaiki source scanlation bahasa Indonesia.
 * Domain backend dialihkan ke www.mikoroku.top (karena www.mikoroku.com
 * sudah jadi SPA statis). Chapter diambil dari www.mikodrive.my.id.
 * baseUrl tetap https://mikoroku.com untuk kompatibilitas.
 *
 * Juli 2026
 */
@Source
abstract class MikoRoku : ZeistManga() {

    private val apiHost = "https://www.mikoroku.top"

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override val hasFilters = true
    override val hasLanguageFilter = false
    override val hasTypeFilter = false

    override val mangaCategory: String = "Manga"

    override fun getStatusList() = listOf(
        Status("Semua", ""),
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
        Status("Hiatus", "Hiatus"),
        Status("Dropped", "Dropped"),
    )

    override fun getGenreList() = listOf(
        Genre("Action", "Action"),
        Genre("Adventure", "Adventure"),
        Genre("Comedy", "Comedy"),
        Genre("Dark Fantasy", "Dark Fantasy"),
        Genre("Drama", "Drama"),
        Genre("Fantasy", "Fantasy"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Isekai", "Isekai"),
        Genre("Magic", "Magic"),
        Genre("Mecha", "Mecha"),
        Genre("Military", "Military"),
        Genre("Mystery", "Mystery"),
        Genre("Psychological", "Psychological"),
        Genre("Romance", "Romance"),
        Genre("School Life", "School Life"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Seinen", "Seinen"),
        Genre("Shounen", "Shounen"),
        Genre("Slice of Life", "Slice of Life"),
        Genre("Supernatural", "Supernatural"),
        Genre("Survival", "Survival"),
        Genre("Tragedy", "Tragedy"),
    )

    override fun apiUrl(feed: String): HttpUrl.Builder = "$apiHost/feeds/posts/default/-/".toHttpUrl().newBuilder()
        .addPathSegment(feed)
        .addQueryParameter("alt", "json")

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonString = response.body.string()
        val result = json.decodeFromString<ZeistMangaDto>(jsonString)

        val mangas = result.feed?.entry.orEmpty()
            .filter { it.category.orEmpty().any { category -> category.term == mangaCategory } }
            .filterNot { it.category.orEmpty().any { category -> excludedCategories.contains(category.term) } }
            .map { entry ->
                SManga.create().apply {
                    title = entry.title?.t ?: ""
                    thumbnail_url = entry.thumbnail?.url ?: ""
                    val href = entry.url?.firstOrNull { it.rel == "alternate" }?.href ?: ""
                    url = href.replace(Regex("^https?://[^/]+"), "").ifEmpty { "/" }
                    if (!url.startsWith("/")) url = "/$url"
                }
            }

        val mangalist = mangas.toMutableList()
        if (mangas.size == MAX_RESULTS + 1) {
            mangalist.removeLast()
            return MangasPage(mangalist, true)
        }
        return MangasPage(mangalist, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiHost${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            thumbnail_url = document.selectFirst("div.grid.gtc-235fr figure img")?.attr("abs:src")
            title = document.selectFirst("article header h1")?.ownText()?.trim() ?: ""
            description = document.selectFirst("#synopsis")?.ownText()?.trim()

            val infoItems = document.select("#extra-info .info-item")
            for (item in infoItems) {
                val label = item.ownText().trim().removeSuffix(":")
                val value = item.selectFirst(".info-value")?.text()?.trim() ?: ""
                when {
                    label.contains("Author", ignoreCase = true) -> author = value
                    label.contains("Artist", ignoreCase = true) -> artist = value
                    label.contains("Genre", ignoreCase = true) -> genre = value
                }
            }

            val statusText = document.selectFirst("aside.r2 .y6x11p .dt")?.text()
            if (statusText != null) {
                status = parseStatus(statusText)
            }
        }
    }

    override val chapterCategory: String = "Chapter"

    override fun chapterListRequest(manga: SManga): Request = GET("$apiHost${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.selectFirst("article header h1")?.ownText()?.trim()
            ?: return emptyList()

        val normalizedTitle = mangaTitle.lowercase().replace(Regex("\\s+"), "")

        val allChapters = mutableListOf<SChapter>()
        var startIndex = 1
        val maxResults = 500

        while (true) {
            val chapterUrl = "$CHAPTER_HOST/feeds/posts/default?alt=json&max-results=$maxResults&start-index=$startIndex"
            val chapterResponse = client.newCall(GET(chapterUrl, headers)).execute()
            val jsonString = chapterResponse.body.string()
            val feed = json.decodeFromString<ZeistMangaDto>(jsonString)
            val entries = feed.feed?.entry.orEmpty()

            if (entries.isEmpty()) break

            val matched = entries
                .filter { entry ->
                    val entryTitle = entry.title?.t ?: return@filter false
                    val normalizedEntry = entryTitle.lowercase().replace(Regex("\\s+"), "")
                    normalizedEntry.contains(normalizedTitle)
                }
                .map { entry ->
                    val chNumber = entry.title?.t?.let { title ->
                        CHAPTER_REGEX.find(title)?.groupValues?.get(1)
                    }
                    SChapter.create().apply {
                        name = chNumber?.let { "Chapter $it" } ?: (entry.title?.t ?: "")
                        url = entry.url?.firstOrNull { it.rel == "alternate" }?.href ?: ""
                        date_upload = parseDate(entry.published?.t?.trim().orEmpty())
                    }
                }
            allChapters.addAll(matched)

            if (entries.size < maxResults) break
            startIndex += maxResults
        }

        return allChapters.sortedByDescending { chapter ->
            val num = CHAPTER_REGEX.find(chapter.name)?.groupValues?.get(1)
            num?.toFloatOrNull() ?: 0f
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = when {
            document.selectFirst("div.check-box") != null ->
                document.select("div.check-box div.separator img[src]")
            document.selectFirst("div[data=imageProtection]") != null ->
                document.select("div[data=imageProtection] div.separator img[src]")
            document.selectFirst("#post-body div.separator") != null ->
                document.select("#post-body div.separator img[src]")
            else ->
                document.select(".post-body div.separator img[src]")
        }

        return images.mapIndexed { index, img ->
            Page(index, imageUrl = cleanImageUrl(img.attr("abs:src")))
        }
    }

    private fun cleanImageUrl(url: String): String {
        return url.replace(Regex("""/s\d+(-[a-z0-9]+)?"""), "/s0")
    }

    companion object {
        private const val MAX_RESULTS = 20
        private val CHAPTER_REGEX = Regex("""(?:Chapter|Ch\.?)\s*([\d.]+)""", RegexOption.IGNORE_CASE)
        private const val CHAPTER_HOST = "https://www.mikodrive.my.id"
    }
}
