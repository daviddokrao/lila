// Drawer cua sidebar trang chu v2 (<1020px). Chi lam viec mo/dong — moi wiring
// dasher/theme/ngon ngu/tim kiem trong sidebar da do topBar.ts lo (sidebar mang id="top").
export function initModule(): void {
  const btn = document.querySelector<HTMLButtonElement>('.hv2-mobilebar__menu');
  const aside = document.querySelector<HTMLElement>('aside.hv2-side');
  const scrim = document.querySelector<HTMLElement>('.hv2-scrim');
  if (!btn || !aside) return;

  const isOpen = () => document.body.classList.contains('hv2-drawer-open');
  const setOpen = (open: boolean) => {
    document.body.classList.toggle('hv2-drawer-open', open);
    btn.setAttribute('aria-expanded', String(open));
    if (open) aside.querySelector<HTMLElement>('a, button, input')?.focus();
    else btn.focus();
  };

  btn.addEventListener('click', () => setOpen(!isOpen()));
  scrim?.addEventListener('click', () => setOpen(false));
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && isOpen()) setOpen(false);
  });
  // Bam mot link dieu huong trong drawer thi dong luon (anchor #hv2-play khong tai lai trang)
  aside.addEventListener('click', e => {
    if (isOpen() && (e.target as HTMLElement).closest('a[href]')) setOpen(false);
  });
}
