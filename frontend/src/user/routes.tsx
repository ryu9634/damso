import { Route, Navigate, Outlet } from 'react-router-dom';
import AppShell from './layout/AppShell';
import FeatureGate from './layout/FeatureGate';
import RequireLogin from './layout/RequireLogin';
import HomePage from './features/home/HomePage';
import { AppFeatureProvider } from './features/home/useAppFeatures';
import LoginPage from './features/auth/LoginPage';
import OAuthCallbackPage from './features/auth/OAuthCallbackPage';
import MyPage from './features/auth/MyPage';
import { AppAuthProvider } from './features/auth/useAppAuth';
import DatingPage from './features/dating/DatingPage';
import LikesPage from './features/dating/LikesPage';
import GuestApp from './features/matching/GuestApp';
import GamesPage from './features/game/GamesPage';
import GameRoomPage from './features/game/GameRoomPage';
import GuestbookPage from './features/guestbook/GuestbookPage';
import NotificationsPage from './features/notification/NotificationsPage';
import { NotificationProvider } from './features/notification/useNotifications';

/**
 * 손님 앱(PWA) 라우팅. 하단 5탭 + 로그인 화면.
 *
 * <Routes> 는 자식으로 <Route> 만 받지만 프래그먼트는 펼쳐서 읽는다.
 * 그래서 App.tsx 가 이걸 그대로 끼워 넣을 수 있다.
 */
export const userRoutes = (
  <Route
    element={
      <AppAuthProvider>
        <AppFeatureProvider>
          <NotificationProvider>
            <Outlet />
          </NotificationProvider>
        </AppFeatureProvider>
      </AppAuthProvider>
    }
  >
    {/* 로그인 화면과 소셜 콜백은 로그인 전에도 열려야 한다 */}
    <Route path="/login" element={<LoginPage />} />
    <Route path="/oauth/:provider" element={<OAuthCallbackPage />} />

    <Route path="/" element={<AppShell />}>
      <Route index element={<HomePage />} />
      <Route path="guestbook" element={<GuestbookPage />} />
      {/* 관리자가 꺼둔 기능은 FeatureGate 가 막고, 필수정보를 안 채운 회원은 마이페이지로 안내한다 */}
      <Route
        path="matching"
        element={
          <FeatureGate feature="matching" title="담소 소개팅">
            <RequireLogin><GuestApp /></RequireLogin>
          </FeatureGate>
        }
      />
      <Route
        path="selecting"
        element={
          <FeatureGate feature="selecting" title="담소 셀렉팅">
            <RequireLogin><DatingPage /></RequireLogin>
          </FeatureGate>
        }
      />
      <Route path="likes" element={<RequireLogin><LikesPage /></RequireLogin>} />
      {/* 게임방 — 로그인 필수. 관리자가 꺼두면 화면에서 안내만 보여준다 */}
      <Route path="games" element={<RequireLogin><GamesPage /></RequireLogin>} />
      <Route path="games/:code" element={<RequireLogin><GameRoomPage /></RequireLogin>} />
      <Route path="notifications" element={<NotificationsPage />} />
      <Route path="my" element={<MyPage />} />
    </Route>

    {/* 기존 QR/링크 호환 — 로그인 게이트가 있는 탭 화면으로 넘긴다 */}
    <Route path="/m" element={<Navigate to="/matching" replace />} />
    <Route path="/dating" element={<Navigate to="/selecting" replace />} />
  </Route>
);
