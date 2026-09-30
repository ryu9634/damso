/**
 * 매장 대면 확인 배지.
 * 사장님이 매장에서 실제로 얼굴을 본 회원에게만 관리자가 켠다.
 * 온라인 소개팅 앱이 줄 수 없는 보증이라 눈에 띄게 둔다.
 */
export default function VerifiedBadge({ className = '' }: { className?: string }) {
  return (
    <span
      className={
        'inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 ' +
        'text-[11px] font-bold text-emerald-700 ' +
        className
      }
      title="담소혼술바에서 직접 확인한 회원이에요"
    >
      ✅ 매장 확인
    </span>
  );
}
