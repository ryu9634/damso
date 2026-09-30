import { useCallback, useEffect, useMemo, useState } from 'react';
import { datingAdminApi } from '../../api/client';
import {
  PageHeader, Card, Button, Empty, Badge, Input, Select, Textarea, Modal, Field,
} from '../../layout/ui';
import {
  DATE_HOURS, DATING_STATUS_LABEL, DATING_STATUS_STYLE, formatHour,
  type DatingAdminProfile, type DatingLikeRow, type DatingPairNote,
} from '../../../shared/types';
import { ageOf, formatDate, formatDateTime, maskContact } from '../../../shared/utils/privacy';
import { downloadCsv, todayStamp } from '../../../shared/utils/csv';

// ─── 날짜 헬퍼 (yyyy-MM-dd 문자열 기준. Date 로 왕복하면 타임존에 휘둘린다) ──
const ymd = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// ─── 선택 내역: 상세 모달 ──────────────────────────────────
/** 모달 안에서 한 사람의 전체 정보를 보여주는 컬럼. */
function ProfileDetailColumn({
  role, profile, fallbackName, revealed, onReveal,
}: {
  role: string;
  profile: DatingAdminProfile | undefined;
  fallbackName: string;
  revealed: boolean;
  onReveal: () => void;
}) {
  if (!profile) {
    return (
      <div className="rounded-lg border border-stone-200 p-4">
        <div className="text-xs font-semibold text-stone-400">{role}</div>
        <div className="mt-1 text-sm font-bold text-stone-800">{fallbackName}</div>
        <p className="mt-3 text-xs text-stone-400">
          회원 정보를 찾을 수 없습니다. (완전 삭제된 회원)
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-stone-200 overflow-hidden">
      <div className="px-4 py-2 bg-stone-50 border-b border-stone-200 flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-stone-500">{role}</span>
        <div className="flex items-center gap-1">
          <Badge className={DATING_STATUS_STYLE[profile.status]}>
            {DATING_STATUS_LABEL[profile.status]}
          </Badge>
          {profile.deletedAt && (
            <Badge className="bg-red-100 text-red-700">휴지통</Badge>
          )}
        </div>
      </div>

      <div className="p-4 space-y-3">
        <div className="flex gap-3">
          {profile.photoUrl ? (
            <img
              src={profile.photoUrl}
              alt={`${profile.name} 사진`}
              className="w-24 h-32 rounded-lg object-cover bg-stone-100 shrink-0"
            />
          ) : (
            <div className="w-24 h-32 rounded-lg bg-stone-100 shrink-0 flex items-center justify-center text-xs text-stone-400">
              사진 없음
            </div>
          )}
          <div className="min-w-0 space-y-1 text-sm">
            <div className="font-bold text-stone-900 break-keep">{profile.name}</div>
            <div className="text-stone-600">
              {profile.gender === 'MALE' ? '남' : '여'} · {profile.birthYear}년생 (
              {ageOf(profile.birthYear)}세)
            </div>
            <div className="text-stone-600 tabular-nums">
              {revealed ? (
                <span className="select-text">{profile.contact}</span>
              ) : (
                <button
                  type="button"
                  onClick={onReveal}
                  className="underline decoration-dotted underline-offset-2 hover:text-stone-900"
                  title="클릭하면 연락처가 보입니다"
                >
                  {maskContact(profile.contact)}
                </button>
              )}
            </div>
            <div className="text-xs text-stone-400">
              등록 {formatDate(profile.createdAt)}
            </div>
          </div>
        </div>

        {/* 설문 답변 */}
        <div className="border-t border-stone-100 pt-2">
          {profile.answers.length === 0 ? (
            <p className="text-xs text-stone-400">작성한 설문 답변이 없습니다.</p>
          ) : (
            <dl className="space-y-1.5">
              {profile.answers.map((a) => (
                <div key={a.questionId} className="flex gap-2">
                  <dt className="w-24 shrink-0 text-xs font-semibold text-stone-500 break-keep">
                    {a.label}
                  </dt>
                  <dd className="flex-1 text-xs text-stone-800 whitespace-pre-wrap break-words">
                    {a.value}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </div>

        {/* 찜 현황 */}
        <div className="border-t border-stone-100 pt-2 space-y-1 text-xs text-stone-600">
          <div>
            <span className="font-semibold text-stone-500">선택함 </span>
            {profile.likedNames.length ? profile.likedNames.join(', ') : '—'}
          </div>
          <div>
            <span className="font-semibold text-stone-500">선택받음 </span>
            {profile.likedByNames.length ? profile.likedByNames.join(', ') : '—'}
          </div>
          <div>
            <span className="font-semibold text-rose-500">쌍방 </span>
            {profile.mutualNames.length ? profile.mutualNames.join(', ') : '—'}
          </div>
        </div>

        {profile.adminMemo && (
          <div className="border-t border-stone-100 pt-2 text-xs text-stone-600 whitespace-pre-wrap">
            <span className="font-semibold text-stone-500">메모 </span>
            {profile.adminMemo}
          </div>
        )}
      </div>
    </div>
  );
}

/** 선택 한 건을 클릭했을 때 두 사람의 상세 정보를 나란히 보여준다. */
function LikeDetailModal({
  row, profileById, onClose, onSaved, onError,
}: {
  row: DatingLikeRow;
  profileById: Map<number, DatingAdminProfile>;
  onClose: () => void;
  onSaved: () => void;
  onError: (msg: string) => void;
}) {
  const [revealed, setRevealed] = useState(false);
  const [memo, setMemo] = useState(row.memo ?? '');
  const [dateDay, setDateDay] = useState(row.dateDay ?? '');
  const [dateHour, setDateHour] = useState<string>(
    row.dateHour == null ? '' : String(row.dateHour),
  );
  const [saving, setSaving] = useState(false);

  const dirty =
    memo !== (row.memo ?? '') ||
    dateDay !== (row.dateDay ?? '') ||
    dateHour !== (row.dateHour == null ? '' : String(row.dateHour));

  const save = async () => {
    setSaving(true);
    try {
      await datingAdminApi.savePairNote(row.fromId, row.toId, {
        memo,
        dateDay: dateDay || null,
        dateHour: dateHour ? Number(dateHour) : null,
      });
      onSaved();
    } catch (e: any) {
      onError(e?.response?.data?.message ?? '저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      title="선택 상세"
      width="max-w-4xl"
      onClose={onClose}
      footer={
        <>
          <Button onClick={() => setRevealed((v) => !v)}>
            {revealed ? '연락처 가리기' : '연락처 보기'}
          </Button>
          <Button onClick={onClose}>닫기</Button>
          <Button variant="primary" onClick={save} disabled={saving || !dirty}>
            {saving ? '저장 중...' : '저장'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="rounded-lg bg-stone-50 border border-stone-200 px-3 py-2 text-sm text-stone-700">
          <b>{row.fromName}</b>
          <span className="mx-1.5 text-rose-500">{row.mutual ? '↔' : '→'}</span>
          <b>{row.toName}</b>
          <span className="mx-2 text-stone-300">|</span>
          {row.fromName} 선택 <span className="tabular-nums">{formatDateTime(row.createdAt)}</span>
          {row.mutual && row.partnerAt && (
            <>
              <span className="mx-2 text-stone-300">|</span>
              {row.toName} 선택 <span className="tabular-nums">{formatDateTime(row.partnerAt)}</span>
            </>
          )}
          {row.mutual && row.mutualAt && (
            <>
              <span className="mx-2 text-stone-300">|</span>
              <span className="text-rose-600 font-semibold">
                쌍방 성립 <span className="tabular-nums">{formatDateTime(row.mutualAt)}</span>
              </span>
            </>
          )}
        </div>

        {/* 데이트 약속 + 메모 (두 사람 공통) */}
        <div className="rounded-lg border border-stone-200 p-3 space-y-3">
          <div className="text-xs font-semibold text-stone-500">
            데이트 약속 · 메모
            <span className="ml-2 font-normal text-stone-400">
              두 회원에게 공통으로 저장돼요 (쌍방이면 반대 방향에서도 같은 내용이 보입니다)
            </span>
          </div>

          <div className="flex flex-wrap items-end gap-3">
            <Field label="데이트 날짜" className="w-44">
              <Input
                type="date"
                value={dateDay}
                onChange={(e) => setDateDay(e.target.value)}
              />
            </Field>
            <Field label="시간" className="w-32">
              <Select value={dateHour} onChange={(e) => setDateHour(e.target.value)}>
                <option value="">미정</option>
                {DATE_HOURS.map((h) => (
                  <option key={h} value={h}>{formatHour(h)}</option>
                ))}
              </Select>
            </Field>
            {(dateDay || dateHour) && (
              <Button
                size="sm"
                onClick={() => { setDateDay(''); setDateHour(''); }}
                className="mb-1"
              >
                약속 지우기
              </Button>
            )}
          </div>

          <Field label="메모" hint={`${memo.length}/500`}>
            <Textarea
              rows={3}
              maxLength={500}
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              placeholder="예: 8/20 저녁 약속 잡음. 여성분이 강남 선호."
            />
          </Field>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <ProfileDetailColumn
            role="선택한 회원"
            profile={profileById.get(row.fromId)}
            fallbackName={row.fromName}
            revealed={revealed}
            onReveal={() => setRevealed(true)}
          />
          <ProfileDetailColumn
            role="선택받은 회원"
            profile={profileById.get(row.toId)}
            fallbackName={row.toName}
            revealed={revealed}
            onReveal={() => setRevealed(true)}
          />
        </div>
      </div>
    </Modal>
  );
}

// ─── 약속 캘린더 ───────────────────────────────────────────
/** 한 달치 그리드. 각 칸에 그 날 잡힌 약속을 시간순으로 쌓아 보여준다. */
function AppointmentCalendar({
  year, month, notes, onPick,
}: {
  year: number;
  /** 0-based (Date 와 동일) */
  month: number;
  notes: DatingPairNote[];
  onPick: (note: DatingPairNote) => void;
}) {
  const today = ymd(new Date());

  /** 날짜별 약속 (시간순). 시간 미정은 뒤로. */
  const byDay = useMemo(() => {
    const m = new Map<string, DatingPairNote[]>();
    for (const n of notes) {
      if (!n.dateDay) continue;
      const list = m.get(n.dateDay) ?? [];
      list.push(n);
      m.set(n.dateDay, list);
    }
    for (const list of m.values()) {
      list.sort((a, b) => (a.dateHour ?? 99) - (b.dateHour ?? 99));
    }
    return m;
  }, [notes]);

  // 그 달 1일이 있는 주의 일요일부터 6주(42칸)
  const start = new Date(year, month, 1);
  start.setDate(1 - start.getDay());
  const cells = Array.from({ length: 42 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return d;
  });

  return (
    <div className="overflow-x-auto">
      <div className="min-w-[46rem]">
        <div className="grid grid-cols-7 border-t border-l border-stone-200">
          {WEEKDAYS.map((w, i) => (
            <div
              key={w}
              className={
                'border-r border-b border-stone-200 bg-stone-50 px-2 py-1.5 text-xs font-bold text-center ' +
                (i === 0 ? 'text-red-500' : i === 6 ? 'text-sky-600' : 'text-stone-600')
              }
            >
              {w}
            </div>
          ))}

          {cells.map((d) => {
            const key = ymd(d);
            const inMonth = d.getMonth() === month;
            const items = byDay.get(key) ?? [];
            return (
              <div
                key={key}
                className={
                  'border-r border-b border-stone-200 min-h-[6.5rem] p-1.5 align-top ' +
                  (inMonth ? 'bg-white' : 'bg-stone-50/60')
                }
              >
                <div className="flex items-center justify-between mb-1">
                  <span
                    className={
                      'text-xs font-semibold tabular-nums ' +
                      (!inMonth
                        ? 'text-stone-300'
                        : key === today
                        ? 'bg-damso-500 text-white rounded-full px-1.5 py-0.5'
                        : d.getDay() === 0
                        ? 'text-red-500'
                        : d.getDay() === 6
                        ? 'text-sky-600'
                        : 'text-stone-700')
                    }
                  >
                    {d.getDate()}
                  </span>
                  {items.length > 1 && (
                    <span className="text-[10px] text-stone-400">{items.length}건</span>
                  )}
                </div>

                <div className="space-y-1">
                  {items.map((n) => (
                    <button
                      key={n.id}
                      type="button"
                      onClick={() => onPick(n)}
                      title={n.memo ?? undefined}
                      className="w-full text-left rounded bg-violet-50 border border-violet-200 px-1.5 py-1 hover:bg-violet-100 transition"
                    >
                      <span className="block text-[11px] font-bold text-violet-700 tabular-nums">
                        {n.dateHour != null ? formatHour(n.dateHour) : '시간 미정'}
                      </span>
                      <span className="block text-[11px] text-stone-700 leading-tight break-keep">
                        {n.aName} · {n.bName}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ─── 페이지 ────────────────────────────────────────────────
type Tab = 'likes' | 'calendar';

export default function DatingMatchesPage() {
  const [tab, setTab] = useState<Tab>('likes');

  const [profiles, setProfiles] = useState<DatingAdminProfile[]>([]);
  const [likes, setLikes] = useState<DatingLikeRow[]>([]);
  const [notes, setNotes] = useState<DatingPairNote[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // 선택 내역 탭
  const [likeQuery, setLikeQuery] = useState('');
  const [mutualOnly, setMutualOnly] = useState(false);
  const [detailRow, setDetailRow] = useState<DatingLikeRow | null>(null);
  // 기간 검색 (선택한 날짜 기준, 빈 값이면 제한 없음)
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  // 약속 캘린더 탭 — 보고 있는 달
  const [cursor, setCursor] = useState(() => {
    const d = new Date();
    return { year: d.getFullYear(), month: d.getMonth() };
  });

  const load = useCallback(async () => {
    setLoading(true); setErr(null);
    try {
      // 상세 모달에서 휴지통 회원도 펼쳐볼 수 있어야 하므로 삭제 포함해 받는다
      const [ps, ls, ns] = await Promise.all([
        datingAdminApi.list(true),
        datingAdminApi.likes(),
        datingAdminApi.pairNotes(),
      ]);
      setProfiles(ps);
      setLikes(ls);
      setNotes(ns);
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const profileById = useMemo(
    () => new Map(profiles.map((p) => [p.id, p])),
    [profiles],
  );

  const visibleLikes = useMemo(() => {
    const q = likeQuery.trim().toLowerCase();
    return likes.filter((l) => {
      if (mutualOnly && !l.mutual) return false;
      // createdAt 은 ISO 문자열이라 앞 10자(yyyy-MM-dd)를 그대로 비교하면 된다
      const day = l.createdAt.slice(0, 10);
      if (fromDate && day < fromDate) return false;
      if (toDate && day > toDate) return false;
      if (!q) return true;
      return `${l.fromName} ${l.toName} ${l.memo ?? ''}`.toLowerCase().includes(q);
    });
  }, [likes, likeQuery, mutualOnly, fromDate, toDate]);

  /** 오늘부터 n일 전까지로 기간을 맞춘다. n=0 이면 오늘 하루. */
  const setRecentDays = (days: number) => {
    const end = new Date();
    const start = new Date();
    start.setDate(start.getDate() - days);
    const iso = (d: Date) => {
      const p = (n: number) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
    };
    setFromDate(iso(start));
    setToDate(iso(end));
  };

  const likeCounts = useMemo(() => ({
    total: likes.length,
    mutual: likes.filter((l) => l.mutual).length,
  }), [likes]);

  /** 약속이 잡힌(날짜가 있는) 것만. 캘린더와 다가오는 약속 목록에 쓴다. */
  const scheduled = useMemo(
    () => notes.filter((n) => n.dateDay != null),
    [notes],
  );

  const monthLabel = `${cursor.year}년 ${cursor.month + 1}월`;
  const monthPrefix = `${cursor.year}-${String(cursor.month + 1).padStart(2, '0')}`;

  const noteCounts = useMemo(() => {
    const today = ymd(new Date());
    return {
      scheduled: scheduled.length,
      thisMonth: scheduled.filter((n) => n.dateDay!.startsWith(monthPrefix)).length,
      upcoming: scheduled.filter((n) => n.dateDay! >= today).length,
      undecided: notes.length - scheduled.length,
    };
  }, [notes, scheduled, monthPrefix]);

  /** 오늘 이후 약속 (가까운 순). 오늘 것도 포함. */
  const upcoming = useMemo(() => {
    const today = ymd(new Date());
    return scheduled.filter((n) => n.dateDay! >= today).slice(0, 8);
  }, [scheduled]);

  const moveMonth = (step: number) => {
    setCursor((c) => {
      const d = new Date(c.year, c.month + step, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
  };

  /** 캘린더에서 약속을 누르면 같은 페어의 선택 내역 상세를 연다. */
  const openNote = (n: DatingPairNote) => {
    const row = likes.find(
      (l) =>
        (l.fromId === n.aId && l.toId === n.bId) ||
        (l.fromId === n.bId && l.toId === n.aId),
    );
    if (row) setDetailRow(row);
    else setErr('이 페어의 선택 기록을 찾을 수 없어 상세를 열 수 없습니다.');
  };

  const exportNotesCsv = () => {
    downloadCsv(
      `데이트약속_${todayStamp()}.csv`,
      ['데이트 날짜', '시간', '회원A', '회원B', '메모', '최근 수정'],
      scheduled.map((n) => [
        n.dateDay ?? '',
        n.dateHour != null ? formatHour(n.dateHour) : '',
        n.aName, n.bName,
        n.memo ?? '',
        n.updatedAt ? formatDateTime(n.updatedAt) : '',
      ]),
    );
  };

  const exportLikesCsv = () => {
    downloadCsv(
      `소개팅선택내역_${todayStamp()}.csv`,
      ['선택한 회원', '성별', '선택받은 회원', '성별', '선택한 날짜', '상대가 선택한 날짜',
        '쌍방', '쌍방 성립일', '데이트 날짜', '데이트 시간', '메모'],
      // 화면에 보이는(기간·검색 필터가 적용된) 목록만 내보낸다
      visibleLikes.map((l) => [
        l.fromName, l.fromGender === 'MALE' ? '남' : '여',
        l.toName, l.toGender === 'MALE' ? '남' : '여',
        formatDateTime(l.createdAt),
        l.partnerAt ? formatDateTime(l.partnerAt) : '',
        l.mutual ? 'O' : 'X',
        l.mutualAt ? formatDateTime(l.mutualAt) : '',
        l.dateDay ?? '',
        l.dateHour != null ? formatHour(l.dateHour) : '',
        l.memo ?? '',
      ]),
    );
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="매칭된 회원목록"
        description={
          tab === 'likes' ? (
            <>선택 {likeCounts.total}건 · 쌍방 {likeCounts.mutual}건</>
          ) : (
            <>
              약속 {noteCounts.scheduled}건 · 이번 달 {noteCounts.thisMonth} · 예정 {noteCounts.upcoming}
              {noteCounts.undecided > 0 && ` · 날짜 미정 ${noteCounts.undecided}`}
            </>
          )
        }
        actions={
          <>
            {tab === 'likes' ? (
              <Button onClick={exportLikesCsv} disabled={visibleLikes.length === 0}>
                CSV 내보내기
              </Button>
            ) : (
              <Button onClick={exportNotesCsv} disabled={scheduled.length === 0}>
                CSV 내보내기
              </Button>
            )}
            <Button variant="secondary" onClick={load} disabled={loading}>
              {loading ? '불러오는 중...' : '새로고침'}
            </Button>
          </>
        }
      />

      {/* 탭 */}
      <div className="flex gap-2 border-b border-stone-200">
        {([
          ['likes', `선택 내역 (${likeCounts.total})`],
          ['calendar', `약속 캘린더 (${noteCounts.scheduled})`],
        ] as [Tab, string][]).map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={
              'px-4 py-2 text-sm font-semibold border-b-2 -mb-px transition ' +
              (tab === key
                ? 'border-damso-500 text-damso-600'
                : 'border-transparent text-stone-400 hover:text-stone-600')
            }
          >
            {label}
          </button>
        ))}
      </div>

      {err && <div className="text-sm text-red-600">{err}</div>}

      {tab === 'likes' && (
        <>
          <div className="rounded-lg bg-sky-50 border border-sky-200 px-3 py-2 text-xs text-sky-800">
            손님이 누른 <b>선택(찜)</b> 기록입니다. 한 줄을 클릭하면 두 회원의 상세 정보를 나란히 볼 수 있습니다.
            <br />
            쌍방인 페어는 <b>여자 → 남자</b> 한 줄로만 표시되고, 남자가 선택한 시각은 날짜 칸 아래에 함께 적힙니다.
          </div>

          <Card>
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <input
                  value={likeQuery}
                  onChange={(e) => setLikeQuery(e.target.value)}
                  placeholder="이름 · 메모 검색"
                  className="flex-1 min-w-[12rem] border border-stone-300 rounded-md px-3 py-2 text-sm"
                />
                <label className="flex items-center gap-1.5 text-xs text-stone-600 cursor-pointer select-none">
                  <input type="checkbox" className="w-4 h-4 accent-damso-500"
                    checked={mutualOnly} onChange={(e) => setMutualOnly(e.target.checked)} />
                  쌍방만 보기
                </label>
                <span className="ml-auto text-xs text-stone-500">{visibleLikes.length}건 표시</span>
              </div>

              {/* 기간 검색 — 선택한 날짜 기준 */}
              <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-stone-100">
                <span className="text-xs font-semibold text-stone-500">선택한 날짜</span>
                <input
                  type="date"
                  value={fromDate}
                  max={toDate || undefined}
                  onChange={(e) => setFromDate(e.target.value)}
                  className="border border-stone-300 rounded-md px-2 py-1.5 text-sm"
                />
                <span className="text-stone-400 text-sm">~</span>
                <input
                  type="date"
                  value={toDate}
                  min={fromDate || undefined}
                  onChange={(e) => setToDate(e.target.value)}
                  className="border border-stone-300 rounded-md px-2 py-1.5 text-sm"
                />
                <div className="flex gap-1">
                  <Button size="sm" onClick={() => setRecentDays(0)}>오늘</Button>
                  <Button size="sm" onClick={() => setRecentDays(6)}>최근 7일</Button>
                  <Button size="sm" onClick={() => setRecentDays(29)}>최근 30일</Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => { setFromDate(''); setToDate(''); }}
                    disabled={!fromDate && !toDate}
                  >
                    전체
                  </Button>
                </div>
              </div>
            </div>
          </Card>

          <Card>
            {visibleLikes.length === 0 ? (
              <Empty>
                {likes.length === 0
                  ? '아직 선택한 기록이 없습니다.'
                  : '조건에 맞는 선택 기록이 없습니다.'}
              </Empty>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-stone-500 border-b border-stone-200">
                      <th className="px-4 py-2 font-semibold">선택한 회원</th>
                      <th className="px-2 py-2" />
                      <th className="px-4 py-2 font-semibold">선택받은 회원</th>
                      <th className="px-4 py-2 font-semibold whitespace-nowrap">선택한 날짜</th>
                      <th className="px-4 py-2 font-semibold whitespace-nowrap">쌍방</th>
                      <th className="px-4 py-2 font-semibold whitespace-nowrap">데이트 약속</th>
                      <th className="px-4 py-2 font-semibold">메모</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleLikes.map((l) => (
                      <tr
                        key={l.id}
                        onClick={() => setDetailRow(l)}
                        className="border-b border-stone-100 last:border-b-0 cursor-pointer hover:bg-stone-50"
                      >
                        <td className="px-4 py-2.5">
                          <span className="font-semibold text-stone-900">{l.fromName}</span>
                          <span className="ml-1.5 text-xs text-stone-400">
                            {l.fromGender === 'MALE' ? '남' : '여'} · {l.fromBirthYear}
                          </span>
                          {l.fromDeleted && (
                            <Badge className="ml-1.5 bg-red-100 text-red-700">휴지통</Badge>
                          )}
                        </td>
                        <td className="px-2 py-2.5 text-rose-500 font-bold">
                          {l.mutual ? '↔' : '→'}
                        </td>
                        <td className="px-4 py-2.5">
                          <span className="font-semibold text-stone-900">{l.toName}</span>
                          <span className="ml-1.5 text-xs text-stone-400">
                            {l.toGender === 'MALE' ? '남' : '여'} · {l.toBirthYear}
                          </span>
                          {l.toDeleted && (
                            <Badge className="ml-1.5 bg-red-100 text-red-700">휴지통</Badge>
                          )}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-stone-600 tabular-nums whitespace-nowrap">
                          {formatDateTime(l.createdAt)}
                          {l.mutual && l.partnerAt && (
                            <span className="block text-[11px] text-stone-400">
                              상대 {formatDateTime(l.partnerAt)}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-2.5 whitespace-nowrap">
                          {l.mutual ? (
                            <Badge className="bg-rose-100 text-rose-700">쌍방</Badge>
                          ) : l.status === 'ACCEPTED' ? (
                            <Badge className="bg-rose-100 text-rose-700">좋아요 수락</Badge>
                          ) : l.status === 'DECLINED' ? (
                            <Badge className="bg-stone-200 text-stone-600">미안해요</Badge>
                          ) : (
                            <Badge className="bg-amber-100 text-amber-800">답변 대기</Badge>
                          )}
                        </td>
                        <td className="px-4 py-2.5 whitespace-nowrap text-xs tabular-nums">
                          {l.dateDay || l.dateHour != null ? (
                            <Badge className="bg-violet-100 text-violet-700">
                              {l.dateDay ?? '날짜 미정'}
                              {l.dateHour != null && ` ${formatHour(l.dateHour)}`}
                            </Badge>
                          ) : (
                            <span className="text-stone-300">—</span>
                          )}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-stone-600 max-w-[16rem] truncate">
                          {l.memo || <span className="text-stone-300">—</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}

      {tab === 'calendar' && (
        <>
          <div className="rounded-lg bg-violet-50 border border-violet-200 px-3 py-2 text-xs text-violet-900">
            선택 내역 상세에서 잡은 <b>데이트 약속</b>이 날짜별로 표시됩니다. 약속을 클릭하면 그 페어의 상세가 열립니다.
          </div>

          {/* 달 이동 */}
          <Card>
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" onClick={() => moveMonth(-1)}>← 이전 달</Button>
              <span className="px-2 text-base font-bold text-stone-800 tabular-nums">
                {monthLabel}
              </span>
              <Button size="sm" onClick={() => moveMonth(1)}>다음 달 →</Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => {
                  const d = new Date();
                  setCursor({ year: d.getFullYear(), month: d.getMonth() });
                }}
              >
                오늘
              </Button>
              <span className="ml-auto text-xs text-stone-500">
                이번 달 약속 {noteCounts.thisMonth}건
              </span>
            </div>
          </Card>

          <Card>
            <AppointmentCalendar
              year={cursor.year}
              month={cursor.month}
              notes={scheduled}
              onPick={openNote}
            />
          </Card>

          {/* 다가오는 약속 */}
          <Card title="다가오는 약속">
            {upcoming.length === 0 ? (
              <Empty>예정된 약속이 없습니다. 선택 내역 상세에서 날짜와 시간을 정해보세요.</Empty>
            ) : (
              <ul className="divide-y divide-stone-100">
                {upcoming.map((n) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      onClick={() => openNote(n)}
                      className="w-full text-left flex flex-wrap items-center gap-x-3 gap-y-1 px-1 py-2.5 hover:bg-stone-50 rounded"
                    >
                      <Badge className="bg-violet-100 text-violet-700 tabular-nums">
                        {n.dateDay}
                        {n.dateHour != null && ` ${formatHour(n.dateHour)}`}
                      </Badge>
                      <span className="text-sm font-semibold text-stone-900">
                        {n.aName}
                        <span className="mx-1.5 text-rose-500">·</span>
                        {n.bName}
                      </span>
                      {/* 손님이 앱에서 직접 잡은 약속은 상태·장소를 함께 보여준다 */}
                      {n.status === 'PROPOSED' && (
                        <Badge className="bg-amber-100 text-amber-800">상대 확인 대기</Badge>
                      )}
                      {n.place && (
                        <span className="text-xs text-stone-500">📍 {n.place}</span>
                      )}
                      {n.memo && (
                        <span className="text-xs text-stone-500 truncate max-w-[24rem]">
                          {n.memo}
                        </span>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </>
      )}

      {detailRow && (
        <LikeDetailModal
          row={detailRow}
          profileById={profileById}
          onClose={() => setDetailRow(null)}
          onSaved={async () => { setDetailRow(null); await load(); }}
          onError={setErr}
        />
      )}

    </div>
  );
}