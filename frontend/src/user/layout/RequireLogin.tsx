import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAppAuth } from '../features/auth/useAppAuth';

/**
 * 신청 화면(담소 소개팅 · 담소 셀렉팅) 앞의 마지막 관문.
 *  - 로그인 자체는 AppShell 이 이미 막아준다.
 *  - 여기서는 간편로그인으로 들어와 필수정보(닉네임·성명·생년월일·휴대폰번호)가 비어 있는 회원을
 *    마이페이지로 안내한다.
 */
export default function RequireLogin({ children }: { children: ReactNode }) {
  const { user } = useAppAuth();

  if (!user) return null;

  if (!user.profileComplete) {
    return (
      <div className="max-w-md mx-auto px-4 pt-16">
        <div className="rounded-3xl bg-white border border-stone-200 shadow-sm p-8 text-center">
          <div className="text-3xl">📝</div>
          <h1 className="mt-3 text-xl font-bold text-stone-900">회원 정보를 먼저 채워주세요</h1>
          <p className="mt-2 text-sm text-stone-500 leading-relaxed">
            닉네임·성명·생년월일·휴대폰번호를 입력해야 신청할 수 있어요.
          </p>
          <Link
            to="/my"
            className="mt-6 block w-full rounded-full bg-damso-500 text-white py-3 font-semibold active:bg-damso-600"
          >
            마이페이지로 이동
          </Link>
          <p className="mt-3 text-xs text-stone-400">만 18세 이상만 이용할 수 있어요.</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
