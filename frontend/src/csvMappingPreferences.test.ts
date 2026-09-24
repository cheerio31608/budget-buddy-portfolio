import { afterEach, describe, expect, it } from 'vitest';
import { rememberColumnMapping, restoreColumnMapping } from './csvMappingPreferences';
import type { Mapping } from './types';

const selectedMapping: Mapping = {
  date: '승인일시',
  description: '가맹점',
  category: '소비항목',
  amount: '사용금액',
  type: '구분',
};

afterEach(() => localStorage.clear());

describe('CSV column mapping preferences', () => {
  it('reuses a previously confirmed mapping when the same headers arrive in a different order', () => {
    const headers = ['승인일시', '가맹점', '소비항목', '사용금액', '구분'];
    rememberColumnMapping(headers, selectedMapping);

    const restored = restoreColumnMapping(
      ['구분', '사용금액', '소비항목', '가맹점', '승인일시'],
      { date: '승인일시', category: '소비항목' },
    );

    expect(restored).toEqual(selectedMapping);
  });

  it('ignores a saved mapping when a referenced column is no longer present', () => {
    rememberColumnMapping(['승인일시', '가맹점', '소비항목', '사용금액', '구분'], selectedMapping);

    const restored = restoreColumnMapping(
      ['승인일시', '가맹점', '소비항목', '금액', '구분'],
      { amount: '금액' },
    );

    expect(restored.amount).toBe('금액');
    expect(restored.description).toBe('');
  });
});
