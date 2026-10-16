import { Injectable, NgZone, OnDestroy, computed, effect, inject, signal } from '@angular/core';

import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { Abogado } from './modelos';

/**
 * La persona que usa la app (la de la sesión iniciada) y sus avisos sin leer.
 * Con sesión de personal se abre el stream de avisos en vivo: cada aviso nuevo suma al contador.
 */
@Injectable({ providedIn: 'root' })
export class SesionService implements OnDestroy {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private zona = inject(NgZone);

  readonly abogados = signal<Abogado[]>([]);
  /** Documento de la persona: para el personal es su cédula (gabinete.personal.id_persona). */
  readonly idPersona = computed(() => this.auth.esPersonal() ? this.auth.idUsuario() : null);
  readonly persona = computed(() => {
    const s = this.auth.sesion();
    const nombres = s?.nombre.split(' ') ?? [];
    return s ? { idPersona: s.idUsuario, nombres: nombres.slice(0, 2).join(' '), apellidos: nombres.slice(2).join(' ') } : null;
  });
  readonly noLeidos = signal(0);
  /** Último aviso recibido en vivo (para mostrarlo un momento). */
  readonly ultimoAviso = signal<{ titulo: string; mensaje: string } | null>(null);

  private stream: EventSource | null = null;

  constructor() {
    try { localStorage.removeItem('gj.idPersona'); } catch { /* resto del selector "Ver como" (antes de F5) */ }
    effect(() => {
      const id = this.idPersona();
      if (id) {
        this.api.abogados(true).subscribe({ next: a => this.abogados.set(a), error: () => this.abogados.set([]) });
      }
      this.conectar(id);
    });
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
    const token = this.auth.token();
    if (!id || !token || typeof EventSource === 'undefined') return;
    // EventSource no permite cabeceras: el token va en la URL solo para esta ruta (el gateway lo quita).
    this.stream = new EventSource(`${this.api.urlAvisosEnVivo(id)}&access_token=${encodeURIComponent(token)}`);
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
