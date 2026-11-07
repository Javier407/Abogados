import { animate, stagger } from 'motion';

/**
 * Animaciones de la interfaz con Motion. Todas respetan «reducir movimiento» del sistema: si está activo,
 * el elemento aparece sin moverse.
 */
const sinMovimiento = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Pestaña en segundo plano: el navegador pausa los cuadros de animación, así que no se anima. */
const oculta = () => typeof document !== 'undefined' && document.hidden;

/** Entrada suave de una lista de elementos, uno tras otro. */
export function entrar(elementos: Element | Element[] | NodeListOf<Element> | null | undefined, retraso = 0.04): void {
  if (!elementos) return;
  const lista = elementos instanceof Element ? [elementos] : Array.from(elementos);
  if (lista.length === 0 || sinMovimiento() || oculta()) return;
  animate(lista, { opacity: [0, 1], y: [8, 0] }, { duration: 0.32, delay: stagger(retraso), ease: [0.22, 1, 0.36, 1] });
}

const DURACION_CONTADOR = 0.8;

/**
 * Contador que sube de 0 hasta el valor; entrega cada paso (p. ej. a una señal que pinta la plantilla).
 * El valor final queda garantizado aunque los cuadros de animación no corran (pestaña en segundo plano o
 * ventana sin pintar): un temporizador lo fija al terminar el tiempo de la animación.
 */
export function contar(hasta: number, alActualizar: (n: number) => void): void {
  if (sinMovimiento() || oculta() || hasta === 0) { alActualizar(hasta); return; }
  let terminado = false;
  const fijar = () => { if (!terminado) { terminado = true; alActualizar(hasta); } };
  animate(0, hasta, {
    duration: DURACION_CONTADOR, ease: [0.22, 1, 0.36, 1],
    onUpdate: v => { if (!terminado) alActualizar(Math.round(v)); },
    onComplete: fijar
  });
  setTimeout(fijar, DURACION_CONTADOR * 1000 + 150);
}
