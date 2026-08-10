// P1.7 — "Nhận xét nhanh của AI": một câu về ván, hiện NGAY trong bảng thông tin ván.
//
// Vì sao là module riêng chứ không nằm trong round: mở lại một ván ĐÃ KẾT THÚC thì lila
// render bằng module **analyse**, không phải round — nên bản đầu tiên (chỉ gắn vào
// `follow-up` của round) không bao giờ hiện ở đúng chỗ người ta xem lại ván. Khối này
// bám vào `div.game__meta`, thứ có mặt ở CẢ trang phân tích lẫn trang ván đang chơi.
//
// Ba ràng buộc:
//  1. KHÔNG BAO GIỜ làm hỏng trang ván. Không lấy được câu nào thì gỡ hẳn khối đi —
//     một khung rỗng trông như trang lỗi, tệ hơn là không có khung.
//  2. Gọi qua `/hlv-app` (proxy same-origin của lila), KHÔNG sang coach.hungkings.com:
//     iframe/fetch cross-origin bị extension chặn, đã trả giá 06/08.
//  3. Chỉ hỏi khi ván ĐÃ KẾT THÚC. Ván đang chơi thì chưa có gì để nhận xét.

export function initModule(): void {
  const meta = document.querySelector<HTMLElement>('.game__meta');
  if (!meta || meta.querySelector('.game-ai-summary')) return;

  const gameId = meta.dataset.aiSummaryGame;
  if (!gameId) return;

  const vi = document.documentElement.lang.startsWith('vi');
  const lang = vi ? 'vi' : 'en';

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
  meta.append(box);

  fetch(`/hlv-app/api/summary/${encodeURIComponent(gameId)}?lang=${lang}`, { credentials: 'omit' })
    .then(r => (r.ok ? r.json() : null))
    .then(j => {
      const text: string = j && typeof j.summary === 'string' ? j.summary.trim() : '';
      if (!text) box.remove();
      else {
        body.textContent = text;
        box.classList.add('ready');
      }
    })
    .catch(() => box.remove());
}
