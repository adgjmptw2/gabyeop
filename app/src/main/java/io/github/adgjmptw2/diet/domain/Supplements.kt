package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import java.util.Locale

const val SUPPLEMENT_NAME_MAX = 20
const val SUPPLEMENT_NAME_MIN = 1
const val SUPPLEMENT_MAX_COUNT = 20

data class Supplement(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class SupplementLog(
    val epochDay: Long,
    val supplementId: String,
    val takenAt: Long,
)

/** 앞뒤 공백을 빼고 1~20자만 받는다. 빈 이름과 20자를 넘는 이름은 null. */
fun parseSupplementName(raw: String): String? {
    val name = raw.trim()
    if (name.isEmpty()) return null
    if (name.codePointCount(0, name.length) !in SUPPLEMENT_NAME_MIN..SUPPLEMENT_NAME_MAX) return null
    return name
}

/** 대소문자·공백 차이만 있는 이름은 같은 이름으로 본다. */
fun supplementNameKey(name: String): String =
    name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

fun hasDuplicateSupplementName(name: String, others: Collection<String>): Boolean {
    val key = supplementNameKey(name)
    return others.any { supplementNameKey(it) == key }
}

data class SupplementMove(val id: String, val delta: Int)

/**
 * 한 칸 위/아래로 옮길 대상. 맨 위에서 올리거나 맨 아래에서 내리면 null.
 * 목록은 sortOrder 오름차순으로 이미 정렬돼 있어야 한다.
 */
fun supplementMove(ids: List<String>, id: String, delta: Int): SupplementMove? {
    if (delta != -1 && delta != 1) return null
    val index = ids.indexOf(id)
    if (index < 0) return null
    val target = index + delta
    if (target !in ids.indices) return null
    return SupplementMove(ids[target], -delta)
}

/** 기록이 없는 날은 맵에 넣지 않는다. 같은 날 같은 영양제가 겹쳐도 하나로 센다. */
fun supplementTakenByDay(logs: List<SupplementLog>): Map<LocalDate, Set<String>> =
    logs.groupBy { LocalDate.ofEpochDay(it.epochDay) }
        .mapValues { (_, dayLogs) -> dayLogs.mapTo(mutableSetOf()) { it.supplementId } }
