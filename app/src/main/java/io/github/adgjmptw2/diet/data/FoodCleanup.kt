package io.github.adgjmptw2.diet.data

/**
 * 1.1.0 기본 음식 정리에서 남길 id를 고른다.
 * 즐겨찾기나 식단이 참조하는 줄은 정리 대상에서 절대 빼지 않는다.
 */
object FoodCleanup {
    fun unreferencedBuiltIns(
        builtIn: List<String>,
        favorites: Set<String>,
        meals: Set<String>,
    ): List<String> = builtIn.filterNot { it in favorites || it in meals }
}
