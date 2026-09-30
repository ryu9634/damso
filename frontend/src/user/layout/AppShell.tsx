import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAppAuth } from '../features/auth/useAppAuth';
import { useAppFeatures } from '../features/home/useAppFeatures';
import { useNotifications } from '../features/notification/useNotifications';

/**
 * 손님용 앱 셸.
 *  - 앱은 로그인해야 쓸 수 있다. 비로그인 상태면 로그인 화면(/login)으로 보낸다.
 *  - 하단 탭(홈 · 방명록 · 담소 소개팅 · 담소 셀렉팅 · 마이페이지)으로 화면을 나눈다.
 *    관리자가 꺼둔 기능의 탭은 아예 보이지 않는다.
 *  - 상단 바에는 알림함으로 가는 종 아이콘을 둔다. (안 읽은 알림 수를 배지로 표시)
 *  - 폭은 모바일 기준(max-w-md)으로 잡고, 하단 탭 높이만큼 본문에 여백을 준다.
 */

type Tab = {
  to: string;
  label: string;
  icon: JSX.Element;
  end?: boolean;
  /** 관리자가 끌 수 있는 기능이면 어떤 기능인지 */
  feature?: 'matching' | 'selecting';
};

const ICON = 'w-6 h-6';

const TABS: Tab[] = [
  {
    to: '/',
    label: '홈',
    end: true,
    icon: (
      <svg className={ICON} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
        <path d="M3 10.5 12 3l9 7.5" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M5.5 9.5V20h13V9.5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    to: '/guestbook',
    label: '방명록',
    icon: (
      <svg className={ICON} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
        <path d="M5 4h11l3 3v13H5z" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M8 9h8M8 13h8M8 17h5" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/matching',
    label: '담소 소개팅',
    feature: 'matching',
    icon: (
      <svg className={ICON} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
        <circle cx="9" cy="8" r="3" />
        <circle cx="16" cy="9.5" r="2.5" />
        <path d="M3.5 19c.6-3 3-4.5 5.5-4.5S14 16 14.5 19" strokeLinecap="round" />
        <path d="M16 14.5c2 0 3.7 1.4 4.3 3.5" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/selecting',
    label: '담소 셀렉팅',
    feature: 'selecting',
    icon: (
      <svg className={ICON} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
        <path
          d="M12 20s-6.5-4.2-8.2-8A4.4 4.4 0 0 1 12 7.6 4.4 4.4 0 0 1 20.2 12c-1.7 3.8-8.2 8-8.2 8Z"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    to: '/my',
    label: '마이페이지',
    icon: (
      <svg className={ICON} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
        <circle cx="12" cy="8" r="3.5" />
        <path d="M4.5 20c.8-3.7 3.8-5.5 7.5-5.5s6.7 1.8 7.5 5.5" strokeLinecap="round" />
      </svg>
    ),
  },
];

export default function AppShell() {
  const { user, loading } = useAppAuth();
  const { features } = useAppFeatures();
  const { unread } = useNotifications();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center">
        <span className="text-sm text-stone-400">불러오는 중...</span>
      </div>
    );
  }

  // 로그인하지 않았으면 어느 탭으로 들어와도 로그인 화면으로 보낸다.
  // (로그인 후 원래 가려던 화면으로 되돌아온다)
  if (!user) {
    return (
      <Navigate
        to="/login"
        state={{ from: location.pathname + location.search }}
        replace
      />
    );
  }

  const tabs = TABS.filter((t) => !t.feature || features[t.feature].enabled);

  return (
    <>
      <div className="min-h-screen bg-stone-50 flex flex-col">
        {/* ─── 상단 브랜드 바 (모든 탭 공통) ───────────── */}
        <header className="sticky top-0 z-30 bg-white/95 backdrop-blur border-b border-stone-200">
          <div className="max-w-md mx-auto h-12 px-4 flex items-center justify-between gap-2">
            <span className="text-sm font-bold text-stone-900">담소혼술바</span>
            <div className="flex items-center gap-2 min-w-0">
              {user.nickname ? (
                <span className="text-xs text-stone-400 truncate max-w-[45vw]">{user.nickname}님</span>
              ) : (
                /* 아직 닉네임을 안 정한 회원 — 눌러서 바로 정하게 한다 */
                <Link to="/my" className="text-xs font-semibold text-damso-600 whitespace-nowrap">
                  닉네임 정하기 →
                </Link>
              )}
              <Link
                to="/notifications"
                aria-label="알림"
                className="relative -mr-1 p-1.5 rounded-full active:bg-stone-100"
              >
                <svg
                  className="w-5 h-5 text-stone-600"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={1.8}
                >
                  <path
                    d="M18 8.5a6 6 0 1 0-12 0c0 5-2 6.5-2 6.5h16s-2-1.5-2-6.5Z"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                  <path d="M10.5 19a2 2 0 0 0 3 0" strokeLinecap="round" />
                </svg>
                {unread > 0 && (
                  <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-damso-500 text-[10px] font-bold text-white flex items-center justify-center">
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </Link>
            </div>
          </div>
        </header>

        {/* 본문 — 하단 탭에 가리지 않도록 여백을 넉넉히 둔다 */}
        <main className="flex-1 pb-[76px]">
          <Outlet />
        </main>

        {/* ─── 하단 탭 ─────────────────────────────────── */}
        <nav
          className="fixed bottom-0 inset-x-0 z-40 bg-white border-t border-stone-200"
          style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
        >
          <div className="max-w-md mx-auto flex">
            {tabs.map((t) => (
              <NavLink
                key={t.to}
                to={t.to}
                end={t.end}
                className={({ isActive }) =>
                  `flex-1 flex flex-col items-center gap-1 py-2.5 text-[10px] font-semibold transition ${
                    isActive ? 'text-damso-600' : 'text-stone-400'
                  }`
                }
              >
                {t.icon}
                <span className="whitespace-nowrap">{t.label}</span>
              </NavLink>
            ))}
          </div>
        </nav>
      </div>
    </>
  );
}
