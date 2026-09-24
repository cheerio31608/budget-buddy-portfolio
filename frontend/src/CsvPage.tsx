import { useState } from 'react';
import { api, errorText } from './api';
import { Stats } from './components';
import { rememberColumnMapping, restoreColumnMapping } from './csvMappingPreferences';
import type { CsvResult, Mapping, Preview } from './types';

const fields: { key: keyof Mapping; label: string; required: boolean }[] = [
  { key: 'date', label: '거래일', required: true },
  { key: 'description', label: '사용처 / 설명', required: false },
  { key: 'category', label: '카테고리', required: true },
  { key: 'amount', label: '금액', required: true },
  { key: 'type', label: '수입 / 지출', required: true },
];

const emptyMapping: Mapping = { date: '', description: '', category: '', amount: '', type: '' };
const requiredFields: (keyof Mapping)[] = ['date', 'category', 'amount', 'type'];

function hasRequiredMappings(mapping: Mapping): boolean {
  return requiredFields.every(field => Boolean(mapping[field]));
}

export function CsvPage({ token }: { token: string }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [mapping, setMapping] = useState<Mapping>(emptyMapping);
  const [result, setResult] = useState<CsvResult | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [report, setReport] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmed, setConfirmed] = useState(false);

  const mappingReady = hasRequiredMappings(mapping);

  const upload = async <T,>(path: string, selected = file, selectedMapping = mapping): Promise<T> => {
    if (!selected) throw new Error('파일을 먼저 선택해 주세요.');
    const body = new FormData();
    body.append('file', selected);
    body.append('mapping', new Blob([JSON.stringify(selectedMapping)], { type: 'application/json' }));
    return api<T>(`/api/csv/${path}`, token, { method: 'POST', body });
  };

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setBusy(false);
    }
  };

  const selectFile = (selected?: File) => {
    setFile(null);
    setPreview(null);
    setResult(null);
    setReport('');
    setConfirmed(false);
    setMapping(emptyMapping);
    if (!selected) return;
    if (!selected.name.toLowerCase().endsWith('.csv') || selected.size > 5 * 1024 * 1024) {
      setError('5MB 이하의 CSV 파일을 선택해 주세요.');
      return;
    }

    void run(async () => {
      const uploadedPreview = await upload<Preview>('preview', selected);
      const restoredMapping = restoreColumnMapping(uploadedPreview.headers, uploadedPreview.suggestedMapping);
      setFile(selected);
      setPreview(uploadedPreview);
      setMapping(restoredMapping);

      if (hasRequiredMappings(restoredMapping)) {
        // Recognized files are analyzed immediately; CSV rows are never saved by this request.
        setResult(await upload<CsvResult>('analyze', selected, restoredMapping));
      }
    });
  };

  const analyzeWithSelectedMapping = () => void run(async () => {
    if (!preview) return;
    rememberColumnMapping(preview.headers, mapping);
    setResult(await upload<CsvResult>('analyze'));
    setConfirmed(false);
  });

  return <>
    <header>
      <div>
        <p className="eyebrow">CSV IMPORT</p>
        <h1>내 가계부 가져오기</h1>
        <p className="muted">CSV를 선택하면 컬럼을 자동 연결해 임시 분석합니다. 저장 전에는 거래 데이터가 바뀌지 않습니다.</p>
      </div>
    </header>

    <section className="card">
      <h2>1. CSV 파일 선택</h2>
      <p className="muted">.csv · 최대 5MB / 분석 5,000행 / 저장 500행 · UTF-8 또는 MS949</p>
      <label className="upload">
        파일 선택
        <input type="file" accept=".csv,text/csv" disabled={busy}
          onChange={event => selectFile(event.target.files?.[0])} />
      </label>
      <p className="small">자주 쓰이는 한국어·영어 헤더를 자동 인식합니다. 확실하지 않은 컬럼만 직접 연결해 주세요.</p>
    </section>

    {preview && <section className="card">
      <h2>2. 미리보기 · {preview.totalRows}행</h2>
      <div className="table-scroll">
        <table>
          <thead><tr>{preview.headers.map(header => <th key={header}>{header}</th>)}</tr></thead>
          <tbody>{preview.rows.slice(0, 10).map((row, index) =>
            <tr key={index}>{preview.headers.map(header => <td key={header}>{row[header]}</td>)}</tr>)}</tbody>
        </table>
      </div>

      <details open={!mappingReady}>
        <summary>{mappingReady ? '자동 연결된 컬럼 보기 / 수정' : '자동 연결이 어려운 필수 컬럼을 연결해 주세요'}</summary>
        <div className="filters mapping-fields">
          {fields.map(({ key, label, required }) => <label key={key}>
            {label}{required ? ' *' : ' (선택)'}
            <select value={mapping[key]} disabled={busy} onChange={event => {
              const updated = { ...mapping, [key]: event.target.value };
              setMapping(updated);
              setResult(null);
              setReport('');
              setConfirmed(false);
            }}>
              <option value="">{required ? '컬럼 선택' : '연결하지 않음'}</option>
              {preview.headers.map(header => <option key={header} value={header}>{header}</option>)}
            </select>
          </label>)}
        </div>
        <p className="muted small">한 번 직접 연결하고 분석하면 같은 헤더 구성을 다음 업로드에서 재사용합니다. 이 설정에는 거래 행이나 비밀번호가 저장되지 않습니다.</p>
      </details>

      {mappingReady && result === null && !busy && <button className="primary" onClick={analyzeWithSelectedMapping}>
        검증 및 임시 분석
      </button>}
    </section>}

    {busy && <p role="status" className="muted">CSV를 확인하고 분석하는 중…</p>}
    {error && <p role="alert" className="error">{error}</p>}
    {notice && <p role="status" className="success">{notice}</p>}

    {result && <>
      <section className="card">
        <h2>3. 검증 결과</h2>
        <p>정상 {result.validation.validRows}건 · 오류 {result.validation.errorRows}건 · 중복 의심 {result.validation.suspectedDuplicates}건</p>
        {result.validation.errors.length > 0 && <ul className="error-list">
          {result.validation.errors.map((rowError, index) =>
            <li key={index}>{rowError.rowNumber}행 [{rowError.field}]: {rowError.message}</li>)}
        </ul>}
        <p className="muted">오류 행이 있으면 전체 저장을 막습니다. 중복 의심 행은 자동 삭제하지 않습니다. 파일 순서대로 잔액을 반영하므로 수입이 지출보다 먼저 있어야 합니다. 같은 파일을 다시 가져오면 이미 저장된 행은 건너뜁니다. 파일을 수정·재정렬하면 별도 업로드로 취급합니다.</p>
        <label className="check">
          <input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} />
          미리보기와 중복 의심 항목을 확인했고 내 거래로 저장하겠습니다.
        </label>
        <button className="primary" disabled={busy || !confirmed || result.validation.errorRows > 0 || !result.validation.validRows}
          onClick={() => void run(async () => {
            const saved = await upload<{ created: number; alreadyImported: number }>('import');
            setNotice(`${saved.created}건 저장, 이미 가져온 ${saved.alreadyImported}건 건너뜀. 대시보드에서 확인해 주세요.`);
            setConfirmed(false);
          })}>거래 데이터에 저장</button>
      </section>
      {result.analysis && <>
        <h2>임시 분석 · 오류 없는 행 기준</h2>
        <p className="muted">아래 잔액은 업로드 파일 내 수입 − 지출입니다. 계정 잔액이 아닙니다.</p>
        <Stats data={result.analysis} />
        <section className="card">
          <h2>CSV AI 해석</h2>
          <p className="muted">정상 행의 집계(카테고리·사용처 포함)가 Google Gemini에 전달됩니다. 생성 요청 시 동의한 것으로 간주합니다.</p>
          <button disabled={busy || result.validation.errorRows > 0}
            onClick={() => void run(async () => setReport(await upload<string>('ai-report')))}>AI 리포트 생성</button>
          {report && <div className="report">{report}</div>}
        </section>
      </>}
    </>}
  </>;
}
