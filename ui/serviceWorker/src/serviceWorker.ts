const sw = self as unknown as ServiceWorkerGlobalScope;
const searchParams = new URL(sw.location.href).searchParams;
const assetBase = new URL(searchParams.get('asset-url')!, sw.location.href).href;

function assetUrl(path: string): string {
  return `${assetBase}assets/${path}`;
}

// ---------------------------------------------------------------------------
// HungKings P1.9(a) — offline shell.
//
// Chỉ đụng HAI loại request, còn lại để trình duyệt đi thẳng như cũ:
//   1. Asset ĐÃ BĂM HASH (`ten.A1B2C3D4.js|css`) — tên đổi mỗi lần build nên nội dung là
//      BẤT BIẾN: cache-first an toàn tuyệt đối, không bao giờ phục vụ bản cũ nhầm.
//      CỐ Ý KHÔNG cache ảnh/âm thanh/font trong `public/`: chúng KHÔNG băm hash, cache-first
//      là ghim bản cũ vĩnh viễn (đúng lớp lỗi "đổi asset mà người dùng không thấy").
//   2. Điều hướng (mode === 'navigate') — network-first, mất mạng thì trả trang dự phòng.
//
// `CACHE` mang số phiên bản: đổi CHIẾN LƯỢC cache thì tăng số, `activate` sẽ xoá cache cũ.
// ---------------------------------------------------------------------------
const CACHE = 'hungkings-shell-v1';
const OFFLINE_URL = assetUrl('offline.html');
// Hash của esbuild: 8 ký tự HOA/số nằm giữa tên và đuôi.
const IMMUTABLE = /\.[A-Z0-9]{8}\.(js|css)$/;

sw.addEventListener('install', (e: ExtendableEvent) => {
  e.waitUntil(
    (async () => {
      try {
        const cache = await caches.open(CACHE);
        await cache.add(new Request(OFFLINE_URL, { cache: 'reload' }));
      } catch {
        // Precache hỏng KHÔNG được chặn install: thà SW sống mà thiếu trang dự phòng
        // còn hơn không có SW nào.
      }
      await sw.skipWaiting();
    })(),
  );
});

sw.addEventListener('activate', (e: ExtendableEvent) => {
  e.waitUntil(
    (async () => {
      const names = await caches.keys();
      await Promise.all(names.filter(n => n.startsWith('hungkings-') && n !== CACHE).map(n => caches.delete(n)));
      await sw.clients.claim();
    })(),
  );
});

sw.addEventListener('fetch', (e: FetchEvent) => {
  const req = e.request;
  if (req.method !== 'GET') return;

  if (req.mode === 'navigate') {
    e.respondWith(
      (async () => {
        try {
          return await fetch(req);
        } catch {
          const cached = await caches.match(OFFLINE_URL);
          return (
            cached ??
            new Response('Mất kết nối · Offline', {
              status: 503,
              headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            })
          );
        }
      })(),
    );
    return;
  }

  const url = new URL(req.url);
  // Cùng origin asset đã băm hash. Khác origin (CDN asset domain) thì `url.origin` vẫn
  // khớp `assetBase` nếu site dùng domain asset riêng — so theo assetBase chứ không theo
  // origin của SW, để bản dùng CDN cũng ăn.
  if (!IMMUTABLE.test(url.pathname) || !req.url.startsWith(assetBase)) return;

  e.respondWith(
    (async () => {
      const cached = await caches.match(req);
      if (cached) return cached;
      const res = await fetch(req);
      if (res.ok) {
        const cache = await caches.open(CACHE);
        cache.put(req, res.clone());
      }
      return res;
    })(),
  );
});

sw.addEventListener('push', (event: PushEvent) => {
  const data = event.data!.json();
  return event.waitUntil(
    sw.registration.showNotification(data.title, {
      badge: assetUrl('logo/lichess-mono-128.png'),
      icon: assetUrl('logo/lichess-favicon-192.png'),
      body: data.body,
      tag: data.tag,
      data: data.payload,
      requireInteraction: true,
    }),
  );
});

async function handleNotificationClick(e: NotificationEvent) {
  const notifications = await sw.registration.getNotifications();
  notifications.forEach(notification => notification.close());

  const windowClients = await sw.clients.matchAll({
    type: 'window',
    includeUncontrolled: true,
  });

  // determine url
  const data = e.notification.data.userData;
  let url = data.path || '/';
  if (data.fullId) url = '/' + data.fullId;
  else if (data.threadId) url = '/inbox/' + data.threadId;
  else if (data.challengeId) url = '/' + data.challengeId;
  else if (data.streamerId) url = `/streamer/${data.streamerId}?redirect=1`;
  else if (data.mentionedBy) url = `/forum/redirect/post/${data.postId}`;
  else if (data.invitedBy) url = `/study/${data.studyId}`;

  // focus open window with same url
  for (const client of windowClients) {
    const clientUrl = new URL(client.url, sw.location.href);
    if (clientUrl.pathname === url && 'focus' in client) return await client.focus();
  }

  // navigate from open homepage to url
  for (const client of windowClients) {
    const clientUrl = new URL(client.url, sw.location.href);
    if (clientUrl.pathname === '/') return await client.navigate(url);
  }

  // open new window
  return await sw.clients.openWindow(url);
}

sw.addEventListener('notificationclick', (e: NotificationEvent) => e.waitUntil(handleNotificationClick(e)));
