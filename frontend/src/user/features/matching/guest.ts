import axios from 'axios';
import { getToken } from '../../api/app';
import type {
  Customer,
  Event,
  Gender,
  GuestCandidate,
  GuestMessage,
  GuestSeatView,
  GuestSettings,
  Message,
  Preference,
  ValueQuestion,
} from '../../../shared/types';

const api = axios.create({
  baseURL: '/api/guest',
  headers: { 'Content-Type': 'application/json' },
});

// 입장 등록은 로그인한 회원만 할 수 있어 토큰을 함께 보낸다.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export const guestApi = {
  event: () => api.get<Event>('/event').then((r) => r.data),

  settings: () => api.get<GuestSettings>('/settings').then((r) => r.data),

  questions: () => api.get<ValueQuestion[]>('/questions').then((r) => r.data),

  register: (req: {
    nickname: string;
    realName: string;
    phone: string;
    gender: Gender;
    mbti: string;
    valuesAnswers: string;
    seatNumber: number;
  }) =>
    api
      .post<Customer>('/customers', { ...req, eventId: 0 })
      .then((r) => r.data),

  me: (customerId: number) =>
    api.get<Customer>('/me', { params: { customerId } }).then((r) => r.data),

  /** 로그인한 회원의 이번 이벤트 등록 정보. 아직 등록 전이면 null. */
  myRegistration: () =>
    api.get<Customer | ''>('/me/account').then((r) => r.data || null),

  login: (realName: string, phone: string) =>
    api.post<Customer>('/login', { realName, phone }).then((r) => r.data),

  seats: (customerId: number, round: number) =>
    api
      .get<GuestSeatView[]>('/seats', { params: { customerId, round } })
      .then((r) => r.data),

  myMatchScores: (customerId: number) =>
    api
      .get<Record<number, number>>('/me/match-scores', { params: { customerId } })
      .then((r) => r.data),

  candidates: (customerId: number) =>
    api
      .get<GuestCandidate[]>('/me/candidates', { params: { customerId } })
      .then((r) => r.data),

  submitPreference: (req: {
    customerId: number;
    firstChoiceId: number | null;
    secondChoiceId: number | null;
    thirdChoiceId: number | null;
  }) => api.post<Preference>('/preference', req).then((r) => r.data),

  myPreference: (customerId: number) =>
    api
      .get<Preference | null>('/preference', { params: { customerId } })
      .then((r) => r.data),

  myMessagesSent: (customerId: number) =>
    api
      .get<GuestMessage[]>('/me/messages-sent', { params: { customerId } })
      .then((r) => r.data),

  myMessagesReceived: (customerId: number) =>
    api
      .get<GuestMessage[]>('/me/messages-received', { params: { customerId } })
      .then((r) => r.data),

  sendMessage: (req: {
    customerId: number;
    receiverId: number;
    content: string;
    senderContact: string;
  }) => api.post<Message>('/message', req).then((r) => r.data),
};
