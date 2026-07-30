import type { StocktakeItem } from '../../api/stocktake';

export type StocktakeScanResult =
  | { kind: 'match'; item: StocktakeItem }
  | { kind: 'ambiguous'; candidateIds: number[] }
  | { kind: 'none' };

function normalized(value?: string): string {
  return value?.trim().toLowerCase() ?? '';
}

function itemMatches(item: StocktakeItem, scan: string): boolean {
  return [item.locationCode, item.batchCode, item.productBarcode]
    .some((value) => normalized(value) === scan);
}

export function resolveStocktakeScan(
  items: StocktakeItem[],
  rawScan: string,
  candidateIds: number[] = [],
): StocktakeScanResult {
  const scan = normalized(rawScan);
  if (!scan) return { kind: 'none' };

  const uncounted = items.filter((item) => item.id !== undefined && !item.isCounted);
  const scope = candidateIds.length > 0
    ? uncounted.filter((item) => candidateIds.includes(item.id as number))
    : uncounted;
  const matches = scope.filter((item) => itemMatches(item, scan));

  if (matches.length === 1) return { kind: 'match', item: matches[0] };
  if (matches.length > 1) {
    return { kind: 'ambiguous', candidateIds: matches.map((item) => item.id as number) };
  }
  return { kind: 'none' };
}
