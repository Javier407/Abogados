import { Injectable, NgZone, OnDestroy, computed, effect, inject, signal } from '@angular/core';

import { ApiService } from './api.service';
import { Abogado } from './modelos';

const CLAVE = 'gj.idPersona';

/**
 * Quién está usando la app y sus avisos sin leer.
 *
 * Hasta que exista el login (F5), la persona se elige en el menú ("Ver como") y se recuerda en este
 * navegador. Con la persona elegida se abre el stream de avisos en vivo: cada aviso nuevo suma al contador.
 */
@Injectable({ providedIn: 'root' })
export class SesionService implements OnDestroy {
  private api = inject(ApiService);
  private zona = inject(NgZone);

  readonly abogados = signal<Abogado[]>([]);
  readonly idPersona = signal<number | null>(leer());
  readonly persona = computed(() => this.abogados().find(a => a.idPersona === this.idPersona()) ?? null);
  readonly noLeidos = signal(0);
  /** Último aviso recibido en vivo (para mostrarlo un momento). */
  readonly ultimoAviso = signal<{ titulo: string; mensaje: string } | null>(null);

  private stream: EventSource | null = null;

  constructor() {
    this.api.abogados(true).subscribe({ next: a => this.abogados.set(a), error: () => this.abogados.set([]) });
    effect(() => this.conectar(this.idPersona()));
  }

  elegir(idPersona: number | null): void {
    try {
      if (idPersona) localStorage.setItem(CLAVE, String(idPersona));
      else localStorage.removeItem(CLAVE);
    } catch { /* navegador sin almacenamiento: se usa solo en esta pestaña */ }
    this.idPersona.set(idPersona);
  }

  refrescarNoLeidos(): void {
    const id = this.idPersona();
    if (!id) { this.noLeidos.set(0); return; }
    this.api.avisosNoLeidos(id).subscribe({ next: r => this.noLeidos.set(r.noLeidas), error: () => {} });
  }

  private conectar(id: number | null): void {
    this.stream?.close();
    this.stream = null;
    this.refrescarNoLeidos();
    if (!id || typeof EventSource === 'undefined') return;
    this.stream = new EventSource(this.api.urlAvisosEnVivo(id));
    this.stream.addEventListener('aviso', e => this.zona.run(() => {
      const dato = JSON.parse((e as MessageEvent).data);
      this.noLeidos.update(n => n + 1);
      this.ultimoAviso.set({ titulo: dato.titulo, mensaje: dato.mensaje });
      setTimeout(() => this.ultimoAviso.set(null), 8000);
    }));
  }

  ngOnDestroy(): void {
    this.stream?.close();
  }
}

function leer(): number | null {
  try {
    const v = Number(localStorage.getItem(CLAVE));
    return v > 0 ? v : null;
  } catch {
    return null;
  }
}
