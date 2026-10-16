import { Component, effect, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Aviso, EstadoTelegram, VinculoTelegram } from '../../core/modelos';
import { SesionService } from '../../core/sesion.service';

/** Avisos dentro de la app de la persona elegida en "Ver como". */
@Component({
  selector: 'app-avisos',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './avisos.component.html'
})
export class AvisosComponent {
  private api = inject(ApiService);
  readonly sesion = inject(SesionService);

  avisos: Aviso[] = [];
  telegram: EstadoTelegram | null = null;
  vinculo: VinculoTelegram | null = null;
  cargando = false;
  error = '';

  constructor() {
    // Recarga al cambiar de persona y cuando llega un aviso en vivo.
    effect(() => {
      this.sesion.idPersona();
      this.sesion.noLeidos();
      this.cargar();
    });
    effect(() => this.cargarTelegram(this.sesion.idPersona()));
  }

  cargar(): void {
    const id = this.sesion.idPersona();
    if (!id) { this.avisos = []; return; }
    this.cargando = true;
    this.api.avisos(id).subscribe({
      next: a => { this.avisos = a; this.cargando = false; },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  cargarTelegram(id: number | null): void {
    this.vinculo = null;
    if (!id) { this.telegram = null; return; }
    this.api.estadoTelegram(id).subscribe({ next: t => this.telegram = t, error: () => this.telegram = null });
  }

  vincularTelegram(): void {
    this.api.vincularTelegram(this.sesion.idPersona()!).subscribe({
      next: v => this.vinculo = v,
      error: e => this.error = mensajeDeError(e)
    });
  }

  desvincularTelegram(): void {
    if (!confirm('¿Dejar de recibir avisos por Telegram?')) return;
    this.api.desvincularTelegram(this.sesion.idPersona()!).subscribe({
      next: () => this.cargarTelegram(this.sesion.idPersona()),
      error: e => this.error = mensajeDeError(e)
    });
  }

  leer(a: Aviso): void {
    if (a.leida) return;
    this.api.marcarAvisoLeido(a.idNotificacion, this.sesion.idPersona()!).subscribe({
      next: () => this.sesion.refrescarNoLeidos(),
      error: e => this.error = mensajeDeError(e)
    });
  }

  leerTodos(): void {
    this.api.marcarAvisosLeidos(this.sesion.idPersona()!).subscribe({
      next: () => this.sesion.refrescarNoLeidos(),
      error: e => this.error = mensajeDeError(e)
    });
  }
}
