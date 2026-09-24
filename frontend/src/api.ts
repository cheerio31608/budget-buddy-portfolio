const base = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
if (import.meta.env.PROD && (!import.meta.env.VITE_API_BASE_URL || !base.startsWith('https://'))) {
  throw new Error('Production requires VITE_API_BASE_URL with HTTPS.');
}

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}
const messages: Record<string, string> = {
  AUTH001: '이메일 또는 비밀번호를 확인해 주세요.', AUTH002: '로그인이 만료됐습니다. 다시 로그인해 주세요.',
  AUTH003: '이 작업을 수행할 권한이 없습니다.', T002: '잔액이 부족합니다. 수입을 먼저 등록하거나 CSV 행 순서를 확인해 주세요.',
  A002: 'AI 기능이 아직 설정되지 않았습니다.', A003: 'AI 서비스가 응답하지 않습니다. 잠시 후 다시 시도해 주세요.',
  A004: 'AI 생성 한도에 도달했거나 재생성 대기 중입니다. 잠시 후 또는 내일 다시 시도해 주세요.',
  A001: '이 기간에는 분석할 거래가 없습니다.', C003: '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  CSV001: 'CSV 파일이 비어 있습니다.', CSV003: '날짜, 카테고리, 금액, 구분에 해당하는 컬럼을 연결해 주세요.',
  CSV004: 'CSV 파일은 5MB 이하여야 합니다.',
};

export async function api<T>(path: string, token?: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let response: Response;
  try { response = await fetch(`${base}${path}`, { ...options, credentials: 'omit', headers }); }
  catch { throw new Error('서버에 연결하지 못했습니다. 네트워크와 서버 실행 상태를 확인해 주세요.'); }
  const isJson = response.headers.get('content-type')?.includes('application/json');
  const data = response.status === 204 ? null : isJson ? await response.json() : await response.text();
  if (!response.ok) {
    if (response.status === 401 && token) window.dispatchEvent(new Event('session-expired'));
    const code = typeof data === 'object' && data ? data.code || '' : '';
    throw new ApiError(response.status, code, messages[code] || data?.message || `요청 처리 실패 (${response.status})`);
  }
  return data as T;
}
export const jsonBody = (body: unknown): RequestInit => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
export const errorText = (error: unknown) => error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';
export const won = (value: number) => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(value) + '원';
export const thisMonth = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; };
