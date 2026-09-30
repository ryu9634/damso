/*
 * 담소혼술바 앱 서비스워커 — 웹 푸시 전용.
 *  - 앱을 닫아둔 사이에 서버가 보낸 알림을 잠금화면에 띄운다.
 *  - 알림을 누르면 이미 열려 있는 앱 탭으로 보내고, 없으면 새로 연다.
 *  - 오프라인 캐시는 하지 않는다. (알림만 담당)
 */

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { title: '담소혼술바', body: event.data ? event.data.text() : '' };
  }

  const title = data.title || '담소혼술바';
  const options = {
    body: data.body || '',
    icon: '/favicon-192.png',
    badge: '/favicon-32.png',
    tag: data.tag || 'damso',
    data: { link: data.link || '/notifications' },
    // 같은 종류의 알림이 연달아 와도 새로 울리게 한다.
    renotify: true,
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const link = (event.notification.data && event.notification.data.link) || '/notifications';
  const url = new URL(link, self.location.origin).href;

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      for (const client of list) {
        // 이미 열려 있는 앱이 있으면 그 창을 쓴다.
        if (client.url.startsWith(self.location.origin) && 'focus' in client) {
          client.navigate(url);
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
