import type { Customer, GuestSettings } from '../../../shared/types';

export default function WaitingPage({ me, settings }: { me: Customer; settings: GuestSettings }) {
  return (
    <div className="text-center pt-12 space-y-4">
      <div className="text-5xl">🍷</div>
      <h1 className="text-xl font-bold">{me.nickname}님, {settings.waitingTitle}</h1>
      <p className="text-sm text-stone-600 leading-relaxed whitespace-pre-line">
        {settings.waitingSubtitle}
      </p>
      <div className="pt-4">
        <div className="inline-flex items-center gap-2 text-xs text-stone-500 bg-white border border-stone-200 px-3 py-1.5 rounded-full">
          <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse" />
          실시간 업데이트 중
        </div>
      </div>
    </div>
  );
}
