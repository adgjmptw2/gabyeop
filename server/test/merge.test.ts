import { describe, expect, it } from "vitest";
import { planWeights, preferProfile, type WeightRow } from "../src/merge";
import type { SyncProfile, SyncWeight } from "../src/types";

function weight(id: string, epochDay: number, updatedAt: number, deleted = false): SyncWeight {
  return { id, epochDay, weightKg: 70, createdAt: 1, updatedAt, deleted };
}

function stored(id: string, epochDay: number, updatedAt: number, modifiedAt = updatedAt, deleted = false): WeightRow {
  return { ...weight(id, epochDay, updatedAt, deleted), modifiedAt, seq: 1 };
}

function liveRows(items: ReturnType<typeof planWeights>): string[] {
  return items.filter((item) => !item.row.deleted).map((item) => item.row.id).sort();
}

describe("profile LWW", () => {
  const base: SyncProfile = {
    sex: "FEMALE", birthYear: 1990, heightCm: 165, startWeightKg: 70,
    targetWeightKg: 60, activity: "LIGHT", startEpochDay: 20_000, updatedAt: 100,
  };

  it("accepts a newer profile and rejects an older one", () => {
    expect(preferProfile({ ...base, updatedAt: 101 }, base)).toBe(true);
    expect(preferProfile({ ...base, updatedAt: 99 }, base)).toBe(false);
  });

  it("keeps the server profile on an equal timestamp", () => {
    expect(preferProfile({ ...base, targetWeightKg: 55 }, base)).toBe(false);
  });
});

describe("weight merge", () => {
  it("adds a new row on an unused day", () => {
    const plan = planWeights([weight("a", 100, 10)], []);
    expect(liveRows(plan)).toEqual(["a"]);
    expect(plan[0]?.tombstone).toBe(false);
  });

  it("ignores an older update to the same id", () => {
    const plan = planWeights([weight("a", 100, 9)], [stored("a", 100, 10)]);
    expect(plan).toEqual([]);
  });

  it("applies a newer update to the same id", () => {
    const plan = planWeights([weight("a", 100, 11)], [stored("a", 100, 10)]);
    expect(plan).toHaveLength(1);
    expect(plan[0]?.row.updatedAt).toBe(11);
  });

  it("keeps the stored row when a different id has an equal timestamp", () => {
    const plan = planWeights([weight("b", 100, 10)], [stored("a", 100, 10)]);
    expect(plan).toEqual([]);
  });

  it("replaces the stored row and returns a tombstone when a new id is newer", () => {
    const plan = planWeights([weight("b", 100, 11)], [stored("a", 100, 10)]);
    expect(liveRows(plan)).toEqual(["b"]);
    expect(plan.find((item) => item.row.id === "a")?.tombstone).toBe(true);
    expect(plan.find((item) => item.row.id === "a")?.row.updatedAt).toBeLessThan(11);
  });

  it("leaves an older different-id row alone", () => {
    const plan = planWeights([weight("b", 100, 9)], [stored("a", 100, 10)]);
    expect(plan).toEqual([]);
  });

  it("collapses duplicate stored rows for one day deterministically", () => {
    const plan = planWeights([], [stored("a", 100, 10), stored("b", 100, 9)]);
    expect(plan).toHaveLength(1);
    expect(plan[0]?.row.id).toBe("b");
    expect(plan[0]?.tombstone).toBe(true);
  });

  it("deletes the live row when the client marks it deleted", () => {
    const plan = planWeights([weight("a", 100, 11, true)], [stored("a", 100, 10)]);
    expect(plan).toHaveLength(1);
    expect(plan[0]?.row.deleted).toBe(true);
    expect(plan[0]?.userDeletion).toBe(true);
  });

  it("moves a row to another day without leaving the old day live", () => {
    const plan = planWeights([weight("a", 101, 11)], [stored("a", 100, 10)]);
    expect(plan).toHaveLength(1);
    expect(plan[0]?.row.epochDay).toBe(101);
  });

  it("lets a newer deleted row reclaim its day after a collision tombstone", () => {
    const storedRows = [stored("a", 100, 10), stored("b", 100, 9)];
    const plan = planWeights([weight("a", 100, 12, true)], storedRows);
    const rowA = plan.find((item) => item.row.id === "a");
    expect(rowA?.row.deleted).toBe(true);
    expect(rowA?.tombstone).toBe(false);
  });
});
