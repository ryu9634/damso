import { useEffect, useState } from 'react';
import { questionApi } from '../../api/client';
import type { ValueQuestion } from '../../../shared/types';
import { PageHeader, Button } from '../../layout/ui';

export default function QuestionsPage() {
  const [questions, setQuestions] = useState<ValueQuestion[]>([]);
  const [editing, setEditing] = useState<number | null>(null);
  const [draft, setDraft] = useState({ label: '', optionA: '', optionB: '' });
  const [adding, setAdding] = useState(false);
  const [newDraft, setNewDraft] = useState({ label: '', optionA: '', optionB: '' });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refresh = async () => {
    setQuestions(await questionApi.list());
  };

  useEffect(() => { refresh(); }, []);

  const startEdit = (q: ValueQuestion) => {
    setEditing(q.id);
    setDraft({ label: q.label, optionA: q.optionA, optionB: q.optionB });
  };

  const saveEdit = async () => {
    if (editing == null) return;
    setBusy(true); setErr(null);
    try {
      await questionApi.update(editing, draft);
      setEditing(null);
      await refresh();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setBusy(false);
    }
  };

  const saveNew = async () => {
    if (!newDraft.label.trim() || !newDraft.optionA.trim() || !newDraft.optionB.trim()) {
      setErr('모든 칸을 입력하세요.');
      return;
    }
    setBusy(true); setErr(null);
    try {
      await questionApi.create(newDraft);
      setNewDraft({ label: '', optionA: '', optionB: '' });
      setAdding(false);
      await refresh();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '추가 실패');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: number) => {
    if (!confirm('이 문항을 삭제하시겠습니까? 기존 손님의 답변에서 해당 항목은 무시됩니다.')) return;
    await questionApi.delete(id);
    refresh();
  };

  const move = async (id: number, direction: 'up' | 'down') => {
    setQuestions(await questionApi.move(id, direction));
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="설문 문항 관리"
        description={<>가치관 매칭 점수에 사용되는 2지선다 문항 · 총 {questions.length}문항</>}
        actions={<Button variant="primary" onClick={() => setAdding((v) => !v)}>{adding ? '추가 닫기' : '+ 문항 추가'}</Button>}
      />

      <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-800">
        ⚠️ 문항을 변경/추가/삭제하면 기존 손님의 답변과 매칭 점수가 달라질 수 있습니다.
        이벤트 시작 전에 미리 확정해두는 것을 권장합니다.
      </div>

      {adding && (
        <div className="bg-white rounded-lg border-2 border-damso-300 p-4 space-y-3">
          <div className="text-sm font-semibold text-damso-700">새 문항 추가</div>
          <input
            value={newDraft.label}
            onChange={(e) => setNewDraft({ ...newDraft, label: e.target.value })}
            placeholder="문항 제목 (예: 갈등 해결 방식)"
            className="w-full border border-stone-300 rounded px-3 py-2 text-sm"
          />
          <div className="grid grid-cols-2 gap-2">
            <input
              value={newDraft.optionA}
              onChange={(e) => setNewDraft({ ...newDraft, optionA: e.target.value })}
              placeholder="선택지 A"
              className="border border-stone-300 rounded px-3 py-2 text-sm"
            />
            <input
              value={newDraft.optionB}
              onChange={(e) => setNewDraft({ ...newDraft, optionB: e.target.value })}
              placeholder="선택지 B"
              className="border border-stone-300 rounded px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => { setAdding(false); setNewDraft({ label: '', optionA: '', optionB: '' }); }}
              className="px-3 py-1.5 text-sm border border-stone-300 rounded">취소</button>
            <button onClick={saveNew} disabled={busy}
              className="px-3 py-1.5 text-sm bg-damso-500 text-white rounded hover:bg-damso-600 disabled:opacity-50">
              {busy ? '추가 중...' : '추가'}
            </button>
          </div>
        </div>
      )}

      {err && <div className="text-sm text-red-600">{err}</div>}

      <div className="bg-white rounded-lg border border-stone-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-stone-100 text-stone-600 text-xs">
            <tr>
              <th className="text-left px-3 py-2 w-12">#</th>
              <th className="text-left px-3 py-2">문항</th>
              <th className="text-left px-3 py-2">선택지 A</th>
              <th className="text-left px-3 py-2">선택지 B</th>
              <th className="text-right px-3 py-2 w-44">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100">
            {questions.length === 0 && (
              <tr><td colSpan={5} className="p-8 text-center text-stone-400">문항이 없습니다.</td></tr>
            )}
            {questions.map((q, i) => (
              <tr key={q.id} className="hover:bg-stone-50">
                <td className="px-3 py-2 text-stone-500">{i + 1}</td>
                {editing === q.id ? (
                  <>
                    <td className="px-3 py-2">
                      <input value={draft.label} onChange={(e) => setDraft({ ...draft, label: e.target.value })}
                        className="w-full border border-stone-300 rounded px-2 py-1 text-sm" />
                    </td>
                    <td className="px-3 py-2">
                      <input value={draft.optionA} onChange={(e) => setDraft({ ...draft, optionA: e.target.value })}
                        className="w-full border border-stone-300 rounded px-2 py-1 text-sm" />
                    </td>
                    <td className="px-3 py-2">
                      <input value={draft.optionB} onChange={(e) => setDraft({ ...draft, optionB: e.target.value })}
                        className="w-full border border-stone-300 rounded px-2 py-1 text-sm" />
                    </td>
                    <td className="px-3 py-2 text-right space-x-1 whitespace-nowrap">
                      <button onClick={saveEdit} disabled={busy}
                        className="text-xs bg-damso-500 text-white px-2 py-1 rounded hover:bg-damso-600 disabled:opacity-50">
                        저장
                      </button>
                      <button onClick={() => setEditing(null)}
                        className="text-xs border border-stone-300 px-2 py-1 rounded hover:bg-stone-100">
                        취소
                      </button>
                    </td>
                  </>
                ) : (
                  <>
                    <td className="px-3 py-2 font-medium">{q.label}</td>
                    <td className="px-3 py-2 text-stone-700">{q.optionA}</td>
                    <td className="px-3 py-2 text-stone-700">{q.optionB}</td>
                    <td className="px-3 py-2 text-right space-x-1 whitespace-nowrap">
                      <button onClick={() => move(q.id, 'up')} disabled={i === 0}
                        className="text-xs px-1.5 py-1 border border-stone-200 rounded hover:bg-stone-100 disabled:opacity-30">↑</button>
                      <button onClick={() => move(q.id, 'down')} disabled={i === questions.length - 1}
                        className="text-xs px-1.5 py-1 border border-stone-200 rounded hover:bg-stone-100 disabled:opacity-30">↓</button>
                      <button onClick={() => startEdit(q)}
                        className="text-xs text-stone-700 hover:underline">수정</button>
                      <button onClick={() => remove(q.id)}
                        className="text-xs text-red-600 hover:underline">삭제</button>
                    </td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
