import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  datingAdminApi,
  datingQuestionApi,
  type DatingProfileUpdateRequest,
} from '../../api/client';
import {
  PageHeader, Card, Button, Empty, Badge, Input, Select, Textarea, Field, Modal, SlideOver,
} from '../../layout/ui';
import {
  DATING_STATUS_LABEL, DATING_STATUS_ORDER, DATING_STATUS_STYLE,
  type DatingAdminProfile, type DatingQuestion, type DatingStatus, type Gender,
} from '../../../shared/types';
import {
  RETENTION_MONTHS, ageOf, daysSince, formatDate, formatDateTime, isRetentionExpired, maskContact,
} from '../../../shared/utils/privacy';
import { downloadCsv, todayStamp } from '../../../shared/utils/csv';
import { formatPhone } from '../../../shared/utils/phone';

// ─── 필터/정렬 옵션 ────────────────────────────────────────
type GenderFilter = 'ALL' | Gender;
type LikeFilter = 'ALL' | 'MUTUAL' | 'RECEIVED' | 'GIVEN' | 'NONE';
type SortKey = 'RECENT' | 'OLDEST' | 'NAME' | 'RECEIVED' | 'AGE_ASC' | 'AGE_DESC';

const LIKE_FILTER_LABEL: Record<LikeFilter, string> = {
  ALL: '찜 전체',
  MUTUAL: '쌍방 있음',
  RECEIVED: '받은 찜 있음',
  GIVEN: '찜한 적 있음',
  NONE: '활동 없음',
};

const SORT_LABEL: Record<SortKey, string> = {
  RECENT: '최신 등록순',
  OLDEST: '오래된 등록순',
  NAME: '이름순',
  RECEIVED: '받은 찜 많은순',
  AGE_ASC: '나이 어린순',
  AGE_DESC: '나이 많은순',
};

// ─── 작은 표시 컴포넌트 ────────────────────────────────────
function GenderBadge({ gender }: { gender: Gender }) {
  return (
    <Badge className={gender === 'MALE' ? 'bg-sky-100 text-sky-700' : 'bg-pink-100 text-pink-700'}>
      {gender === 'MALE' ? '남' : '여'}
    </Badge>
  );
}

function StatusBadge({ status }: { status: DatingStatus }) {
  return <Badge className={DATING_STATUS_STYLE[status]}>{DATING_STATUS_LABEL[status]}</Badge>;
}

function Photo({ url, name, size = 'w-10 h-10' }: { url: string | null; name: string; size?: string }) {
  if (url) {
    return (
      <img src={url} alt={name} className={`${size} rounded-lg object-cover border border-stone-200`} />
    );
  }
  return (
    <div className={`${size} rounded-lg bg-stone-100 border border-stone-200 flex items-center justify-center text-[10px] text-stone-400`}>
      없음
    </div>
  );
}

/** 마스킹된 연락처 — 클릭하면 원문이 보인다. */
function Contact({
  value, revealed, onReveal,
}: {
  value: string;
  revealed: boolean;
  onReveal: () => void;
}) {
  if (revealed) return <span className="select-text tabular-nums">{value}</span>;
  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onReveal(); }}
      className="tabular-nums text-stone-500 hover:text-stone-800 underline decoration-dotted underline-offset-2"
      title="클릭하면 연락처가 보입니다"
    >
      {maskContact(value)}
    </button>
  );
}

// ─── 수정 폼 ───────────────────────────────────────────────
interface EditDraft {
  name: string;
  contact: string;
  birthYear: string;
  gender: Gender;
  photoUrl: string;
  answers: Record<number, string>;
}

function toDraft(p: DatingAdminProfile): EditDraft {
  const answers: Record<number, string> = {};
  p.answers.forEach((a) => { answers[a.questionId] = a.value; });
  return {
    name: p.name,
    contact: p.contact,
    birthYear: String(p.birthYear),
    gender: p.gender,
    photoUrl: p.photoUrl ?? '',
    answers,
  };
}

function EditModal({
  profile, questions, onClose, onSaved,
}: {
  profile: DatingAdminProfile;
  questions: DatingQuestion[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [draft, setDraft] = useState<EditDraft>(() => toDraft(profile));
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const setAnswer = (qid: number, value: string) =>
    setDraft((d) => ({ ...d, answers: { ...d.answers, [qid]: value } }));

  const save = async () => {
    const year = Number(draft.birthYear);
    if (!draft.name.trim()) { setErr('이름을 입력하세요.'); return; }
    if (!draft.contact.trim()) { setErr('연락처를 입력하세요.'); return; }
    if (!Number.isFinite(year) || year < 1940 || year > 2012) {
      setErr('출생년도를 확인해주세요. (1940~2012)');
      return;
    }
    const req: DatingProfileUpdateRequest = {
      name: draft.name.trim(),
      contact: draft.contact.trim(),
      birthYear: year,
      gender: draft.gender,
      photoUrl: draft.photoUrl.trim() || null,
      answers: questions.map((q) => ({
        questionId: q.id,
        value: (draft.answers[q.id] ?? '').trim(),
      })),
    };
    setBusy(true); setErr(null);
    try {
      await datingAdminApi.update(profile.id, req);
      onSaved();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '저장에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      title={`회원 정보 수정 — ${profile.name}`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" onClick={save} disabled={busy}>
            {busy ? '저장 중...' : '저장'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label="이름">
            <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
          </Field>
          <Field label="연락처">
            <Input
              value={draft.contact}
              onChange={(e) => setDraft({ ...draft, contact: formatPhone(e.target.value) })}
              placeholder="010-0000-0000"
            />
          </Field>
          <Field label="출생년도">
            <Input
              value={draft.birthYear}
              inputMode="numeric"
              onChange={(e) => setDraft({ ...draft, birthYear: e.target.value.replace(/\D/g, '').slice(0, 4) })}
            />
          </Field>
          <Field label="성별">
            <Select
              value={draft.gender}
              onChange={(e) => setDraft({ ...draft, gender: e.target.value as Gender })}
            >
              <option value="MALE">남성</option>
              <option value="FEMALE">여성</option>
            </Select>
          </Field>
        </div>

        <Field label="사진 경로" hint="손님이 업로드한 경로입니다. 비우면 사진 없음으로 처리됩니다.">
          <Input
            value={draft.photoUrl}
            onChange={(e) => setDraft({ ...draft, photoUrl: e.target.value })}
            placeholder="/uploads/xxxx.jpg"
          />
        </Field>

        {questions.length > 0 && (
          <div className="pt-2 border-t border-stone-100 space-y-3">
            <div className="text-xs font-bold text-stone-500">상세 질문 답변</div>
            {questions.map((q) => {
              const value = draft.answers[q.id] ?? '';
              return (
                <Field key={q.id} label={<>{q.label}{q.required && <span className="text-red-500"> *</span>}</>}>
                  {q.type === 'TEXTAREA' ? (
                    <Textarea rows={3} value={value} onChange={(e) => setAnswer(q.id, e.target.value)} />
                  ) : q.type === 'SELECT' ? (
                    <Select value={value} onChange={(e) => setAnswer(q.id, e.target.value)}>
                      <option value="">선택 안 함</option>
                      {q.options.map((o) => <option key={o} value={o}>{o}</option>)}
                    </Select>
                  ) : (
                    <Input
                      value={value}
                      inputMode={q.type === 'NUMBER' ? 'numeric' : undefined}
                      onChange={(e) =>
                        setAnswer(q.id, q.type === 'NUMBER' ? e.target.value.replace(/\D/g, '') : e.target.value)
                      }
                    />
                  )}
                </Field>
              );
            })}
          </div>
        )}

        {err && <div className="text-sm text-red-600">{err}</div>}
      </div>
    </Modal>
  );
}

// ─── 상세 패널 ─────────────────────────────────────────────
function DetailPanel({
  profile, onClose, onChanged, onEdit,
}: {
  profile: DatingAdminProfile;
  onClose: () => void;
  onChanged: () => void;
  onEdit: () => void;
}) {
  const [memo, setMemo] = useState(profile.adminMemo ?? '');
  const [savingMemo, setSavingMemo] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => { setMemo(profile.adminMemo ?? ''); }, [profile.id, profile.adminMemo]);

  const run = async (fn: () => Promise<unknown>, confirmText?: string) => {
    if (confirmText && !confirm(confirmText)) return;
    setErr(null);
    try {
      await fn();
      onChanged();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '처리에 실패했습니다.');
    }
  };

  const saveMemo = async () => {
    setSavingMemo(true);
    await run(() => datingAdminApi.setMemo(profile.id, memo));
    setSavingMemo(false);
  };

  const deleted = profile.deletedAt != null;
  const expired = isRetentionExpired(profile.createdAt);

  return (
    <SlideOver
      open
      title={
        <span className="flex items-center gap-2">
          {profile.name}
          <GenderBadge gender={profile.gender} />
          <StatusBadge status={profile.status} />
        </span>
      }
      subtitle={`${profile.birthYear}년생 (만 ${ageOf(profile.birthYear) - 1}세 전후) · 등록 ${formatDateTime(profile.createdAt)}`}
      onClose={onClose}
      footer={
        deleted ? (
          <>
            <Button onClick={() => run(() => datingAdminApi.restore(profile.id))}>복구</Button>
            <Button
              variant="danger"
              onClick={() => run(
                () => datingAdminApi.hardDelete(profile.id),
                `'${profile.name}' 을(를) 완전히 삭제할까요?\n찜·답변·매칭 이력까지 사라지며 복구할 수 없습니다.`,
              )}
            >
              완전 삭제
            </Button>
          </>
        ) : (
          <>
            <Button onClick={onEdit}>정보 수정</Button>
            <Button
              variant="danger"
              onClick={() => run(
                () => datingAdminApi.delete(profile.id),
                `'${profile.name}' 을(를) 휴지통으로 옮길까요? 나중에 복구할 수 있습니다.`,
              )}
            >
              삭제 (휴지통)
            </Button>
          </>
        )
      }
    >
      {deleted && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-700">
          {formatDateTime(profile.deletedAt!)} 에 삭제된 회원입니다. 손님 열람 목록에서 제외돼 있어요.
        </div>
      )}
      {expired && !deleted && (
        <div className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-800">
          등록한 지 {daysSince(profile.createdAt)}일 지났습니다. (보관기간 {RETENTION_MONTHS}개월 경과 — 정리 대상)
        </div>
      )}

      {/* 기본 정보 */}
      <div className="flex gap-4">
        <Photo url={profile.photoUrl} name={profile.name} size="w-28 h-28" />
        <div className="min-w-0 flex-1 space-y-1 text-sm">
          <div className="text-stone-500 text-xs">연락처</div>
          <div className="font-semibold text-stone-800 select-text tabular-nums">{profile.contact}</div>
        </div>
      </div>

      {/* 상태 */}
      <Field label="관리 상태">
        <Select
          value={profile.status}
          onChange={(e) => run(() => datingAdminApi.setStatus(profile.id, e.target.value as DatingStatus))}
        >
          {DATING_STATUS_ORDER.map((s) => (
            <option key={s} value={s}>{DATING_STATUS_LABEL[s]}</option>
          ))}
        </Select>
      </Field>

      {/* 매장 대면 확인 */}
      <Field
        label="매장 확인"
        hint="매장에서 직접 얼굴을 확인한 회원만 켜주세요. 손님 카드에 '✅ 매장 확인' 배지가 붙습니다."
      >
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant={profile.verifiedAt ? 'primary' : 'ghost'}
            onClick={() => run(() => datingAdminApi.setVerified(profile.id, !profile.verifiedAt))}
          >
            {profile.verifiedAt ? '✅ 확인됨 (누르면 해제)' : '매장 확인 표시하기'}
          </Button>
          {profile.verifiedAt && (
            <span className="text-xs text-stone-400">{formatDate(profile.verifiedAt)} 확인</span>
          )}
        </div>
      </Field>

      {profile.hiddenAt && (
        <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">
          본인이 프로필 노출을 꺼둔 상태입니다. ({formatDate(profile.hiddenAt)}부터) 목록에 보이지 않습니다.
        </div>
      )}

      {/* 메모 */}
      <Field label="관리자 메모" hint="손님에게는 보이지 않습니다. 최대 500자.">
        <Textarea
          rows={3}
          maxLength={500}
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          placeholder="예: 10/3 연락처 전달 완료, 카톡 응답 느림"
        />
      </Field>
      <div className="-mt-3 flex justify-end">
        <Button size="sm" variant="primary" onClick={saveMemo} disabled={savingMemo || memo === (profile.adminMemo ?? '')}>
          {savingMemo ? '저장 중...' : '메모 저장'}
        </Button>
      </div>

      {/* 답변 */}
      <div>
        <div className="text-xs font-bold text-stone-500 mb-2">상세 답변</div>
        {profile.answers.length === 0 ? (
          <p className="text-xs text-stone-400">작성된 답변이 없습니다.</p>
        ) : (
          <dl className="space-y-2">
            {profile.answers.map((a) => (
              <div key={a.questionId} className="grid grid-cols-[7rem_1fr] gap-2 text-xs">
                <dt className="text-stone-500 font-semibold break-words">{a.label}</dt>
                <dd className="text-stone-800 whitespace-pre-wrap break-words">{a.value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>

      {/* 찜 현황 */}
      <div className="space-y-2 pt-3 border-t border-stone-100">
        <div className="text-xs font-bold text-stone-500">찜 현황</div>
        <NameRow label="찜한 상대" names={profile.likedNames} />
        <NameRow label="나를 찜" names={profile.likedByNames} />
        <NameRow label="쌍방" names={profile.mutualNames} accent />
      </div>

      {err && <div className="text-sm text-red-600">{err}</div>}
    </SlideOver>
  );
}

function NameRow({ label, names, accent }: { label: string; names: string[]; accent?: boolean }) {
  return (
    <div className="flex gap-2 text-xs">
      <span className="w-16 shrink-0 text-stone-500 font-semibold">{label}</span>
      {names.length === 0 ? (
        <span className="text-stone-400">—</span>
      ) : (
        <span className="flex flex-wrap gap-1">
          {names.map((n) => (
            <Badge key={n} className={accent ? 'bg-rose-100 text-rose-700' : 'bg-stone-100 text-stone-600'}>
              {n}
            </Badge>
          ))}
        </span>
      )}
    </div>
  );
}

// ─── 페이지 ────────────────────────────────────────────────
export default function DatingAdminPage() {
  const [profiles, setProfiles] = useState<DatingAdminProfile[]>([]);
  const [questions, setQuestions] = useState<DatingQuestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // 탭: 정상 / 휴지통
  const [trash, setTrash] = useState(false);

  // 필터
  const [query, setQuery] = useState('');
  const [genderFilter, setGenderFilter] = useState<GenderFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | DatingStatus>('ALL');
  const [likeFilter, setLikeFilter] = useState<LikeFilter>('ALL');
  const [photoOnly, setPhotoOnly] = useState(false);
  const [expiredOnly, setExpiredOnly] = useState(false);
  const [sort, setSort] = useState<SortKey>('RECENT');

  // 선택 / 상세 / 수정
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [detailId, setDetailId] = useState<number | null>(null);
  const [editId, setEditId] = useState<number | null>(null);
  const [revealAll, setRevealAll] = useState(false);
  const [revealed, setRevealed] = useState<Set<number>>(new Set());
  const [bulkStatus, setBulkStatus] = useState<DatingStatus>('ACTIVE');

  const load = useCallback(async () => {
    setLoading(true); setErr(null);
    try {
      const data = await datingAdminApi.list(true); // 휴지통 포함해 한 번에 받아 탭으로 분리
      setProfiles(data);
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { datingQuestionApi.list().then(setQuestions).catch(() => {}); }, []);

  // 탭 전환 시 선택 초기화
  useEffect(() => { setSelected(new Set()); }, [trash]);

  const scoped = useMemo(
    () => profiles.filter((p) => (trash ? p.deletedAt != null : p.deletedAt == null)),
    [profiles, trash],
  );

  const summary = useMemo(() => {
    const live = profiles.filter((p) => p.deletedAt == null);
    const mutualPeople = live.filter((p) => p.mutualNames.length > 0).length;
    return {
      total: live.length,
      male: live.filter((p) => p.gender === 'MALE').length,
      female: live.filter((p) => p.gender === 'FEMALE').length,
      mutual: mutualPeople,
      idle: live.filter((p) => p.likedNames.length === 0 && p.likedByNames.length === 0).length,
      expired: live.filter((p) => isRetentionExpired(p.createdAt)).length,
      trash: profiles.length - live.length,
    };
  }, [profiles]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const digits = q.replace(/\D/g, '');

    const matched = scoped.filter((p) => {
      if (genderFilter !== 'ALL' && p.gender !== genderFilter) return false;
      if (statusFilter !== 'ALL' && p.status !== statusFilter) return false;
      if (photoOnly && !p.photoUrl) return false;
      if (expiredOnly && !isRetentionExpired(p.createdAt)) return false;

      if (likeFilter === 'MUTUAL' && p.mutualNames.length === 0) return false;
      if (likeFilter === 'RECEIVED' && p.likedByNames.length === 0) return false;
      if (likeFilter === 'GIVEN' && p.likedNames.length === 0) return false;
      if (likeFilter === 'NONE' && (p.likedNames.length > 0 || p.likedByNames.length > 0)) return false;

      if (!q) return true;
      const haystack = [
        p.name,
        p.contact,
        p.adminMemo ?? '',
        String(p.birthYear),
        ...p.answers.map((a) => `${a.label} ${a.value}`),
        ...p.likedNames, ...p.likedByNames,
      ].join(' ').toLowerCase();
      if (haystack.includes(q)) return true;
      return digits.length >= 3 && p.contact.replace(/\D/g, '').includes(digits);
    });

    const sorted = [...matched];
    sorted.sort((a, b) => {
      switch (sort) {
        case 'OLDEST': return a.createdAt.localeCompare(b.createdAt);
        case 'NAME': return a.name.localeCompare(b.name, 'ko');
        case 'RECEIVED': return b.likedByNames.length - a.likedByNames.length
          || b.createdAt.localeCompare(a.createdAt);
        case 'AGE_ASC': return b.birthYear - a.birthYear;
        case 'AGE_DESC': return a.birthYear - b.birthYear;
        default: return b.createdAt.localeCompare(a.createdAt);
      }
    });
    return sorted;
  }, [scoped, query, genderFilter, statusFilter, likeFilter, photoOnly, expiredOnly, sort]);

  const detail = useMemo(
    () => profiles.find((p) => p.id === detailId) ?? null,
    [profiles, detailId],
  );
  const editing = useMemo(
    () => profiles.find((p) => p.id === editId) ?? null,
    [profiles, editId],
  );

  // ─── 선택 ───────────────────────────────────────────────
  const allSelected = filtered.length > 0 && filtered.every((p) => selected.has(p.id));
  const toggleAll = () =>
    setSelected(allSelected ? new Set() : new Set(filtered.map((p) => p.id)));
  const toggleOne = (id: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });

  const selectedIds = useMemo(
    () => filtered.filter((p) => selected.has(p.id)).map((p) => p.id),
    [filtered, selected],
  );

  // ─── 액션 ───────────────────────────────────────────────
  const run = async (fn: () => Promise<unknown>, confirmText?: string) => {
    if (confirmText && !confirm(confirmText)) return;
    setErr(null);
    try {
      await fn();
      await load();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '처리에 실패했습니다.');
    }
  };

  const applyBulkStatus = () =>
    run(async () => {
      await datingAdminApi.bulkStatus(selectedIds, bulkStatus);
      setSelected(new Set());
    });

  const bulkDelete = () =>
    run(async () => {
      await datingAdminApi.bulkDelete(selectedIds);
      setSelected(new Set());
    }, `선택한 ${selectedIds.length}명을 휴지통으로 옮길까요?`);

  const bulkRestore = () =>
    run(async () => {
      await datingAdminApi.bulkRestore(selectedIds);
      setSelected(new Set());
    });

  const bulkHardDelete = () =>
    run(async () => {
      await datingAdminApi.bulkHardDelete(selectedIds);
      setSelected(new Set());
    }, `선택한 ${selectedIds.length}명을 완전히 삭제할까요?\n찜·답변·매칭 이력까지 사라지며 복구할 수 없습니다.`);

  const exportCsv = (rows: DatingAdminProfile[]) => {
    const headers = [
      '이름', '성별', '출생년도', '연락처', '상태', '등록일',
      '찜한 수', '받은 찜 수', '쌍방', '관리자 메모',
      ...questions.map((q) => q.label),
    ];
    const body = rows.map((p) => {
      const byId = new Map(p.answers.map((a) => [a.questionId, a.value]));
      return [
        p.name,
        p.gender === 'MALE' ? '남' : '여',
        p.birthYear,
        p.contact,
        DATING_STATUS_LABEL[p.status],
        formatDate(p.createdAt),
        p.likedNames.length,
        p.likedByNames.length,
        p.mutualNames.join(' / '),
        p.adminMemo ?? '',
        ...questions.map((q) => byId.get(q.id) ?? ''),
      ];
    });
    downloadCsv(`소개팅회원_${todayStamp()}.csv`, headers, body);
  };

  const resetFilters = () => {
    setQuery(''); setGenderFilter('ALL'); setStatusFilter('ALL');
    setLikeFilter('ALL'); setPhotoOnly(false); setExpiredOnly(false); setSort('RECENT');
  };

  const filterActive =
    query !== '' || genderFilter !== 'ALL' || statusFilter !== 'ALL' ||
    likeFilter !== 'ALL' || photoOnly || expiredOnly;

  const isRevealed = (id: number) => revealAll || revealed.has(id);
  const reveal = (id: number) => setRevealed((prev) => new Set(prev).add(id));

  return (
    <div className="space-y-5">
      <PageHeader
        title="등록된 회원정보"
        description={
          <>
            전체 {summary.total}명 · 남 {summary.male} · 여 {summary.female} · 쌍방 있음{' '}
            {summary.mutual} · 활동 없음 {summary.idle}
            {summary.expired > 0 && <> · <span className="text-amber-700 font-semibold">보관기간 경과 {summary.expired}</span></>}
          </>
        }
        actions={
          <>
            <Button onClick={() => exportCsv(filtered)} disabled={filtered.length === 0}>
              CSV 내보내기
            </Button>
            <Button variant="secondary" onClick={load} disabled={loading}>
              {loading ? '불러오는 중...' : '새로고침'}
            </Button>
          </>
        }
      />

      {/* 탭 */}
      <div className="flex items-center gap-1 border-b border-stone-200">
        {([false, true] as const).map((t) => (
          <button
            key={String(t)}
            onClick={() => setTrash(t)}
            className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px transition ${
              trash === t
                ? 'border-damso-500 text-damso-700'
                : 'border-transparent text-stone-500 hover:text-stone-800'
            }`}
          >
            {t ? `휴지통 (${summary.trash})` : `회원 목록 (${summary.total})`}
          </button>
        ))}
      </div>

      {/* 검색 · 필터 · 정렬 */}
      <Card>
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="이름 · 연락처 · 답변 · 메모 통합 검색"
              className="flex-1 min-w-[14rem]"
            />
            <Select
              value={sort}
              onChange={(e) => setSort(e.target.value as SortKey)}
              className="w-auto"
            >
              {(Object.keys(SORT_LABEL) as SortKey[]).map((k) => (
                <option key={k} value={k}>{SORT_LABEL[k]}</option>
              ))}
            </Select>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Select
              value={genderFilter}
              onChange={(e) => setGenderFilter(e.target.value as GenderFilter)}
              className="w-auto"
            >
              <option value="ALL">성별 전체</option>
              <option value="MALE">남성</option>
              <option value="FEMALE">여성</option>
            </Select>

            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as 'ALL' | DatingStatus)}
              className="w-auto"
            >
              <option value="ALL">상태 전체</option>
              {DATING_STATUS_ORDER.map((s) => (
                <option key={s} value={s}>{DATING_STATUS_LABEL[s]}</option>
              ))}
            </Select>

            <Select
              value={likeFilter}
              onChange={(e) => setLikeFilter(e.target.value as LikeFilter)}
              className="w-auto"
            >
              {(Object.keys(LIKE_FILTER_LABEL) as LikeFilter[]).map((k) => (
                <option key={k} value={k}>{LIKE_FILTER_LABEL[k]}</option>
              ))}
            </Select>

            <label className="flex items-center gap-1.5 text-xs text-stone-600 cursor-pointer select-none">
              <input type="checkbox" className="w-4 h-4 accent-damso-500"
                checked={photoOnly} onChange={(e) => setPhotoOnly(e.target.checked)} />
              사진 있는 회원만
            </label>

            <label className="flex items-center gap-1.5 text-xs text-stone-600 cursor-pointer select-none">
              <input type="checkbox" className="w-4 h-4 accent-damso-500"
                checked={expiredOnly} onChange={(e) => setExpiredOnly(e.target.checked)} />
              보관기간 {RETENTION_MONTHS}개월 경과
            </label>

            <label className="flex items-center gap-1.5 text-xs text-stone-600 cursor-pointer select-none">
              <input type="checkbox" className="w-4 h-4 accent-damso-500"
                checked={revealAll} onChange={(e) => setRevealAll(e.target.checked)} />
              연락처 전체 표시
            </label>

            {filterActive && (
              <Button size="sm" variant="ghost" onClick={resetFilters}>필터 초기화</Button>
            )}
            <span className="ml-auto text-xs text-stone-500">
              {filtered.length}명 표시
            </span>
          </div>
        </div>
      </Card>

      {err && <div className="text-sm text-red-600">{err}</div>}

      {/* 일괄 작업 바 */}
      {selectedIds.length > 0 && (
        <div className="sticky top-2 z-20 flex flex-wrap items-center gap-2 rounded-xl border border-damso-300 bg-damso-50 px-4 py-2.5 shadow-sm">
          <span className="text-sm font-bold text-damso-800">{selectedIds.length}명 선택</span>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>선택 해제</Button>
          <span className="w-px h-5 bg-damso-200" />
          {trash ? (
            <>
              <Button size="sm" onClick={bulkRestore}>복구</Button>
              <Button size="sm" variant="danger" onClick={bulkHardDelete}>완전 삭제</Button>
            </>
          ) : (
            <>
              <Select
                value={bulkStatus}
                onChange={(e) => setBulkStatus(e.target.value as DatingStatus)}
                className="w-auto py-1 text-xs"
              >
                {DATING_STATUS_ORDER.map((s) => (
                  <option key={s} value={s}>{DATING_STATUS_LABEL[s]}</option>
                ))}
              </Select>
              <Button size="sm" variant="primary" onClick={applyBulkStatus}>상태 일괄 변경</Button>
              <Button size="sm" variant="danger" onClick={bulkDelete}>휴지통으로</Button>
            </>
          )}
          <Button
            size="sm"
            onClick={() => exportCsv(filtered.filter((p) => selected.has(p.id)))}
          >
            선택 CSV
          </Button>
        </div>
      )}

      {/* 목록 테이블 */}
      <Card className="overflow-hidden">
        {filtered.length === 0 ? (
          <Empty>
            {scoped.length === 0
              ? (trash ? '휴지통이 비어 있습니다.' : '등록된 소개팅 프로필이 없습니다.')
              : '조건에 맞는 회원이 없습니다.'}
          </Empty>
        ) : (
          <div className="overflow-x-auto -m-5">
            <table className="w-full text-sm">
              <thead className="bg-stone-50 text-stone-500 text-xs">
                <tr>
                  <th className="w-10 px-3 py-2.5">
                    <input type="checkbox" className="w-4 h-4 accent-damso-500"
                      checked={allSelected} onChange={toggleAll} aria-label="전체 선택" />
                  </th>
                  <th className="px-2 py-2.5 text-left font-semibold">회원</th>
                  <th className="px-2 py-2.5 text-left font-semibold">연락처</th>
                  <th className="px-2 py-2.5 text-left font-semibold">상태</th>
                  <th className="px-2 py-2.5 text-center font-semibold whitespace-nowrap">찜 / 받음 / 쌍방</th>
                  <th className="px-2 py-2.5 text-left font-semibold">메모</th>
                  <th className="px-2 py-2.5 text-left font-semibold whitespace-nowrap">등록일</th>
                  <th className="px-3 py-2.5 text-right font-semibold">관리</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {filtered.map((p) => {
                  const expired = isRetentionExpired(p.createdAt);
                  return (
                    <tr
                      key={p.id}
                      onClick={() => setDetailId(p.id)}
                      className={`cursor-pointer transition hover:bg-stone-50 ${
                        selected.has(p.id) ? 'bg-damso-50/60' : ''
                      }`}
                    >
                      <td className="px-3 py-2.5" onClick={(e) => e.stopPropagation()}>
                        <input type="checkbox" className="w-4 h-4 accent-damso-500"
                          checked={selected.has(p.id)} onChange={() => toggleOne(p.id)}
                          aria-label={`${p.name} 선택`} />
                      </td>

                      <td className="px-2 py-2.5">
                        <div className="flex items-center gap-2.5 min-w-[11rem]">
                          <Photo url={p.photoUrl} name={p.name} />
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <span className="font-semibold text-stone-900 truncate">{p.name}</span>
                              <GenderBadge gender={p.gender} />
                              {p.verifiedAt && (
                                <span title="매장에서 확인한 회원" className="text-xs">✅</span>
                              )}
                              {p.hiddenAt && (
                                <span title="본인이 노출을 꺼둠" className="text-xs">🙈</span>
                              )}
                            </div>
                            <div className="text-xs text-stone-500">
                              {p.birthYear}년생
                            </div>
                          </div>
                        </div>
                      </td>

                      <td className="px-2 py-2.5 text-xs whitespace-nowrap">
                        <Contact value={p.contact} revealed={isRevealed(p.id)} onReveal={() => reveal(p.id)} />
                      </td>

                      <td className="px-2 py-2.5" onClick={(e) => e.stopPropagation()}>
                        {trash ? (
                          <StatusBadge status={p.status} />
                        ) : (
                          <Select
                            value={p.status}
                            onChange={(e) =>
                              run(() => datingAdminApi.setStatus(p.id, e.target.value as DatingStatus))
                            }
                            className="w-auto py-1 text-xs"
                          >
                            {DATING_STATUS_ORDER.map((s) => (
                              <option key={s} value={s}>{DATING_STATUS_LABEL[s]}</option>
                            ))}
                          </Select>
                        )}
                      </td>

                      <td className="px-2 py-2.5 text-center text-xs whitespace-nowrap tabular-nums">
                        <span className="text-stone-500">{p.likedNames.length}</span>
                        <span className="text-stone-300"> / </span>
                        <span className="text-stone-700 font-semibold">{p.likedByNames.length}</span>
                        <span className="text-stone-300"> / </span>
                        <span className={p.mutualNames.length ? 'text-rose-600 font-bold' : 'text-stone-400'}>
                          {p.mutualNames.length}
                        </span>
                      </td>

                      <td className="px-2 py-2.5 text-xs text-stone-500 max-w-[12rem]">
                        <span className="line-clamp-2 break-words">{p.adminMemo || '—'}</span>
                      </td>

                      <td className="px-2 py-2.5 text-xs text-stone-500 whitespace-nowrap">
                        {formatDate(p.createdAt)}
                        {expired && (
                          <Badge className="ml-1 bg-amber-100 text-amber-800">{RETENTION_MONTHS}개월+</Badge>
                        )}
                      </td>

                      <td className="px-3 py-2.5 text-right whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                        {trash ? (
                          <>
                            <button
                              className="text-xs text-stone-700 hover:underline px-1"
                              onClick={() => run(() => datingAdminApi.restore(p.id))}
                            >
                              복구
                            </button>
                            <button
                              className="text-xs text-red-600 hover:underline px-1"
                              onClick={() => run(
                                () => datingAdminApi.hardDelete(p.id),
                                `'${p.name}' 을(를) 완전히 삭제할까요? 복구할 수 없습니다.`,
                              )}
                            >
                              완전삭제
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              className="text-xs text-stone-700 hover:underline px-1"
                              onClick={() => setEditId(p.id)}
                            >
                              수정
                            </button>
                            <button
                              className="text-xs text-red-600 hover:underline px-1"
                              onClick={() => run(
                                () => datingAdminApi.delete(p.id),
                                `'${p.name}' 을(를) 휴지통으로 옮길까요?`,
                              )}
                            >
                              삭제
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {detail && (
        <DetailPanel
          profile={detail}
          onClose={() => setDetailId(null)}
          onChanged={load}
          onEdit={() => { setEditId(detail.id); setDetailId(null); }}
        />
      )}

      {editing && (
        <EditModal
          profile={editing}
          questions={questions}
          onClose={() => setEditId(null)}
          onSaved={async () => { setEditId(null); await load(); }}
        />
      )}
    </div>
  );
}