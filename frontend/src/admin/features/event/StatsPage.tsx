import { useCallback, useEffect, useState } from 'react';
import { statsApi } from '../../api/client';
import type { Stats } from '../../../shared/types';
import { PageHeader, Card, Button, StatCard, Empty } from '../../layout/ui';

// 오늘 기준 yyyy-MM-dd
function ymd(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export default function StatsPage() {
  const today = new Date();
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);

  const [from, setFrom] = useState(ymd(monthStart));
  const [to, setTo] = useState(ymd(today));
  const [allTime, setAllTime] = useState(false);
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setErr(null);
    try {
      const data = allTime ? await statsApi.get() : await statsApi.get(from, to);
      setStats(data);
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '통계를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [from, to, allTime]);

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const pct = (num: number, den: number) => (den <= 0 ? 0 : Math.round((num / den) * 1000) / 10);

  return (
    <div className="space-y-6">
      <PageHeader title="통계" description="기간 내 이벤트를 합산한 지표입니다." />

      {/* 기간 필터 */}
      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-stone-500">시작일</span>
            <input
              type="date"
              value={from}
              disabled={allTime}
              onChange={(e) => setFrom(e.target.value)}
              className="border border-stone-300 rounded-md px-3 py-2 text-sm disabled:bg-stone-100 disabled:text-stone-400"
            />
          </label>
          <span className="pb-2 text-stone-400">~</span>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-stone-500">종료일</span>
            <input
              type="date"
              value={to}
              disabled={allTime}
              onChange={(e) => setTo(e.target.value)}
              className="border border-stone-300 rounded-md px-3 py-2 text-sm disabled:bg-stone-100 disabled:text-stone-400"
            />
          </label>
          <label className="flex items-center gap-1.5 pb-2 text-sm text-stone-600">
            <input type="checkbox" checked={allTime} onChange={(e) => setAllTime(e.target.checked)} />
            전체 기간
          </label>
          <Button variant="primary" className="ml-auto" onClick={load} disabled={loading}>
            {loading ? '불러오는 중...' : '조회'}
          </Button>
        </div>
      </Card>

      {err && <div className="text-sm text-red-600">{err}</div>}

      {stats && (
        <>
          <div className="text-sm text-stone-500">
            {allTime ? '전체 기간' : `${stats.from} ~ ${stats.to}`} · 이벤트 {stats.eventCount}개 · 손님 {stats.totalCustomers}명
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* MBTI I/E 비율 */}
            <Card title="MBTI 내향(I) / 외향(E) 비율">
              {stats.mbtiI + stats.mbtiE === 0 ? (
                <Empty />
              ) : (
                <>
                  <Bar
                    leftLabel={`I ${stats.mbtiI}명`}
                    rightLabel={`E ${stats.mbtiE}명`}
                    leftPct={pct(stats.mbtiI, stats.mbtiI + stats.mbtiE)}
                  />
                  <div className="mt-2 text-sm text-stone-600">
                    I {pct(stats.mbtiI, stats.mbtiI + stats.mbtiE)}% · E {pct(stats.mbtiE, stats.mbtiI + stats.mbtiE)}%
                  </div>
                </>
              )}
            </Card>

            <StatCard
              label="호감도 지목 매칭률"
              value={`${pct(stats.preferenceMatched, stats.preferenceParticipants)}%`}
              sub={`쌍방 매칭 ${stats.preferenceMatched}명 / 지목 참여 ${stats.preferenceParticipants}명`}
              accent="text-rose-600"
            />
            <StatCard
              label="메시지 전송률"
              value={`${pct(stats.messageSenders, stats.totalCustomers)}%`}
              sub={`전송 ${stats.messageSenders}명 / 전체 ${stats.totalCustomers}명`}
              accent="text-damso-600"
            />
            <StatCard
              label="메시지 쌍방 전송률"
              value={`${pct(stats.mutualMessagePeople, stats.messageSenders)}%`}
              sub={`쌍방 ${stats.mutualMessagePeople}명 / 전송 ${stats.messageSenders}명`}
              accent="text-pink-600"
            />
          </div>
        </>
      )}
    </div>
  );
}

function Bar({ leftLabel, rightLabel, leftPct }: { leftLabel: string; rightLabel: string; leftPct: number }) {
  return (
    <div>
      <div className="flex h-6 rounded-full overflow-hidden border border-stone-200">
        <div className="bg-indigo-500" style={{ width: `${leftPct}%` }} />
        <div className="bg-orange-400 flex-1" />
      </div>
      <div className="flex justify-between mt-1 text-xs text-stone-600">
        <span>{leftLabel}</span>
        <span>{rightLabel}</span>
      </div>
    </div>
  );
}
