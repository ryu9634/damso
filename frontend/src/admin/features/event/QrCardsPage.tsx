import { useEffect, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { hostApi } from '../../api/client';
import { PageHeader, Button } from '../../layout/ui';

const FRONT_PORT = window.location.port || '5173';

export default function QrCardsPage() {
  const [host, setHost] = useState(window.location.host);
  const [lanIps, setLanIps] = useState<string[]>([]);
  const [autoApplied, setAutoApplied] = useState(false);

  useEffect(() => {
    (async () => {
      const saved = localStorage.getItem('damso.qrHost');
      try {
        const info = await hostApi.info();
        setLanIps(info.lanIps);
        // localhost로 보고 있는 경우 LAN IP로 자동 대체
        if (!saved && /^(localhost|127\.|0\.0\.)/.test(window.location.hostname) && info.lanIps.length > 0) {
          setHost(`${info.lanIps[0]}:${FRONT_PORT}`);
          setAutoApplied(true);
          return;
        }
      } catch {
        // ignore
      }
      if (saved) setHost(saved);
    })();
  }, []);

  const protocol = window.location.protocol;
  // 각 탭으로 바로 들어가게 한다. (로그인 안 한 손님은 로그인 안내 화면이 먼저 뜬다)
  const guestUrl = `${protocol}//${host}/matching`;
  const datingUrl = `${protocol}//${host}/selecting`;

  const saveHost = (h: string) => {
    setHost(h);
    localStorage.setItem('damso.qrHost', h);
    setAutoApplied(false);
  };

  return (
    <div className="space-y-6">
      <div className="print:hidden">
        <PageHeader title="QR 코드" description="손님이 스캔하면 해당 페이지로 진입합니다 (입장 / 소개팅 신청)" actions={<Button variant="secondary" onClick={() => window.print()}>🖨 인쇄</Button>} />
      </div>

      <section className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-800 print:hidden">
        💡 손님은 매장 와이파이로 접속합니다. 아래 주소가 휴대폰에서 접속 가능한 주소인지 확인하세요.
        {autoApplied && (
          <div className="mt-1 font-semibold">
            ✓ PC의 LAN IP를 자동으로 사용하도록 적용했어요.
          </div>
        )}
      </section>

      <div className="flex items-center gap-2 print:hidden">
        <label className="text-sm text-stone-600 whitespace-nowrap">접속 주소:</label>
        <input
          value={host}
          onChange={(e) => saveHost(e.target.value)}
          placeholder="192.168.0.10:5173"
          className="flex-1 border border-stone-300 rounded px-3 py-1.5 text-sm font-mono"
        />
        <button
          onClick={() => saveHost(window.location.host)}
          className="text-xs px-3 py-1.5 border border-stone-300 rounded hover:bg-stone-100"
        >
          현재 주소로
        </button>
      </div>

      {lanIps.length > 0 && (
        <div className="flex items-center gap-2 flex-wrap text-xs print:hidden">
          <span className="text-stone-500">감지된 LAN IP:</span>
          {lanIps.map((ip) => (
            <button
              key={ip}
              onClick={() => saveHost(`${ip}:${FRONT_PORT}`)}
              className={`px-2 py-1 rounded border font-mono ${
                host === `${ip}:${FRONT_PORT}`
                  ? 'bg-damso-500 text-white border-damso-500'
                  : 'bg-white border-stone-300 hover:bg-stone-100'
              }`}
            >
              {ip}:{FRONT_PORT}
            </button>
          ))}
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-2">
        <QrCard
          subtitle="매칭 이벤트 입장"
          url={guestUrl}
          hint={<>📱 손님이 휴대폰 카메라로 위 QR을 스캔하면<br />입장 등록 폼이 열립니다</>}
        />
        <QrCard
          subtitle="담소가 해주는 소개팅 신청 💘"
          url={datingUrl}
          hint={<>📱 스캔하면 소개팅 프로필 작성 페이지가 열립니다<br />(사진·자기소개·원하는 상대 입력)</>}
        />
      </div>

      <style>{`
        @media print {
          @page { size: A4; margin: 12mm; }
          aside, header { display: none !important; }
          main { padding: 0 !important; }
        }
      `}</style>
    </div>
  );
}

function QrCard({
  subtitle, url, hint,
}: {
  subtitle: string;
  url: string;
  hint: React.ReactNode;
}) {
  return (
    <div className="bg-white border-2 border-stone-300 rounded-xl p-8 flex flex-col items-center print:border print:p-4 break-inside-avoid">
      <div className="text-lg font-bold text-stone-800 mb-2">담소혼술바</div>
      <div className="text-xs text-stone-500 mb-5">{subtitle}</div>

      <div className="p-3 bg-white border border-stone-200 rounded-lg">
        <QRCodeSVG value={url} size={240} level="M" />
      </div>

      <div className="mt-5 text-center">
        <div className="text-[11px] text-stone-500 mb-1">스캔 후 자동 이동되는 주소</div>
        <div className="text-sm font-mono text-stone-700 break-all max-w-xs">{url}</div>
      </div>

      <div className="mt-6 text-sm text-stone-600 text-center max-w-xs">
        {hint}
      </div>
    </div>
  );
}