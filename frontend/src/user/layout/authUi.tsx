import type { AuthProviderInfo } from '../../shared/types';

/**
 * 로그인·마이페이지에서 함께 쓰는 규칙과 조각들.
 * (서버 검증 규칙과 값을 맞춰 두고 한곳에서만 고친다)
 */

/** 가입 가능 최소 나이 (서버 AppUser.MIN_AGE 와 동일). */
export const MIN_AGE = 18;
/** 아이디 규칙 (서버 AuthRequests.LOGIN_ID_PATTERN 과 동일). */
export const LOGIN_ID_RE = /^[A-Za-z0-9._-]{4,20}$/;
/** 닉네임 규칙 (서버 AuthRequests.NICKNAME_PATTERN 과 동일) — 공백 없이 2~12자. */
export const NICKNAME_RE = /^\S{2,12}$/;
/** 휴대폰번호 규칙 (서버 AuthRequests.PHONE_PATTERN 과 동일) — 하이픈을 뺀 숫자만. */
export const PHONE_RE = /^01[0-9]{8,9}$/;
export const MIN_PASSWORD = 8;

/** 화면에 보이는 하이픈을 뺀 실제 저장 형태. */
export function phoneDigits(input: string): string {
  return input.replace(/\D/g, '');
}

export const INPUT =
  'w-full rounded-xl border border-stone-300 px-4 py-3 text-stone-800 focus:outline-none focus:border-damso-500';
export const LABEL = 'block text-sm font-medium text-stone-600 mb-1';
export const PRIMARY_BTN =
  'w-full rounded-full bg-damso-500 text-white py-3 font-semibold active:bg-damso-600 disabled:opacity-40';
export const SUB_BTN =
  'w-full rounded-full border border-stone-300 bg-white text-stone-700 py-2.5 text-sm font-semibold active:bg-stone-100 disabled:opacity-40';

/** 만 나이 계산 (생일이 지났는지까지 반영). */
export function ageOf(birthDate: string): number | null {
  const d = new Date(birthDate);
  if (Number.isNaN(d.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - d.getFullYear();
  const beforeBirthday =
    today.getMonth() < d.getMonth() ||
    (today.getMonth() === d.getMonth() && today.getDate() < d.getDate());
  if (beforeBirthday) age -= 1;
  return age;
}

const PROVIDER_BTN: Record<string, string> = {
  KAKAO: 'bg-[#FEE500] text-[#191600]',
  NAVER: 'bg-[#03C75A] text-white',
};

/** 간편로그인 버튼 묶음. link 모드면 돌아온 뒤 현재 계정에 연결한다. */
export function SocialButtons({
  providers,
  link,
}: {
  providers: AuthProviderInfo[];
  link?: boolean;
}) {
  const go = (p: AuthProviderInfo) => {
    // 콜백 화면이 "로그인"인지 "연동"인지 구분할 수 있게 표시해 둔다.
    sessionStorage.setItem('damso.oauth.mode', link ? 'link' : 'login');
    window.location.assign(p.authorizeUrl!);
  };
  return (
    <>
      {providers.map((p) => (
        <button
          key={p.provider}
          onClick={() => go(p)}
          className={`block w-full rounded-full py-3 text-center font-semibold ${
            PROVIDER_BTN[p.provider] ?? 'bg-stone-800 text-white'
          }`}
        >
          {p.label}{link ? ' 계정 연결하기' : '로 시작하기'}
        </button>
      ))}
    </>
  );
}

/** 마이페이지 정보 한 줄. */
export function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-stone-100 last:border-0">
      <span className="text-sm text-stone-500">{label}</span>
      <span className="text-sm font-semibold text-stone-800">{value}</span>
    </div>
  );
}
