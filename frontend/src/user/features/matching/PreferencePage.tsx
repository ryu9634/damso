import { useEffect, useState } from 'react';
import { guestApi } from './guest';
import MiniSeatMap, { type SeatHighlight } from './MiniSeatMap';
import type { Customer, GuestCandidate, GuestSeatView, GuestSettings, Preference } from '../../../shared/types';

type Rank = 1 | 2 | 3;

const RANK_META: Record<Rank, { label: string; border: string; btnOn: string; slot: string; tone: SeatHighlight['tone'] }> = {
  1: { label: '1순위', border: 'border-damso-500', btnOn: 'bg-damso-500 text-white', slot: 'text-damso-600 bg-damso-50', tone: 'damso' },
  2: { label: '2순위', border: 'border-amber-400', btnOn: 'bg-amber-400 text-white', slot: 'text-amber-700 bg-amber-50', tone: 'amber' },
  3: { label: '3순위', border: 'border-sky-400', btnOn: 'bg-sky-400 text-white', slot: 'text-sky-700 bg-sky-50', tone: 'amber' },
};

export default function PreferencePage({ me, settings }: { me: Customer; settings: GuestSettings }) {
  const [candidates, setCandidates] = useState<GuestCandidate[]>([]);
  const [seats, setSeats] = useState<GuestSeatView[]>([]);
  const [existing, setExisting] = useState<Preference | null>(null);
  // picks[0] = 1순위, picks[1] = 2순위, picks[2] = 3순위
  const [picks, setPicks] = useState<(number | null)[]>([null, null, null]);
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const setPick = (rank: Rank, id: number | null) =>
    setPicks((prev) => prev.map((v, i) => (i === rank - 1 ? id : v)));

  useEffect(() => {
    Promise.all([
      guestApi.candidates(me.id),
      guestApi.myPreference(me.id),
      guestApi.seats(me.id, 3),
    ]).then(([cs, pref, ss]) => {
      setCandidates(cs);
      setSeats(ss);
      if (pref) {
        setExisting(pref);
        setPicks([pref.firstChoiceId, pref.secondChoiceId, pref.thirdChoiceId]);
        setSubmitted(true);
      }
    });
  }, [me.id]);

  const submit = async () => {
    const chosen = picks.filter((p): p is number => p != null);
    if (chosen.length !== new Set(chosen).size) {
      setErr('1·2·3순위는 서로 다른 사람이어야 해요.');
      return;
    }
    setSubmitting(true); setErr(null);
    try {
      await guestApi.submitPreference({
        customerId: me.id,
        firstChoiceId: picks[0],
        secondChoiceId: picks[1],
        thirdChoiceId: picks[2],
      });
      setSubmitted(true);
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '제출 실패');
    } finally {
      setSubmitting(false);
    }
  };

  // 어떤 후보를 특정 순위로 지정. 이미 다른 순위로 잡혀있으면 그 순위는 비우고 이동.
  const togglePick = (id: number, rank: Rank) => {
    if (submitted) return;
    setPicks((prev) => {
      const next = [...prev];
      if (next[rank - 1] === id) {
        next[rank - 1] = null; // 같은 버튼 다시 누르면 해제
        return next;
      }
      // 다른 순위에 이미 있으면 제거
      for (let i = 0; i < next.length; i++) if (next[i] === id) next[i] = null;
      next[rank - 1] = id;
      return next;
    });
  };

  if (submitted) {
    return (
      <div className="space-y-5">
        <div className="text-center pt-2">
          <div className="text-5xl">💌</div>
          <h1 className="text-xl font-bold mt-2">지목 완료</h1>
          <p className="text-sm text-stone-600 mt-1">결과를 기다려주세요</p>
        </div>
        <div className="bg-white border border-stone-200 rounded-xl p-4 space-y-3">
          {([1, 2, 3] as Rank[]).map((r) =>
            picks[r - 1] != null ? (
              <Row key={r} rank={r} candidate={candidates.find((c) => c.id === picks[r - 1])} />
            ) : null,
          )}
        </div>
        {existing && (
          <button
            onClick={() => setSubmitted(false)}
            className="w-full py-3 rounded-md border border-stone-300 text-stone-700 active:bg-stone-100"
          >
            다시 선택하기
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="text-center pt-2">
        <div className="text-3xl">💗</div>
        <h1 className="text-xl font-bold mt-2">{settings.preferenceTitle}</h1>
        <p className="text-sm text-stone-600 mt-1 whitespace-pre-line">{settings.preferenceSubtitle}</p>
      </div>

      <div className="flex items-center justify-around bg-white border border-stone-200 rounded-lg py-3 px-2">
        {([1, 2, 3] as Rank[]).map((r) => (
          <Slot key={r} rank={r} customer={candidates.find((c) => c.id === picks[r - 1])} onClear={() => setPick(r, null)} />
        ))}
      </div>

      <MiniSeatMap
        seats={seats}
        myId={me.id}
        title="현재 자리 배치 · 3차 (참고)"
        highlights={([1, 2, 3] as Rank[]).flatMap((r) =>
          picks[r - 1] != null
            ? [{ id: picks[r - 1]!, label: RANK_META[r].label, tone: RANK_META[r].tone } as SeatHighlight]
            : [],
        )}
      />

      <div>
        <div className="text-xs font-semibold text-stone-600 mb-2">
          {me.gender === 'MALE' ? '여성' : '남성'} 손님 {candidates.length}명
        </div>
        <div className="space-y-2">
          {candidates.map((c) => {
            const rank = (picks.indexOf(c.id) + 1) as 0 | Rank;
            const activeBorder = rank ? RANK_META[rank].border : 'border-stone-200';
            return (
              <div
                key={c.id}
                className={`bg-white border-2 rounded-lg p-3 flex items-center justify-between gap-2 ${activeBorder}`}
              >
                <div className="flex-1 min-w-0">
                  <div className="text-base font-bold text-stone-900 truncate">{c.nickname}</div>
                </div>
                <div className="flex gap-1">
                  {([1, 2, 3] as Rank[]).map((r) => (
                    <button
                      key={r}
                      onClick={() => togglePick(c.id, r)}
                      className={`px-2.5 py-1.5 rounded-md text-xs font-bold ${
                        rank === r ? RANK_META[r].btnOn : 'bg-stone-100 text-stone-700 active:bg-stone-200'
                      }`}
                    >
                      {r}순위
                    </button>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {err && <div className="text-sm text-red-600 text-center">{err}</div>}

      <div className="sticky bottom-3 pt-2">
        <button
          onClick={submit}
          disabled={submitting}
          className="w-full py-4 rounded-full text-white font-bold bg-damso-500 active:bg-damso-600 disabled:bg-stone-300 shadow-lg"
        >
          {submitting ? '제출 중...' : '제출하기'}
        </button>
      </div>
    </div>
  );
}

function Slot({
  rank, customer, onClear,
}: { rank: Rank; customer?: GuestCandidate; onClear: () => void }) {
  return (
    <div className="flex flex-col items-center gap-1">
      <div className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${RANK_META[rank].slot}`}>{rank}순위</div>
      {customer ? (
        <button onClick={onClear} className="font-bold text-stone-900 active:text-red-600 text-sm">
          {customer.nickname} ✕
        </button>
      ) : (
        <div className="text-stone-300 text-sm">미선택</div>
      )}
    </div>
  );
}

function Row({ rank, candidate }: { rank: Rank; candidate?: GuestCandidate }) {
  if (!candidate) return null;
  return (
    <div className="flex items-center justify-between">
      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${RANK_META[rank].slot}`}>{RANK_META[rank].label}</span>
      <div className="text-right">
        <div className="font-bold text-stone-900">{candidate.nickname}</div>
      </div>
    </div>
  );
}
