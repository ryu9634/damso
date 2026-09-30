import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { GuestbookEntry } from '../../../shared/types';
import { appApi, appErrMsg } from '../../api/app';
import { useAppAuth } from '../auth/useAppAuth';
import { formatDateTime } from '../../../shared/utils/privacy';
import { PageBody } from '../../layout/PageHeading';

const MAX_LEN = 500;

/** 방명록 — 열람은 누구나, 작성은 로그인한 회원만. */
export default function GuestbookPage() {
  const { user } = useAppAuth();
  const [entries, setEntries] = useState<GuestbookEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [content, setContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      setEntries(await appApi.guestbook());
    } catch {
      setError('방명록을 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [user?.id]);

  const submit = async () => {
    const text = content.trim();
    if (!text) return;
    setSaving(true);
    setError('');
    try {
      const saved = await appApi.writeGuestbook(text);
      setEntries((list) => [saved, ...list]);
      setContent('');
    } catch (e) {
      setError(appErrMsg(e, '남기지 못했어요. 잠시 후 다시 시도해주세요.'));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    if (!confirm('이 글을 지울까요?')) return;
    try {
      await appApi.deleteGuestbook(id);
      setEntries((list) => list.filter((e) => e.id !== id));
    } catch (e) {
      setError(appErrMsg(e, '삭제하지 못했어요.'));
    }
  };

  return (
    <>
      <PageBody>
        {/* ─── 작성칸 ─────────────────────────────── */}
        {user ? (
          <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-4">
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value.slice(0, MAX_LEN))}
              placeholder="오늘 담소는 어떠셨나요?"
              className="w-full min-h-[88px] rounded-xl border border-stone-200 bg-stone-50 px-3 py-2.5 text-sm text-stone-800 focus:outline-none focus:border-damso-400"
            />
            <div className="mt-2 flex items-center justify-between">
              <span className="text-xs text-stone-400">{content.length} / {MAX_LEN}</span>
              <button
                onClick={submit}
                disabled={saving || !content.trim()}
                className="rounded-full bg-damso-500 text-white px-5 py-2 text-sm font-semibold active:bg-damso-600 disabled:opacity-40"
              >
                {saving ? '남기는 중...' : '남기기'}
              </button>
            </div>
          </section>
        ) : (
          <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-5 text-center">
            <p className="text-sm text-stone-600">로그인하면 방명록을 남길 수 있어요.</p>
            <Link
              to="/my"
              className="mt-3 inline-block rounded-full bg-damso-500 text-white px-6 py-2.5 text-sm font-semibold active:bg-damso-600"
            >
              로그인 · 회원가입
            </Link>
          </section>
        )}

        {error && (
          <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-600">
            {error}
          </div>
        )}

        {/* ─── 목록 ───────────────────────────────── */}
        {loading ? (
          <div className="py-10 text-center text-sm text-stone-400">불러오는 중...</div>
        ) : entries.length === 0 ? (
          <div className="py-10 text-center text-sm text-stone-400">
            아직 남겨진 글이 없어요. 첫 글의 주인공이 되어보세요!
          </div>
        ) : (
          <ul className="space-y-3 pb-6">
            {entries.map((e) => (
              <li key={e.id} className="rounded-2xl bg-white border border-stone-200 shadow-sm p-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-stone-800">{e.authorName}</span>
                  <span className="text-xs text-stone-400">{formatDateTime(e.createdAt)}</span>
                </div>
                <p className="mt-2 text-sm text-stone-600 whitespace-pre-line leading-relaxed">
                  {e.content}
                </p>
                {e.mine && (
                  <button
                    onClick={() => remove(e.id)}
                    className="mt-2 text-xs text-stone-400 active:text-red-500"
                  >
                    삭제
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </PageBody>
    </>
  );
}
