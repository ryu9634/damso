import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { GameRoom } from '../../../shared/types';
import { gameApi } from './game';
import { appErrMsg } from '../../api/app';
import { useAppFeatures } from '../home/useAppFeatures';
import PageHeading, { PageBody } from '../../layout/PageHeading';
import { INPUT, LABEL, PRIMARY_BTN, SUB_BTN } from '../../layout/authUi';

/**
 * 게임방 로비 — 열린 방 목록 · 방 만들기 · 코드로 들어가기.
 * 방 목록은 사람이 드나들면 바뀌므로 화면에 머무는 동안 주기적으로 다시 불러온다.
 */
const REFRESH_MS = 5000;

export default function GamesPage() {
  const navigate = useNavigate();
  const { features } = useAppFeatures();

  const [rooms, setRooms] = useState<GameRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [title, setTitle] = useState('');
  const [creating, setCreating] = useState(false);

  const enabled = features.gameRoom.enabled;

  const load = useCallback(async () => {
    try {
      setRooms(await gameApi.rooms());
    } catch (e) {
      setError(appErrMsg(e, '방 목록을 불러오지 못했어요.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => clearInterval(timer);
  }, [enabled, load]);

  const create = async () => {
    setBusy(true);
    setError('');
    try {
      const state = await gameApi.create(title.trim() || null);
      navigate(`/games/${state.code}`);
    } catch (e) {
      setError(appErrMsg(e, '방을 만들지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  /** 잘못 눌러 엉뚱한 방에 들어가지 않도록 한 번 묻는다. */
  const enter = async (roomCode: string, ask: string) => {
    if (!confirm(ask)) return;
    setBusy(true);
    setError('');
    try {
      await gameApi.join(roomCode);
      navigate(`/games/${roomCode}`);
    } catch (e) {
      setError(appErrMsg(e, '방에 들어가지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  if (!enabled) {
    return (
      <>
        <PageHeading title="게임방" />
        <PageBody>
          <div className="rounded-3xl bg-white border border-stone-200 p-8 text-center">
            <div className="text-3xl">🎲</div>
            <p className="mt-3 text-sm text-stone-500 whitespace-pre-line leading-relaxed">
              {features.gameRoom.notice || '게임방은 지금 이용할 수 없어요.'}
            </p>
          </div>
        </PageBody>
      </>
    );
  }

  return (
    <>
      <PageHeading
        title="게임방"
        description="같이 온 사람들과 폰 하나씩 들고 즐기는 짧은 게임이에요."
        back={() => navigate('/')}
      />
      <PageBody>
        {error && (
          <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-600">
            {error}
          </div>
        )}

        {/* ─── 방 만들기 ─────────────────────────── */}
        <section className="rounded-3xl bg-white border border-stone-200 shadow-sm p-5">
          {creating ? (
            <div className="space-y-3">
              <div>
                <label className={LABEL}>방 이름 (선택)</label>
                <input
                  className={INPUT}
                  value={title}
                  maxLength={40}
                  placeholder="예: 3번 테이블"
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>
              <div className="flex gap-2">
                <button
                  className="flex-1 rounded-full border border-stone-300 bg-white text-stone-700 py-3 font-semibold active:bg-stone-100"
                  onClick={() => setCreating(false)}
                  disabled={busy}
                >
                  취소
                </button>
                <button className={PRIMARY_BTN + ' flex-1'} onClick={create} disabled={busy}>
                  {busy ? '만드는 중...' : '방 만들기'}
                </button>
              </div>
            </div>
          ) : (
            <button className={PRIMARY_BTN} onClick={() => setCreating(true)}>
              🎲 새 게임방 만들기
            </button>
          )}
        </section>

        {/* ─── 열린 방 ───────────────────────────── */}
        <section>
          <div className="px-1 text-sm font-bold text-stone-800">지금 열린 방</div>
          {loading ? (
            <p className="py-8 text-center text-sm text-stone-400">불러오는 중...</p>
          ) : rooms.length === 0 ? (
            <div className="mt-3 rounded-3xl bg-white border border-stone-200 p-8 text-center text-sm text-stone-400">
              아직 열린 방이 없어요. 먼저 만들어보세요!
            </div>
          ) : (
            <ul className="mt-3 space-y-2">
              {rooms.map((room) => (
                <li key={room.code}>
                  <button
                    onClick={() =>
                      enter(
                        room.code,
                        `${room.title || `${room.hostName}님의 방`} (${room.playerCount}명)\n`
                          + '이 방에 들어갈까요?',
                      )
                    }
                    disabled={busy}
                    className="w-full text-left rounded-2xl bg-white border border-stone-200 shadow-sm px-4 py-3 flex items-center gap-3 active:bg-stone-50"
                  >
                    <span className="rounded-xl bg-stone-900 text-white px-3 py-2 text-sm font-bold tracking-widest">
                      {room.code}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-bold text-stone-900 truncate">
                        {room.title || `${room.hostName}님의 방`}
                      </span>
                      <span className="block text-xs text-stone-400">
                        {room.playerCount}명 · {room.statusLabel}
                        {room.joined ? ' · 참여 중' : ''}
                      </span>
                    </span>
                    <span className="text-stone-300">›</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <p className="px-1 text-xs text-stone-400 leading-relaxed">
          방을 만든 사람이 게임을 시작하고 결과를 공개해요. 인원 제한은 없어요.
        </p>
        <button className={SUB_BTN} onClick={() => navigate('/')}>홈으로</button>
      </PageBody>
    </>
  );
}
