import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { CsvPage } from './CsvPage';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); localStorage.clear(); sessionStorage.clear(); });
describe('public web flow', () => {
  it('shows login failure safely without persisting credentials', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'AUTH001' }), { status: 401, headers: { 'content-type': 'application/json' } })));
    render(<App />);
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'person@example.com' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'incorrect-password' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
  it('rejects non CSV files before uploading', () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    render(<CsvPage token="test-token" />);
    fireEvent.change(screen.getByLabelText('파일 선택'), { target: { files: [new File(['x'], 'file.exe')] } });
    expect(screen.getByRole('alert')).toHaveTextContent('5MB 이하');
    expect(fetch).not.toHaveBeenCalled();
  });
  it('uploads a preview using Bearer, and does not automatically import it', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ headers: ['date'], rows: [{ date: '2026-01-01' }], suggestedMapping: { date: 'date' }, totalRows: 1 }), { headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', fetch); render(<CsvPage token="test-token" />);
    fireEvent.change(screen.getByLabelText('파일 선택'), { target: { files: [new File(['date\n2026-01-01'], 'data.csv')] } });
    await waitFor(() => expect(screen.getByText('2026-01-01')).toBeInTheDocument());
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][0]).toContain('/api/csv/preview');
    expect(fetch.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('automatically analyzes a CSV when all required columns are recognized', async () => {
    const preview = {
      headers: ['거래일자', '상호명', '분류', '금액', '구분'],
      rows: [{ 거래일자: '2026-08-01', 상호명: '마트', 분류: '식비', 금액: '12000', 구분: '지출' }],
      suggestedMapping: { date: '거래일자', description: '상호명', category: '분류', amount: '금액', type: '구분' },
      totalRows: 1,
    };
    const analysis = {
      validation: { validRows: 1, errorRows: 0, suspectedDuplicates: 0, errors: [] },
      analysis: null,
    };
    const fetch = vi.fn().mockImplementation(async (url: string) => {
      const response = url.includes('/preview') ? preview : analysis;
      return new Response(JSON.stringify(response), { headers: { 'content-type': 'application/json' } });
    });
    vi.stubGlobal('fetch', fetch);
    render(<CsvPage token="test-token" />);

    fireEvent.change(screen.getByLabelText('파일 선택'), {
      target: { files: [new File(['sample'], 'sample.csv')] },
    });

    expect(await screen.findByText('정상 1건 · 오류 0건 · 중복 의심 0건')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls[0][0]).toContain('/api/csv/preview');
    expect(fetch.mock.calls[1][0]).toContain('/api/csv/analyze');
    expect(screen.getByRole('button', { name: '거래 데이터에 저장' })).toBeDisabled();
  });
});
