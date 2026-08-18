// B5 — Khối DEMO "HLV AI" trên TRANG CHỦ (ui-redesign/reports/02-benchmark-ia.md mục 1.4 +
// khuyến nghị B5). Khách đi ĐÚNG MỘT nước trên một bàn cờ nhỏ, HLV AI nhận xét ngay tại chỗ
// bằng tiếng Việt.
//
// Vì sao cần: điều khác biệt thật của HungKings so với lichess là HLV AI giải thích VÌ SAO,
// bằng tiếng Việt — nhưng khách vào trang chủ không có cách nào hiểu điều đó trong 10 giây.
// Trước bản này chỗ đó chỉ có một trích dẫn TĨNH, tức một lời quảng cáo chứ không phải bằng
// chứng. chessable bán khoá học bằng đúng cơ chế này: cho thử trước, hỏi lại sau.
//
// BỐN RÀNG BUỘC, theo thứ tự quan trọng:
//
//  1. TRANG CHỦ KHÔNG BAO GIỜ ĐƯỢC PHỤ THUỘC MỘT SERVICE CÓ THỂ CHẾT. Khối này bắt đầu ở
//     trạng thái ẨN (class `hk-cdemo--init`, CSS `display:none`). Nó chỉ HIỆN sau khi
//     `/hlv-app/healthz` trả lời OK. Coach chết / chưa deploy / proxy hỏng ⇒ khối bị GỠ khỏi
//     DOM và khách không bao giờ thấy nó, chứ không phải thấy một hộp báo lỗi. Không có JS
//     cũng vậy: shell nằm im ở `display:none`.
//  2. KHÔNG CHẶN RENDER. Mọi thứ ở đây chạy sau khi trang đã dựng xong (module esm được nạp
//     qua site.asset.loadEsm), và lượt gọi mạng đầu tiên chỉ xảy ra SAU khi khối dựng xong.
//  3. DƯỚI 4 GIÂY hoặc thôi. Câu trả lời cache ấm về trong vài chục mili giây (khoá cache chỉ
//     có 20 nước — xem coach/src/demo.ts). Quá 4 giây thì khối TỰ THU LẠI: thà không có khối
//     còn hơn để khách nhìn một cái spinner trên trang chủ.
//  4. SAME-ORIGIN QUA `/hlv-app`. KHÔNG gọi coach.hungkings.com: fetch/iframe cross-origin bị
//     extension trình duyệt chặn — dự án đã trả giá đúng lỗi này 06/08.
//
// Bàn cờ dùng chuột/chạm; hàng nút "1.e4 · 1.d4 · 1.Nf3 · 1.c4" bên dưới là ĐƯỜNG TƯƠNG ĐƯƠNG
// cho bàn phím và trình đọc màn hình (chessground không thao tác được bằng bàn phím), đồng
// thời dồn phần lớn lượt bấm vào 4 nước quen thuộc nên cache gần như luôn ấm.

import { initMiniBoardWith, getChessground } from 'lib/view/miniBoard';

/** Mốc "chữ về" của khối. Quá mốc này thì thu khối lại chứ không chờ tiếp. */
const ANSWER_TIMEOUT_MS = 4000;

/** Thăm dò coach: đủ ngắn để không ai kịp thấy khối nhảy vào, đủ dài cho một cú proxy nội bộ. */
const PROBE_TIMEOUT_MS = 2500;

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR';

/**
 * ĐÚNG 20 nước đi đầu hợp lệ của Trắng. Chép cứng thay vì kéo luật cờ vào bundle: thế cờ
 * này là hằng số, và danh sách phải khớp từng chữ với `DEMO_MOVES` bên coach/src/demo.ts —
 * nước nào không có trong danh sách bên đó sẽ bị trả về rỗng.
 */
const DESTS: [string, string[]][] = [
  ['a2', ['a3', 'a4']],
  ['b2', ['b3', 'b4']],
  ['c2', ['c3', 'c4']],
  ['d2', ['d3', 'd4']],
  ['e2', ['e3', 'e4']],
  ['f2', ['f3', 'f4']],
  ['g2', ['g3', 'g4']],
  ['h2', ['h3', 'h4']],
  ['b1', ['a3', 'c3']],
  ['g1', ['f3', 'h3']],
];

/** Bốn nước mở màn quen thuộc nhất — vừa là lối đi bằng bàn phím, vừa giữ cache ấm. */
const PICKS = ['e4', 'd4', 'Nf3', 'c4'];

interface Copy {
  hint: string;
  thinking: string;
  again: string;
  more: string;
  picksLabel: string;
  boardLabel: string;
}

const COPY: Record<'vi' | 'en', Copy> = {
  vi: {
    hint: 'Đi thử một nước cho Trắng — HLV AI sẽ nói ngay vì sao.',
    thinking: 'HLV đang xem nước của bạn…',
    again: 'Thử nước khác',
    more: 'Cho HLV giải cả một ván →',
    picksLabel: 'Hoặc chọn nhanh một nước mở màn',
    boardLabel: 'Bàn cờ thử: đi một nước cho Trắng',
  },
  en: {
    hint: 'Play a move for White — the AI coach explains why, right here.',
    thinking: 'The coach is looking at your move…',
    again: 'Try another move',
    more: 'Have the coach explain a whole game →',
    picksLabel: 'Or pick a familiar opening move',
    boardLabel: 'Demo board: play one move for White',
  },
};

export function initModule(): void {
  const root = document.querySelector<HTMLElement>('.hk-cdemo--init');
  if (!root) return;

  const lang: 'vi' | 'en' = document.documentElement.lang.startsWith('vi') ? 'vi' : 'en';
  const copy = COPY[lang];

  // Ràng buộc #1: hỏi coach còn sống KHÔNG rồi mới dựng gì cả. Thất bại thì gỡ hẳn shell —
  // trang chủ trở về đúng như chưa từng có khối này.
  probe()
    .then(alive => (alive ? build(root, lang, copy) : root.remove()))
    .catch(() => root.remove());
}

function probe(): Promise<boolean> {
  return withTimeout(PROBE_TIMEOUT_MS, signal =>
    fetch('/hlv-app/healthz', { credentials: 'omit', signal }).then(r => r.ok),
  ).catch(() => false);
}

function build(root: HTMLElement, lang: 'vi' | 'en', copy: Copy): void {
  const board = root.querySelector<HTMLElement>('.hk-cdemo__board');
  const say = root.querySelector<HTMLElement>('.hk-cdemo__say');
  const more = root.querySelector<HTMLElement>('.hk-cdemo__more');
  const foot = root.querySelector<HTMLElement>('.hk-cdemo__foot');
  if (!board || !say || !more || !foot) {
    root.remove();
    return;
  }

  say.textContent = copy.hint;

  // Hàng nút chọn nhanh: dựng ở JS chứ không ở Scala, để khi không có JS (hoặc coach chết)
  // không còn lại nút chết nào trên trang.
  const picks = document.createElement('div');
  picks.className = 'hk-cdemo__picks';
  const picksLabel = document.createElement('span');
  picksLabel.className = 'hk-cdemo__pickslabel';
  picksLabel.textContent = copy.picksLabel;
  picks.append(picksLabel);
  for (const san of PICKS) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'hk-cdemo__pick';
    b.textContent = `1.${san}`;
    b.addEventListener('click', () => playPick(san));
    picks.append(b);
  }
  // Hàng chọn nhanh đứng TRÊN cụm chân, không nằm trong nó: `.hk-cdemo__foot` là một hàng
  // flex, nhét cả hàng nút vào đó thì "Thử nước khác" bị đẩy sang phải cạnh 4 nút mở màn
  // (đã đo trên harness) thay vì đứng cạnh liên kết giải cả ván.
  foot.before(picks);

  const again = document.createElement('button');
  again.type = 'button';
  again.className = 'hk-cdemo__again';
  again.textContent = copy.again;
  again.hidden = true;
  more.before(again);

  const dests = new Map<Key, Key[]>(DESTS as [Key, Key[]][]);

  initMiniBoardWith(board, {
    fen: START_FEN,
    orientation: 'white',
    viewOnly: false,
    coordinates: false,
    turnColor: 'white',
    highlight: { lastMove: true, check: false },
    animation: { enabled: !reducedMotion() },
    premovable: { enabled: false },
    drawable: { enabled: false, visible: false },
    movable: {
      free: false,
      color: 'white',
      dests,
      events: {
        after: (orig: Key, dest: Key) => ask(sanOf(orig, dest)),
      },
    },
  });

  const cg = getChessground(board);

  /** Nút chọn nhanh: đi hộ trên bàn cờ để người dùng THẤY nước đó, rồi mới hỏi. */
  const playPick = (san: string) => {
    const dest = san.replace('N', '');
    const orig = san.startsWith('N')
      ? dest === 'f3' || dest === 'h3'
        ? 'g1'
        : 'b1'
      : DESTS.find(([, ds]) => ds.includes(dest))?.[0];
    if (!orig || !cg) return;
    // `cg.move` dùng baseMove, KHÔNG bắn movable.events.after — nên không có lượt hỏi kép.
    cg.move(orig as Key, dest as Key);
    ask(san);
  };

  const ask = (san: string) => {
    // Một nước là hết: khoá bàn cờ và hàng nút để không có lượt gọi thứ hai từ cùng một khách.
    cg?.set({ viewOnly: true, movable: { dests: new Map() } });
    picks.querySelectorAll('button').forEach(b => (b.disabled = true));
    say.textContent = copy.thinking;
    root.classList.add('hk-cdemo--asking');

    withTimeout(ANSWER_TIMEOUT_MS, signal =>
      fetch(`/hlv-app/api/demo?move=${encodeURIComponent(san)}&lang=${lang}`, {
        credentials: 'omit',
        signal,
      })
        .then(r => (r.ok ? r.json() : null))
        .then(j => (j && typeof j.text === 'string' ? j.text.trim() : '')),
    )
      .then(text => (text ? answered(text) : collapse(root)))
      .catch(() => collapse(root));
  };

  const answered = (text: string) => {
    say.textContent = text;
    root.classList.remove('hk-cdemo--asking');
    root.classList.add('hk-cdemo--answered');
    again.hidden = false;
    more.removeAttribute('hidden');
  };

  // CỐ Ý không giấu lại `more`: một khi khách đã nghe HLV nói một lần thì lời mời "giải cả
  // một ván" là thứ đáng giữ trên màn hình, không phải thứ nên rút lại khi họ thử nước khác.
  again.addEventListener('click', () => {
    again.hidden = true;
    root.classList.remove('hk-cdemo--answered');
    say.textContent = copy.hint;
    picks.querySelectorAll('button').forEach(b => (b.disabled = false));
    cg?.set({
      fen: START_FEN,
      lastMove: undefined,
      viewOnly: false,
      turnColor: 'white',
      movable: { free: false, color: 'white', dests },
    });
  });

  board.setAttribute('aria-label', copy.boardLabel);
  root.classList.remove('hk-cdemo--init');
}

/** b1/g1 là Mã, còn lại là Tốt: SAN của nước đầu tiên chỉ có đúng hai dạng đó. */
function sanOf(orig: Key, dest: Key): string {
  return orig === 'b1' || orig === 'g1' ? `N${dest}` : dest;
}

/**
 * Thu khối lại. Dùng max-height chứ không `display:none` để có một cú thu mượt; ai đã bật
 * "giảm chuyển động" thì gỡ thẳng, không animate. Kết cục của cả hai đường là GỠ KHỎI DOM,
 * nên không còn lại khoảng trống nào trên trang chủ.
 */
function collapse(root: HTMLElement): void {
  if (reducedMotion()) {
    root.remove();
    return;
  }
  root.style.maxHeight = `${root.scrollHeight}px`;
  root.classList.add('hk-cdemo--collapsing');

  // VIỆC GỠ PHẢI ĐƯỢC HẸN NGAY, KHÔNG ĐƯỢC NẰM TRONG requestAnimationFrame.
  //
  // rAF KHÔNG chạy khi tab đang ẩn (trình duyệt dừng hẳn nó để tiết kiệm pin). Bản trước
  // đặt `setTimeout(remove, 400)` BÊN TRONG rAF, nên nếu người dùng bấm demo rồi chuyển
  // tab trước lúc thu gọn thì callback không bao giờ chạy → hẹn giờ không bao giờ được
  // đặt → khối kẹt ở "HLV đang xem nước của bạn…" VĨNH VIỄN khi họ quay lại.
  //
  // Đã tái hiện thật 18/08: đo trong tab ẩn (`document.hidden === true`), khối vào
  // `--collapsing` rồi đứng nguyên hơn 10 giây, chữ không đổi. Ban đầu tưởng là hạn chế
  // của môi trường đo, nhưng tab ẩn CHÍNH LÀ kịch bản người dùng thật.
  //
  // Nay hẹn giờ đứng độc lập; rAF chỉ còn lo phần NHÌN (cho transition có điểm bắt đầu).
  // Tab ẩn thì không có animation để xem, nhưng khối vẫn biến mất đúng hẹn.
  setTimeout(() => root.remove(), 400);
  requestAnimationFrame(() => {
    root.style.maxHeight = '0px';
  });
}

function reducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** fetch + hạn chờ cứng. AbortController huỷ ở phía trình duyệt; phía coach vẫn chạy nốt và
 *  ghi kết quả vào cache, nên chính lượt bị bỏ chờ này làm ẤM cache cho người kế tiếp. */
function withTimeout<T>(ms: number, run: (signal: AbortSignal) => Promise<T>): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), ms);
  return run(ctrl.signal).finally(() => clearTimeout(timer));
}
