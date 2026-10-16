import { Component, effect, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Aviso } from '../../core/modelos';
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
  cargando = false;
  error = '';

  constructor() {
    // Recarga al cambiar de persona y cuando llega un aviso en vivo.
    effect(() => {
      this.sesion.idPersona();
      this.sesion.noLeidos();
      this.cargar();
    });
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
