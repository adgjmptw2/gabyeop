package io.github.adgjmptw2.diet.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `소수 첫째 자리로 반올림한다`() {
        assertEquals("72.5", 72.45.kg())
    }

    @Test
    fun `부동소수점 오차를 표시하지 않는다`() {
        assertEquals("7.6", 7.599999999999994.kg())
    }

    @Test
    fun `음수 0은 0_0으로 표시한다`() {
        assertEquals("0.0", (-0.0).kg())
    }

    @Test
    fun `absKg는 음수 부호를 없앤다`() {
        assertEquals("7.6", (-7.599999999999994).absKg())
    }

    @Test
    fun `정수도 소수 한 자리로 표시한다`() {
        assertEquals("70.0", 70.0.kg())
    }
}