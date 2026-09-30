import { pushApi } from '../../api/app';

/**
 * 웹 푸시 켜기/끄기.
 *  - 브라우저에 알림 권한을 받고, 기기별 구독 정보를 서버에 등록한다.
 *  - iOS 사파리는 홈 화면에 추가(PWA)한 뒤에만 푸시를 받을 수 있어, 그 경우를 따로 알려준다.
 */

/** 이 브라우저가 웹 푸시를 지원하는지. */
export function pushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/** iOS 사파리에서 홈 화면에 추가하지 않은 상태인지 — 이때는 권한 요청 자체가 막혀 있다. */
export function needsHomeScreen(): boolean {
  const ua = navigator.userAgent;
  const isIos = /iPad|iPhone|iPod/.test(ua);
  const standalone =
    (window.navigator as any).standalone === true ||
    window.matchMedia('(display-mode: standalone)').matches;
  return isIos && !standalone;
}

export function pushPermission(): NotificationPermission | 'unsupported' {
  return pushSupported() ? Notification.permission : 'unsupported';
}

async function registration(): Promise<ServiceWorkerRegistration> {
  const reg = await navigator.serviceWorker.register('/sw.js');
  // 갓 등록한 워커가 활성화될 때까지 기다린다.
  await navigator.serviceWorker.ready;
  return reg;
}

/**
 * 알림 켜기 — 권한 요청 → 구독 생성 → 서버 등록.
 *
 * @returns 등록된 기기 수
 * @throws 사용자가 권한을 거부했거나 브라우저가 지원하지 않으면 안내 문구를 담은 Error
 */
export async function enablePush(publicKey: string): Promise<number> {
  if (!pushSupported()) {
    throw new Error('이 브라우저에서는 알림을 받을 수 없어요.');
  }
  if (needsHomeScreen()) {
    throw new Error(
      '아이폰은 공유 버튼 → "홈 화면에 추가"로 앱을 설치한 뒤에 알림을 켤 수 있어요.',
    );
  }

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    throw new Error('브라우저에서 알림이 차단돼 있어요. 사이트 설정에서 알림을 허용해주세요.');
  }

  const reg = await registration();
  const existing = await reg.pushManager.getSubscription();
  const sub =
    existing ??
    (await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    }));

  const json = sub.toJSON();
  if (!json.endpoint || !json.keys?.p256dh || !json.keys?.auth) {
    throw new Error('구독 정보를 만들지 못했어요. 잠시 후 다시 시도해주세요.');
  }

  return pushApi.subscribe({
    endpoint: json.endpoint,
    p256dh: json.keys.p256dh,
    auth: json.keys.auth,
  });
}

/** 알림 끄기 — 이 기기의 구독만 해제한다. */
export async function disablePush(): Promise<number> {
  let endpoint: string | undefined;
  if (pushSupported()) {
    const reg = await navigator.serviceWorker.getRegistration('/sw.js');
    const sub = await reg?.pushManager.getSubscription();
    if (sub) {
      endpoint = sub.endpoint;
      await sub.unsubscribe();
    }
  }
  return pushApi.unsubscribe(endpoint);
}

/** 이 기기가 지금 구독 중인지. */
export async function isSubscribed(): Promise<boolean> {
  if (!pushSupported() || Notification.permission !== 'granted') return false;
  const reg = await navigator.serviceWorker.getRegistration('/sw.js');
  const sub = await reg?.pushManager.getSubscription();
  return !!sub;
}

/** 서버가 준 base64url 공개키를 브라우저가 받는 형식으로 바꾼다. */
function urlBase64ToUint8Array(base64: string): ArrayBuffer {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(normalized);
  const buffer = new ArrayBuffer(raw.length);
  const view = new Uint8Array(buffer);
  for (let i = 0; i < raw.length; i += 1) view[i] = raw.charCodeAt(i);
  return buffer;
}
