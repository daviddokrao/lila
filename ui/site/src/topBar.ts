import { blurIfEscape, blurIfPrimaryClick, memoize } from 'lib';
import { clamp } from 'lib/algo';
import { isTouchDevice } from 'lib/device';
import { pubsub } from 'lib/pubsub';
import { wsSend } from 'lib/socket';
import { spinnerHtml } from 'lib/view';

import { loadCssPath, loadEsm } from './asset';

export default function () {
  const top = document.getElementById('top')!;

  const initiatingHtml = `<div class="initiating">${spinnerHtml}</div>`,
    isVisible = (selector: string) => {
      const el = document.querySelector(selector),
        display = el && window.getComputedStyle(el).display;
      return display && display !== 'none';
    };

  // On touchscreens, clicking the top menu element expands it. There's no top link.
  // Only for mq-topnav-visible in ui/lib/css/abstract/_media-queries.scss
  if ('ontouchstart' in window && window.matchMedia('(min-width: 1020px)').matches)
    $('#topnav section > a').removeAttr('href');

  const blockBodyScroll = (e: Event) => {
    // on iOS, overflow: hidden isn't sufficient
    if (!document.getElementById('topnav')!.contains(e.target as HTMLElement)) e.preventDefault();
  };

  $('#tn-tg').on('change', e => {
    const menuOpen = (e.target as HTMLInputElement).checked;
    if (menuOpen) {
      document.body.addEventListener('touchmove', blockBodyScroll, { passive: false });
      $(e.target).addClass('opened');
    } else {
      document.body.removeEventListener('touchmove', blockBodyScroll);
      setTimeout(() => $(e.target).removeClass('opened'), 200);
    }
    document.body.classList.toggle('masked', menuOpen);
  });

  // Đóng bảng khi bấm ra ngoài. Tách ra khỏi handler `.toggle` vì các nút tắt vào
  // pane dasher (data-dasher-pane) cũng cần đúng cơ chế này — trước 04/08 chúng chỉ
  // biết MỞ, nên bảng ngôn ngữ mở ra rồi không có cách nào đóng lại (David báo).
  const dismissOnOutsideClick = ($p: Cash) =>
    setTimeout(() => {
      const handler = (e: Event) => {
        const target = e.target as HTMLElement;
        if (!target.isConnected || $p[0]?.contains(target)) return;
        $p.removeClass('shown');
        $('html').off('click', handler);
      };
      $('html').on('click', handler);
    }, 10);

  $(top).on('click', '.toggle', function (this: HTMLElement, e: Event) {
    blurIfPrimaryClick(e);
    const $p = $(this).parent().toggleClass('shown');
    $p.siblings('.shown').removeClass('shown');
    dismissOnOutsideClick($p);
    return false;
  });

  {
    // challengeApp
    let instance: Promise<any> | undefined;
    const $toggle = $('#challenge-toggle'),
      $countSpan = $toggle.find('span');
    $toggle.one('mouseover click', () => load());
    const load = function (data?: any) {
      if (instance) return;
      const $el = $('#challenge-app').html(initiatingHtml);
      loadCssPath('challenge');
      instance = loadEsm('challenge', {
        init: {
          el: $el[0],
          data,
          show() {
            if (!isVisible('#challenge-app')) $toggle.trigger('click');
          },
          setCount(nb: number) {
            const newTitle = $countSpan.attr('title')!.replace(/\d+/, nb.toString());
            $countSpan.data('count', nb).attr('title', newTitle).attr('aria-label', newTitle);
          },
          pulse() {
            $toggle.addClass('pulse');
          },
        },
      });
    };
    pubsub.on('socket.in.challenges', async data => {
      if (!instance) load(data);
      else (await instance).update(data);
    });

    pubsub.on('challenge-app.open', () => $toggle.trigger('click'));
  }

  {
    // notifyApp
    let instance: Promise<any> | undefined;
    const $toggle = $('#notify-toggle'),
      $countSpan = $toggle.find('span'),
      selector = '#notify-app';

    const load = (data?: any) => {
      if (instance) return;
      const $el = $('#notify-app').html(initiatingHtml);
      loadCssPath('notify');
      instance = loadEsm('notify', {
        init: {
          el: $el.empty()[0],
          data,
          isVisible: () => isVisible(selector),
          updateUnread(nb: number | 'increment') {
            const existing = ($countSpan.data('count') as number) || 0;
            if (nb === 'increment') nb = existing + 1;
            if (this.isVisible()) nb = 0;
            const newTitle = $countSpan.attr('title')!.replace(/\d+/, nb.toString());
            $countSpan.data('count', nb).attr('title', newTitle).attr('aria-label', newTitle);
            return nb && nb !== existing;
          },
          show() {
            if (!isVisible(selector)) $toggle.trigger('click');
          },
          setNotified() {
            wsSend('notified');
          },
          pulse() {
            $toggle.addClass('pulse');
          },
        },
      });
    };

    $toggle
      .one('mouseover click', () => load())
      .on('click', () => {
        if ('Notification' in window) Notification.requestPermission();
        setTimeout(async () => {
          if (instance && isVisible(selector)) (await instance).onShow();
        }, 200);
      });

    pubsub.on('socket.in.notifications', async data => {
      if (!instance) load(data);
      else (await instance).update(data);
    });
    pubsub.on('notify-app.set-read', async user => {
      if (!instance) load();
      else (await instance).setMsgRead(user);
    });
  }

  {
    // dasher
    // `ctrl` giữ instance đã nạp để đọc ĐƯỢC pane đang mở một cách ĐỒNG BỘ. Không thể
    // nhớ pane cuối bằng biến riêng: nút "quay lại" trong pane tự gọi setMode('links'),
    // nên biến tự quản sẽ lệch và nút ngôn ngữ đóng bảng thay vì mở lại đúng pane.
    let ctrl: any;
    const load = memoize(async () => (ctrl = await loadEsm<any>('dasher')));
    $('#top .dasher .toggle').one('mouseover click', function (this: HTMLElement) {
      $(this).removeAttr('href');
      loadCssPath('dasher');
      load();
    });

    // Bánh răng LUÔN mở ở pane gốc. Dasher nhớ pane dùng lần trước, nên không có dòng
    // này thì hễ vừa dùng nút cờ xong là bấm bánh răng lại ra danh sách ngôn ngữ — tức
    // ngôn ngữ VẪN nằm trong nút cài đặt, đúng thứ David bảo bỏ vì trùng lặp.
    // setTimeout(0): handler gắn thẳng trên nút chạy TRƯỚC handler uỷ quyền `.toggle`
    // của #top, nên lúc này class `shown` còn là trạng thái CŨ. Phải đợi hết vòng sự
    // kiện mới đọc được nó vừa mở hay vừa đóng.
    $('#top .dasher .toggle').on('click', () =>
      setTimeout(() => {
        if ($('#top .dasher').hasClass('shown')) ctrl?.close();
      }, 0),
    );

    // Header shortcuts that only exist to reach a dasher pane in one click. The dasher
    // stays the single implementation; this just opens it on the right pane.
    $('#top [data-dasher-pane]').on('click', async function (this: HTMLElement, e: Event) {
      e.preventDefault();
      // stopPropagation là BẮT BUỘC, đã đo chứ không phải đề phòng: nút tắt nằm NGOÀI
      // `.dasher`, nên nếu để sự kiện bay tiếp lên `html` thì handler "bấm ra ngoài"
      // đăng ký từ lần mở TRƯỚC sẽ chạy sau ta và đóng ngay bảng ta vừa mở. Triệu chứng
      // đúng bằng lỗi cũ (bấm không ăn) nhưng chỉ lộ ở một đường: mở pane ngôn ngữ →
      // bấm "quay lại" → bấm nút cờ. Handler `.toggle` của upstream thoát được vì nó
      // `return false`, thứ mà cash dịch thành preventDefault + stopPropagation.
      e.stopPropagation();
      const $dasher = $('#top .dasher');
      if (!$dasher.length) return;
      const pane = this.dataset.dasherPane as any;
      // Bấm lần hai vào ĐÚNG nút đang mở thì đóng lại. Trước 04/08 nhánh này chỉ có
      // addClass('shown') nên nút trông như chết ở chiều về (David báo).
      if ($dasher.hasClass('shown') && ctrl?.mode() === pane) {
        $dasher.removeClass('shown');
        return;
      }
      loadCssPath('dasher');
      $dasher.addClass('shown').siblings('.shown').removeClass('shown');
      dismissOnOutsideClick($dasher);
      (await load())?.setMode(pane);
    });

    // The theme shortcut posts the same preference the dasher does, so it goes through
    // the dasher too rather than reimplementing the apply/persist pair. Falling back to
    // a normal submit would work, but would cost a full page load.
    $('#top .site-buttons__bg').on('submit', async function (this: HTMLFormElement, e: Event) {
      const next = $(this).find('input[name="bg"]').val() as string;
      if (!next || !$('#top .dasher').length) return; // let the browser post it
      e.preventDefault();
      loadCssPath('dasher');
      const other = next === 'dark' ? 'light' : 'dark',
        $button = $(this).find('button'),
        wasLabel = $button.attr('title'),
        nextLabel = $button.data('label-alt');
      $button
        .attr({ title: nextLabel, 'aria-label': nextLabel })
        .data('label-alt', wasLabel)
        .removeClass(`bg-toggle--${next}`)
        .addClass(`bg-toggle--${other}`);
      $(this).find('input[name="bg"]').val(other);
      await (await load())?.background.set(next);
    });
  }

  {
    // cli
    const $wrap = $('#clinput');
    if (!$wrap.length) return;
    const $input = $wrap.find('input');
    let booted = false,
      clicked = false;
    const boot = () => {
      if (booted) return;
      booted = true;
      loadEsm('cli', { init: { input: $input[0] } }).catch(() => (booted = false));
    };
    $input.on({
      keydown: blurIfEscape,
      click: () => {
        clicked = true;
      },
      blur() {
        clicked = false;
        $input.val('');
        $('body').removeClass('clinput');
      },
      focus() {
        boot();
        $('body').addClass('clinput');
      },
    });
    $wrap.find('a').on({
      mouseover: boot,
      click() {
        $('body').hasClass('clinput') ? $input[0]!.blur() : $input[0]!.focus();
      },
    });
    $wrap.on('mouseenter', () => {
      if ($input[0] !== document.activeElement) $input[0]!.focus();
    });
    $wrap.on('mouseleave', () => {
      if (!clicked && !$input.val()) $input[0]!.blur();
    });
    site.mousetrap
      .bind('/', () => {
        $input.val('/');
        $input[0]!.focus();
        top.classList.remove('hide');
      })
      .bind('s', () => {
        $input[0]!.focus();
        top.classList.remove('hide');
      });
  }

  {
    // stick top bar
    let lastY = window.scrollY;
    if (lastY > 0) top.classList.add('scrolled');

    window.addEventListener(
      'scroll',
      () => {
        const y = window.scrollY;
        top.classList.toggle('scrolled', y > 0);
        if (y > lastY + 10) top.classList.add('hide');
        else if (y <= clamp(lastY - 20, { min: 0, max: document.body.scrollHeight - window.innerHeight }))
          top.classList.remove('hide');
        else return;

        lastY = Math.max(0, y);
      },
      { passive: true },
    );

    if (!isTouchDevice() || site.blindMode || !document.querySelector('main.analyse')) return;

    // double tap to align top of board with viewport
    document.querySelector<HTMLElement>('.main-board')?.addEventListener(
      'dblclick',
      e => {
        lastY = -9999;
        window.scrollTo({
          top: parseInt(window.getComputedStyle(document.body).getPropertyValue('---site-header-height')),
          behavior: 'instant',
        });
        e.preventDefault();
      },
      { passive: true },
    );
  }
}
