package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightRulesTest {

    @Test
    fun `쉼표 소수를 받아 첫째 자리로 반올림한다`() {
        assertEquals(72.5, WeightRules.parse("72,45")!!, 0.0)
    }

    @Test
    fun `앞뒤 공백을 허용한다`() {
        assertEquals(72.5, WeightRules.parse("  72.45  ")!!, 0.0)
    }

    @Test
    fun `72_44는 72_4가 된다`() {
        assertEquals(72.4, WeightRules.parse("72.44")!!, 0.0)
    }

    @Test
    fun `범위 경계 30과 300은 허용한다`() {
        assertEquals(30.0, WeightRules.parse("30")!!, 0.0)
        assertEquals(300.0, WeightRules.parse("300")!!, 0.0)
    }

    @Test
    fun `반올림하면 경계 안이 되는 29_95도 거부한다`() {
        assertNull(WeightRules.parse("29.95"))
    }

    @Test
    fun `반올림하면 경계 안이 되는 300_04도 거부한다`() {
        assertNull(WeightRules.parse("300.04"))
    }

    @Test
    fun `범위 밖과 빈 문자열과 유한하지 않은 숫자는 거부한다`() {
        assertNull(WeightRules.parse("29.9"))
        assertNull(WeightRules.parse("300.1"))
        assertNull(WeightRules.parse(""))
        assertNull(WeightRules.parse("NaN"))
        assertNull(WeightRules.parse("Infinity"))
    }
}