import { useEffect, useState } from 'react';
import { guestApi } from './guest';
import { formatPhone } from '../../../shared/utils/phone';
import { useAppAuth } from '../auth/useAppAuth';
import type { Customer, Gender, ValueQuestion } from '../../../shared/types';

const MBTI_LIST = [
  'INTJ', 'INTP', 'ENTJ', 'ENTP',
  'INFJ', 'INFP', 'ENFJ', 'ENFP',
  'ISTJ', 'ISFJ', 'ESTJ', 'ESFJ',
  'ISTP', 'ISFP', 'ESTP', 'ESFP',
];

export default function RegistrationPage({ onRegistered }: { onRegistered: (c: Customer) => void }) {
  const { user } = useAppAuth();
  const [questions, setQuestions] = useState<ValueQuestion[]>([]);
  const [form, setForm] = useState({
    // 회원가입 때 받은 정보는 미리 채워둔다. (닉네임은 최대 5자라 잘라서 넣는다)
    nickname: (user?.nickname ?? '').slice(0, 5),
    realName: user?.name ?? '',
    phone: formatPhone(user?.phone ?? ''),
    gender: '' as Gender | '',
    mbti: '',
    seatNumber: 0,
  });
  const [answers, setAnswers] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    guestApi.questions().then((qs) => {
      setQuestions(qs);
      setAnswers(qs.map(() => ''));
    });
  }, []);

  const allFilled =
    form.nickname.trim()
    && form.realName.trim()
    && form.phone.trim()
    && form.gender
    && form.mbti
    && form.seatNumber >= 1 && form.seatNumber <= 9
    && answers.length > 0
    && answers.every((a) => a === '0' || a === '1');

  const submit = async () => {
    if (!allFilled) {
      setErr('모든 항목을 입력해주세요.');
      return;
    }
    setSubmitting(true); setErr(null);
    try {
      const c = await guestApi.register({
        nickname: form.nickname.trim(),
        realName: form.realName.trim(),
        phone: form.phone.trim(),
        gender: form.gender as Gender,
        mbti: form.mbti,
        valuesAnswers: answers.join(''),
        seatNumber: form.seatNumber,
      });
      onRegistered(c);
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '등록에 실패했어요. 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* 페이지 제목은 상단(GuestShell)에서 이미 보여주므로 여기선 안내 한 줄만 둔다 */}
      <p className="rounded-xl bg-damso-50 border border-damso-100 px-4 py-2.5 text-xs text-damso-700">
        {user
          ? '회원 정보를 채워뒀어요. 나머지만 입력하면 입장 등록이 끝나요.'
          : '아래 정보를 입력하면 입장 등록이 끝나요.'}
      </p>

      <Section title="기본 정보">
        <Input label="닉네임 (최대 5자)" placeholder="다른 손님에게 보일 이름"
          maxLength={5}
          value={form.nickname} onChange={(v) => setForm({ ...form, nickname: v.slice(0, 5) })} />
        <Input label="본인 성함" placeholder="실명 (외부 비공개)"
          value={form.realName} onChange={(v) => setForm({ ...form, realName: v })} />
        <Input label="연락처" placeholder="010-0000-0000" inputMode="tel"
          value={form.phone} onChange={(v) => setForm({ ...form, phone: formatPhone(v) })} />

        <div>
          <Label>자리 번호</Label>
          <div className="grid grid-cols-9 gap-1.5 mt-2">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setForm({ ...form, seatNumber: n })}
                className={`py-3 rounded-md text-base font-bold transition ${
                  form.seatNumber === n
                    ? 'bg-damso-500 text-white'
                    : 'bg-stone-100 text-stone-700 active:bg-stone-200'
                }`}
              >
                {n}
              </button>
            ))}
          </div>
          <div className="text-[11px] text-stone-500 mt-1.5">
            매장에서 안내받은 자리 번호를 선택해주세요
          </div>
        </div>

        <div>
          <Label>성별</Label>
          <div className="grid grid-cols-2 gap-2 mt-2">
            <GenderBtn current={form.gender} value="MALE" label="남성"
              onClick={() => setForm({ ...form, gender: 'MALE' })} />
            <GenderBtn current={form.gender} value="FEMALE" label="여성"
              onClick={() => setForm({ ...form, gender: 'FEMALE' })} />
          </div>
        </div>

        <div>
          <Label>MBTI</Label>
          <div className="grid grid-cols-4 gap-1.5 mt-2">
            {MBTI_LIST.map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setForm({ ...form, mbti: m })}
                className={`py-2 rounded-md text-xs font-semibold transition ${
                  form.mbti === m
                    ? 'bg-damso-500 text-white'
                    : 'bg-stone-100 text-stone-700 active:bg-stone-200'
                }`}
              >
                {m}
              </button>
            ))}
          </div>
        </div>
      </Section>

      <Section title={`가치관 ${questions.length}문항`} subtitle="더 잘 맞는 상대를 찾기 위한 질문이에요">
        {questions.length === 0 ? (
          <div className="text-sm text-stone-500 text-center py-4">문항 불러오는 중...</div>
        ) : (
          <div className="space-y-3">
            {questions.map((q, i) => (
              <div key={q.id} className="bg-white border border-stone-200 rounded-lg p-3">
                <div className="text-xs text-stone-500 mb-1.5">{i + 1} / {questions.length}</div>
                <div className="text-sm font-semibold text-stone-900 mb-2.5">{q.label}</div>
                <div className="space-y-1.5">
                  {[q.optionA, q.optionB].map((opt, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => {
                        const next = [...answers];
                        next[i] = String(idx);
                        setAnswers(next);
                      }}
                      className={`w-full text-left px-3 py-2.5 rounded-md text-sm transition ${
                        answers[i] === String(idx)
                          ? 'bg-damso-100 border-2 border-damso-500 text-damso-800 font-medium'
                          : 'bg-stone-50 border-2 border-transparent text-stone-700 active:bg-stone-100'
                      }`}
                    >
                      {opt}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </Section>

      {err && <div className="text-sm text-red-600 text-center">{err}</div>}

      <div className="sticky bottom-3 pt-2">
        <button
          onClick={submit}
          disabled={!allFilled || submitting}
          className="w-full py-4 rounded-full text-white font-bold text-base bg-damso-500 active:bg-damso-600 disabled:bg-stone-300 shadow-lg"
        >
          {submitting ? '등록 중...' : allFilled ? '입장하기' : '모든 항목 입력 필요'}
        </button>
      </div>
    </div>
  );
}

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-sm font-bold text-stone-900">{title}</h2>
        {subtitle && <p className="text-xs text-stone-500 mt-0.5">{subtitle}</p>}
      </div>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Label({ children }: { children: React.ReactNode }) {
  return <label className="text-xs font-medium text-stone-600">{children}</label>;
}

function Input({
  label, value, onChange, placeholder, inputMode, maxLength,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  inputMode?: 'text' | 'tel' | 'email';
  maxLength?: number;
}) {
  return (
    <div>
      <Label>{label}</Label>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        inputMode={inputMode}
        maxLength={maxLength}
        className="mt-1 w-full border border-stone-300 rounded-md px-3 py-3 text-base"
      />
    </div>
  );
}

function GenderBtn({
  current, value, label, onClick,
}: { current: string; value: Gender; label: string; onClick: () => void }) {
  const selected = current === value;
  const tone = value === 'MALE' ? 'damso' : 'rose';
  const cls = selected
    ? value === 'MALE' ? 'bg-blue-500 text-white border-blue-500'
                       : 'bg-pink-500 text-white border-pink-500'
    : 'bg-white text-stone-700 border-stone-300 active:bg-stone-50';
  return (
    <button
      type="button"
      onClick={onClick}
      data-tone={tone}
      className={`py-3 border-2 rounded-md font-medium transition ${cls}`}
    >
      {label}
    </button>
  );
}
