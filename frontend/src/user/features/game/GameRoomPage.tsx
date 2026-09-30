import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { GamePlayer, GameRound, GameState } from '../../../shared/types';
import { GAME_TYPE_EMOJI } from '../../../shared/types';
import { gameApi } from './game';
import { appErrMsg } from '../../api/app';
import PageHeading, { PageBody } from '../../layout/PageHeading';
import { PRIMARY_BTN, SUB_BTN } from '../../layout/authUi';

/**
 * 게임방 화면.
 *  - 서버 상태 하나를 2초마다 다시 불러 화면을 다시 그린다. 방에 사람이 드나들고
 *    다른 사람이 답을 내는 걸 보여주려면 이만큼은 필요하다.
 *  - 방장에게만 시작·공개·종료 버튼이 보인다. (서버에서도 같은 규칙으로 막는다)
 */
const POLL_MS = 2000;

export default function GameRoomPage() {
  const { code = '' } = useParams();
  const navigate = useNavigate();

  const [state, setState] = useState<GameState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  // 게임 목록은 접어 둔다. 방에 들어와 아직 아무것도 안 했을 때만 펼쳐 보여준다.
  const [pickingGame, setPickingGame] = useState(false);
  // 내가 방금 누른 동작의 결과를 폴링이 덮어쓰지 않도록 잠깐 멈춘다.
  const pausedRef = useRef(false);

  const refresh = useCallback(async () => {
    if (pausedRef.current) return;
    try {
      setState(await gameApi.state(code));
      setError('');
    } catch (e) {
      setError(appErrMsg(e, '방 상태를 불러오지 못했어요.'));
    } finally {
      setLoading(false);
    }
  }, [code]);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  /** 버튼 동작 공통 — 응답으로 화면을 바로 갱신하고, 그동안 폴링을 멈춘다. */
  const run = async (fn: () => Promise<GameState | void>, fallback: string) => {
    setBusy(true);
    pausedRef.current = true;
    setError('');
    try {
      const next = await fn();
      if (next) setState(next);
    } catch (e) {
      setError(appErrMsg(e, fallback));
    } finally {
      pausedRef.current = false;
      setBusy(false);
    }
  };

  const leave = async () => {
    if (!confirm('방에서 나갈까요?')) return;
    await run(() => gameApi.leave(code), '나가지 못했어요.');
    navigate('/games');
  };

  const closeRoom = async () => {
    if (!confirm('방을 종료할까요? 모두 나가게 됩니다.')) return;
    await run(() => gameApi.close(code), '종료하지 못했어요.');
    navigate('/games');
  };

  const startGame = async (type: GameState['games'][number]['type']) => {
    await run(() => gameApi.start(code, type), '게임을 시작하지 못했어요.');
    setPickingGame(false);
  };

  if (loading) {
    return (
      <PageBody>
        <p className="py-10 text-center text-sm text-stone-400">불러오는 중...</p>
      </PageBody>
    );
  }

  if (!state) {
    return (
      <>
        <PageHeading title="게임방" back={() => navigate('/games')} />
        <PageBody>
          <div className="rounded-3xl bg-white border border-stone-200 p-8 text-center">
            <div className="text-3xl">🚪</div>
            <p className="mt-3 text-sm text-stone-500">{error || '방을 찾을 수 없어요.'}</p>
            <button className={PRIMARY_BTN + ' mt-5'} onClick={() => navigate('/games')}>
              방 목록으로
            </button>
          </div>
        </PageBody>
      </>
    );
  }

  const round = state.round;

  return (
    <>
      <PageHeading
        title={state.title || '게임방'}
        description={`방 코드 ${state.code} · ${state.activeCount}명 참여 중`}
        back={() => navigate('/games')}
      />
      <PageBody>
        {error && (
          <div className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-600">
            {error}
          </div>
        )}

        {/* ─── 참가자 ─────────────────────────────── */}
        <section className="rounded-3xl bg-white border border-stone-200 shadow-sm p-5">
          <div className="flex items-baseline justify-between">
            <div className="text-sm font-bold text-stone-800">참가자 {state.players.length}명</div>
            <div className="text-xs text-stone-400">방장 {state.hostName}</div>
          </div>
          <ul className="mt-3 flex flex-wrap gap-2">
            {state.players.map((p) => (
              <li
                key={p.userId}
                className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
                  p.me
                    ? 'bg-damso-500 text-white'
                    : p.active
                      ? 'bg-stone-100 text-stone-700'
                      : 'bg-stone-50 text-stone-300'
                }`}
              >
                {p.host && '👑 '}
                {p.moderator && '🎙 '}
                {p.name}
              </li>
            ))}
          </ul>
        </section>

        {/* ─── 사회자 ─────────────────────────────── */}
        {/* 마피아를 할 때만 쓰는 자리라 다른 게임이 도는 중에는 접어 두되,
            게임을 고르는 중에는 보여준다. 마피아를 시작하기 전에 미리 맡아야 하기 때문이다. */}
        {(!round || round.type === 'MAFIA' || pickingGame) && (
        <section className="rounded-2xl bg-white border border-stone-200 px-4 py-3 flex items-center gap-3">
          <span className="text-xl select-none">🎙</span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-bold text-stone-800">
              마피아 사회자
              {state.moderatorName && <span className="ml-1 text-damso-600">{state.moderatorName}</span>}
            </span>
            <span className="block text-xs text-stone-400">
              {state.moderatorName
                ? '마피아에서만 쓰는 자리예요. 역할을 받지 않고 진행만 해요.'
                : '마피아를 할 때 전체 역할표를 보며 밤을 진행할 수 있어요.'}
            </span>
          </span>
          {state.moderator ? (
            <button
              className="shrink-0 rounded-full border border-stone-300 px-3 py-1.5 text-xs font-semibold text-stone-600 active:bg-stone-100"
              disabled={busy}
              onClick={() => run(() => gameApi.setModerator(state.code, false), '바꾸지 못했어요.')}
            >
              내려놓기
            </button>
          ) : (
            !state.moderatorName && (
              <button
                className="shrink-0 rounded-full bg-stone-900 text-white px-3 py-1.5 text-xs font-semibold active:bg-stone-700"
                disabled={busy}
                onClick={() => run(() => gameApi.setModerator(state.code, true), '맡지 못했어요.')}
              >
                사회자 맡기
              </button>
            )
          )}
        </section>
        )}

        {/* ─── 진행 중인 게임 ─────────────────────── */}
        {/* 다른 게임을 고르는 중에는 하던 게임을 감춘다. 둘이 같이 보이면 헷갈린다. */}
        {round && !pickingGame && (
          <section className="rounded-3xl bg-white border border-stone-200 shadow-sm p-5">
            <div className="text-xs font-bold tracking-wide text-damso-500">
              {GAME_TYPE_EMOJI[round.type]} {round.typeLabel}
            </div>

            {round.type === 'LIAR' || round.type === 'MAFIA' ? (
              <RoleGame
                round={round}
                players={state.players}
                busy={busy}
                onVote={(userId) =>
                  run(() => gameApi.answer(state.code, round.id, String(userId)), '지목하지 못했어요.')
                }
              />
            ) : round.type === 'BALANCE' ? (
              <>
                <div className="mt-3 grid gap-2">
                  {(['A', 'B'] as const).map((side) => {
                    const text = side === 'A' ? round.optionA : round.optionB;
                    const count = side === 'A' ? round.countA : round.countB;
                    const names = side === 'A' ? round.namesA : round.namesB;
                    const chosen = round.myAnswer === side;
                    return (
                      <button
                        key={side}
                        disabled={busy || round.revealed}
                        onClick={() =>
                          run(() => gameApi.answer(state.code, round.id, side), '고르지 못했어요.')
                        }
                        className={`rounded-2xl border-2 px-4 py-4 text-left transition ${
                          chosen
                            ? 'border-damso-500 bg-damso-50'
                            : 'border-stone-200 bg-white active:bg-stone-50'
                        } ${round.revealed ? 'cursor-default' : ''}`}
                      >
                        <span className="block text-base font-bold text-stone-900">{text}</span>
                        {round.revealed && (
                          <span className="mt-1 block text-xs text-stone-500">
                            {count}명 {names.length > 0 && `· ${names.join(', ')}`}
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>
                <p className="mt-3 text-xs text-stone-400">
                  {round.revealed
                    ? '결과가 공개됐어요.'
                    : `${round.answered}명이 골랐어요${round.myAnswer ? ' · 나도 골랐어요' : ''}`}
                </p>
              </>
            ) : (
              <div className="mt-3 rounded-2xl bg-gradient-to-br from-damso-50 to-amber-50 px-4 py-6 text-center">
                <div className="text-3xl">🎯</div>
                <div className="mt-2 text-xl font-bold text-stone-900">
                  {round.winnerName}님 당첨!
                </div>
                {round.optionA && (
                  <p className="mt-2 text-sm text-stone-600">{round.optionA}</p>
                )}
              </div>
            )}

            {/* 방장만 다음 단계로 넘긴다 */}
            {state.host && round.phase === 'ROLE' && (
              <button
                className={PRIMARY_BTN + ' mt-4'}
                disabled={busy}
                onClick={() => run(() => gameApi.openVote(state.code, round.id), '투표를 열지 못했어요.')}
              >
                이야기 끝 · 투표 시작
              </button>
            )}
            {state.host && (round.phase === 'OPEN' || round.phase === 'VOTE') && (
              <button
                className={PRIMARY_BTN + ' mt-4'}
                disabled={busy}
                onClick={() => run(() => gameApi.reveal(state.code, round.id), '공개하지 못했어요.')}
              >
                결과 공개하기
              </button>
            )}

            {/* 결과를 본 뒤 같은 게임으로 한 판 더 */}
            {state.host && round.revealed && (
              <button
                className={PRIMARY_BTN + ' mt-4'}
                disabled={busy}
                onClick={() => startGame(round.type)}
              >
                {round.type === 'BALANCE' ? '다음 문제 →' : '한 번 더 →'}
              </button>
            )}
          </section>
        )}

        {/* ─── 게임 고르기 ────────────────────────── */}
        {/* 목록을 늘 펼쳐두면 화면이 길어진다. 게임이 없을 때와 직접 누를 때만 편다. */}
        {state.host ? (
          !round || pickingGame ? (
            <section className="rounded-3xl bg-white border border-stone-200 shadow-sm p-5">
              <div className="flex items-center justify-between">
                <div className="text-sm font-bold text-stone-800">게임 고르기</div>
                {round && (
                  <button
                    className="text-xs font-semibold text-stone-400"
                    onClick={() => setPickingGame(false)}
                  >
                    닫기
                  </button>
                )}
              </div>
              <div className="mt-3 space-y-2">
                {state.games.length === 0 && (
                  <p className="text-sm text-stone-400">지금 할 수 있는 게임이 없어요.</p>
                )}
                {state.games.map((g) => (
                  <button
                    key={g.type}
                    disabled={busy}
                    onClick={() => startGame(g.type)}
                    className="w-full text-left rounded-2xl border border-stone-200 px-4 py-3 flex items-center gap-3 active:bg-stone-50 disabled:opacity-40"
                  >
                    <span className="text-2xl">{GAME_TYPE_EMOJI[g.type]}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-bold text-stone-900">{g.label}</span>
                      <span className="block text-xs text-stone-400">{g.description}</span>
                    </span>
                    <span className="text-xs font-bold text-damso-600">시작</span>
                  </button>
                ))}
              </div>
            </section>
          ) : (
            <button className={SUB_BTN} onClick={() => setPickingGame(true)} disabled={busy}>
              다른 게임 선택
            </button>
          )
        ) : (
          !round && (
            <div className="rounded-3xl bg-white border border-stone-200 p-8 text-center text-sm text-stone-400">
              방장이 게임을 고르는 중이에요. 잠시만요!
            </div>
          )
        )}

        {/* ─── 방 나가기 ──────────────────────────── */}
        <div className="space-y-2">
          <button className={SUB_BTN} onClick={leave} disabled={busy}>
            방 나가기
          </button>
          {state.host && (
            <button
              className="w-full py-2 text-xs text-stone-400 active:text-red-500"
              onClick={closeRoom}
              disabled={busy}
            >
              방 종료하기
            </button>
          )}
        </div>
      </PageBody>
    </>
  );
}

/**
 * 라이어 게임 · 마피아 — 두 게임이 같은 흐름을 쓴다.
 *  역할 확인(각자 다른 화면) → 이야기(자리에서) → 지목 → 결과.
 *  내 역할과 제시어는 서버가 나에게만 내려준다. 라이어에게는 제시어가 오지 않는다.
 */
function RoleGame({
  round, players, busy, onVote,
}: {
  round: GameRound;
  players: GamePlayer[];
  busy: boolean;
  onVote: (userId: number) => void;
}) {
  const hiddenLabel = round.type === 'LIAR' ? '라이어' : '마피아';
  // 이번 판을 함께 하는 사람만 서로를 지목한다.
  // (마피아 사회자, 판이 시작된 뒤 들어온 사람은 여기서 빠진다)
  const inRound = (userId: number) => round.playerIds.includes(userId);
  const me = players.find((p) => p.me);
  const iPlay = !!me && inRound(me.userId);

  return (
    <div className="mt-3 space-y-3">
      {/* ─── 사회자 화면: 전체 역할표 ─────────── */}
      {round.allRoles.length > 0 && (
        <div className="rounded-2xl bg-stone-900 text-white px-4 py-4">
          <div className="text-xs font-bold text-stone-400">🎙 사회자 전용 · 전체 역할</div>
          <ul className="mt-2 space-y-1">
            {round.allRoles.map((r) => (
              <li key={r.name} className="flex items-center justify-between text-sm">
                <span>{r.name}</span>
                <span
                  className={`font-bold ${
                    r.role === 'MAFIA'
                      ? 'text-rose-400'
                      : r.role === 'POLICE'
                        ? 'text-sky-300'
                        : r.role === 'DOCTOR'
                          ? 'text-emerald-300'
                          : 'text-stone-400'
                  }`}
                >
                  {r.roleLabel}
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-stone-400 leading-relaxed">
            밤 진행은 말로 해주세요. 지목·조사·치료 결과를 보고 낮에 투표를 열면 됩니다.
          </p>
        </div>
      )}

      {/* ─── 내 역할 ─────────────────────────── */}
      {round.myRole && !round.revealed && (
        <div
          className={`rounded-2xl px-4 py-5 text-center ${
            round.myRole === 'CITIZEN'
              ? 'bg-stone-50 border border-stone-200'
              : round.myRole === 'POLICE'
                ? 'bg-sky-50 border border-sky-200'
                : round.myRole === 'DOCTOR'
                  ? 'bg-emerald-50 border border-emerald-200'
                  : 'bg-gradient-to-br from-rose-50 to-amber-50 border border-rose-200'
          }`}
        >
          <div className="text-xs font-bold text-stone-400">나의 역할</div>
          <div className="mt-1 text-xl font-bold text-stone-900">
            {round.myRoleLabel}
          </div>
          {round.myRoleHint && (
            <p className="mt-1 text-xs text-stone-500">{round.myRoleHint}</p>
          )}

          {round.type === 'LIAR' && round.myRole === 'CITIZEN' && round.keyword && (
            <div className="mt-3 rounded-xl bg-white px-4 py-3">
              <div className="text-xs text-stone-400">{round.category}</div>
              <div className="text-lg font-bold text-stone-900">{round.keyword}</div>
            </div>
          )}
          {round.type === 'MAFIA' && round.mates.length > 0 && (
            <p className="mt-2 text-sm text-rose-600">동료: {round.mates.join(', ')}</p>
          )}
          {round.type === 'MAFIA' && round.myRole === 'MAFIA' && round.mates.length === 0 && (
            <p className="mt-2 text-sm text-rose-600">혼자예요. 들키지 마세요!</p>
          )}
        </div>
      )}

      {round.phase === 'ROLE' && (
        <p className="text-xs text-stone-400 text-center">
          돌아가며 한마디씩 해보세요. 준비되면 방장이 투표를 엽니다.
        </p>
      )}

      {/* ─── 지목 ────────────────────────────── */}
      {round.phase === 'VOTE' && !iPlay && (
        <p className="rounded-xl bg-stone-50 px-4 py-3 text-sm text-stone-500">
          {round.allRoles.length > 0
            ? '사회자는 투표하지 않아요. 다 지목하면 결과를 공개해주세요.'
            : '이번 판은 참여하지 않았어요. 다음 판부터 함께해요!'}
        </p>
      )}

      {round.phase === 'VOTE' && iPlay && (
        <div>
          <div className="text-sm font-bold text-stone-800">
            {hiddenLabel}로 의심되는 사람은?
          </div>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {players.filter((p) => inRound(p.userId)).map((p) => {
              const picked = round.myAnswer === String(p.userId);
              return (
                <button
                  key={p.userId}
                  disabled={busy || p.me}
                  onClick={() => onVote(p.userId)}
                  className={`rounded-xl border px-3 py-3 text-sm font-semibold transition ${
                    picked
                      ? 'border-rose-500 bg-rose-50 text-rose-700'
                      : 'border-stone-200 bg-white text-stone-700 active:bg-stone-50'
                  } ${p.me ? 'opacity-40' : ''}`}
                >
                  {p.name}
                  {p.me && ' (나)'}
                </button>
              );
            })}
          </div>
          <p className="mt-2 text-xs text-stone-400">
            {round.answered}명이 지목했어요{round.myAnswer ? ' · 나도 골랐어요' : ''}
          </p>
        </div>
      )}

      {/* ─── 결과 ────────────────────────────── */}
      {round.revealed && (
        <div className="space-y-3">
          <div className="rounded-2xl bg-gradient-to-br from-damso-50 to-amber-50 px-4 py-5 text-center">
            <div className="text-2xl">{round.type === 'LIAR' ? '🤥' : '🕵️'}</div>
            <div className="mt-2 text-base font-bold text-stone-900">{round.outcome}</div>
            {round.revealedRoles.length > 0 && (
              <div className="mt-2 text-sm text-stone-600">
                {hiddenLabel}: {round.revealedRoles.join(', ')}
              </div>
            )}
            {round.type === 'LIAR' && round.keyword && (
              <div className="mt-1 text-sm text-stone-500">
                제시어는 “{round.keyword}”였어요
              </div>
            )}
          </div>

          {round.votes.length > 0 && (
            <ul className="space-y-1.5">
              {round.votes.map((v) => (
                <li
                  key={v.name}
                  className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5"
                >
                  <span className="text-sm font-semibold text-stone-700">{v.name}</span>
                  <span className="text-xs text-stone-500">
                    {v.count}표 · {v.voters.join(', ')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
