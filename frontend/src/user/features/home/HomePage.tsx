import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AppHome, AppWeek, DrinkItem } from '../../../shared/types';
import { appApi } from '../../api/app';
import { useAppAuth } from '../auth/useAppAuth';
import { useAppFeatures } from './useAppFeatures';
import { PageBody } from '../../layout/PageHeading';
import WeekCalendar from './WeekCalendar';

/** 사진도 이모지도 없을 때 쓰는 기본 잔 모양. */
const FALLBACK_EMOJI = '🍸';

/** 홈 — 인사 · 이번 주 일정 · 오늘의 한 잔 · 술 메뉴 · 바로가기 · 성사 커플. */
export default function HomePage() {
  const { user } = useAppAuth();
  const { features } = useAppFeatures();
  const [home, setHome] = useState<AppHome | null>(null);
  const [week, setWeek] = useState<AppWeek | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    const load = () =>
      appApi
        .home()
        .then((d) => { if (alive) setHome(d); })
        .catch(() => { if (alive) setFailed(true); })
        .finally(() => { if (alive) setLoading(false); });
    load();
    return () => { alive = false; };
  }, []);

  // 소개팅이 진행 중이면 단계가 바뀌는 것을 홈에서도 따라간다. (매장에서 화면을 켜 두는 경우)
  const matchingLive = home?.matching?.live ?? false;
  useEffect(() => {
    if (!matchingLive) return;
    const t = setInterval(() => {
      appApi.home().then(setHome).catch(() => { /* 다음 주기에 다시 */ });
    }, 15000);
    return () => clearInterval(t);
  }, [matchingLive]);

  // 이번 주 일정은 홈이 없어도 따로 보여줄 수 있어 요청을 나눠 둔다.
  useEffect(() => {
    let alive = true;
    appApi
      .week()
      .then((d) => { if (alive) setWeek(d); })
      .catch(() => { if (alive) setWeek(null); });
    return () => { alive = false; };
  }, []);

  // 홈 응답에도 기능 on/off 가 들어 있어 더 최신이다. 없으면 공용 값을 쓴다.
  // 소개팅 바로가기는 '기능이 켜져 있다'가 아니라 '지금 진행 중이다'일 때만 띄운다.
  const canMatching = matchingLive;
  const canSelecting = home?.features?.selecting?.enabled ?? features.selecting.enabled;
  // 홈 카드는 기능 사용과 별개로 켠다. 준비되기 전에는 카드만 감춰둔다.
  const canGame = home?.features?.gameRoomOnHome ?? features.gameRoomOnHome;

  return (
    <PageBody>
      {loading && <SkeletonCard />}
      {failed && !loading && (
        <div className="rounded-2xl bg-white border border-stone-200 p-5 text-sm text-stone-500">
          지금은 정보를 불러올 수 없어요. 잠시 후 다시 열어주세요.
        </div>
      )}

      {home && (
        <>
          {/* ─── 인사 ────────────────────────────── */}
          <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-damso-500 via-damso-400 to-amber-300 text-white p-5 shadow-sm">
            {/* 배경 장식 — 큰 잔 모양을 흐릿하게 깔아 분위기만 낸다 */}
            <span className="pointer-events-none absolute -right-3 -bottom-6 text-[7rem] opacity-20 select-none">
              🍸
            </span>

            <div className="relative">
              <span className="text-[11px] font-bold tracking-widest text-white/80">DAMSO</span>

              <h1 className="mt-3 text-2xl font-bold leading-snug">
                {user?.nickname ? `${user.nickname}님,` : '어서 오세요,'}
                <br />
                오늘도 한 잔 어때요?
              </h1>

              {home.hours && (
                <div className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-white/20 px-3 py-1 text-xs font-semibold">
                  🕗 {home.hours}
                </div>
              )}
              {home.notice && (
                <p className="mt-3 text-sm text-white/90 whitespace-pre-line leading-relaxed">
                  {home.notice}
                </p>
              )}
            </div>
          </section>

          {/* ─── 진행 중인 담소 소개팅 ───────────── */}
          {home.matching?.live && <MatchingLiveCard m={home.matching} />}

          {/* ─── 이번 주 일정 ────────────────────── */}
          {week && <WeekCalendar week={week} />}

          {/* ─── 오늘의 추천 한 잔 ───────────────── */}
          <section className="rounded-3xl bg-white border border-stone-200 shadow-sm overflow-hidden">
            {home.cocktail.imageUrl ? (
              <img
                src={home.cocktail.imageUrl}
                alt={home.cocktail.name}
                className="w-full h-44 object-cover bg-stone-100"
              />
            ) : (
              <div className="h-32 bg-gradient-to-br from-damso-50 to-amber-50 flex items-center justify-center">
                <span className="text-6xl select-none">
                  {home.cocktail.emoji || FALLBACK_EMOJI}
                </span>
              </div>
            )}
            <div className="p-5">
              <div className="text-xs font-bold tracking-wide text-damso-500">
                오늘의 추천 한 잔
              </div>
              <div className="mt-1 text-xl font-bold text-stone-900">
                {home.cocktail.name || '준비 중이에요'}
              </div>
              {home.cocktail.description && (
                <p className="mt-2 text-sm text-stone-500 whitespace-pre-line leading-relaxed">
                  {home.cocktail.description}
                </p>
              )}
            </div>
          </section>

          {/* ─── 술 메뉴 ─────────────────────────── */}
          {home.drinks.length > 0 && <DrinkStrip drinks={home.drinks} />}

          {/* ─── 바로가기 ────────────────────────── */}
          {/* 소개팅은 위의 진행 중 카드가 대신하므로 여기서는 셀렉팅만 둔다 */}
          {(canMatching || canSelecting) && (
            <section className={`grid gap-3 ${canMatching && canSelecting ? 'grid-cols-2' : 'grid-cols-1'}`}>
              {canSelecting && (
                <ShortcutCard
                  to="/selecting"
                  emoji="💌"
                  title="담소 셀렉팅"
                  subtitle="프로필 보고 선택"
                  className="from-violet-50 to-sky-50 border-violet-100"
                />
              )}
            </section>
          )}

          {/* ─── 게임방 ──────────────────────────── */}
          {canGame && (
            <Link
              to="/games"
              className="block rounded-3xl border border-amber-100 bg-gradient-to-br from-amber-50 to-rose-50 p-5 active:scale-[0.99] transition"
            >
              <div className="flex items-center gap-3">
                <span className="text-3xl select-none">🎲</span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-bold text-stone-800">게임방</span>
                  <span className="block text-xs text-stone-500">
                    같이 온 사람들과 짧은 게임 한 판 — 방 만들고 코드만 알려주세요
                  </span>
                </span>
                <span className="text-stone-300">›</span>
              </div>
            </Link>
          )}

          {/* ─── 성사 커플 ───────────────────────── */}
          <section className="rounded-3xl bg-white border border-stone-200 shadow-sm p-5">
            <div className="flex items-baseline justify-between">
              <div className="text-sm font-bold text-stone-800">💞 성사 커플</div>
              <div className="text-sm text-stone-400">
                누적 <span className="font-bold text-damso-600">{home.couples.total}</span>쌍
              </div>
            </div>

            {home.couples.visible && home.couples.recent.length > 0 ? (
              <ul className="mt-3 space-y-2">
                {home.couples.recent.map((c, i) => (
                  <li
                    key={`${c.label}-${i}`}
                    className="flex items-center justify-between rounded-2xl bg-gradient-to-r from-rose-50 to-white px-4 py-2.5"
                  >
                    <span className="text-sm font-semibold text-stone-700">{c.label}</span>
                    <span className="text-xs text-stone-400">{c.since ?? ''}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-sm text-stone-500">
                {home.couples.visible
                  ? '아직 소개된 커플이 없어요. 첫 주인공이 되어보세요!'
                  : '커플 명단은 비공개로 운영 중이에요.'}
              </p>
            )}

            {canSelecting && (
              <Link
                to="/selecting"
                className="mt-4 block w-full rounded-full bg-damso-500 text-white py-3 text-center font-semibold active:bg-damso-600"
              >
                담소 셀렉팅 신청하기
              </Link>
            )}
          </section>
        </>
      )}
    </PageBody>
  );
}

/**
 * 지금 진행 중인 담소 소개팅 — 홈 맨 위에 크게. 단계와 참여 인원을 보여주고 바로 들어가게 한다.
 * 이미 등록한 회원에게는 '이어서 참여', 아직이면 등록(등록 단계일 때) 안내로 문구를 바꾼다.
 */
function MatchingLiveCard({ m }: { m: NonNullable<AppHome['matching']> }) {
  const registering = m.status === 'REGISTRATION';
  const cta = m.registered
    ? (registering ? '대기 화면 보기' : '내 자리 · 진행 화면 보기')
    : (registering ? '지금 참여 등록하기' : '이미 등록했다면 이어서 참여');
  return (
    <Link
      to="/matching"
      className="block rounded-3xl border border-rose-200 bg-gradient-to-br from-rose-50 via-orange-50 to-amber-50 p-5 shadow-sm active:scale-[0.99] transition"
    >
      <div className="flex items-center gap-2">
        <span className="relative flex h-2.5 w-2.5">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-rose-400 opacity-75" />
          <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-rose-500" />
        </span>
        <span className="text-xs font-bold tracking-wide text-rose-600">담소 소개팅 진행 중</span>
      </div>
      <div className="mt-2 flex items-start gap-3">
        <span className="text-3xl select-none">🍻</span>
        <div className="min-w-0 flex-1">
          <div className="text-base font-bold text-stone-900 truncate">{m.name}</div>
          <div className="mt-0.5 text-sm text-stone-600">{m.statusLabel}</div>
          <div className="mt-0.5 text-xs text-stone-400">
            지금 {m.participants}명 참여
            {m.registered && <span className="ml-1 font-semibold text-rose-600">· 나도 참여 중</span>}
          </div>
        </div>
      </div>
      <div className="mt-4 rounded-full bg-rose-500 py-3 text-center text-sm font-semibold text-white">
        {cta} →
      </div>
    </Link>
  );
}

/** 관리자가 등록한 술 메뉴를 가로로 훑어볼 수 있게 늘어놓는다. */
function DrinkStrip({ drinks }: { drinks: DrinkItem[] }) {
  return (
    <section>
      <div className="flex items-baseline justify-between px-1">
        <div className="text-sm font-bold text-stone-800">🍹 오늘의 술 메뉴</div>
        <div className="text-xs text-stone-400">옆으로 넘겨보세요</div>
      </div>

      {/* 좌우 여백을 음수 마진으로 넓혀 화면 끝까지 스크롤되게 한다 */}
      <div className="mt-3 -mx-4 px-4 overflow-x-auto">
        <div className="flex gap-3 pb-1">
          {drinks.map((d) => (
            <article
              key={d.id}
              className="w-28 shrink-0 rounded-2xl bg-white border border-stone-200 shadow-sm overflow-hidden"
            >
              {d.imageUrl ? (
                <img
                  src={d.imageUrl}
                  alt={d.name}
                  className="w-full h-20 object-cover bg-stone-100"
                />
              ) : (
                <div className="h-20 bg-gradient-to-br from-damso-50 to-amber-50 flex items-center justify-center">
                  <span className="text-3xl select-none">{d.emoji || FALLBACK_EMOJI}</span>
                </div>
              )}
              <div className="px-3 py-2">
                <div className="text-xs font-bold text-stone-800 truncate">{d.name}</div>
                {d.description && (
                  <p className="mt-0.5 text-[11px] text-stone-400 line-clamp-2 leading-snug">
                    {d.description}
                  </p>
                )}
                {d.recommended && (
                  <span className="mt-1 inline-block rounded-full bg-damso-50 px-2 py-0.5 text-[10px] font-bold text-damso-600">
                    추천
                  </span>
                )}
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/** 홈에서 바로 들어가는 기능 카드. */
function ShortcutCard({
  to, emoji, title, subtitle, className,
}: {
  to: string;
  emoji: string;
  title: string;
  subtitle: string;
  className: string;
}) {
  return (
    <Link
      to={to}
      className={`rounded-3xl border bg-gradient-to-br p-4 active:scale-[0.98] transition ${className}`}
    >
      <span className="text-3xl select-none">{emoji}</span>
      <div className="mt-2 text-sm font-bold text-stone-800">{title}</div>
      <div className="text-xs text-stone-500">{subtitle}</div>
    </Link>
  );
}

function SkeletonCard() {
  return (
    <div className="rounded-3xl bg-white border border-stone-200 p-5 animate-pulse space-y-3">
      <div className="h-4 w-24 bg-stone-100 rounded" />
      <div className="h-6 w-40 bg-stone-100 rounded" />
      <div className="h-4 w-full bg-stone-100 rounded" />
    </div>
  );
}
