import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { ReactNode } from 'react';
import type { AppUser } from '../../../shared/types';
import { authApi, getToken, setToken } from '../../api/app';

/**
 * 앱 로그인 상태를 앱 전체(탭 셸)에서 공유한다.
 *  - 앱을 열 때 저장된 토큰으로 한 번 /auth/me 를 확인하고, 실패하면 토큰을 버린다.
 */
interface AppAuthValue {
  user: AppUser | null;
  loading: boolean;
  /** 로그인 결과 저장 (토큰 + 회원). */
  signIn: (token: string, user: AppUser) => void;
  signOut: () => Promise<void>;
  /** 회원 정보가 바뀐 뒤 화면 갱신용. */
  setUser: (user: AppUser) => void;
  refresh: () => Promise<void>;
}

const Ctx = createContext<AppAuthValue | null>(null);

export function AppAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<AppUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!getToken()) {
      setUserState(null);
      setLoading(false);
      return;
    }
    try {
      setUserState(await authApi.me());
    } catch {
      // 만료·삭제된 토큰이면 조용히 로그아웃 상태로 되돌린다.
      setToken(null);
      setUserState(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  const signIn = useCallback((token: string, next: AppUser) => {
    setToken(token);
    setUserState(next);
    setLoading(false);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // 서버 세션이 이미 없어도 로컬 로그아웃은 진행한다.
    }
    setToken(null);
    setUserState(null);
  }, []);

  const value = useMemo<AppAuthValue>(
    () => ({ user, loading, signIn, signOut, setUser: setUserState, refresh }),
    [user, loading, signIn, signOut, refresh],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAppAuth(): AppAuthValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAppAuth 는 AppAuthProvider 안에서만 쓸 수 있어요.');
  return v;
}
