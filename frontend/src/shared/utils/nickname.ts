/** 닉네임 길이에 맞춰 좁은 셀에서도 가독성 있게 보이도록 폰트 클래스 반환. */
export function nameSizeClass(name: string): string {
  const len = name.length;
  if (len <= 2) return 'text-[15px]';
  if (len === 3) return 'text-[12px]';
  if (len === 4) return 'text-[10.5px]';
  if (len === 5) return 'text-[9px]';
  return 'text-[8px]';
}