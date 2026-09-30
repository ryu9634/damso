import { useEffect, useMemo, useState } from 'react';
import { adminAppApi, type AdminUserProfileInput, type TempPassword } from '../../api/client';
import type { AdminAppUser } from '../../../shared/types';
import {
  PageHeader, Card, Button, Empty, Badge, Input, Field, Modal,
} from '../../layout/ui';
import { formatDate, formatDateTime } from '../../../shared/utils/privacy';
import { formatPhone } from '../../../shared/utils/phone';

/**
 * 앱 회원(계정) 관리.
 *  - 가입 현황과 푸시 수신 동의 여부를 보고, 손님이 문의해 오면 여기서 정보를 고쳐준다.
 *  - 비밀번호는 해시로만 저장돼 있어 확인해 줄 수 없다. 대신 임시 비밀번호를 발급한다.
 */

/** 손님 마이페이지와 같은 규칙 (서버 AuthRequests 와 맞춰 둔다). */
const NICKNAME_RE = /^\S{2,12}$/;
const PHONE_RE = /^01[0-9]{8,9}$/;
const MIN_AGE = 18;

export default function AppMembersPage() {
  const [users, setUsers] = useState<AdminAppUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState<AdminAppUser | null>(null);
  // 초기화 결과 — 이 화면을 닫으면 임시 비밀번호를 다시 볼 수 없다.
  const [issued, setIssued] = useState<{ user: AdminAppUser; temp: TempPassword } | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      setUsers(await adminAppApi.users());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const notify = (text: string) => {
    setMsg(text);
    setTimeout(() => setMsg(null), 2500);
  };

  const fail = (e: any, fallback: string) => {
    setError(e?.response?.data?.message || fallback);
    setTimeout(() => setError(null), 5000);
  };

  const filtered = useMemo(() => {
    const k = keyword.trim();
    if (!k) return users;
    const digits = k.replace(/\D/g, '');
    return users.filter(
      (u) =>
        u.name.includes(k) ||
        (u.nickname ?? '').includes(k) ||
        (u.loginId ?? '').includes(k.toLowerCase()) ||
        (digits.length > 0 && (u.phone ?? '').includes(digits)),
    );
  }, [users, keyword]);

  const pushCount = users.filter((u) => u.pushOptIn).length;

  const save = async (input: AdminUserProfileInput) => {
    if (!editing) return;
    try {
      await adminAppApi.updateUser(editing.id, input);
      await load();
      setEditing(null);
      notify('회원 정보를 수정했습니다.');
    } catch (e) {
      fail(e, '수정하지 못했습니다.');
    }
  };

  const resetPassword = async (u: AdminAppUser) => {
    if (
      !confirm(
        `${u.name} 회원의 비밀번호를 초기화할까요?\n` +
          '임시 비밀번호가 새로 발급되고, 지금 로그인돼 있는 기기는 모두 로그아웃됩니다.',
      )
    ) return;
    try {
      const temp = await adminAppApi.resetPassword(u.id);
      setIssued({ user: u, temp });
      await load();
    } catch (e) {
      fail(e, '초기화하지 못했습니다.');
    }
  };

  const remove = async (u: AdminAppUser) => {
    if (!confirm(`${u.name} 회원을 탈퇴 처리할까요?`)) return;
    try {
      await adminAppApi.removeUser(u.id);
      await load();
      notify('탈퇴 처리했습니다.');
    } catch (e) {
      fail(e, '처리하지 못했습니다.');
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="앱 회원"
        description="앱(홈·방명록·마이페이지)에 가입한 회원 목록이에요. 문의가 오면 정보 수정과 비밀번호 초기화를 여기서 해줄 수 있어요."
        actions={<Button onClick={load}>새로고침</Button>}
      />

      {msg && (
        <div className="text-sm text-damso-700 bg-damso-50 border border-damso-200 rounded-md px-4 py-2">
          {msg}
        </div>
      )}
      {error && (
        <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-4 py-2">
          {error}
        </div>
      )}

      <Card
        title={`전체 ${users.length}명 · 푸시 동의 ${pushCount}명`}
        actions={
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="이름 · 번호 검색"
            className="w-48"
          />
        }
      >
        {loading ? (
          <Empty>불러오는 중...</Empty>
        ) : filtered.length === 0 ? (
          <Empty>가입한 회원이 없습니다.</Empty>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-stone-500 border-b border-stone-200">
                  <th className="py-2 pr-3">아이디</th>
                  <th className="py-2 pr-3">닉네임</th>
                  <th className="py-2 pr-3">성명</th>
                  <th className="py-2 pr-3">생년월일</th>
                  <th className="py-2 pr-3">휴대폰</th>
                  <th className="py-2 pr-3">로그인 수단</th>
                  <th className="py-2 pr-3">푸시</th>
                  <th className="py-2 pr-3">가입일</th>
                  <th className="py-2 pr-3">최근 로그인</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {filtered.map((u) => (
                  <tr key={u.id} className="border-b border-stone-100 whitespace-nowrap">
                    <td className="py-2 pr-3 text-stone-600">
                      {u.loginId ?? '—'}
                      {u.passwordResetAt && (
                        <Badge className="ml-2 bg-sky-100 text-sky-700">임시 비밀번호</Badge>
                      )}
                    </td>
                    <td className="py-2 pr-3 font-semibold text-stone-800">
                      {u.nickname ?? '—'}
                    </td>
                    <td className="py-2 pr-3 text-stone-700">
                      {u.name}
                      {!u.profileComplete && (
                        <Badge className="ml-2 bg-amber-100 text-amber-800">정보 미완료</Badge>
                      )}
                    </td>
                    <td className="py-2 pr-3 text-stone-600">
                      {u.birthDate ? `${u.birthDate} (만 ${u.age}세)` : '—'}
                    </td>
                    <td className="py-2 pr-3 text-stone-600">
                      {u.phone ? formatPhone(u.phone) : '—'}
                    </td>
                    <td className="py-2 pr-3 text-stone-600">
                      {u.loginMethods.length > 0 ? u.loginMethods.join(' · ') : '—'}
                    </td>
                    <td className="py-2 pr-3">
                      {u.pushOptIn ? (
                        <Badge className="bg-emerald-100 text-emerald-800">동의</Badge>
                      ) : (
                        <Badge>미동의</Badge>
                      )}
                    </td>
                    <td className="py-2 pr-3 text-stone-500">{formatDate(u.createdAt)}</td>
                    <td className="py-2 pr-3 text-stone-500">
                      {u.lastLoginAt ? formatDateTime(u.lastLoginAt) : '—'}
                    </td>
                    <td className="py-2">
                      <div className="flex justify-end gap-1.5 whitespace-nowrap">
                        <Button size="sm" onClick={() => setEditing(u)}>수정</Button>
                        <Button
                          size="sm"
                          onClick={() => resetPassword(u)}
                          disabled={!u.loginId}
                          title={u.loginId ? undefined : '아이디가 없는 간편로그인 전용 계정이에요.'}
                        >
                          비밀번호 초기화
                        </Button>
                        <Button variant="danger" size="sm" onClick={() => remove(u)}>
                          탈퇴 처리
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <EditModal user={editing} onClose={() => setEditing(null)} onSave={save} />
      <TempPasswordModal issued={issued} onClose={() => setIssued(null)} />
    </div>
  );
}

/** 회원 정보 수정. 검증 규칙은 손님 마이페이지와 같다. */
function EditModal({
  user, onClose, onSave,
}: {
  user: AdminAppUser | null;
  onClose: () => void;
  onSave: (input: AdminUserProfileInput) => void | Promise<void>;
}) {
  const [nickname, setNickname] = useState('');
  const [name, setName] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [phone, setPhone] = useState('');

  useEffect(() => {
    if (!user) return;
    setNickname(user.nickname ?? '');
    setName(user.name);
    setBirthDate(user.birthDate ?? '');
    setPhone(formatPhone(user.phone ?? ''));
  }, [user]);

  const digits = phone.replace(/\D/g, '');
  const age = birthDate ? ageOf(birthDate) : null;
  const underAge = age !== null && age < MIN_AGE;
  const valid =
    NICKNAME_RE.test(nickname) && name.trim().length > 0 &&
    birthDate.length > 0 && !underAge && PHONE_RE.test(digits);

  return (
    <Modal
      open={user !== null}
      title={`회원 정보 수정${user ? ` — ${user.name}` : ''}`}
      onClose={onClose}
      width="max-w-lg"
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button
            variant="primary"
            disabled={!valid}
            onClick={() =>
              onSave({ nickname: nickname.trim(), name: name.trim(), birthDate, phone: digits })
            }
          >
            저장
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="닉네임" hint="공백 없이 2~12자. 다른 회원과 겹칠 수 없어요.">
          <Input
            value={nickname}
            onChange={(e) => setNickname(e.target.value.replace(/\s/g, ''))}
          />
          {nickname.length > 0 && !NICKNAME_RE.test(nickname) && (
            <span className="block text-[11px] text-red-600 mt-1">
              공백 없이 2~12자로 지어주세요.
            </span>
          )}
        </Field>

        <Field label="성명">
          <Input value={name} maxLength={30} onChange={(e) => setName(e.target.value)} />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="생년월일" hint={`만 ${MIN_AGE}세 이상만 이용할 수 있어요.`}>
            <Input
              type="date"
              value={birthDate}
              max={new Date().toISOString().slice(0, 10)}
              onChange={(e) => setBirthDate(e.target.value)}
            />
            {underAge && (
              <span className="block text-[11px] text-red-600 mt-1">
                만 {MIN_AGE}세 미만이에요.
              </span>
            )}
          </Field>
          <Field label="휴대폰번호">
            <Input
              value={phone}
              inputMode="numeric"
              placeholder="010-0000-0000"
              onChange={(e) => setPhone(formatPhone(e.target.value))}
            />
            {phone.length > 0 && !PHONE_RE.test(digits) && (
              <span className="block text-[11px] text-red-600 mt-1">
                번호를 정확히 입력해주세요.
              </span>
            )}
          </Field>
        </div>

        <p className="text-xs text-stone-500">
          아이디와 비밀번호는 여기서 바꾸지 않아요. 비밀번호를 잊었다면 목록의
          '비밀번호 초기화'로 임시 비밀번호를 발급해주세요.
        </p>
      </div>
    </Modal>
  );
}

/** 초기화된 임시 비밀번호. 늘 같은 값이라 손님에게 말로 알려주면 된다. */
function TempPasswordModal({
  issued, onClose,
}: {
  issued: { user: AdminAppUser; temp: TempPassword } | null;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);

  useEffect(() => { setCopied(false); }, [issued]);

  const copy = async () => {
    if (!issued) return;
    try {
      await navigator.clipboard.writeText(issued.temp.password);
      setCopied(true);
    } catch {
      // 클립보드를 못 쓰는 브라우저 — 화면의 값을 직접 읽어 전달하면 된다.
    }
  };

  return (
    <Modal
      open={issued !== null}
      title="임시 비밀번호 발급"
      onClose={onClose}
      width="max-w-md"
      footer={<Button variant="primary" onClick={onClose}>확인했어요</Button>}
    >
      {issued && (
        <div className="space-y-4">
          <div className="rounded-md bg-amber-50 border border-amber-200 px-4 py-2.5 text-sm text-amber-800">
            비밀번호가 아래 값으로 바뀌었어요. 손님에게 알려주세요.
          </div>

          <div className="rounded-lg border border-stone-200 bg-stone-50 p-4 space-y-2">
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs font-semibold text-stone-500">아이디</span>
              <span className="text-sm font-bold text-stone-900">{issued.temp.loginId}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs font-semibold text-stone-500">임시 비밀번호</span>
              <span className="text-lg font-bold tracking-wider text-stone-900 select-all">
                {issued.temp.password}
              </span>
            </div>
          </div>

          <Button onClick={copy}>{copied ? '복사했어요' : '비밀번호 복사'}</Button>

          <p className="text-xs text-stone-500 leading-relaxed">
            {issued.user.name} 회원이 이 비밀번호로 로그인한 뒤, 마이페이지 →
            '비밀번호 변경'에서 새 비밀번호로 바꾸도록 안내해주세요.
            초기화하면서 기존에 로그인돼 있던 기기는 모두 로그아웃됐어요.
          </p>
        </div>
      )}
    </Modal>
  );
}

/** 만 나이 (생일이 지났는지까지 반영). */
function ageOf(birthDate: string): number | null {
  const d = new Date(birthDate);
  if (Number.isNaN(d.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - d.getFullYear();
  const beforeBirthday =
    today.getMonth() < d.getMonth() ||
    (today.getMonth() === d.getMonth() && today.getDate() < d.getDate());
  if (beforeBirthday) age -= 1;
  return age;
}
