import { useState } from 'react';
import { guestApi } from './guest';
import { formatPhone } from '../../../shared/utils/phone';
import type { Customer } from '../../../shared/types';

export default function ReloginPage({ onLogin }: { onLogin: (c: Customer) => void }) {
  const [realName, setRealName] = useState('');
  const [phone, setPhone] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!realName.trim() || !phone.trim()) {
      setErr('성함과 연락처를 모두 입력해주세요.');
      return;
    }
    setSubmitting(true); setErr(null);
    try {
      const c = await guestApi.login(realName.trim(), phone.trim());
      onLogin(c);
    } catch (e: any) {
      if (e?.response?.status === 404) {
        setErr('일치하는 정보를 찾을 수 없어요. 다시 확인해주세요.');
      } else {
        setErr('로그인에 실패했어요. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 pt-6">
      <div className="text-center">
        <div className="text-3xl">🔑</div>
        <h1 className="text-lg font-bold mt-2">다시 입장하기</h1>
        <p className="text-sm text-stone-500 mt-1">
          등록 시 입력한 성함과 연락처를 입력해주세요
        </p>
      </div>

      <div className="space-y-3">
        <div>
          <label className="text-xs font-medium text-stone-600">본인 성함</label>
          <input
            value={realName}
            onChange={(e) => setRealName(e.target.value)}
            placeholder="실명"
            className="mt-1 w-full border border-stone-300 rounded-md px-3 py-3 text-base"
          />
        </div>
        <div>
          <label className="text-xs font-medium text-stone-600">연락처</label>
          <input
            value={phone}
            onChange={(e) => setPhone(formatPhone(e.target.value))}
            placeholder="010-0000-0000"
            inputMode="tel"
            className="mt-1 w-full border border-stone-300 rounded-md px-3 py-3 text-base"
          />
        </div>
      </div>

      {err && <div className="text-sm text-red-600 text-center">{err}</div>}

      <button
        onClick={submit}
        disabled={submitting}
        className="w-full py-4 rounded-full text-white font-bold text-base bg-damso-500 active:bg-damso-600 disabled:bg-stone-300 shadow-lg"
      >
        {submitting ? '확인 중...' : '입장하기'}
      </button>

      <p className="text-xs text-stone-500 text-center">
        정보를 모르시면 직원에게 문의해주세요
      </p>
    </div>
  );
}
