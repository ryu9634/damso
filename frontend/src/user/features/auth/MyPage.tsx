import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AppUser, AuthProvider, AuthProviderInfo, DatingSelf } from '../../../shared/types';
import { authApi, appErrMsg, pushApi } from '../../api/app';
import { datingApi } from '../dating/dating';
import { useAppAuth } from './useAppAuth';
import { useAppFeatures } from '../home/useAppFeatures';
import { formatPhone } from '../../../shared/utils/phone';
import {
  INPUT, LABEL, MIN_AGE, MIN_PASSWORD, LOGIN_ID_RE, NICKNAME_RE, PHONE_RE,
  PRIMARY_BTN, SUB_BTN, Row, SocialButtons, ageOf, phoneDigits,
} from '../../layout/authUi';
import { PageBody } from '../../layout/PageHeading';
import { disablePush, enablePush, isSubscribed, needsHomeScreen, pushSupported } from '../notification/push';

/**
 * 마이페이지 — 로그인한 회원 전용.
 * (비로그인 상태는 AppShell 이 로그인 화면으로 보내므로 여기선 로그인 폼을 두지 않는다)
 */
export default function MyPage() {
  const { user, setUser, signOut, refresh } = useAppAuth();
  const { features } = useAppFeatures();
  // 셀렉팅을 꺼두면 프로필도 받은 좋아요도 마이페이지에서 감춘다.
  // 서버가 어차피 막고 있어, 남겨두면 눌러도 안 되는 버튼만 보인다.
  const canSelecting = features.selecting.enabled;
  const [editing, setEditing] = useState(false);
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [name, setName] = useState(user?.name ?? '');
  const [birthDate, setBirthDate] = useState(user?.birthDate ?? '');
  const [phone, setPhone] = useState(formatPhone(user?.phone ?? ''));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [pushNote, setPushNote] = useState('');
  const [providers, setProviders] = useState<AuthProviderInfo[]>([]);
  const [showPassword, setShowPassword] = useState(false);
  // 담소 셀렉팅 프로필 — undefined: 확인 중, null: 아직 등록 전
  const [datingProfile, setDatingProfile] = useState<DatingSelf | null | undefined>(undefined);
  const [visibilityBusy, setVisibilityBusy] = useState(false);
  // 이 기기가 지금 푸시를 받도록 등록돼 있는지 (계정 설정과 별개로 기기마다 다르다)
  const [pushOnDevice, setPushOnDevice] = useState(false);
  const [pushBusy, setPushBusy] = useState(false);

  // 소셜 가입 등으로 필수정보가 비어 있으면 바로 수정 화면을 연다.
  const needsProfile = !!user && !user.profileComplete;
  useEffect(() => { if (needsProfile) setEditing(true); }, [needsProfile]);

  useEffect(() => {
    authApi.providers()
      .then((list) => setProviders(list.filter((p) => p.configured && p.authorizeUrl)))
      .catch(() => setProviders([]));
  }, []);

  // 신청 현황은 마이페이지에서 한눈에 보여주고, 수정도 여기서 들어간다.
  useEffect(() => {
    if (!canSelecting) return;
    datingApi.me()
      .then(setDatingProfile)
      .catch(() => setDatingProfile(null));
  }, [canSelecting]);

  // 이 기기의 구독 상태를 확인해 스위치에 반영한다.
  useEffect(() => {
    let alive = true;
    isSubscribed().then((v) => { if (alive) setPushOnDevice(v); }).catch(() => {});
    return () => { alive = false; };
  }, []);

  const age = useMemo(() => (birthDate ? ageOf(birthDate) : null), [birthDate]);
  const underAge = age !== null && age < MIN_AGE;
  const phoneValid = PHONE_RE.test(phoneDigits(phone));

  if (!user) return null;

  const unlinked = providers.filter((p) => !user.linkedProviders.includes(p.provider));

  const save = async () => {
    setBusy(true);
    setError('');
    try {
      const updated = await authApi.updateMe({
        nickname: nickname.trim(),
        name: name.trim(),
        birthDate,
        phone: phoneDigits(phone),
      });
      setUser(updated);
      setEditing(false);
    } catch (e) {
      setError(appErrMsg(e, '저장하지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  const unlink = async (provider: AuthProvider) => {
    if (!confirm('이 간편로그인 연결을 해제할까요?')) return;
    try {
      setUser(await authApi.unlinkSocial(provider.toLowerCase()));
    } catch (e) {
      setError(appErrMsg(e, '연결을 해제하지 못했어요.'));
    }
  };

  /**
   * 푸시 알람 허용 토글.
   * 브라우저 알림 권한을 먼저 물어보고, 그 결과와 함께 서버에 저장한다.
   */
  /**
   * 푸시 알림 on/off.
   *  - 켤 때: 브라우저 권한 → 이 기기 구독 등록 → 계정 설정 저장.
   *    권한이 막혀 있으면 설정을 켜지 않고 안내만 남긴다. (켜졌다고 착각하면 안 된다)
   *  - 끌 때: 이 기기의 구독을 지우고 설정도 끈다.
   */
  const togglePush = async () => {
    const next = !user.pushOptIn;
    setPushNote('');
    setPushBusy(true);
    try {
      if (next) {
        const info = await pushApi.info();
        if (!info.publicKey) throw new Error('지금은 알림을 켤 수 없어요. 잠시 후 다시 시도해주세요.');
        await enablePush(info.publicKey);
        setPushOnDevice(true);
      } else {
        await disablePush();
        setPushOnDevice(false);
      }
      setUser(await authApi.setPush(next));
    } catch (e: any) {
      setPushNote(e?.message || appErrMsg(e, '설정을 바꾸지 못했어요.'));
    } finally {
      setPushBusy(false);
    }
  };

  /** 담소 셀렉팅 프로필 노출 on/off. 끄면 남의 목록에서 빠지고 나도 열람할 수 없다. */
  const toggleVisible = async () => {
    if (!datingProfile) return;
    const next = !datingProfile.visible;
    if (next === false && !confirm('프로필 노출을 끌까요?\n상대 목록에서 빠지고, 나도 이성 프로필을 볼 수 없어요.')) {
      return;
    }
    setVisibilityBusy(true);
    setError('');
    try {
      setDatingProfile(await datingApi.setVisible(datingProfile.id, next));
    } catch (e) {
      setError(appErrMsg(e, '노출 설정을 바꾸지 못했어요.'));
    } finally {
      setVisibilityBusy(false);
    }
  };

  const withdraw = async () => {
    if (!confirm('탈퇴하면 회원 정보와 방명록 작성 권한이 사라져요. 계속할까요?')) return;
    try {
      await authApi.withdraw();
    } finally {
      await refresh();
      window.location.assign('/login');
    }
  };

  return (
    <>
      <PageBody>
      {needsProfile && (
        <div className="rounded-xl bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-800">
          닉네임·성명·생년월일·휴대폰번호를 입력해야 서비스를 이용할 수 있어요. (만 {MIN_AGE}세 이상)
        </div>
      )}

      {/* ─── 내 정보 ───────────────────────────── */}
      <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-5">
        {editing ? (
          <div className="space-y-4">
            <div>
              <label className={LABEL}>닉네임 *</label>
              <input
                className={INPUT}
                value={nickname}
                onChange={(e) => setNickname(e.target.value.replace(/\s/g, ''))}
                placeholder="방명록에 표시될 이름 (2~12자)"
              />
              {nickname.length > 0 && !NICKNAME_RE.test(nickname) && (
                <p className="mt-1 text-xs text-red-500">공백 없이 2~12자로 지어주세요.</p>
              )}
            </div>
            <div>
              <label className={LABEL}>성명 *</label>
              <input className={INPUT} value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div>
              <label className={LABEL}>생년월일 *</label>
              <input
                className={INPUT}
                type="date"
                value={birthDate}
                max={new Date().toISOString().slice(0, 10)}
                onChange={(e) => setBirthDate(e.target.value)}
              />
              {underAge && (
                <p className="mt-1 text-xs text-red-500">만 {MIN_AGE}세 이상만 이용할 수 있어요.</p>
              )}
            </div>
            <div>
              <label className={LABEL}>휴대폰번호 *</label>
              <input
                className={INPUT}
                type="tel"
                inputMode="numeric"
                value={phone}
                onChange={(e) => setPhone(formatPhone(e.target.value))}
                placeholder="010-0000-0000"
              />
              {phone.length > 0 && !phoneValid && (
                <p className="mt-1 text-xs text-red-500">휴대폰번호를 정확히 입력해주세요.</p>
              )}
            </div>
            {error && <div className="text-sm text-red-600">{error}</div>}
            <div className="flex gap-2">
              {!needsProfile && (
                <button
                  className="flex-1 rounded-full border border-stone-300 bg-white text-stone-700 py-3 font-semibold active:bg-stone-100"
                  onClick={() => {
                    setEditing(false);
                    setNickname(user.nickname ?? '');
                    setName(user.name);
                    setBirthDate(user.birthDate ?? '');
                    setPhone(formatPhone(user.phone ?? ''));
                    setError('');
                  }}
                >
                  취소
                </button>
              )}
              <button
                className={PRIMARY_BTN + ' flex-1'}
                disabled={
                  busy || !NICKNAME_RE.test(nickname) || !name.trim() ||
                  !birthDate || underAge || !phoneValid
                }
                onClick={save}
              >
                {busy ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        ) : (
          <>
            <Row label="아이디" value={user.loginId ?? '미설정'} />
            <Row label="닉네임" value={user.nickname ?? '미설정'} />
            <Row label="성명" value={`${user.name} (비공개)`} />
            <Row
              label="생년월일"
              value={user.birthDate ? `${user.birthDate} (만 ${user.age}세)` : '미입력'}
            />
            <Row label="휴대폰번호" value={user.phone ? formatPhone(user.phone) : '미입력'} />
            <button className={SUB_BTN + ' mt-4'} onClick={() => setEditing(true)}>
              내 정보 수정
            </button>
          </>
        )}
      </section>

      {/* ─── 내 신청 (담소 셀렉팅) ─────────────── */}
      {canSelecting && (
      <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-5">
        <div className="text-sm font-bold text-stone-800">담소 셀렉팅 프로필</div>
        <p className="mt-1 text-xs text-stone-500">
          {datingProfile === undefined
            ? '확인 중이에요...'
            : datingProfile
              ? '등록된 프로필이에요. 연락처가 바뀌면 꼭 고쳐주세요.'
              : '아직 프로필을 등록하지 않았어요.'}
        </p>

        {datingProfile && (
          <div className="mt-3 rounded-xl bg-stone-50 px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="text-sm text-stone-700">
                {datingProfile.name} · {datingProfile.birthYear}년생 ·{' '}
                {datingProfile.gender === 'MALE' ? '남성' : '여성'}
              </span>
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                  datingProfile.canSelect
                    ? 'bg-emerald-100 text-emerald-700'
                    : 'bg-amber-100 text-amber-800'
                }`}
              >
                {datingProfile.statusLabel}
              </span>
            </div>
            {datingProfile.notice && (
              <p className="mt-1.5 text-xs text-stone-500 leading-relaxed">
                {datingProfile.notice}
              </p>
            )}
          </div>
        )}

        {/* 노출 스위치 — 관리자가 막아둔 상태(심사중·제외)에서는 켤 수 없다 */}
        {datingProfile && (
          <div className="mt-3 flex items-center justify-between gap-3 rounded-xl border border-stone-200 px-4 py-3">
            <div className="min-w-0">
              <div className="text-sm font-semibold text-stone-800">프로필 노출</div>
              <p className="mt-0.5 text-xs text-stone-500 leading-relaxed">
                {datingProfile.visible
                  ? '지금 이성 회원 목록에 보이고 있어요.'
                  : '목록에서 빠져 있어요. 나도 이성 프로필을 볼 수 없어요.'}
              </p>
            </div>
            <button
              onClick={toggleVisible}
              disabled={visibilityBusy}
              aria-pressed={datingProfile.visible}
              className={`shrink-0 w-14 h-8 rounded-full transition relative disabled:opacity-40 ${
                datingProfile.visible ? 'bg-damso-500' : 'bg-stone-300'
              }`}
            >
              <span
                className={`absolute top-1 w-6 h-6 rounded-full bg-white transition-all ${
                  datingProfile.visible ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>
        )}

        {datingProfile !== undefined && (
          <Link
            to={datingProfile ? '/selecting?edit=1' : '/selecting'}
            className={
              datingProfile
                ? SUB_BTN + ' mt-3 block text-center'
                : 'mt-3 block w-full rounded-full bg-damso-500 text-white py-2.5 text-sm font-semibold text-center active:bg-damso-600'
            }
          >
            {datingProfile ? '프로필 수정' : '프로필 등록하러 가기'}
          </Link>
        )}
      </section>
      )}

      {/* ─── 받은 좋아요 ───────────────────────── */}
      {canSelecting && (
      <Link
        to="/likes"
        className="flex items-center gap-3 rounded-2xl bg-white border border-stone-200 shadow-sm p-5 active:bg-stone-50"
      >
        <span className="text-2xl select-none">💌</span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-bold text-stone-800">받은 좋아요</span>
          <span className="mt-0.5 block text-xs text-stone-500">
            나에게 마음을 보내온 분들을 확인하고 만남 약속까지 잡아보세요.
          </span>
        </span>
        <span className="text-stone-300">›</span>
      </Link>
      )}

      {/* ─── 로그인 수단 ───────────────────────── */}
      <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-5">
        <div className="text-sm font-bold text-stone-800">로그인 수단</div>
        <p className="mt-1 text-xs text-stone-500">
          한 계정에 아이디와 간편로그인을 함께 연결할 수 있어요.
        </p>

        <div className="mt-3 space-y-2">
          <div className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5">
            <span className="text-sm text-stone-700">
              아이디 {user.loginId ? `(${user.loginId})` : ''}
            </span>
            <span className={`text-xs font-semibold ${user.hasPassword ? 'text-emerald-600' : 'text-stone-400'}`}>
              {user.hasPassword ? '사용 중' : '미설정'}
            </span>
          </div>
          {user.linkedProviders.map((p) => (
            <div key={p} className="flex items-center justify-between rounded-xl bg-stone-50 px-4 py-2.5">
              <span className="text-sm text-stone-700">{p === 'KAKAO' ? '카카오' : '네이버'}</span>
              <button
                onClick={() => unlink(p)}
                className="text-xs font-semibold text-stone-400 active:text-red-500"
              >
                연결 해제
              </button>
            </div>
          ))}
        </div>

        {unlinked.length > 0 && (
          <div className="mt-3 space-y-2">
            <SocialButtons providers={unlinked} link />
          </div>
        )}

        <button className={SUB_BTN + ' mt-3'} onClick={() => setShowPassword((v) => !v)}>
          {user.hasPassword ? '비밀번호 변경' : '아이디·비밀번호 만들기'}
        </button>
        {showPassword && (
          <PasswordForm
            hasPassword={user.hasPassword}
            onDone={(updated) => { setUser(updated); setShowPassword(false); }}
          />
        )}
      </section>

      {/* ─── 알림 설정 ─────────────────────────── */}
      <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-5">
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="text-sm font-bold text-stone-800">푸시 알림 받기</div>
            <p className="mt-1 text-xs text-stone-500">
              앱을 닫아둬도 좋아요·만남 약속 소식을 휴대폰으로 알려드려요.
            </p>
            {user.pushOptIn && !pushOnDevice && (
              <p className="mt-1 text-xs text-amber-600">
                이 기기는 아직 등록되지 않았어요. 스위치를 껐다 켜주세요.
              </p>
            )}
          </div>
          <button
            onClick={togglePush}
            disabled={pushBusy}
            aria-pressed={user.pushOptIn}
            className={`shrink-0 w-14 h-8 rounded-full transition relative disabled:opacity-40 ${
              user.pushOptIn ? 'bg-damso-500' : 'bg-stone-300'
            }`}
          >
            <span
              className={`absolute top-1 w-6 h-6 rounded-full bg-white transition-all ${
                user.pushOptIn ? 'left-7' : 'left-1'
              }`}
            />
          </button>
        </div>
        {pushNote && <p className="mt-3 text-xs text-amber-600">{pushNote}</p>}
        {!pushSupported() && (
          <p className="mt-3 text-xs text-stone-400">
            이 브라우저는 푸시 알림을 지원하지 않아요. 앱을 열면 알림함에서 확인할 수 있어요.
          </p>
        )}
        {pushSupported() && needsHomeScreen() && (
          <p className="mt-3 text-xs text-stone-400">
            아이폰은 공유 버튼 → &apos;홈 화면에 추가&apos; 로 설치하면 알림을 받을 수 있어요.
          </p>
        )}
      </section>

      {/* ─── 계정 ──────────────────────────────── */}
      <section className="rounded-2xl bg-white border border-stone-200 shadow-sm p-5 space-y-2">
        <button className={SUB_BTN} onClick={() => signOut()}>로그아웃</button>
        <button
          className="w-full py-2 text-xs text-stone-400 active:text-red-500"
          onClick={withdraw}
        >
          회원 탈퇴
        </button>
      </section>
      </PageBody>
    </>
  );
}

/** 비밀번호 변경 / (간편로그인 전용 계정의) 아이디·비밀번호 신규 설정. */
function PasswordForm({
  hasPassword,
  onDone,
}: {
  hasPassword: boolean;
  onDone: (user: AppUser) => void;
}) {
  const [loginId, setLoginId] = useState('');
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [next2, setNext2] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const valid =
    next.length >= MIN_PASSWORD &&
    next === next2 &&
    (hasPassword ? current.length > 0 : LOGIN_ID_RE.test(loginId));

  const submit = async () => {
    setBusy(true);
    setError('');
    try {
      onDone(await authApi.changePassword({
        currentPassword: hasPassword ? current : undefined,
        newPassword: next,
        loginId: hasPassword ? undefined : loginId.trim(),
      }));
    } catch (e) {
      setError(appErrMsg(e, '변경하지 못했어요.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mt-3 space-y-3 border-t border-stone-100 pt-3">
      {!hasPassword && (
        <div>
          <label className={LABEL}>아이디 *</label>
          <input
            className={INPUT}
            value={loginId}
            autoCapitalize="none"
            onChange={(e) => setLoginId(e.target.value.trim())}
            placeholder="영문·숫자 4~20자"
          />
        </div>
      )}
      {hasPassword && (
        <div>
          <label className={LABEL}>현재 비밀번호 *</label>
          <input
            className={INPUT}
            type="password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </div>
      )}
      <div>
        <label className={LABEL}>새 비밀번호 *</label>
        <input
          className={INPUT}
          type="password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          placeholder={`${MIN_PASSWORD}자 이상`}
        />
      </div>
      <div>
        <label className={LABEL}>새 비밀번호 확인 *</label>
        <input
          className={INPUT}
          type="password"
          value={next2}
          onChange={(e) => setNext2(e.target.value)}
        />
      </div>
      {error && <div className="text-sm text-red-600">{error}</div>}
      <button className={PRIMARY_BTN} disabled={busy || !valid} onClick={submit}>
        {busy ? '저장 중...' : '저장'}
      </button>
    </div>
  );
}
