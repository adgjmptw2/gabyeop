package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodEmojiTest {
    @Test fun rice() = expect("🍚", "비빔밥")
    @Test fun riceRollBeforeRice() = expect("🍙", "참치김밥", "밥", "밥류")
    @Test fun noodlesBeforeSoup() = expect("🍜", "닭칼국수", "국", "국 및 탕류")
    @Test fun chineseNoodles() = expect("🍜", "짜장", "", "볶음류")
    @Test fun pasta() = expect("🍝", "토마토 파스타")
    @Test fun burgerBeforeChicken() = expect("🍔", "치킨버거")
    @Test fun pizza() = expect("🍕", "치즈피자")
    @Test fun sushiBeforeFish() = expect("🍣", "연어초밥")
    @Test fun sandwichBeforeBread() = expect("🥪", "계란 샌드위치")
    @Test fun hotDog() = expect("🌭", "감자핫도그")
    @Test fun stewBeforeIngredient() = expect("🍲", "김치찌개")
    @Test fun potatoSoup() = expect("🍲", "감자탕")
    @Test fun milkBeforeFruit() = expect("🥛", "딸기우유")
    @Test fun latteBeforeCoffee() = expect("🥛", "커피라떼")
    @Test fun iceCreamBeforeCoffee() = expect("🍨", "커피 아이스크림")
    @Test fun sweetPotato() = expect("🍠", "군고구마")
    @Test fun porridge() = expect("🥣", "닭죽")
    @Test fun representativeName() = expect("🍗", "오리지널", "치킨", "튀김류")
    @Test fun categoryFallback() = expect("🥬", "미정", "", "나물·숙채류")
    @Test fun soupCategory() = expect("🍲", "미정", "", "국 및 탕류")
    @Test fun fruitCategory() = expect("🍎", "미정", "", "과일류")
    @Test fun keywordBeforeCategory() = expect("🍤", "새우", "", "수·조·어·육류")
    @Test fun fruitKeywords() {
        listOf("사과" to "🍎", "바나나" to "🍌", "딸기" to "🍓", "포도" to "🍇",
            "수박" to "🍉", "귤" to "🍊", "오렌지" to "🍊").forEach { (name, emoji) ->
            expect(emoji, name)
        }
    }
    @Test fun emptyInput() = expect("🍽️", "")
    @Test fun unknownInput() = expect("🍽️", "이름없음", "미정", "기타")

    private fun expect(emoji: String, name: String, rep: String = "", category: String = "") {
        assertEquals(emoji, FoodEmoji.of(name, rep, category))
    }
}
