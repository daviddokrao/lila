import { h, type VNode } from 'snabbdom';

import { onInsert } from 'lib/view';

import { PaneCtrl } from './interfaces';
import { header } from './util';

type Code = string;
type Name = string;

export type Lang = [Code, Name];

export interface LangsData {
  current: Code;
  accepted: Code[];
  list: Lang[];
}

export class LangsCtrl extends PaneCtrl {
  render = (): VNode =>
    h('div.sub.langs', [
      header(i18n.site.language, this.close),
      h(
        'form',
        { attrs: { method: 'post', action: '/translation/select' } },
        this.list().map(([code, name]: Lang) =>
          h(
            'button' +
              (this.data.current === code ? '.current' : '') +
              (this.data.accepted.includes(code) ? '.accepted' : ''),
            {
              attrs: { type: 'submit', name: 'lang', value: code, title: code },
              hook: this.data.current === code ? onInsert(el => el.scrollIntoView({ block: 'center' })) : {},
            },
            name,
          ),
        ),
      ),
    ]);

  private get data() {
    return this.root.data.lang;
  }

  // Accepted languages float to the top, but they must not be listed twice. Upstream
  // concatenates the accepted subset onto the full list; the duplicates went unnoticed
  // there because the full list is ordered by language code, which scatters the copies
  // mid-list. We order it by popularity instead, so both copies land side by side at the
  // very top of the pane.
  private readonly list = (): Lang[] => {
    const isAccepted = (lang: Lang) => this.data.accepted.includes(lang[0]);
    return [...this.data.list.filter(isAccepted), ...this.data.list.filter(l => !isAccepted(l))];
  };
}
