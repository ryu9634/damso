import { useEffect, useRef, useState } from 'react';
import type { DatingProfileCard, DatingQuestion, DatingSelf, Gender } from '../../../shared/types';
import { datingApi, type DatingProfileInput } from './dating';
import { compressImage, formatBytes } from '../../../shared/utils/image';
import { formatPhone } from '../../../shared/utils/phone';
import { useAppAuth } from '../auth/useAppAuth';
import { Link, useSearchParams } from 'react-router-dom';
import PageHeading, { PageBody } from '../../layout/PageHeading';
import VerifiedBadge from '../../layout/VerifiedBadge';

/** 서버 업로드 제한(application.yml 의 max-file-size 와 맞춘다). */
const MAX_PHOTO_BYTES = 8 * 1024 * 1024;

const STORAGE_KEY = 'damso.dating.profileId';

type Screen = 'loading' | 'landing' | 'consent' | 'register' | 'link' | 'browse' | 'edit' | 'blocked';

/** 본문 여백은 다른 탭과 동일하게 PageBody 를 쓴다. (상단 제목은 PageHeading) */
function Shell({ children }: { children: React.ReactNode }) {
  return <PageBody>{children}</PageBody>;
}

function errMsg(e: any, fallback: string): string {
  return e?.response?.data?.message || fallback;
}

const PRIMARY_BTN =
  'w-full rounded-full bg-damso-500 text-white py-3 font-semibold active:bg-damso-600 disabled:opacity-40 disabled:active:bg-damso-500';
const SUB_BTN =
  'w-full rounded-full border border-stone-300 bg-white text-stone-700 py-3 font-semibold active:bg-stone-100';
const NAV_BTN =
  'flex-1 rounded-full border border-stone-300 bg-white text-stone-700 py-3 font-semibold active:bg-stone-100 disabled:opacity-30 disabled:active:bg-white';
const INPUT =
  'w-full rounded-xl border border-stone-300 px-4 py-3 text-stone-800 focus:outline-none focus:border-damso-500';
const LABEL = 'block text-sm font-medium text-stone-600 mb-1';

interface RegForm {
  name: string;
  contact: string;
  birthYear: string;
  gender: Gender | '';
  photoUrl: string | null;
}

const EMPTY_FORM: RegForm = {
  name: '', contact: '', birthYear: '', gender: '', photoUrl: null,
};

/** 커스텀 질문 하나의 입력칸. */
function QuestionField({
  q, value, onChange,
}: {
  q: DatingQuestion;
  value: string;
  onChange: (v: string) => void;
}) {
  if (q.type === 'SELECT') {
    return (
      <div className="flex flex-wrap gap-2">
        {q.options.map((opt) => (
          <button
            key={opt}
            type="button"
            onClick={() => onChange(opt)}
            className={
              'rounded-lg px-3 py-2 text-sm font-semibold border ' +
              (value === opt
                ? 'bg-damso-500 text-white border-damso-500'
                : 'bg-white text-stone-600 border-stone-300')
            }
          >
            {opt}
          </button>
        ))}
      </div>
    );
  }
  if (q.type === 'TEXTAREA') {
    return (
      <textarea
        className={`${INPUT} min-h-[72px]`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={q.label}
      />
    );
  }
  return (
    <input
      className={INPUT}
      type={q.type === 'NUMBER' ? 'number' : 'text'}
      inputMode={q.type === 'NUMBER' ? 'numeric' : undefined}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={q.label}
    />
  );
}

/** 커스텀 질문 전체 입력 목록. (등록 STEP 2 / 내 정보 수정에서 공용) */
function QuestionList({
  questions, answers, onChange,
}: {
  questions: DatingQuestion[];
  answers: Record<number, string>;
  onChange: (id: number, v: string) => void;
}) {
  return (
    <>
      {questions.map((q) => (
        <div key={q.id}>
          <label className={LABEL}>
            {q.label}
            {q.required && <span className="text-damso-500"> *</span>}
          </label>
          <QuestionField
            q={q}
            value={answers[q.id] ?? ''}
            onChange={(v) => onChange(q.id, v)}
          />
        </div>
      ))}
    </>
  );
}

/** 사진 선택/변경/삭제 위젯. (등록 STEP 2 / 내 정보 수정에서 공용) */
function PhotoPicker({
  photoUrl, inputRef, photoStage, photoNote, onPick, onRemove,
}: {
  photoUrl: string | null;
  inputRef: React.MutableRefObject<HTMLInputElement | null>;
  photoStage: '' | 'compress' | 'upload';
  photoNote: string;
  onPick: (file: File | undefined) => void;
  onRemove: () => void;
}) {
  const uploading = photoStage !== '';
  return (
    <div>
      <label className={LABEL}>사진 업로드</label>

      {/* 파일 입력은 숨기고 큼직한 버튼으로 대신 연다 (모바일 터치 영역 확보) */}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        onChange={(e) => onPick(e.target.files?.[0])}
        className="sr-only"
      />

      {photoUrl ? (
        <div className="space-y-2">
          <img
            src={photoUrl}
            alt="미리보기"
            className="w-full h-64 rounded-2xl object-cover bg-stone-100"
          />
          <div className="flex gap-2">
            <button
              type="button"
              disabled={uploading}
              onClick={() => inputRef.current?.click()}
              className="flex-1 rounded-full border border-stone-300 bg-white text-stone-700 py-2.5 text-sm font-semibold active:bg-stone-100 disabled:opacity-40"
            >
              사진 변경
            </button>
            <button
              type="button"
              disabled={uploading}
              onClick={onRemove}
              className="flex-1 rounded-full border border-red-200 bg-white text-red-600 py-2.5 text-sm font-semibold active:bg-red-50 disabled:opacity-40"
            >
              삭제
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
          className="w-full rounded-2xl border-2 border-dashed border-stone-300 bg-white py-8 px-4 text-center active:bg-stone-50 disabled:opacity-60"
        >
          <span className="block text-3xl">📷</span>
          <span className="block mt-2 text-sm font-semibold text-stone-700">
            사진 선택하기
          </span>
          <span className="block mt-1 text-xs text-stone-400">
            사진첩에서 고르거나 바로 촬영할 수 있어요
          </span>
        </button>
      )}

      {uploading && (
        <p className="mt-2 text-sm text-damso-600">
          {photoStage === 'compress' ? '사진 준비 중...' : '업로드 중...'}
        </p>
      )}
      {!uploading && photoNote && (
        <p className="mt-2 text-xs text-stone-400">{photoNote}</p>
      )}
    </div>
  );
}

export default function DatingPage() {
  const { user } = useAppAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [screen, setScreen] = useState<Screen>('loading');
  const [profileId, setProfileId] = useState<number | null>(null);
  // 심사중·휴면·제외처럼 지금은 고를 수 없는 상태일 때 보여줄 안내
  const [blocked, setBlocked] = useState<{ label: string; notice: string } | null>(null);

  // 관리자 커스텀 질문
  const [questions, setQuestions] = useState<DatingQuestion[]>([]);

  // registration state
  const [regStep, setRegStep] = useState<1 | 2>(1);
  const [agreed, setAgreed] = useState(false);
  const [form, setForm] = useState<RegForm>(EMPTY_FORM);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [regError, setRegError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 사진 업로드 상태
  const photoInputRef = useRef<HTMLInputElement | null>(null);
  const [photoStage, setPhotoStage] = useState<'' | 'compress' | 'upload'>('');
  const [photoNote, setPhotoNote] = useState('');
  const uploading = photoStage !== '';

  // 계정 도입 전 등록한 프로필 연결 state
  const [linkName, setLinkName] = useState('');
  const [linkContact, setLinkContact] = useState('');
  const [linkError, setLinkError] = useState('');
  const [linking, setLinking] = useState(false);

  // 내 정보 수정 state (폼은 등록 화면의 form/answers 를 그대로 재사용한다)
  const [editLoading, setEditLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // browse state — 한 번에 한 명씩만 보여주고 이전/다음으로 넘긴다
  const [cards, setCards] = useState<DatingProfileCard[]>([]);
  const [cardIndex, setCardIndex] = useState(0);
  const [browseLoading, setBrowseLoading] = useState(false);
  const [browseError, setBrowseError] = useState('');
  const [likeError, setLikeError] = useState('');
  // '관심 없음'으로 치워둔 프로필 수 — 0보다 크면 다시 보기 버튼을 띄운다
  const [passedCount, setPassedCount] = useState(0);

  useEffect(() => {
    datingApi.questions().then(setQuestions).catch(() => setQuestions([]));
  }, []);

  // 로그인한 계정에 연결된 프로필을 찾아 바로 열람 화면으로 보낸다.
  useEffect(() => {
    let alive = true;
    datingApi.me()
      .then((self) => {
        if (!alive) return;
        if (self) {
          localStorage.setItem(STORAGE_KEY, String(self.id));
          setProfileId(self.id);
          applySelf(self);
        } else {
          localStorage.removeItem(STORAGE_KEY);
          setScreen('landing');
        }
      })
      .catch(() => { if (alive) setScreen('landing'); });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    if (screen === 'browse' && profileId != null) {
      loadBrowse(profileId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screen, profileId]);

  // 마이페이지의 '프로필 수정' 으로 들어온 경우 (/selecting?edit=1) 곧바로 수정 화면을 연다.
  useEffect(() => {
    if (screen !== 'browse' || profileId == null) return;
    if (searchParams.get('edit') !== '1') return;
    const next = new URLSearchParams(searchParams);
    next.delete('edit');
    setSearchParams(next, { replace: true });
    openEdit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screen, profileId, searchParams]);

  async function loadBrowse(id: number) {
    setBrowseLoading(true);
    setBrowseError('');
    try {
      const list = await datingApi.browse(id);
      setCards(list);
      setCardIndex(0);
      datingApi.passCount(id).then(setPassedCount).catch(() => setPassedCount(0));
    } catch (e: any) {
      setBrowseError(errMsg(e, '프로필을 불러오지 못했어요.'));
    } finally {
      setBrowseLoading(false);
    }
  }

  function persistProfile(id: number) {
    localStorage.setItem(STORAGE_KEY, String(id));
    setProfileId(id);
    setScreen('browse');
  }

  /** 서버가 알려준 상태에 따라 열람 화면 또는 안내 화면으로 보낸다. */
  function applySelf(self: DatingSelf) {
    if (self.canSelect) {
      setBlocked(null);
      setScreen('browse');
      return;
    }
    setBlocked({
      label: self.statusLabel,
      notice: self.notice ?? '지금은 담소 셀렉팅을 이용할 수 없어요.',
    });
    setScreen('blocked');
  }

  function set<K extends keyof RegForm>(key: K, value: RegForm[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function resetRegistration() {
    setForm(EMPTY_FORM);
    setAnswers({});
    setRegStep(1);
    setAgreed(false);
    setPhotoNote('');
  }

  /** 회원가입 때 받은 정보(성명·연락처·생년월일)를 등록 폼에 미리 채운다. */
  function prefillFromAccount() {
    setForm({
      ...EMPTY_FORM,
      name: user?.name ?? '',
      contact: formatPhone(user?.phone ?? ''),
      birthYear: user?.birthDate ? user.birthDate.slice(0, 4) : '',
    });
    setAnswers({});
    setRegStep(1);
    setAgreed(false);
    setPhotoNote('');
  }

  async function handlePhoto(file: File | undefined) {
    // 같은 파일을 다시 골라도 change 가 발생하도록 값을 비워둔다
    if (photoInputRef.current) photoInputRef.current.value = '';
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setRegError('사진 파일만 올릴 수 있어요.');
      return;
    }

    setRegError('');
    setPhotoNote('');
    setPhotoStage('compress');
    try {
      // 사진첩 원본은 3~12MB라 그대로 올리면 느리다. 줄여서 올린다.
      const shrunk = await compressImage(file);

      if (shrunk.size > MAX_PHOTO_BYTES) {
        setRegError(
          `사진 용량이 너무 커요. (${formatBytes(shrunk.size)}) 8MB 이하 사진으로 올려주세요.`,
        );
        return;
      }

      setPhotoStage('upload');
      const url = await datingApi.uploadPhoto(shrunk);
      set('photoUrl', url);
      setPhotoNote(
        shrunk.size < file.size
          ? `업로드 완료!`
          : `업로드 완료!`,
      );
    } catch (e: any) {
      setRegError(errMsg(e, '사진 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.'));
    } finally {
      setPhotoStage('');
    }
  }

  function removePhoto() {
    set('photoUrl', null);
    setPhotoNote('');
    if (photoInputRef.current) photoInputRef.current.value = '';
  }

  function validateStep1(): string | null {
    if (!form.name.trim()) return '이름을 입력해 주세요.';
    if (!form.contact.trim()) return '연락처를 입력해 주세요.';
    if (!form.birthYear.trim() || Number.isNaN(Number(form.birthYear)))
      return '출생년도를 숫자로 입력해 주세요.';
    if (!form.gender) return '성별을 선택해 주세요.';
    return null;
  }

  function validateStep2(): string | null {
    for (const q of questions) {
      if (q.required && !(answers[q.id] ?? '').trim()) {
        return `'${q.label}'을(를) 입력해 주세요.`;
      }
    }
    return null;
  }

  function goStep2() {
    const err = validateStep1();
    if (err) {
      setRegError(err);
      return;
    }
    setRegError('');
    setRegStep(2);
  }

  async function submitProfile() {
    const err = validateStep2();
    if (err) {
      setRegError(err);
      return;
    }
    setRegError('');
    setSubmitting(true);
    try {
      const input: DatingProfileInput = {
        name: form.name.trim(),
        contact: form.contact.trim(),
        birthYear: Number(form.birthYear),
        gender: form.gender as Gender,
        photoUrl: form.photoUrl,
        answers: questions
          .map((q) => ({ questionId: q.id, value: (answers[q.id] ?? '').trim() }))
          .filter((a) => a.value),
        agreedPrivacy: true,
      };
      const self = await datingApi.createProfile(input);
      localStorage.setItem(STORAGE_KEY, String(self.id));
      setProfileId(self.id);
      resetRegistration();
      // 새로 등록하면 보통 '심사중'이라 열람 대신 안내 화면으로 간다.
      applySelf(self);
    } catch (e: any) {
      setRegError(errMsg(e, '프로필 등록에 실패했어요.'));
    } finally {
      setSubmitting(false);
    }
  }

  /** 내 정보 수정 화면 열기 — 서버에서 현재 값을 받아 폼을 채운다. */
  async function openEdit() {
    if (profileId == null) return;
    setRegError('');
    setPhotoNote('');
    setEditLoading(true);
    setScreen('edit');
    try {
      const self = await datingApi.getSelf(profileId);
      setForm({
        name: self.name,
        contact: self.contact,
        birthYear: String(self.birthYear),
        gender: self.gender,
        photoUrl: self.photoUrl,
      });
      setAnswers(
        Object.fromEntries(self.answers.map((a) => [a.questionId, a.value])),
      );
    } catch (e: any) {
      setRegError(errMsg(e, '내 정보를 불러오지 못했어요.'));
    } finally {
      setEditLoading(false);
    }
  }

  async function submitEdit() {
    if (profileId == null) return;
    const err = validateStep1() ?? validateStep2();
    if (err) {
      setRegError(err);
      return;
    }
    setRegError('');
    setSaving(true);
    try {
      const updated = await datingApi.updateProfile(profileId, {
        name: form.name.trim(),
        contact: form.contact.trim(),
        birthYear: Number(form.birthYear),
        photoUrl: form.photoUrl,
        answers: questions
          .map((q) => ({ questionId: q.id, value: (answers[q.id] ?? '').trim() }))
          .filter((a) => a.value),
      });
      resetRegistration();
      applySelf(updated);
    } catch (e: any) {
      setRegError(errMsg(e, '정보 수정에 실패했어요.'));
    } finally {
      setSaving(false);
    }
  }

  /** 계정 도입 전에 등록한 프로필을 이름+연락처로 확인해 내 계정에 연결한다. */
  async function submitLink() {
    if (!linkName.trim() || !linkContact.trim()) {
      setLinkError('이름과 연락처를 입력해 주세요.');
      return;
    }
    setLinkError('');
    setLinking(true);
    try {
      const self = await datingApi.link(linkName.trim(), linkContact.trim());
      setLinkName('');
      setLinkContact('');
      localStorage.setItem(STORAGE_KEY, String(self.id));
      setProfileId(self.id);
      applySelf(self);
    } catch (e: any) {
      setLinkError(errMsg(e, '일치하는 프로필을 찾지 못했어요.'));
    } finally {
      setLinking(false);
    }
  }

  /** 좋아요/취소. 좋아요를 보내면 상대에게 알림이 간다. */
  async function toggleLike(card: DatingProfileCard) {
    if (profileId == null) return;
    const next = !card.liked;
    setLikeError('');
    // optimistic update
    setCards((cs) =>
      cs.map((c) => (c.id === card.id ? { ...c, liked: next } : c)),
    );
    try {
      if (next) {
        await datingApi.like(profileId, card.id);
      } else {
        await datingApi.unlike(profileId, card.id);
      }
    } catch (e: any) {
      // revert on failure — 하루 선택 제한·이미 답한 좋아요 등 서버 메시지를 그대로 보여준다
      setCards((cs) =>
        cs.map((c) => (c.id === card.id ? { ...c, liked: !next } : c)),
      );
      setLikeError(errMsg(e, '처리하지 못했어요. 잠시 후 다시 시도해 주세요.'));
    }
  }

  /** 관심 없음 — 목록에서 바로 빼고, 다음 사람이 그 자리에 온다. */
  async function passCard(card: DatingProfileCard) {
    if (profileId == null) return;
    setLikeError('');
    const before = cards;
    setCards((cs) => cs.filter((c) => c.id !== card.id));
    try {
      await datingApi.pass(profileId, card.id);
      setPassedCount((n) => n + 1);
    } catch (e: any) {
      setCards(before);
      setLikeError(errMsg(e, '처리하지 못했어요. 잠시 후 다시 시도해 주세요.'));
    }
  }

  /** 치워둔 프로필 되살리기. */
  async function restorePassed() {
    if (profileId == null) return;
    try {
      await datingApi.clearPasses(profileId);
      setPassedCount(0);
      await loadBrowse(profileId);
    } catch (e: any) {
      setLikeError(errMsg(e, '되살리지 못했어요.'));
    }
  }

  // 목록이 줄어든 뒤에도 인덱스가 범위를 벗어나지 않게 보정한다
  const safeIndex = Math.min(cardIndex, Math.max(0, cards.length - 1));
  const currentCard = cards[safeIndex];

  function moveCard(step: 1 | -1) {
    const next = safeIndex + step;
    if (next < 0 || next >= cards.length) return;
    setCardIndex(next);
    // 긴 프로필을 보다가 넘기면 아래쪽에 머물러 있어서 위로 올려준다
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---- Screens ----

  if (screen === 'loading') {
    return (
      <Shell>
        <div className="pt-10 text-center text-sm text-stone-400">불러오는 중...</div>
      </Shell>
    );
  }

  if (screen === 'landing') {
    return (
      <Shell>
        <div className="rounded-2xl bg-white shadow-sm border border-stone-200 p-6 text-center">
          <p className="text-sm text-stone-500">
            {user?.nickname ? `${user.nickname}님, ` : ''}담소가 해주는 소개팅에 오신 걸 환영해요.
          </p>
          <div className="mt-6 space-y-3">
            <button
              className={PRIMARY_BTN}
              onClick={() => {
                setRegError('');
                prefillFromAccount();
                setScreen('consent');
              }}
            >
              프로필 등록하기
            </button>
            <button
              className={SUB_BTN}
              onClick={() => {
                setLinkError('');
                setScreen('link');
              }}
            >
              이전에 등록한 프로필 연결하기
            </button>
          </div>
        </div>
      </Shell>
    );
  }

  if (screen === 'consent') {
    return (
      <>
        <PageHeading
          title="개인정보 수집·이용 동의"
          description="담소 셀렉팅 프로필 등록 전 확인해주세요."
          back={() => setScreen('landing')}
        />
        <Shell>
        <div className="rounded-2xl bg-white shadow-sm border border-stone-200 p-6">
          <p className="text-sm text-stone-600 leading-relaxed">
            소개팅 매칭을 위해 이름, 연락처, 출생년도, 성별, 사진 및 프로필 답변 등의
            정보를 수집·이용해요. 수집한 정보는 매칭 서비스 제공 목적으로만 사용되며,
            주인장이 매칭을 도와드릴 때 활용돼요. 동의를 거부하실 수 있으나, 이 경우
            매칭 서비스 이용이 어려워요.
          </p>
          <label className="mt-5 flex items-center gap-3 cursor-pointer select-none">
            <input
              type="checkbox"
              className="w-5 h-5 accent-damso-500"
              checked={agreed}
              onChange={(e) => setAgreed(e.target.checked)}
            />
            <span className="text-sm text-stone-700">
              위 개인정보 수집·이용에 동의합니다.
            </span>
          </label>
          <button
            className={`${PRIMARY_BTN} mt-6`}
            disabled={!agreed}
            onClick={() => {
              setRegError('');
              setScreen('register');
            }}
          >
            동의하고 시작
          </button>
        </div>
        </Shell>
      </>
    );
  }

  if (screen === 'register') {
    return (
      <>
        <PageHeading
          title={regStep === 1 ? '기본 정보' : '상세 정보'}
          description={`프로필 등록 · STEP ${regStep} / 2`}
          back={() => {
            if (regStep === 2) {
              setRegError('');
              setRegStep(1);
            } else {
              setScreen('consent');
            }
          }}
        />
        <Shell>

        {regStep === 1 && (
          <div className="space-y-4">
            <p className="rounded-xl bg-damso-50 border border-damso-100 px-4 py-2.5 text-xs text-damso-700">
              회원 정보에 있는 이름·연락처·출생년도를 미리 채워뒀어요. 필요하면 고칠 수 있어요.
            </p>
            <div>
              <label className={LABEL}>이름</label>
              <input
                className={INPUT}
                value={form.name}
                onChange={(e) => set('name', e.target.value)}
                placeholder="이름"
              />
            </div>
            <div>
              <label className={LABEL}>연락처</label>
              <input
                className={INPUT}
                value={form.contact}
                onChange={(e) => set('contact', e.target.value)}
                placeholder="인스타 ID 또는 폰번호"
              />
            </div>
            <div>
              <label className={LABEL}>출생년도</label>
              <input
                className={INPUT}
                type="number"
                inputMode="numeric"
                value={form.birthYear}
                onChange={(e) => set('birthYear', e.target.value)}
                placeholder="예: 1996"
              />
            </div>
            <div>
              <label className={LABEL}>성별</label>
              <div className="grid grid-cols-2 gap-3">
                {(['MALE', 'FEMALE'] as Gender[]).map((g) => (
                  <button
                    key={g}
                    type="button"
                    onClick={() => set('gender', g)}
                    className={
                      'rounded-full py-3 font-semibold border ' +
                      (form.gender === g
                        ? 'bg-damso-500 text-white border-damso-500'
                        : 'bg-white text-stone-600 border-stone-300')
                    }
                  >
                    {g === 'MALE' ? '남성' : '여성'}
                  </button>
                ))}
              </div>
            </div>

            {regError && <p className="text-sm text-red-500">{regError}</p>}

            <button className={PRIMARY_BTN} onClick={goStep2}>
              다음
            </button>
          </div>
        )}

        {regStep === 2 && (
          <div className="space-y-4">
            <QuestionList
              questions={questions}
              answers={answers}
              onChange={(id, v) => setAnswers((a) => ({ ...a, [id]: v }))}
            />

            <PhotoPicker
              photoUrl={form.photoUrl}
              inputRef={photoInputRef}
              photoStage={photoStage}
              photoNote={photoNote}
              onPick={handlePhoto}
              onRemove={removePhoto}
            />

            {regError && <p className="text-sm text-red-500">{regError}</p>}

            <button
              className={PRIMARY_BTN}
              disabled={submitting || uploading}
              onClick={submitProfile}
            >
              {submitting ? '등록 중...' : '프로필 등록 완료'}
            </button>
          </div>
        )}
        </Shell>
      </>
    );
  }

  if (screen === 'edit') {
    return (
      <>
        <PageHeading
          title="내 정보 수정"
          description="등록할 때 작성한 내용을 언제든 고칠 수 있어요."
          back={() => {
            resetRegistration();
            setRegError('');
            setScreen(blocked ? 'blocked' : 'browse');
          }}
        />
        <Shell>

        {editLoading ? (
          <p className="text-center text-stone-400 py-10">불러오는 중...</p>
        ) : (
          <div className="space-y-4">
            <div>
              <label className={LABEL}>이름</label>
              <input
                className={INPUT}
                value={form.name}
                onChange={(e) => set('name', e.target.value)}
                placeholder="이름"
              />
            </div>
            <div>
              <label className={LABEL}>연락처</label>
              <input
                className={INPUT}
                value={form.contact}
                onChange={(e) => set('contact', e.target.value)}
                placeholder="인스타 ID 또는 폰번호"
              />
            </div>
            <p className="text-xs text-stone-400 -mt-2">
              주인장이 매칭을 도와드릴 때 쓰는 정보예요. 연락처가 바뀌면 꼭 수정해 주세요.
            </p>
            <div>
              <label className={LABEL}>출생년도</label>
              <input
                className={INPUT}
                type="number"
                inputMode="numeric"
                value={form.birthYear}
                onChange={(e) => set('birthYear', e.target.value)}
                placeholder="예: 1996"
              />
            </div>
            <div>
              <label className={LABEL}>성별</label>
              <div className="rounded-xl border border-stone-200 bg-stone-50 px-4 py-3 text-stone-500 text-sm">
                {form.gender === 'MALE' ? '남성' : '여성'} · 성별은 바꿀 수 없어요
              </div>
            </div>

            <QuestionList
              questions={questions}
              answers={answers}
              onChange={(id, v) => setAnswers((a) => ({ ...a, [id]: v }))}
            />

            <PhotoPicker
              photoUrl={form.photoUrl}
              inputRef={photoInputRef}
              photoStage={photoStage}
              photoNote={photoNote}
              onPick={handlePhoto}
              onRemove={removePhoto}
            />

            {regError && <p className="text-sm text-red-500">{regError}</p>}

            <button
              className={PRIMARY_BTN}
              disabled={saving || uploading}
              onClick={submitEdit}
            >
              {saving ? '저장 중...' : '저장하기'}
            </button>
          </div>
        )}
        </Shell>
      </>
    );
  }

  if (screen === 'blocked' && blocked) {
    return (
      <Shell>
        <div className="rounded-3xl bg-white shadow-sm border border-stone-200 p-8 text-center">
          <div className="text-4xl">{blocked.label === '심사중' ? '⏳' : '🌙'}</div>
          <div className="mt-3 inline-block rounded-full bg-stone-100 px-3 py-1 text-xs font-bold text-stone-600">
            {blocked.label}
          </div>
          <p className="mt-3 text-sm text-stone-600 leading-relaxed whitespace-pre-line">
            {blocked.notice}
          </p>
          <button className={`${SUB_BTN} mt-6`} onClick={openEdit}>
            내 정보 수정
          </button>
          <p className="mt-3 text-xs text-stone-400">
            심사가 끝나면 이 화면에서 바로 프로필을 볼 수 있어요.
          </p>
        </div>
      </Shell>
    );
  }

  if (screen === 'link') {
    return (
      <>
        <PageHeading
          title="기존 프로필 연결"
          description="예전에 신청한 적이 있다면 그때 입력한 이름과 연락처로 확인해 지금 계정에 연결해 드려요."
          back={() => setScreen('landing')}
        />
        <Shell>
        <div className="rounded-2xl bg-white shadow-sm border border-stone-200 p-6">
          <div className="space-y-4">
            <div>
              <label className={LABEL}>이름</label>
              <input
                className={INPUT}
                value={linkName}
                onChange={(e) => setLinkName(e.target.value)}
                placeholder="이름"
              />
            </div>
            <div>
              <label className={LABEL}>연락처</label>
              <input
                className={INPUT}
                value={linkContact}
                onChange={(e) => setLinkContact(e.target.value)}
                placeholder="인스타 ID 또는 폰번호"
              />
            </div>
            {linkError && <p className="text-sm text-red-500">{linkError}</p>}
            <button
              className={PRIMARY_BTN}
              disabled={linking}
              onClick={submitLink}
            >
              {linking ? '확인 중...' : '내 계정에 연결하기'}
            </button>
          </div>
        </div>
        </Shell>
      </>
    );
  }

  // browse
  return (
    <Shell>
      <div className="rounded-2xl bg-damso-50 border border-damso-100 px-4 py-3">
        <p className="text-xs text-damso-700 leading-relaxed">
          하루에 2명까지 좋아요를 보낼 수 있어요. 좋아요를 보내면 상대에게 알림이 가고,
          상대가 좋아요로 답하면 만남 약속을 잡을 수 있어요.
        </p>
        <Link to="/likes" className="mt-1.5 inline-block text-xs font-bold text-damso-600">
          받은 좋아요 보러 가기 →
        </Link>
      </div>

      {likeError && (
        <div className="mb-4 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-600">
          {likeError}
        </div>
      )}

      {browseLoading && (
        <p className="text-center text-stone-400 py-10">불러오는 중...</p>
      )}
      {browseError && (
        <p className="text-center text-red-500 py-6">{browseError}</p>
      )}

      {!browseLoading && !browseError && cards.length === 0 && (
        <div className="rounded-3xl bg-white border border-stone-100 p-10 text-center text-stone-400">
          아직 등록된 이성 프로필이 없어요
        </div>
      )}

      {!browseLoading && !browseError && currentCard && (
        <>
          <div className="flex items-center justify-center mb-3">
            <span className="text-sm font-medium text-stone-400">
              <span className="text-damso-600 font-bold">{safeIndex + 1}</span>
              {` / ${cards.length}`}
            </span>
          </div>

          <ProfileCard
            key={currentCard.id}
            card={currentCard}
            onToggleLike={toggleLike}
            onPass={passCard}
          />

          <div className="mt-4 flex gap-3">
            <button
              className={NAV_BTN}
              disabled={safeIndex === 0}
              onClick={() => moveCard(-1)}
            >
              ← 이전
            </button>
            <button
              className={NAV_BTN}
              disabled={safeIndex >= cards.length - 1}
              onClick={() => moveCard(1)}
            >
              다음 →
            </button>
          </div>

          <p className="mt-3 text-center text-xs text-stone-400">
            {safeIndex >= cards.length - 1
              ? '마지막 프로필이에요'
              : '다음 버튼으로 다른 프로필도 볼 수 있어요'}
          </p>

          {passedCount > 0 && (
            <button
              onClick={restorePassed}
              className="mt-3 w-full rounded-full border border-stone-300 bg-white py-2.5 text-sm font-semibold text-stone-600 active:bg-stone-100"
            >
              관심 없음으로 치운 {passedCount}명 다시 보기
            </button>
          )}
        </>
      )}
    </Shell>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex gap-3 py-1.5 border-b border-stone-100 last:border-b-0">
      <span className="w-24 shrink-0 text-sm font-medium text-stone-500">
        {label}
      </span>
      <span className="flex-1 text-sm text-stone-800 whitespace-pre-wrap break-words">
        {value}
      </span>
    </div>
  );
}

function ProfileCard({
  card,
  onToggleLike,
  onPass,
}: {
  card: DatingProfileCard;
  onToggleLike: (card: DatingProfileCard) => void;
  onPass: (card: DatingProfileCard) => void;
}) {
  return (
    <div className="rounded-3xl bg-white shadow-sm border border-stone-100 overflow-hidden">
      {card.photoUrl ? (
        <img
          src={card.photoUrl}
          alt="프로필 사진"
          className="w-full h-72 object-cover"
        />
      ) : (
        <div className="w-full h-72 bg-stone-100 flex items-center justify-center text-stone-400">
          사진 없음
        </div>
      )}

      <div className="p-5">
        {card.verified && (
          <div className="mb-2">
            <VerifiedBadge />
          </div>
        )}
        <Row label="출생년도" value={card.birthYear} />
        {card.answers.map((a) => (
          <Row key={a.questionId} label={a.label} value={a.value} />
        ))}

        <button
          onClick={() => onToggleLike(card)}
          className={
            'mt-4 w-full rounded-full py-3 font-semibold border transition-colors ' +
            (card.liked
              ? 'bg-red-500 text-white border-red-500'
              : 'bg-white text-stone-600 border-stone-300')
          }
        >
          {card.liked ? '♥ 좋아요 보냄' : '♡ 좋아요 보내기'}
        </button>

        {/* 관심 없음은 상대에게 알리지 않는다. 조용히 내 목록에서만 빠진다. */}
        {!card.liked && (
          <button
            onClick={() => {
              if (window.confirm('이 프로필을 목록에서 치울까요?\n상대에게는 알리지 않아요.')) {
                onPass(card);
              }
            }}
            className="mt-2 w-full rounded-full py-2.5 text-sm font-semibold text-stone-400 active:bg-stone-50"
          >
            관심 없음
          </button>
        )}
      </div>
    </div>
  );
}