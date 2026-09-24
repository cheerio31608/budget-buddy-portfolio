import { useEffect, useState } from 'react';
import { api, errorText, jsonBody, thisMonth } from './api';
import { Stats, TransactionTable } from './components';
import { CsvPage } from './CsvPage';
import { TransactionsPage } from './TransactionsPage';
import type { Analysis, Category, Report, Session, Transaction } from './types';

export default function App() {
  // Tokens stay in memory: refreshing the browser requires signing in again.
  const [session, setSession] = useState<Session | null>(null);
  const [page, setPage] = useState('dashboard');
  useEffect(() => { const expire = () => setSession(null); window.addEventListener('session-expired', expire); return () => window.removeEventListener('session-expired', expire); }, []);
  useEffect(() => { if (!session) return; const timer = setTimeout(() => setSession(null), Math.max(0, new Date(session.expiresAt).getTime() - Date.now())); return () => clearTimeout(timer); }, [session]);
  if (!session) return <Auth onLogin={setSession} />;
  const pages = [['dashboard', '대시보드'], ['transactions', '거래 내역'], ['csv', 'CSV 가져오기'], ['reports', 'AI 리포트']];
  return <div className="shell"><aside><a className="brand" href="#">Budget Buddy<span>나의 소비를 이해하는 시간</span></a><nav aria-label="주 메뉴">{pages.map(([id, name]) => <button key={id} aria-current={page === id ? 'page' : undefined} onClick={() => setPage(id)}>{name}</button>)}</nav><div className="account"><span>{session.email}</span><button onClick={async () => { try { await api('/api/web-auth/logout', session.accessToken, { method: 'POST' }); } catch { /* The local token is removed even when the server cannot be reached. */ } finally { setSession(null); } }}>로그아웃</button><small>보안을 위해 새로고침 시 다시 로그인합니다.</small></div></aside><main>
    {page === 'dashboard' && <Dashboard token={session.accessToken} />}
    {page === 'transactions' && <TransactionsPage token={session.accessToken} />}
    {page === 'csv' && <CsvPage token={session.accessToken} />}
    {page === 'reports' && <Reports token={session.accessToken} />}
  </main></div>;
}

function Auth({ onLogin }: { onLogin: (session: Session) => void }) {
  const [register, setRegister] = useState(false), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  return <main className="auth"><section className="auth-intro"><p className="eyebrow">YOUR PERSONAL MONEY JOURNAL</p><h1>Budget Buddy</h1><p>기록은 간단하게.<br />소비는 더 선명하게.</p><ul><li>나만의 거래 내역과 월별 소비 분석</li><li>CSV 가계부를 한곳에 가져오기</li><li>숫자를 이해하기 쉽게 설명하는 AI 리포트</li></ul></section><section className="card auth-form"><h2>{register ? '새 계정 만들기' : '다시 만나 반가워요'}</h2><p className="muted">DB 계정이 아닌 Budget Buddy 계정을 사용합니다.</p><form onSubmit={async e => { e.preventDefault(); const data = new FormData(e.currentTarget); setError(''); setBusy(true); try { onLogin(await api<Session>(`/api/web-auth/${register ? 'register' : 'login'}`, undefined, jsonBody({ email: data.get('email'), password: data.get('password') }))); } catch (e) { setError(errorText(e)); } finally { setBusy(false); } }}>
    <label>이메일<input name="email" type="email" autoComplete="username" maxLength={254} required /></label><label>비밀번호<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={register ? 10 : 1} maxLength={72} required /></label>{register && <small>10자 이상, UTF-8 기준 72바이트 이하로 설정해 주세요.</small>}{error && <p role="alert" className="error">{error}</p>}<button className="primary" disabled={busy}>{busy ? '확인 중…' : register ? '회원가입' : '로그인'}</button></form><button className="link" onClick={() => { setRegister(!register); setError(''); }}>{register ? '계정이 있나요? 로그인' : '처음인가요? 회원가입'}</button><p className="muted small">포트폴리오 데모입니다. 실제 민감한 금융정보는 입력하지 마세요.</p></section></main>;
}

function Dashboard({ token }: { token: string }) {
  const [month, setMonth] = useState(thisMonth()), [data, setData] = useState<Analysis | null>(null), [rows, setRows] = useState<Transaction[]>([]), [categories, setCategories] = useState<Category[]>([]), [error, setError] = useState('');
  useEffect(() => { let active = true; setData(null); setError(''); Promise.all([api<Analysis>(`/api/analysis/monthly?month=${month}`, token), api<Transaction[]>('/api/transactions', token), api<Category[]>('/api/categories', token)]).then(([a, t, c]) => { if (active) { setData(a); setRows(t); setCategories(c); } }).catch(e => active && setError(errorText(e))); return () => { active = false; }; }, [month, token]);
  return <><header><div><p className="eyebrow">OVERVIEW</p><h1>나의 가계부</h1><p className="muted">계산은 정확하게, 소비 습관은 한눈에.</p></div><label>분석할 월<input type="month" value={month} min="1900-01" max={thisMonth()} onChange={e => e.target.value && setMonth(e.target.value)} /></label></header>{error && <p role="alert" className="error">{error}</p>}{data ? <Stats data={data} /> : !error && <p role="status">분석을 불러오는 중…</p>}<section className="card"><h2>최근 거래</h2><TransactionTable rows={rows.slice(0, 5)} categories={categories} /></section></>;
}

function Reports({ token }: { token: string }) {
  const [month, setMonth] = useState(thisMonth()), [reports, setReports] = useState<Report[]>([]), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  useEffect(() => { api<Report[]>('/api/ai/reports', token).then(setReports).catch(e => setError(errorText(e))); }, [token]);
  return <><header><div><p className="eyebrow">INSIGHTS</p><h1>AI 소비 리포트</h1><p className="muted">서버가 계산한 통계를 Gemini가 해석합니다. 투자 조언이 아닙니다.</p></div></header><section className="card"><div className="actions"><label>분석할 월<input type="month" min="1900-01" max={thisMonth()} value={month} onChange={e => e.target.value && setMonth(e.target.value)} /></label><button className="primary" disabled={busy} onClick={async () => { setBusy(true); setError(''); try { await api(`/api/ai/report/monthly?month=${month}`, token, { method: 'POST' }); setReports(await api<Report[]>('/api/ai/reports', token)); } catch (e) { setError(errorText(e)); } finally { setBusy(false); } }}>{busy ? '분석 작성 중…' : '리포트 생성'}</button></div><p className="muted small">카테고리·사용처별 집계가 Google Gemini에 전달됩니다. 생성 시 동의한 것으로 간주합니다. 일일 사용량 및 대기시간 제한이 있으며 실패한 시도도 횟수에 포함됩니다.</p>{error && <p role="alert" className="error">{error}</p>}</section>{!reports.length && <p className="empty">저장된 리포트가 없습니다. 거래 등록 후 생성해 보세요.</p>}{reports.map(r => <article className="card" key={r.reportId}><p className="eyebrow">{r.reportType === 'CSV_ANALYSIS' ? 'CSV 분석' : `${r.reportMonth || '월간'} 분석`} · {new Date(r.generatedAt).toLocaleString('ko-KR')}</p><div className="report">{r.reportContent}</div></article>)}</>;
}
