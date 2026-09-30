import { useEffect, useRef, useState } from 'react';
import { useEvent } from '../hooks/useEvent';
import { eventApi } from '../api/client';
import type { Event } from '../../shared/types';
import { STATUS_LABEL } from '../../shared/types';

export default function EventSelector() {
  const { events, event, setEvent, refresh } = useEvent();
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('담소혼술바 매칭 이벤트');
  const [busy, setBusy] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
        setCreating(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleStartNew = async () => {
    const name = newName.trim() || '담소혼술바 매칭 이벤트';
    setBusy(true);
    try {
      const created = await eventApi.startNew(name);
      await refresh();
      setEvent(created);
      setCreating(false);
      setOpen(false);
      setNewName('담소혼술바 매칭 이벤트');
    } finally {
      setBusy(false);
    }
  };

  if (!event) {
    return <div className="text-xs text-stone-400">이벤트 로딩 중...</div>;
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full text-left px-3 py-2 rounded-md bg-stone-800 hover:bg-stone-700 transition"
      >
        <div className="flex items-center justify-between text-[10px] text-stone-400 mb-0.5">
          <span>선택된 이벤트</span>
          <span>▾</span>
        </div>
        <div className="text-sm font-semibold text-white truncate">{event.name}</div>
        <div className="flex items-center gap-2 mt-1">
          <StatusBadge event={event} />
          <span className="text-[10px] text-stone-400">#{event.id}</span>
        </div>
      </button>

      {open && (
        <div className="absolute bottom-full left-0 right-0 mb-2 bg-white text-stone-900 rounded-lg shadow-2xl border border-stone-200 max-h-96 overflow-y-auto">
          <div className="px-3 py-2 border-b border-stone-100 text-[10px] font-semibold text-stone-500 uppercase tracking-wide">
            이벤트 ({events.length})
          </div>
          {events.map((e) => (
            <button
              key={e.id}
              onClick={() => { setEvent(e); setOpen(false); }}
              className={`w-full text-left px-3 py-2.5 hover:bg-stone-50 transition border-b border-stone-50 last:border-b-0 ${
                event.id === e.id ? 'bg-damso-50' : ''
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="flex-1 min-w-0">
                  <div className={`text-sm font-medium truncate ${
                    e.status === 'CLOSED' ? 'text-stone-500' : 'text-stone-900'
                  }`}>
                    {e.name}
                  </div>
                  <div className="text-[10px] text-stone-500 mt-0.5">
                    #{e.id} · {new Date(e.createdAt).toLocaleString('ko-KR', {
                      month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
                    })}
                  </div>
                </div>
                <StatusBadge event={e} />
              </div>
            </button>
          ))}
          <div className="border-t border-stone-100 p-2">
            {creating ? (
              <div className="space-y-2">
                <input
                  autoFocus
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleStartNew();
                    else if (e.key === 'Escape') setCreating(false);
                  }}
                  placeholder="새 이벤트 이름"
                  className="w-full border border-stone-300 rounded px-2 py-1.5 text-sm"
                />
                <div className="flex gap-1">
                  <button
                    onClick={handleStartNew}
                    disabled={busy}
                    className="flex-1 text-sm px-3 py-1.5 bg-damso-500 text-white rounded hover:bg-damso-600 disabled:opacity-50"
                  >
                    {busy ? '생성 중...' : '시작'}
                  </button>
                  <button
                    onClick={() => setCreating(false)}
                    className="text-sm px-3 py-1.5 border border-stone-300 rounded hover:bg-stone-100"
                  >
                    취소
                  </button>
                </div>
              </div>
            ) : (
              <button
                onClick={() => setCreating(true)}
                className="w-full text-center text-sm px-3 py-2 bg-damso-500 text-white rounded hover:bg-damso-600"
              >
                + 새 이벤트 시작
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function StatusBadge({ event }: { event: Event }) {
  const closed = event.status === 'CLOSED';
  const inProgress = !closed && event.status !== 'REGISTRATION';
  const cls = closed
    ? 'bg-stone-200 text-stone-600'
    : inProgress
    ? 'bg-emerald-100 text-emerald-700'
    : 'bg-amber-100 text-amber-700';
  return (
    <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium whitespace-nowrap ${cls}`}>
      {STATUS_LABEL[event.status]}
    </span>
  );
}
