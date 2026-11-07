import { animate, stagger } from 'motion';

/**
 * Animaciones de la interfaz con Motion. Todas respetan «reducir movimiento» del sistema: si está activo,
 * el elemento aparece sin moverse.
 */
const sinMovimiento = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Entrada suave de una lista de elementos, uno tras otro. */
export function entrar(elementos: Element | Element[] | NodeListOf<Element> | null | undefined, retraso = 0.04): void {
  if (!elementos) return;
  const lista = elementos instanceof Element ? [elementos] : Array.from(elementos);
  if (lista.length === 0 || sinMovimiento()) return;
  animate(lista, { opacity: [0, 1], y: [8, 0] }, { duration: 0.32, delay: stagger(retraso), ease: [0.22, 1, 0.36, 1] });
}

/** Contador que sube hasta el valor (para las cifras del inicio). */
export function contar(el: HTMLElement | null | undefined, hasta: number, formato: (n: number) => string = n => String(Math.round(n))): void {
  if (!el) return;
  if (sinMovimiento() || hasta === 0) { el.textContent = formato(hasta); return; }
  animate(0, hasta, { duration: 0.8, ease: [0.22, 1, 0.36, 1], onUpdate: v => el.textContent = formato(v) });
}
