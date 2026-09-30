import type { Event } from '../../../shared/types';
import { STATUS_LABEL } from '../../../shared/types';
import { PageBody } from '../../layout/PageHeading';

const STEPS: { code: Event['status']; label: string }[] = [
  { code: 'REGISTRATION', label: '입장' },
  { code: 'ROUND_1', label: '1차' },
  { code: 'ROUND_2', label: '2차' },
  { code: 'PREFERENCE', label: '지목' },
  { code: 'ROUND_3', label: '3차' },
  { code: 'MESSAGE', label: '메시지' },
];

/**
 * 담소 소개팅(현장 이벤트) 탭의 틀.
 *  - 상단 제목·여백은 다른 탭과 같은 PageHeading / PageBody 를 쓴다.
 *  - 이 탭에만 있는 진행 단계 막대는 제목 바로 아래에 붙인다.
 */
export default function GuestShell({
  event,
  children,
}: {
  event: Event | null;
  children: React.ReactNode;
}) {
  const currentIdx = STEPS.findIndex((s) => s.code === event?.status);
  const showSteps = !!event && event.status !== 'CLOSED';

  return (
    <>
      {/* 이 탭에만 있는 진행 단계 표시 (제목은 하단 탭으로 갈음) */}
      {showSteps && (
        <div className="max-w-md mx-auto px-4 pt-4">
          <div className="text-xs text-stone-400 mb-1.5 text-right">
            {STATUS_LABEL[event!.status]}
          </div>
          <div className="flex items-center gap-1">
            {STEPS.map((s, i) => (
              <div
                key={s.code}
                className={`h-1.5 flex-1 rounded-full ${
                  i <= currentIdx ? 'bg-damso-500' : 'bg-stone-200'
                }`}
              />
            ))}
          </div>
        </div>
      )}

      <PageBody>{children}</PageBody>
    </>
  );
}
