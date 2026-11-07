import { describe, expect, it } from "vitest";
import { HttpError } from "../src/http";
import { parseSyncRequest } from "../src/validate";

const validWeight = {
  id: "w1", epochDay: 20_000, weightKg: 68.4,
  createdAt: 1_000, updatedAt: 2_000, deleted: false,
};

const validMeal = {
  id: "m1", epochDay: 20_000, mealType: "LUNCH", foodId: "D123", name: "비빔밥",
  emoji: "🍚", amount: 350, unit: "g", kcal: 560, carb: 80, prot: 20, fat: 12,
  createdAt: 1_000, updatedAt: 2_000, deleted: false,
};

describe("sync request validation", () => {
  it("parses a valid request and defaults absent arrays", () => {
    const parsed = parseSyncRequest({ since: 0, weights: [validWeight], meals: [validMeal] });
    expect(parsed.since).toBe(0);
    expect(parsed.profile).toBeNull();
    expect(parsed.weights).toHaveLength(1);
    expect(parsed.meals).toHaveLength(1);
    expect(parsed.rejected).toEqual([]);
  });

  it("skips only invalid rows and reports their ids", () => {
    const parsed = parseSyncRequest({
      since: 5,
      weights: [validWeight, { ...validWeight, id: "too-light", weightKg: 19.9 }, { ...validWeight, id: "too-heavy", weightKg: 400.1 }],
      meals: [validMeal, { ...validMeal, id: "bad-kcal", kcal: 20_001 }],
    });
    expect(parsed.weights.map((row) => row.id)).toEqual(["w1"]);
    expect(parsed.meals.map((row) => row.id)).toEqual(["m1"]);
    expect(parsed.rejected).toEqual(["too-light", "too-heavy", "bad-kcal"]);
  });

  it("uses 'invalid' when a rejected row has no usable id", () => {
    const parsed = parseSyncRequest({ since: 0, weights: [{ weightKg: 70 }], meals: [] });
    expect(parsed.rejected).toEqual(["invalid"]);
  });

  it("accepts the inclusive boundary values", () => {
    const parsed = parseSyncRequest({
      since: 0,
      weights: [{ ...validWeight, weightKg: 20 }, { ...validWeight, id: "w2", weightKg: 400 }],
      meals: [{ ...validMeal, kcal: 0 }, { ...validMeal, id: "m2", kcal: 20_000 }],
    });
    expect(parsed.rejected).toEqual([]);
    expect(parsed.weights).toHaveLength(2);
    expect(parsed.meals).toHaveLength(2);
  });

  it("rejects non-finite numbers and malformed enum values", () => {
    const parsed = parseSyncRequest({
      since: 0,
      weights: [{ ...validWeight, id: "nan", weightKg: Number.NaN }, { ...validWeight, id: "string", weightKg: "70" }],
      meals: [{ ...validMeal, id: "type", mealType: "BRUNCH" }],
    });
    expect(parsed.rejected).toEqual(["nan", "string", "type"]);
  });

  it("rejects an invalid since and an invalid profile", () => {
    expect(() => parseSyncRequest({ since: Number.NaN })).toThrow(HttpError);
    expect(() => parseSyncRequest({ since: 0.5 })).toThrow(HttpError);
    expect(() => parseSyncRequest({ since: 0, profile: { sex: "X" } })).toThrow(HttpError);
  });

  it("rejects more than 2,000 incoming rows", () => {
    const rows = Array.from({ length: 2_001 }, (_, index) => ({ ...validWeight, id: `w${index}` }));
    expect(() => parseSyncRequest({ since: 0, weights: rows })).toThrow(HttpError);
  });

  it("does not let rejected rows bypass the total row limit", () => {
    const rows = Array.from({ length: 2_001 }, (_, index) => ({ ...validWeight, id: `w${index}`, weightKg: 1 }));
    expect(() => parseSyncRequest({ since: 0, weights: rows })).toThrow(HttpError);
  });
});
