import { describe, expect, it } from "vitest";
import { selectPage, planFasts, RESPONSE_LIMIT } from "../src/sync";

type Weight = { id: string; seq: number };
type Meal = { id: string; seq: number };

function profile(seq: number) {
  return {
    data: JSON.stringify({
      sex: "FEMALE", birthYear: 1990, heightCm: 165, startWeightKg: 70,
      targetWeightKg: 60, activity: "LIGHT", startEpochDay: 20_000, updatedAt: seq,
    }),
    updated_at: seq,
    seq,
  };
}

function weightRow(id: string, seq: number) {
  return {
    id, epoch_day: 20_000, weight_kg: 70, created_at: 1, updated_at: seq,
    modified_at: seq, deleted: 0, seq,
  };
}

function mealRow(id: string, seq: number) {
  return {
    id, epoch_day: 20_000, meal_type: "LUNCH", food_id: null, name: "밥", emoji: "🍚",
    amount: 100, unit: "g", kcal: 130, carb: null, prot: null, fat: null,
    created_at: 1, updated_at: seq, deleted: 0, seq,
  };
}

function noteRow(epochDay: number, seq: number) {
  return {
    epoch_day: epochDay, memo: "한 줄", note: "자세한 메모", updated_at: seq, deleted: 0, seq,
  };
}

describe("sync page assembly", () => {
  it("returns body fat, water, and fasting without server-only seq fields", () => {
    const page = selectPage(null, [{ ...weightRow("w", 1), body_fat_pct: 28.5 }], [],
      [{ ...noteRow(20000, 2), water_ml: 1250 }], 0, [],
      [{ id: "f", start_at: 1000, end_at: 16000, target_hours: 16, updated_at: 3, deleted: 0, seq: 3 }]);
    expect(page.weights[0]?.bodyFatPct).toBe(28.5);
    expect(page.notes[0]?.waterMl).toBe(1250);
    expect(page.fasts).toEqual([{ id: "f", startAt: 1000, endAt: 16000, targetHours: 16, updatedAt: 3, deleted: false }]);
    expect(page.cursor).toBe(3);
  });

  it("includes fasts in the global page boundary without skipping notes", () => {
    const weights = Array.from({ length: 1999 }, (_, i) => weightRow("w" + i, i + 1));
    const fast = { id: "f", start_at: 1000, end_at: null, target_hours: 16, updated_at: 1, deleted: 0, seq: 2000 };
    const first = selectPage(null, weights, [], [noteRow(20000, 2001)], 0, [], [fast]);
    expect(first.fasts).toHaveLength(1);
    expect(first.notes).toEqual([]);
    expect(first.cursor).toBe(2000);
    expect(first.more).toBe(true);
    const next = selectPage(null, [], [], [noteRow(20000, 2001)], first.cursor, [], [fast]);
    expect(next.notes).toHaveLength(1);
    expect(next.fasts).toEqual([]);
    expect(next.more).toBe(false);
  });
  it("orders changes from different tables by their global seq", () => {
    const page = selectPage(profile(5), [weightRow("w", 2)], [mealRow("m", 9)], [noteRow(20_000, 7)], 0, []);
    expect(page.cursor).toBe(9);
    expect(page.profile).not.toBeNull();
    expect(page.weights.map((row) => row.id)).toEqual(["w"]);
    expect(page.meals.map((row) => row.id)).toEqual(["m"]);
    expect(page.notes.map((row) => row.epochDay)).toEqual([20_000]);
    expect(page.more).toBe(false);
  });

  it("does not skip earlier rows when the profile revision has a high seq", () => {
    const page = selectPage(profile(10), [weightRow("w1", 2), weightRow("w2", 3)], [mealRow("m1", 4)], [noteRow(20_000, 5)], 0, []);
    expect(page.cursor).toBe(10);
    expect(page.weights).toHaveLength(2);
    expect(page.meals).toHaveLength(1);
    expect(page.notes).toHaveLength(1);
  });

  it("returns only rows after since", () => {
    const page = selectPage(profile(3), [weightRow("old", 1), weightRow("new", 4)], [], [noteRow(19_999, 2), noteRow(20_000, 5)], 3, []);
    expect(page.weights.map((row) => row.id)).toEqual(["new"]);
    expect(page.notes.map((row) => row.epochDay)).toEqual([20_000]);
    expect(page.cursor).toBe(5);
  });

  it("caps a page at the response limit and advances to its own last row", () => {
    const weights = Array.from({ length: RESPONSE_LIMIT + 5 }, (_, index) => weightRow(`w${index}`, index + 1));
    const page = selectPage(null, weights, [], [], 0, []);
    expect(page.weights).toHaveLength(RESPONSE_LIMIT);
    expect(page.cursor).toBe(RESPONSE_LIMIT);
    expect(page.more).toBe(true);
  });

  it("caps a mixed page at 2,000 rows and leaves higher notes for the next page", () => {
    const weights = Array.from({ length: 1_000 }, (_, index) => weightRow(`w${index}`, index + 1));
    const meals = Array.from({ length: 1_000 }, (_, index) => mealRow(`m${index}`, index + 1_001));
    const notes = Array.from({ length: 5 }, (_, index) => noteRow(20_000 + index, index + 2_001));
    const page = selectPage(null, weights, meals, notes, 0, []);
    expect(page.weights).toHaveLength(1_000);
    expect(page.meals).toHaveLength(1_000);
    expect(page.notes).toHaveLength(0);
    expect(page.cursor).toBe(2_000);
    expect(page.more).toBe(true);
  });

  it("includes all changes on a later page after a notes cursor", () => {
    const page = selectPage(null, [weightRow("w", 2_001)], [], [noteRow(20_000, 2_002)], 2_000, []);
    expect(page.weights).toHaveLength(1);
    expect(page.notes).toHaveLength(1);
    expect(page.cursor).toBe(2_002);
    expect(page.more).toBe(false);
  });

  it("returns the same cursor when nothing changed", () => {
    const page = selectPage(null, [], [], [], 42, []);
    expect(page.cursor).toBe(42);
    expect(page.more).toBe(false);
    expect(page.profile).toBeNull();
    expect(page.notes).toEqual([]);
  });

  it("keeps rejected ids in the response", () => {
    const page = selectPage(null, [], [], [], 0, ["bad-1", "bad-2"]);
    expect(page.rejected).toEqual(["bad-1", "bad-2"]);
  });
});

describe("fast LWW", () => {
  const active = { id: "a", startAt: 1000, endAt: null, targetHours: 16, updatedAt: 10, deleted: false, seq: 1 };
  it("keeps equal revisions and accepts a newer end", () => {
    expect(planFasts([active], [{ ...active, endAt: 2000 }])).toEqual([]);
    expect(planFasts([active], [{ ...active, endAt: 2000, updatedAt: 11 }])[0]?.endAt).toBe(2000);
    expect(planFasts([active], [{ ...active, updatedAt: 11, deleted: true }])[0]?.deleted).toBe(true);
  });
  it("resolves simultaneous starts deterministically and returns a tombstone", () => {
    const plan = planFasts([active], [{ ...active, id: "b" }]);
    expect(plan.find((row) => row.id === "b")?.deleted).toBe(false);
    expect(plan.find((row) => row.id === "a")?.deleted).toBe(true);
    expect(plan.find((row) => row.id === "a")?.updatedAt).toBeGreaterThan(active.updatedAt);
  });
});
