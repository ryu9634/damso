import { useCallback, useEffect, useState } from 'react';
import { eventApi } from '../api/client';
import type { Event } from '../../shared/types';
import { EventContext } from './useEvent';

const STORAGE_KEY = 'damso.selectedEventId';

export function EventProvider({ children }: { children: React.ReactNode }) {
  const [events, setEvents] = useState<Event[]>([]);
  const [event, setEventState] = useState<Event | null>(null);

  const refresh = useCallback(async () => {
    const list = await eventApi.list();
    if (list.length === 0) {
      const created = await eventApi.current();
      setEvents([created]);
      setEventState(created);
      return;
    }
    setEvents(list);

    const storedId = Number(localStorage.getItem(STORAGE_KEY));
    const found = list.find((e) => e.id === storedId);
    if (found) {
      setEventState(found);
    } else {
      const fallback = list[0];
      setEventState(fallback);
      localStorage.setItem(STORAGE_KEY, String(fallback.id));
    }
  }, []);

  const setEvent = useCallback((e: Event) => {
    setEventState(e);
    localStorage.setItem(STORAGE_KEY, String(e.id));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <EventContext.Provider value={{ events, event, setEvent, refresh }}>
      {children}
    </EventContext.Provider>
  );
}
