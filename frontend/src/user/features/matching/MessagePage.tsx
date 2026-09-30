import { useCallback, useEffect, useState } from 'react';
import { guestApi } from './guest';
import MiniSeatMap, { type SeatHighlight } from './MiniSeatMap';
import type { Customer, GuestCandidate, GuestMessage, GuestSeatView, GuestSettings } from '../../../shared/types';

export default function MessagePage({ me, settings }: { me: Customer; settings: GuestSettings }) {
  const MAX_SEND = Math.max(1, settings.maxMessageRecipients);
  const [candidates, setCandidates] = useState<GuestCandidate[]>([]);
  const [sentList, setSentList] = useState<GuestMessage[]>([]);
  const [received, setReceived] = useState<GuestMessage[]>([]);
  const [seats, setSeats] = useState<GuestSeatView[]>([]);
  const [receiverId, setReceiverId] = useState<number | null>(null);
  const [content, setContent] = useState('');
  const [contact, setContact] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const [cs, sentMsgs, recvMsgs, ss] = await Promise.all([
      guestApi.candidates(me.id),
      guestApi.myMessagesSent(me.id),
      guestApi.myMessagesReceived(me.id),
      guestApi.seats(me.id, 4),
    ]);
    setCandidates(cs);
    setSentList(sentMsgs);
    setReceived(recvMsgs);
    setSeats(ss);
    // 이미 보낸 연락처를 재사용
    if (sentMsgs.length > 0) setContact((prev) => prev || sentMsgs[0].senderContact);
  }, [me.id]);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 4000);
    return () => clearInterval(t);
  }, [refresh]);

  const sentReceiverIds = new Set(sentList.map((m) => m.receiverId));
  const remaining = MAX_SEND - sentList.length;
  // 아직 메시지를 보내지 않은 후보만 폼에 노출
  const selectable = candidates.filter((c) => !sentReceiverIds.has(c.id));

  const seatHighlights: SeatHighlight[] = [
    ...sentList.map((m) => ({ id: m.receiverId, label: '내가 보냄', tone: 'damso' } as SeatHighlight)),
    ...(receiverId != null && !sentReceiverIds.has(receiverId)
      ? [{ id: receiverId, label: '받는 사람', tone: 'amber' } as SeatHighlight]
      : []),
  ];

  const submit = async () => {
    if (receiverId == null) { setErr('받는 사람을 선택해주세요.'); return; }
    if (!content.trim()) { setErr('메시지를 입력해주세요.'); return; }
    if (!contact.trim()) { setErr('본인 연락처(인스타 ID 또는 폰번호)를 입력해주세요.'); return; }
    setSubmitting(true); setErr(null);
    try {
      await guestApi.sendMessage({
        customerId: me.id,
        receiverId,
        content: content.trim(),
        senderContact: contact.trim(),
      });
      // 다음 사람에게 이어서 보낼 수 있도록 대상/내용만 초기화
      setReceiverId(null);
      setContent('');
      await refresh();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '발송 실패');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-5">
      {/* 받은 메시지 — 있으면 최상단에 강조 */}
      {received.length > 0 && (
        <section className="space-y-3">
          <div className="text-center">
            <div className="text-xl">받은 메세지는 곧 사라집니다!<br></br>캡쳐 해두세요📷️</div>
            <div className="text-4xl">💝</div>
            <h2 className="text-lg font-bold mt-1">받은 마음 {received.length}개</h2>
            <p className="text-xs text-stone-600 mt-0.5">
              {me.nickname}님에게 마음을 전한 사람이 있어요
            </p>
          </div>
          <div className="space-y-3">
            {received.map((m) => (
              <div key={m.id} className="bg-gradient-to-br from-rose-50 to-pink-50 border-2 border-rose-200 rounded-xl p-4 shadow-sm">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-stone-500">From.</span>
                    <span className="font-bold text-stone-900">{m.senderNickname}</span>
                  </div>
                  <span className="text-[10px] text-stone-500">
                    {new Date(m.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
                <p className="text-stone-800 whitespace-pre-wrap text-sm leading-relaxed bg-white/70 rounded-lg p-3">
                  {m.content}
                </p>
                <div className="mt-3 pt-3 border-t border-rose-100">
                  <div className="text-[11px] text-stone-500 mb-0.5">상대 연락처</div>
                  <div className="font-semibold text-rose-700 text-sm select-all break-all">
                    {m.senderContact}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* 4차 자리 배치도 (참고) */}
      {seats.length > 0 && (
        <MiniSeatMap
          seats={seats}
          myId={me.id}
          title="현재 자리 배치 · 4차 (참고)"
          highlights={seatHighlights}
        />
      )}

      {/* 내가 보낸 메시지 목록 */}
      {sentList.length > 0 && (
        <section className="space-y-3">
          <div className="text-center">
            <div className="text-2xl">💌</div>
            <h2 className="text-base font-bold mt-1">마음을 전했어요 ({sentList.length}/{MAX_SEND})</h2>
            <p className="text-xs text-stone-600 mt-0.5">
              {remaining > 0 ? `${remaining}명에게 더 보낼 수 있어요` : '상대도 곧 이 마음을 받게 됩니다'}
            </p>
          </div>
          {sentList.map((m) => (
            <div key={m.id} className="bg-white border border-stone-200 rounded-xl p-4 space-y-3">
              <div className="flex items-center justify-between text-sm">
                <span className="text-stone-500">To.</span>
                <span className="font-bold text-stone-900">{m.receiverNickname}</span>
              </div>
              <div className="border-t border-stone-100 pt-3">
                <div className="text-stone-800 whitespace-pre-wrap text-sm leading-relaxed">
                  {m.content}
                </div>
              </div>
              <div className="border-t border-stone-100 pt-3 text-xs text-stone-500">
                <div>내 연락처</div>
                <div className="text-stone-700 mt-0.5">{m.senderContact}</div>
              </div>
            </div>
          ))}
        </section>
      )}

      {/* 보내기 폼 — 아직 2명 미만이고 보낼 상대가 남아있을 때 */}
      {remaining > 0 && selectable.length > 0 && (
        <section className="space-y-5">
          <div className="text-center">
            <div className="text-3xl">✉️</div>
            <h2 className="text-lg font-bold mt-1">{settings.messageTitle}</h2>
            <p className="text-xs text-stone-600 mt-1 whitespace-pre-line">{settings.messageSubtitle}</p>
            <p className="text-xs text-stone-500 mt-0.5">최대 {MAX_SEND}명에게 보낼 수 있어요.</p>
          </div>

          <div>
            <div className="text-xs font-semibold text-stone-600 mb-2">받는 사람</div>
            <div className="grid grid-cols-2 gap-2">
              {selectable.map((c) => (
                <button
                  key={c.id}
                  onClick={() => setReceiverId(c.id)}
                  className={`p-3 rounded-lg border-2 text-left transition ${
                    receiverId === c.id
                      ? 'border-damso-500 bg-damso-50'
                      : 'border-stone-200 bg-white active:bg-stone-50'
                  }`}
                >
                  <div className="font-bold text-stone-900">{c.nickname}</div>
                </button>
              ))}
            </div>
          </div>

          <div>
            <div className="text-xs font-semibold text-stone-600 mb-2">메시지</div>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="전하고 싶은 말을 적어주세요"
              rows={5}
              className="w-full border border-stone-300 rounded-md px-3 py-3 text-base resize-none"
            />
            <div className="text-right text-[10px] text-stone-400 mt-1">{content.length}/500</div>
          </div>

          <div>
            <div className="text-xs font-semibold text-stone-600 mb-2">내 연락처</div>
            <input
              value={contact}
              onChange={(e) => setContact(e.target.value)}
              placeholder="인스타 ID 또는 폰번호"
              className="w-full border border-stone-300 rounded-md px-3 py-3 text-base"
            />
            <div className="text-[10px] text-stone-500 mt-1">
              상대에게 마음과 함께 이 연락처가 전달돼요.
            </div>
          </div>

          {err && <div className="text-sm text-red-600 text-center">{err}</div>}

          <div className="sticky bottom-3 pt-2">
            <button
              onClick={submit}
              disabled={submitting || receiverId == null || !content.trim() || !contact.trim()}
              className="w-full py-4 rounded-full text-white font-bold bg-damso-500 active:bg-damso-600 disabled:bg-stone-300 shadow-lg"
            >
              {submitting ? '전달 중...' : '마음 전하기'}
            </button>
          </div>
        </section>
      )}

      {/* 받은 메시지가 없을 때 안내 */}
      {received.length === 0 && sentList.length > 0 && (
        <div className="bg-stone-50 border border-stone-200 rounded-lg p-3 text-center text-xs text-stone-500">
          🕊️ 아직 받은 마음이 없어요. 도착하면 여기에 표시됩니다.
        </div>
      )}
    </div>
  );
}
