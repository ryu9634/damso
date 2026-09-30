import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { AppNotification } from '../../../shared/types';
import { NOTIFICATION_EMOJI } from '../../../shared/types';
import { notificationApi, appErrMsg } from '../../api/app';
import { useNotifications } from './useNotifications';
import PageHeading, { PageBody } from '../../layout/PageHeading';

/**
 * 알림함 — 좋아요·만남 약속 소식이 쌓인다.
 *  - 화면을 열면 전부 읽음 처리하고 종 배지를 지운다.
 *  - 알림을 누르면 연결된 화면(받은 좋아요 등)으로 이동한다.
 */
export default function NotificationsPage() {
  const navigate = useNavigate();
  const { refresh } = useNotifications();
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    notificationApi.list()
      .then(async (list) => {
        if (!alive) return;
        setItems(list);
        if (list.some((n) => !n.read)) {
          await notificationApi.readAll().catch(() => undefined);
          await refresh();
        }
      })
      .catch((e) => { if (alive) setError(appErrMsg(e, '알림을 불러오지 못했어요.')); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // 화면을 열 때 한 번만 불러온다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <PageHeading title="알림" back={() => navigate(-1)} />
      <PageBody>
        {error && (
          <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-600">
            {error}
          </div>
        )}

        {loading ? (
          <p className="py-10 text-center text-sm text-stone-400">불러오는 중...</p>
        ) : items.length === 0 ? (
          <div className="rounded-3xl bg-white border border-stone-200 p-10 text-center text-sm text-stone-400">
            아직 도착한 알림이 없어요.
          </div>
        ) : (
          <ul className="space-y-2">
            {items.map((n) => (
              <li key={n.id}>
                <button
                  onClick={() => n.linkPath && navigate(n.linkPath)}
                  disabled={!n.linkPath}
                  className={`w-full text-left rounded-2xl border p-4 flex gap-3 transition ${
                    n.read
                      ? 'bg-white border-stone-200'
                      : 'bg-damso-50/60 border-damso-100'
                  } ${n.linkPath ? 'active:bg-stone-50' : ''}`}
                >
                  <span className="text-2xl select-none shrink-0">
                    {NOTIFICATION_EMOJI[n.type] ?? '🔔'}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-bold text-stone-900">{n.title}</span>
                    {n.body && (
                      <span className="mt-0.5 block text-sm text-stone-500 leading-relaxed">
                        {n.body}
                      </span>
                    )}
                    <span className="mt-1 block text-xs text-stone-400">
                      {formatWhen(n.createdAt)}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </PageBody>
    </>
  );
}

/** 오늘이면 시각만, 아니면 날짜까지. */
function formatWhen(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return sameDay ? time : `${d.getMonth() + 1}월 ${d.getDate()}일 ${time}`;
}
