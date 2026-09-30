import { useEffect, useMemo, useState } from 'react';
import type { GameContent, GameRoom, GameType } from '../../../shared/types';
import { GAME_TYPE_EMOJI } from '../../../shared/types';
import { adminGameApi, type GameContentInput } from '../../api/client';
import {
  PageHeader, Card, Button, Empty, Input, Field, Badge, Modal,
} from '../../layout/ui';
import { formatDateTime } from '../../../shared/utils/privacy';

/**
 * 게임방 관리 — 게임 문항과 열린 방.
 *  - 게임방 자체와 게임 종류를 켜고 끄는 스위치는 '기능 사용' 화면에 모아 뒀다.
 *  - 여기서는 손님이 실제로 보게 될 내용(문항·벌칙)과 지금 돌아가는 방을 다룬다.
 */

const TYPE_LABEL: Record<GameType, string> = {
  BALANCE: '밸런스 게임',
  PICK: '랜덤 뽑기 벌칙',
  LIAR: '라이어 게임 제시어',
  MAFIA: '마피아',
};

const TYPE_HINT: Record<GameType, string> = {
  BALANCE: '두 선택지를 모두 채워주세요.',
  PICK: '뽑힌 사람이 할 미션 한 줄을 적어주세요.',
  LIAR: '제시어와 카테고리를 함께 적어주세요. 카테고리는 라이어에게도 보입니다.',
  MAFIA: '마피아는 따로 등록할 문항이 없어요.',
};

/** 문항을 등록하는 게임만 화면에 카드로 만든다. (마피아는 역할만 나누면 된다) */
const CONTENT_TYPES: GameType[] = ['BALANCE', 'PICK', 'LIAR'];

/** 입력칸 라벨 — 게임마다 두 번째 칸의 뜻이 다르다. */
const FIELD_LABEL: Record<GameType, [string, string | null]> = {
  BALANCE: ['선택지 A', '선택지 B'],
  PICK: ['미션 내용', null],
  LIAR: ['제시어', '카테고리'],
  MAFIA: ['내용', null],
};

export default function GamesAdminPage() {
  const [contents, setContents] = useState<GameContent[]>([]);
  const [rooms, setRooms] = useState<GameRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState<GameContent | null>(null);
  const [adding, setAdding] = useState<GameType | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [c, r] = await Promise.all([adminGameApi.contents(), adminGameApi.rooms()]);
      setContents(c);
      setRooms(r);
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

  const save = async (input: GameContentInput) => {
    try {
      if (editing) await adminGameApi.updateContent(editing.id, input);
      else await adminGameApi.createContent(input);
      await load();
      setEditing(null);
      setAdding(null);
      notify(editing ? '문항을 수정했습니다.' : '문항을 추가했습니다.');
    } catch (e) {
      fail(e, '저장하지 못했습니다.');
    }
  };

  const remove = async (item: GameContent) => {
    if (!confirm('이 문항을 지울까요?')) return;
    try {
      await adminGameApi.removeContent(item.id);
      await load();
      notify('문항을 지웠습니다.');
    } catch (e) {
      fail(e, '지우지 못했습니다.');
    }
  };

  const toggleVisible = async (item: GameContent) => {
    try {
      await adminGameApi.updateContent(item.id, {
        type: item.type,
        optionA: item.optionA,
        optionB: item.optionB,
        visible: !item.visible,
        sortOrder: item.sortOrder,
      });
      await load();
    } catch (e) {
      fail(e, '바꾸지 못했습니다.');
    }
  };

  const closeRoom = async (room: GameRoom) => {
    if (!confirm(`${room.code} 방을 종료할까요?`)) return;
    try {
      await adminGameApi.closeRoom(room.code);
      await load();
      notify('방을 종료했습니다.');
    } catch (e) {
      fail(e, '종료하지 못했습니다.');
    }
  };

  const byType = useMemo(() => {
    const m = new Map<GameType, GameContent[]>();
    for (const c of contents) {
      if (!m.has(c.type)) m.set(c.type, []);
      m.get(c.type)!.push(c);
    }
    return m;
  }, [contents]);

  const openRooms = rooms.filter((r) => r.status !== 'CLOSED');

  return (
    <div className="space-y-6">
      <PageHeader
        title="게임방"
        description="손님이 방을 만들어 함께 즐기는 짧은 게임이에요. 게임방·게임 종류를 켜고 끄는 건 '기능 사용' 화면에 있습니다."
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

      {CONTENT_TYPES.map((type) => {
        const list = byType.get(type) ?? [];
        return (
          <Card
            key={type}
            title={
              <span className="flex items-center gap-2">
                <span>{GAME_TYPE_EMOJI[type]}</span>
                <span>{TYPE_LABEL[type]}</span>
                <span className="text-xs font-normal text-stone-400">{list.length}개</span>
              </span>
            }
            actions={
              <Button size="sm" variant="primary" onClick={() => { setEditing(null); setAdding(type); }}>
                + 추가
              </Button>
            }
          >
            {loading ? (
              <Empty>불러오는 중...</Empty>
            ) : list.length === 0 ? (
              <Empty>등록된 문항이 없습니다.</Empty>
            ) : (
              <ul className="divide-y divide-stone-100">
                {list.map((item) => (
                  <li key={item.id} className="flex items-center gap-3 py-2.5">
                    <span className="min-w-0 flex-1 text-sm text-stone-800">
                      {type === 'BALANCE' ? (
                        <>
                          {item.optionA}
                          <span className="mx-2 text-stone-300">vs</span>
                          {item.optionB}
                        </>
                      ) : type === 'LIAR' ? (
                        <>
                          {item.optionA}
                          <span className="ml-2 text-xs text-stone-400">{item.optionB}</span>
                        </>
                      ) : (
                        item.optionA
                      )}
                      {!item.visible && <Badge className="ml-2">숨김</Badge>}
                    </span>
                    <span className="flex shrink-0 gap-1.5">
                      <Button size="sm" onClick={() => toggleVisible(item)}>
                        {item.visible ? '숨기기' : '보이기'}
                      </Button>
                      <Button size="sm" onClick={() => { setAdding(null); setEditing(item); }}>
                        수정
                      </Button>
                      <Button size="sm" variant="danger" onClick={() => remove(item)}>삭제</Button>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        );
      })}

      <Card title={`열린 방 ${openRooms.length}개`}>
        {loading ? (
          <Empty>불러오는 중...</Empty>
        ) : openRooms.length === 0 ? (
          <Empty>지금 열려 있는 방이 없습니다.</Empty>
        ) : (
          <ul className="divide-y divide-stone-100">
            {openRooms.map((room) => (
              <li key={room.code} className="flex flex-wrap items-center gap-3 py-2.5">
                <Badge className="bg-stone-900 text-white tracking-widest">{room.code}</Badge>
                <span className="text-sm font-semibold text-stone-900">
                  {room.title || `${room.hostName}님의 방`}
                </span>
                <span className="text-xs text-stone-500">
                  {room.playerCount}명 · {room.statusLabel}
                </span>
                <span className="text-xs text-stone-400">{formatDateTime(room.createdAt)}</span>
                <Button size="sm" variant="danger" className="ml-auto" onClick={() => closeRoom(room)}>
                  강제 종료
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <ContentModal
        open={editing !== null || adding !== null}
        content={editing}
        type={editing?.type ?? adding ?? 'BALANCE'}
        onClose={() => { setEditing(null); setAdding(null); }}
        onSave={save}
      />
    </div>
  );
}

/** 문항 등록·수정 모달. */
function ContentModal({
  open, content, type, onClose, onSave,
}: {
  open: boolean;
  content: GameContent | null;
  type: GameType;
  onClose: () => void;
  onSave: (input: GameContentInput) => void | Promise<void>;
}) {
  const [optionA, setOptionA] = useState('');
  const [optionB, setOptionB] = useState('');
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    if (!open) return;
    setOptionA(content?.optionA ?? '');
    setOptionB(content?.optionB ?? '');
    setVisible(content?.visible ?? true);
  }, [open, content]);

  const [labelA, labelB] = FIELD_LABEL[type];
  // 밸런스는 선택지 두 개가 다 있어야 성립한다. 라이어의 카테고리는 없어도 된다.
  const valid = optionA.trim().length > 0 && (type !== 'BALANCE' || optionB.trim().length > 0);

  return (
    <Modal
      open={open}
      title={`${TYPE_LABEL[type]} ${content ? '수정' : '추가'}`}
      onClose={onClose}
      width="max-w-lg"
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button
            variant="primary"
            disabled={!valid}
            onClick={() =>
              onSave({
                type,
                optionA: optionA.trim(),
                optionB: labelB ? optionB.trim() || null : null,
                visible,
                sortOrder: content?.sortOrder,
              })
            }
          >
            저장
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label={labelA} hint={TYPE_HINT[type]}>
          <Input
            value={optionA}
            maxLength={200}
            placeholder={
              type === 'BALANCE' ? '예: 소주' : type === 'LIAR' ? '예: 사과' : '예: 옆 사람에게 칭찬 한마디'
            }
            onChange={(e) => setOptionA(e.target.value)}
          />
        </Field>
        {labelB && (
          <Field label={labelB}>
            <Input
              value={optionB}
              maxLength={200}
              placeholder={type === 'BALANCE' ? '예: 맥주' : '예: 과일'}
              onChange={(e) => setOptionB(e.target.value)}
            />
          </Field>
        )}
        <label className="flex items-center gap-2 text-sm text-stone-700">
          <input
            type="checkbox"
            className="w-4 h-4 accent-damso-500"
            checked={visible}
            onChange={(e) => setVisible(e.target.checked)}
          />
          게임에 사용하기
        </label>
      </div>
    </Modal>
  );
}
