import axios from 'axios';
import { getToken } from '../../api/app';
import type {
  DatingLikeCard,
  DatingMeeting,
  DatingProfileCard,
  DatingQuestion,
  DatingSelf,
  Gender,
  MeetingGuide,
  MeetingSlot,
} from '../../../shared/types';

const api = axios.create({ baseURL: '/api/dating' });

// 프로필 등록·열람·찜은 모두 로그인한 회원만 할 수 있다.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export interface DatingAnswerInput {
  questionId: number;
  value: string;
}

export interface DatingProfileInput {
  name: string;
  contact: string;
  birthYear: number;
  gender: Gender;
  photoUrl?: string | null;
  answers: DatingAnswerInput[];
  agreedPrivacy: boolean;
}

/** 본인 정보 수정 — 성별은 서버가 기존 값을 유지하므로 보내지 않는다. */
export interface DatingSelfUpdateInput {
  name: string;
  contact: string;
  birthYear: number;
  photoUrl?: string | null;
  answers: DatingAnswerInput[];
}

export const datingApi = {
  questions: () =>
    api.get<DatingQuestion[]>('/questions').then((r) => r.data),

  createProfile: (req: DatingProfileInput) =>
    api.post<DatingSelf>('/profiles', req, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  /** 로그인한 회원의 프로필. 아직 등록 전이면 null. */
  me: () => api.get<DatingSelf | ''>('/profiles/me').then((r) => r.data || null),

  /** 계정 도입 전에 등록한 프로필을 이름+연락처로 확인해 지금 계정에 연결. */
  link: (name: string, contact: string) =>
    api.post<DatingSelf>('/profiles/link', { name, contact }, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  getSelf: (id: number) =>
    api.get<DatingSelf>(`/profiles/${id}`).then((r) => r.data),

  updateProfile: (id: number, req: DatingSelfUpdateInput) =>
    api.put<DatingSelf>(`/profiles/${id}`, req, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  browse: (id: number) =>
    api.get<DatingProfileCard[]>(`/profiles/${id}/browse`).then((r) => r.data),

  /** 좋아요 보내기 — 상대에게 알림이 간다. */
  like: (id: number, targetId: number) =>
    api.post(`/profiles/${id}/likes/${targetId}`).then((r) => r.data),

  /** 좋아요 취소 — 상대가 답하기 전에만 된다. */
  unlike: (id: number, targetId: number) =>
    api.delete(`/profiles/${id}/likes/${targetId}`).then((r) => r.data),

  /** 관심 없음 — 내 목록에서만 치운다. 상대에게는 알리지 않는다. */
  pass: (id: number, targetId: number) =>
    api.post(`/profiles/${id}/passes/${targetId}`).then((r) => r.data),

  /** 치워둔 프로필 수. */
  passCount: (id: number) =>
    api.get<{ passed: number }>(`/profiles/${id}/passes/count`).then((r) => r.data.passed),

  /** 치워둔 프로필 전체 되살리기. */
  clearPasses: (id: number) =>
    api.delete(`/profiles/${id}/passes`).then((r) => r.data),

  /** 프로필 노출 on/off (본인). */
  setVisible: (id: number, visible: boolean) =>
    api.patch<DatingSelf>(`/profiles/${id}/visibility`, { visible }, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  /** 나에게 온 좋아요 목록. */
  receivedLikes: (id: number) =>
    api.get<DatingLikeCard[]>(`/profiles/${id}/likes/received`).then((r) => r.data),

  /** 내가 보낸 좋아요 목록. */
  sentLikes: (id: number) =>
    api.get<DatingLikeCard[]>(`/profiles/${id}/likes/sent`).then((r) => r.data),

  /** 받은 좋아요에 답하기. 답한 뒤의 받은 목록을 그대로 돌려준다. */
  respondLike: (id: number, likeId: number, accept: boolean) =>
    api.post<DatingLikeCard[]>(`/profiles/${id}/likes/${likeId}/respond`, { accept }, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  /** 약속 화면 안내 (장소·예약금 문구·예약 링크). */
  meetingGuide: () =>
    api.get<MeetingGuide>('/meeting-guide').then((r) => r.data),

  /** 만남 약속 제안 — 날짜 하나 + 선호 시간 최대 3개. */
  proposeMeeting: (
    id: number,
    otherId: number,
    req: { slots: MeetingSlot[] },
  ) =>
    api.put<DatingMeeting>(`/profiles/${id}/meetings/${otherId}`, req, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  /** 상대가 제안한 시간 중 하나를 골라 확정. */
  confirmMeeting: (id: number, otherId: number, slot: MeetingSlot | null) =>
    api.post<DatingMeeting>(`/profiles/${id}/meetings/${otherId}/confirm`, slot ?? {}, {
      headers: { 'Content-Type': 'application/json' },
    }).then((r) => r.data),

  /** 약속 취소. */
  cancelMeeting: (id: number, otherId: number) =>
    api.delete<DatingMeeting>(`/profiles/${id}/meetings/${otherId}`).then((r) => r.data),

  uploadPhoto: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api
      .post<{ url: string }>('/photo', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.url);
  },
};
