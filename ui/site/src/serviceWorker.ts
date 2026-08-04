import { log } from 'lib/permalog';
import { storage } from 'lib/storage';

import { url as assetUrl, jsModule } from './asset';

export default async function () {
  // HungKings P1.9(a): TÁCH đăng ký service worker khỏi gate PushManager.
  // Trước đây một điều kiện `&&` duy nhất gác cả hai, nên trình duyệt nào thiếu
  // PushManager là KHÔNG BAO GIỜ đăng ký service worker — mà Safari trên iOS chỉ có
  // PushManager khi trang ĐÃ được cài thành PWA. Hệ quả: đúng nền tảng cần offline
  // shell nhất lại là nền tảng không bao giờ nhận được nó.
  if (!('serviceWorker' in navigator)) return;
  const workerUrl = new URL(assetUrl(jsModule('serviceWorker'), { pathOnly: true }), self.location.href);
  workerUrl.searchParams.set('asset-url', document.body.getAttribute('data-asset-url')!);
  let newSub: PushSubscription | undefined = undefined;
  try {
    const reg = await navigator.serviceWorker.register(workerUrl.href, { scope: '/', updateViaCache: 'all' });

    // Từ đây trở xuống là phần PUSH — thiếu API thì bỏ qua, service worker (và offline
    // shell) ở trên vẫn đã đăng ký xong.
    if (!('Notification' in window && 'PushManager' in window)) return;

    const store = storage.make('push-subscribed');
    const resub = parseInt(store.get() || '0', 10) + 43200000 < Date.now(); // 12 hours
    const vapid = document.body.getAttribute('data-vapid');
    const sub = await reg.pushManager.getSubscription();

    if (!vapid || Notification.permission !== 'granted') return store.remove();
    else if (sub && !resub) return;

    newSub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: vapid });

    if (!newSub) throw new Error(JSON.stringify(await reg.pushManager.permissionState()));

    const res = await fetch('/push/subscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newSub),
    });

    if (res.ok && !res.redirected) store.set(String(Date.now()));
    else throw new Error(res.statusText);
  } catch (err: any) {
    log('serviceWorker.ts:', err.message, newSub);
    if (newSub?.endpoint) await newSub?.unsubscribe();
  }
}
