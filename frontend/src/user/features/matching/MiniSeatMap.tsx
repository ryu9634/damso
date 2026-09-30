import { nameSizeClass } from '../../../shared/utils/nickname';
import type { GuestSeatView } from '../../../shared/types';

export interface SeatHighlight {
  id: number;
  label: string;
  tone: 'damso' | 'amber';
}

const TONE_CELL: Record<SeatHighlight['tone'], string> = {
  damso: 'border-damso-600 bg-damso-200 text-damso-900 ring-2 ring-damso-400',
  amber: 'border-amber-500 bg-amber-100 text-amber-900 ring-2 ring-amber-400',
};

const TONE_LABEL: Record<SeatHighlight['tone'], string> = {
  damso: 'text-damso-700',
  amber: 'text-amber-700',
};

/** 손님 화면용 ㄷ자 미니 자리 배치도. highlights로 특정 손님 셀을 강조. */
export default function MiniSeatMap({
  seats,
  myId,
  highlights = [],
  title = '현재 자리 배치 (참고)',
}: {
  seats: GuestSeatView[];
  myId: number;
  highlights?: SeatHighlight[];
  title?: string;
}) {
  const map = new Map<string, GuestSeatView>();
  seats.forEach((s) => map.set(`${s.seatNumber}-${s.slotIndex}`, s));

  const cell = (seat: number, slot: number) => {
    const a = map.get(`${seat}-${slot}`);
    const filled = a != null;
    const isMine = a?.customerId === myId;
    const hl = a?.customerId != null
      ? highlights.find((h) => h.id === a.customerId)
      : undefined;
    const isMale = a?.gender === 'MALE';

    let cls = 'border-stone-200 bg-stone-50 text-stone-400';
    if (isMine) cls = 'border-amber-500 bg-amber-200 text-amber-900';
    else if (hl) cls = TONE_CELL[hl.tone];
    else if (filled) cls = isMale
      ? 'border-sky-500 bg-sky-100 text-sky-900'
      : 'border-pink-400 bg-pink-100 text-pink-900';

    return (
      <div className={`relative rounded border-2 ${cls} min-h-[44px] p-0.5 flex flex-col justify-center items-center`}>
        <span className="absolute top-0 right-0.5 text-[8px] font-bold opacity-70">{seat}</span>
        {filled ? (
          <div className={`font-extrabold ${nameSizeClass(a.nickname)} w-full text-center break-all leading-[1.1]`}>{a.nickname}</div>
        ) : (
          <span className="text-[9px]">빈</span>
        )}
        {hl && !isMine && (
          <div className={`absolute bottom-0 left-0.5 text-[8px] font-bold ${TONE_LABEL[hl.tone]}`}>{hl.label}</div>
        )}
      </div>
    );
  };

  return (
    <div className="bg-stone-100 rounded-xl border border-stone-300 p-2">
      <div className="text-[11px] text-stone-600 mb-2 text-center">{title}</div>
      <div className="grid grid-cols-9 grid-rows-7 gap-1" style={{ gridAutoFlow: 'row dense' }}>
        <div />
        {cell(6, 0)}{cell(6, 1)}{cell(5, 0)}{cell(5, 1)}{cell(4, 0)}{cell(4, 1)}{cell(3, 0)}
        <div />

        {cell(7, 0)}
        <div className="col-span-7 row-span-6 bg-amber-50 border-2 border-amber-300 rounded-lg flex items-center justify-center">
          <div className="text-2xl">🍷</div>
        </div>
        {cell(3, 1)}

        {cell(7, 1)}{cell(2, 0)}
        {cell(8, 0)}{cell(2, 1)}
        {cell(8, 1)}{cell(1, 0)}
        {cell(9, 0)}{cell(1, 1)}
        {cell(9, 1)}
      </div>
    </div>
  );
}