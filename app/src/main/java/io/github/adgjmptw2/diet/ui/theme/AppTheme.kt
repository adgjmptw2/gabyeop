package io.github.adgjmptw2.diet.ui.theme

enum class AppTheme(val label: String) {
    LIGHT("라이트"),
    DARK("다크"),
    BUNNY("토끼"),
    OCEAN("오션");

    val isDark: Boolean get() = this == DARK
}
