package io.github.adgjmptw2.diet.ui.meals

import io.github.adgjmptw2.diet.domain.Food
import io.github.adgjmptw2.diet.domain.Macro
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.MealType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MealSnapshotTest {
    private val original = MealEntry("meal", LocalDate.of(2026, 11, 8), MealType.LUNCH,
        "u-food", "예전 이름", "🍚", 123.4567, "g", 150.123456789,
        20.0, 10.0, 5.0, null)
    private val snapshot = Food("u-food", original.name, null, "", original.name,
        original.kcal / original.amount * 100, 20.0 / original.amount * 100,
        10.0 / original.amount * 100, 5.0 / original.amount * 100, original.amount, "g", false)
    private val current = snapshot.copy(name = "새 이름", kcal100 = 200.0, carb100 = 30.0)
    private val editor = MealEditorState(original.date, original.type, food = snapshot,
        currentFood = current, original = original, amountText = original.amount.amountText(), unit = "g")

    @Test fun unchangedAmountKeepsExactSnapshotEvenWhenDisplayedAmountIsRounded() {
        assertEquals(original.amount, editor.amount!!, 0.0)
        assertEquals(original.name, editor.previewFood?.name)
        assertEquals(Macro(original.kcal, original.carb, original.prot, original.fat), editor.nutrition)
    }

    @Test fun changingAmountCanUseTheCurrentFoodAndRestoringItRecoversSnapshot() {
        val edited = editor.copy(amountText = "200", amountEdited = true)
        assertEquals(current.name, edited.previewFood?.name)
        assertEquals(400.0, edited.nutrition!!.kcal, 0.0)
        val restored = edited.copy(amountText = original.amount.toString())
        assertEquals(original.name, restored.previewFood?.name)
        assertEquals(original.kcal, restored.nutrition!!.kcal, 0.0)
    }

    @Test fun deletedOrMissingFoodUsesTheSnapshotWhenChangingAmount() {
        val edited = editor.copy(currentFood = null, amountText = "200", amountEdited = true)
        assertEquals(original.name, edited.previewFood?.name)
        assertEquals(original.kcal / original.amount * 200, edited.nutrition!!.kcal, 0.000001)
    }
}
