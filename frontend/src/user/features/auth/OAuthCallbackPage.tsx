import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { authApi, appErrMsg } from '../../api/app';
import { useAppAuth } from './useAppAuth';

/**
 * 네이버/카카오 동의화면에서 돌아오는 자리 (/oauth/kakao, /oauth/naver).
 * 인가 코드를 서버로 넘겨 로그인을 마치고 마이페이지로 보낸다.
 */
export default function OAuthCallbackPage() {
  const { provider = '' } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { signIn } = useAppAuth();
  const [error, setError] = useState('');
  // React StrictMode 의 이중 실행으로 인가 코드가 두 번 소모되지 않게 막는다.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const code = params.get('code');
    const deniedMessage = params.get('error_description') || params.get('error');
    if (!code) {
      setError(deniedMessage ? '로그인이 취소됐어요.' : '인가 코드를 받지 못했어요.');
      return;
    }

    // 마이페이지에서 "계정 연결"로 출발했으면 새 계정을 만들지 않고 현재 계정에 붙인다.
    const link = sessionStorage.getItem('damso.oauth.mode') === 'link';
    sessionStorage.removeItem('damso.oauth.mode');

    authApi
      .oauth(
        provider,
        code,
        params.get('state'),
        `${window.location.origin}/oauth/${provider}`,
        link,
      )
      .then((result) => {
        signIn(result.token, result.user);
        navigate('/my', { replace: true });
      })
      .catch((e) => setError(appErrMsg(e, '로그인에 실패했어요.')));
  }, [provider, params, navigate, signIn]);

  return (
    <div className="max-w-md mx-auto px-4 pt-24 text-center">
      {error ? (
        <>
          <p className="text-sm text-stone-600">{error}</p>
          <button
            onClick={() => navigate('/my', { replace: true })}
            className="mt-5 rounded-full bg-damso-500 text-white px-6 py-2.5 text-sm font-semibold active:bg-damso-600"
          >
            마이페이지로
          </button>
        </>
      ) : (
        <p className="text-sm text-stone-500">로그인 중이에요...</p>
      )}
    </div>
  );
}
