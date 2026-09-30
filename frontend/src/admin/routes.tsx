import { Route } from 'react-router-dom';
import AdminLayout from './layout/AdminLayout';
import AdminLoginPage from './pages/AdminLoginPage';
import AdminAccountPage from './pages/AdminAccountPage';
import DashboardPage from './features/event/DashboardPage';
import SeatsPage from './features/event/SeatsPage';
import CustomersPage from './features/event/CustomersPage';
import QrCardsPage from './features/event/QrCardsPage';
import QuestionsPage from './features/event/QuestionsPage';
import PreferencesPage from './features/event/PreferencesPage';
import MessagesPage from './features/event/MessagesPage';
import StatsPage from './features/event/StatsPage';
import DatingAdminPage from './features/dating/DatingAdminPage';
import DatingMatchesPage from './features/dating/DatingMatchesPage';
import DatingQuestionsPage from './features/dating/DatingQuestionsPage';
import MatchScoresPage from './features/dating/MatchScoresPage';
import DrinksPage from './features/venue/DrinksPage';
import CalendarPage from './features/venue/CalendarPage';
import AppGuestbookPage from './features/venue/AppGuestbookPage';
import GamesAdminPage from './features/game/GamesAdminPage';
import SettingsPage from './features/setting/SettingsPage';
import AppMembersPage from './features/member/AppMembersPage';

/** 설정 카탈로그의 앱 홈 화면 그룹명 (SettingsCatalog 의 G_HOME 과 같아야 한다). */
const APP_HOME_GROUP = '앱 홈 화면';
/** 기능 on/off 그룹명 (SettingsCatalog 의 G_FEATURE 와 같아야 한다). */
const APP_FEATURE_GROUP = '앱 기능 사용';
/** 셀렉팅 약속 안내 그룹명 (SettingsCatalog 의 G_SELECTING 과 같아야 한다). */
const SELECTING_GROUP = '담소 셀렉팅 약속';

/**
 * 관리자 콘솔 라우팅. 전부 /admin 아래이며, 서버에서도 같은 접두어를
 * AdminAuthInterceptor 가 통째로 잠근다.
 */
export const adminRoutes = (
  <>
    {/* 로그인 화면은 관리자 레이아웃(로그인 확인) 밖에 둔다 */}
    <Route path="/admin/login" element={<AdminLoginPage />} />
    <Route path="/admin" element={<AdminLayout />}>
      <Route index element={<DashboardPage />} />
      <Route path="seats" element={<SeatsPage />} />
      <Route path="customers" element={<CustomersPage />} />
      <Route path="qr-cards" element={<QrCardsPage />} />
      <Route path="questions" element={<QuestionsPage />} />
      <Route path="scores" element={<MatchScoresPage />} />
      <Route path="preferences" element={<PreferencesPage />} />
      <Route path="messages" element={<MessagesPage />} />
      <Route path="stats" element={<StatsPage />} />
      <Route
        path="settings"
        element={
          <SettingsPage exclude={[APP_HOME_GROUP, APP_FEATURE_GROUP, SELECTING_GROUP]} />
        }
      />
      <Route path="dating" element={<DatingAdminPage />} />
      <Route path="dating/matches" element={<DatingMatchesPage />} />
      <Route path="dating/questions" element={<DatingQuestionsPage />} />
      <Route
        path="dating/meeting"
        element={
          <SettingsPage
            title="약속 안내 설정"
            description="손님이 만남 약속을 잡을 때 보이는 장소·예약금 안내와 예약(결제) 링크예요. 장소는 손님이 고르지 않고 여기 적은 곳으로 고정됩니다."
            only={[SELECTING_GROUP]}
          />
        }
      />
      <Route
        path="app/home"
        element={
          <SettingsPage
            title="앱 홈 화면"
            description="손님 앱 홈의 영업여부·공지·성사 커플 공개 여부를 설정해요. 추천 한 잔과 술 메뉴는 '술 메뉴'에서 관리합니다."
            only={[APP_HOME_GROUP]}
          />
        }
      />
      <Route path="app/drinks" element={<DrinksPage />} />
      <Route path="app/calendar" element={<CalendarPage />} />
      <Route path="app/games" element={<GamesAdminPage />} />
      <Route
        path="app/features"
        element={
          <SettingsPage
            title="기능 사용"
            description="담소 소개팅·담소 셀렉팅을 필요할 때만 켜서 운영할 수 있어요. 끄면 손님 앱에서 탭이 사라집니다."
            only={[APP_FEATURE_GROUP]}
          />
        }
      />
      <Route path="app/guestbook" element={<AppGuestbookPage />} />
      <Route path="app/members" element={<AppMembersPage />} />
      <Route path="account" element={<AdminAccountPage />} />
    </Route>
  </>
);
