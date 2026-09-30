import { useEffect, useMemo, useState } from 'react';
import { customerApi, questionApi, seatApi } from '../../api/client';
import { useEvent } from '../../hooks/useEvent';
import type { Customer, SeatAssignment, ValueQuestion } from '../../../shared/types';
import { PageHeader } from '../../layout/ui';

/** 자동 배치 버튼에 붙는 라운드별 이름. */
const AUTO_LABEL: Record<number, string> = {
  1: '손님 입력 자리',
  2: '점수 높은 순',
  3: '점수 낮은 순',
  4: '지목 우선순위',
};

/** 라운드별로 무엇을 기준으로 앉히는지. */
const ROUND_HINT: Record<number, string> = {
  1: '손님 입력 자리 번호 기준 자동 배치',
  2: '매칭 점수 최대화 · 동점 랜덤',
  3: '매칭 점수 최소화 — 가장 안 맞게 나온 상대와 (여성 자리 고정)',
  4: '쌍방 1순위 > 2순위/교차 > 단방 지목 > 미지목',
};

export default function SeatsPage() {
  const { event } = useEvent();
  const [round, setRound] = useState(1);
  const [seats, setSeats] = useState<SeatAssignment[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [questions, setQuestions] = useState<ValueQuestion[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [picker, setPicker] = useState<{ seat: number; slot: number } | null>(null);

  const refresh = async () => {
    if (!event) return;
    const [s, c] = await Promise.all([
      seatApi.list(event.id, round),
      customerApi.list(event.id),
    ]);
    setSeats(s);
    setCustomers(c);
  };

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [event?.id, round]);
  useEffect(() => { questionApi.list().then(setQuestions).catch(() => setQuestions([])); }, []);

  const runAuto = async () => {
    if (!event) return;
    setBusy(true); setErr(null);
    try {
      if (round === 1) await seatApi.assignRound1(event.id);
      else if (round === 2) await seatApi.assignRound2(event.id);
      else if (round === 3) await seatApi.assignRound3(event.id);
      else if (round === 4) await seatApi.assignRound4(event.id);
      await refresh();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '배치 실행 실패');
    } finally {
      setBusy(false);
    }
  };

  const clearRound = async () => {
    if (!event) return;
    if (!confirm(`${round}차 자리 배치를 모두 비울까요?`)) return;
    await seatApi.clearRound(event.id, round);
    refresh();
  };

  const removeFromSeat = async (customerId: number) => {
    if (!event) return;
    await seatApi.remove(event.id, round, customerId);
    refresh();
  };

  const assigned = useMemo(() => new Set(seats.map((s) => s.customerId)), [seats]);
  const unassigned = customers.filter((c) => !assigned.has(c.id) && c.status === 'ACTIVE');

  // 이 차수에 배치된 손님 + 가치관 답변 (자리번호/슬롯 순)
  const assignedCustomers = useMemo(() => {
    const byId = new Map(customers.map((c) => [c.id, c]));
    return seats
      .map((s) => {
        const c = byId.get(s.customerId);
        return c ? { seat: s, customer: c } : null;
      })
      .filter((x): x is { seat: SeatAssignment; customer: Customer } => x != null)
      .sort((a, b) => a.seat.seatNumber - b.seat.seatNumber || a.seat.slotIndex - b.seat.slotIndex);
  }, [seats, customers]);

  // seatMap: "seat-slot" → SeatAssignment (셀당 1명)
  const seatMap = new Map<string, SeatAssignment>();
  seats.forEach((s) => seatMap.set(`${s.seatNumber}-${s.slotIndex}`, s));

  return (
    <div className="space-y-6">
      <PageHeader title="자리 배치" description="ㄷ자 18셀 · 셀당 1명 · 수동 배치" />

      <div className="flex items-center gap-2">
        {[1, 2, 3, 4].map((r) => (
          <button
            key={r}
            onClick={() => setRound(r)}
            className={`px-4 py-2 text-sm rounded-md border ${
              round === r
                ? 'bg-damso-500 text-white border-damso-500'
                : 'bg-white border-stone-300 hover:bg-stone-100'
            }`}
          >
            {r}차 자리
          </button>
        ))}
      </div>

      <div className="bg-white rounded-lg border border-stone-200 p-4 flex flex-wrap items-center gap-3">
        <button
          onClick={runAuto}
          disabled={busy}
          className="px-4 py-2 text-sm bg-damso-500 text-white rounded-md hover:bg-damso-600 disabled:opacity-50"
        >
          {busy ? '배치 중...' : `${AUTO_LABEL[round] ?? ''} 자동 배치`}
        </button>
        <button
          onClick={clearRound}
          className="px-3 py-2 text-sm bg-white border border-stone-300 rounded-md hover:bg-stone-100"
        >
          {round}차 초기화
        </button>
        {err && <span className="text-sm text-red-600">{err}</span>}
        <div className="ml-auto text-xs text-stone-500">
          {ROUND_HINT[round]}
        </div>
      </div>

      <div className="bg-stone-100 rounded-lg border border-stone-300 p-5">
        <div
          className="grid grid-cols-9 grid-rows-7 gap-2 max-w-5xl mx-auto"
          style={{ gridAutoFlow: 'row dense' }}
        >
          {/* Row 1: empty + 6slot0 6slot1 5slot0 5slot1 4slot0 4slot1 3slot0 + empty */}
          <div />
          <Cell seat={6} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 6, slot: 0 })} onRemove={removeFromSeat} />
          <Cell seat={6} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 6, slot: 1 })} onRemove={removeFromSeat} />
          <Cell seat={5} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 5, slot: 0 })} onRemove={removeFromSeat} />
          <Cell seat={5} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 5, slot: 1 })} onRemove={removeFromSeat} />
          <Cell seat={4} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 4, slot: 0 })} onRemove={removeFromSeat} />
          <Cell seat={4} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 4, slot: 1 })} onRemove={removeFromSeat} />
          <Cell seat={3} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 3, slot: 0 })} onRemove={removeFromSeat} />
          <div />

          {/* Row 2: 7slot0 + 주인장 + 3slot1 */}
          <Cell seat={7} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 7, slot: 0 })} onRemove={removeFromSeat} />
          <div className="col-span-7 row-span-6 bg-amber-50 border-2 border-amber-300 rounded-lg flex flex-col items-center justify-center text-center">
            <div className="text-4xl mb-2">🍷</div>
            <div className="text-lg font-bold text-amber-800">주인장 위치</div>
            <div className="text-xs text-amber-700 mt-1">바 카운터 안쪽</div>
          </div>
          <Cell seat={3} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 3, slot: 1 })} onRemove={removeFromSeat} />

          {/* Row 3: 7slot1 + 2slot0 */}
          <Cell seat={7} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 7, slot: 1 })} onRemove={removeFromSeat} />
          <Cell seat={2} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 2, slot: 0 })} onRemove={removeFromSeat} />

          {/* Row 4: 8slot0 + 2slot1 */}
          <Cell seat={8} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 8, slot: 0 })} onRemove={removeFromSeat} />
          <Cell seat={2} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 2, slot: 1 })} onRemove={removeFromSeat} />

          {/* Row 5: 8slot1 + 1slot0 */}
          <Cell seat={8} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 8, slot: 1 })} onRemove={removeFromSeat} />
          <Cell seat={1} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 1, slot: 0 })} onRemove={removeFromSeat} />

          {/* Row 6: 9slot0 + 1slot1 */}
          <Cell seat={9} slot={0} seatMap={seatMap} onAdd={() => setPicker({ seat: 9, slot: 0 })} onRemove={removeFromSeat} />
          <Cell seat={1} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 1, slot: 1 })} onRemove={removeFromSeat} />

          {/* Row 7: 9slot1 */}
          <Cell seat={9} slot={1} seatMap={seatMap} onAdd={() => setPicker({ seat: 9, slot: 1 })} onRemove={removeFromSeat} />
        </div>
        <div className="flex items-center justify-center gap-3 mt-4 text-xs text-stone-600">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-3 rounded bg-sky-400 border border-sky-500" />
            남성
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-3 rounded bg-pink-300 border border-pink-400" />
            여성
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-3 rounded bg-stone-100 border border-stone-300" />
            빈자리
          </span>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-stone-200 p-5">
        <div className="text-sm font-semibold mb-3">
          미배치 손님 ({unassigned.length}명)
        </div>
        <div className="flex flex-wrap gap-1.5">
          {unassigned.length === 0 && <span className="text-xs text-stone-400">모두 배치 완료</span>}
          {unassigned.map((c) => (
            <span key={c.id}
              className={`px-2 py-1 rounded border text-xs ${
                c.gender === 'MALE'
                  ? 'bg-sky-50 border-sky-200 text-sky-800'
                  : 'bg-pink-50 border-pink-200 text-pink-800'
              }`}>
              <span className="font-medium">{c.nickname}</span>
            </span>
          ))}
        </div>
      </div>

      <QnaPanel round={round} questions={questions} assignedCustomers={assignedCustomers} />

      {picker && event && (
        <PickerModal
          eventId={event.id}
          round={round}
          seat={picker.seat}
          slot={picker.slot}
          customers={customers.filter((c) => c.status === 'ACTIVE')}
          assignedIds={assigned}
          existingAtCell={seatMap.get(`${picker.seat}-${picker.slot}`)?.customerId ?? null}
          onClose={() => setPicker(null)}
          onAssigned={() => { setPicker(null); refresh(); }}
        />
      )}
    </div>
  );
}

function QnaPanel({
  round, questions, assignedCustomers,
}: {
  round: number;
  questions: ValueQuestion[];
  assignedCustomers: { seat: SeatAssignment; customer: Customer }[];
}) {
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const toggle = (id: number) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const allOpen = assignedCustomers.length > 0 && expanded.size === assignedCustomers.length;
  const toggleAll = () =>
    setExpanded(allOpen ? new Set() : new Set(assignedCustomers.map((a) => a.customer.id)));

  return (
    <div className="bg-white rounded-lg border border-stone-200">
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-5 py-3 text-sm font-semibold hover:bg-stone-50 rounded-lg"
      >
        <span>
          {round}차 배치 손님 가치관 답변
          <span className="ml-2 text-xs font-normal text-stone-400">({assignedCustomers.length}명)</span>
        </span>
        <span className="text-xs text-stone-400">{open ? '숨기기 ▲' : '펼치기 ▼'}</span>
      </button>

      {open && (
        <div className="px-5 pb-5 border-t border-stone-100 pt-3 space-y-2">
          {questions.length === 0 ? (
            <div className="text-xs text-stone-400 py-2">등록된 가치관 질문이 없습니다.</div>
          ) : assignedCustomers.length === 0 ? (
            <div className="text-xs text-stone-400 py-2">이 차수에 배치된 손님이 없습니다.</div>
          ) : (
            <>
              <div className="flex justify-end">
                <button
                  onClick={toggleAll}
                  className="text-xs text-stone-500 hover:text-stone-900 underline underline-offset-2"
                >
                  {allOpen ? '전체 접기' : '전체 펼치기'}
                </button>
              </div>
              {assignedCustomers.map(({ seat, customer }) => {
                const isOpen = expanded.has(customer.id);
                const isMale = customer.gender === 'MALE';
                return (
                  <div key={customer.id} className="border border-stone-200 rounded-md overflow-hidden">
                    <button
                      onClick={() => toggle(customer.id)}
                      className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-stone-50"
                    >
                      <span className={`inline-flex items-center justify-center w-6 h-6 rounded text-xs font-bold ${
                        isMale ? 'bg-sky-100 text-sky-800' : 'bg-pink-100 text-pink-800'
                      }`}>
                        {seat.seatNumber}
                      </span>
                      <span className="font-medium text-sm">{customer.nickname}</span>
                      <span className={`text-[11px] ${isMale ? 'text-sky-600' : 'text-pink-600'}`}>
                        {isMale ? '남' : '여'}
                      </span>
                      {customer.mbti && (
                        <span className="text-[11px] text-stone-500 font-mono">{customer.mbti}</span>
                      )}
                      <span className="ml-auto text-xs text-stone-400">{isOpen ? '▲' : '▼'}</span>
                    </button>

                    {isOpen && (
                      <div className="px-3 pb-3 pt-1 border-t border-stone-100 space-y-2">
                        {questions.map((q, i) => {
                          const ans = customer.valuesAnswers?.[i];
                          const pickedA = ans === '0';
                          const pickedB = ans === '1';
                          return (
                            <div key={q.id} className="text-xs">
                              <div className="text-stone-500 mb-1">{i + 1}. {q.label}</div>
                              <div className="grid grid-cols-2 gap-1.5">
                                <Choice text={q.optionA} picked={pickedA} />
                                <Choice text={q.optionB} picked={pickedB} />
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </>
          )}
        </div>
      )}
    </div>
  );
}

function Choice({ text, picked }: { text: string; picked: boolean }) {
  return (
    <span
      className={`px-2 py-1.5 rounded border text-center ${
        picked
          ? 'bg-damso-100 border-damso-500 text-damso-800 font-semibold'
          : 'bg-stone-50 border-stone-200 text-stone-400'
      }`}
    >
      {text}
    </span>
  );
}

function Cell({
  seat, slot, seatMap, onAdd, onRemove,
}: {
  seat: number;
  slot: number;
  seatMap: Map<string, SeatAssignment>;
  onAdd: () => void;
  onRemove: (customerId: number) => void;
}) {
  const a = seatMap.get(`${seat}-${slot}`);
  const filled = a != null;
  const isMale = a?.gender === 'MALE';

  const cls = filled
    ? isMale
      ? 'border-sky-500 bg-sky-300 text-sky-900'
      : 'border-pink-400 bg-pink-300 text-pink-900'
    : 'border-stone-300 bg-stone-100 text-stone-400 hover:bg-stone-200';

  return (
    <div className={`relative rounded-md border-2 ${cls} min-h-[92px] p-2 flex flex-col gap-1 transition`}>
      <span className="absolute top-0.5 right-1.5 text-xs font-bold opacity-80">{seat}</span>
      {filled ? (
        <button
          onClick={() => {
            if (confirm(`${a.customerNickname}님을 자리에서 빼시겠습니까?`)) {
              onRemove(a.customerId);
            }
          }}
          className="bg-white border border-stone-200 rounded px-1 py-2 text-center hover:bg-red-50 hover:border-red-300 group flex-1 flex items-center justify-center"
        >
          <div className="text-lg font-extrabold text-stone-900 group-hover:text-red-700 leading-tight tracking-tight break-all w-full">
            {a.customerNickname}
          </div>
        </button>
      ) : (
        <button onClick={onAdd} className="flex-1 text-xs rounded hover:text-damso-600">
          + 배치
        </button>
      )}
    </div>
  );
}

function PickerModal({
  eventId, round, seat, slot, customers, assignedIds, existingAtCell, onClose, onAssigned,
}: {
  eventId: number;
  round: number;
  seat: number;
  slot: number;
  customers: Customer[];
  assignedIds: Set<number>;
  existingAtCell: number | null;
  onClose: () => void;
  onAssigned: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'MALE' | 'FEMALE'>('ALL');

  const assign = async (customerId: number) => {
    setBusy(true); setErr(null);
    try {
      await seatApi.manual({ eventId, round, seatNumber: seat, slotIndex: slot, customerId });
      onAssigned();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '배치 실패');
    } finally {
      setBusy(false);
    }
  };

  const filtered = customers.filter((c) => filter === 'ALL' || c.gender === filter);

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-md w-full max-h-[80vh] overflow-y-auto">
        <div className="px-5 py-4 border-b border-stone-200 flex items-center justify-between">
          <h2 className="font-semibold">
            {round}차 · {seat}번 자리 · 슬롯 {slot + 1}
          </h2>
          <button onClick={onClose} className="text-stone-500 hover:text-stone-900">✕</button>
        </div>
        <div className="p-5 space-y-3">
          {existingAtCell != null && (
            <div className="text-xs bg-amber-50 border border-amber-200 rounded p-2 text-amber-800">
              이 셀에 이미 손님이 있어요. 새로 선택하면 교체됩니다.
            </div>
          )}
          <div className="flex gap-1 text-xs">
            {(['ALL', 'MALE', 'FEMALE'] as const).map((f) => (
              <button key={f}
                onClick={() => setFilter(f)}
                className={`px-2 py-1 rounded ${
                  filter === f
                    ? 'bg-stone-900 text-white'
                    : 'bg-stone-100 text-stone-700 hover:bg-stone-200'
                }`}>
                {f === 'ALL' ? '전체' : f === 'MALE' ? '남성' : '여성'}
              </button>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-2">
            {filtered.length === 0 && (
              <div className="col-span-2 text-center text-stone-400 text-sm py-4">손님이 없습니다.</div>
            )}
            {filtered.map((c) => {
              const isHere = existingAtCell === c.id;
              const isOther = !isHere && assignedIds.has(c.id);
              return (
                <button
                  key={c.id}
                  disabled={busy || isHere}
                  onClick={() => assign(c.id)}
                  className={`text-left px-3 py-2 rounded border text-sm transition ${
                    isHere ? 'bg-stone-100 border-stone-200 text-stone-400' :
                    isOther ? 'bg-amber-50 border-amber-200 hover:bg-amber-100' :
                    c.gender === 'MALE' ? 'bg-sky-50 border-sky-200 hover:bg-sky-100' :
                    'bg-pink-50 border-pink-200 hover:bg-pink-100'
                  }`}
                >
                  <div className="font-medium">{c.nickname}</div>
                  <div className="text-[10px] text-stone-500">{c.gender === 'MALE' ? '남' : '여'}</div>
                  {isHere && <div className="text-[10px] text-stone-500 mt-1">현재 이 자리</div>}
                  {isOther && <div className="text-[10px] text-amber-700 mt-1">다른 자리에서 이동</div>}
                </button>
              );
            })}
          </div>
          {err && <div className="text-sm text-red-600">{err}</div>}
        </div>
      </div>
    </div>
  );
}