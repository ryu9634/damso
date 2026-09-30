import { useEffect, useRef, useState } from 'react';
import type { DrinkItem } from '../../../shared/types';
import { drinkApi, type DrinkItemInput } from '../../api/client';
import { PageHeader, Card, Button, Empty, Input, Textarea, Field, Badge } from '../../layout/ui';

/**
 * 앱 홈에 보여줄 술 · 칵테일 메뉴 관리.
 *  - 이모지만 골라도 되고, 사진을 올리면 사진이 우선 보인다.
 *  - '오늘의 추천'은 한 잔만 켤 수 있고, 켜면 홈 맨 위 카드에 크게 나온다.
 */

/** 고르기 쉬우라고 미리 담아둔 술 이모지. 직접 입력해도 된다. */
const EMOJI_PALETTE = [
  '🍸', '🍹', '🍷', '🍺', '🍻', '🥂', '🥃', '🍶',
  '🍾', '🧉', '🧊', '🍋', '🍒', '🫗', '☕', '🥤',
];

const EMPTY: DrinkItemInput = {
  emoji: '🍸',
  name: '',
  description: null,
  imageUrl: null,
  visible: true,
  recommended: false,
};

export default function DrinksPage() {
  const [items, setItems] = useState<DrinkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setItems(await drinkApi.list());
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
    setTimeout(() => setError(null), 4000);
  };

  const save = async (id: number, input: DrinkItemInput) => {
    try {
      await drinkApi.update(id, input);
      await load();
      notify('저장했습니다.');
    } catch (e) {
      fail(e, '저장하지 못했습니다.');
    }
  };

  const create = async (input: DrinkItemInput) => {
    try {
      await drinkApi.create(input);
      await load();
      setAdding(false);
      notify('메뉴를 추가했습니다.');
    } catch (e) {
      fail(e, '추가하지 못했습니다.');
    }
  };

  const remove = async (item: DrinkItem) => {
    if (!confirm(`'${item.name}' 메뉴를 지울까요?`)) return;
    try {
      await drinkApi.remove(item.id);
      await load();
      notify('메뉴를 지웠습니다.');
    } catch (e) {
      fail(e, '지우지 못했습니다.');
    }
  };

  /** 위/아래 이동 — 화면 순서를 그대로 서버에 저장한다. */
  const move = async (index: number, step: -1 | 1) => {
    const next = index + step;
    if (next < 0 || next >= items.length) return;
    const reordered = [...items];
    const [moved] = reordered.splice(index, 1);
    reordered.splice(next, 0, moved);
    setItems(reordered);
    try {
      setItems(await drinkApi.reorder(reordered.map((d) => d.id)));
    } catch (e) {
      await load();
      fail(e, '순서를 바꾸지 못했습니다.');
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="술 메뉴"
        description="손님 앱 홈에 보이는 술·칵테일 목록이에요. 이모지나 사진으로 꾸밀 수 있어요."
        actions={
          <Button variant="primary" onClick={() => setAdding((v) => !v)}>
            {adding ? '추가 닫기' : '+ 메뉴 추가'}
          </Button>
        }
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

      {adding && (
        <Card title="새 메뉴">
          <DrinkForm
            initial={EMPTY}
            submitLabel="추가"
            onSubmit={create}
            onCancel={() => setAdding(false)}
          />
        </Card>
      )}

      {loading ? (
        <Empty>불러오는 중...</Empty>
      ) : items.length === 0 ? (
        <Empty>등록된 메뉴가 없습니다. 오른쪽 위 '메뉴 추가'로 시작해보세요.</Empty>
      ) : (
        <div className="space-y-4">
          {items.map((item, i) => (
            <Card
              key={item.id}
              title={
                <span className="flex items-center gap-2">
                  <span className="text-lg">{item.emoji || '🍸'}</span>
                  <span>{item.name}</span>
                  {item.recommended && (
                    <Badge className="bg-damso-100 text-damso-700">오늘의 추천</Badge>
                  )}
                  {!item.visible && <Badge>숨김</Badge>}
                </span>
              }
              actions={
                <>
                  <Button size="sm" onClick={() => move(i, -1)} disabled={i === 0}>↑</Button>
                  <Button size="sm" onClick={() => move(i, 1)} disabled={i === items.length - 1}>↓</Button>
                  <Button size="sm" variant="danger" onClick={() => remove(item)}>삭제</Button>
                </>
              }
            >
              <DrinkForm
                initial={{
                  emoji: item.emoji,
                  name: item.name,
                  description: item.description,
                  imageUrl: item.imageUrl,
                  visible: item.visible,
                  recommended: item.recommended,
                }}
                submitLabel="저장"
                onSubmit={(input) => save(item.id, input)}
              />
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

/** 메뉴 한 건의 입력 폼. (추가·수정 공용) */
function DrinkForm({
  initial, submitLabel, onSubmit, onCancel,
}: {
  initial: DrinkItemInput;
  submitLabel: string;
  onSubmit: (input: DrinkItemInput) => void | Promise<void>;
  onCancel?: () => void;
}) {
  const [form, setForm] = useState<DrinkItemInput>(initial);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const fileRef = useRef<HTMLInputElement | null>(null);

  // 목록을 다시 불러오면 바깥 값이 바뀌므로 폼도 맞춰준다.
  useEffect(() => { setForm(initial); }, [
    initial.emoji, initial.name, initial.description,
    initial.imageUrl, initial.visible, initial.recommended,
  ]);

  const set = <K extends keyof DrinkItemInput>(key: K, value: DrinkItemInput[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const pickImage = async (file: File | undefined) => {
    if (fileRef.current) fileRef.current.value = '';
    if (!file) return;
    setUploadError('');
    setUploading(true);
    try {
      set('imageUrl', await drinkApi.uploadImage(file));
    } catch (e: any) {
      setUploadError(e?.response?.data?.message || '사진을 올리지 못했습니다.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-[auto,1fr]">
        {/* 미리보기 */}
        <div className="w-28">
          {form.imageUrl ? (
            <img
              src={form.imageUrl}
              alt="미리보기"
              className="w-28 h-28 rounded-xl object-cover bg-stone-100 border border-stone-200"
            />
          ) : (
            <div className="w-28 h-28 rounded-xl border border-stone-200 bg-gradient-to-br from-damso-50 to-amber-50 flex items-center justify-center text-4xl">
              {form.emoji || '🍸'}
            </div>
          )}
        </div>

        <div className="space-y-3">
          <Field label="이름">
            <Input
              value={form.name}
              maxLength={40}
              placeholder="예: 담소 하이볼"
              onChange={(e) => set('name', e.target.value)}
            />
          </Field>
          <Field label="설명" hint="홈 카드에 작게 보여요. 비워둬도 됩니다.">
            <Textarea
              rows={2}
              maxLength={200}
              value={form.description ?? ''}
              onChange={(e) => set('description', e.target.value || null)}
            />
          </Field>
        </div>
      </div>

      <Field label="이모지" hint="사진이 없을 때 카드에 크게 보여요.">
        <div className="flex flex-wrap gap-1.5">
          {EMOJI_PALETTE.map((emoji) => (
            <button
              key={emoji}
              type="button"
              onClick={() => set('emoji', emoji)}
              className={`w-10 h-10 rounded-lg border text-xl transition ${
                form.emoji === emoji
                  ? 'border-damso-400 bg-damso-50'
                  : 'border-stone-200 bg-white hover:bg-stone-50'
              }`}
            >
              {emoji}
            </button>
          ))}
          <Input
            value={form.emoji ?? ''}
            maxLength={16}
            placeholder="직접 입력"
            onChange={(e) => set('emoji', e.target.value || null)}
            className="w-28"
          />
        </div>
      </Field>

      <Field label="사진" hint="사진을 올리면 이모지 대신 사진이 보여요. (8MB 이하)">
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="sr-only"
            onChange={(e) => pickImage(e.target.files?.[0])}
          />
          <Button size="sm" onClick={() => fileRef.current?.click()} disabled={uploading}>
            {uploading ? '올리는 중...' : form.imageUrl ? '사진 변경' : '사진 올리기'}
          </Button>
          {form.imageUrl && (
            <Button size="sm" variant="ghost" onClick={() => set('imageUrl', null)}>
              사진 지우기
            </Button>
          )}
          {uploadError && <span className="text-xs text-red-600">{uploadError}</span>}
        </div>
      </Field>

      <div className="flex flex-wrap items-center gap-5">
        <label className="flex items-center gap-2 text-sm text-stone-700">
          <input
            type="checkbox"
            className="w-4 h-4 accent-damso-500"
            checked={form.visible}
            onChange={(e) => set('visible', e.target.checked)}
          />
          홈에 보이기
        </label>
        <label className="flex items-center gap-2 text-sm text-stone-700">
          <input
            type="checkbox"
            className="w-4 h-4 accent-damso-500"
            checked={form.recommended}
            onChange={(e) => set('recommended', e.target.checked)}
          />
          오늘의 추천 한 잔 <span className="text-xs text-stone-400">(하나만 켜집니다)</span>
        </label>
      </div>

      <div className="flex justify-end gap-2 pt-1">
        {onCancel && <Button onClick={onCancel}>취소</Button>}
        <Button
          variant="primary"
          disabled={!form.name.trim() || uploading}
          onClick={() => onSubmit({ ...form, name: form.name.trim() })}
        >
          {submitLabel}
        </Button>
      </div>
    </div>
  );
}
