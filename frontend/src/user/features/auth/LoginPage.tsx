import { useEffect, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import type { AuthProviderInfo } from '../../../shared/types';
import { authApi, appErrMsg } from '../../api/app';
import { useAppAuth } from './useAppAuth';
import { formatPhone } from '../../../shared/utils/phone';
import {
  INPUT, LABEL, MIN_AGE, MIN_PASSWORD, LOGIN_ID_RE, NICKNAME_RE, PHONE_RE,
  PRIMARY_BTN, SocialButtons, ageOf, phoneDigits,
} from '../../layout/authUi';

/**
 * 로그인 · 회원가입 화면.
 *  - 앱은 로그인해야 쓸 수 있어서, 비로그인 상태면 어느 탭으로 들어와도 여기로 온다.
 *  - 로그인에 성공하면 원래 가려던 화면(state.from)으로 되돌려 보낸다.
 */
export default function LoginPage() {
  const { user, loading, signIn } = useAppAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [providers, setProviders] = useState<AuthProviderInfo[]>([]);

  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  // 가입 폼 추가 항목
  const [password2, setPassword2] = useState('');
  const [nickname, setNickname] = useState('');
  const [name, setName] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [phone, setPhone] = useState('');
  const [pushOptIn, setPushOptIn] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [idCheck, setIdCheck] = useState<'idle' | 'ok' | 'taken'>('idle');
  const [nickCheck, setNickCheck] = useState<'idle' | 'ok' | 'taken'>('idle');

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  /**
   * 아이디·비밀번호 입력칸 참조.
   * 비밀번호 관리자(사파리·크롬 자동완성)가 값을 직접 넣으면 React 의 onChange 가 안 걸려
   * 화면에는 채워져 보여도 state 는 빈 채로 남는다. 제출할 때는 입력칸의 실제 값을 먼저 본다.
   */
  const loginIdRef = useRef<HTMLInputElement | null>(null);
  const passwordRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    authApi.providers()
      .then((list) => setProviders(list.filter((p) => p.configured && p.authorizeUrl)))
      .catch(() => setProviders([]));
  }, []);

  // 가입 화면에서 아이디를 다 입력하면 중복 여부를 조용히 확인한다.
  useEffect(() => {
    if (mode !== 'signup' || !LOGIN_ID_RE.test(loginId)) {
      setIdCheck('idle');
      return;
    }
    let alive = true;
    const timer = setTimeout(() => {
      authApi.checkId(loginId)
        .then((ok) => { if (alive) setIdCheck(ok ? 'ok' : 'taken'); })
        .catch(() => { if (alive) setIdCheck('idle'); });
    }, 350);
    return () => { alive = false; clearTimeout(timer); };
  }, [loginId, mode]);

  // 닉네임도 같은 방식으로 중복 여부를 확인한다.
  useEffect(() => {
    if (mode !== 'signup' || !NICKNAME_RE.test(nickname)) {
      setNickCheck('idle');
      return;
    }
    let alive = true;
    const timer = setTimeout(() => {
      authApi.checkNickname(nickname)
        .then((ok) => { if (alive) setNickCheck(ok ? 'ok' : 'taken'); })
        .catch(() => { if (alive) setNickCheck('idle'); });
    }, 350);
    return () => { alive = false; clearTimeout(timer); };
  }, [nickname, mode]);

  if (loading) {
    return <div className="py-20 text-center text-sm text-stone-400">불러오는 중...</div>;
  }
  // 이미 로그인한 상태로 들어오면 앱으로 돌려보낸다.
  if (user) return <Navigate to={from} replace />;

  const age = birthDate ? ageOf(birthDate) : null;
  const underAge = age !== null && age < MIN_AGE;
  const idValid = LOGIN_ID_RE.test(loginId);
  const nickValid = NICKNAME_RE.test(nickname);
  const pwValid = password.length >= MIN_PASSWORD;
  const pwMatch = password === password2;
  const phoneValid = PHONE_RE.test(phoneDigits(phone));

  const canSignup =
    idValid && idCheck !== 'taken' && pwValid && pwMatch &&
    nickValid && nickCheck !== 'taken' &&
    name.trim().length > 0 && birthDate.length > 0 && !underAge &&
    phoneValid && agreed;
  const submit = async () => {
    // 자동완성으로 채워진 값도 놓치지 않도록 입력칸에서 직접 읽는다.
    const id = (loginIdRef.current?.value ?? loginId).trim();
    const pw = passwordRef.current?.value ?? password;

    if (mode === 'login' && (id.length === 0 || pw.length === 0)) {
      setError('아이디와 비밀번호를 입력해주세요.');
      return;
    }

    setBusy(true);
    setError('');
    try {
      const result =
        mode === 'signup'
          ? await authApi.signup({
              loginId: id,
              password: pw,
              nickname: nickname.trim(),
              name: name.trim(),
              birthDate,
              phone: phoneDigits(phone),
              pushOptIn,
              agreedPrivacy: agreed,
            })
          : await authApi.login(id, pw);
      signIn(result.token, result.user);
      navigate(from, { replace: true });
    } catch (e) {
      setError(appErrMsg(e, mode === 'signup' ? '가입하지 못했어요.' : '로그인하지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-stone-50">
      <div className="max-w-md mx-auto px-4 pt-12 pb-12">
        <header className="px-1 text-center">
          <div className="text-xs font-semibold tracking-widest text-damso-500">DAMSO</div>
          <h1 className="mt-1 text-2xl font-bold text-stone-900">담소혼술바</h1>
          <p className="mt-2 text-sm text-stone-500">
            로그인하면 홈·방명록·소개팅을 모두 이용할 수 있어요.
          </p>
        </header>

        {/* ─── 로그인 / 가입 전환 ─────────────────── */}
        <div className="mt-7 flex rounded-full bg-stone-100 p-1">
          {(['login', 'signup'] as const).map((m) => (
            <button
              key={m}
              onClick={() => { setMode(m); setError(''); }}
              className={`flex-1 rounded-full py-2 text-sm font-semibold transition ${
                mode === m ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-500'
              }`}
            >
              {m === 'login' ? '로그인' : '회원가입'}
            </button>
          ))}
        </div>

        <form
          className="mt-5 space-y-4"
          onSubmit={(e) => { e.preventDefault(); if (!busy) submit(); }}
        >
          <div>
            <label className={LABEL}>아이디 *</label>
            <input
              ref={loginIdRef}
              className={INPUT}
              value={loginId}
              autoCapitalize="none"
              autoCorrect="off"
              autoComplete="username"
              onChange={(e) => setLoginId(e.target.value.trim())}
              placeholder="영문·숫자 4~20자"
            />
            {mode === 'signup' && loginId.length > 0 && (
              <p
                className={`mt-1 text-xs ${
                  !idValid || idCheck === 'taken' ? 'text-red-500' : 'text-emerald-600'
                }`}
              >
                {!idValid
                  ? '영문·숫자·._- 조합 4~20자로 만들어주세요.'
                  : idCheck === 'taken'
                    ? '이미 사용 중인 아이디예요.'
                    : idCheck === 'ok'
                      ? '사용할 수 있는 아이디예요.'
                      : '확인 중...'}
              </p>
            )}
          </div>

          <div>
            <label className={LABEL}>비밀번호 *</label>
            <input
              ref={passwordRef}
              className={INPUT}
              type="password"
              value={password}
              autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'signup' ? `${MIN_PASSWORD}자 이상` : '비밀번호'}
            />
          </div>

          {mode === 'signup' && (
            <>
              <div>
                <label className={LABEL}>비밀번호 확인 *</label>
                <input
                  className={INPUT}
                  type="password"
                  value={password2}
                  onChange={(e) => setPassword2(e.target.value)}
                />
                {password2.length > 0 && !pwMatch && (
                  <p className="mt-1 text-xs text-red-500">비밀번호가 서로 달라요.</p>
                )}
              </div>

              <div>
                <label className={LABEL}>닉네임 *</label>
                <input
                  className={INPUT}
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value.replace(/\s/g, ''))}
                  placeholder="방명록에 표시될 이름 (2~12자)"
                />
                {nickname.length > 0 && (
                  <p
                    className={`mt-1 text-xs ${
                      !nickValid || nickCheck === 'taken' ? 'text-red-500' : 'text-emerald-600'
                    }`}
                  >
                    {!nickValid
                      ? '공백 없이 2~12자로 지어주세요.'
                      : nickCheck === 'taken'
                        ? '이미 사용 중인 닉네임이에요.'
                        : nickCheck === 'ok'
                          ? '사용할 수 있는 닉네임이에요.'
                          : '확인 중...'}
                  </p>
                )}
              </div>

              <div>
                <label className={LABEL}>성명 *</label>
                <input
                  className={INPUT}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="실명을 입력해주세요"
                />
                <p className="mt-1 text-xs text-stone-400">
                  본인 확인용이며 다른 손님에게는 닉네임만 보여요.
                </p>
              </div>

              <div>
                <label className={LABEL}>생년월일 *</label>
                <input
                  className={INPUT}
                  type="date"
                  value={birthDate}
                  max={new Date().toISOString().slice(0, 10)}
                  onChange={(e) => setBirthDate(e.target.value)}
                />
                <p className={`mt-1 text-xs ${underAge ? 'text-red-500' : 'text-stone-400'}`}>
                  만 {MIN_AGE}세 이상만 가입할 수 있어요.
                  {!underAge && age !== null ? ` (만 ${age}세)` : ''}
                </p>
              </div>

              <div>
                <label className={LABEL}>휴대폰번호 *</label>
                <input
                  className={INPUT}
                  type="tel"
                  inputMode="numeric"
                  value={phone}
                  onChange={(e) => setPhone(formatPhone(e.target.value))}
                  placeholder="010-0000-0000"
                />
                <p className={`mt-1 text-xs ${phone.length > 0 && !phoneValid ? 'text-red-500' : 'text-stone-400'}`}>
                  {phone.length > 0 && !phoneValid
                    ? '휴대폰번호를 정확히 입력해주세요.'
                    : '매칭 소식·예약 안내를 보내드릴 때 사용해요.'}
                </p>
              </div>

              <label className="flex items-center gap-3 cursor-pointer select-none">
                <input
                  type="checkbox"
                  className="w-5 h-5 accent-damso-500"
                  checked={pushOptIn}
                  onChange={(e) => setPushOptIn(e.target.checked)}
                />
                <span className="text-sm text-stone-700">
                  푸시 알림 받기 <span className="text-stone-400">(선택)</span>
                </span>
              </label>
              <label className="flex items-start gap-3 cursor-pointer select-none">
                <input
                  type="checkbox"
                  className="w-5 h-5 mt-0.5 accent-damso-500"
                  checked={agreed}
                  onChange={(e) => setAgreed(e.target.checked)}
                />
                <span className="text-sm text-stone-700 leading-relaxed">
                  이용약관 및 개인정보 수집·이용에 동의합니다.{' '}
                  <span className="text-stone-400">
                    (성명·생년월일·휴대폰번호를 회원 확인 및 연락 목적으로 수집하며,
                    다른 손님에게는 공개되지 않아요)
                  </span>
                </span>
              </label>
            </>
          )}

          {error && (
            <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-600">
              {error}
            </div>
          )}

          {/* 로그인은 버튼을 잠그지 않는다. 자동완성 때문에 state 가 비어 보여도 눌리게 두고,
              빈 값이면 위에서 안내 문구로 알려준다. */}
          <button
            type="submit"
            className={PRIMARY_BTN}
            disabled={busy || (mode === 'signup' && !canSignup)}
          >
            {busy ? '처리 중...' : mode === 'signup' ? '가입하기' : '로그인'}
          </button>
        </form>

        {/* ─── 간편로그인 ─────────────────────────── */}
        {/* 아이디·비밀번호가 기본 수단이라 그 아래에 둔다. */}
        {providers.length > 0 && (
          <div className="mt-6 space-y-2">
            <div className="flex items-center gap-3 pb-1 text-xs text-stone-400">
              <span className="flex-1 h-px bg-stone-200" />
              또는
              <span className="flex-1 h-px bg-stone-200" />
            </div>
            <SocialButtons providers={providers} />
          </div>
        )}
      </div>
    </div>
  );
}
