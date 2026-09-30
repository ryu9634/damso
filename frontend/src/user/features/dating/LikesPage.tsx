import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { DatingLikeCard, DatingMeeting, MeetingGuide, MeetingSlot } from '../../../shared/types';
import { DATE_HOURS, formatHour } from '../../../shared/types';
import { datingApi } from './dating';
import { appErrMsg } from '../../api/app';
import { useAppFeatures } from '../home/useAppFeatures';
import { useNotifications } from '../notification/useNotifications';
import PageHeading, { PageBody } from '../../layout/PageHeading';
import { INPUT, LABEL, PRIMARY_BTN, SUB_BTN } from '../../layout/authUi';
import VerifiedBadge from '../../layout/VerifiedBadge';

/**
 * 받은 좋아요 · 보낸 좋아요.
 *  - 받은 좋아요에는 '좋아요 / 미안해요'로 답한다.
 *  - 서로 좋아요가 되면 그 자리에서 만남 약속을 잡을 수 있다.
 */
type Tab = 'received' | 'sent';

export default function LikesPage() {
  const { features } = useAppFeatures();
  const { refresh: refreshBadge } = useNotifications();

  const [tab, setTab] = useState<Tab>('received');
  // undefined: 확인 중, null: 셀렉팅 프로필 없음
  const [profileId, setProfileId] = useState<number | null | undefined>(undefined);
  const [received, setReceived] = useState<DatingLikeCard[]>([]);
  const [sent, setSent] = useState<DatingLikeCard[]>([]);
  // 장소·예약금 안내와 예약 링크는 관리자 설정에서 온다.
  const [guide, setGuide] = useState<MeetingGuide | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (id: number) => {
    setLoading(true);
    try {
      const [r, s] = await Promise.all([datingApi.receivedLikes(id), datingApi.sentLikes(id)]);
      setReceived(r);
      setSent(s);
    } catch (e) {
      setError(appErrMsg(e, '목록을 불러오지 못했어요.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    datingApi.meetingGuide().then(setGuide).catch(() => setGuide(null));
  }, []);

  useEffect(() => {
    let alive = true;
    datingApi.me()
      .then((self) => {
        if (!alive) return;
        setProfileId(self ? self.id : null);
        if (self) load(self.id);
        else setLoading(false);
      })
      .catch(() => {
        if (!alive) return;
        setProfileId(null);
        setLoading(false);
      });
    return () => { alive = false; };
  }, [load]);

  const reload = async () => {
    if (typeof profileId === 'number') await load(profileId);
    await refreshBadge();
  };

  const respond = async (likeId: number, accept: boolean) => {
    if (typeof profileId !== 'number') return;
    if (!accept && !confirm('미안해요로 답할까요? 되돌릴 수 없어요.')) return;
    setError('');
    try {
      setReceived(await datingApi.respondLike(profileId, likeId, accept));
      await reload();
    } catch (e) {
      setError(appErrMsg(e, '답하지 못했어요.'));
    }
  };

  const cancelSent = async (targetProfileId: number) => {
    if (typeof profileId !== 'number') return;
    if (!confirm('보낸 좋아요를 취소할까요?')) return;
    setError('');
    try {
      await datingApi.unlike(profileId, targetProfileId);
      await reload();
    } catch (e) {
      setError(appErrMsg(e, '취소하지 못했어요.'));
    }
  };

  if (!features.selecting.enabled) {
    return (
      <>
        <PageHeading title="받은 좋아요" />
        <PageBody>
          <div className="rounded-3xl bg-white border border-stone-200 p-8 text-center">
            <div className="text-3xl">🌙</div>
            <p className="mt-3 text-sm text-stone-500 whitespace-pre-line leading-relaxed">
              {features.selecting.notice || '담소 셀렉팅은 지금 이용할 수 없어요.'}
            </p>
          </div>
        </PageBody>
      </>
    );
  }

  const list = tab === 'received' ? received : sent;
  const pendingCount = received.filter((c) => c.status === 'PENDING').length;

  return (
    <>
      <PageHeading
        title="받은 좋아요"
        description="마음을 보내온 분들이에요. 답하고 만남 약속까지 잡아보세요."
      />
      <PageBody>
        {profileId === null ? (
          <div className="rounded-3xl bg-white border border-stone-200 p-8 text-center">
            <div className="text-3xl">💌</div>
            <p className="mt-3 text-sm text-stone-500">
              담소 셀렉팅 프로필을 먼저 등록해야 좋아요를 주고받을 수 있어요.
            </p>
            <Link
              to="/selecting"
              className={PRIMARY_BTN + ' mt-5 inline-block text-center'}
            >
              프로필 등록하러 가기
            </Link>
          </div>
        ) : (
          <>
            <div className="flex rounded-full bg-stone-100 p-1">
              {(['received', 'sent'] as Tab[]).map((t) => (
                <button
                  key={t}
                  onClick={() => setTab(t)}
                  className={`flex-1 rounded-full py-2 text-sm font-semibold transition ${
                    tab === t ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-500'
                  }`}
                >
                  {t === 'received' ? '받은 좋아요' : '보낸 좋아요'}
                  {t === 'received' && pendingCount > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center rounded-full bg-damso-500 px-1.5 text-[10px] font-bold text-white">
                      {pendingCount}
                    </span>
                  )}
                </button>
              ))}
            </div>

            {error && (
              <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-600">
                {error}
              </div>
            )}

            {loading ? (
              <p className="py-10 text-center text-sm text-stone-400">불러오는 중...</p>
            ) : list.length === 0 ? (
              <div className="rounded-3xl bg-white border border-stone-200 p-10 text-center text-sm text-stone-400">
                {tab === 'received'
                  ? '아직 받은 좋아요가 없어요.'
                  : '아직 보낸 좋아요가 없어요.'}
              </div>
            ) : (
              list.map((card) => (
                <LikeCard
                  key={card.likeId}
                  card={card}
                  tab={tab}
                  guide={guide}
                  onRespond={respond}
                  onCancelSent={cancelSent}
                  meProfileId={profileId as number}
                  onChanged={reload}
                />
              ))
            )}
          </>
        )}
      </PageBody>
    </>
  );
}

const STATUS_STYLE: Record<DatingLikeCard['status'], string> = {
  PENDING: 'bg-amber-50 text-amber-700',
  ACCEPTED: 'bg-rose-50 text-rose-600',
  DECLINED: 'bg-stone-100 text-stone-500',
  EXPIRED: 'bg-stone-100 text-stone-400',
};

/**
 * 답변 기한까지 남은 시간.
 * 지났거나 기한이 없으면 null — 화면에 아무것도 띄우지 않는다.
 */
function remainingText(expiresAt: string | null): string | null {
  if (!expiresAt) return null;
  const left = new Date(expiresAt).getTime() - Date.now();
  if (Number.isNaN(left) || left <= 0) return null;
  const hours = Math.floor(left / 3600000);
  if (hours >= 1) return `${hours}시간 남음`;
  return `${Math.max(1, Math.floor(left / 60000))}분 남음`;
}

function LikeCard({
  card, tab, guide, meProfileId, onRespond, onCancelSent, onChanged,
}: {
  card: DatingLikeCard;
  tab: Tab;
  guide: MeetingGuide | null;
  meProfileId: number;
  onRespond: (likeId: number, accept: boolean) => void;
  onCancelSent: (profileId: number) => void;
  onChanged: () => Promise<void>;
}) {
  const [open, setOpen] = useState(false);

  return (
    <section className="rounded-3xl bg-white border border-stone-200 shadow-sm overflow-hidden">
      <div className="flex gap-3 p-4">
        {card.photoUrl ? (
          <img
            src={card.photoUrl}
            alt="프로필 사진"
            className="w-20 h-20 rounded-2xl object-cover bg-stone-100 shrink-0"
          />
        ) : (
          <div className="w-20 h-20 rounded-2xl bg-stone-100 flex items-center justify-center text-2xl shrink-0">
            🥂
          </div>
        )}

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="text-base font-bold text-stone-900 truncate">
              {card.name ?? `${card.birthYear}년생`}
            </span>
            <span
              className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${STATUS_STYLE[card.status]}`}
            >
              {card.statusLabel}
            </span>
          </div>
          {card.verified && (
            <div className="mt-1">
              <VerifiedBadge />
            </div>
          )}
          <div className="mt-0.5 text-xs text-stone-400">
            {card.name ? `${card.birthYear}년생 · ` : ''}
            {formatDay(card.createdAt)}
            {tab === 'received' ? ' 받음' : ' 보냄'}
          </div>

          {card.answers.length > 0 && (
            <button
              onClick={() => setOpen((v) => !v)}
              className="mt-2 text-xs font-semibold text-damso-600"
            >
              {open ? '프로필 접기' : '프로필 자세히 보기'}
            </button>
          )}
        </div>
      </div>

      {open && card.answers.length > 0 && (
        <div className="px-4 pb-4 -mt-1 space-y-1.5">
          {card.answers.map((a) => (
            <div key={a.questionId} className="flex gap-2 text-sm">
              <span className="w-20 shrink-0 text-stone-400">{a.label}</span>
              <span className="flex-1 text-stone-700 whitespace-pre-wrap break-words">
                {a.value}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* 받은 좋아요 — 아직 답하기 전 */}
      {tab === 'received' && card.status === 'PENDING' && (
        <div className="border-t border-stone-100 px-4 pt-3 text-xs text-stone-400">
          {remainingText(card.expiresAt)
            ? `답변 기한 ${remainingText(card.expiresAt)} · 기한이 지나면 자동으로 닫혀요.`
            : '곧 기한이 지나요.'}
        </div>
      )}
      {tab === 'received' && card.status === 'PENDING' && (
        <div className="flex gap-2 p-4">
          <button
            className="flex-1 rounded-full border border-stone-300 bg-white text-stone-600 py-2.5 text-sm font-semibold active:bg-stone-100"
            onClick={() => onRespond(card.likeId, false)}
          >
            🥲 미안해요
          </button>
          <button
            className="flex-1 rounded-full bg-rose-500 text-white py-2.5 text-sm font-semibold active:bg-rose-600"
            onClick={() => onRespond(card.likeId, true)}
          >
            💖 좋아요
          </button>
        </div>
      )}

      {/* 보낸 좋아요 — 상대가 답하기 전이면 취소할 수 있다 */}
      {tab === 'sent' && card.status === 'PENDING' && (
        <div className="border-t border-stone-100 p-4">
          <p className="text-xs text-stone-400">
            상대의 답을 기다리는 중이에요.
            {remainingText(card.expiresAt) ? ` (${remainingText(card.expiresAt)})` : ''}
          </p>
          <button
            className={SUB_BTN + ' mt-2'}
            onClick={() => onCancelSent(card.profileId)}
          >
            좋아요 취소
          </button>
        </div>
      )}

      {card.status === 'DECLINED' && (
        <div className="border-t border-stone-100 px-4 py-3 text-xs text-stone-400">
          {tab === 'received'
            ? '미안해요로 답한 좋아요예요.'
            : '이번엔 인연이 닿지 않았어요.'}
        </div>
      )}

      {card.status === 'EXPIRED' && (
        <div className="border-t border-stone-100 px-4 py-3 text-xs text-stone-400">
          {tab === 'received'
            ? '기한 안에 답하지 않아 닫힌 좋아요예요.'
            : '72시간 동안 답이 없어 닫혔어요. 담소 셀렉팅에서 다시 보낼 수 있어요.'}
        </div>
      )}

      {card.status === 'ACCEPTED' && (
        <MeetingBox
          meProfileId={meProfileId}
          otherProfileId={card.profileId}
          meeting={card.meeting}
          guide={guide}
          onChanged={onChanged}
        />
      )}
    </section>
  );
}

/**
 * 서로 좋아요가 된 두 사람의 만남 약속.
 *  - 제안하는 쪽은 '날짜+시간' 후보를 최대 3개까지 고른다.
 *  - 받은 쪽이 그중 하나를 골라야 확정된다.
 *  - 장소는 관리자가 정해 둔 곳으로 고정이라 손님이 입력하지 않는다.
 */
function MeetingBox({
  meProfileId, otherProfileId, meeting, guide, onChanged,
}: {
  meProfileId: number;
  otherProfileId: number;
  meeting: DatingMeeting | null;
  guide: MeetingGuide | null;
  onChanged: () => Promise<void>;
}) {
  const status = meeting?.status ?? 'NONE';
  const candidates = meeting?.slots ?? [];
  const hourChoices = guide?.hours ?? [...DATE_HOURS];
  const maxSlots = guide?.maxSlots ?? 3;
  const place = meeting?.place ?? guide?.place ?? '담소혼술바';

  const [editing, setEditing] = useState(false);
  // 제안 폼 — 날짜와 시간을 한 줄씩. 빈 줄은 보내지 않는다.
  const [rows, setRows] = useState<MeetingSlot[]>(candidates);
  // 상대가 제안한 후보 중 내가 고른 것
  const [picked, setPicked] = useState<MeetingSlot | null>(
    candidates.length === 1 ? candidates[0] : null,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const today = new Date().toISOString().slice(0, 10);
  const waitingForMe = status === 'PROPOSED' && !meeting?.proposedByMe;

  const openForm = () => {
    setRows(candidates.length > 0 ? candidates : [{ day: '', hour: hourChoices[0] }]);
    setEditing(true);
    setError('');
  };

  const setRow = (i: number, patch: Partial<MeetingSlot>) => {
    setRows((prev) => prev.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  };
  const addRow = () => {
    setRows((prev) =>
      prev.length >= maxSlots ? prev : [...prev, { day: '', hour: hourChoices[0] }]);
  };
  const removeRow = (i: number) => {
    setRows((prev) => (prev.length <= 1 ? prev : prev.filter((_, idx) => idx !== i)));
  };

  const propose = async () => {
    const slots = rows.filter((r) => r.day);
    if (slots.length === 0) {
      setError('만날 날짜와 시간을 하나 이상 골라주세요.');
      return;
    }
    const seen = new Set(slots.map((s) => `${s.day} ${s.hour}`));
    if (seen.size !== slots.length) {
      setError('같은 날짜·시간을 두 번 고르셨어요.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await datingApi.proposeMeeting(meProfileId, otherProfileId, { slots });
      setEditing(false);
      await onChanged();
    } catch (e) {
      setError(appErrMsg(e, '약속을 제안하지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    if (candidates.length > 1 && !picked) {
      setError('만날 날짜와 시간을 골라주세요.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await datingApi.confirmMeeting(
        meProfileId, otherProfileId,
        picked ?? (candidates.length === 1 ? candidates[0] : null),
      );
      await onChanged();
    } catch (e) {
      setError(appErrMsg(e, '약속을 확정하지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!confirm2('약속을 취소할까요?')) return;
    setBusy(true);
    setError('');
    try {
      await datingApi.cancelMeeting(meProfileId, otherProfileId);
      setEditing(false);
      await onChanged();
    } catch (e) {
      setError(appErrMsg(e, '약속을 취소하지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="border-t border-stone-100 bg-rose-50/50 p-4">
      <div className="text-sm font-bold text-stone-800">🍷 만남 약속</div>

      {/* 잡힌(또는 제안된) 약속 요약 */}
      {status !== 'NONE' && !editing && (
        <div className="mt-2 rounded-2xl bg-white border border-rose-100 px-4 py-3">
          {status === 'CONFIRMED' ? (
            <div className="text-sm font-bold text-stone-900">
              {formatSlotLong({ day: meeting!.dateDay!, hour: meeting!.dateHour! })}
            </div>
          ) : (
            /* 내가 제안해 상대의 선택을 기다리는 중이면 후보를 그대로 보여준다.
               (내가 고를 차례면 아래 선택 버튼이 같은 값을 보여주므로 생략한다) */
            !waitingForMe && (
              <div className="flex flex-wrap gap-1.5">
                {candidates.map((s) => (
                  <span
                    key={`${s.day} ${s.hour}`}
                    className="rounded-lg bg-rose-50 px-2 py-1 text-xs font-bold text-rose-600"
                  >
                    {formatSlot(s)}
                  </span>
                ))}
              </div>
            )
          )}

          <div className="mt-1.5 text-sm text-stone-600">📍 {place}</div>
          <div className="mt-2 text-xs font-semibold text-rose-600">
            {status === 'CONFIRMED'
              ? '약속이 확정됐어요!'
              : meeting?.proposedByMe
                ? '상대가 고르는 중이에요.'
                : '상대가 제안한 날짜예요. 하나를 골라주세요.'}
          </div>
        </div>
      )}

      {/* 내가 고를 차례 — 후보 선택 */}
      {waitingForMe && !editing && candidates.length > 1 && (
        <div className="mt-3 grid gap-2">
          {candidates.map((s) => {
            const on = picked?.day === s.day && picked?.hour === s.hour;
            return (
              <button
                key={`${s.day} ${s.hour}`}
                type="button"
                onClick={() => setPicked(s)}
                className={
                  'rounded-xl px-4 py-3 text-sm font-semibold border text-left ' +
                  (on
                    ? 'bg-rose-500 text-white border-rose-500'
                    : 'bg-white text-stone-600 border-stone-300')
                }
              >
                {formatSlotLong(s)}
              </button>
            );
          })}
        </div>
      )}

      {status === 'NONE' && !editing && (
        <p className="mt-1 text-xs text-stone-500">
          마음이 통했어요. 편한 날짜와 시간을 골라 제안해보세요.
        </p>
      )}

      {/* 제안 폼 — 날짜+시간 한 줄씩 최대 3개 */}
      {editing && (
        <div className="mt-3 space-y-3">
          <div>
            <label className={LABEL}>
              날짜와 시간{' '}
              <span className="font-normal text-stone-400">
                (최대 {maxSlots}개 · {rows.filter((r) => r.day).length}개 선택)
              </span>
            </label>
            <div className="space-y-2">
              {rows.map((row, i) => (
                <div key={i} className="flex items-center gap-2">
                  <input
                    className={INPUT + ' flex-1'}
                    type="date"
                    min={today}
                    value={row.day}
                    onChange={(e) => setRow(i, { day: e.target.value })}
                  />
                  <select
                    className={INPUT + ' w-28'}
                    value={row.hour}
                    onChange={(e) => setRow(i, { hour: Number(e.target.value) })}
                  >
                    {hourChoices.map((h) => (
                      <option key={h} value={h}>{formatHour(h)}</option>
                    ))}
                  </select>
                  {rows.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeRow(i)}
                      aria-label="지우기"
                      className="shrink-0 w-9 h-9 rounded-full border border-stone-300 bg-white text-stone-400 active:bg-stone-100"
                    >
                      ×
                    </button>
                  )}
                </div>
              ))}
            </div>
            {rows.length < maxSlots && (
              <button
                type="button"
                onClick={addRow}
                className="mt-2 w-full rounded-xl border border-dashed border-stone-300 bg-white py-2.5 text-sm font-semibold text-stone-500 active:bg-stone-50"
              >
                + 날짜 추가 ({rows.length}/{maxSlots})
              </button>
            )}
            <p className="mt-1 text-xs text-stone-400">
              고른 것 중에서 상대가 하나를 고르면 약속이 확정돼요.
            </p>
          </div>
          <div>
            <label className={LABEL}>장소</label>
            <div className="rounded-xl border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-500">
              📍 {place} · 장소는 고정이에요
            </div>
          </div>
        </div>
      )}

      {error && <p className="mt-2 text-sm text-red-500">{error}</p>}

      <div className="mt-3 flex flex-wrap gap-2">
        {editing ? (
          <>
            <button
              className="flex-1 rounded-full border border-stone-300 bg-white text-stone-700 py-2.5 text-sm font-semibold active:bg-stone-100"
              onClick={() => { setEditing(false); setError(''); }}
              disabled={busy}
            >
              취소
            </button>
            <button
              className="flex-1 rounded-full bg-damso-500 text-white py-2.5 text-sm font-semibold active:bg-damso-600 disabled:opacity-40"
              onClick={propose}
              disabled={busy}
            >
              {busy ? '보내는 중...' : '약속 제안하기'}
            </button>
          </>
        ) : (
          <>
            {waitingForMe && (
              <button
                className="flex-1 rounded-full bg-rose-500 text-white py-2.5 text-sm font-semibold active:bg-rose-600 disabled:opacity-40"
                onClick={confirm}
                disabled={busy}
              >
                이걸로 확정
              </button>
            )}
            <button
              className="flex-1 rounded-full border border-stone-300 bg-white text-stone-700 py-2.5 text-sm font-semibold active:bg-stone-100"
              onClick={openForm}
              disabled={busy}
            >
              {status === 'NONE' ? '약속 잡기' : '다른 날짜로 제안'}
            </button>
            {status !== 'NONE' && (
              <button
                className="w-full rounded-full py-2 text-xs text-stone-400 active:text-red-500"
                onClick={cancel}
                disabled={busy}
              >
                약속 취소
              </button>
            )}
          </>
        )}
      </div>

      {/* 장소·예약금 안내와 예약 링크 (관리자 설정) */}
      {guide && <BookingGuide guide={guide} />}
    </div>
  );
}

/** 예약금 안내와 예약(결제) 링크. 링크가 없으면 안내 문구만 보여준다. */
function BookingGuide({ guide }: { guide: MeetingGuide }) {
  if (!guide.notice && !guide.bookingUrl) return null;
  return (
    <div className="mt-3 rounded-2xl bg-white border border-rose-100 px-4 py-3">
      {guide.notice && (
        <p className="text-xs text-stone-600 whitespace-pre-line leading-relaxed">
          {guide.notice}
        </p>
      )}
      {guide.bookingUrl && (
        <>
          {guide.bookingNotice && (
            <p className="mt-2 text-xs font-semibold text-stone-700">{guide.bookingNotice}</p>
          )}
          <a
            href={guide.bookingUrl}
            target="_blank"
            rel="noreferrer"
            className="mt-2 block w-full rounded-full bg-stone-900 text-white py-2.5 text-center text-sm font-semibold active:bg-stone-700"
          >
            예약하러 가기
          </a>
        </>
      )}
    </div>
  );
}

/** 2026-09-05 19시 → "9/5(금) 19:00" */
function formatSlot(slot: MeetingSlot): string {
  const d = new Date(`${slot.day}T00:00:00`);
  const dow = '일월화수목금토'[d.getDay()];
  return `${d.getMonth() + 1}/${d.getDate()}(${dow}) ${formatHour(slot.hour)}`;
}

/** 2026-09-05 19시 → "9월 5일(금) 19:00" */
function formatSlotLong(slot: MeetingSlot): string {
  const d = new Date(`${slot.day}T00:00:00`);
  const dow = '일월화수목금토'[d.getDay()];
  return `${d.getMonth() + 1}월 ${d.getDate()}일(${dow}) ${formatHour(slot.hour)}`;
}

/** window.confirm 을 감싼다. (컴포넌트 안의 confirm 이름과 겹치지 않게) */
function confirm2(message: string): boolean {
  return window.confirm(message);
}

/** 2026-08-27T21:00 → 8월 27일 */
function formatDay(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}
