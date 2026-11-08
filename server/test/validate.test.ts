import { describe, expect, it } from "vitest";
import { HttpError } from "../src/http";
import { parseProfile, parseSyncRequest } from "../src/validate";

const validWeight = {
  id: "w1", epochDay: 20_000, weightKg: 68.4,
  createdAt: 1_000, updatedAt: 2_000, deleted: false,
};

const validMeal = {
  id: "m1", epochDay: 20_000, mealType: "LUNCH", foodId: "D123", name: "비빔밥",
  emoji: "🍚", amount: 350, unit: "g", kcal: 560, carb: 80, prot: 20, fat: 12,
  createdAt: 1_000, updatedAt: 2_000, deleted: false,
};

const validNote = {
  waterMl: 0,
  epochDay: 20_000, memo: "가볍게 시작", note: "아침은 가볍게 먹었다.",
  updatedAt: 2_000, deleted: false,
};

describe("sync request validation", () => {
  it("keeps new fields absent for older clients so merges can preserve stored values", () => {
    const parsed = parseSyncRequest({ since: 0, weights: [validWeight],
      notes: [{ epochDay: 20000, memo: "", note: "", updatedAt: 1, deleted: false }] });
    expect(parsed.weights[0]).not.toHaveProperty("bodyFatPct");
    expect(parsed.notes[0]).not.toHaveProperty("waterMl");
    expect(parsed.notes[0]?.deleted).toBe(false);
    expect(parsed.fasts).toEqual([]);
  });

  it("validates manual body fat inclusive bounds and null", () => {
    const parsed = parseSyncRequest({ since: 0, weights: [
      { ...validWeight, id: "min", bodyFatPct: 3 }, { ...validWeight, id: "max", bodyFatPct: 70 },
      { ...validWeight, id: "null", bodyFatPct: null }, { ...validWeight, id: "low", bodyFatPct: 2.9 },
      { ...validWeight, id: "high", bodyFatPct: 70.1 }, { ...validWeight, id: "nan", bodyFatPct: Number.NaN },
    ] });
    expect(parsed.weights.map((row) => row.id)).toEqual(["min", "max", "null"]);
    expect(parsed.rejected).toEqual(["low", "high", "nan"]);
  });

  it("validates calorie target without requiring it from older profiles", () => {
    const profile = { sex: "FEMALE", birthYear: 1990, heightCm: 165, startWeightKg: 70,
      targetWeightKg: 60, activity: "LIGHT", startEpochDay: 20000, updatedAt: 1 };
    expect(parseProfile(profile)).not.toHaveProperty("calorieTarget");
    for (const calorieTarget of [null, 800, 5000]) expect(parseProfile({ ...profile, calorieTarget }).calorieTarget).toBe(calorieTarget);
    for (const calorieTarget of [799, 5001, 1200.5, "1500", Number.NaN])
      expect(() => parseProfile({ ...profile, calorieTarget })).toThrow(HttpError);
  });

  it("validates water as an integer and keeps water-only days", () => {
    const parsed = parseSyncRequest({ since: 0, notes: [0, 10000, -1, 10001, 250.5, null].map((waterMl, i) =>
      ({ ...validNote, epochDay: 20000 + i, memo: "", note: "", waterMl })) });
    expect(parsed.notes.map((row) => row.waterMl)).toEqual([0, 10000, 0]);
    expect(parsed.notes.map((row) => row.deleted)).toEqual([true, false, true]);
    expect(parsed.rejected).toEqual(["n:20002", "n:20003", "n:20004"]);
  });

  it("validates fasting duration, target hours, and nullable end", () => {
    const fast = { id: "f", startAt: 1000, endAt: null, targetHours: 16, updatedAt: 2000, deleted: false };
    const week = 7 * 24 * 3600000;
    const parsed = parseSyncRequest({ since: 0, fasts: [fast,
      { ...fast, id: "week", endAt: 1000 + week, targetHours: 72 },
      { ...fast, id: "short", endAt: 1000, targetHours: 1 },
      { ...fast, id: "backwards", endAt: 999 },
      { ...fast, id: "long", endAt: 1001 + week },
      { ...fast, id: "target", targetHours: 73 },
      { ...fast, id: "fraction", targetHours: 16.5 },
    ] });
    expect(parsed.fasts.map((row) => row.id)).toEqual(["f", "week", "short"]);
    expect(parsed.rejected).toEqual(["f:backwards", "f:long", "f:target", "f:fraction"]);
    expect(() => parseSyncRequest({ since: 0, fasts: {} })).toThrow(HttpError);
    expect(() => parseSyncRequest({ since: 0, notes: Array(2000).fill(validNote), fasts: [fast] })).toThrow(HttpError);
  });
  it("parses a valid request and defaults absent arrays", () => {
    const parsed = parseSyncRequest({ since: 0, weights: [validWeight], meals: [validMeal], notes: [validNote] });
    expect(parsed.since).toBe(0);
    expect(parsed.profile).toBeNull();
    expect(parsed.weights).toHaveLength(1);
    expect(parsed.meals).toHaveLength(1);
    expect(parsed.notes).toEqual([validNote]);
    expect(parsed.rejected).toEqual([]);
  });

  it("defaults notes to an empty array for older clients", () => {
    const parsed = parseSyncRequest({ since: 0, weights: [validWeight], meals: [validMeal] });
    expect(parsed.notes).toEqual([]);
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
      notes: [
        { ...validNote, memo: "가".repeat(40), note: "나".repeat(2_000) },
        { ...validNote, epochDay: 20_001, memo: "", note: "", deleted: true },
      ],
    });
    expect(parsed.rejected).toEqual([]);
    expect(parsed.weights).toHaveLength(2);
    expect(parsed.meals).toHaveLength(2);
    expect(parsed.notes).toHaveLength(2);
  });

  it("counts memo and note limits by Unicode code points", () => {
    const parsed = parseSyncRequest({
      since: 0,
      notes: [
        { ...validNote, memo: "😀".repeat(40), note: "😀".repeat(2_000) },
        { ...validNote, epochDay: 20_001, memo: "😀".repeat(41) },
        { ...validNote, epochDay: 20_002, note: "😀".repeat(2_001) },
      ],
    });
    expect(parsed.notes.map((row) => row.epochDay)).toEqual([20_000]);
    expect(parsed.rejected).toEqual(["n:20001", "n:20002"]);
  });

  it("rejects notes outside the shared epoch-day range and with invalid timestamps", () => {
    const parsed = parseSyncRequest({
      since: 0,
      notes: [
        { ...validNote, epochDay: -25_568 },
        { ...validNote, epochDay: 84_371 },
        { ...validNote, epochDay: 20_001, updatedAt: -1 },
        { ...validNote, epochDay: 20_002, updatedAt: 0.5 },
      ],
    });
    expect(parsed.notes).toEqual([]);
    expect(parsed.rejected).toEqual(["n:-25568", "n:84371", "n:20001", "n:20002"]);
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

  it("applies the 2,000-row limit across all tables, including notes", () => {
    const notes = Array.from({ length: 2_000 }, (_, index) => ({ ...validNote, epochDay: index }));
    expect(() => parseSyncRequest({ since: 0, weights: [validWeight], notes })).toThrow(HttpError);
  });

  it("does not let rejected rows bypass the total row limit", () => {
    const rows = Array.from({ length: 2_001 }, (_, index) => ({ ...validWeight, id: `w${index}`, weightKg: 1 }));
    expect(() => parseSyncRequest({ since: 0, weights: rows })).toThrow(HttpError);
  });

  it("counts rejected rows across arrays toward the total limit", () => {
    const bad = (id: string) => ({ ...validWeight, id, weightKg: 1 });
    const badWeights = Array.from({ length: 1_000 }, (_, index) => bad(`w${index}`));
    const badMeals = Array.from({ length: 1_000 }, (_, index) => ({ ...validMeal, id: `m${index}`, kcal: -1 }));
    expect(() => parseSyncRequest({ since: 0, weights: badWeights, meals: badMeals, notes: [validNote] })).toThrow(HttpError);
  });
});
