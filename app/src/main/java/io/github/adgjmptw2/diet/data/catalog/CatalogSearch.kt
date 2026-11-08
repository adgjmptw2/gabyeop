package io.github.adgjmptw2.diet.data.catalog

import java.util.Locale

/** catalog 검색의 토큰·정렬 규칙. Android 없이 JVM에서 검증한다. */
object CatalogSearch {
    fun normalizedQuery(query: String): String =
        query.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    /** 공백으로 나눈 토큰. 빈 토큰은 버린다. */
    fun tokens(query: String): List<String> = normalizedQuery(query)
        .split(' ')
        .filter { it.isNotEmpty() }

    /** skey는 이름+업체를 소문자·공백 제거한 값이다. 모든 토큰이 들어 있어야 한다. */
    fun matchesAll(skey: String, tokens: List<String>): Boolean =
        tokens.isNotEmpty() && tokens.all { skey.contains(it) }

    fun searchKey(name: String, brand: String): String =
        (name + " " + brand).lowercase(Locale.ROOT).replace(WHITESPACE, "")

    /**
     * 이름 정확히 일치 → 이름 앞부분 일치 → 짧은 이름 → 음식·원재료 먼저.
     * 이름 비교는 소문자·공백 하나로 정리한 값끼리 한다.
     */
    fun comparator(query: String): Comparator<CatalogFood> {
        val normalized = normalizedQuery(query)
        return compareBy<CatalogFood>(
            { when {
                normalizedName(it.name) == normalized -> 0
                normalizedName(it.name).startsWith(normalized) -> 1
                else -> 2
            } },
            { it.name.length },
            { if (it.kind <= 1) 0 else 1 },
            { normalizedName(it.name) },
            { it.id },
        )
    }

    fun ranking(query: String, foods: List<CatalogFood>): List<CatalogFood> =
        foods.sortedWith(comparator(query))

    fun normalizedName(name: String): String =
        name.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
