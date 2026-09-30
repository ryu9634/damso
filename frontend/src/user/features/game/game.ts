import axios from 'axios';
import { getToken } from '../../api/app';
import type { GameRoom, GameState, GameType } from '../../../shared/types';

/**
 * 게임방 API. 모두 로그인 필요.
 * 방 화면은 state() 를 주기적으로 다시 불러 갱신한다. (지금은 2초 간격)
 */
const api = axios.create({ baseURL: '/api/app/games' });

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

const JSON_HEADER = { headers: { 'Content-Type': 'application/json' } };

export const gameApi = {
  /** 열려 있는 방 목록. */
  rooms: () => api.get<GameRoom[]>('/rooms').then((r) => r.data),

  create: (title?: string | null) =>
    api.post<GameState>('/rooms', { title: title ?? null }, JSON_HEADER).then((r) => r.data),

  state: (code: string) => api.get<GameState>(`/rooms/${code}`).then((r) => r.data),

  join: (code: string) => api.post<GameState>(`/rooms/${code}/join`).then((r) => r.data),

  leave: (code: string) => api.post(`/rooms/${code}/leave`).then((r) => r.data),

  /** 마피아 사회자 맡기 / 내려놓기. */
  setModerator: (code: string, take: boolean) =>
    api.post<GameState>(`/rooms/${code}/moderator`, { take }, JSON_HEADER).then((r) => r.data),

  /** 방 종료 — 방장만. */
  close: (code: string) => api.post(`/rooms/${code}/close`).then((r) => r.data),

  /** 게임 시작 — 방장만. */
  start: (code: string, gameType: GameType) =>
    api.post<GameState>(`/rooms/${code}/rounds`, { gameType }, JSON_HEADER).then((r) => r.data),

  /** 투표 열기 — 방장만. (라이어·마피아) */
  openVote: (code: string, roundId: number) =>
    api.post<GameState>(`/rooms/${code}/rounds/${roundId}/vote-open`).then((r) => r.data),

  /** 밸런스는 'A'/'B', 역할 게임은 지목할 사람의 userId 문자열. */
  answer: (code: string, roundId: number, value: string) =>
    api.post<GameState>(`/rooms/${code}/rounds/${roundId}/answer`, { value }, JSON_HEADER)
      .then((r) => r.data),

  /** 결과 공개 — 방장만. */
  reveal: (code: string, roundId: number) =>
    api.post<GameState>(`/rooms/${code}/rounds/${roundId}/reveal`).then((r) => r.data),
};
