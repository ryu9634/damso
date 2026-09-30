import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { notificationApi } from '../../api/app';
import { useAppAuth } from '../auth/useAppAuth';

/**
 * 안 읽은 알림 개수 (상단 종 아이콘 배지).
 *  - 로그인한 동안 주기적으로 확인하고, 새 알림이 늘어나면 브라우저 알림으로도 띄운다.
 *    (휴대폰 푸시는 서버에 발송 수단이 붙으면 그대로 이어진다)
 */
interface NotificationValue {
  unread: number;
  refresh: () => Promise<void>;
}

const Ctx = createContext<NotificationValue | null>(null);

/** 새 알림 확인 주기. */
const POLL_MS = 60_000;

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user } = useAppAuth();
  const [unread, setUnread] = useState(0);
  // 직전에 본 개수 — 늘어났을 때만 브라우저 알림을 띄운다.
  const seen = useRef<number | null>(null);

  const refresh = useCallback(async () => {
    if (!user) {
      setUnread(0);
      seen.current = null;
      return;
    }
    try {
      const count = await notificationApi.unreadCount();
      setUnread(count);
      if (seen.current !== null && count > seen.current) {
        notifyBrowser(count - seen.current);
      }
      seen.current = count;
    } catch {
      // 네트워크가 잠깐 끊긴 경우 — 다음 주기에 다시 확인한다.
    }
  }, [user]);

  useEffect(() => {
    refresh();
    if (!user) return;
    const timer = setInterval(refresh, POLL_MS);
    return () => clearInterval(timer);
  }, [refresh, user]);

  const value = useMemo<NotificationValue>(() => ({ unread, refresh }), [unread, refresh]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useNotifications(): NotificationValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useNotifications 는 NotificationProvider 안에서만 쓸 수 있어요.');
  return v;
}

/** 알림 권한을 이미 허용한 경우에만 조용히 띄운다. (여기서 권한을 새로 묻지는 않는다) */
function notifyBrowser(count: number) {
  try {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    new Notification('담소혼술바', {
      body: count === 1 ? '새 알림이 도착했어요.' : `새 알림 ${count}건이 도착했어요.`,
    });
  } catch {
    // 알림을 지원하지 않는 환경 — 앱 안 배지로만 알린다.
  }
}
