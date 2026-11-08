package io.github.adgjmptw2.diet.domain

data class Fast(
    val id: String, val startAt: Long, val endAt: Long?, val targetHours: Int,
    val updatedAt: Long, val deleted: Boolean = false,
)
data class FastStage(val fromHours: Int, val name: String, val description: String)

object Fasting {
    const val HOUR_MS = 3_600_000L
    const val MAX_DURATION_MS = 7 * 24 * HOUR_MS
    val stages = listOf(
        FastStage(0, "소화 중", "먹은 음식을 소화하고 혈당이 올라가 있어요"),
        FastStage(4, "혈당 안정", "혈당이 내려와 저장된 에너지를 쓰기 시작해요"),
        FastStage(8, "저장된 당 쓰는 중", "간에 저장된 당이 줄고 지방을 쓰는 비율이 늘어요"),
        FastStage(12, "지방을 더 쓰는 구간", "지방을 주로 쓰고 케톤이 조금씩 만들어져요"),
        FastStage(18, "케톤 증가", "케톤이 더 늘어요. 어지러우면 멈추세요"),
        FastStage(24, "긴 단식", "오래 굶는 건 의사와 상의하세요"),
    )
    fun elapsedMillis(startAt: Long, now: Long, endAt: Long? = null): Long =
        ((endAt ?: now) - startAt).coerceAtLeast(0)
    fun stageIndex(elapsedMillis: Long): Int =
        stages.indexOfLast { elapsedMillis.coerceAtLeast(0) >= it.fromHours * HOUR_MS }.coerceAtLeast(0)
    fun progress(elapsedMillis: Long, targetHours: Int): Float =
        (elapsedMillis.toDouble() / (targetHours.coerceAtLeast(1) * HOUR_MS)).toFloat().coerceIn(0f, 1f)
    fun durationText(elapsedMillis: Long): String {
        val minutes = elapsedMillis.coerceAtLeast(0) / 60_000
        return "${minutes / 60}시간 ${minutes % 60}분"
    }
}
