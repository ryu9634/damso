import { useCallback, useEffect, useState } from 'react';
import { guestApi } from './guest';
import type { Customer, Event, GuestSettings } from '../../../shared/types';

const KEY_CUSTOMER = 'damso.guest.customerId';
const KEY_EVENT = 'damso.guest.eventId';

export function useGuestSession() {
  const [event, setEvent] = useState<Event | null>(null);
  const [me, setMe] = useState<Customer | null>(null);
  const [settings, setSettings] = useState<GuestSettings | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const ev = await guestApi.event();
      setEvent(ev);

      // 로그인한 회원이면 계정으로 이번 이벤트 등록을 먼저 찾는다.
      // (기기를 바꿔도 다시 입력할 필요가 없다)
      try {
        const mine = await guestApi.myRegistration();
        if (mine) {
          setMe(mine);
          localStorage.setItem(KEY_CUSTOMER, String(mine.id));
          localStorage.setItem(KEY_EVENT, String(mine.eventId));
          return;
        }
        // 로그인은 됐는데 이번 이벤트에 등록이 없다 — 기기에 남아 있는 예전(다른 사람) 세션은
        // 이 회원 것이 아니므로 쓰지 않는다. 같은 폰으로 여러 명이 들어와도 남의 자리를 보지 않는다.
        localStorage.removeItem(KEY_CUSTOMER);
        localStorage.removeItem(KEY_EVENT);
        setMe(null);
        return;
      } catch {
        // 비로그인·토큰 만료 — 아래 localStorage 방식으로 넘어간다.
      }

      const storedEvId = Number(localStorage.getItem(KEY_EVENT));
      const storedCustId = Number(localStorage.getItem(KEY_CUSTOMER));

      // 이벤트가 바뀌면 기존 등록은 무효
      if (storedEvId && storedEvId !== ev.id) {
        localStorage.removeItem(KEY_CUSTOMER);
        localStorage.removeItem(KEY_EVENT);
        setMe(null);
        return;
      }
      if (storedCustId) {
        try {
          const c = await guestApi.me(storedCustId);
          if (c.eventId === ev.id) {
            setMe(c);
            localStorage.setItem(KEY_EVENT, String(ev.id));
          } else {
            localStorage.removeItem(KEY_CUSTOMER);
            localStorage.removeItem(KEY_EVENT);
            setMe(null);
          }
        } catch {
          localStorage.removeItem(KEY_CUSTOMER);
          localStorage.removeItem(KEY_EVENT);
          setMe(null);
        }
      } else {
        setMe(null);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 4000);
    return () => clearInterval(t);
  }, [refresh]);

  // 손님 화면 문구/설정 — 한 번만 불러온다 (관리자가 바꾸면 새로고침 시 반영)
  useEffect(() => {
    guestApi.settings().then(setSettings).catch(() => setSettings(null));
  }, []);

  const setRegistered = (c: Customer) => {
    setMe(c);
    localStorage.setItem(KEY_CUSTOMER, String(c.id));
    localStorage.setItem(KEY_EVENT, String(c.eventId));
  };

  const reset = () => {
    localStorage.removeItem(KEY_CUSTOMER);
    localStorage.removeItem(KEY_EVENT);
    setMe(null);
  };

  return { event, me, settings, loading, refresh, setRegistered, reset };
}
