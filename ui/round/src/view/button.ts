import type { VNode, Hooks } from 'snabbdom';

import { finished, aborted, replayable, rematchable, moretimeable, type PlayerUser } from 'lib/game';
import type { ClockData } from 'lib/game/clock/clockCtrl';
import { game as gameRoute } from 'lib/game/router';
import { licon, type LiconValue } from 'lib/licon';
import { pubsub } from 'lib/pubsub';
import {
  spinnerVdom as spinner,
  type LooseVNodes,
  type LooseVNode,
  hl,
  bind,
  onInsert,
  dataIcon,
} from 'lib/view';

import type RoundController from '../ctrl';
import type { EventsWithoutPayload, RoundData } from '../interfaces';

export interface ButtonState {
  enabled: boolean;
  overrideHint?: string;
}

function analysisBoardOrientation(data: RoundData) {
  return data.game.variant.key === 'racingKings' ? 'white' : data.player.color;
}

function poolUrl(clock: ClockData, blocking?: PlayerUser) {
  return '/#pool/' + clock.initial / 60 + '+' + clock.increment + (blocking ? '/' + blocking.id : '');
}

function analysisButton(ctrl: RoundController): VNode | false {
  const d = ctrl.data,
    url = gameRoute(d, analysisBoardOrientation(d)) + '#' + ctrl.ply;
  return (
    replayable(d) &&
    hl(
      'a.fbt',
      {
        attrs: { href: url },
        hook: bind(
          'click',
          e => {
            // force page load in case the URL is the same
            if (d.local) {
              d.local.analyse();
              return e.preventDefault();
            }
            if (location.pathname === url.split('#')[0]) location.reload();
          },
          undefined,
          false,
        ),
      },
      i18n.site.analysis,
    )
  );
}

// Liên kết sang bot giải thích ván bằng tiếng Việt (service riêng, ngoài lila).
// Chỉ hiện khi ván đã kết thúc — bot đọc PGN của ván đã xong.
function coachButton(ctrl: RoundController): VNode | false {
  const d = ctrl.data;
  return (
    finished(d) &&
    hl(
      'a.fbt',
      { attrs: { href: `/hlv/${d.game.id}`, target: '_blank', rel: 'noopener' } },
      'Giải thích ván (AI)',
    )
  );
}

// P1.7 — "Nhận xét nhanh của AI" NGAY trong trang ván.
//
// Vì sao ở đây: Game Review của chess.com được ưa chuộng vì nó nằm TRONG luồng, còn
// nút "Giải thích ván (AI)" hiện tại là một cú nhảy sang trang khác. Một câu hiện tại
// chỗ là đủ để người chơi thấy giá trị mà không phải rời trang.
//
// Ba ràng buộc đã cân nhắc:
//  1. KHÔNG BAO GIỜ chặn hay làm hỏng trang ván. Fetch chạy sau khi node được chèn,
//     mọi lỗi đều nuốt, và khối tự gỡ nếu không có nội dung.
//  2. Gọi qua `/hlv-app` (proxy same-origin của lila) chứ không sang coach.hungkings.com
//     — cross-origin bị extension chặn, đã trả giá hồi 06/08 (memory hlv-nhung-same-origin).
//  3. Mỗi ván chỉ hỏi MỘT lần cho mỗi ngôn ngữ; coach cache lại nên vào lại là tức thì.
const summaryAsked = new Set<string>();

function aiSummary(ctrl: RoundController): VNode | false {
  const d = ctrl.data;
  if (!finished(d)) return false;
  const gameId = d.game.id;
  const vi = document.documentElement.lang.startsWith('vi');
  const lang = vi ? 'vi' : 'en';
  return hl('div.round-ai-summary', { key: 'ai-summary' }, [
    hl(
      'div.round-ai-summary__body',
      {
        hook: onInsert(el => {
          const box = el.parentElement as HTMLElement | null;
          const cacheKey = `${gameId}:${lang}`;
          if (summaryAsked.has(cacheKey)) return;
          summaryAsked.add(cacheKey);
          fetch(`/hlv-app/api/summary/${gameId}?lang=${lang}`, { credentials: 'omit' })
            .then(r => (r.ok ? r.json() : null))
            .then(j => {
              const text = j && typeof j.summary === 'string' ? j.summary.trim() : '';
              // Không có câu nào (hết hạn mức, ván chưa đọc được…) thì gỡ hẳn khối đi.
              // Một khung rỗng trông như trang hỏng, tệ hơn là không có khung nào.
              if (!text) box?.remove();
              else {
                el.textContent = text;
                box?.classList.add('ready');
              }
            })
            .catch(() => box?.remove());
        }),
      },
      hl('span.round-ai-summary__wait', vi ? 'HLV AI đang xem ván…' : 'AI coach is reading…'),
    ),
    hl(
      'a.round-ai-summary__more',
      { attrs: { href: `/hlv/${gameId}`, target: '_blank', rel: 'noopener' } },
      vi ? 'Nghe giải đầy đủ →' : 'Full explanation →',
    ),
  ]);
}

// P1.1: mời đăng ký đúng "khoảnh khắc vàng" — khách ẩn danh vừa chơi xong ván.
// Một dòng link trong follow-up, KHÔNG popup (danh giới đã chốt trong DECISIONS).
function signupNudge(ctrl: RoundController): VNode | false {
  const vi = document.documentElement.lang.startsWith('vi');
  return (
    finished(ctrl.data) &&
    !document.body.dataset.user &&
    hl(
      'a.fbt',
      { attrs: { href: '/signup' } },
      vi ? 'Tạo tài khoản miễn phí — lưu ván, có rating' : 'Free account — save games, get a rating',
    )
  );
}

function rematchButtons(ctrl: RoundController): LooseVNodes {
  const d = ctrl.data,
    me = !!d.player.offeringRematch,
    disabled = !me && !d.opponent.onGame && (!!d.clock || !d.player.user || !d.opponent.user),
    them = !!d.opponent.offeringRematch && !disabled;
  if (!rematchable(d)) return [];
  return [
    them &&
      hl(
        'button.rematch-decline',
        {
          attrs: { 'data-icon': licon.X, title: i18n.site.decline },
          hook: bind('click', () => ctrl.socket.send('rematch-no')),
        },
        ctrl.nvui ? i18n.site.decline : '',
      ),
    hl(
      'button.fbt.rematch.white',
      {
        class: { me, glowing: them },
        attrs: {
          disabled,
          title: them
            ? i18n.site.yourOpponentWantsToPlayANewGameWithYou
            : me
              ? i18n.site.rematchOfferSent
              : '',
        },
        hook: bind(
          'click',
          () => {
            const d = ctrl.data;
            if (d.game.rematch) location.href = gameRoute(d.game.rematch, d.opponent.color);
            else if (d.player.offeringRematch) {
              d.player.offeringRematch = false;
              ctrl.socket.send('rematch-no');
            } else if (d.opponent.onGame || !d.clock) {
              d.player.offeringRematch = true;
              if (d.opponent.onGame) ctrl.socket.send('rematch-yes');
              else if (!disabled && !d.opponent.onGame) ctrl.challengeRematch();
            }
          },
          ctrl.redraw,
        ),
      },
      [me ? spinner() : hl('span', i18n.site.rematch)],
    ),
  ];
}

export function standard(
  ctrl: RoundController,
  condition: ((d: RoundData) => ButtonState) | undefined,
  icon: LiconValue,
  hint: string,
  socketMsg: EventsWithoutPayload,
  onclick?: () => void,
): VNode {
  // disabled if condition callback is provided and is falsy
  const enabled = () => !condition || condition(ctrl.data).enabled;
  const hintFn = () => condition?.(ctrl.data)?.overrideHint || hint;
  return hl(
    'button.fbt.' + socketMsg,
    {
      attrs: { disabled: !enabled(), ...(!ctrl.nvui ? { title: hintFn() } : {}) },
      hook: bind('click', () => {
        if (enabled()) onclick ? onclick() : ctrl.socket.sendLoading(socketMsg);
      }),
    },
    ctrl.nvui ? [hintFn()] : [hl('span', { attrs: dataIcon(icon) })],
  );
}

export function opponentGone(ctrl: RoundController): LooseVNode {
  const gone = ctrl.opponentGone();
  if (ctrl.data.game.rules?.includes('noClaimWin')) return null;
  return gone === true
    ? hl('div.suggestion', [
        hl('p', { hook: onSuggestionHook }, i18n.site.opponentLeftChoices),
        hl(
          'button.button',
          { hook: bind('click', () => ctrl.socket.sendLoading('resign-force')) },
          i18n.site.forceResignation,
        ),
        hl(
          'button.button',
          { hook: bind('click', () => ctrl.socket.sendLoading('draw-force')) },
          i18n.site.forceDraw,
        ),
      ])
    : gone !== false &&
        hl(
          'div.suggestion.opponent-left-counter',
          hl('p', i18n.site.opponentLeftCounter.asArray(gone, hl('strong', gone))),
        );
}

const fbtCancel = (f: (v: boolean) => void) =>
  hl('button.fbt.no', {
    attrs: { title: i18n.site.cancel, 'data-icon': licon.X },
    hook: bind('click', () => f(false)),
  });

export const resignConfirm = (ctrl: RoundController): VNode =>
  hl('div.act-confirm', [
    hl('button.fbt.yes', {
      attrs: { title: i18n.site.resign, 'data-icon': licon.FlagOutline },
      hook: bind('click', () => ctrl.resign(true)),
    }),
    fbtCancel(ctrl.resign),
  ]);

export const drawConfirm = (ctrl: RoundController): VNode =>
  hl('div.act-confirm', [
    hl('button.fbt.yes.draw-yes', {
      attrs: { title: i18n.site.offerDraw, 'data-icon': licon.OneHalf },
      hook: bind('click', () => ctrl.offerDraw(true)),
    }),
    fbtCancel(ctrl.offerDraw),
  ]);

export const claimThreefold = (ctrl: RoundController, condition: (d: RoundData) => ButtonState): VNode =>
  hl(
    'button.button.draw-yes',
    {
      hook: bind('click', () =>
        condition(ctrl.data).enabled ? ctrl.socket.sendLoading('draw-claim') : undefined,
      ),
      attrs: {
        title: condition(ctrl.data)?.overrideHint || i18n.site.claimADraw,
        disabled: !condition(ctrl.data).enabled,
      },
      class: { disabled: !condition(ctrl.data).enabled },
    },
    hl('span', '½'),
  );

export function threefoldSuggestion(ctrl: RoundController): LooseVNode {
  return (
    ctrl.data.game.threefold &&
    hl('div.suggestion', [hl('p', { hook: onSuggestionHook }, i18n.site.threefoldRepetition)])
  );
}

export function backToTournament(ctrl: RoundController): LooseVNode {
  const d = ctrl.data;
  return (
    d.tournament?.running &&
    hl('div.follow-up', [
      hl(
        'a.text.fbt.strong.glowing',
        {
          attrs: { 'data-icon': licon.PlayTriangle, href: '/tournament/' + d.tournament.id },
          hook: bind('click', ctrl.setRedirecting),
        },
        i18n.site.backToTournament,
      ),
      hl('form', { attrs: { method: 'post', action: '/tournament/' + d.tournament.id + '/withdraw' } }, [
        hl('button.text.fbt.weak', { attrs: dataIcon(licon.Pause) }, i18n.site.pause),
      ]),
      analysisButton(ctrl),
    ])
  );
}

export function backToSwiss(ctrl: RoundController): LooseVNode {
  const d = ctrl.data;
  return (
    d.swiss?.running &&
    hl('div.follow-up', [
      hl(
        'a.text.fbt.strong.glowing',
        {
          attrs: { 'data-icon': licon.PlayTriangle, href: '/swiss/' + d.swiss.id },
          hook: bind('click', ctrl.setRedirecting),
        },
        i18n.site.backToTournament,
      ),
      analysisButton(ctrl),
    ])
  );
}

export function moretime(ctrl: RoundController): LooseVNode {
  return (
    moretimeable(ctrl.data) &&
    hl('a.moretime', {
      attrs: {
        title: ctrl.data.clock
          ? i18n.site.giveNbSeconds(ctrl.data.clock.moretime)
          : i18n.preferences.giveMoreTime,
        'data-icon': licon.PlusButton,
      },
      hook: bind('click', ctrl.socket.moreTime),
    })
  );
}

export function followUp(ctrl: RoundController): VNode {
  const d = ctrl.data,
    rematchable =
      !d.game.rematch &&
      (finished(d) || (aborted(d) && (!d.game.rated || !['lobby', 'pool'].includes(d.game.source)))) &&
      !d.tournament &&
      !d.simul &&
      !d.swiss &&
      !d.game.boosted,
    newable = (finished(d) || aborted(d)) && ['lobby', 'pool', 'local'].includes(d.game.source),
    rematchZone = rematchable || d.game.rematch ? rematchButtons(ctrl) : [];
  return hl('div.follow-up', [
    rematchZone,
    d.tournament &&
      hl('a.fbt', { attrs: { href: '/tournament/' + d.tournament.id } }, i18n.site.viewTournament),
    d.swiss && hl('a.fbt', { attrs: { href: '/swiss/' + d.swiss.id } }, i18n.site.viewTournament),
    newable &&
      hl(
        'button.fbt.new-opponent',
        {
          hook: bind('click', () => {
            if (d.game.source === 'local') d.local?.newOpponent();
            else if (d.game.source === 'pool') location.href = poolUrl(d.clock!, d.opponent.user);
            else location.href = '/?hook_like=' + d.game.id;
          }),
        },
        i18n.site.newOpponent,
      ),
    analysisButton(ctrl),
    coachButton(ctrl),
    signupNudge(ctrl),
    aiSummary(ctrl),
  ]);
}

export function watcherFollowUp(ctrl: RoundController): LooseVNode {
  const d = ctrl.data,
    content = [
      d.game.rematch &&
        hl(
          'a.fbt.text',
          { attrs: { href: `/${d.game.rematch}/${d.opponent.color}` } },
          i18n.site.viewRematch,
        ),
      d.tournament &&
        hl('a.fbt', { attrs: { href: '/tournament/' + d.tournament.id } }, i18n.site.viewTournament),

      d.swiss && hl('a.fbt', { attrs: { href: '/swiss/' + d.swiss.id } }, i18n.site.viewTournament),
      analysisButton(ctrl),
      coachButton(ctrl),
      aiSummary(ctrl),
    ];
  return content.find(x => !!x) && hl('div.follow-up', content);
}

const onSuggestionHook: Hooks = onInsert(el => pubsub.emit('round.suggestion', el.textContent));
