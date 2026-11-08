import { resultRows } from "./d1";
import {
  orderedWeights, planNotes, planWeights, preferProfile,
  type NoteRow, type WeightPlanItem, type WeightRow,
} from "./merge";
import { HttpError } from "./http";
import type { ParsedSync } from "./validate";
import type { SyncFast, SyncMeal, SyncNote, SyncProfile, SyncWeight } from "./types";

export const RESPONSE_LIMIT = 2_000;
const SEQ_BLOCK = 1_000_000;

interface ProfileDatabaseRow {
  data: string;
  updated_at: number;
  seq: number;
}

interface WeightDatabaseRow {
  id: string;
  epoch_day: number;
  weight_kg: number;
  body_fat_pct?: number | null;
  created_at: number;
  updated_at: number;
  modified_at: number;
  deleted: number;
  seq: number;
}

interface MealDatabaseRow {
  id: string;
  epoch_day: number;
  meal_type: string;
  food_id: string | null;
  name: string;
  emoji: string;
  amount: number;
  unit: string;
  kcal: number;
  carb: number | null;
  prot: number | null;
  fat: number | null;
  created_at: number;
  updated_at: number;
  deleted: number;
  seq: number;
}

interface FastDatabaseRow {
  id: string; start_at: number; end_at: number | null; target_hours: number;
  updated_at: number; deleted: number; seq: number;
}

interface NoteDatabaseRow {
  water_ml?: number;
  epoch_day: number;
  memo: string;
  note: string;
  updated_at: number;
  deleted: number;
  seq: number;
}

interface StoredMeal extends SyncMeal {
  seq: number;
}

interface ProfileRow extends SyncProfile {
  seq: number;
}

interface Statement {
  sql: string;
  params: (string | number | null)[];
  tag: "counter" | "profileWrite" | "weightWrite" | "mealWrite" | "noteWrite" |
    "fastWrite" | "pageProfile" | "pageWeights" | "pageMeals" | "pageNotes" | "pageFasts";
}

export interface SyncResult {
  cursor: number;
  profile: SyncProfile | null;
  weights: SyncWeight[];
  meals: SyncMeal[];
  notes: SyncNote[];
  fasts: SyncFast[];
  more: boolean;
  rejected: string[];
}

export async function syncUser(db: D1Database, userId: string, request: ParsedSync): Promise<SyncResult> {
  const records = await db.batch([
    db.prepare("SELECT id FROM users WHERE id = ? LIMIT 1").bind(userId),
    db.prepare("SELECT data, updated_at, seq FROM profiles WHERE user_id = ?").bind(userId),
    relevantWeightsStatement(db, userId, request),
    relevantMealsStatement(db, userId, request),
    relevantNotesStatement(db, userId, request),
    db.prepare(`SELECT id, start_at, end_at, target_hours, updated_at, deleted, seq FROM fasts
      WHERE user_id = ? AND ((deleted = 0 AND end_at IS NULL) OR id IN (SELECT value FROM json_each(?)))`)
      .bind(userId, JSON.stringify(request.fasts.map((row) => row.id))),
  ]);
  if (resultRows<{ id: string }>(records[0])[0] === undefined) {
    throw new HttpError(401, "invalid_access_token");
  }
  const profileResult = resultRows<ProfileDatabaseRow>(records[1])[0] ?? null;
  const weightResult = resultRows<WeightDatabaseRow>(records[2]);
  const mealResult = resultRows<MealDatabaseRow>(records[3]);
  const noteResult = resultRows<NoteDatabaseRow>(records[4]);

  const storedProfile = profileResult === null ? null : parseStoredProfile(profileResult);
  const profileWrite = planProfile(storedProfile, request.profile);
  const weightPlan = orderedWeights(planWeights(request.weights, weightResult.map(toWeight)));
  const mealWrites = planMeals(mealResult.map(toMeal), request.meals);
  const noteWrites = planNotes(request.notes, noteResult.map(toNote));
  const fastWrites = planFasts(resultRows<FastDatabaseRow>(records[5]).map(toFast), request.fasts);
  const hasWrites = profileWrite !== null || weightPlan.length > 0 || mealWrites.length > 0 || noteWrites.length > 0 || fastWrites.length > 0;

  if (!hasWrites) return readPage(db, userId, request.since, request.rejected);

  const statements = buildBatch(userId, request.since, profileWrite, weightPlan, mealWrites, noteWrites, fastWrites);
  const results = await db.batch(statements.map((item) => db.prepare(item.sql).bind(...item.params)));
  const page = new Map<string, unknown>();
  statements.forEach((item, index) => {
    if (item.tag !== "counter") page.set(item.tag, results[index]);
  });
  return selectPage(
    resultRows<ProfileDatabaseRow>(page.get("pageProfile")).at(0) ?? null,
    resultRows<WeightDatabaseRow>(page.get("pageWeights")),
    resultRows<MealDatabaseRow>(page.get("pageMeals")),
    resultRows<NoteDatabaseRow>(page.get("pageNotes")),
    request.since,
    request.rejected,
    resultRows<FastDatabaseRow>(page.get("pageFasts")),
  );
}

function buildBatch(
  userId: string,
  since: number,
  profile: ProfileRow | null,
  weights: readonly WeightPlanItem[],
  meals: readonly (StoredMeal & { seq: number })[],
  notes: readonly NoteRow[],
  fasts: readonly (SyncFast & { seq: number })[],
): Statement[] {
  const statements: Statement[] = [
    { sql: "UPDATE counters SET value = value + ? WHERE name = 'seq'", params: [SEQ_BLOCK], tag: "counter" },
  ];
  let rank = 1;
  if (profile !== null) {
    const { seq: _seq, ...profileData } = profile;
    statements.push({
      sql: `INSERT INTO profiles (user_id, data, updated_at, seq)
            VALUES (?, ?, ?, (SELECT value FROM counters WHERE name = 'seq') - ${SEQ_BLOCK} + ${rank})
            ON CONFLICT(user_id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at, seq = excluded.seq
            WHERE excluded.updated_at > profiles.updated_at`,
      params: [userId, JSON.stringify(profileData), profile.updatedAt],
      tag: "profileWrite",
    });
    rank += 1;
  }
  if (weights.length > 0) {
    statements.push(weightUpsert(userId, weights, rank));
    rank += weights.length;
  }
  if (meals.length > 0) {
    statements.push(mealUpsert(userId, meals, rank));
    rank += meals.length;
  }
  if (notes.length > 0) {
    statements.push(noteUpsert(userId, notes, rank));
    rank += notes.length;
  }
  if (fasts.length > 0) statements.push(fastUpsert(userId, fasts, rank));
  statements.push({
    sql: "SELECT data, updated_at, seq FROM profiles WHERE user_id = ? AND seq > ? LIMIT 1",
    params: [userId, since],
    tag: "pageProfile",
  });
  statements.push({
    sql: `SELECT id, epoch_day, weight_kg, body_fat_pct, created_at, updated_at, modified_at, deleted, seq
          FROM weights WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?`,
    params: [userId, since, RESPONSE_LIMIT + 1],
    tag: "pageWeights",
  });
  statements.push({
    sql: `SELECT id, epoch_day, meal_type, food_id, name, emoji, amount, unit, kcal, carb, prot, fat,
                 created_at, updated_at, deleted, seq
          FROM meals WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?`,
    params: [userId, since, RESPONSE_LIMIT + 1],
    tag: "pageMeals",
  });
  statements.push({
    sql: `SELECT epoch_day, memo, note, water_ml, updated_at, deleted, seq
          FROM notes WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?`,
    params: [userId, since, RESPONSE_LIMIT + 1],
    tag: "pageNotes",
  });
  statements.push({
    sql: "SELECT id, start_at, end_at, target_hours, updated_at, deleted, seq FROM fasts WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?",
    params: [userId, since, RESPONSE_LIMIT + 1], tag: "pageFasts",
  });
  return statements;
}

function weightUpsert(userId: string, items: readonly WeightPlanItem[], firstRank: number): Statement {
  const rows = items.map((item, index) => ({
    id: item.row.id,
    userId,
    epochDay: item.row.epochDay,
    weightKg: item.row.weightKg,
    bodyFatPct: item.row.bodyFatPct ?? null,
    createdAt: item.row.createdAt,
    updatedAt: item.row.updatedAt,
    modifiedAt: item.row.modifiedAt,
    deleted: item.row.deleted ? 1 : 0,
    rank: firstRank + index,
  }));
  return {
    sql: `INSERT INTO weights (id, user_id, epoch_day, weight_kg, body_fat_pct, created_at, updated_at, modified_at, deleted, seq)
          SELECT s.id, s.user_id, s.epochDay, s.weightKg, s.bodyFatPct, s.createdAt, s.updatedAt, s.modifiedAt, s.deleted,
                 (SELECT value FROM counters WHERE name = 'seq') - ${SEQ_BLOCK} + s.rank
          FROM (
            SELECT json_extract(value, '$.id') AS id, json_extract(value, '$.userId') AS user_id,
                   json_extract(value, '$.epochDay') AS epochDay, json_extract(value, '$.weightKg') AS weightKg,
                   json_extract(value, '$.bodyFatPct') AS bodyFatPct,
                   json_extract(value, '$.createdAt') AS createdAt, json_extract(value, '$.updatedAt') AS updatedAt,
                   json_extract(value, '$.modifiedAt') AS modifiedAt,
                   json_extract(value, '$.deleted') AS deleted, json_extract(value, '$.rank') AS rank
            FROM json_each(?)
          ) AS s WHERE true
          ON CONFLICT(user_id, id) DO UPDATE SET epoch_day = excluded.epoch_day, weight_kg = excluded.weight_kg, body_fat_pct = excluded.body_fat_pct,
            created_at = excluded.created_at, updated_at = excluded.updated_at,
            modified_at = excluded.modified_at, deleted = excluded.deleted, seq = excluded.seq
          WHERE excluded.modified_at > weights.modified_at
             OR (excluded.deleted = 1 AND excluded.modified_at = weights.modified_at)`,
    params: [JSON.stringify(rows)],
    tag: "weightWrite",
  };
}

function mealUpsert(userId: string, meals: readonly StoredMeal[], firstRank: number): Statement {
  const rows = meals.map((meal, index) => ({
    id: meal.id,
    userId,
    epochDay: meal.epochDay,
    mealType: meal.mealType,
    foodId: meal.foodId,
    name: meal.name,
    emoji: meal.emoji,
    amount: meal.amount,
    unit: meal.unit,
    kcal: meal.kcal,
    carb: meal.carb,
    prot: meal.prot,
    fat: meal.fat,
    createdAt: meal.createdAt,
    updatedAt: meal.updatedAt,
    deleted: meal.deleted ? 1 : 0,
    rank: firstRank + index,
  }));
  return {
    sql: `INSERT INTO meals (id, user_id, epoch_day, meal_type, food_id, name, emoji, amount, unit, kcal,
                             carb, prot, fat, created_at, updated_at, deleted, seq)
          SELECT s.id, s.userId, s.epochDay, s.mealType, s.foodId, s.name, s.emoji, s.amount, s.unit,
                 s.kcal, s.carb, s.prot, s.fat, s.createdAt, s.updatedAt, s.deleted,
                 (SELECT value FROM counters WHERE name = 'seq') - ${SEQ_BLOCK} + s.rank
          FROM (
            SELECT json_extract(value, '$.id') AS id, json_extract(value, '$.userId') AS userId,
                   json_extract(value, '$.epochDay') AS epochDay, json_extract(value, '$.mealType') AS mealType,
                   json_extract(value, '$.foodId') AS foodId, json_extract(value, '$.name') AS name,
                   json_extract(value, '$.emoji') AS emoji, json_extract(value, '$.amount') AS amount,
                   json_extract(value, '$.unit') AS unit, json_extract(value, '$.kcal') AS kcal,
                   json_extract(value, '$.carb') AS carb, json_extract(value, '$.prot') AS prot,
                   json_extract(value, '$.fat') AS fat, json_extract(value, '$.createdAt') AS createdAt,
                   json_extract(value, '$.updatedAt') AS updatedAt, json_extract(value, '$.deleted') AS deleted,
                   json_extract(value, '$.rank') AS rank
            FROM json_each(?)
          ) AS s WHERE true
          ON CONFLICT(user_id, id) DO UPDATE SET epoch_day = excluded.epoch_day, meal_type = excluded.meal_type,
            food_id = excluded.food_id, name = excluded.name, emoji = excluded.emoji, amount = excluded.amount,
            unit = excluded.unit, kcal = excluded.kcal, carb = excluded.carb, prot = excluded.prot,
            fat = excluded.fat, created_at = excluded.created_at, updated_at = excluded.updated_at,
            deleted = excluded.deleted, seq = excluded.seq
          WHERE excluded.updated_at > meals.updated_at`,
    params: [JSON.stringify(rows)],
    tag: "mealWrite",
  };
}

function noteUpsert(userId: string, notes: readonly NoteRow[], firstRank: number): Statement {
  const rows = notes.map((note, index) => ({
    userId,
    epochDay: note.epochDay,
    memo: note.memo,
    note: note.note,
    waterMl: note.waterMl ?? 0,
    updatedAt: note.updatedAt,
    deleted: note.deleted ? 1 : 0,
    rank: firstRank + index,
  }));
  return {
    sql: `INSERT INTO notes (user_id, epoch_day, memo, note, water_ml, updated_at, deleted, seq)
          SELECT s.userId, s.epochDay, s.memo, s.note, s.waterMl, s.updatedAt, s.deleted,
                 (SELECT value FROM counters WHERE name = 'seq') - ${SEQ_BLOCK} + s.rank
          FROM (
            SELECT json_extract(value, '$.userId') AS userId,
                   json_extract(value, '$.epochDay') AS epochDay,
                   json_extract(value, '$.memo') AS memo, json_extract(value, '$.note') AS note,
                   json_extract(value, '$.waterMl') AS waterMl,
                   json_extract(value, '$.updatedAt') AS updatedAt,
                   json_extract(value, '$.deleted') AS deleted, json_extract(value, '$.rank') AS rank
            FROM json_each(?)
          ) AS s WHERE true
          ON CONFLICT(user_id, epoch_day) DO UPDATE SET memo = excluded.memo, note = excluded.note, water_ml = excluded.water_ml,
            updated_at = excluded.updated_at, deleted = excluded.deleted, seq = excluded.seq
          WHERE excluded.updated_at > notes.updated_at`,
    params: [JSON.stringify(rows)],
    tag: "noteWrite",
  };
}

function relevantWeightsStatement(db: D1Database, userId: string, request: ParsedSync): D1PreparedStatement {
  return db
    .prepare(
      `SELECT id, epoch_day, weight_kg, body_fat_pct, created_at, updated_at, modified_at, deleted, seq
       FROM weights
       WHERE user_id = ? AND (id IN (SELECT value FROM json_each(?)) OR epoch_day IN (SELECT value FROM json_each(?)))`,
    )
    .bind(
      userId,
      JSON.stringify(request.weights.map((row) => row.id)),
      JSON.stringify([...new Set(request.weights.map((row) => row.epochDay))]),
    );
}

function relevantMealsStatement(db: D1Database, userId: string, request: ParsedSync): D1PreparedStatement {
  return db
    .prepare(
      `SELECT id, epoch_day, meal_type, food_id, name, emoji, amount, unit, kcal, carb, prot, fat,
              created_at, updated_at, deleted, seq
       FROM meals WHERE user_id = ? AND id IN (SELECT value FROM json_each(?))`,
    )
    .bind(userId, JSON.stringify(request.meals.map((row) => row.id)));
}

function relevantNotesStatement(db: D1Database, userId: string, request: ParsedSync): D1PreparedStatement {
  return db
    .prepare(
      `SELECT epoch_day, memo, note, water_ml, updated_at, deleted, seq
       FROM notes WHERE user_id = ? AND epoch_day IN (SELECT value FROM json_each(?))`,
    )
    .bind(userId, JSON.stringify([...new Set(request.notes.map((row) => row.epochDay))]));
}

async function readPage(db: D1Database, userId: string, since: number, rejected: string[]): Promise<SyncResult> {
  const results = await db.batch([
    db.prepare("SELECT data, updated_at, seq FROM profiles WHERE user_id = ? AND seq > ? LIMIT 1").bind(userId, since),
    pageWeightsStatement(db, userId, since),
    pageMealsStatement(db, userId, since),
    pageNotesStatement(db, userId, since),
    db.prepare("SELECT id, start_at, end_at, target_hours, updated_at, deleted, seq FROM fasts WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?").bind(userId, since, RESPONSE_LIMIT + 1),
  ]);
  return selectPage(
    resultRows<ProfileDatabaseRow>(results[0])[0] ?? null,
    resultRows<WeightDatabaseRow>(results[1]),
    resultRows<MealDatabaseRow>(results[2]),
    resultRows<NoteDatabaseRow>(results[3]),
    since,
    rejected,
    resultRows<FastDatabaseRow>(results[4]),
  );
}

function pageWeightsStatement(db: D1Database, userId: string, since: number): D1PreparedStatement {
  return db
    .prepare(
      `SELECT id, epoch_day, weight_kg, body_fat_pct, created_at, updated_at, modified_at, deleted, seq
       FROM weights WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?`,
    )
    .bind(userId, since, RESPONSE_LIMIT + 1);
}

function pageMealsStatement(db: D1Database, userId: string, since: number): D1PreparedStatement {
  return db
    .prepare(
      `SELECT id, epoch_day, meal_type, food_id, name, emoji, amount, unit, kcal, carb, prot, fat,
              created_at, updated_at, deleted, seq
       FROM meals WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?`,
    )
    .bind(userId, since, RESPONSE_LIMIT + 1);
}

function pageNotesStatement(db: D1Database, userId: string, since: number): D1PreparedStatement {
  return db
    .prepare(
      `SELECT epoch_day, memo, note, water_ml, updated_at, deleted, seq
       FROM notes WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?`,
    )
    .bind(userId, since, RESPONSE_LIMIT + 1);
}

interface PageChange {
  seq: number;
  kind: "profile" | "weight" | "meal" | "note" | "fast";
  row: ProfileDatabaseRow | WeightDatabaseRow | MealDatabaseRow | NoteDatabaseRow | FastDatabaseRow;
}

/**
 * Merge the five per-table windows into one global sequence-ordered page.
 *
 * Each table reads one extra row. Taking the lowest 2,000 across all five
 * guarantees that no unreturned row has a seq below the returned cursor, even
 * when a profile revision has a much higher seq than food or weight rows.
 */
export function selectPage(
  profileRow: ProfileDatabaseRow | null,
  weightRows: readonly WeightDatabaseRow[],
  mealRows: readonly MealDatabaseRow[],
  noteRows: readonly NoteDatabaseRow[],
  since: number,
  rejected: string[],
  fastRows: readonly FastDatabaseRow[] = [],
): SyncResult {
  const changes: PageChange[] = [];
  if (profileRow !== null && profileRow.seq > since) {
    changes.push({ seq: profileRow.seq, kind: "profile", row: profileRow });
  }
  for (const row of weightRows) {
    if (row.seq > since) changes.push({ seq: row.seq, kind: "weight", row });
  }
  for (const row of mealRows) {
    if (row.seq > since) changes.push({ seq: row.seq, kind: "meal", row });
  }
  for (const row of noteRows) {
    if (row.seq > since) changes.push({ seq: row.seq, kind: "note", row });
  }
  for (const row of fastRows) {
    if (row.seq > since) changes.push({ seq: row.seq, kind: "fast", row });
  }
  changes.sort((left, right) => left.seq - right.seq);

  const page = changes.slice(0, RESPONSE_LIMIT);
  const profileChange = page.find((change) => change.kind === "profile");
  const weightChanges = page.filter((change) => change.kind === "weight");
  const mealChanges = page.filter((change) => change.kind === "meal");
  const noteChanges = page.filter((change) => change.kind === "note");
  const profile = profileChange === undefined ? null : parseStoredProfile(profileChange.row as ProfileDatabaseRow);
  const weights = weightChanges.map((change) => toWeight(change.row as WeightDatabaseRow));
  const meals = mealChanges.map((change) => toMeal(change.row as MealDatabaseRow));
  const notes = noteChanges.map((change) => toNote(change.row as NoteDatabaseRow));
  const last = page.at(-1);
  const cursor = last === undefined ? since : Math.max(since, last.seq);

  return {
    cursor,
    profile: profile === null ? null : stripSeq(profile),
    weights: weights.map(wireWeight),
    meals: meals.map(stripSeq),
    notes: notes.map(stripSeq),
    fasts: page.filter((change) => change.kind === "fast").map((change) => stripSeq(toFast(change.row as FastDatabaseRow))),
    more: changes.some((change) => change.seq > cursor),
    rejected,
  };
}

function parseStoredProfile(row: ProfileDatabaseRow): (SyncProfile & { seq: number }) | null {
  try {
    const parsed: unknown = JSON.parse(row.data);
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return null;
    return { ...(parsed as SyncProfile), updatedAt: row.updated_at, seq: row.seq };
  } catch {
    return null;
  }
}

function planProfile(stored: (SyncProfile & { seq: number }) | null, incoming: SyncProfile | null): ProfileRow | null {
  if (incoming === null) return null;
  if (stored !== null && !preferProfile(incoming, stored)) return null;
  return { ...incoming, seq: 0 };
}

function planMeals(stored: readonly StoredMeal[], incoming: readonly SyncMeal[]): Array<StoredMeal & { seq: number }> {
  const byId = new Map<string, StoredMeal>();
  for (const row of stored) byId.set(row.id, row);
  const changed = new Map<string, StoredMeal & { seq: number }>();
  for (const source of incoming) {
    const previous = byId.get(source.id);
    if (previous !== undefined && source.updatedAt <= previous.updatedAt) continue;
    const row = { ...source, seq: 0 };
    byId.set(row.id, row);
    changed.set(row.id, row);
  }
  return [...changed.values()];
}

function toWeight(row: WeightDatabaseRow): WeightRow {
  return {
    id: row.id,
    epochDay: row.epoch_day,
    weightKg: row.weight_kg,
    bodyFatPct: row.body_fat_pct ?? null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    modifiedAt: row.modified_at,
    deleted: row.deleted === 1,
    seq: row.seq,
  };
}

function toMeal(row: MealDatabaseRow): StoredMeal {
  return {
    id: row.id,
    epochDay: row.epoch_day,
    mealType: row.meal_type,
    foodId: row.food_id,
    name: row.name,
    emoji: row.emoji,
    amount: row.amount,
    unit: row.unit,
    kcal: row.kcal,
    carb: row.carb,
    prot: row.prot,
    fat: row.fat,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    deleted: row.deleted === 1,
    seq: row.seq,
  };
}

function toNote(row: NoteDatabaseRow): NoteRow {
  return {
    epochDay: row.epoch_day,
    memo: row.memo,
    note: row.note,
    waterMl: row.water_ml ?? 0,
    updatedAt: row.updated_at,
    deleted: row.deleted === 1,
    seq: row.seq,
  };
}

function stripSeq<T extends { seq: number }>(row: T): Omit<T, "seq"> {
  const { seq: _seq, ...rest } = row;
  return rest;
}

function wireWeight(row: WeightRow): SyncWeight {
  const { seq: _seq, modifiedAt: _modifiedAt, ...weight } = row;
  return weight;
}

function toFast(row: FastDatabaseRow): SyncFast & { seq: number } {
  return { id: row.id, startAt: row.start_at, endAt: row.end_at, targetHours: row.target_hours,
    updatedAt: row.updated_at, deleted: row.deleted === 1, seq: row.seq };
}

/** Strict LWW per id, then deterministic resolution of simultaneous starts. */
export function planFasts(stored: readonly (SyncFast & { seq: number })[], incoming: readonly SyncFast[]): Array<SyncFast & { seq: number }> {
  const byId = new Map(stored.map((row) => [row.id, row]));
  const changed = new Map<string, SyncFast & { seq: number }>();
  for (const source of incoming) {
    const previous = byId.get(source.id);
    if (previous !== undefined && source.updatedAt <= previous.updatedAt) continue;
    const row = { ...source, seq: 0 };
    byId.set(row.id, row);
    changed.set(row.id, row);
  }
  const active = [...byId.values()].filter((row) => !row.deleted && row.endAt === null)
    .sort((a, b) => b.updatedAt - a.updatedAt || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
  const winner = active[0];
  if (winner !== undefined) for (const loser of active.slice(1)) {
    changed.set(loser.id, { ...loser, deleted: true, updatedAt: Math.max(winner.updatedAt, loser.updatedAt) + 1, seq: 0 });
  }
  return [...changed.values()];
}

function fastUpsert(userId: string, fasts: readonly SyncFast[], firstRank: number): Statement {
  const rows = fasts.map((row, index) => ({ ...row, userId, deleted: row.deleted ? 1 : 0, rank: firstRank + index }));
  return {
    sql: `INSERT INTO fasts (id, user_id, start_at, end_at, target_hours, updated_at, deleted, seq)
      SELECT json_extract(value, '$.id'), json_extract(value, '$.userId'), json_extract(value, '$.startAt'),
        json_extract(value, '$.endAt'), json_extract(value, '$.targetHours'), json_extract(value, '$.updatedAt'),
        json_extract(value, '$.deleted'), (SELECT value FROM counters WHERE name = 'seq') - ${SEQ_BLOCK} + json_extract(value, '$.rank')
      FROM json_each(?) WHERE true
      ON CONFLICT(user_id, id) DO UPDATE SET start_at = excluded.start_at, end_at = excluded.end_at,
        target_hours = excluded.target_hours, updated_at = excluded.updated_at, deleted = excluded.deleted, seq = excluded.seq
      WHERE excluded.updated_at > fasts.updated_at`,
    params: [JSON.stringify(rows)], tag: "fastWrite",
  };
}
