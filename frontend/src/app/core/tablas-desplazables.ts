import { DestroyRef, inject } from '@angular/core';

/**
 * Accesibilidad (WCAG 2.1.1): una tabla que no cabe se desplaza de lado dentro de .tabla-wrapper. Si la
 * tabla no tiene botones ni enlaces, quien usa solo el teclado no podría desplazarla. Mientras desborde, su
 * contenedor se vuelve enfocable (Tab y luego las flechas) y recibe un nombre: el título más cercano.
 * Cuando deja de desbordar se quita, para no sumar paradas de Tab inútiles. Cubre todas las pantallas sin
 * tocar sus plantillas.
 */
export function vigilarTablasDesplazables(): void {
  if (typeof ResizeObserver === 'undefined' || typeof MutationObserver === 'undefined') return;
  const destruir = inject(DestroyRef);
  const vistas = new WeakSet<Element>();

  const revisar = (el: HTMLElement) => {
    const desborda = el.scrollWidth > el.clientWidth + 1;
    if (desborda && !el.hasAttribute('tabindex')) {
      el.setAttribute('tabindex', '0');
      el.setAttribute('role', 'region');
      el.setAttribute('aria-label', nombre(el));
      el.dataset['desplazable'] = '1';
    } else if (!desborda && el.dataset['desplazable']) {
      el.removeAttribute('tabindex');
      el.removeAttribute('role');
      el.removeAttribute('aria-label');
      delete el.dataset['desplazable'];
    }
  };

  // Se observa el contenedor y su tabla; en ambos casos se revisa el contenedor.
  const tamano = new ResizeObserver(entradas => entradas.forEach(e => {
    const contenedor = (e.target as HTMLElement).closest<HTMLElement>('.tabla-wrapper');
    if (contenedor) revisar(contenedor);
  }));
  // Cada cambio de la página (llegan datos, se cambia de pantalla) revisa todas las tablas, una vez por tanda.
  let pendiente = false;
  const revisarTodas = () => {
    if (pendiente) return;
    pendiente = true;
    queueMicrotask(() => {
      pendiente = false;
      document.querySelectorAll<HTMLElement>('.tabla-wrapper').forEach(el => {
        if (!vistas.has(el)) {
          vistas.add(el);
          tamano.observe(el);
          const tabla = el.querySelector('table');
          if (tabla) tamano.observe(tabla);   // el contenido crece al llegar los datos
        }
        revisar(el);
      });
    });
  };

  const cambios = new MutationObserver(revisarTodas);
  cambios.observe(document.body, { childList: true, subtree: true });
  addEventListener('resize', revisarTodas);
  revisarTodas();
  destruir.onDestroy(() => { cambios.disconnect(); tamano.disconnect(); removeEventListener('resize', revisarTodas); });
}

/** Nombre de la región: el título de la tarjeta o de la página más cercano, o uno genérico. */
function nombre(el: HTMLElement): string {
  // Sube por los contenedores hasta encontrar uno con título (tarjeta, sección o la página).
  for (let c = el.parentElement?.closest<HTMLElement>('.card, section, .modulo-container'); c;
       c = c.parentElement?.closest<HTMLElement>('.card, section, .modulo-container')) {
    const titulo = c.querySelector('h2, h1')?.textContent?.trim();
    if (titulo) return `Tabla: ${titulo} (desplazable)`;
  }
  return 'Tabla desplazable';
}
