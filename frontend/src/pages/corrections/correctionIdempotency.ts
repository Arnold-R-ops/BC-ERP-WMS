const STORAGE_PREFIX = 'wms:emergency-correction:apply:';
const memoryKeys = new Map<number, string>();

function storageKey(correctionId: number): string {
  return `${STORAGE_PREFIX}${correctionId}`;
}

function newKey(correctionId: number): string {
  const randomPart = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `esc-${correctionId}-${randomPart}`;
}

export function getStableCorrectionIdempotencyKey(correctionId: number): string {
  const inMemory = memoryKeys.get(correctionId);
  if (inMemory) return inMemory;

  const key = storageKey(correctionId);
  try {
    const stored = sessionStorage.getItem(key);
    if (stored) {
      memoryKeys.set(correctionId, stored);
      return stored;
    }
    const created = newKey(correctionId);
    sessionStorage.setItem(key, created);
    memoryKeys.set(correctionId, created);
    return created;
  } catch {
    const created = newKey(correctionId);
    memoryKeys.set(correctionId, created);
    return created;
  }
}

export function hasCorrectionIdempotencyKey(correctionId: number): boolean {
  if (memoryKeys.has(correctionId)) return true;
  try {
    const stored = sessionStorage.getItem(storageKey(correctionId));
    if (!stored) return false;
    memoryKeys.set(correctionId, stored);
    return true;
  } catch {
    return false;
  }
}

export function clearCorrectionIdempotencyKey(correctionId: number): void {
  memoryKeys.delete(correctionId);
  try {
    sessionStorage.removeItem(storageKey(correctionId));
  } catch {
    // Storage may be unavailable in hardened browser contexts. The in-memory key
    // was still cleared, so the next explicit attempt receives a new request key.
  }
}
