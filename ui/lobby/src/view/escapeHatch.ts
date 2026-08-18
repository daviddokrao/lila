import { h, type VNode } from 'snabbdom';

import { bind } from 'lib/view';

import type LobbyController from '../ctrl';

// HungKings: chuỗi song ngữ CỤC BỘ cho khối "lối thoát" xếp cặp — không đụng registry
// dịch chính của lila (nhiều tốn kém compile), theo đúng khuôn `t(vi, en)` mà
// `app/views/lobby/homeV2.scala` đã dùng, chỉ chuyển sang phía client. `document
// .documentElement.lang` là do lila set = `ctx.lang.code` lúc render trang (xem
// `modules/web/src/main/ui/layout.scala: htmlTag`), nên đây vẫn đi theo `ctx.lang`.
const homeI18n = (vi: string, en: string): string => (document.documentElement.lang.startsWith('vi') ? vi : en);

export default function renderEscapeHatch(ctrl: LobbyController): VNode | null {
  const hatch = ctrl.escapeHatch;
  if (!hatch || ctrl.tab !== 'real_time') return null;
  const { pool } = hatch;

  const openWithSameClock = (gameType: 'ai' | 'friend') => () =>
    ctrl.setupCtrl.openModal(gameType, {
      timeMode: 'realTime',
      time: pool.lim,
      increment: pool.inc,
    });

  return h(
    'div.lobby__escape-hatch',
    // aria-live: khối này xuất hiện SAU khi trang đã đọc xong, trình đọc màn hình cần
    // được báo chủ động thay vì im lặng chờ người dùng tự dò lại.
    { attrs: { role: 'status', 'aria-live': 'polite' } },
    [
      h(
        'p.lobby__escape-hatch__text',
        homeI18n(
          'Chưa tìm được đối thủ. Site hiện còn ít người chơi trực tuyến nên việc xếp cặp có thể mất một lúc — seek của bạn vẫn đang chạy nền, hễ có người khớp là vào ván ngay. Trong lúc chờ, bạn có thể:',
          'No opponent found yet. The site currently has few players online, so matching can take a while — your seek keeps running in the background and you will jump straight into the game if someone matches. While you wait, you can:',
        ),
      ),
      h('div.lobby__escape-hatch__actions', [
        h(
          'button.button.button-metal.lobby__escape-hatch__action',
          { attrs: { type: 'button' }, hook: bind('click', openWithSameClock('ai'), ctrl.redraw) },
          homeI18n('Chơi với máy ngay', 'Play the computer now'),
        ),
        h(
          'button.button.button-metal.lobby__escape-hatch__action',
          { attrs: { type: 'button' }, hook: bind('click', openWithSameClock('friend'), ctrl.redraw) },
          homeI18n('Mời bạn bằng liên kết', 'Invite a friend by link'),
        ),
      ]),
    ],
  );
}
