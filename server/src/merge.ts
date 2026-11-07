import type { SyncProfile, SyncWeight } from "./types";

export interface WeightRow extends SyncWeight {
  seq: number;
  /** Server-only modification time; may be newer than the client-visible updatedAt. */
  modifiedAt: number;
}

export interface WeightPlanItem {
  row: WeightRow;
  /** Server-created deletion for a row that lost a same-day conflict. */
  tombstone: boolean;
  /** User-initiated deletion, which may delete the current live row. */
  userDeletion?: boolean;
}

/** The specification requires the server copy to win an equal-timestamp tie. */
export function newerUpdatedAt(incoming: number, stored: number): boolean {
  return incoming > stored;
}

export function preferProfile(incoming: SyncProfile, stored: SyncProfile): boolean {
  return newerUpdatedAt(incoming.updatedAt, stored.updatedAt);
}

function pickStoredWinner(left: WeightRow, right: WeightRow): WeightRow {
  if (right.updatedAt !== left.updatedAt) return right.updatedAt > left.updatedAt ? right : left;
  if (right.modifiedAt !== left.modifiedAt) return right.modifiedAt > left.modifiedAt ? right : left;
  return right.id < left.id ? right : left;
}

function makeTombstone(row: WeightRow, winnerUpdatedAt: number): WeightRow {
  return {
    ...row,
    deleted: true,
    // Keep the tombstone ordered before the winner in the client's updatedAt sort.
    updatedAt: Math.max(0, winnerUpdatedAt - 1),
    modifiedAt: winnerUpdatedAt,
    seq: 0,
  };
}

/**
 * Decide the final rows for every epoch day touched by the request.
 *
 * Stored rows are reduced to one live winner per day. Incoming rows are then
 * applied in request order. An incoming row must be strictly newer than both the
 * stored copy of the same id and the current live row for that day, so equal
 * timestamps keep the server row as required by the sync contract.
 */
export function planWeights(incoming: readonly SyncWeight[], stored: readonly WeightRow[]): WeightPlanItem[] {
  const byId = new Map<string, WeightRow>();
  for (const row of stored) byId.set(row.id, { ...row });

  const live = new Map<number, WeightRow>();
  const planned = new Map<string, WeightPlanItem>();

  for (const row of [...byId.values()]) {
    if (row.deleted) continue;
    const current = live.get(row.epochDay);
    if (current === undefined) {
      live.set(row.epochDay, row);
      continue;
    }
    const winner = pickStoredWinner(current, row);
    const loser = winner.id === current.id ? row : current;
    live.set(row.epochDay, winner);
    if (loser.id === winner.id) continue;
    const tombstone = makeTombstone(loser, winner.updatedAt);
    planned.set(loser.id, { row: tombstone, tombstone: true });
    byId.set(loser.id, tombstone);
  }

  for (const source of incoming) {
    const existing = byId.get(source.id);
    if (existing !== undefined && source.updatedAt <= existing.modifiedAt) continue;

    const current = live.get(source.epochDay);
    if (current !== undefined && current.id !== source.id && source.updatedAt <= current.modifiedAt) continue;

    if (existing !== undefined && existing.epochDay !== source.epochDay && !existing.deleted) {
      const oldLive = live.get(existing.epochDay);
      if (oldLive !== undefined && oldLive.id === source.id) live.delete(existing.epochDay);
    }

    if (current !== undefined && current.id !== source.id) {
      const tombstone = makeTombstone(current, source.updatedAt);
      planned.set(current.id, { row: tombstone, tombstone: true });
      byId.set(current.id, tombstone);
    }

    const row: WeightRow = { ...source, modifiedAt: source.updatedAt, seq: 0 };
    planned.set(row.id, { row, tombstone: false, userDeletion: row.deleted });
    byId.set(row.id, row);
    if (row.deleted) live.delete(row.epochDay);
    else live.set(row.epochDay, row);
  }

  return [...planned.values()];
}

/** The Android client applies rows sorted by updatedAt, so tombstones come first. */
export function orderedWeights(items: readonly WeightPlanItem[]): WeightPlanItem[] {
  return [
    ...items.filter((item) => item.tombstone),
    ...items.filter((item) => !item.tombstone),
  ];
}
