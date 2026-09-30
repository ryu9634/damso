import { useEffect, useState } from 'react';
import { dashboardApi, eventApi } from '../../api/client';
import { useEvent } from '../../hooks/useEvent';
import type { Dashboard, EventStatus } from '../../../shared/types';
import { STATUS_LABEL } from '../../../shared/types';
import { PageHeader, Card, Button } from '../../layout/ui';

const STATUSES: EventStatus[] = [
  'REGISTRATION', 'ROUND_1', 'ROUND_2', 'ROUND_3', 'PREFERENCE', 'ROUND_4', 'MESSAGE', 'CLOSED',
];

export default function DashboardPage() {
  const { event, setEvent, refresh: refreshEvents } = useEvent();
  const [data, setData] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('담소혼술바 매칭 이벤트');
  const [creating, setCreating] = useState(false);

  const refresh = async (eventId: number) => {
    setLoading(true);
    try {
      const d = await dashboardApi.get(eventId);
      setData(d);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!event) return;
    refresh(event.id);
    const t = setInterval(() => refresh(event.id), 5000);
    return () => clearInterval(t);
  }, [event?.id]);

  const handleStatus = async (s: EventStatus) => {
    if (!event) return;
    const fromLabel = STATUS_LABEL[event.status];
    const toLabel = STATUS_LABEL[s];
    if (!confirm(`이벤트 단계를 [${fromLabel}] → [${toLabel}] 로 변경할까요?`)) return;
    const updated = await eventApi.updateStatus(event.id, s);
    setEvent(updated);
    await refreshEvents();
    refresh(event.id);
  };

  const handleClear = async () => {
    if (!event) return;
    if (!confirm('현재 이벤트의 모든 손님 · 좌석 · 지목 · 메시지 데이터를 삭제합니다. 진행하시겠습니까?')) return;
    await eventApi.clear(event.id);
    await refreshEvents();
    refresh(event.id);
  };

  const submitNew = async () => {
    const name = newName.trim() || '담소혼술바 매칭 이벤트';
    setCreating(true);
    try {
      const created = await eventApi.startNew(name);
      await refreshEvents();
      setEvent(created);
      refresh(created.id);
      setShowCreate(false);
      setNewName('담소혼술바 매칭 이벤트');
    } finally {
      setCreating(false);
    }
  };

  if (!event || !data) {
    return <div className="text-stone-500">불러오는 중...</div>;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={event.name}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span>#{event.id}</span>
            <span>·</span>
            <span>생성: {new Date(event.createdAt).toLocaleString('ko-KR')}</span>
            <span>·</span>
            <span>현재 단계: {STATUS_LABEL[data.status]}</span>
          </span>
        }
        actions={
          <>
            <Button variant="danger" onClick={handleClear}>데이터 초기화</Button>
            <Button variant="secondary" onClick={() => setShowCreate((v) => !v)}>+ 새 이벤트 시작</Button>
            <Button variant="secondary" onClick={() => refresh(event.id)} disabled={loading}>
              {loading ? '새로고침...' : '새로고침'}
            </Button>
          </>
        }
      />

      {showCreate && (
        <div className="bg-white border border-damso-300 rounded-lg p-4 flex items-center gap-2">
          <span className="text-sm font-medium text-stone-700 whitespace-nowrap">새 이벤트 이름:</span>
          <input
            autoFocus
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submitNew();
              else if (e.key === 'Escape') setShowCreate(false);
            }}
            placeholder="예: 5월 31일 토요일 매칭"
            className="flex-1 border border-stone-300 rounded px-3 py-1.5 text-sm"
          />
          <button onClick={submitNew} disabled={creating}
            className="px-4 py-1.5 text-sm bg-damso-500 text-white rounded hover:bg-damso-600 disabled:opacity-50">
            {creating ? '생성 중...' : '시작'}
          </button>
          <button onClick={() => setShowCreate(false)}
            className="px-3 py-1.5 text-sm border border-stone-300 rounded hover:bg-stone-100">
            취소
          </button>
        </div>
      )}

      <Card title="진행 단계">
        <div className="flex flex-wrap gap-2">
          {STATUSES.map((s) => (
            <button
              key={s}
              onClick={() => handleStatus(s)}
              className={`px-3 py-1.5 text-sm rounded-md border transition ${
                data.status === s
                  ? 'bg-damso-500 text-white border-damso-500'
                  : 'bg-white border-stone-300 text-stone-700 hover:bg-stone-100'
              }`}
            >
              {STATUS_LABEL[s]}
            </button>
          ))}
        </div>
      </Card>

      <section className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="입장 인원" value={data.totalCustomers} unit="명" />
        <StatCard label="남성" value={data.maleCount} unit="명" tone="blue" />
        <StatCard label="여성" value={data.femaleCount} unit="명" tone="pink" />
        <StatCard label="성비" value={ratio(data.maleCount, data.femaleCount)} unit="" />

        <StatCard label="1차 배치" value={data.round1Assigned} unit="명" />
        <StatCard label="2차 배치" value={data.round2Assigned} unit="명" />
        <StatCard label="3차 배치" value={data.round3Assigned} unit="명" />
        <StatCard label="4차 배치" value={data.round4Assigned} unit="명" />
        <StatCard label="호감도 제출" value={data.preferenceSubmitted} unit="명" />
      </section>

      <Card title="최종 메시지">
        <div className="text-3xl font-bold text-stone-900">{data.messageCount}건</div>
      </Card>
    </div>
  );
}

function ratio(m: number, f: number) {
  if (m === 0 && f === 0) return '0:0';
  return `${m}:${f}`;
}

function StatCard({
  label, value, unit, tone,
}: { label: string; value: number | string; unit: string; tone?: 'blue' | 'pink' }) {
  const toneCls =
    tone === 'blue' ? 'border-blue-200 bg-blue-50' :
    tone === 'pink' ? 'border-pink-200 bg-pink-50' :
    'border-stone-200 bg-white';
  return (
    <div className={`rounded-xl border shadow-sm ${toneCls} p-4`}>
      <div className="text-xs font-medium text-stone-500">{label}</div>
      <div className="mt-2 flex items-baseline gap-1">
        <span className="text-2xl font-bold text-stone-900">{value}</span>
        {unit && <span className="text-sm text-stone-500">{unit}</span>}
      </div>
    </div>
  );
}
