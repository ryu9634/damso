import { useEffect, useMemo, useState } from 'react';
import { customerApi, messageApi } from '../../api/client';
import { useEvent } from '../../hooks/useEvent';
import type { Customer, Message } from '../../../shared/types';
import { PageHeader } from '../../layout/ui';

export default function MessagesPage() {
  const { event } = useEvent();
  const [messages, setMessages] = useState<Message[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [search, setSearch] = useState('');

  const refresh = async () => {
    if (!event) return;
    const [ms, cs] = await Promise.all([
      messageApi.list(event.id),
      customerApi.list(event.id),
    ]);
    setMessages(ms);
    setCustomers(cs);
  };

  useEffect(() => {
    refresh();
    if (!event) return;
    const t = setInterval(refresh, 3000);
    return () => clearInterval(t);
    /* eslint-disable-next-line */
  }, [event?.id]);

  const genderById = useMemo(() => {
    const m = new Map<number, 'MALE' | 'FEMALE'>();
    customers.forEach((c) => m.set(c.id, c.gender));
    return m;
  }, [customers]);

  const filtered = useMemo(() => {
    const s = search.trim().toLowerCase();
    if (!s) return messages;
    return messages.filter((m) =>
      m.senderNickname.toLowerCase().includes(s)
      || m.receiverNickname.toLowerCase().includes(s)
      || m.content.toLowerCase().includes(s)
      || m.senderContact.toLowerCase().includes(s)
    );
  }, [messages, search]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="최종 메시지 모니터링"
        description={<>총 {messages.length}건 · 실시간</>}
        actions={
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="보낸이/받는이/내용/연락처 검색"
            className="w-80 border border-stone-300 rounded px-3 py-1.5 text-sm"
          />
        }
      />

      <div className="space-y-3">
        {filtered.length === 0 && (
          <div className="bg-white rounded-lg border border-stone-200 p-10 text-center text-stone-400">
            {messages.length === 0 ? '아직 전달된 메시지가 없습니다.' : '검색 결과가 없습니다.'}
          </div>
        )}
        {filtered.map((m) => (
          <div key={m.id} className="bg-white rounded-lg border border-stone-200 p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2 text-sm">
              <div className="flex items-center gap-2">
                <GenderDot gender={genderById.get(m.senderId)} />
                <span className="font-semibold text-damso-700">{m.senderNickname}</span>
                <span className="text-stone-400">→</span>
                <GenderDot gender={genderById.get(m.receiverId)} />
                <span className="font-semibold text-stone-900">{m.receiverNickname}</span>
              </div>
              <div className="text-xs text-stone-500">{new Date(m.createdAt).toLocaleString('ko-KR')}</div>
            </div>
            <p className="text-stone-800 whitespace-pre-wrap text-sm leading-relaxed">{m.content}</p>
            <div className="mt-3 pt-3 border-t border-stone-100 text-xs text-stone-500">
              연락처: <span className="font-medium text-stone-700">{m.senderContact}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function GenderDot({ gender }: { gender?: 'MALE' | 'FEMALE' }) {
  const cls = gender === 'MALE'
    ? 'bg-sky-400 border-sky-500'
    : gender === 'FEMALE'
    ? 'bg-pink-300 border-pink-400'
    : 'bg-stone-300 border-stone-400';
  return <span className={`inline-block w-2.5 h-2.5 rounded-full border ${cls}`} />;
}
