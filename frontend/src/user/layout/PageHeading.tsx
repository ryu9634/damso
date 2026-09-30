import type { ReactNode } from 'react';

/**
 * 하위 화면(동의 · 등록 · 수정 등)의 머리말.
 *  - 탭 자체(홈·방명록·소개팅·셀렉팅·마이페이지)는 하단 탭에 이름이 이미 있어 제목을 두지 않는다.
 *    지금 어디인지 헷갈릴 수 있는 하위 화면에서만 쓴다.
 *  - back: 되돌아가는 버튼 (제목 위에 붙는다)
 *  - right: 제목 오른쪽 보조 버튼
 *  - 보여줄 게 하나도 없으면 아무것도 그리지 않는다.
 */
export default function PageHeading({
  title,
  description,
  back,
  right,
}: {
  title?: string;
  description?: ReactNode;
  back?: () => void;
  right?: ReactNode;
}) {
  if (!title && !description && !back && !right) return null;

  return (
    <header className="max-w-md mx-auto px-4 pt-5 pb-3">
      {back && (
        <button
          type="button"
          onClick={back}
          className="mb-2 -ml-1 px-1 py-0.5 text-sm text-stone-500 active:text-stone-800"
        >
          ← 뒤로
        </button>
      )}
      {(title || description || right) && (
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            {title && <h1 className="text-xl font-bold text-stone-900">{title}</h1>}
            {description && (
              <p className="mt-1 text-sm text-stone-500 leading-relaxed">{description}</p>
            )}
          </div>
          {right && <div className="shrink-0 pt-1">{right}</div>}
        </div>
      )}
    </header>
  );
}

/** 본문 영역 — 좌우 여백과 카드 간격을 모든 화면에서 통일한다. */
export function PageBody({ children }: { children: ReactNode }) {
  return <div className="max-w-md mx-auto px-4 pt-4 pb-6 space-y-4">{children}</div>;
}
