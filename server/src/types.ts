export interface Env {
  DB: D1Database;
  GOOGLE_WEB_CLIENT_ID: string;
  CONTACT_EMAIL: string;
  JWT_SECRET: string;
}

export interface SyncProfile {
  sex: string;
  birthYear: number;
  heightCm: number;
  startWeightKg: number;
  targetWeightKg: number;
  activity: string;
  startEpochDay: number;
  updatedAt: number;
}

export interface SyncWeight {
  id: string;
  epochDay: number;
  weightKg: number;
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

export interface SyncMeal {
  id: string;
  epochDay: number;
  mealType: string;
  foodId: string | null;
  name: string;
  emoji: string;
  amount: number;
  unit: string;
  kcal: number;
  carb: number | null;
  prot: number | null;
  fat: number | null;
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

export interface SyncNote {
  epochDay: number;
  memo: string;
  note: string;
  updatedAt: number;
  deleted: boolean;
}

export interface SyncRequest {
  since: number;
  profile: SyncProfile | null;
  weights: SyncWeight[];
  meals: SyncMeal[];
  notes: SyncNote[];
}

export interface SyncResponse {
  cursor: number;
  profile: SyncProfile | null;
  weights: SyncWeight[];
  meals: SyncMeal[];
  notes: SyncNote[];
  more: boolean;
  rejected: string[];
}
