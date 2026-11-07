package io.github.adgjmptw2.diet.domain

import java.util.Locale

object FoodEmoji {
    private class Rule(val emoji: String, vararg val keywords: String)

    // 복합 음식과 조리 이름을 재료보다 먼저 찾는다.
    private val foods = listOf(
        Rule("🥪", "샌드위치", "샌드", "sandwich"),
        Rule("🌭", "핫도그", "hotdog", "hot dog"),
        Rule("🍔", "햄버거", "버거", "burger"),
        Rule("🍕", "피자", "pizza"),
        Rule("🍣", "초밥", "스시", "sushi"),
        Rule("🍙", "김밥", "주먹밥", "삼각김밥"),
        Rule("🍝", "파스타", "스파게티", "라자냐", "pasta"),
        Rule("🍜", "라면", "라멘", "국수", "짜장", "자장", "짬뽕", "우동", "쌀국수", "면"),
        Rule("🥟", "만두", "딤섬"),
        Rule("🍡", "떡볶이", "떡꼬치"),
        Rule("🥗", "샐러드", "샐러드볼", "salad"),
        Rule("🍨", "아이스크림", "빙수", "젤라토", "소프트콘"),
        Rule("🍰", "케이크", "케익", "티라미수"),
        Rule("🍪", "쿠키", "과자", "크래커", "비스킷"),
        Rule("🍞", "빵", "토스트", "베이글", "크루아상", "도넛"),
        Rule("🥤", "주스", "쥬스", "스무디", "에이드", "콜라", "사이다"),
        Rule("🍺", "맥주", "비어"),
        Rule("🍷", "소주", "와인", "막걸리"),
        Rule("🥛", "우유", "라떼", "요거트", "요구르트", "두유"),
        Rule("☕", "커피", "아메리카노", "에스프레소", "카푸치노", "coffee"),
        Rule("🍵", "녹차", "홍차", "보리차", "우롱차", "허브차", "말차", "차"),
        Rule("🍚", "비빔밥", "볶음밥", "덮밥", "밥", "리소토", "리조또"),
        Rule("🍲", "찌개", "전골", "두부", "국", "탕", "스튜"),
        Rule("🥣", "죽", "스프", "수프", "오트밀"),
        Rule("🍗", "치킨", "닭", "오리고기", "훈제오리", "오리구이", "삼계"),
        Rule("🥩", "삼겹살", "돼지", "소고기", "쇠고기", "불고기", "스테이크", "갈비", "차돌", "한우", "목살", "돈까스", "돈가스", "제육"),
        Rule("🐟", "생선", "고등어", "연어", "참치", "갈치", "꽁치", "명태", "조기"),
        Rule("🍤", "새우"),
        Rule("🍳", "계란", "달걀", "오믈렛"),
        Rule("🥬", "김치"),
        Rule("🍡", "떡"),
        Rule("🍠", "고구마"),
        Rule("🍟", "감자튀김", "감자", "프렌치프라이"),
        Rule("🍎", "사과"),
        Rule("🍌", "바나나"),
        Rule("🍓", "딸기"),
        Rule("🍇", "포도"),
        Rule("🍉", "수박"),
        Rule("🍊", "귤", "오렌지", "한라봉", "천혜향"),
    )

    private val categories = listOf(
        Rule("🍚", "밥", "곡류"),
        Rule("🍜", "면"),
        Rule("🥟", "만두"),
        Rule("🍲", "국", "탕", "찌개", "전골", "두류", "찜"),
        Rule("🥣", "죽", "스프", "수프"),
        Rule("🍞", "빵", "과자", "제과"),
        Rule("🥛", "유제품", "우유", "빙과"),
        Rule("🍵", "음료", "차류"),
        Rule("🥬", "채소", "나물", "생채", "무침", "김치", "절임", "장아찌"),
        Rule("🍎", "과일"),
        Rule("🐟", "수산", "어류", "젓갈"),
        Rule("🥩", "육류", "구이", "볶음", "조림"),
        Rule("🍟", "튀김", "서류"),
        Rule("🍳", "부침", "전·적", "알류"),
    )

    fun of(name: String, rep: String, category: String): String =
        find(name, foods) ?: find(rep, foods) ?: find(category, categories) ?: "🍽️"

    private fun find(text: String, rules: List<Rule>): String? {
        val normalized = text.lowercase(Locale.ROOT).replace(" ", "")
        return rules.firstOrNull { rule ->
            rule.keywords.any {
                if (it == "차") normalized.endsWith("차") else normalized.contains(it.replace(" ", ""))
            }
        }?.emoji
    }
}
