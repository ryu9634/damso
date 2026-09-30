import type { ReactNode } from 'react';
import type { Customer, GuestSettings } from '../../../shared/types';

/**
 * 운영시간 외(이벤트 종료) 안내 화면.
 * 안내 문구는 줄바꿈을 그대로 살리고, 주소가 들어 있으면 눌러서 열 수 있게 한다.
 */
export default function EndPage({
  me, onReset, settings,
}: { me: Customer | null; onReset: () => void; settings: GuestSettings }) {
  return (
    <div className="text-center pt-12 space-y-4">
      <div className="text-5xl">🌙</div>
      <h1 className="text-xl font-bold">{settings.endTitle}</h1>
      <p className="text-sm text-stone-600 leading-relaxed whitespace-pre-line">
        {linkify(settings.endSubtitle)}
      </p>
      {me && (
        <button
          onClick={onReset}
          className="mt-6 text-xs text-stone-500 underline"
        >
          다시 시작하기
        </button>
      )}
    </div>
  );
}

/**
 * 문구 속 주소를 링크로 바꿔준다. 두 가지 형태를 받는다.
 *   [예약링크 바로가기](https://…)  → 레이블이 보이고 주소는 숨는다
 *   https://…                      → 주소가 그대로 보인다 (예전 문구 호환)
 */
const LINK_PATTERN = /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/\S+)/g;

function linkify(text: string): ReactNode[] {
  const out: ReactNode[] = [];
  let cursor = 0;

  for (const m of text.matchAll(LINK_PATTERN)) {
    const at = m.index ?? 0;
    if (at > cursor) out.push(text.slice(cursor, at));

    // 레이블 형태면 [1]=레이블 [2]=주소, 맨주소 형태면 [3]에 주소가 들어온다.
    const href = m[2] ?? m[3];
    const label = m[1] ?? m[3];
    out.push(
      <a
        key={at}
        href={href}
        target="_blank"
        rel="noreferrer"
        className="text-damso-600 font-semibold underline break-all"
      >
        {label}
      </a>,
    );
    cursor = at + m[0].length;
  }

  if (cursor < text.length) out.push(text.slice(cursor));
  return out;
}
