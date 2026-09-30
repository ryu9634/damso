import { useEffect, useState } from 'react';
import { adminAppApi } from '../../api/client';
import type { AdminGuestbookEntry } from '../../../shared/types';
import { PageHeader, Card, Button, Empty, Badge } from '../../layout/ui';
import { formatDateTime } from '../../../shared/utils/privacy';

/** 방명록 관리 — 부적절한 글은 숨기거나 삭제한다. */
export default function AppGuestbookPage() {
  const [entries, setEntries] = useState<AdminGuestbookEntry[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setEntries(await adminAppApi.guestbook());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const toggleHidden = async (e: AdminGuestbookEntry) => {
    const updated = await adminAppApi.setGuestbookHidden(e.id, !e.hidden);
    setEntries((list) => list.map((it) => (it.id === e.id ? updated : it)));
  };

  const remove = async (e: AdminGuestbookEntry) => {
    if (!confirm('이 글을 삭제할까요? 손님 화면에서 완전히 사라집니다.')) return;
    await adminAppApi.deleteGuestbook(e.id);
    setEntries((list) => list.filter((it) => it.id !== e.id));
  };

  const hiddenCount = entries.filter((e) => e.hidden).length;

  return (
    <div className="space-y-6">
      <PageHeader
        title="방명록"
        description="손님이 앱에 남긴 글이에요. 숨김 처리하면 손님 화면에서 보이지 않습니다."
        actions={<Button onClick={load}>새로고침</Button>}
      />

      <Card title={`전체 ${entries.length}개 · 숨김 ${hiddenCount}개`}>
        {loading ? (
          <Empty>불러오는 중...</Empty>
        ) : entries.length === 0 ? (
          <Empty>남겨진 글이 없습니다.</Empty>
        ) : (
          <ul className="space-y-3">
            {entries.map((e) => (
              <li
                key={e.id}
                className={`rounded-lg border p-4 ${
                  e.hidden ? 'border-stone-200 bg-stone-50' : 'border-stone-200 bg-white'
                }`}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="text-sm font-semibold text-stone-800">{e.authorName}</span>
                    {e.realName && (
                      <span className="text-xs text-stone-400">({e.realName})</span>
                    )}
                    <span className="text-xs text-stone-400">{formatDateTime(e.createdAt)}</span>
                    {e.hidden && <Badge className="bg-stone-200 text-stone-600">숨김</Badge>}
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <Button size="sm" onClick={() => toggleHidden(e)}>
                      {e.hidden ? '다시 보이기' : '숨기기'}
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => remove(e)}>
                      삭제
                    </Button>
                  </div>
                </div>
                <p className="mt-2 text-sm text-stone-600 whitespace-pre-line leading-relaxed">
                  {e.content}
                </p>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
