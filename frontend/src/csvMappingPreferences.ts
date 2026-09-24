import type { Mapping } from './types';

const STORAGE_PREFIX = 'budget-buddy.csv-column-mapping.v1:';
const MAPPING_FIELDS: (keyof Mapping)[] = ['date', 'description', 'category', 'amount', 'type'];

/** Mapping preferences contain header names only, never uploaded transaction rows. */
export function restoreColumnMapping(
  headers: string[],
  suggested: Partial<Mapping>,
  storage?: Storage,
): Mapping {
  const mapping: Mapping = { date: '', description: '', category: '', amount: '', type: '', ...suggested };
  try {
    const saved = (storage ?? window.localStorage).getItem(storageKey(headers));
    if (!saved) return mapping;

    const parsed: unknown = JSON.parse(saved);
    if (typeof parsed !== 'object' || parsed === null) return mapping;
    const savedMapping = parsed as Partial<Mapping>;
    for (const field of MAPPING_FIELDS) {
      const header = savedMapping[field];
      if (typeof header === 'string' && (header === '' || headers.includes(header))) {
        mapping[field] = header;
      }
    }
  } catch {
    // If browser storage is unavailable or malformed, use the server's suggestions.
  }
  return mapping;
}

export function rememberColumnMapping(headers: string[], mapping: Mapping, storage?: Storage): void {
  try {
    (storage ?? window.localStorage).setItem(storageKey(headers), JSON.stringify(mapping));
  } catch {
    // Analysis should still work when the browser cannot persist preferences.
  }
}

function storageKey(headers: string[]): string {
  const schema = headers
    .map(header => header.normalize('NFKC').toLowerCase().replace(/[\s_-]/g, ''))
    .sort();
  return `${STORAGE_PREFIX}${JSON.stringify(schema)}`;
}
