import { useEffect, useState } from 'react';
import { datingQuestionApi, type DatingQuestionRequest } from '../../api/client';
import type { DatingQuestion, DatingQuestionType } from '../../../shared/types';
import { PageHeader, Card, Button, Empty } from '../../layout/ui';

const TYPE_LABEL: Record<DatingQuestionType, string> = {
  TEXT: '단답',
  TEXTAREA: '장문',
  NUMBER: '숫자',
  SELECT: '선택지',
};

const TYPE_ORDER: DatingQuestionType[] = ['TEXT', 'TEXTAREA', 'NUMBER', 'SELECT'];

interface Draft {
  label: string;
  type: DatingQuestionType;
  options: string; // 줄바꿈 구분
  required: boolean;
}

const EMPTY_DRAFT: Draft = { label: '', type: 'TEXT', options: '', required: true };

function toDraft(q: DatingQuestion): Draft {
  return { label: q.label, type: q.type, options: q.options.join('\n'), required: q.required };
}

function toRequest(d: Draft): DatingQuestionRequest {
  return {
    label: d.label.trim(),
    type: d.type,
    options: d.type === 'SELECT' ? d.options : null,
    required: d.required,
  };
}

/** 추가/수정 공용 입력 폼. */
function QuestionForm({
  draft, setDraft, onSave, onCancel, busy, saveLabel,
}: {
  draft: Draft;
  setDraft: (d: Draft) => void;
  onSave: () => void;
  onCancel: () => void;
  busy: boolean;
  saveLabel: string;
}) {
  return (
    <div className="space-y-3">
      <input
        value={draft.label}
        onChange={(e) => setDraft({ ...draft, label: e.target.value })}
        placeholder="질문 제목 (예: 키 (cm), 성격)"
        className="w-full border border-stone-300 rounded px-3 py-2 text-sm"
      />
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-stone-500">유형</span>
          <div className="flex gap-1">
            {TYPE_ORDER.map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setDraft({ ...draft, type: t })}
                className={`text-xs px-2.5 py-1.5 rounded border ${
                  draft.type === t
                    ? 'bg-damso-500 text-white border-damso-500'
                    : 'bg-white text-stone-600 border-stone-300'
                }`}
              >
                {TYPE_LABEL[t]}
              </button>
            ))}
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm text-stone-600 cursor-pointer select-none">
          <input
            type="checkbox"
            className="w-4 h-4 accent-damso-500"
            checked={draft.required}
            onChange={(e) => setDraft({ ...draft, required: e.target.checked })}
          />
          필수 입력
        </label>
      </div>

      {draft.type === 'SELECT' && (
        <div>
          <div className="text-xs font-medium text-stone-500 mb-1">
            선택지 (한 줄에 하나씩)
          </div>
          <textarea
            value={draft.options}
            onChange={(e) => setDraft({ ...draft, options: e.target.value })}
            rows={4}
            placeholder={'예:\nINTJ\nINTP\nENTJ'}
            className="w-full border border-stone-300 rounded px-3 py-2 text-sm resize-y font-mono"
          />
        </div>
      )}

      <div className="flex justify-end gap-2">
        <button
          onClick={onCancel}
          className="px-3 py-1.5 text-sm border border-stone-300 rounded hover:bg-stone-100"
        >
          취소
        </button>
        <button
          onClick={onSave}
          disabled={busy}
          className="px-3 py-1.5 text-sm bg-damso-500 text-white rounded hover:bg-damso-600 disabled:opacity-50"
        >
          {busy ? '저장 중...' : saveLabel}
        </button>
      </div>
    </div>
  );
}

export default function DatingQuestionsPage() {
  const [questions, setQuestions] = useState<DatingQuestion[]>([]);
  const [adding, setAdding] = useState(false);
  const [newDraft, setNewDraft] = useState<Draft>(EMPTY_DRAFT);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<Draft>(EMPTY_DRAFT);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refresh = async () => {
    setQuestions(await datingQuestionApi.list());
  };

  useEffect(() => { refresh(); }, []);

  const validate = (d: Draft): string | null => {
    if (!d.label.trim()) return '질문 제목을 입력하세요.';
    if (d.type === 'SELECT' && !d.options.split('\n').some((s) => s.trim())) {
      return '선택지 유형은 선택지를 하나 이상 입력해야 합니다.';
    }
    return null;
  };

  const saveNew = async () => {
    const v = validate(newDraft);
    if (v) { setErr(v); return; }
    setBusy(true); setErr(null);
    try {
      await datingQuestionApi.create(toRequest(newDraft));
      setNewDraft(EMPTY_DRAFT);
      setAdding(false);
      await refresh();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '추가 실패');
    } finally {
      setBusy(false);
    }
  };

  const startEdit = (q: DatingQuestion) => {
    setEditingId(q.id);
    setEditDraft(toDraft(q));
    setErr(null);
  };

  const saveEdit = async () => {
    if (editingId == null) return;
    const v = validate(editDraft);
    if (v) { setErr(v); return; }
    setBusy(true); setErr(null);
    try {
      await datingQuestionApi.update(editingId, toRequest(editDraft));
      setEditingId(null);
      await refresh();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (q: DatingQuestion) => {
    if (!confirm(`'${q.label}' 질문을 삭제할까요? 기존 손님의 해당 답변도 함께 삭제됩니다.`)) return;
    await datingQuestionApi.delete(q.id);
    await refresh();
  };

  const move = async (id: number, direction: 'up' | 'down') => {
    setQuestions(await datingQuestionApi.move(id, direction));
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="소개팅 질문 관리"
        description={<>손님 프로필 등록 화면에 표시되는 상세 질문 · 총 {questions.length}개</>}
        actions={
          <Button variant="primary" onClick={() => { setAdding((v) => !v); setErr(null); }}>
            {adding ? '추가 닫기' : '+ 질문 추가'}
          </Button>
        }
      />

      <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-800">
        ⚠️ 이름·연락처·출생년도·성별·사진은 고정 항목입니다. 여기서는 그 외 상세 질문을
        자유롭게 추가·수정·삭제·정렬할 수 있어요. 손님 등록 화면이 이 순서대로 표시됩니다.
      </div>

      {adding && (
        <Card title="새 질문 추가" className="border-2 border-damso-300">
          <QuestionForm
            draft={newDraft}
            setDraft={setNewDraft}
            onSave={saveNew}
            onCancel={() => { setAdding(false); setNewDraft(EMPTY_DRAFT); setErr(null); }}
            busy={busy}
            saveLabel="추가"
          />
        </Card>
      )}

      {err && <div className="text-sm text-red-600">{err}</div>}

      {questions.length === 0 && !adding ? (
        <Card><Empty>질문이 없습니다. ‘질문 추가’로 만들어 주세요.</Empty></Card>
      ) : (
        <div className="space-y-3">
          {questions.map((q, i) => (
            <Card key={q.id}>
              {editingId === q.id ? (
                <QuestionForm
                  draft={editDraft}
                  setDraft={setEditDraft}
                  onSave={saveEdit}
                  onCancel={() => setEditingId(null)}
                  busy={busy}
                  saveLabel="저장"
                />
              ) : (
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-stone-400 text-xs">{i + 1}</span>
                      <span className="font-semibold text-stone-900">{q.label}</span>
                      <span className="inline-flex items-center rounded-full bg-stone-100 text-stone-600 px-2 py-0.5 text-xs font-medium">
                        {TYPE_LABEL[q.type]}
                      </span>
                      {q.required && (
                        <span className="inline-flex items-center rounded-full bg-damso-50 text-damso-700 px-2 py-0.5 text-xs font-medium">
                          필수
                        </span>
                      )}
                    </div>
                    {q.type === 'SELECT' && q.options.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {q.options.map((opt) => (
                          <span key={opt} className="text-xs text-stone-600 bg-stone-50 border border-stone-200 rounded px-2 py-0.5">
                            {opt}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-1 shrink-0 whitespace-nowrap">
                    <button onClick={() => move(q.id, 'up')} disabled={i === 0}
                      className="text-xs px-1.5 py-1 border border-stone-200 rounded hover:bg-stone-100 disabled:opacity-30">↑</button>
                    <button onClick={() => move(q.id, 'down')} disabled={i === questions.length - 1}
                      className="text-xs px-1.5 py-1 border border-stone-200 rounded hover:bg-stone-100 disabled:opacity-30">↓</button>
                    <button onClick={() => startEdit(q)}
                      className="text-xs text-stone-700 hover:underline px-1">수정</button>
                    <button onClick={() => remove(q)}
                      className="text-xs text-red-600 hover:underline px-1">삭제</button>
                  </div>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}