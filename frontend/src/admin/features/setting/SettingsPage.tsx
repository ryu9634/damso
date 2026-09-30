import { useEffect, useMemo, useState } from 'react';
import { settingsApi } from '../../api/client';
import type { SettingItem } from '../../../shared/types';
import { PageHeader, Card, Button, Empty } from '../../layout/ui';

/**
 * 환경설정 화면.
 *  - 같은 API(설정 카탈로그)를 쓰지만, only 로 특정 그룹만 떼어 보여줄 수 있다.
 *    (현장 소개팅 운영값 / 앱 홈 화면 설정을 각각 다른 메뉴로 나누기 위함)
 */
export default function SettingsPage({
  title = '환경설정',
  description = '현장 소개팅 운영값을 설정해요. (손님 화면 문구는 기본값으로 자동 적용됩니다)',
  only,
  exclude,
}: {
  title?: string;
  description?: string;
  /** 이 그룹만 보여준다. */
  only?: string[];
  /** 이 그룹은 감춘다. */
  exclude?: string[];
} = {}) {
  const [items, setItems] = useState<SettingItem[]>([]);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const data = await settingsApi.list();
      setItems(data);
      setDraft(Object.fromEntries(data.map((i) => [i.key, i.value])));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const visible = useMemo(
    () =>
      items.filter(
        (i) =>
          (!only || only.includes(i.group)) &&
          (!exclude || !exclude.includes(i.group)),
      ),
    [items, only, exclude],
  );

  const dirty = useMemo(
    () => visible.some((i) => (draft[i.key] ?? '') !== i.value),
    [visible, draft],
  );

  const groups = useMemo(() => {
    const g = new Map<string, SettingItem[]>();
    for (const i of visible) {
      if (!g.has(i.group)) g.set(i.group, []);
      g.get(i.group)!.push(i);
    }
    return Array.from(g.entries());
  }, [visible]);

  const save = async () => {
    setSaving(true); setMsg(null);
    try {
      // 화면에 보이는 항목만 저장한다. (다른 메뉴의 값은 건드리지 않음)
      const payload = Object.fromEntries(visible.map((i) => [i.key, draft[i.key] ?? '']));
      const data = await settingsApi.update(payload);
      setItems(data);
      setDraft(Object.fromEntries(data.map((i) => [i.key, i.value])));
      setMsg('저장되었습니다.');
    } catch {
      setMsg('저장에 실패했습니다.');
    } finally {
      setSaving(false);
      setTimeout(() => setMsg(null), 2500);
    }
  };

  const resetAll = async () => {
    if (!confirm('모든 설정을 기본값으로 되돌릴까요?')) return;
    const data = await settingsApi.resetDefault();
    setItems(data);
    setDraft(Object.fromEntries(data.map((i) => [i.key, i.value])));
    setMsg('기본값으로 초기화했습니다.');
    setTimeout(() => setMsg(null), 2500);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={title}
        description={description}
        actions={
          <>
            <Button variant="ghost" onClick={resetAll}>기본값 복원</Button>
            <Button variant="primary" onClick={save} disabled={!dirty || saving}>
              {saving ? '저장 중...' : '저장'}
            </Button>
          </>
        }
      />

      {msg && (
        <div className="text-sm text-damso-700 bg-damso-50 border border-damso-200 rounded-md px-4 py-2">
          {msg}
        </div>
      )}

      {loading ? (
        <Empty>불러오는 중...</Empty>
      ) : groups.length === 0 ? (
        <Empty>설정 항목이 없습니다.</Empty>
      ) : (
        groups.map(([group, fields]) => (
          <Card key={group} title={group}>
            <div className="space-y-4">
              {fields.map((f) => (
                <label key={f.key} className="block">
                  <div className="text-sm font-medium text-stone-700 mb-1">{f.label}</div>
                  {f.type === 'TEXTAREA' ? (
                    <textarea
                      value={draft[f.key] ?? ''}
                      onChange={(e) => setDraft((d) => ({ ...d, [f.key]: e.target.value }))}
                      rows={2}
                      className="w-full border border-stone-300 rounded-md px-3 py-2 text-sm resize-y focus:outline-none focus:ring-2 focus:ring-damso-300"
                    />
                  ) : f.type === 'SELECT' ? (
                    <select
                      value={draft[f.key] ?? ''}
                      onChange={(e) => setDraft((d) => ({ ...d, [f.key]: e.target.value }))}
                      className="border border-stone-300 rounded-md bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-damso-300"
                    >
                      {f.options.map((opt) => (
                        <option key={opt} value={opt}>{opt}</option>
                      ))}
                    </select>
                  ) : (
                    <input
                      type={f.type === 'NUMBER' ? 'number' : 'text'}
                      min={f.type === 'NUMBER' ? 1 : undefined}
                      value={draft[f.key] ?? ''}
                      onChange={(e) => setDraft((d) => ({ ...d, [f.key]: e.target.value }))}
                      className={`border border-stone-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-damso-300 ${
                        f.type === 'NUMBER' ? 'w-28' : 'w-full'
                      }`}
                    />
                  )}
                </label>
              ))}
            </div>
          </Card>
        ))
      )}
    </div>
  );
}
