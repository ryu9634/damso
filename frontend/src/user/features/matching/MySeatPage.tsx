import { useEffect, useState } from 'react';
import { guestApi } from './guest';
import { nameSizeClass } from '../../../shared/utils/nickname';
import type { Customer, GuestSeatView, ValueQuestion } from '../../../shared/types';

const ROUND_LABEL: Record<number, string> = {
  1: '1차 자리',
  2: '2차 자리',
  3: '3차 자리',
  4: '4차 자리',
};

export default function MySeatPage({ me, round }: { me: Customer; round: number }) {
  const [seats, setSeats] = useState<GuestSeatView[]>([]);
  const [scores, setScores] = useState<Record<number, number>>({});
  const [questions, setQuestions] = useState<ValueQuestion[]>([]);
  const [showQna, setShowQna] = useState(false);

  useEffect(() => {
    const refresh = () => guestApi.seats(me.id, round).then(setSeats);
    refresh();
    const t = setInterval(refresh, 4000);
    return () => clearInterval(t);
  }, [me.id, round]);

  useEffect(() => {
    guestApi.questions().then(setQuestions).catch(() => setQuestions([]));
  }, []);

  useEffect(() => {
    if (round === 2) {
      guestApi.myMatchScores(me.id).then(setScores).catch(() => setScores({}));
    } else {
      setScores({});
    }
  }, [me.id, round]);

  const seatMap = new Map<string, GuestSeatView>();
  seats.forEach((s) => seatMap.set(`${s.seatNumber}-${s.slotIndex}`, s));

  const C = (seat: number, slot: number) => (
    <Cell seatMap={seatMap} myId={me.id} myGender={me.gender} scores={scores} seat={seat} slot={slot} />
  );

  const mine = seats.find((s) => s.customerId === me.id);

  return (
    <div className="space-y-4">
      <div className="text-center pt-1">
        <div className="text-sm font-bold text-damso-600 tracking-wide">
          {ROUND_LABEL[round]}
        </div>
        {!mine && (
          <div className="mt-2 text-sm text-stone-600">자리 배정을 기다리는 중...</div>
        )}
      </div>

      <div className="bg-stone-100 rounded-xl border border-stone-300 p-3">
        <div
          className="grid grid-cols-9 grid-rows-7 gap-1 mx-auto"
          style={{ gridAutoFlow: 'row dense' }}
        >
          {/* Row 1 */}
          <div />
          {C(6, 0)}
          {C(6, 1)}
          {C(5, 0)}
          {C(5, 1)}
          {C(4, 0)}
          {C(4, 1)}
          {C(3, 0)}
          <div />

          {/* Row 2 */}
          {C(7, 0)}
          <div className="col-span-7 row-span-6 bg-amber-50 border-2 border-amber-300 rounded-lg flex flex-col items-center justify-center text-center">
            <div className="text-3xl">🍷</div>
            <div className="text-xs font-bold text-amber-800 mt-1">주인장</div>
          </div>
          {C(3, 1)}

          {/* Row 3-7 */}
          {C(7, 1)}
          {C(2, 0)}

          {C(8, 0)}
          {C(2, 1)}

          {C(8, 1)}
          {C(1, 0)}

          {C(9, 0)}
          {C(1, 1)}

          {C(9, 1)}
        </div>

        <div className="flex items-center justify-center gap-3 mt-3 text-[10px] text-stone-600">
          <span className="flex items-center gap-1">
            <span className="inline-block w-2.5 h-2.5 rounded bg-sky-400 border border-sky-500" /> 남
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block w-2.5 h-2.5 rounded bg-pink-300 border border-pink-400" /> 여
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block w-2.5 h-2.5 rounded bg-amber-300 border border-amber-500" /> 내 자리
          </span>
        </div>
      </div>

      <div className="text-xs text-stone-500 text-center">
        실시간 갱신 중 · 자리가 바뀌면 자동으로 표시돼요
      </div>

      {questions.length > 0 && (
        <div className="bg-white rounded-xl border border-stone-200">
          <button
            onClick={() => setShowQna((v) => !v)}
            className="w-full flex items-center justify-between px-4 py-3 active:bg-stone-50 rounded-xl"
          >
            <span className="text-sm font-bold text-stone-800">
              내가 답한 가치관
              <span className="ml-1.5 text-xs font-normal text-stone-400">{questions.length}문항</span>
            </span>
            <span className="text-xs text-stone-400">{showQna ? '숨기기 ▲' : '펼치기 ▼'}</span>
          </button>
          {showQna && (
            <div className="px-4 pb-4 border-t border-stone-100 pt-3 space-y-3">
              {questions.map((q, i) => {
                const ans = me.valuesAnswers?.[i];
                return (
                  <div key={q.id}>
                    <div className="text-xs text-stone-500 mb-1.5">{i + 1}. {q.label}</div>
                    <div className="grid grid-cols-2 gap-1.5">
                      <Opt text={q.optionA} picked={ans === '0'} />
                      <Opt text={q.optionB} picked={ans === '1'} />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Opt({ text, picked }: { text: string; picked: boolean }) {
  return (
    <span
      className={`px-2.5 py-2 rounded-md text-xs text-center border-2 leading-tight ${
        picked
          ? 'bg-damso-100 border-damso-500 text-damso-800 font-semibold'
          : 'bg-stone-50 border-transparent text-stone-400'
      }`}
    >
      {text}
    </span>
  );
}

function Cell({
  seatMap, myId, myGender, scores, seat, slot,
}: {
  seatMap: Map<string, GuestSeatView>;
  myId: number;
  myGender: 'MALE' | 'FEMALE';
  scores: Record<number, number>;
  seat: number;
  slot: number;
}) {
  const a = seatMap.get(`${seat}-${slot}`);
  const filled = a != null;
  const isMine = a?.customerId === myId;
  const isMale = a?.gender === 'MALE';
  const isOpposite = filled && !isMine && a?.gender !== myGender;
  const score = isOpposite && a?.customerId != null ? scores[a.customerId] : undefined;

  const base = isMine
    ? 'border-amber-500 bg-amber-200 text-amber-900'
    : filled
    ? isMale
      ? 'border-sky-500 bg-sky-300 text-sky-900'
      : 'border-pink-400 bg-pink-300 text-pink-900'
    : 'border-stone-200 bg-stone-50 text-stone-400';

  return (
    <div className={`relative rounded border-2 ${base} min-h-[56px] p-1 flex flex-col justify-center items-center leading-tight`}>
      <span className={`absolute top-0 right-0.5 text-[9px] font-bold ${isMine ? 'text-amber-700' : filled ? 'opacity-80' : 'text-stone-500'}`}>
        {seat}
      </span>
      {filled ? (
        <>
          <div className={`font-extrabold ${nameSizeClass(a.nickname)} w-full text-center break-all leading-[1.1]`}>{a.nickname}</div>
          {isMine && <div className="text-[9px] font-bold mt-0.5">★ 나</div>}
          {score !== undefined && (
            <div className="absolute bottom-0 left-0.5 text-[9px] font-bold bg-white/80 px-1 rounded">
              {score}점
            </div>
          )}
        </>
      ) : (
        <span className="text-[10px]">빈자리</span>
      )}
    </div>
  );
}