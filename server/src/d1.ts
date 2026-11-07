/** D1 result shape used locally so small differences between workers-types versions do not leak. */
export interface QueryResult<T> {
  results?: T[];
  changes?: number;
  meta?: { changes?: number };
}

export async function runBatch(
  db: D1Database,
  statements: readonly D1PreparedStatement[],
): Promise<QueryResult<unknown>[]> {
  if (statements.length === 0) return [];
  return (await db.batch([...statements])) as unknown as QueryResult<unknown>[];
}

export function resultRows<T>(result: unknown): T[] {
  const rows = (result as QueryResult<T> | undefined)?.results;
  return Array.isArray(rows) ? rows : [];
}

export function changesOf(result: QueryResult<unknown> | undefined): number {
  if (result === undefined) return 0;
  return result.changes ?? result.meta?.changes ?? 0;
}

export function placeholders(count: number): string {
  return new Array(count).fill("?").join(", ");
}

export function chunks<T>(items: readonly T[], size: number): T[][] {
  const output: T[][] = [];
  for (let index = 0; index < items.length; index += size) {
    output.push(items.slice(index, index + size));
  }
  return output;
}
