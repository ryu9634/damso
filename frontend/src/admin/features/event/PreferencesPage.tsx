import { useEffect, useMemo, useState } from 'react';
import { customerApi, preferenceApi } from '../../api/client';
import { useEvent } from '../../hooks/useEvent';
import type { Customer, Preference } from '../../../shared/types';
import { PageHeader } from '../../layout/ui';

type MutualPair = { a: string; b: string; aRank: number; bRank: number };

/** 쌍방 구간. 약한 쪽 순위(= 두 순위 중 큰 값)로 묶는다. */
const MUTUAL_TIERS: { tier: 1 | 2 | 3; label: string; icon: string; head: string; chip: string }[] = [
  { tier: 1, label: '쌍방 1순위', icon: '💕', head: 'text-rose-700', chip: 'border-rose-400' },
  { tier: 2, label: '쌍방 2순위', icon: '💗', head: 'text-rose-600', chip: 'border-rose-300' },
  { tier: 3, label: '쌍방 3순위', icon: '💓', head: 'text-rose-500', chip: 'border-rose-200' },
];

/** 칩 안에 붙는 '몇 순위로 지목했는지' 배지. */
function RankBadge({ rank }: { rank: number }) {
  return (
    <span className="px-1.5 py-0.5 rounded bg-rose-100 text-rose-700 text-[10px] font-bold leading-none">
      {rank}순위
    </span>
  );
}

export default function PreferencesPage() {
  const { event } = useEvent();
  const [prefs, setPrefs] = useState<Preference[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);

  const refresh = async () => {
    if (!event) return;
    const [p, c] = await Promise.all([
      preferenceApi.list(event.id),
      customerApi.list(event.id),
    ]);
    setPrefs(p);
    setCustomers(c);
  };

  useEffect(() => {
    refresh();
    if (!event) return;
    const t = setInterval(refresh, 3000);
    return () => clearInterval(t);
    /* eslint-disable-next-line */
  }, [event?.id]);

  const submitted = useMemo(() => new Set(prefs.map((p) => p.voterId)), [prefs]);
  const pending = customers.filter((c) => !submitted.has(c.id));
  const pendingM = pending.filter((c) => c.gender === 'MALE');
  const pendingF = pending.filter((c) => c.gender === 'FEMALE');

  const genderByNickname = useMemo(() => {
    const m = new Map<string, 'MALE' | 'FEMALE'>();
    customers.forEach((c) => m.set(c.nickname, c.gender));
    return m;
  }, [customers]);

  // 받은 지목 카운트 (인기 순) — 1순위 3점, 2순위 2점, 3순위 1점
  const popularity = new Map<string, { first: number; second: number; third: number }>();
  const bump = (nick: string | null, rank: 'first' | 'second' | 'third') => {
    if (!nick) return;
    const c = popularity.get(nick) ?? { first: 0, second: 0, third: 0 };
    c[rank]++;
    popularity.set(nick, c);
  };
  for (const p of prefs) {
    bump(p.firstChoiceNickname, 'first');
    bump(p.secondChoiceNickname, 'second');
    bump(p.thirdChoiceNickname, 'third');
  }
  const popular = Array.from(popularity.entries())
    .map(([nickname, c]) => ({ nickname, ...c, score: c.first * 3 + c.second * 2 + c.third }))
    .sort((a, b) => b.score - a.score);

  // 쌍방 지목 찾기: 1순위 쌍방 vs 그 외 쌍방(2·3순위 포함)으로 분리
  // rankOf[voter][target] = 1|2|3 (지목 순위)
  const rankOf = new Map<string, Map<string, number>>();
  for (const p of prefs) {
    const m = new Map<string, number>();
    if (p.firstChoiceNickname) m.set(p.firstChoiceNickname, 1);
    if (p.secondChoiceNickname) m.set(p.secondChoiceNickname, 2);
    if (p.thirdChoiceNickname) m.set(p.thirdChoiceNickname, 3);
    rankOf.set(p.voterNickname, m);
  }
  // 쌍방을 약한 쪽 순위로 묶는다. 1·2순위로 서로 지목했으면 '쌍방 2순위'.
  // 칩에 양쪽 순위를 같이 찍으므로 어느 조합인지는 화면에서 바로 보인다.
  const mutualByTier: Record<1 | 2 | 3, MutualPair[]> = { 1: [], 2: [], 3: [] };
  const seen = new Set<string>();
  for (const [a, aPicks] of rankOf) {
    for (const b of aPicks.keys()) {
      const bRank = rankOf.get(b)?.get(a);
      if (bRank == null) continue; // b 가 a 를 지목 안 함 → 쌍방 아님
      const key = [a, b].sort().join('|');
      if (seen.has(key)) continue;
      seen.add(key);
      const aRank = aPicks.get(b)!;
      const tier = Math.max(aRank, bRank) as 1 | 2 | 3;
      mutualByTier[tier].push({ a, b, aRank, bRank });
    }
  }
  const mutualTotal = mutualByTier[1].length + mutualByTier[2].length + mutualByTier[3].length;

  return (
    <div className="space-y-6">
      <PageHeader title="호감도 지목 현황" description={<>제출 {prefs.length} / 전체 {customers.length}명 · 미제출 {pending.length}명</>} actions={<span className="text-sm text-stone-500">실시간 (3초마다 새로고침)</span>} />

      {pending.length > 0 && (
        <section className="bg-amber-50 border border-amber-200 rounded-lg p-4">
          <div className="text-sm font-semibold text-amber-800 mb-2">⏳ 아직 지목 안한 손님</div>
          <div className="space-y-1.5">
            <PendingRow label="여성" tone="pink" customers={pendingF} />
            <PendingRow label="남성" tone="blue" customers={pendingM} />
          </div>
        </section>
      )}

      {mutualTotal > 0 && (
        <section className="bg-rose-50 border border-rose-200 rounded-lg p-4 space-y-3">
          {MUTUAL_TIERS.map((t) => (
            <div key={t.tier}>
              <div className={`text-sm font-semibold mb-2 ${t.head}`}>
                {t.icon} {t.label} ({mutualByTier[t.tier].length}쌍)
              </div>
              <div className="flex flex-wrap gap-2 min-h-[1.75rem]">
                {mutualByTier[t.tier].length === 0 && <span className="text-xs text-rose-300">아직 없음</span>}
                {mutualByTier[t.tier].map((m, i) => (
                  <span key={i} className={`px-3 py-1 bg-white border rounded-full text-sm inline-flex items-center gap-1.5 ${t.chip}`}>
                    <NameWithGender name={m.a} gender={genderByNickname.get(m.a)} />
                    <RankBadge rank={m.aRank} />
                    <span>↔</span>
                    <NameWithGender name={m.b} gender={genderByNickname.get(m.b)} />
                    <RankBadge rank={m.bRank} />
                  </span>
                ))}
              </div>
            </div>
          ))}
        </section>
      )}

      <div className="grid grid-cols-2 gap-6">
        <section className="bg-white rounded-lg border border-stone-200">
          <div className="px-4 py-3 border-b border-stone-200 text-sm font-semibold">지목 내역</div>
          <table className="w-full text-sm">
            <thead className="bg-stone-50 text-xs text-stone-600">
              <tr>
                <th className="text-left px-3 py-2">지목자</th>
                <th className="text-left px-3 py-2">1순위</th>
                <th className="text-left px-3 py-2">2순위</th>
                <th className="text-left px-3 py-2">3순위</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {prefs.length === 0 && (
                <tr><td colSpan={4} className="p-6 text-center text-stone-400">아직 지목이 없습니다.</td></tr>
              )}
              {prefs.map((p) => (
                <tr key={p.id} className="hover:bg-stone-50">
                  <td className="px-3 py-2 font-medium">
                    <NameWithGender name={p.voterNickname} gender={genderByNickname.get(p.voterNickname)} />
                  </td>
                  <td className="px-3 py-2">
                    <NameWithGender name={p.firstChoiceNickname} gender={genderByNickname.get(p.firstChoiceNickname)} tone="damso" />
                  </td>
                  <td className="px-3 py-2">
                    {p.secondChoiceNickname
                      ? <NameWithGender name={p.secondChoiceNickname} gender={genderByNickname.get(p.secondChoiceNickname)} tone="stone" />
                      : <span className="text-stone-400 text-xs">—</span>}
                  </td>
                  <td className="px-3 py-2">
                    {p.thirdChoiceNickname
                      ? <NameWithGender name={p.thirdChoiceNickname} gender={genderByNickname.get(p.thirdChoiceNickname)} tone="stone" />
                      : <span className="text-stone-400 text-xs">—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="bg-white rounded-lg border border-stone-200">
          <div className="px-4 py-3 border-b border-stone-200 text-sm font-semibold">인기 순위</div>
          <table className="w-full text-sm">
            <thead className="bg-stone-50 text-xs text-stone-600">
              <tr>
                <th className="text-left px-3 py-2">#</th>
                <th className="text-left px-3 py-2">닉네임</th>
                <th className="text-right px-3 py-2">1순위</th>
                <th className="text-right px-3 py-2">2순위</th>
                <th className="text-right px-3 py-2">3순위</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {popular.length === 0 && (
                <tr><td colSpan={5} className="p-6 text-center text-stone-400">데이터 없음</td></tr>
              )}
              {popular.map((row, i) => (
                <tr key={row.nickname} className="hover:bg-stone-50">
                  <td className="px-3 py-2 text-stone-500">{i + 1}</td>
                  <td className="px-3 py-2">
                    <NameWithGender name={row.nickname} gender={genderByNickname.get(row.nickname)} />
                  </td>
                  <td className="px-3 py-2 text-right">{row.first}</td>
                  <td className="px-3 py-2 text-right text-stone-500">{row.second}</td>
                  <td className="px-3 py-2 text-right text-stone-400">{row.third}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </div>
  );
}

function NameWithGender({
  name, gender, tone,
}: {
  name: string;
  gender?: 'MALE' | 'FEMALE';
  tone?: 'damso' | 'stone';
}) {
  const dot = gender === 'MALE'
    ? 'bg-sky-400 border-sky-500'
    : gender === 'FEMALE'
    ? 'bg-pink-300 border-pink-400'
    : 'bg-stone-300 border-stone-400';
  const wrap = tone === 'damso'
    ? 'bg-damso-100 text-damso-700'
    : tone === 'stone'
    ? 'bg-stone-100 text-stone-700'
    : '';
  return (
    <span className={`inline-flex items-center gap-1 ${wrap ? `px-2 py-0.5 rounded text-xs ${wrap}` : ''}`}>
      <span className={`inline-block w-2 h-2 rounded-full border ${dot}`} />
      <span className={tone ? '' : 'font-medium'}>{name}</span>
    </span>
  );
}

function PendingRow({
  label, tone, customers,
}: { label: string; tone: 'blue' | 'pink'; customers: Customer[] }) {
  const toneCls = tone === 'blue' ? 'bg-blue-100 text-blue-800 border-blue-300' : 'bg-pink-100 text-pink-800 border-pink-300';
  return (
    <div className="flex items-start gap-2">
      <div className="text-xs font-medium w-10 pt-1 text-stone-600">{label}</div>
      <div className="flex flex-wrap gap-1 flex-1">
        {customers.length === 0 && <span className="text-xs text-stone-400 py-1">전원 제출 완료</span>}
        {customers.map((c) => (
          <span key={c.id} className={`px-2 py-0.5 rounded border text-xs font-medium ${toneCls}`}>
            {c.nickname}
          </span>
        ))}
      </div>
    </div>
  );
}
