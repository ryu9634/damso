import { Routes } from 'react-router-dom';
import { userRoutes } from './user/routes';
import { adminRoutes } from './admin/routes';

/**
 * 두 앱을 한 번들에 담아 붙인다.
 *   /        손님 앱 (PWA)      → user/routes.tsx
 *   /admin   관리자 콘솔        → admin/routes.tsx
 *
 * 각 앱의 화면·인증 컨텍스트·API 클라이언트는 서로 만나지 않는다.
 * 여기는 어느 경로를 누가 맡는지만 보여 준다.
 */
export default function App() {
  return (
    <Routes>
      {userRoutes}
      {adminRoutes}
    </Routes>
  );
}
