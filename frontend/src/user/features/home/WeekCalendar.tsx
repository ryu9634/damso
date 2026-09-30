import type { AppWeek, AppWeekItem, WeekItemKind } from '../../../shared/types';

/**
 * 홈의 '이번 주' 캘린더.
 *  - 월~일 7칸을 항상 그리고, 일정이 있는 날에만 점(또는 이모지)을 찍는다.
 *  - 칸 아래에는 일정이 있는 날만 한 줄씩 풀어서 보여준다. 모바일에서는 칸 안에
 *    글씨를 욱여넣는 것보다 이 편이 읽힌다.
 *  - 만남 약속은 본인에게만 내려오므로 여기 보이면 내 약속이다.
 */

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];

const DOT: Record<WeekItemKind, string> = {
  EVENT: 'bg-violet-400',
  HOLIDAY: 'bg-stone-400',
  MEETING: 'bg-rose-400',
};

const ROW: Record<WeekItemKind, string> = {
  EVENT: 'bg-violet-50 text-violet-700',
  HOLIDAY: 'bg-stone-100 text-stone-600',
  MEETING: 'bg-rose-50 text-rose-700',
};

export default function WeekCalendar({ week }: { week: AppWeek }) {
  const busyDays = week.days.filter((d) => d.items.length > 0);

  return (
    <section className="rounded-3xl bg-white border border-stone-200 shadow-sm p-5">
      <div className="flex items-baseline justify-between">
        <div className="text-sm font-bold text-stone-800">🗓 이번 주</div>
        <div className="text-xs text-stone-400 tabular-nums">
          {shortDay(week.weekStart)} ~ {shortDay(week.weekEnd)}
        </div>
      </div>

      {/* 요일 칸 */}
      <div className="mt-3 grid grid-cols-7 gap-1">
        {week.days.map((d, i) => (
          <div key={d.date} className="flex flex-col items-center gap-1">
            <span
              className={`text-[11px] font-semibold ${
                i === 5 ? 'text-sky-500' : i === 6 ? 'text-red-400' : 'text-stone-400'
              }`}
            >
              {WEEKDAYS[i]}
            </span>
            <span
              className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold tabular-nums ${
                d.today
                  ? 'bg-damso-500 text-white'
                  : d.items.some((it) => it.kind === 'HOLIDAY')
                    ? 'bg-stone-100 text-stone-400'
                    : 'text-stone-700'
              }`}
            >
              {dayNumber(d.date)}
            </span>
            {/* 일정 표시 점 — 종류별 색, 최대 3개까지만 */}
            <span className="h-1.5 flex items-center gap-0.5">
              {d.items.slice(0, 3).map((it, j) => (
                <span key={j} className={`w-1.5 h-1.5 rounded-full ${DOT[it.kind]}`} />
              ))}
            </span>
          </div>
        ))}
      </div>

      {/* 일정이 있는 날만 풀어서 */}
      {busyDays.length === 0 ? (
        <p className="mt-4 text-sm text-stone-400">이번 주에는 예정된 일정이 없어요.</p>
      ) : (
        <ul className="mt-4 space-y-2">
          {busyDays.map((d) => (
            <li key={d.date} className="flex gap-3">
              <span
                className={`w-12 shrink-0 pt-1.5 text-xs font-bold tabular-nums ${
                  d.today ? 'text-damso-600' : 'text-stone-400'
                }`}
              >
                {shortDay(d.date)}
              </span>
              <span className="min-w-0 flex-1 space-y-1.5">
                {d.items.map((it, i) => (
                  <ItemRow key={i} item={it} />
                ))}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function ItemRow({ item }: { item: AppWeekItem }) {
  return (
    <span className={`flex items-center gap-2 rounded-xl px-3 py-2 ${ROW[item.kind]}`}>
      <span className="text-base leading-none select-none">{item.emoji ?? '•'}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-bold truncate">{item.title}</span>
        {item.detail && (
          <span className="block text-xs opacity-80 truncate">{item.detail}</span>
        )}
      </span>
    </span>
  );
}

/** 2026-08-27 → 8/27 */
function shortDay(iso: string): string {
  const [, m, d] = iso.split('-');
  return `${Number(m)}/${Number(d)}`;
}

function dayNumber(iso: string): number {
  return Number(iso.split('-')[2]);
}
