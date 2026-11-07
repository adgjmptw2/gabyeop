import { HttpError, isObject } from "./http";
import type { SyncMeal, SyncProfile, SyncWeight } from "./types";

export const MAX_ROWS = 2_000;
export const MIN_EPOCH_DAY = -25_567;
export const MAX_EPOCH_DAY = 84_370;
const MAX_TIME = 9_007_199_254_740_991;
const MAX_ID_LENGTH = 128;
const MAX_NAME_LENGTH = 200;
const MAX_EMOJI_LENGTH = 32;
const MAX_UNIT_LENGTH = 16;
const MEAL_TYPES = new Set(["BREAKFAST", "LUNCH", "DINNER", "SNACK"]);
const SEXES = new Set(["MALE", "FEMALE"]);
const ACTIVITIES = new Set(["SEDENTARY", "LIGHT", "MODERATE", "ACTIVE"]);

export interface ParsedSync {
  since: number;
  profile: SyncProfile | null;
  weights: SyncWeight[];
  meals: SyncMeal[];
  rejected: string[];
}

export function parseSyncRequest(body: Record<string, unknown>): ParsedSync {
  const since = finiteNumber(body.since, "since", -MAX_TIME, MAX_TIME);
  if (!Number.isSafeInteger(since)) throw new HttpError(400, "since");
  const profile = body.profile === undefined || body.profile === null ? null : parseProfile(body.profile);
  const weights = parseRows(body.weights, "weights", parseWeight);
  const meals = parseRows(body.meals, "meals", parseMeal);
  if (weights.items.length + meals.items.length > MAX_ROWS) {
    throw new HttpError(413, "too_many_rows");
  }
  return {
    since,
    profile,
    weights: weights.items,
    meals: meals.items,
    rejected: [...weights.rejected, ...meals.rejected],
  };
}

export function parseProfile(value: unknown): SyncProfile {
  if (!isObject(value)) throw new HttpError(400, "invalid_profile");
  const sex = stringValue(value.sex, "invalid_profile", 16);
  const activity = stringValue(value.activity, "invalid_profile", 32);
  if (!SEXES.has(sex) || !ACTIVITIES.has(activity)) throw new HttpError(400, "invalid_profile");
  const birthYear = integer(value.birthYear, "invalid_profile", 1900, 2100);
  const heightCm = finiteNumber(value.heightCm, "invalid_profile", 100, 250);
  const startWeightKg = finiteNumber(value.startWeightKg, "invalid_profile", 20, 400);
  const targetWeightKg = finiteNumber(value.targetWeightKg, "invalid_profile", 20, 400);
  const startEpochDay = integer(value.startEpochDay, "invalid_profile", MIN_EPOCH_DAY, MAX_EPOCH_DAY);
  const updatedAt = integer(value.updatedAt, "invalid_profile", 0, MAX_TIME);
  return {
    sex,
    birthYear,
    heightCm,
    startWeightKg,
    targetWeightKg,
    activity,
    startEpochDay,
    updatedAt,
  };
}

interface ParsedRows<T> {
  items: T[];
  rejected: string[];
}

function parseRows<T>(value: unknown, field: string, parse: (row: unknown) => T): ParsedRows<T> {
  if (value === undefined || value === null) return { items: [], rejected: [] };
  if (!Array.isArray(value)) throw new HttpError(400, `invalid_${field}`);
  if (value.length > MAX_ROWS) throw new HttpError(413, "too_many_rows");
  const items: T[] = [];
  const rejected: string[] = [];
  for (const row of value) {
    let id = "invalid";
    if (isObject(row) && typeof row.id === "string" && row.id.length > 0 && row.id.length <= MAX_ID_LENGTH) {
      id = row.id;
    }
    try {
      items.push(parse(row));
    } catch {
      rejected.push(id);
    }
  }
  return { items, rejected };
}

function parseWeight(value: unknown): SyncWeight {
  if (!isObject(value)) throw new Error("invalid row");
  const id = stringValue(value.id, "invalid_weight", MAX_ID_LENGTH);
  const epochDay = integer(value.epochDay, "invalid_weight", MIN_EPOCH_DAY, MAX_EPOCH_DAY);
  const weightKg = finiteNumber(value.weightKg, "invalid_weight", 20, 400);
  const createdAt = integer(value.createdAt, "invalid_weight", 0, MAX_TIME);
  const updatedAt = integer(value.updatedAt, "invalid_weight", 0, MAX_TIME);
  const deleted = booleanValue(value.deleted, "invalid_weight");
  return { id, epochDay, weightKg, createdAt, updatedAt, deleted };
}

function parseMeal(value: unknown): SyncMeal {
  if (!isObject(value)) throw new Error("invalid row");
  const id = stringValue(value.id, "invalid_meal", MAX_ID_LENGTH);
  const epochDay = integer(value.epochDay, "invalid_meal", MIN_EPOCH_DAY, MAX_EPOCH_DAY);
  const mealType = stringValue(value.mealType, "invalid_meal", 16);
  if (!MEAL_TYPES.has(mealType)) throw new Error("invalid meal type");
  const foodId = optionalString(value.foodId, "invalid_meal", MAX_ID_LENGTH);
  const name = stringValue(value.name, "invalid_meal", MAX_NAME_LENGTH);
  const emoji = stringValue(value.emoji, "invalid_meal", MAX_EMOJI_LENGTH);
  const amount = finiteNumber(value.amount, "invalid_meal", 0, 1_000_000);
  const unit = stringValue(value.unit, "invalid_meal", MAX_UNIT_LENGTH);
  const kcal = finiteNumber(value.kcal, "invalid_meal", 0, 20_000);
  const carb = optionalNumber(value.carb, "invalid_meal", 0, 20_000);
  const prot = optionalNumber(value.prot, "invalid_meal", 0, 20_000);
  const fat = optionalNumber(value.fat, "invalid_meal", 0, 20_000);
  const createdAt = integer(value.createdAt, "invalid_meal", 0, MAX_TIME);
  const updatedAt = integer(value.updatedAt, "invalid_meal", 0, MAX_TIME);
  const deleted = booleanValue(value.deleted, "invalid_meal");
  return {
    id,
    epochDay,
    mealType,
    foodId,
    name,
    emoji,
    amount,
    unit,
    kcal,
    carb,
    prot,
    fat,
    createdAt,
    updatedAt,
    deleted,
  };
}

function stringValue(value: unknown, code: string, maxLength: number): string {
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new HttpError(400, code);
  }
  return value;
}

function optionalString(value: unknown, code: string, maxLength: number): string | null {
  if (value === undefined || value === null) return null;
  return stringValue(value, code, maxLength);
}

function finiteNumber(value: unknown, code: string, min: number, max: number): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    throw new HttpError(400, code);
  }
  return value;
}

function optionalNumber(value: unknown, code: string, min: number, max: number): number | null {
  if (value === undefined || value === null) return null;
  return finiteNumber(value, code, min, max);
}

function integer(value: unknown, code: string, min: number, max: number): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < min || value > max) {
    throw new HttpError(400, code);
  }
  return value;
}

function booleanValue(value: unknown, code: string): boolean {
  if (typeof value !== "boolean") throw new HttpError(400, code);
  return value;
}
