import { h, type VNode } from 'snabbdom';

import { blurIfPrimaryClick, defined, type Prop } from '@/common';

import { bind } from './snabbdom';

interface CmnToggleBase {
  id: string;
  title?: string;
  disabled?: boolean;
  redraw?: Redraw;
}

export interface CmnToggle extends CmnToggleBase {
  checked: boolean;
  propsChecked?: boolean;
  change(v: boolean): void;
}

export interface CmnToggleProp extends CmnToggleBase {
  prop: Prop<boolean>;
}

export interface CmnToggleWrap extends CmnToggle {
  name: string;
}
export interface CmnToggleWrapProp extends CmnToggleProp {
  name: string;
}

export const cmnToggleProp = (opts: CmnToggleProp): VNode =>
  cmnToggle({
    ...opts,
    checked: opts.prop(),
    change: v => opts.prop(v),
  });

// HungKings — a11y `aria-command-name` (Lighthouse fail cu tren /analysis): span boc
// nay mang role="button" nhung KHONG co ten (label ben trong rong, chi de ve cong tac).
// Control that su la <input type=checkbox> ben trong; span chi la vo trang tri. Bo
// role di la het loi ma khong doi hanh vi — khong CSS nao bat theo [role=button].
export const cmnToggle = (opts: CmnToggle): VNode =>
  h('span.cmn-toggle', [
    h(`input#cmn-tg-${opts.id}`, {
      attrs: { type: 'checkbox', checked: opts.checked, disabled: !!opts.disabled },
      on: {
        click: blurIfPrimaryClick,
      },
      props: defined(opts.propsChecked) ? { checked: opts.propsChecked } : undefined,
      hook: bind('change', e => opts.change((e.target as HTMLInputElement).checked), opts.redraw),
    }),
    h('label', { attrs: { for: `cmn-tg-${opts.id}` } }),
  ]);

export const cmnToggleWrapProp = (opts: CmnToggleWrapProp): VNode =>
  cmnToggleWrap({
    ...opts,
    checked: opts.prop(),
    change: v => opts.prop(v),
  });

export const cmnToggleWrap = (opts: CmnToggleWrap): VNode =>
  h('label.cmn-toggle-wrap', opts.title ? { attrs: { title: opts.title } } : {}, [
    cmnToggle({ ...opts, title: undefined }),
    opts.name,
  ]);
