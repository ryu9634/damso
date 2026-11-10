import axios from 'axios';
import type {
  AdminAppUser,
  AdminGuestbookEntry,
  CalendarEntry,
  CalendarEntryType,
  GameContent,
  GameRoom,
  GameType,
  Customer,
  CustomerStatus,
  Dashboard,
  DatingAdminProfile,
  DatingLikeRow,
  DatingPairNote,
  DatingQuestion,
  DatingQuestionType,
  DatingStatus,
  DrinkItem,
  Event,
  EventStatus,
  Gender,
  MatchScoreMatrix,
  Message,
  Preference,
  SeatAssignment,
  SettingItem,
  Stats,
  ValueQuestion,
} from '../../shared/types';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

/** 관리자 로그인 토큰 (손님 앱 토큰과 별도로 보관한다). */
const ADMIN_TOKEN_KEY = 'damso.admin.token';

export function getAdminToken(): string | null {
  return localStorage.getItem(ADMIN_TOKEN_KEY);
}

export function setAdminToken(token: string | null) {
  if (token) localStorage.setItem(ADMIN_TOKEN_KEY, token);
  else localStorage.removeItem(ADMIN_TOKEN_KEY);
}

api.interceptors.request.use((config) => {
  const token = getAdminToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 토큰이 만료되면 화면 어디에 있든 로그인 화면으로 되돌린다.
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error?.response?.status;
    const url: string = error?.config?.url ?? '';
    if (status === 401 && !url.includes('/admin/auth/login')) {
      setAdminToken(null);
      if (!window.location.pathname.startsWith('/admin/login')) {
        window.location.assign('/admin/login');
      }
    }
    return Promise.reject(error);
  },
);

export interface AdminAccount {
  id: number;
  username: string;
  /** 기본 비밀번호(admin)를 아직 안 바꿨는지 */
  initialPassword: boolean;
  lastLoginAt: string | null;
}

export const adminAuthApi = {
  login: (username: string, password: string) =>
    api.post<{ token: string; admin: AdminAccount }>('/admin/auth/login', { username, password })
      .then((r) => r.data),

  me: () => api.get<AdminAccount>('/admin/auth/me').then((r) => r.data),

  logout: () => api.post('/admin/auth/logout').then((r) => r.data),

  changePassword: (currentPassword: string, newPassword: string) =>
    api.put<AdminAccount>('/admin/auth/password', { currentPassword, newPassword })
      .then((r) => r.data),
};

export const eventApi = {
  list: () => api.get<Event[]>('/admin/events').then((r) => r.data),
  current: () => api.get<Event>('/admin/events/current').then((r) => r.data),
  startNew: (name?: string) =>
    api.post<Event>('/admin/events/start-new', { name }).then((r) => r.data),
  clear: (id: number) =>
    api.post(`/admin/events/${id}/clear`).then((r) => r.data),
  updateStatus: (id: number, status: EventStatus) =>
    api.patch<Event>(`/admin/events/${id}/status`, { status }).then((r) => r.data),
};

export const customerApi = {
  list: (eventId: number) =>
    api.get<Customer[]>('/admin/customers', { params: { eventId } }).then((r) => r.data),
  create: (req: {
    eventId: number;
    nickname: string;
    realName: string;
    phone: string;
    gender: Gender;
    mbti: string;
    valuesAnswers: string;
    seatNumber: number;
  }) => api.post<Customer>('/admin/customers', req).then((r) => r.data),
  update: (
    id: number,
    req: {
      nickname: string;
      realName: string;
      phone: string;
      gender: Gender;
      mbti: string;
      valuesAnswers: string;
      seatNumber: number;
    },
  ) => api.put<Customer>(`/admin/customers/${id}`, req).then((r) => r.data),
  setStatus: (id: number, status: CustomerStatus) =>
    api.patch<Customer>(`/admin/customers/${id}/status`, { status }).then((r) => r.data),
  delete: (id: number) => api.delete(`/admin/customers/${id}`).then((r) => r.data),
};

export const seatApi = {
  list: (eventId: number, round: number) =>
    api.get<SeatAssignment[]>('/admin/seats', { params: { eventId, round } })
      .then((r) => r.data),
  assignRound1: (eventId: number) =>
    api.post<SeatAssignment[]>('/admin/seats/round1/auto', null, { params: { eventId } })
      .then((r) => r.data),
  assignRound2: (eventId: number) =>
    api.post<SeatAssignment[]>('/admin/seats/round2/auto', null, { params: { eventId } })
      .then((r) => r.data),
  assignRound3: (eventId: number) =>
    api.post<SeatAssignment[]>('/admin/seats/round3/auto', null, { params: { eventId } })
      .then((r) => r.data),
  assignRound4: (eventId: number) =>
    api.post<SeatAssignment[]>('/admin/seats/round4/auto', null, { params: { eventId } })
      .then((r) => r.data),
  manual: (req: {
    eventId: number;
    round: number;
    seatNumber: number;
    slotIndex: number;
    customerId: number;
  }) => api.put<SeatAssignment>('/admin/seats/manual', req).then((r) => r.data),
  remove: (eventId: number, round: number, customerId: number) =>
    api.delete('/admin/seats', { params: { eventId, round, customerId } })
      .then((r) => r.data),
  clearRound: (eventId: number, round: number) =>
    api.delete('/admin/seats/round', { params: { eventId, round } })
      .then((r) => r.data),
};

export const dashboardApi = {
  get: (eventId: number) =>
    api.get<Dashboard>('/admin/dashboard', { params: { eventId } }).then((r) => r.data),
};

export const statsApi = {
  // from/to: 'yyyy-MM-dd' (미지정 시 전체 기간)
  get: (from?: string, to?: string) =>
    api.get<Stats>('/admin/stats', { params: { from: from || undefined, to: to || undefined } })
      .then((r) => r.data),
};

export const settingsApi = {
  list: () => api.get<SettingItem[]>('/admin/settings').then((r) => r.data),
  update: (values: Record<string, string>) =>
    api.put<SettingItem[]>('/admin/settings', values).then((r) => r.data),
  resetDefault: () =>
    api.post<SettingItem[]>('/admin/settings/reset-default').then((r) => r.data),
};

export const preferenceApi = {
  list: (eventId: number) =>
    api.get<Preference[]>('/admin/preferences', { params: { eventId } }).then((r) => r.data),
};

export const messageApi = {
  list: (eventId: number) =>
    api.get<Message[]>('/admin/messages', { params: { eventId } }).then((r) => r.data),
};

export const matchScoreApi = {
  matrix: (eventId: number) =>
    api.get<MatchScoreMatrix>('/admin/match-scores', { params: { eventId } })
      .then((r) => r.data),
};

export interface DatingProfileUpdateRequest {
  name: string;
  contact: string;
  birthYear: number;
  gender: Gender;
  photoUrl?: string | null;
  answers: { questionId: number; value: string }[];
}

export const datingAdminApi = {
  /** includeDeleted: 휴지통(소프트 삭제된 회원)까지 함께 조회 */
  list: (includeDeleted = false) =>
    api
      .get<DatingAdminProfile[]>('/admin/dating/profiles', { params: { includeDeleted } })
      .then((r) => r.data),

  update: (id: number, req: DatingProfileUpdateRequest) =>
    api.put(`/admin/dating/profiles/${id}`, req).then((r) => r.data),

  setStatus: (id: number, status: DatingStatus) =>
    api.patch(`/admin/dating/profiles/${id}/status`, { status }).then((r) => r.data),

  setMemo: (id: number, memo: string) =>
    api.patch(`/admin/dating/profiles/${id}/memo`, { memo }).then((r) => r.data),

  /** 매장에서 직접 얼굴을 확인했는지 표시 — 손님 카드에 '매장 확인' 배지가 붙는다. */
  setVerified: (id: number, verified: boolean) =>
    api.patch(`/admin/dating/profiles/${id}/verify`, { verified }).then((r) => r.data),

  /** 소프트 삭제 (휴지통으로) */
  delete: (id: number) =>
    api.delete(`/admin/dating/profiles/${id}`).then((r) => r.data),

  restore: (id: number) =>
    api.post(`/admin/dating/profiles/${id}/restore`).then((r) => r.data),

  /** 완전 삭제 — 복구 불가 */
  hardDelete: (id: number) =>
    api.delete(`/admin/dating/profiles/${id}/hard`).then((r) => r.data),

  bulkStatus: (ids: number[], status: DatingStatus) =>
    api.post<{ updated: number }>('/admin/dating/profiles/bulk/status', { ids, status })
      .then((r) => r.data),
  bulkDelete: (ids: number[]) =>
    api.post<{ updated: number }>('/admin/dating/profiles/bulk/delete', { ids })
      .then((r) => r.data),
  bulkRestore: (ids: number[]) =>
    api.post<{ updated: number }>('/admin/dating/profiles/bulk/restore', { ids })
      .then((r) => r.data),
  bulkHardDelete: (ids: number[]) =>
    api.post<{ updated: number }>('/admin/dating/profiles/bulk/hard-delete', { ids })
      .then((r) => r.data),

  /** 선택(찜) 내역 — 누가 누구를 언제 선택했는지, 최신순 */
  likes: () =>
    api.get<DatingLikeRow[]>('/admin/dating/likes').then((r) => r.data),

  /** 페어 메모 + 데이트 약속 전체 (약속 캘린더용) */
  pairNotes: () =>
    api.get<DatingPairNote[]>('/admin/dating/pairs/notes').then((r) => r.data),

  /**
   * 페어 메모 + 데이트 약속 저장. 두 id 순서는 상관없다.
   * 셋 다 비우면(빈 메모 + null 날짜/시간) 기록이 삭제된다.
   */
  savePairNote: (
    aId: number,
    bId: number,
    body: { memo: string; dateDay: string | null; dateHour: number | null },
  ) =>
    api.put(`/admin/dating/pairs/${aId}/${bId}/note`, body).then((r) => r.data),
};

export interface DatingQuestionRequest {
  label: string;
  type: DatingQuestionType;
  options?: string | null; // SELECT 일 때 줄바꿈 구분 선택지
  required: boolean;
}

export const datingQuestionApi = {
  list: () =>
    api.get<DatingQuestion[]>('/admin/dating/questions').then((r) => r.data),
  create: (req: DatingQuestionRequest) =>
    api.post<DatingQuestion>('/admin/dating/questions', req).then((r) => r.data),
  update: (id: number, req: DatingQuestionRequest) =>
    api.put<DatingQuestion>(`/admin/dating/questions/${id}`, req).then((r) => r.data),
  delete: (id: number) =>
    api.delete(`/admin/dating/questions/${id}`).then((r) => r.data),
  move: (id: number, direction: 'up' | 'down') =>
    api.post<DatingQuestion[]>(`/admin/dating/questions/${id}/move`, null, { params: { direction } })
      .then((r) => r.data),
};

export const hostApi = {
  info: () =>
    api.get<{ lanIps: string[] }>('/admin/host-info').then((r) => r.data),
};

export const questionApi = {
  list: () => api.get<ValueQuestion[]>('/admin/questions').then((r) => r.data),
  create: (req: { label: string; optionA: string; optionB: string }) =>
    api.post<ValueQuestion>('/admin/questions', req).then((r) => r.data),
  update: (id: number, req: { label: string; optionA: string; optionB: string }) =>
    api.put<ValueQuestion>(`/admin/questions/${id}`, req).then((r) => r.data),
  delete: (id: number) =>
    api.delete(`/admin/questions/${id}`).then((r) => r.data),
  move: (id: number, direction: 'up' | 'down') =>
    api.post<ValueQuestion[]>(`/admin/questions/${id}/move`, null, { params: { direction } })
      .then((r) => r.data),
};

/** 관리자가 고칠 수 있는 회원 정보 (손님 마이페이지와 같은 항목). */
export interface AdminUserProfileInput {
  nickname: string;
  name: string;
  /** yyyy-MM-dd */
  birthDate: string;
  /** 하이픈을 뺀 숫자만 */
  phone: string;
}

/** 초기화 직후 한 번만 받아볼 수 있는 임시 비밀번호. */
export interface TempPassword {
  loginId: string;
  password: string;
}

/** 앱(손님 모바일) 회원 · 방명록 관리. */
export const adminAppApi = {
  users: () => api.get<AdminAppUser[]>('/admin/app/users').then((r) => r.data),

  updateUser: (id: number, req: AdminUserProfileInput) =>
    api.put<AdminAppUser>(`/admin/app/users/${id}`, req).then((r) => r.data),

  /** 비밀번호 초기화 — 임시 비밀번호를 만들어 돌려준다. 다시 볼 수 없다. */
  resetPassword: (id: number) =>
    api.post<TempPassword>(`/admin/app/users/${id}/password/reset`).then((r) => r.data),

  removeUser: (id: number) =>
    api.delete(`/admin/app/users/${id}`).then((r) => r.data),

  guestbook: () =>
    api.get<AdminGuestbookEntry[]>('/admin/app/guestbook').then((r) => r.data),
  setGuestbookHidden: (id: number, hidden: boolean) =>
    api.patch<AdminGuestbookEntry>(`/admin/app/guestbook/${id}/hidden`, { hidden })
      .then((r) => r.data),
  deleteGuestbook: (id: number) =>
    api.delete(`/admin/app/guestbook/${id}`).then((r) => r.data),
};

/** 앱 홈에 보여줄 술 · 칵테일 메뉴 관리. */
export interface DrinkItemInput {
  emoji: string | null;
  name: string;
  description: string | null;
  imageUrl: string | null;
  visible: boolean;
  recommended: boolean;
  sortOrder?: number;
}

export const drinkApi = {
  list: () => api.get<DrinkItem[]>('/admin/drinks').then((r) => r.data),

  create: (req: DrinkItemInput) =>
    api.post<DrinkItem>('/admin/drinks', req).then((r) => r.data),

  update: (id: number, req: DrinkItemInput) =>
    api.put<DrinkItem>(`/admin/drinks/${id}`, req).then((r) => r.data),

  remove: (id: number) => api.delete(`/admin/drinks/${id}`).then((r) => r.data),

  /** 화면에 보이는 순서대로 id 를 보낸다. */
  reorder: (idsInOrder: number[]) =>
    api.put<DrinkItem[]>('/admin/drinks/order', idsInOrder).then((r) => r.data),

  uploadImage: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api
      .post<{ url: string }>('/admin/drinks/image', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.url);
  },
};

/** 가게 캘린더 (행사 · 휴일). */
export interface CalendarEntryInput {
  type: CalendarEntryType;
  title: string;
  description: string | null;
  /** yyyy-MM-dd */
  startDay: string;
  /** 하루짜리면 null */
  endDay: string | null;
  timeText: string | null;
  emoji: string | null;
}

export const calendarApi = {
  list: () => api.get<CalendarEntry[]>('/admin/calendar').then((r) => r.data),

  create: (req: CalendarEntryInput) =>
    api.post<CalendarEntry>('/admin/calendar', req).then((r) => r.data),

  update: (id: number, req: CalendarEntryInput) =>
    api.put<CalendarEntry>(`/admin/calendar/${id}`, req).then((r) => r.data),

  remove: (id: number) => api.delete(`/admin/calendar/${id}`).then((r) => r.data),
};

/** 게임방 — 문항 관리와 열린 방 확인. */
export interface GameContentInput {
  type: GameType;
  optionA: string;
  optionB: string | null;
  visible: boolean;
  sortOrder?: number;
}

export const adminGameApi = {
  contents: () => api.get<GameContent[]>('/admin/games/contents').then((r) => r.data),

  createContent: (req: GameContentInput) =>
    api.post<GameContent>('/admin/games/contents', req).then((r) => r.data),

  updateContent: (id: number, req: GameContentInput) =>
    api.put<GameContent>(`/admin/games/contents/${id}`, req).then((r) => r.data),

  removeContent: (id: number) =>
    api.delete(`/admin/games/contents/${id}`).then((r) => r.data),

  rooms: () => api.get<GameRoom[]>('/admin/games/rooms').then((r) => r.data),

  closeRoom: (code: string) =>
    api.post(`/admin/games/rooms/${code}/close`).then((r) => r.data),
};
