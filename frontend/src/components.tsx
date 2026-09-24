import { won } from './api';
import type { Analysis, Category, Transaction } from './types';

export function Stats({ data }: { data: Analysis }) {
  return <><div className="metrics">
    {[['현재 잔액', data.currentBalance], ['선택한 달 수입', data.totalIncome], ['선택한 달 지출', data.totalExpense]].map(([label, value]) =>
      <article className="metric" key={label}><span>{label}</span><strong>{won(Number(value))}</strong></article>)}
    <article className="metric"><span>전월 대비 지출</span><strong>{data.expenseChangeRate == null ? '비교 없음' : `${data.expenseChangeRate > 0 ? '+' : ''}${data.expenseChangeRate}%`}</strong></article>
  </div><p className="muted">분석 기간 {data.periodStart} ~ {data.periodEnd} · 현재 달은 전월 동기간과 비교합니다.</p>
  <div className="grid-two"><section className="card"><h2>어디에 가장 많이 썼을까요?</h2><p className="muted">카테고리별 지출</p>
    {!data.categoryExpenses.length && <p className="empty">지출을 등록하면 소비 비율을 볼 수 있어요.</p>}
    {data.categoryExpenses.map(c => <div className="bar-item" key={c.category}><div><span>{c.category}</span><strong>{won(c.amount)}</strong></div><meter min={0} max={Math.max(data.totalExpense, 1)} value={c.amount} aria-label={`${c.category} 지출 비율`} /></div>)}
  </section><section className="card"><h2>월별 소비 흐름</h2><p className="muted">최근 6개월 지출</p><div className="trend">
    {data.monthlyExpenses.map(p => <div className="trend-column" key={p.label}><small>{won(p.amount)}</small><div className="trend-track"><div style={{ height: `${Math.max(2, p.amount / Math.max(1, ...data.monthlyExpenses.map(x => x.amount)) * 100)}%` }} /></div><span>{p.label}</span></div>)}
  </div></section></div>
  <div className="summary"><span>거래 <strong>{data.transactionCount}건</strong></span><span>평균 지출 <strong>{won(data.averageExpense)}</strong></span><span>자주 찾는 사용처 <strong>{data.topVendor || '아직 없음'}</strong></span></div></>;
}

export function TransactionTable({ rows, categories }: { rows: Transaction[]; categories: Category[] }) {
  return <div className="table-scroll"><table><thead><tr><th>거래일</th><th>사용처 / 설명</th><th>카테고리</th><th>구분</th><th className="right">금액</th><th className="right">등록 후 잔액</th></tr></thead><tbody>
    {rows.map(t => <tr key={t.transactionId}><td>{t.transactionAt.slice(0, 10)}</td><td>{t.vendorName || t.description || '사용처 미입력'}</td><td>{categories.find(c => c.categoryId === t.categoryId)?.name || '기타'}</td><td><span className={`badge ${t.transactionType === 'INCOME' ? 'income' : ''}`}>{t.transactionType === 'INCOME' ? '수입' : '지출'}</span></td><td className="right">{t.transactionType === 'INCOME' ? '+' : '−'}{won(t.amount)}</td><td className="right">{won(t.balanceAfter)}</td></tr>)}
    {!rows.length && <tr><td colSpan={6} className="empty">표시할 거래가 없습니다. 첫 수입을 등록해 보세요.</td></tr>}
  </tbody></table></div>;
}
