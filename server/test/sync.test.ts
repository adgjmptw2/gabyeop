import { describe, expect, it } from "vitest";
import { selectPage, RESPONSE_LIMIT } from "../src/sync";

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

describe("sync page assembly", () => {
  it("orders changes from different tables by their global seq", () => {
    const page = selectPage(profile(5), [weightRow("w", 2)], [mealRow("m", 9)], 0, []);
    expect(page.cursor).toBe(9);
    expect(page.profile).not.toBeNull();
    expect(page.weights.map((row) => row.id)).toEqual(["w"]);
    expect(page.meals.map((row) => row.id)).toEqual(["m"]);
    expect(page.more).toBe(false);
  });

  it("does not skip earlier rows when the profile revision has a high seq", () => {
    const page = selectPage(profile(10), [weightRow("w1", 2), weightRow("w2", 3)], [mealRow("m1", 4)], 0, []);
    expect(page.cursor).toBe(10);
    expect(page.weights).toHaveLength(2);
    expect(page.meals).toHaveLength(1);
  });

  it("returns only rows after since", () => {
    const page = selectPage(profile(3), [weightRow("old", 1), weightRow("new", 4)], [], 3, []);
    expect(page.weights.map((row) => row.id)).toEqual(["new"]);
    expect(page.cursor).toBe(4);
  });

  it("caps a page at the response limit and advances to its own last row", () => {
    const weights = Array.from({ length: RESPONSE_LIMIT + 5 }, (_, index) => weightRow(`w${index}`, index + 1));
    const page = selectPage(null, weights, [], 0, []);
    expect(page.weights).toHaveLength(RESPONSE_LIMIT);
    expect(page.cursor).toBe(RESPONSE_LIMIT);
    expect(page.more).toBe(true);
  });

  it("returns the same cursor when nothing changed", () => {
    const page = selectPage(null, [], [], 42, []);
    expect(page.cursor).toBe(42);
    expect(page.more).toBe(false);
    expect(page.profile).toBeNull();
  });

  it("keeps rejected ids in the response", () => {
    const page = selectPage(null, [], [], 0, ["bad-1", "bad-2"]);
    expect(page.rejected).toEqual(["bad-1", "bad-2"]);
  });
});
