import { useEffect, useState } from 'react';
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { EventProvider } from '../hooks/EventProvider';
import EventSelector from './EventSelector';
import { adminAuthApi, getAdminToken, setAdminToken, type AdminAccount } from '../api/client';

type MenuItem = { to: string; label: string; end?: boolean };
type MenuGroup = { key: string; label: string; items: MenuItem[] };

// 상단 대메뉴(그룹) → 좌측 서브메뉴(하위 항목) 구조
const MENU: MenuGroup[] = [
  {
    key: 'onsite',
    label: '현장 소개팅',
    items: [
      { to: '/admin', label: '대시보드', end: true },
      { to: '/admin/customers', label: '손님 데이터' },
      { to: '/admin/qr-cards', label: '입장 QR' },
      { to: '/admin/seats', label: '자리 배치도' },
      { to: '/admin/messages', label: '메시지' },
      { to: '/admin/questions', label: '설문 문항' },
      { to: '/admin/scores', label: '매칭 점수' },
      { to: '/admin/preferences', label: '호감도 지목' },
      { to: '/admin/settings', label: '환경설정' },
    ],
  },
  {
    key: 'choice',
    label: '손님 선택 소개팅',
    items: [
      { to: '/admin/dating', label: '등록된 회원정보', end: true },
      { to: '/admin/dating/matches', label: '매칭된 회원목록' },
      { to: '/admin/dating/questions', label: '소개팅 질문 관리' },
      { to: '/admin/dating/meeting', label: '약속 안내 설정' },
    ],
  },
  {
    key: 'app',
    label: '손님 앱',
    items: [
      { to: '/admin/app/home', label: '홈 화면' },
      { to: '/admin/app/drinks', label: '술 메뉴' },
      { to: '/admin/app/calendar', label: '캘린더' },
      { to: '/admin/app/games', label: '게임방' },
      { to: '/admin/app/features', label: '기능 사용' },
      { to: '/admin/app/guestbook', label: '방명록' },
      { to: '/admin/app/members', label: '앱 회원' },
    ],
  },
  {
    key: 'stats',
    label: '총괄 통계',
    items: [{ to: '/admin/stats', label: '통계' }],
  },
];

function isItemActive(item: MenuItem, pathname: string) {
  if (item.end) return pathname === item.to;
  return pathname === item.to || pathname.startsWith(item.to + '/');
}

export default function AdminLayout() {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  const location = useLocation();
  const { pathname } = location;
  const navigate = useNavigate();

  // 로그인 확인 — 토큰이 없거나 만료면 로그인 화면으로 보낸다.
  const [admin, setAdmin] = useState<AdminAccount | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    if (!getAdminToken()) {
      setChecking(false);
      return;
    }
    adminAuthApi.me()
      .then(setAdmin)
      .catch(() => setAdmin(null))
      .finally(() => setChecking(false));
  }, []);

  const logout = async () => {
    try {
      await adminAuthApi.logout();
    } catch {
      // 세션이 이미 없어도 로컬 로그아웃은 진행한다.
    }
    setAdminToken(null);
    navigate('/admin/login', { replace: true });
  };

  if (checking) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center">
        <span className="text-sm text-stone-400">불러오는 중...</span>
      </div>
    );
  }

  if (!admin) {
    return (
      <Navigate
        to="/admin/login"
        state={{ from: location.pathname + location.search }}
        replace
      />
    );
  }

  const activeGroup =
    MENU.find((g) => g.items.some((it) => isItemActive(it, pathname))) ?? MENU[0];

  return (
    <EventProvider>
      <div className="min-h-screen flex flex-col bg-stone-50">
        {/* ─── 상단 대메뉴 ─────────────────────────────── */}
        <header className="sticky top-0 z-40 bg-stone-900 text-white">
          <div className="flex items-center gap-3 px-4 md:px-6 h-14">
            {/* 모바일 서브메뉴 토글 */}
            <button
              onClick={() => setOpen((v) => !v)}
              aria-label="메뉴 토글"
              className="md:hidden p-2 -ml-2 active:bg-stone-800 rounded"
            >
              <span className="block w-5 h-0.5 bg-white mb-1" />
              <span className="block w-5 h-0.5 bg-white mb-1" />
              <span className="block w-5 h-0.5 bg-white" />
            </button>

            <div className="font-bold whitespace-nowrap">
              담소혼술바 <span className="text-stone-400 font-normal text-sm">관리자</span>
            </div>

            {/* 대메뉴 탭 */}
            <nav className="flex-1 flex items-center gap-1 overflow-x-auto">
              {MENU.map((g) => {
                const active = g.key === activeGroup.key;
                return (
                  <button
                    key={g.key}
                    onClick={() => navigate(g.items[0].to)}
                    className={`px-3 md:px-4 py-1.5 rounded-md text-sm font-semibold whitespace-nowrap transition ${
                      active
                        ? 'bg-damso-500 text-white'
                        : 'text-stone-300 hover:bg-stone-800 hover:text-white'
                    }`}
                  >
                    {g.label}
                  </button>
                );
              })}
            </nav>

            {/* 계정 */}
            <div className="flex items-center gap-2 shrink-0">
              <NavLink
                to="/admin/account"
                className="hidden sm:inline text-xs text-stone-400 hover:text-white"
              >
                {admin.username}
              </NavLink>
              <button
                onClick={logout}
                className="px-3 py-1.5 rounded-md text-xs font-semibold text-stone-300 hover:bg-stone-800 hover:text-white"
              >
                로그아웃
              </button>
            </div>
          </div>

          {/* 기본 비밀번호를 그대로 쓰고 있으면 알려준다 */}
          {admin.initialPassword && (
            <div className="bg-amber-100 text-amber-900 text-xs px-4 md:px-6 py-2">
              기본 비밀번호(admin)를 사용 중이에요. 다른 사람이 관리자 화면에 들어올 수 있으니{' '}
              <NavLink to="/admin/account" className="font-bold underline">
                비밀번호를 바꿔주세요.
              </NavLink>
            </div>
          )}
        </header>

        <div className="flex-1 md:flex">
          {/* 모바일 backdrop */}
          {open && (
            <div
              onClick={close}
              className="md:hidden fixed inset-0 bg-black/50 z-40"
            />
          )}

          {/* ─── 좌측 서브메뉴 ───────────────────────────── */}
          {/* 모바일: 좌측 드로어(fixed) / 데스크톱: 헤더 아래 sticky 고정 */}
          <aside
            className={`fixed inset-y-0 left-0 z-50 w-60 bg-white border-r border-stone-200 flex flex-col transform transition-transform md:sticky md:inset-y-auto md:top-14 md:z-30 md:h-[calc(100vh-3.5rem)] md:self-start md:transform-none ${
              open ? 'translate-x-0' : '-translate-x-full'
            }`}
          >
            <div className="md:hidden flex items-center justify-between px-4 py-3 border-b border-stone-200">
              <div className="text-sm font-bold text-stone-800">{activeGroup.label}</div>
              <button onClick={close} className="text-stone-400 px-2 py-1">✕</button>
            </div>
            <div className="hidden md:block px-5 pt-5 pb-2 text-xs font-semibold uppercase tracking-wide text-stone-400">
              {activeGroup.label}
            </div>
            <nav className="flex-1 px-3 py-3 space-y-1 overflow-y-auto">
              {activeGroup.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  onClick={close}
                  className={({ isActive }) =>
                    `block px-4 py-2.5 rounded-md text-sm font-medium transition ${
                      isActive
                        ? 'bg-damso-50 text-damso-700'
                        : 'text-stone-600 hover:bg-stone-100 hover:text-stone-900'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
            {/* 선택된 이벤트: 현장 소개팅일 때만 좌측 하단에 표시 */}
            {activeGroup.key === 'onsite' && (
              <div className="mt-auto border-t border-stone-200 p-3">
                <div className="bg-stone-900 rounded-lg p-2">
                  <EventSelector />
                </div>
              </div>
            )}
          </aside>

          {/* ─── 컨텐츠 영역 ─────────────────────────────── */}
          <main className="flex-1 min-w-0">
            <div className="max-w-6xl mx-auto p-4 md:p-8">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </EventProvider>
  );
}