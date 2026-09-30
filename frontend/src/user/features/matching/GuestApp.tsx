import { useGuestSession } from './useGuestSession';
import GuestShell from './GuestShell';
import RegistrationPage from './RegistrationPage';
import ReloginPage from './ReloginPage';
import WaitingPage from './WaitingPage';
import MySeatPage from './MySeatPage';
import PreferencePage from './PreferencePage';
import MessagePage from './MessagePage';
import EndPage from './EndPage';
import { DEFAULT_GUEST_SETTINGS } from '../../../shared/types';

export default function GuestApp() {
  const { event, me, settings, loading, setRegistered, reset } = useGuestSession();
  const s = settings ?? DEFAULT_GUEST_SETTINGS;

  if (loading) {
    return (
      <GuestShell event={event}>
        <div className="text-center pt-16 text-stone-500">불러오는 중...</div>
      </GuestShell>
    );
  }

  if (!event) {
    return (
      <GuestShell event={null}>
        <div className="text-center pt-16 text-stone-500">진행 중인 이벤트가 없습니다.</div>
      </GuestShell>
    );
  }

  if (event.status === 'CLOSED') {
    return (
      <GuestShell event={event}>
        <EndPage me={me} onReset={reset} settings={s} />
      </GuestShell>
    );
  }

  // 아직 등록 안 했으면 등록 화면 (단, 이벤트가 REGISTRATION 단계여야 등록 가능)
  if (!me) {
    if (event.status !== 'REGISTRATION') {
      // 매칭 중에 세션 끊긴 손님 → 본명+연락처로 재로그인
      return (
        <GuestShell event={event}>
          <ReloginPage onLogin={setRegistered} />
        </GuestShell>
      );
    }
    return (
      <GuestShell event={event}>
        <RegistrationPage onRegistered={setRegistered} />
      </GuestShell>
    );
  }

  // 등록된 손님 — 이벤트 상태별 화면 분기
  return (
    <GuestShell event={event}>
      {event.status === 'REGISTRATION' && <WaitingPage me={me} settings={s} />}
      {event.status === 'ROUND_1' && <MySeatPage me={me} round={1} />}
      {event.status === 'ROUND_2' && <MySeatPage me={me} round={2} />}
      {event.status === 'ROUND_3' && <MySeatPage me={me} round={3} />}
      {event.status === 'PREFERENCE' && <PreferencePage me={me} settings={s} />}
      {event.status === 'ROUND_4' && <MySeatPage me={me} round={4} />}
      {event.status === 'MESSAGE' && <MessagePage me={me} settings={s} />}
    </GuestShell>
  );
}
