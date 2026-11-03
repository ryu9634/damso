export type Gender = 'MALE' | 'FEMALE';

export type CustomerStatus = 'ACTIVE' | 'LEFT';

export type EventStatus =
  | 'REGISTRATION'
  | 'ROUND_1'
  | 'ROUND_2'
  | 'ROUND_3'
  | 'PREFERENCE'
  | 'ROUND_4'
  | 'MESSAGE'
  | 'CLOSED';

export interface Event {
  id: number;
  name: string;
  status: EventStatus;
  createdAt: string;
}

export interface Customer {
  id: number;
  eventId: number;
  nickname: string;
  realName: string;
  phone: string;
  gender: Gender;
  mbti: string;
  valuesAnswers: string;
  seatNumber: number | null;
  status: CustomerStatus;
  createdAt: string;
}

export interface SeatAssignment {
  id: number;
  eventId: number;
  round: number;
  seatNumber: number;
  slotIndex: number;
  gender: Gender;
  customerId: number;
  customerNickname: string;
  customerMbti: string;
}

export interface Dashboard {
  eventId: number;
  eventName: string;
  status: EventStatus;
  totalCustomers: number;
  maleCount: number;
  femaleCount: number;
  round1Assigned: number;
  round2Assigned: number;
  round3Assigned: number;
  round4Assigned: number;
  preferenceSubmitted: number;
  messageCount: number;
}

export interface Stats {
  from: string | null;
  to: string | null;
  eventCount: number;
  totalCustomers: number;
  mbtiI: number;
  mbtiE: number;
  preferenceParticipants: number;
  preferenceMatched: number;
  messageSenders: number;
  mutualMessagePeople: number;
}

export interface SettingItem {
  key: string;
  label: string;
  group: string;
  type: 'NUMBER' | 'TEXT' | 'TEXTAREA' | 'SELECT';
  value: string;
  /** SELECT 일 때의 선택지. 나머지 타입은 빈 배열. */
  options: string[];
}

export interface GuestSettings {
  maxMessageRecipients: number;
  preferenceTitle: string;
  preferenceSubtitle: string;
  messageTitle: string;
  messageSubtitle: string;
  waitingTitle: string;
  waitingSubtitle: string;
  endTitle: string;
  endSubtitle: string;
}

export const DEFAULT_GUEST_SETTINGS: GuestSettings = {
  maxMessageRecipients: 2,
  preferenceTitle: '호감 가는 상대를 골라주세요',
  preferenceSubtitle: '호감 가는 상대를 1·2·3순위로 골라주세요. (선택 안 해도 됩니다)',
  messageTitle: '마음을 전해보세요',
  messageSubtitle: '오늘 만난 사람에게 마음을 전해보세요.',
  waitingTitle: '등록이 완료됐어요!',
  waitingSubtitle: '다른 손님 입장을 기다리는 중입니다.',
  endTitle: '담소 소개팅 운영시간 아니에요~',
  endSubtitle: '운영요일 : 금요일, 토요일\n운영시간 : 19:00~21:00\n[예약링크 바로가기](https://map.naver.com/p/entry/place/2006044431?placePath=/booking)',
};

export interface Preference {
  id: number;
  voterId: number;
  voterNickname: string;
  firstChoiceId: number;
  firstChoiceNickname: string;
  secondChoiceId: number | null;
  secondChoiceNickname: string | null;
  thirdChoiceId: number | null;
  thirdChoiceNickname: string | null;
}

export interface Message {
  id: number;
  senderId: number;
  senderNickname: string;
  receiverId: number;
  receiverNickname: string;
  content: string;
  senderContact: string;
  createdAt: string;
}

export const STATUS_LABEL: Record<EventStatus, string> = {
  REGISTRATION: '손님 입력',
  ROUND_1: '1차 자리',
  ROUND_2: '2차 자리',
  ROUND_3: '3차 자리',
  PREFERENCE: '호감도 지목',
  ROUND_4: '4차 자리',
  MESSAGE: '메시지 전달',
  CLOSED: '종료',
};

export interface ValueQuestion {
  id: number;
  sortOrder: number;
  label: string;
  optionA: string;
  optionB: string;
}

export interface GuestSeatView {
  seatNumber: number;
  slotIndex: number;
  gender: Gender;
  customerId: number | null;
  nickname: string;
}

export interface GuestCandidate {
  id: number;
  nickname: string;
  mbti: string;
  gender: Gender;
}

export interface GuestMessage {
  id: number;
  senderId: number;
  senderNickname: string;
  senderMbti: string;
  receiverId: number;
  receiverNickname: string;
  content: string;
  senderContact: string;
  createdAt: string;
}

export interface PersonBrief {
  id: number;
  nickname: string;
  mbti: string;
}

/** 소개팅 상세 질문 (관리자 커스텀). */
export type DatingQuestionType = 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'SELECT';

export interface DatingQuestion {
  id: number;
  sortOrder: number;
  label: string;
  type: DatingQuestionType;
  options: string[];
  required: boolean;
}

/** 프로필 표시용 답변 한 줄. */
export interface DatingAnswerView {
  questionId: number;
  label: string;
  value: string;
}

/** 본인 프로필 (등록/로그인 후, 이름·연락처 + 작성한 설문 답변 포함). */
export interface DatingSelf {
  id: number;
  name: string;
  contact: string;
  birthYear: number;
  gender: Gender;
  photoUrl: string | null;
  answers: DatingAnswerView[];
  /** 관리 상태 (심사중·활동중·휴면·제외 등) */
  status: DatingStatus;
  statusLabel: string;
  /** 지금 이성 프로필을 보고 고를 수 있는 상태인지 */
  canSelect: boolean;
  /** 이용할 수 없는 상태일 때의 안내 문구. 이용 가능하면 null */
  notice: string | null;
  /** 본인이 켜둔 노출 스위치. 끄면 남의 목록에서 빠지고 나도 열람할 수 없다 */
  visible: boolean;
  /** 매장에서 직접 확인받은 회원인지 */
  verified: boolean;
}

/** 이성 프로필 열람 카드 (이름·연락처 미노출). */
export interface DatingProfileCard {
  id: number;
  gender: Gender;
  birthYear: number;
  photoUrl: string | null;
  answers: DatingAnswerView[];
  liked: boolean;
  /** 매장에서 직접 얼굴을 확인한 회원 */
  verified: boolean;
}

/** 받은 좋아요에 대한 답. */
export type DatingLikeStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';

/** 서로 좋아요가 된 두 사람의 만남 약속. */
/** 만날 날짜와 시각 한 벌. */
export interface MeetingSlot {
  /** yyyy-MM-dd */
  day: string;
  /** 19~23 */
  hour: number;
}

export interface DatingMeeting {
  otherProfileId: number;
  status: MeetingStatus;
  /** 확정된 날짜(yyyy-MM-dd). 제안 중이면 후보 가운데 가장 이른 날 */
  dateDay: string | null;
  /** 확정된 시각(19~23). 상대가 고르기 전이면 null */
  dateHour: number | null;
  /** 제안한 사람이 고른 후보 (날짜+시간, 최대 3개) */
  slots: MeetingSlot[];
  /** 관리자가 정해 둔 장소 (손님은 고르지 않는다) */
  place: string | null;
  /** 내가 제안해서 상대 확인을 기다리는 중인지 */
  proposedByMe: boolean;
  updatedAt: string | null;
}

/** 약속 화면 안내 — 관리자 환경설정에서 온다. */
export interface MeetingGuide {
  place: string;
  notice: string;
  bookingNotice: string;
  /** 비어 있으면 예약 링크 버튼을 띄우지 않는다. */
  bookingUrl: string | null;
  /** 고를 수 있는 시간대 */
  hours: number[];
  /** 후보 최대 선택 개수 */
  maxSlots: number;
}

/** 받은/보낸 좋아요 한 줄. 이름은 서로 좋아요가 된 뒤에만 내려온다. */
export interface DatingLikeCard {
  likeId: number;
  profileId: number;
  name: string | null;
  gender: Gender;
  birthYear: number;
  photoUrl: string | null;
  answers: DatingAnswerView[];
  createdAt: string;
  status: DatingLikeStatus;
  statusLabel: string;
  respondedAt: string | null;
  /** 서로 좋아요일 때만 채워진다. */
  meeting: DatingMeeting | null;
  /** 상대가 매장에서 확인받은 회원인지 */
  verified: boolean;
  /** 답변 기한. 아직 답을 기다리는 좋아요에만 채워진다 */
  expiresAt: string | null;
}

/** 소개팅 회원 관리 상태. */
export type DatingStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'MATCHING'
  | 'MATCHED'
  | 'DORMANT'
  | 'EXCLUDED';

export const DATING_STATUS_ORDER: DatingStatus[] = [
  'PENDING', 'ACTIVE', 'MATCHING', 'MATCHED', 'DORMANT', 'EXCLUDED',
];

export const DATING_STATUS_LABEL: Record<DatingStatus, string> = {
  PENDING: '심사중',
  ACTIVE: '활동중',
  MATCHING: '소개진행',
  MATCHED: '성사',
  DORMANT: '휴면',
  EXCLUDED: '제외',
};

/** 상태 배지 색상 (Tailwind 클래스). */
export const DATING_STATUS_STYLE: Record<DatingStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  ACTIVE: 'bg-emerald-100 text-emerald-800',
  MATCHING: 'bg-sky-100 text-sky-800',
  MATCHED: 'bg-rose-100 text-rose-700',
  DORMANT: 'bg-stone-200 text-stone-600',
  EXCLUDED: 'bg-red-100 text-red-700',
};

/** 관리자 전용 프로필 (이름·연락처 + 답변 + 찜 현황 + 관리 정보). */
export interface DatingAdminProfile {
  id: number;
  name: string;
  contact: string;
  birthYear: number;
  gender: Gender;
  photoUrl: string | null;
  createdAt: string;
  answers: DatingAnswerView[];
  likedNames: string[];
  likedByNames: string[];
  mutualNames: string[];
  status: DatingStatus;
  adminMemo: string | null;
  /** 소프트 삭제 시각. null 이면 정상 회원. */
  deletedAt: string | null;
  /** 매장 대면 확인 시각. null 이면 미확인. */
  verifiedAt: string | null;
  /** 본인이 노출을 꺼둔 시각. null 이면 노출 중. */
  hiddenAt: string | null;
}

/** 관리자 선택(찜) 내역 한 줄 — 누가 누구를 언제 선택했는지. */
export interface DatingLikeRow {
  id: number;
  fromId: number;
  fromName: string;
  fromGender: Gender;
  fromBirthYear: number;
  fromDeleted: boolean;
  toId: number;
  toName: string;
  toGender: Gender;
  toBirthYear: number;
  toDeleted: boolean;
  /** 선택한 날짜 */
  createdAt: string;
  /** 상대도 선택해 쌍방이 된 상태인지 */
  mutual: boolean;
  /** 쌍방이 성립한 시각 (아니면 null) */
  mutualAt: string | null;
  /** 상대가 나를 선택한 시각. 쌍방이 아니면 null (쌍방은 여자→남자 한 줄만 내려오므로 남자 쪽 날짜가 여기 담긴다) */
  partnerAt: string | null;
  /** 페어 단위 관리자 메모 (쌍방이면 양방향이 같은 값을 공유) */
  memo: string | null;
  /** 데이트 날짜 (yyyy-MM-dd). 미정이면 null */
  dateDay: string | null;
  /** 데이트 시각(시). 19~23. 미정이면 null */
  dateHour: number | null;
  /** 받은 사람의 답 (좋아요/미안해요/답변 대기) */
  status: DatingLikeStatus;
  statusLabel: string;
}

/** 만남 약속 상태. */
export type MeetingStatus = 'NONE' | 'PROPOSED' | 'CONFIRMED';

/** 페어(두 회원) 메모 + 데이트 약속. 약속 캘린더에서 쓴다. */
export interface DatingPairNote {
  id: number;
  aId: number;
  aName: string;
  bId: number;
  bName: string;
  memo: string | null;
  /** yyyy-MM-dd. 약속 미정이면 null */
  dateDay: string | null;
  /** 19~23. 미정이면 null */
  dateHour: number | null;
  updatedAt: string | null;
  /** 손님이 직접 잡은 약속의 장소. 관리자가 잡은 약속이면 null */
  place: string | null;
  status: MeetingStatus;
}

/** 데이트 시간으로 고를 수 있는 시(hour) 목록 — 19:00 ~ 23:00, 한 시간 단위. */
export const DATE_HOURS = [19, 20, 21, 22, 23] as const;

/** 19 → "19:00" */
export function formatHour(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`;
}


export interface MatchScoreMatrix {
  males: PersonBrief[];
  females: PersonBrief[];
  /** mbtiMatrix[maleIdx][femaleIdx] */
  mbtiMatrix: number[][];
  valuesMatrix: number[][];
  totalMatrix: number[][];
}

// ─── 앱(손님 모바일) ──────────────────────────────────────

export type AuthProvider = 'LOCAL' | 'KAKAO' | 'NAVER';

/** 앱 회원 계정 (마이페이지). */
export interface AppUser {
  id: number;
  /** 로그인 아이디. 간편로그인만 쓰는 계정은 null. */
  loginId: string | null;
  /** 아이디/비밀번호로도 로그인할 수 있는지 */
  hasPassword: boolean;
  /** 이 계정에 연결된 간편로그인 목록 */
  linkedProviders: AuthProvider[];
  /** 공개 이름 — 방명록 등 다른 손님에게 보이는 이름 */
  nickname: string | null;
  name: string;
  birthDate: string | null;
  age: number | null;
  phone: string | null;
  pushOptIn: boolean;
  /** 성명·생년월일이 모두 채워졌는지 (소셜 가입 후 보완 필요 여부) */
  profileComplete: boolean;
}

export interface AuthResult {
  token: string;
  user: AppUser;
  needsProfile: boolean;
}

/** 간편로그인 버튼 노출 정보. configured=false 면 화면에 표시하지 않는다. */
export interface AuthProviderInfo {
  provider: AuthProvider;
  label: string;
  configured: boolean;
  authorizeUrl: string | null;
}

/** 관리자가 등록한 술·칵테일 메뉴 한 줄. */
export interface DrinkItem {
  id: number;
  emoji: string | null;
  name: string;
  description: string | null;
  imageUrl: string | null;
  sortOrder: number;
  visible: boolean;
  /** 오늘의 추천 한 잔 (한 번에 하나만) */
  recommended: boolean;
}

/** 지금 쓸 수 있는 기능. 꺼져 있으면 탭·카드를 감추고 안내 문구를 보여준다. */
export interface AppFeatures {
  matching: { enabled: boolean; notice: string };
  selecting: { enabled: boolean; notice: string };
  gameRoom: { enabled: boolean; notice: string };
  /** 홈 화면에 게임방 카드를 띄울지 (기능 사용과 별개) */
  gameRoomOnHome: boolean;
}

/** 기능 정보를 못 불러왔을 때의 기본값 — 일단 열어둔다. */
export const DEFAULT_FEATURES: AppFeatures = {
  matching: { enabled: true, notice: '' },
  selecting: { enabled: true, notice: '' },
  gameRoom: { enabled: true, notice: '' },
  gameRoomOnHome: false,
};

/** 홈 화면 데이터. */
export interface AppHome {
  openStatus: string;
  open: boolean;
  hours: string;
  notice: string;
  cocktail: {
    name: string;
    description: string;
    imageUrl: string | null;
    emoji: string | null;
  };
  drinks: DrinkItem[];
  couples: {
    visible: boolean;
    total: number;
    recent: { label: string; since: string | null }[];
  };
  /** 지금 진행 중인 담소 소개팅. live 가 false 면 나머지는 비어 있다 */
  matching: {
    live: boolean;
    eventId: number | null;
    name: string | null;
    status: string | null;
    statusLabel: string | null;
    participants: number;
    /** 로그인한 회원이 이번 이벤트에 이미 참여 등록했는지 */
    registered: boolean;
  };
  features: AppFeatures;
}

// ─── 가게 캘린더 (행사 · 휴일) ────────────────────────────

export type CalendarEntryType = 'EVENT' | 'HOLIDAY';

export const CALENDAR_TYPE_LABEL: Record<CalendarEntryType, string> = {
  EVENT: '행사',
  HOLIDAY: '휴일',
};

/** 종류를 고르면 자동으로 붙는 기본 이모지 (서버 CalendarEntryType 과 같아야 한다). */
export const CALENDAR_TYPE_EMOJI: Record<CalendarEntryType, string> = {
  EVENT: '🎉',
  HOLIDAY: '🌙',
};

export interface CalendarEntry {
  id: number;
  type: CalendarEntryType;
  typeLabel: string;
  title: string;
  description: string | null;
  /** yyyy-MM-dd */
  startDay: string;
  /** 하루짜리면 null */
  endDay: string | null;
  timeText: string | null;
  emoji: string | null;
}

/** 홈 '이번 주'에 찍히는 한 건. MEETING 은 본인의 담소 셀렉팅 약속. */
export type WeekItemKind = 'EVENT' | 'HOLIDAY' | 'MEETING';

export interface AppWeekItem {
  kind: WeekItemKind;
  title: string;
  detail: string | null;
  emoji: string | null;
}

export interface AppWeekDay {
  /** yyyy-MM-dd */
  date: string;
  /** 1(월) ~ 7(일) */
  dayOfWeek: number;
  today: boolean;
  items: AppWeekItem[];
}

export interface AppWeek {
  weekStart: string;
  weekEnd: string;
  days: AppWeekDay[];
}

// ─── 게임방 ──────────────────────────────────────────────

export type GameType = 'BALANCE' | 'PICK' | 'LIAR' | 'MAFIA';

export const GAME_TYPE_EMOJI: Record<GameType, string> = {
  BALANCE: '⚖️',
  PICK: '🎯',
  LIAR: '🤥',
  MAFIA: '🕵️',
};

/** 라운드 진행 단계. 역할 게임은 ROLE → VOTE → REVEALED 로 간다. */
export type GamePhase = 'OPEN' | 'ROLE' | 'VOTE' | 'REVEALED';

export type PlayerRole = 'CITIZEN' | 'LIAR' | 'MAFIA' | 'POLICE' | 'DOCTOR';

/** 사회자 진행용 역할표 한 줄. */
export interface RoleCard {
  name: string;
  role: PlayerRole;
  roleLabel: string;
}

/** 누가 몇 표 받았는지. */
export interface GameVote {
  name: string;
  count: number;
  voters: string[];
}

export type GameRoomStatus = 'WAITING' | 'PLAYING' | 'CLOSED';

/** 방 목록 한 줄. */
export interface GameRoom {
  code: string;
  title: string | null;
  hostName: string;
  status: GameRoomStatus;
  statusLabel: string;
  playerCount: number;
  /** 내가 이미 들어가 있는 방인지 */
  joined: boolean;
  createdAt: string;
}

export interface GamePlayer {
  userId: number;
  name: string;
  /** 최근까지 화면을 보고 있는지 */
  active: boolean;
  host: boolean;
  /** 마피아 진행을 맡은 사람 */
  moderator: boolean;
  me: boolean;
}

export interface GameOption {
  type: GameType;
  label: string;
  description: string;
}

export interface GameRound {
  id: number;
  type: GameType;
  typeLabel: string;
  phase: GamePhase;
  phaseLabel: string;
  revealed: boolean;
  optionA: string | null;
  optionB: string | null;
  /** 랜덤 뽑기에서 뽑힌 사람 */
  winnerName: string | null;
  /** 내가 고른 값 (A/B) 또는 내가 지목한 사람의 id */
  myAnswer: string | null;
  answered: number;
  countA: number;
  countB: number;
  namesA: string[];
  namesB: string[];
  /** ─── 라이어·마피아 ─── */
  /** 내 역할. 남의 역할은 공개 전까지 내려오지 않는다 */
  myRole: PlayerRole | null;
  myRoleLabel: string | null;
  /** 라이어 게임 제시어. 라이어에게는 공개 전까지 null */
  keyword: string | null;
  category: string | null;
  /** 역할 카드에 함께 보이는 한 줄 설명 */
  myRoleHint: string | null;
  /** 마피아일 때 보이는 동료 */
  mates: string[];
  /** 사회자에게만 내려오는 전체 역할표 (마피아 전용) */
  allRoles: RoleCard[];
  /** 이번 판에 역할을 받은 사람들. 투표 후보이자 투표할 수 있는 사람 */
  playerIds: number[];
  /** 공개 뒤 득표 집계 */
  votes: GameVote[];
  /** 공개 뒤 밝혀지는 라이어·마피아 이름 */
  revealedRoles: string[];
  /** 공개 뒤 결과 한 줄 */
  outcome: string | null;
}

/** 게임방 화면이 통째로 그리는 상태. */
export interface GameState {
  code: string;
  title: string | null;
  status: GameRoomStatus;
  statusLabel: string;
  hostName: string;
  /** 내가 방장인지 */
  host: boolean;
  /** 마피아 사회자 이름. 없으면 null */
  moderatorName: string | null;
  /** 내가 사회자인지 */
  moderator: boolean;
  players: GamePlayer[];
  activeCount: number;
  round: GameRound | null;
  games: GameOption[];
}

/** 관리자가 등록하는 게임 문항. */
export interface GameContent {
  id: number;
  type: GameType;
  typeLabel: string;
  optionA: string;
  optionB: string | null;
  visible: boolean;
  sortOrder: number;
}

/** 알림함 한 줄. */
export type NotificationType =
  | 'LIKE_RECEIVED'
  | 'LIKE_ACCEPTED'
  | 'LIKE_DECLINED'
  | 'MEETING_PROPOSED'
  | 'MEETING_CONFIRMED'
  | 'MEETING_CANCELED'
  | 'NOTICE';

export const NOTIFICATION_EMOJI: Record<NotificationType, string> = {
  LIKE_RECEIVED: '💌',
  LIKE_ACCEPTED: '💖',
  LIKE_DECLINED: '🥲',
  MEETING_PROPOSED: '📅',
  MEETING_CONFIRMED: '🎉',
  MEETING_CANCELED: '🗓️',
  NOTICE: '📢',
};

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  body: string | null;
  linkPath: string | null;
  read: boolean;
  createdAt: string;
}

/** 웹 푸시 구독 상태 (마이페이지 알림 스위치). */
export interface PushInfo {
  /** 브라우저가 구독할 때 쓰는 서버 공개키. 서버 준비가 안 됐으면 null */
  publicKey: string | null;
  supported: boolean;
  /** 이 계정에 등록된 기기 수 */
  devices: number;
}

export interface GuestbookEntry {
  id: number;
  authorName: string;
  content: string;
  createdAt: string;
  mine: boolean;
}

/** 관리자 방명록 관리용 (실명·숨김 포함). */
export interface AdminGuestbookEntry {
  id: number;
  userId: number;
  /** 글쓴이의 현재 닉네임 */
  authorName: string;
  /** 회원 실명. 탈퇴 회원이면 null */
  realName: string | null;
  content: string;
  hidden: boolean;
  createdAt: string;
}

/** 관리자 앱 회원 목록용. */
export interface AdminAppUser {
  id: number;
  loginId: string | null;
  nickname: string | null;
  name: string;
  birthDate: string | null;
  age: number | null;
  phone: string | null;
  /** 이 계정이 쓸 수 있는 로그인 수단 (예: ['아이디', '카카오']) */
  loginMethods: string[];
  pushOptIn: boolean;
  profileComplete: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  /** 관리자가 비밀번호를 초기화해 준 시각. 손님이 바꾸면 다시 null 이 된다. */
  passwordResetAt: string | null;
}
