import { createContext, useContext } from 'react';
import type { Event } from '../../shared/types';

export interface EventContextValue {
  /** 전체 이벤트 (최근 순) */
  events: Event[];
  /** 현재 선택된 이벤트 */
  event: Event | null;
  /** 선택 이벤트 변경 */
  setEvent: (e: Event) => void;
  /** 목록과 현재 이벤트를 서버에서 다시 로드 */
  refresh: () => Promise<void>;
}

export const EventContext = createContext<EventContextValue | null>(null);

export function useEvent(): EventContextValue {
  const ctx = useContext(EventContext);
  if (!ctx) {
    throw new Error('useEvent는 EventProvider 안에서만 사용할 수 있습니다.');
  }
  return ctx;
}
