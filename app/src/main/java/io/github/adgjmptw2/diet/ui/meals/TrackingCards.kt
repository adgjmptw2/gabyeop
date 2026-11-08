package io.github.adgjmptw2.diet.ui.meals

import java.util.Locale

fun liters(ml: Int): String = String.format(Locale.KOREA, "%.2f", ml / 1000.0).trimEnd('0').let {
    if (it.endsWith('.')) it + "0" else it
}
