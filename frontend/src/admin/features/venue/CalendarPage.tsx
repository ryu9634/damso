import { useEffect, useMemo, useState } from 'react';
import type { CalendarEntry, CalendarEntryType } from '../../../shared/types';
import { CALENDAR_TYPE_EMOJI, CALENDAR_TYPE_LABEL } from '../../../shared/types';
import { calendarApi, type CalendarEntryInput } from '../../api/client';
import {
  PageHeader, Card, Button, Empty, Input, Textarea, Select, Field, Badge, Modal,
} from '../../layout/ui';

/**
 * 가게 캘린더 — 행사 · 휴일 관리.
 *  - 여기 등록한 일정이 손님 앱 홈의 '이번 주'에 그대로 보인다.
 *  - 담소 셀렉팅에서 잡힌 만남 약속은 손님 본인에게만 보이며, 관리자는
 *    '손님 선택 소개팅 → 매칭된 회원목록'의 약속 캘린더에서 본다.
 */

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const ymd = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

const TYPE_STYLE: Record<CalendarEntryType, string> = {
  EVENT: 'bg-violet-50 border-violet-200 text-violet-700 hover:bg-violet-100',
  HOLIDAY: 'bg-stone-100 border-stone-300 text-stone-600 hover:bg-stone-200',
};

const TYPE_BADGE: Record<CalendarEntryType, string> = {
  EVENT: 'bg-violet-100 text-violet-700',
  HOLIDAY: 'bg-stone-200 text-stone-600',
};

/** 하루짜리면 시작일, 여러 날이면 마지막 날. */
function lastDay(e: CalendarEntry): string {
  return e.endDay && e.endDay > e.startDay ? e.endDay : e.startDay;
}

function covers(e: CalendarEntry, day: string): boolean {
  return e.startDay <= day && day <= lastDay(e);
}

export default function CalendarPage() {
  const today = new Date();
  const [cursor, setCursor] = useState({ year: today.getFullYear(), month: today.getMonth() });
  const [entries, setEntries] = useState<CalendarEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 편집 중인 일정. 'new' 면 새로 만드는 중(시작일만 미리 정해 둔다)
  const [editing, setEditing] = useState<CalendarEntry | null>(null);
  const [creatingOn, setCreatingOn] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      setEntries(await calendarApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const notify = (text: string) => {
    setMsg(text);
    setTimeout(() => setMsg(null), 2500);
  };

  const fail = (e: any, fallback: string) => {
    setError(e?.response?.data?.message || fallback);
    setTimeout(() => setError(null), 4000);
  };

  const save = async (input: CalendarEntryInput) => {
    try {
      if (editing) await calendarApi.update(editing.id, input);
      else await calendarApi.create(input);
      await load();
      setEditing(null);
      setCreatingOn(null);
      notify(editing ? '일정을 수정했습니다.' : '일정을 등록했습니다.');
    } catch (e) {
      fail(e, '저장하지 못했습니다.');
    }
  };

  const remove = async (entry: CalendarEntry) => {
    if (!confirm(`'${entry.title}' 일정을 지울까요?`)) return;
    try {
      await calendarApi.remove(entry.id);
      await load();
      setEditing(null);
      notify('일정을 지웠습니다.');
    } catch (e) {
      fail(e, '지우지 못했습니다.');
    }
  };

  // 그 달 1일이 있는 주의 일요일부터 6주(42칸)
  const cells = useMemo(() => {
    const start = new Date(cursor.year, cursor.month, 1);
    start.setDate(1 - start.getDay());
    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      return d;
    });
  }, [cursor]);

  const todayKey = ymd(today);
  const monthPrefix = `${cursor.year}-${String(cursor.month + 1).padStart(2, '0')}`;
  const monthCount = entries.filter((e) => e.startDay.startsWith(monthPrefix)).length;
  const upcoming = entries.filter((e) => lastDay(e) >= todayKey).slice(0, 10);

  const moveMonth = (step: -1 | 1) => {
    const d = new Date(cursor.year, cursor.month + step, 1);
    setCursor({ year: d.getFullYear(), month: d.getMonth() });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="캘린더"
        description="행사와 휴일을 등록하면 손님 앱 홈의 '이번 주'에 그대로 보여요."
        actions={
          <Button variant="primary" onClick={() => { setEditing(null); setCreatingOn(todayKey); }}>
            + 일정 추가
          </Button>
        }
      />

      {msg && (
        <div className="text-sm text-damso-700 bg-damso-50 border border-damso-200 rounded-md px-4 py-2">
          {msg}
        </div>
      )}
      {error && (
        <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-4 py-2">
          {error}
        </div>
      )}

      <Card>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" onClick={() => moveMonth(-1)}>← 이전</Button>
          <span className="text-base font-bold text-stone-900 tabular-nums px-1">
            {cursor.year}년 {cursor.month + 1}월
          </span>
          <Button size="sm" onClick={() => moveMonth(1)}>다음 →</Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setCursor({ year: today.getFullYear(), month: today.getMonth() })}
          >
            오늘
          </Button>
          <span className="ml-auto text-xs text-stone-500">이번 달 일정 {monthCount}건</span>
        </div>
      </Card>

      <Card>
        {loading ? (
          <Empty>불러오는 중...</Empty>
        ) : (
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
                  const inMonth = d.getMonth() === cursor.month;
                  const items = entries.filter((e) => covers(e, key));
                  return (
                    <div
                      key={key}
                      onClick={() => { setEditing(null); setCreatingOn(key); }}
                      className={
                        'border-r border-b border-stone-200 min-h-[6.5rem] p-1.5 align-top cursor-pointer transition hover:bg-damso-50/40 ' +
                        (inMonth ? 'bg-white' : 'bg-stone-50/60')
                      }
                    >
                      <div className="mb-1">
                        <span
                          className={
                            'text-xs font-semibold tabular-nums ' +
                            (!inMonth
                              ? 'text-stone-300'
                              : key === todayKey
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
                      </div>

                      <div className="space-y-1">
                        {items.map((e) => (
                          <button
                            key={e.id}
                            type="button"
                            onClick={(ev) => {
                              ev.stopPropagation();
                              setCreatingOn(null);
                              setEditing(e);
                            }}
                            title={e.description ?? undefined}
                            className={`w-full text-left rounded border px-1.5 py-1 transition ${TYPE_STYLE[e.type]}`}
                          >
                            <span className="block text-[11px] font-bold leading-tight break-keep">
                              {e.emoji} {e.title}
                            </span>
                            {e.timeText && (
                              <span className="block text-[11px] opacity-80 tabular-nums">
                                {e.timeText}
                              </span>
                            )}
                          </button>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
              <p className="mt-2 text-xs text-stone-400">
                날짜 칸을 누르면 그 날짜로 일정을 추가하고, 일정을 누르면 수정합니다.
              </p>
            </div>
          </div>
        )}
      </Card>

      <Card title="다가오는 일정">
        {upcoming.length === 0 ? (
          <Empty>예정된 일정이 없습니다.</Empty>
        ) : (
          <ul className="divide-y divide-stone-100">
            {upcoming.map((e) => (
              <li key={e.id}>
                <button
                  type="button"
                  onClick={() => { setCreatingOn(null); setEditing(e); }}
                  className="w-full text-left flex flex-wrap items-center gap-x-3 gap-y-1 px-1 py-2.5 hover:bg-stone-50 rounded"
                >
                  <Badge className={TYPE_BADGE[e.type]}>{e.typeLabel}</Badge>
                  <span className="text-xs text-stone-500 tabular-nums">
                    {e.startDay}
                    {e.endDay && e.endDay > e.startDay ? ` ~ ${e.endDay}` : ''}
                  </span>
                  <span className="text-sm font-semibold text-stone-900">
                    {e.emoji} {e.title}
                  </span>
                  {e.timeText && <span className="text-xs text-stone-500">{e.timeText}</span>}
                  {e.description && (
                    <span className="text-xs text-stone-400 truncate max-w-[20rem]">
                      {e.description}
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <EntryModal
        open={editing !== null || creatingOn !== null}
        entry={editing}
        defaultDay={creatingOn ?? todayKey}
        onClose={() => { setEditing(null); setCreatingOn(null); }}
        onSave={save}
        onDelete={editing ? () => remove(editing) : undefined}
      />
    </div>
  );
}

/** 일정 등록 · 수정 모달. */
function EntryModal({
  open, entry, defaultDay, onClose, onSave, onDelete,
}: {
  open: boolean;
  entry: CalendarEntry | null;
  defaultDay: string;
  onClose: () => void;
  onSave: (input: CalendarEntryInput) => void | Promise<void>;
  onDelete?: () => void;
}) {
  const [type, setType] = useState<CalendarEntryType>('EVENT');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startDay, setStartDay] = useState(defaultDay);
  const [endDay, setEndDay] = useState('');
  const [timeText, setTimeText] = useState('');
  const [emoji, setEmoji] = useState('');

  // 모달을 열 때마다 대상에 맞춰 폼을 다시 채운다.
  useEffect(() => {
    if (!open) return;
    setType(entry?.type ?? 'EVENT');
    setTitle(entry?.title ?? '');
    setDescription(entry?.description ?? '');
    setStartDay(entry?.startDay ?? defaultDay);
    setEndDay(entry?.endDay ?? '');
    setTimeText(entry?.timeText ?? '');
    setEmoji(entry?.emoji ?? '');
  }, [open, entry, defaultDay]);

  const submit = () =>
    onSave({
      type,
      title: title.trim(),
      description: description.trim() || null,
      startDay,
      endDay: endDay || null,
      timeText: timeText.trim() || null,
      emoji: emoji.trim() || null,
    });

  return (
    <Modal
      open={open}
      title={entry ? '일정 수정' : '일정 추가'}
      onClose={onClose}
      width="max-w-xl"
      footer={
        <>
          {onDelete && <Button variant="danger" onClick={onDelete}>삭제</Button>}
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" disabled={!title.trim() || !startDay} onClick={submit}>
            저장
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="종류">
            <Select
              value={type}
              onChange={(e) => setType(e.target.value as CalendarEntryType)}
            >
              {(['EVENT', 'HOLIDAY'] as CalendarEntryType[]).map((t) => (
                <option key={t} value={t}>
                  {CALENDAR_TYPE_EMOJI[t]} {CALENDAR_TYPE_LABEL[t]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="이모지" hint={`비워두면 ${CALENDAR_TYPE_EMOJI[type]} 로 표시돼요.`}>
            <Input
              value={emoji}
              maxLength={16}
              placeholder={CALENDAR_TYPE_EMOJI[type]}
              onChange={(e) => setEmoji(e.target.value)}
            />
          </Field>
        </div>

        <Field label="제목">
          <Input
            value={title}
            maxLength={60}
            placeholder={type === 'HOLIDAY' ? '예: 정기 휴무' : '예: 위스키 시음회'}
            onChange={(e) => setTitle(e.target.value)}
          />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="시작일">
            <Input type="date" value={startDay} onChange={(e) => setStartDay(e.target.value)} />
          </Field>
          <Field label="끝나는 날" hint="하루짜리면 비워두세요.">
            <Input
              type="date"
              value={endDay}
              min={startDay}
              onChange={(e) => setEndDay(e.target.value)}
            />
          </Field>
        </div>

        <Field label="시간 안내" hint="예: 19:00 시작 · 하루 종일이면 비워두세요.">
          <Input
            value={timeText}
            maxLength={30}
            onChange={(e) => setTimeText(e.target.value)}
          />
        </Field>

        <Field label="설명" hint="홈에서는 시간 안내가 없을 때만 이 문구가 보여요.">
          <Textarea
            rows={2}
            maxLength={200}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>
      </div>
    </Modal>
  );
}
