import { useEffect, useState } from 'react';
import { adminAuthApi, setAdminToken, type AdminAccount } from '../api/client';
import { PageHeader, Card, Button, Input, Field } from '../layout/ui';
import { formatDateTime } from '../../shared/utils/privacy';

/** 관리자 계정 — 비밀번호 변경. */
export default function AdminAccountPage() {
  const [me, setMe] = useState<AdminAccount | null>(null);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [next2, setNext2] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    adminAuthApi.me().then(setMe).catch(() => setMe(null));
  }, []);

  const valid = current.length > 0 && next.length >= 4 && next === next2;

  const submit = async () => {
    setBusy(true);
    setError('');
    setMsg('');
    try {
      await adminAuthApi.changePassword(current, next);
      // 비밀번호를 바꾸면 서버가 기존 세션을 끊으므로 다시 로그인해야 한다.
      setAdminToken(null);
      setMsg('비밀번호를 변경했습니다. 다시 로그인해주세요.');
      setTimeout(() => window.location.assign('/admin/login'), 1200);
    } catch (e: any) {
      setError(e?.response?.data?.message ?? '변경하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="관리자 계정"
        description="관리자 화면에 로그인할 때 쓰는 계정이에요."
      />

      <Card title="계정 정보">
        <div className="text-sm text-stone-700">
          아이디 <span className="font-semibold">{me?.username ?? '—'}</span>
        </div>
        <div className="mt-1 text-sm text-stone-500">
          최근 로그인 {me?.lastLoginAt ? formatDateTime(me.lastLoginAt) : '—'}
        </div>
        {me?.initialPassword && (
          <div className="mt-3 rounded-md bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-800">
            기본 비밀번호(admin)를 그대로 쓰고 있어요. 아래에서 꼭 바꿔주세요.
          </div>
        )}
      </Card>

      <Card title="비밀번호 변경">
        <div className="space-y-4 max-w-sm">
          <Field label="현재 비밀번호">
            <Input
              type="password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
          </Field>
          <Field label="새 비밀번호" hint="4자 이상">
            <Input type="password" value={next} onChange={(e) => setNext(e.target.value)} />
          </Field>
          <Field label="새 비밀번호 확인">
            <Input type="password" value={next2} onChange={(e) => setNext2(e.target.value)} />
          </Field>

          {next2.length > 0 && next !== next2 && (
            <div className="text-sm text-red-600">새 비밀번호가 서로 다릅니다.</div>
          )}
          {error && <div className="text-sm text-red-600">{error}</div>}
          {msg && <div className="text-sm text-damso-700">{msg}</div>}

          <Button variant="primary" disabled={!valid || busy} onClick={submit}>
            {busy ? '변경 중...' : '비밀번호 변경'}
          </Button>
        </div>
      </Card>
    </div>
  );
}
