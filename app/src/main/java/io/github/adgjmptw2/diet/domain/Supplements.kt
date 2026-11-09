package io.github.adgjmptw2.diet.domain

import java.time.LocalDate
import java.util.Locale

const val SUPPLEMENT_NAME_MAX = 20
const val SUPPLEMENT_NAME_MIN = 1
const val SUPPLEMENT_MAX_COUNT = 20

const val SUPPLEMENT_SLOT_MORNING = 1
const val SUPPLEMENT_SLOT_LUNCH = 2
const val SUPPLEMENT_SLOT_EVENING = 4
const val SUPPLEMENT_SLOTS_ALL = 7 // 1 | 2 | 4

val SUPPLEMENT_SLOT_ORDER = listOf(SUPPLEMENT_SLOT_MORNING, SUPPLEMENT_SLOT_LUNCH, SUPPLEMENT_SLOT_EVENING)

data class Supplement(
    val id: String,
    val name: String,
    val slots: Int,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class SupplementCheck(
    val supplementId: String,
    val slot: Int,
)

data class SupplementLog(
    val epochDay: Long,
    val supplementId: String,
    val slot: Int,
    val takenAt: Long,
)

data class SupplementProgress(val done: Int, val total: Int)

data class SupplementToggleAllPlan(
    val slotsToAdd: Set<Int>,
    val removeAll: Boolean,
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

fun supplementSlotFlags(slots: Int): Set<Int> =
    SUPPLEMENT_SLOT_ORDER.filterTo(mutableSetOf()) { (slots and it) != 0 }

fun supplementSlotCount(slots: Int): Int = supplementSlotFlags(slots).size

fun supplementSlotLabel(slot: Int): String? = when (slot) {
    SUPPLEMENT_SLOT_MORNING -> "아침"
    SUPPLEMENT_SLOT_LUNCH -> "점심"
    SUPPLEMENT_SLOT_EVENING -> "저녁"
    else -> null
}

/** 0은 허용하지 않는다. 여러 때를 켤 수 있고, 켜진 때가 하나도 없으면 잘못된 값이다. */
fun normalizeSupplementSlots(slots: Int): Int {
    val masked = slots and SUPPLEMENT_SLOTS_ALL
    require(masked != 0) { "영양제를 먹는 때를 하나 이상 골라 주세요" }
    return masked
}

/** 마지막 하나는 끄지 않는다. 켜진 때가 없으면 누른 때만 켠다. */
fun toggleSupplementSlot(slots: Int, slot: Int): Int {
    require(slot in SUPPLEMENT_SLOT_ORDER) { "영양제 때를 확인해 주세요" }
    val current = (slots and SUPPLEMENT_SLOTS_ALL)
    val next = current xor slot
    return if (next == 0) current else next
}

/**
 * 나누기 켬: 때별 체크 수 / 전체 때별 수.
 * 나누기 끔: 모든 때를 체크한 영양제 수 / 영양제 수.
 */
fun supplementProgress(
    supplements: Collection<Supplement>,
    checks: Set<SupplementCheck>,
    splitByTime: Boolean,
): SupplementProgress {
    if (splitByTime) {
        var done = 0
        var total = 0
        supplements.forEach { supplement ->
            supplementSlotFlags(supplement.slots).forEach { slot ->
                total += 1
                if (SupplementCheck(supplement.id, slot) in checks) done += 1
            }
        }
        return SupplementProgress(done, total)
    }
    val done = supplements.count { supplement ->
        val flags = supplementSlotFlags(supplement.slots)
        flags.isNotEmpty() && flags.all { SupplementCheck(supplement.id, it) in checks }
    }
    return SupplementProgress(done, supplements.size)
}

/** 통합 모드 한 번 탭: 모두 체크돼 있으면 그날 로그를 모두 지우고, 아니면 빠진 때를 채운다. */
fun supplementToggleAllPlan(
    supplement: Supplement,
    checks: Set<SupplementCheck>,
): SupplementToggleAllPlan {
    val flags = supplementSlotFlags(supplement.slots)
    if (flags.isEmpty()) return SupplementToggleAllPlan(emptySet(), false)
    val checked = flags.filterTo(mutableSetOf()) { SupplementCheck(supplement.id, it) in checks }
    if (checked.size == flags.size) return SupplementToggleAllPlan(emptySet(), true)
    return SupplementToggleAllPlan(flags - checked, false)
}

/** 기록이 없는 날은 맵에 넣지 않는다. 같은 날 같은 때가 겹쳐도 하나로 센다. */
fun supplementTakenByDay(logs: List<SupplementLog>): Map<LocalDate, Set<SupplementCheck>> =
    logs.groupBy { LocalDate.ofEpochDay(it.epochDay) }
        .mapValues { (_, dayLogs) ->
            dayLogs.mapTo(mutableSetOf()) { SupplementCheck(it.supplementId, it.slot) }
        }
