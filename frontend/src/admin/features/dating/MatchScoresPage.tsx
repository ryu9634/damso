import { useEffect, useMemo, useState } from 'react';
import { matchScoreApi } from '../../api/client';
import { useEvent } from '../../hooks/useEvent';
import type { MatchScoreMatrix, PersonBrief } from '../../../shared/types';
import { PageHeader, Button } from '../../layout/ui';

export default function MatchScoresPage() {
  const { event } = useEvent();
  const [data, setData] = useState<MatchScoreMatrix | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = async () => {
    if (!event) return;
    setLoading(true);
    try {
      setData(await matchScoreApi.matrix(event.id));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [event?.id]);

  if (!data) {
    return <div className="text-stone-500">불러오는 중...</div>;
  }

  if (data.males.length === 0 || data.females.length === 0) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-bold">매칭 점수</h1>
        <div className="bg-white border border-stone-200 rounded-lg p-10 text-center text-stone-500">
          남성 {data.males.length}명 · 여성 {data.females.length}명<br />
          점수 계산하려면 남녀 손님이 모두 입장해야 합니다.
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="매칭 점수"
        description={<>남 {data.males.length} × 여 {data.females.length} 모든 쌍의 점수</>}
        actions={<Button variant="secondary" onClick={refresh} disabled={loading}>{loading ? '계산 중...' : '새로고침'}</Button>}
      />

      <ScoreTable
        title="① MBTI 궁합 점수"
        subtitle="제공된 매트릭스 기반 · 0~20점"
        legend={[
          { color: 'bg-stone-100', label: '0~5' },
          { color: 'bg-amber-100', label: '6~10' },
          { color: 'bg-emerald-100', label: '11~15' },
          { color: 'bg-emerald-300', label: '16~20' },
        ]}
        males={data.males}
        females={data.females}
        matrix={data.mbtiMatrix}
        max={20}
        toneFor={mbtiTone}
      />

      <ScoreTable
        title="② 가치관 일치 점수"
        subtitle="2지선다 문항 답변 일치 비율 · 0~80점"
        legend={[
          { color: 'bg-stone-100', label: '0~20' },
          { color: 'bg-sky-100', label: '21~40' },
          { color: 'bg-sky-200', label: '41~60' },
          { color: 'bg-sky-400', label: '61~80' },
        ]}
        males={data.males}
        females={data.females}
        matrix={data.valuesMatrix}
        max={80}
        toneFor={valuesTone}
      />

      <ScoreTable
        title="③ 합산 점수 (100점 만점)"
        subtitle="MBTI + 가치관 · 각 남성의 최고 점수와 전체 최고 점수 하이라이트"
        legend={[
          { color: 'bg-rose-50', label: '낮음' },
          { color: 'bg-rose-300', label: '중간' },
          { color: 'bg-rose-500 text-white', label: '높음 / Top' },
        ]}
        males={data.males}
        females={data.females}
        matrix={data.totalMatrix}
        max={100}
        toneFor={totalTone}
        highlightBest
      />
    </div>
  );
}

function ScoreTable({
  title, subtitle, legend, males, females, matrix, max, toneFor, highlightBest,
}: {
  title: string;
  subtitle: string;
  legend: { color: string; label: string }[];
  males: PersonBrief[];
  females: PersonBrief[];
  matrix: number[][];
  max: number;
  toneFor: (score: number, max: number) => string;
  highlightBest?: boolean;
}) {
  // 각 남성(컬럼)의 최고 점수, 전체 최고 점수
  const { perColMax, overallMax } = useMemo(() => {
    let overall = -1;
    const cols = males.map((_, i) => {
      let mx = -1;
      for (let j = 0; j < females.length; j++) mx = Math.max(mx, matrix[i][j]);
      overall = Math.max(overall, mx);
      return mx;
    });
    return { perColMax: cols, overallMax: overall };
  }, [males, females, matrix]);

  return (
    <section className="bg-white border border-stone-200 rounded-lg overflow-hidden">
      <div className="px-5 py-3 border-b border-stone-200 flex items-end justify-between flex-wrap gap-2">
        <div>
          <h2 className="font-bold text-stone-900">{title}</h2>
          <p className="text-xs text-stone-500 mt-0.5">{subtitle}</p>
        </div>
        <div className="flex items-center gap-2 text-[10px]">
          {legend.map((l, i) => (
            <span key={i} className={`px-2 py-0.5 rounded ${l.color}`}>{l.label}</span>
          ))}
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="bg-stone-50">
              <th className="sticky left-0 bg-stone-50 px-2 py-2 text-left border-b border-r border-stone-200 z-10">
                <div className="text-[10px] text-stone-500">↓ 여성 / 남성 →</div>
              </th>
              {males.map((m) => (
                <th key={m.id} className="px-2 py-2 border-b border-stone-200 min-w-[70px]">
                  <div className="font-bold text-blue-700">{m.nickname}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {females.map((f, j) => (
              <tr key={f.id}>
                <th className="sticky left-0 bg-white px-2 py-2 text-left border-b border-r border-stone-200 z-10">
                  <div className="font-bold text-pink-700">{f.nickname}</div>
                </th>
                {males.map((m, i) => {
                  const score = matrix[i][j];
                  const isColBest = highlightBest && score === perColMax[i] && score > 0;
                  const isOverallBest = highlightBest && score === overallMax && score > 0;
                  const tone = toneFor(score, max);
                  const ring = isOverallBest
                    ? 'ring-2 ring-amber-500 ring-offset-1'
                    : isColBest
                    ? 'ring-1 ring-stone-700'
                    : '';
                  return (
                    <td key={m.id} className="border-b border-stone-100 p-0.5 text-center">
                      <div className={`rounded ${tone} ${ring} font-bold py-2`}>
                        {score}
                        {isOverallBest && <span className="ml-0.5 text-[9px]">★</span>}
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function mbtiTone(score: number, _max: number): string {
  if (score >= 16) return 'bg-emerald-300 text-emerald-900';
  if (score >= 11) return 'bg-emerald-100 text-emerald-800';
  if (score >= 6) return 'bg-amber-100 text-amber-800';
  return 'bg-stone-100 text-stone-600';
}

function valuesTone(score: number, _max: number): string {
  if (score >= 61) return 'bg-sky-400 text-white';
  if (score >= 41) return 'bg-sky-200 text-sky-900';
  if (score >= 21) return 'bg-sky-100 text-sky-800';
  return 'bg-stone-100 text-stone-600';
}

function totalTone(score: number, max: number): string {
  const ratio = score / max;
  if (ratio >= 0.75) return 'bg-rose-500 text-white';
  if (ratio >= 0.5) return 'bg-rose-300 text-rose-900';
  if (ratio >= 0.3) return 'bg-rose-100 text-rose-800';
  return 'bg-rose-50 text-stone-600';
}