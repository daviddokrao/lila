// P1.7 — "Nhận xét nhanh của AI": một câu về ván, hiện NGAY trong bảng thông tin ván.
//
// Vì sao là module riêng chứ không nằm trong round: mở lại một ván ĐÃ KẾT THÚC thì lila
// render bằng module **analyse**, không phải round — nên bản đầu tiên (chỉ gắn vào
// `follow-up` của round) không bao giờ hiện ở đúng chỗ người ta xem lại ván. Khối này
// bám vào `div.game__meta`, thứ có mặt ở CẢ trang phân tích lẫn trang ván đang chơi.
//
// Bổ sung (cờ LILA_ROUND_REVIEW_CTA, xem ui/round/src/view/button.ts `aiNoteMount`):
// module dò MỌI phần tử mang `[data-ai-summary-game]`, không chỉ `.game__meta` — cụm nút
// sau ván có thể tự thêm một mốc neo nữa (`.follow-up__ai-note`) để câu nhận xét đứng
// ngay cạnh nút "Xem AI giải cả ván" thay vì chỉ nằm ở khung thông tin bên cạnh bàn cờ.
// Một fetch DUY NHẤT (gameId lấy từ mốc đầu tiên) được áp cho MỌI mốc tìm thấy.
//
// ⚠️ Vì sao KHÔNG giữ tham chiếu DOM qua lời hứa fetch (đã trả giá, đo trên live):
// `analyse/src/view/main.ts` làm `$(elm).replaceWith(ctrl.opts.$side)` khi dựng
// `aside.analyse__side`. Sau cú đó, phần tử ta vừa `append` có thể thành **mồ côi** —
// khối vẫn hiện trên trang (bản trong DOM) nhưng biến trong closure trỏ vào bản đã bị
// tách ra, nên cập nhật chữ xong thì KHÔNG ai thấy: khối đứng mãi ở "Đang xem ván…".
// Nên: lúc trả kết quả thì TRUY VẤN LẠI DOM, và cập nhật MỌI bản đang có.
//
// Hai ràng buộc khác:
//  - KHÔNG BAO GIỜ làm hỏng trang ván: không có câu nào thì gỡ hẳn khối đi (khung rỗng
//    trông như trang lỗi, tệ hơn là không có khung), và có hạn chờ để không treo mãi.
//  - Gọi qua `/hlv-app` (proxy same-origin của lila), KHÔNG sang coach.hungkings.com:
//    fetch/iframe cross-origin bị extension chặn, đã trả giá 06/08.

const TIMEOUT_MS = 25_000;

export function initModule(): void {
  // Mọi mốc chưa có khối (idempotent: gọi lại initModule sau khi đã dựng xong sẽ không
  // tìm thấy mốc nào còn trống nên tự thoát, y hệt hành vi return-sớm bản gốc).
  const mounts = Array.from(document.querySelectorAll<HTMLElement>('[data-ai-summary-game]')).filter(
    el => !el.querySelector('.game-ai-summary'),
  );
  if (!mounts.length) return;

  const gameId = mounts[0].dataset.aiSummaryGame;
  if (!gameId) return;

  const vi = document.documentElement.lang.startsWith('vi');
  const lang = vi ? 'vi' : 'en';

  for (const mount of mounts) {
    const box = document.createElement('section');
    box.className = 'game-ai-summary';
    const head = document.createElement('h3');
    head.className = 'game-ai-summary__head';
    head.textContent = vi ? 'Nhận xét nhanh của HLV AI' : 'Quick take from the AI coach';
    const body = document.createElement('p');
    body.className = 'game-ai-summary__body';
    body.textContent = vi ? 'Đang xem ván…' : 'Reading the game…';
    const more = document.createElement('a');
    more.className = 'game-ai-summary__more';
    more.href = `/hlv/${gameId}`;
    more.target = '_blank';
    more.rel = 'noopener';
    more.textContent = vi ? 'Nghe giải đầy đủ →' : 'Full explanation →';
    box.append(head, body, more);
    mount.append(box);
  }

  // Truy vấn lại DOM thay vì dùng `box`/`body` ở trên — xem ghi chú mồ côi ở đầu tệp.
  const apply = (text: string) => {
    document.querySelectorAll<HTMLElement>('.game-ai-summary').forEach(el => {
      if (!text) el.remove();
      else {
        const p = el.querySelector<HTMLElement>('.game-ai-summary__body');
        if (p) p.textContent = text;
        el.classList.add('ready');
      }
    });
  };

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);

  fetch(`/hlv-app/api/summary/${encodeURIComponent(gameId)}?lang=${lang}`, {
    credentials: 'omit',
    signal: ctrl.signal,
  })
    .then(r => (r.ok ? r.json() : null))
    .then(j => apply(j && typeof j.summary === 'string' ? j.summary.trim() : ''))
    .catch(() => apply(''))
    .finally(() => clearTimeout(timer));
}
