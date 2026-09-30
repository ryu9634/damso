import { useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { adminAuthApi, setAdminToken } from '../api/client';

/**
 * 관리자 로그인 화면.
 *  - 관리자 화면(/admin/**)은 로그인해야 열리고, API 도 서버에서 함께 막혀 있다.
 *  - 첫 실행 시 admin / admin 계정이 만들어진다. 로그인 후 비밀번호를 바꾸는 걸 권한다.
 */
export default function AdminLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/admin';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  /**
   * 입력칸 참조. 비밀번호 관리자가 값을 직접 채우면 React 의 onChange 가 걸리지 않아
   * 화면에는 채워져 보여도 state 는 비어 있다. 제출할 때는 입력칸의 실제 값을 먼저 본다.
   */
  const usernameRef = useRef<HTMLInputElement | null>(null);
  const passwordRef = useRef<HTMLInputElement | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const id = (usernameRef.current?.value ?? username).trim();
    const pw = passwordRef.current?.value ?? password;
    if (!id || !pw) {
      setError('아이디와 비밀번호를 입력하세요.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const { token } = await adminAuthApi.login(id, pw);
      setAdminToken(token);
      navigate(from, { replace: true });
    } catch (err: any) {
      setError(err?.response?.data?.message ?? '로그인하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-stone-900 flex items-center justify-center px-4">
      <form
        onSubmit={submit}
        className="w-full max-w-sm bg-white rounded-xl shadow-lg p-8"
      >
        <div className="text-center">
          <div className="text-lg font-bold text-stone-900">
            담소혼술바 <span className="text-stone-400 font-normal text-sm">관리자</span>
          </div>
          <p className="mt-1 text-sm text-stone-500">운영 화면에 로그인하세요.</p>
        </div>

        <div className="mt-6 space-y-4">
          <label className="block">
            <span className="text-sm font-medium text-stone-700">아이디</span>
            <input
              ref={usernameRef}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoCapitalize="none"
              autoCorrect="off"
              autoComplete="username"
              autoFocus
              className="mt-1 w-full border border-stone-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-damso-300"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-stone-700">비밀번호</span>
            <input
              ref={passwordRef}
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full border border-stone-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-damso-300"
            />
          </label>

          {error && (
            <div className="rounded-md bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-md bg-damso-500 text-white py-2.5 text-sm font-semibold hover:bg-damso-600 disabled:bg-stone-300"
          >
            {busy ? '로그인 중...' : '로그인'}
          </button>
        </div>
      </form>
    </div>
  );
}
