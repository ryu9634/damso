/**
 * 개인정보 취급 유틸 (관리자 화면 전용).
 *  - 목록에서는 연락처를 가려 두고, 필요할 때만 펼쳐 본다.
 *  - 등록 후 오래된(보관기간 경과) 프로필을 표시해 정리를 유도한다.
 */

/** 개인정보 보관 기간(개월). 이 기간이 지나면 정리 대상으로 표시한다. */
export const RETENTION_MONTHS = 6;

/** 010-1234-5678 → 010-****-5678. 형식이 다르면 가운데를 통째로 가린다. */
export function maskContact(contact: string): string {
  if (!contact) return '—';
  const digits = contact.replace(/\D/g, '');
  if (digits.length >= 10) {
    const head = digits.slice(0, 3);
    const tail = digits.slice(-4);
    return `${head}-****-${tail}`;
  }
  if (contact.length <= 2) return '*'.repeat(contact.length);
  return `${contact.slice(0, 1)}${'*'.repeat(Math.max(1, contact.length - 2))}${contact.slice(-1)}`;
}

/** 등록 후 경과 일수. */
export function daysSince(iso: string): number {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return 0;
  return Math.floor((Date.now() - then) / 86_400_000);
}

/** 보관기간(RETENTION_MONTHS)이 지났는지. */
export function isRetentionExpired(iso: string): boolean {
  return daysSince(iso) >= RETENTION_MONTHS * 30;
}

/** yyyy-MM-dd */
export function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** yyyy-MM-dd HH:mm */
export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${formatDate(iso)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** 출생년도 → 만 나이(근사, 연도 기준). */
export function ageOf(birthYear: number): number {
  return new Date().getFullYear() - birthYear + 1;
}