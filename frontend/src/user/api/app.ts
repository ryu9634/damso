import axios from 'axios';
import type {
  AppFeatures,
  AppHome,
  AppNotification,
  AppWeek,
  AppUser,
  AuthProviderInfo,
  AuthResult,
  GuestbookEntry,
  PushInfo,
} from '../../shared/types';

/**
 * 앱(손님 모바일) API.
 *  - 로그인 토큰은 localStorage 에 두고 매 요청 Authorization 헤더로 붙인다.
 */

const TOKEN_KEY = 'damso.app.token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export interface SignupInput {
  loginId: string;
  password: string;
  /** 공개 이름 */
  nickname: string;
  name: string;
  /** yyyy-MM-dd */
  birthDate: string;
  /** 하이픈을 뺀 숫자만. 필수 항목이다. */
  phone: string;
  pushOptIn: boolean;
  agreedPrivacy: boolean;
}

export interface ProfileInput {
  nickname: string;
  name: string;
  birthDate: string;
  phone: string;
}

export const authApi = {
  providers: () =>
    api.get<AuthProviderInfo[]>('/auth/providers').then((r) => r.data),

  signup: (req: SignupInput) =>
    api.post<AuthResult>('/auth/signup', req).then((r) => r.data),

  login: (loginId: string, password: string) =>
    api.post<AuthResult>('/auth/login', { loginId, password }).then((r) => r.data),

  /** 아이디 사용 가능 여부. */
  checkId: (loginId: string) =>
    api.get<{ available: boolean }>('/auth/check-id', { params: { loginId } })
      .then((r) => r.data.available),

  /** 닉네임 사용 가능 여부. */
  checkNickname: (nickname: string) =>
    api.get<{ available: boolean }>('/auth/check-nickname', { params: { nickname } })
      .then((r) => r.data.available),

  /**
   * 소셜 동의화면에서 돌아온 인가 코드 처리.
   * link=true 면 새 계정을 만들지 않고 현재 로그인한 계정에 연결한다.
   */
  oauth: (
    provider: string,
    code: string,
    state: string | null,
    redirectUri: string,
    link = false,
  ) =>
    api
      .post<AuthResult>(`/auth/oauth/${provider}`, { code, state, redirectUri, link })
      .then((r) => r.data),

  /** 간편로그인 연결 해제. */
  unlinkSocial: (provider: string) =>
    api.delete<AppUser>(`/auth/me/social/${provider}`).then((r) => r.data),

  /** 비밀번호 변경. 아이디가 없던 간편로그인 계정은 loginId 도 함께 만든다. */
  changePassword: (req: {
    currentPassword?: string;
    newPassword: string;
    loginId?: string;
  }) => api.put<AppUser>('/auth/me/password', req).then((r) => r.data),

  me: () => api.get<AppUser>('/auth/me').then((r) => r.data),

  updateMe: (req: ProfileInput) =>
    api.put<AppUser>('/auth/me', req).then((r) => r.data),

  setPush: (pushOptIn: boolean, pushToken?: string | null) =>
    api.put<AppUser>('/auth/me/push', { pushOptIn, pushToken: pushToken ?? null })
      .then((r) => r.data),

  logout: () => api.post('/auth/logout').then((r) => r.data),

  withdraw: () => api.delete('/auth/me').then((r) => r.data),
};

export const appApi = {
  home: () => api.get<AppHome>('/app/home').then((r) => r.data),

  /** 지금 쓸 수 있는 기능 (담소 소개팅 · 담소 셀렉팅). */
  features: () => api.get<AppFeatures>('/app/features').then((r) => r.data),

  /** 이번 주(월~일) 캘린더 — 가게 행사·휴일 + 내 만남 약속. */
  week: () => api.get<AppWeek>('/app/calendar/week').then((r) => r.data),

  guestbook: () => api.get<GuestbookEntry[]>('/app/guestbook').then((r) => r.data),

  writeGuestbook: (content: string) =>
    api.post<GuestbookEntry>('/app/guestbook', { content }).then((r) => r.data),

  deleteGuestbook: (id: number) =>
    api.delete(`/app/guestbook/${id}`).then((r) => r.data),
};

/** 알림함 — 좋아요·만남 약속 소식이 쌓인다. */
export const notificationApi = {
  list: () => api.get<AppNotification[]>('/app/notifications').then((r) => r.data),

  unreadCount: () =>
    api.get<{ count: number }>('/app/notifications/unread-count').then((r) => r.data.count),

  readAll: () => api.post('/app/notifications/read-all').then((r) => r.data),
};

/** 웹 푸시 구독 — 앱을 닫아둔 사이에 온 소식을 기기로 받는다. */
export const pushApi = {
  info: () => api.get<PushInfo>('/app/push').then((r) => r.data),

  subscribe: (sub: { endpoint: string; p256dh: string; auth: string }) =>
    api.post<{ devices: number }>('/app/push/subscribe', sub).then((r) => r.data.devices),

  unsubscribe: (endpoint?: string) =>
    api.post<{ devices: number }>('/app/push/unsubscribe', { endpoint: endpoint ?? null })
      .then((r) => r.data.devices),
};

/** 서버가 내려준 안내 문구를 꺼낸다. */
export function appErrMsg(e: any, fallback: string): string {
  return e?.response?.data?.message || fallback;
}
