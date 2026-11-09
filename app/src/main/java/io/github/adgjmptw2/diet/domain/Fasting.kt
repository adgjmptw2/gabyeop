package io.github.adgjmptw2.diet.domain

data class Fast(
    val id: String, val startAt: Long, val endAt: Long?, val targetHours: Int,
    val updatedAt: Long, val deleted: Boolean = false,
)
data class FastStage(val fromHours: Int, val name: String, val description: String)

enum class PastFastError(val message: String) {
    ORDER("끝 시각이 시작보다 늦어야 해요"),
    FUTURE("끝 시각은 지금보다 늦을 수 없어요"),
    DURATION("단식은 7일까지 기록할 수 있어요"),
    OVERLAP("다른 단식과 시간이 겹쳐요"),
    TARGET("목표는 1~72시간으로 골라 주세요"),
    CHANGED("단식 기록이 바뀌었어요"),
}

object Fasting {
    val TARGET_HOURS = 1..72
    const val HOUR_MS = 3_600_000L
    const val MAX_DURATION_MS = 7 * 24 * HOUR_MS
    val stages = listOf(
        FastStage(0, "소화 중", "먹은 음식을 소화하고 혈당이 올라가 있어요"),
        FastStage(4, "혈당 안정", "혈당이 내려와 저장된 에너지를 쓰기 시작해요"),
        FastStage(8, "저장된 당 쓰는 중", "간에 저장된 당이 줄고 지방을 쓰는 비율이 늘어요"),
        FastStage(12, "지방을 더 쓰는 구간", "지방을 주로 쓰고 케톤이 조금씩 만들어져요"),
        FastStage(18, "케톤 증가", "케톤이 더 늘어요. 어지러우면 단식을 멈추고 드세요"),
        FastStage(24, "긴 단식", "24시간 넘는 단식은 의사와 상의하세요"),
    )
    fun elapsedMillis(startAt: Long, now: Long, endAt: Long? = null): Long =
        ((endAt ?: now) - startAt).coerceAtLeast(0)
    /** 시작 시각은 지금 이전, 7일 이내만 */
    fun validStart(startAt: Long, now: Long): Boolean = startAt <= now && now - startAt <= MAX_DURATION_MS
    /** 구간은 [시작, 끝). 끝과 다음 시작이 같은 기록은 겹치지 않는다. */
    fun pastError(id: String, startAt: Long, endAt: Long, targetHours: Int,
        others: List<Fast>, now: Long): PastFastError? = when {
        endAt <= startAt -> PastFastError.ORDER
        endAt > now -> PastFastError.FUTURE
        // 뺄셈 오버플로도 7일을 넘는 구간으로 처리한다.
        endAt - startAt < 0 || endAt - startAt > MAX_DURATION_MS -> PastFastError.DURATION
        others.any { other ->
            val otherEnd = other.endAt
            !other.deleted && other.id != id && (otherEnd == null || otherEnd > other.startAt) &&
                (otherEnd == null || startAt < otherEnd) && endAt > other.startAt
        } -> PastFastError.OVERLAP
        targetHours !in TARGET_HOURS -> PastFastError.TARGET
        else -> null
    }

    fun deletePast(fast: Fast, now: Long): Fast? =
        fast.takeIf { it.endAt != null && !it.deleted }
            ?.copy(deleted = true, updatedAt = maxOf(now, fast.updatedAt + 1))

    /** 삭제 직후의 스냅샷과 같은 행에만 되돌리기를 적용한다. */
    fun restorePast(current: Fast, deleted: Fast, now: Long): Fast? =
        current.takeIf { it == deleted && it.deleted && it.endAt != null }
            ?.copy(deleted = false, updatedAt = maxOf(now, current.updatedAt + 1))

    fun stageIndex(elapsedMillis: Long): Int =
        stages.indexOfLast { elapsedMillis.coerceAtLeast(0) >= it.fromHours * HOUR_MS }.coerceAtLeast(0)
    fun progress(elapsedMillis: Long, targetHours: Int): Float =
        (elapsedMillis.coerceAtLeast(0).toDouble() / (targetHours.coerceIn(TARGET_HOURS) * HOUR_MS)).toFloat().coerceIn(0f, 1f)
    fun durationText(elapsedMillis: Long): String {
        val minutes = elapsedMillis.coerceAtLeast(0) / 60_000
        return "${minutes / 60}시간 ${minutes % 60}분"
    }
}
