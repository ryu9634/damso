import { useEffect, useState } from 'react';
import { customerApi, questionApi } from '../../api/client';
import { useEvent } from '../../hooks/useEvent';
import type { Customer, CustomerStatus, Gender, ValueQuestion } from '../../../shared/types';
import { PageHeader, Button } from '../../layout/ui';

export default function CustomersPage() {
  const { event } = useEvent();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [questions, setQuestions] = useState<ValueQuestion[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [detail, setDetail] = useState<Customer | null>(null);
  const [edit, setEdit] = useState<Customer | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'MALE' | 'FEMALE'>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | CustomerStatus>('ALL');
  const [search, setSearch] = useState('');

  const refresh = async () => {
    if (!event) return;
    const [c, q] = await Promise.all([
      customerApi.list(event.id),
      questionApi.list(),
    ]);
    setCustomers(c);
    setQuestions(q);
  };

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [event?.id]);

  const handleDelete = async (id: number) => {
    if (!confirm('정말 삭제하시겠습니까?')) return;
    await customerApi.delete(id);
    refresh();
  };

  const toggleStatus = async (c: Customer) => {
    const next: CustomerStatus = c.status === 'ACTIVE' ? 'LEFT' : 'ACTIVE';
    if (next === 'LEFT' && !confirm(`${c.nickname}님을 '퇴장' 상태로 변경할까요?\n진행 중인 좌석 배치에서도 자동 제거됩니다.`)) return;
    await customerApi.setStatus(c.id, next);
    refresh();
  };

  const filtered = customers.filter((c) => {
    if (filter !== 'ALL' && c.gender !== filter) return false;
    if (statusFilter !== 'ALL' && c.status !== statusFilter) return false;
    if (search.trim()) {
      const s = search.toLowerCase();
      return c.nickname.toLowerCase().includes(s)
        || c.realName.toLowerCase().includes(s)
        || c.phone.includes(s);
    }
    return true;
  });

  const activeCount = customers.filter((c) => c.status === 'ACTIVE').length;
  const leftCount = customers.length - activeCount;

  return (
    <div className="space-y-6">
      <PageHeader
        title="손님 입력 데이터"
        description={<>
          총 {customers.length}명 · 입장 <span className="text-emerald-700 font-medium">{activeCount}</span> / 퇴장 <span className="text-stone-700 font-medium">{leftCount}</span>
          <span className="ml-2 text-xs">(연락처는 외부 노출 금지)</span>
        </>}
        actions={<Button variant="primary" onClick={() => setShowCreate(true)}>+ 손님 수동 추가</Button>}
      />

      <div className="flex items-center gap-3 text-sm flex-wrap">
        <div className="flex gap-1">
          {(['ALL', 'MALE', 'FEMALE'] as const).map((g) => (
            <button
              key={g}
              onClick={() => setFilter(g)}
              className={`px-3 py-1.5 rounded-md border ${
                filter === g
                  ? 'bg-stone-900 text-white border-stone-900'
                  : 'bg-white border-stone-300 hover:bg-stone-100'
              }`}
            >
              {g === 'ALL' ? '전체' : g === 'MALE' ? '남성만' : '여성만'}
            </button>
          ))}
        </div>
        <div className="flex gap-1">
          {(['ALL', 'ACTIVE', 'LEFT'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1.5 rounded-md border ${
                statusFilter === s
                  ? 'bg-stone-900 text-white border-stone-900'
                  : 'bg-white border-stone-300 hover:bg-stone-100'
              }`}
            >
              {s === 'ALL' ? '입퇴장 전체' : s === 'ACTIVE' ? '입장 중' : '퇴장'}
            </button>
          ))}
        </div>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="닉네임/본명/연락처/QR 검색"
          className="flex-1 max-w-sm border border-stone-300 rounded px-3 py-1.5"
        />
        <div className="ml-auto text-xs text-stone-500">{filtered.length}명 표시</div>
      </div>

      <div className="bg-white rounded-lg border border-stone-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-stone-100 text-stone-600 text-xs">
            <tr>
              <Th>#</Th>
              <Th>상태</Th>
              <Th>닉네임</Th>
              <Th>성별</Th>
              <Th>MBTI</Th>
              <Th>본명</Th>
              <Th>연락처</Th>
              <Th>자리</Th>
              <Th>입장 시각</Th>
              <Th></Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100">
            {filtered.length === 0 && (
              <tr><td colSpan={10} className="p-6 text-center text-stone-400">표시할 손님이 없습니다.</td></tr>
            )}
            {filtered.map((c, i) => (
              <tr key={c.id} className={`hover:bg-stone-50 ${c.status === 'LEFT' ? 'opacity-50' : ''}`}>
                <Td>{i + 1}</Td>
                <Td>
                  <button
                    onClick={() => toggleStatus(c)}
                    className={`px-2 py-1 rounded-md text-xs font-semibold transition ${
                      c.status === 'ACTIVE'
                        ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                        : 'bg-stone-200 text-stone-600 hover:bg-stone-300'
                    }`}
                    title="클릭하여 상태 변경"
                  >
                    {c.status === 'ACTIVE' ? '● 입장' : '○ 퇴장'}
                  </button>
                </Td>
                <Td className="font-medium">
                  <button onClick={() => setDetail(c)} className="text-damso-600 hover:underline">
                    {c.nickname}
                  </button>
                </Td>
                <Td>
                  <span className={`px-2 py-0.5 rounded text-xs ${c.gender === 'MALE' ? 'bg-blue-100 text-blue-700' : 'bg-pink-100 text-pink-700'}`}>
                    {c.gender === 'MALE' ? '남' : '여'}
                  </span>
                </Td>
                <Td>{c.mbti}</Td>
                <Td className="text-stone-600">{c.realName}</Td>
                <Td className="text-stone-600">{c.phone}</Td>
                <Td className="font-bold text-stone-900">
                  {c.seatNumber != null ? `${c.seatNumber}번` : '—'}
                </Td>
                <Td className="text-stone-500 text-xs">{new Date(c.createdAt).toLocaleTimeString('ko-KR')}</Td>
                <Td className="space-x-2 whitespace-nowrap">
                  <button onClick={() => setEdit(c)} className="text-xs text-stone-700 hover:underline">수정</button>
                  <button onClick={() => handleDelete(c.id)} className="text-xs text-red-600 hover:underline">삭제</button>
                </Td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreate && event && (
        <CreateModal eventId={event.id} questions={questions} onClose={() => setShowCreate(false)} onCreated={() => { setShowCreate(false); refresh(); }} />
      )}
      {edit && (
        <EditModal customer={edit} questions={questions} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); refresh(); }} />
      )}
      {detail && <DetailModal customer={detail} questions={questions} onClose={() => setDetail(null)} />}
    </div>
  );
}

function Th({ children }: { children?: React.ReactNode }) {
  return <th className="text-left px-3 py-2 font-medium">{children}</th>;
}
function Td({ children, className = '' }: { children?: React.ReactNode; className?: string }) {
  return <td className={`px-3 py-2 ${className}`}>{children}</td>;
}

function CreateModal({
  eventId, questions, onClose, onCreated,
}: { eventId: number; questions: ValueQuestion[]; onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState({
    nickname: '', realName: '', phone: '',
    gender: 'MALE' as Gender, mbti: 'ENFP', seatNumber: 1,
  });
  const [answers, setAnswers] = useState<string[]>(() => questions.map(() => '0'));
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    if (questions.length === 0) {
      setErr('설문 문항이 없습니다. 먼저 설문 문항을 등록해주세요.');
      return;
    }
    setSubmitting(true); setErr(null);
    try {
      await customerApi.create({
        eventId,
        ...form,
        mbti: form.mbti.toUpperCase(),
        valuesAnswers: answers.join(''),
      });
      onCreated();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div className="px-5 py-4 border-b border-stone-200 flex items-center justify-between">
          <h2 className="font-semibold">손님 수동 추가</h2>
          <button onClick={onClose} className="text-stone-500 hover:text-stone-900">✕</button>
        </div>
        <div className="p-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Field label="닉네임" value={form.nickname} onChange={(v) => setForm({ ...form, nickname: v })} />
            <Field label="본명" value={form.realName} onChange={(v) => setForm({ ...form, realName: v })} />
            <Field label="연락처" value={form.phone} onChange={(v) => setForm({ ...form, phone: v })} />
            <div>
              <label className="text-xs font-medium text-stone-600">성별</label>
              <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value as Gender })}
                className="mt-1 w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
                <option value="MALE">남성</option>
                <option value="FEMALE">여성</option>
              </select>
            </div>
            <Field label="MBTI (예: ENFP)" value={form.mbti} onChange={(v) => setForm({ ...form, mbti: v })} />
            <div>
              <label className="text-xs font-medium text-stone-600">자리 번호</label>
              <select value={form.seatNumber}
                onChange={(e) => setForm({ ...form, seatNumber: Number(e.target.value) })}
                className="mt-1 w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => <option key={n} value={n}>{n}번</option>)}
              </select>
            </div>
          </div>

          <QuestionAnswersEditor questions={questions} answers={answers} onChange={setAnswers} />

          {err && <div className="text-sm text-red-600">{err}</div>}
        </div>
        <div className="px-5 py-3 border-t border-stone-200 flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm border border-stone-300 rounded">취소</button>
          <button onClick={submit} disabled={submitting}
            className="px-4 py-1.5 text-sm bg-damso-500 text-white rounded hover:bg-damso-600 disabled:opacity-50">
            {submitting ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}

function QuestionAnswersEditor({
  questions, answers, onChange,
}: { questions: ValueQuestion[]; answers: string[]; onChange: (next: string[]) => void }) {
  return (
    <div>
      <div className="text-sm font-medium text-stone-700 mb-2">가치관 {questions.length}문항</div>
      {questions.length === 0 ? (
        <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded p-3">
          등록된 설문 문항이 없습니다. "설문 문항" 메뉴에서 먼저 등록해주세요.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2">
          {questions.map((q, i) => (
            <div key={q.id} className="flex items-center justify-between border border-stone-200 rounded px-3 py-2 text-xs">
              <span className="font-medium text-stone-700">{i + 1}. {q.label}</span>
              <div className="flex gap-1">
                {[q.optionA, q.optionB].map((opt, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => {
                      const next = [...answers];
                      while (next.length < questions.length) next.push('0');
                      next[i] = String(idx);
                      onChange(next);
                    }}
                    className={`px-2 py-1 rounded ${
                      answers[i] === String(idx)
                        ? 'bg-damso-500 text-white'
                        : 'bg-stone-100 text-stone-700 hover:bg-stone-200'
                    }`}
                  >
                    {opt}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div>
      <label className="text-xs font-medium text-stone-600">{label}</label>
      <input value={value} onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
    </div>
  );
}

function EditModal({ customer, questions, onClose, onSaved }: {
  customer: Customer;
  questions: ValueQuestion[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState({
    nickname: customer.nickname,
    realName: customer.realName,
    phone: customer.phone,
    gender: customer.gender,
    mbti: customer.mbti,
    seatNumber: customer.seatNumber ?? 1,
  });
  const [answers, setAnswers] = useState<string[]>(() => {
    const base = customer.valuesAnswers.split('');
    const out: string[] = [];
    for (let i = 0; i < questions.length; i++) out.push(base[i] ?? '0');
    return out;
  });
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    setSubmitting(true); setErr(null);
    try {
      await customerApi.update(customer.id, {
        ...form,
        mbti: form.mbti.toUpperCase(),
        valuesAnswers: answers.join(''),
      });
      onSaved();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div className="px-5 py-4 border-b border-stone-200 flex items-center justify-between">
          <h2 className="font-semibold">손님 정보 수정 — {customer.nickname}</h2>
          <button onClick={onClose} className="text-stone-500 hover:text-stone-900">✕</button>
        </div>
        <div className="p-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Field label="닉네임" value={form.nickname} onChange={(v) => setForm({ ...form, nickname: v })} />
            <Field label="본명" value={form.realName} onChange={(v) => setForm({ ...form, realName: v })} />
            <Field label="연락처" value={form.phone} onChange={(v) => setForm({ ...form, phone: v })} />
            <div>
              <label className="text-xs font-medium text-stone-600">성별</label>
              <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value as Gender })}
                className="mt-1 w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
                <option value="MALE">남성</option>
                <option value="FEMALE">여성</option>
              </select>
            </div>
            <Field label="MBTI" value={form.mbti} onChange={(v) => setForm({ ...form, mbti: v })} />
            <div>
              <label className="text-xs font-medium text-stone-600">자리 번호</label>
              <select value={form.seatNumber}
                onChange={(e) => setForm({ ...form, seatNumber: Number(e.target.value) })}
                className="mt-1 w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => <option key={n} value={n}>{n}번</option>)}
              </select>
            </div>
          </div>

          <QuestionAnswersEditor questions={questions} answers={answers} onChange={setAnswers} />

          {err && <div className="text-sm text-red-600">{err}</div>}
        </div>
        <div className="px-5 py-3 border-t border-stone-200 flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm border border-stone-300 rounded">취소</button>
          <button onClick={submit} disabled={submitting}
            className="px-4 py-1.5 text-sm bg-damso-500 text-white rounded hover:bg-damso-600 disabled:opacity-50">
            {submitting ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}

function DetailModal({ customer, questions, onClose }: { customer: Customer; questions: ValueQuestion[]; onClose: () => void }) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-lg w-full">
        <div className="px-5 py-4 border-b border-stone-200 flex items-center justify-between">
          <h2 className="font-semibold">{customer.nickname} 상세</h2>
          <button onClick={onClose} className="text-stone-500 hover:text-stone-900">✕</button>
        </div>
        <div className="p-5 space-y-2 text-sm">
          <Row label="성별" value={customer.gender === 'MALE' ? '남성' : '여성'} />
          <Row label="MBTI" value={customer.mbti} />
          <Row label="자리 번호" value={customer.seatNumber != null ? `${customer.seatNumber}번` : '미지정'} />
          <Row label="본명" value={customer.realName} />
          <Row label="연락처" value={customer.phone} />
          <div className="pt-3 border-t border-stone-200">
            <div className="text-xs font-medium text-stone-600 mb-2">가치관 답변</div>
            <div className="space-y-1">
              {questions.length === 0 && <div className="text-xs text-stone-400">등록된 설문 문항이 없습니다.</div>}
              {questions.map((q, i) => {
                const ch = customer.valuesAnswers[i];
                const idx = ch === '1' ? 1 : ch === '0' ? 0 : -1;
                const ans = idx === 0 ? q.optionA : idx === 1 ? q.optionB : '응답 없음';
                return (
                  <div key={q.id} className="flex justify-between text-xs">
                    <span className="text-stone-600">{i + 1}. {q.label}</span>
                    <span className={`font-medium ${idx < 0 ? 'text-stone-400' : 'text-stone-900'}`}>{ans}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-stone-500">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
