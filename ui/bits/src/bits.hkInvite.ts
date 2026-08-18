/**
 * HungKings — MỘT liên kết vừa thách đấu vừa mang mã giới thiệu (báo cáo 02, B4).
 *
 * Site có ~16 tài khoản thật và gần như 0 ván, nên lời mời cá nhân là kênh tăng
 * trưởng khả thi duy nhất. Hệ giới thiệu 33% đã dựng xong nhưng chưa có lối dùng
 * tự nhiên — module này là lối đó.
 *
 * HÌNH DẠNG URL ĐÃ CHỐT: `https://hungkings.com/<idVán>?moi=HK7F2QX`
 * tức chính liên kết ván thách đấu, gắn thêm một tham số. KHÔNG dùng
 * `/r/<mã>?next=...` vì:
 *   1. `/r/<mã>` bị gác sau `LILA_POINTS`. Cờ tắt (hoặc service điểm chết) thì cả
 *      lời mời chết theo và người nhận không vào chơi được — hỏng đúng thứ quan
 *      trọng nhất để cứu thứ ít quan trọng hơn. Với `?moi=`, phần ván không bao
 *      giờ phụ thuộc phần điểm.
 *   2. Không cần route mới, không cần sửa `conf/routes` hay `Main.scala`.
 *   3. Không mở thêm bề mặt chuyển hướng (`next=` là một open-redirect phải rào).
 *   4. Liên kết vẫn là liên kết ván THẬT: xem trước, OG graph, khán giả đều y như cũ.
 *
 * BẢO MẬT: mã KHÔNG bao giờ đọc từ trang. Nó lấy từ `/diem-app/api/me` — proxy
 * same-origin của lila tự ký `X-HK-User` bằng HMAC theo phiên đăng nhập, nên
 * trình duyệt chỉ nhận được mã của CHÍNH người đang đăng nhập. Không có đường
 * nào để gán mã người khác lên liên kết của mình.
 *
 * Bước bấm link KHÔNG ghi gì vào cơ sở dữ liệu — chỉ đặt cookie, đúng như
 * `/r/<mã>`. Quan hệ giới thiệu vẫn chỉ được ghi lúc TẠO TÀI KHOẢN.
 */

import { pubsub } from 'lib/pubsub';

/**
 * Tên tham số mang mã mời. Khai DUY NHẤT ở đây: Scala chỉ phát ra liên kết ván
 * trần, toàn bộ việc gắn/đọc mã nằm trong file này nên không có nguy cơ hai nơi
 * ghi hai tên khác nhau. Không đụng tham số nào lila đã đọc (`pov`, `user`,
 * `variant`, `fen`, `color`, `time`, `days`...).
 */
const PARAM = 'moi';

/** Trùng với ràng buộc route `/r/$code<HK[A-Z2-9]{5}>`. */
const CODE_RE = /^HK[A-Z2-9]{5}$/;

/** Trùng với `Main.referralCookieName`. */
const COOKIE = 'hk_ref';

/** 90 ngày, giây — trùng với `Main.pointsReferral`. */
const MAX_AGE = 90 * 24 * 3600;

// ------------------------------------------------------------ phía người NHẬN

/**
 * Bắt `?moi=<mã>` rồi đặt cookie.
 *
 * KHÔNG khai `Domain`: cookie chỉ thuộc đúng host đang xem, mà host đó cũng
 * chính là nơi form đăng ký gửi lên. Khai `Domain` cho khớp lila thì phải đoán
 * tên miền đăng ký được từ `location.hostname` — đoán sai một lần là cookie
 * không bao giờ tới máy chủ, mà hỏng kiểu đó thì im lặng.
 */
function capture(): void {
  const code = new URLSearchParams(location.search).get(PARAM);
  if (!code || !CODE_RE.test(code)) return;

  const secure = location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${COOKIE}=${code}; Max-Age=${MAX_AGE}; Path=/; SameSite=Lax${secure}`;

  // Gỡ tham số khỏi thanh địa chỉ: người nhận chép URL gửi tiếp cho người thứ ba
  // thì không nên mang theo mã của người mời đầu tiên.
  const url = new URL(location.href);
  url.searchParams.delete(PARAM);
  history.replaceState(history.state, '', url.toString());
}

// ------------------------------------------------------------- phía người MỜI

let cached: string | null | undefined;

async function myCode(): Promise<string | null> {
  if (cached !== undefined) return cached;
  let found: string | null = null;
  try {
    const res = await fetch('/diem-app/api/me', {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });
    // 401 = khách chưa đăng nhập · 404 = LILA_POINTS tắt. Cả hai đều là trạng
    // thái BÌNH THƯỜNG: trang giữ nguyên liên kết ván trần, không báo lỗi gì.
    if (res.ok) {
      const data: unknown = await res.json();
      const code = (data as { referralCode?: unknown }).referralCode;
      if (typeof code === 'string' && CODE_RE.test(code)) found = code;
    }
  } catch {
    // mạng hỏng — vẫn là liên kết ván chạy được, không có gì phải nói với người dùng
  }
  cached = found;
  return found;
}

function withCode(base: string, code: string): string {
  const url = new URL(base, location.href);
  url.searchParams.set(PARAM, code);
  return url.toString();
}

function say(box: HTMLElement, text: string): void {
  const el = box.querySelector<HTMLElement>('.invite__hk__say');
  if (el) el.textContent = text;
}

async function decorate(): Promise<void> {
  const box = document.querySelector<HTMLElement>('.invite__hk');
  if (!box) return;

  const base = box.dataset.hkLink;
  if (!base) return;

  const copyBtn = box.querySelector<HTMLButtonElement>('.invite__hk__copy');
  const zalo = box.querySelector<HTMLAnchorElement>('.invite__hk__zalo');
  const input = box.querySelector<HTMLInputElement>('input.copy-me__target');

  const code = await myCode();
  const link = code ? withCode(base, code) : base;

  // Một liên kết duy nhất ở MỌI chỗ trên trang: ô chép, nút chép, nút Zalo, và
  // khay chia sẻ của máy (`bits.challengePage` đọc chính ô input này).
  // Cất vào dataset chứ không chỉ giữ trong biến: `.challenge-page` được vẽ lại
  // mỗi lần socket báo `reload`, nên chỗ bấm phải đọc lại chứ đừng nhớ.
  box.dataset.hkCurrent = link;
  if (input) input.value = link;
  if (zalo) zalo.href = `https://sp.zalo.me/plugins/share?u=${encodeURIComponent(link)}`;

  if (code) box.querySelector<HTMLElement>('.invite__hk__note')?.classList.remove('none');

  const current = () => box.dataset.hkCurrent ?? base;

  if (copyBtn && !copyBtn.dataset.hkWired) {
    copyBtn.dataset.hkWired = '1';
    copyBtn.addEventListener('click', () => {
      // KHÔNG tự viết lại phần chép: bấm hộ nút của `.copy-me` để đi đúng đường
      // mà `ui/site/src/domHandlers.ts` đã xử lý sẵn (kể cả dấu tích báo đã chép).
      // Bấm bằng mã bên trong một cú bấm thật vẫn giữ được "hoạt động người dùng"
      // mà `navigator.clipboard` đòi hỏi.
      box.querySelector<HTMLButtonElement>('.copy-me__button')?.click();
      // Đổi biểu tượng thì trình đọc màn hình không nói gì — cần một vùng live.
      say(box, copyBtn.dataset.copied ?? '');
    });
  }

  if (zalo && !zalo.dataset.hkWired) {
    zalo.dataset.hkWired = '1';
    zalo.addEventListener('click', e => {
      // Trên điện thoại, khay chia sẻ của hệ điều hành là đường tới Zalo mà
      // người Việt dùng thật (Zalo nằm sẵn trong khay). Chỉ khi máy không có
      // khay mới rơi về trang chia sẻ web của Zalo ở `href`.
      if (typeof navigator.share !== 'function') return;
      e.preventDefault();
      navigator.share({ url: current() }).catch(() => {});
    });
  }
}

capture();
void decorate();

// `bits.challengePage` vẽ lại cả `.challenge-page` mỗi lần socket báo `reload`
// (ví dụ đối thủ vừa vào). Không nghe lại thì sau lần vẽ đó liên kết tụt về bản
// không có mã mà không ai thấy — đúng kiểu hỏng im lặng.
pubsub.on('content-loaded', () => {
  void decorate();
});
