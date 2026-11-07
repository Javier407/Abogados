import { Injectable, signal } from '@angular/core';

export type Tema = 'claro' | 'oscuro' | 'sistema';

/**
 * Tema claro u oscuro. Por defecto sigue al sistema; la elección se recuerda en este navegador
 * (es una preferencia de quien mira, no un dato del sistema).
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly clave = 'lexora.tema';
  private readonly consulta = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
  readonly tema = signal<Tema>(this.leer());
  readonly oscuro = signal(false);

  constructor() {
    this.aplicar();
    this.consulta?.addEventListener?.('change', () => this.aplicar());
  }

  elegir(tema: Tema): void {
    this.tema.set(tema);
    try { localStorage.setItem(this.clave, tema); } catch { /* navegador sin almacenamiento: no se recuerda */ }
    this.aplicar();
  }

  alternar(): void {
    this.elegir(this.oscuro() ? 'claro' : 'oscuro');
  }

  private leer(): Tema {
    try {
      const t = localStorage.getItem(this.clave);
      if (t === 'claro' || t === 'oscuro' || t === 'sistema') return t;
    } catch { /* sin almacenamiento */ }
    return 'sistema';
  }

  private aplicar(): void {
    const t = this.tema();
    const oscuro = t === 'oscuro' || (t === 'sistema' && !!this.consulta?.matches);
    this.oscuro.set(oscuro);
    document.documentElement.classList.toggle('dark', oscuro);
  }
}
