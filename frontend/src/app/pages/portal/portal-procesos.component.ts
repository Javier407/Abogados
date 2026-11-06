import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ProcesoPortal, etiqueta } from '../../core/modelos';

/** Portal del cliente: sus procesos, en qué etapa van, su abogado y la próxima audiencia. */
@Component({
  selector: 'app-portal-procesos',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Mis procesos</h1>
        <p>Bienvenido, {{ auth.sesion()?.nombre }}. Aquí ve en qué va cada uno de sus procesos.</p>
      </div>

      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
      @if (cargando) { <p class="form-ayuda">Cargando sus procesos…</p> }

      @if (!cargando && !error && procesos.length === 0) {
        <div class="empty-state">No tiene procesos registrados con la firma.</div>
      }

      <div class="portal-lista">
        @for (p of procesos; track p.idProceso) {
          <a class="card portal-proceso" [routerLink]="['/portal/procesos', p.idProceso]">
            <div class="card-body">
              <div class="portal-cabeza">
                <span class="mono radicado">{{ p.radicado }}</span>
                <span class="badge" [class.badge-success]="p.estado === 'ACTIVO'"
                      [class.badge-muted]="p.estado !== 'ACTIVO'">{{ etiqueta(p.estado) }}</span>
              </div>
              <h2>{{ p.asunto }}</h2>
              <p class="form-ayuda">{{ p.tipoProceso }} · {{ p.despacho || 'Despacho por asignar' }}</p>
              <dl class="portal-datos">
                <div><dt>Etapa</dt><dd>{{ etiqueta(p.etapa) }}</dd></div>
                <div><dt>Su abogado</dt><dd>{{ p.abogado || '—' }}</dd></div>
                <div><dt>Última novedad</dt><dd>{{ p.ultimaNovedad ? (p.ultimaNovedad | date:'dd/MM/yyyy') : '—' }}</dd></div>
              </dl>
              @if (p.proximaAudiencia; as a) {
                <p class="portal-audiencia">
                  Próxima audiencia: <strong>{{ a.fecha | date:'EEEE d MMMM' }}</strong>
                  @if (a.hora) { a las {{ a.hora.slice(0, 5) }} }
                  @if (a.lugar) { · {{ a.lugar }} }
                </p>
              }
            </div>
          </a>
        }
      </div>
    </div>
  `
})
export class PortalProcesosComponent implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly etiqueta = etiqueta;

  procesos: ProcesoPortal[] = [];
  cargando = true;
  error = '';

  ngOnInit(): void {
    this.api.portalProcesos().subscribe({
      next: p => { this.procesos = p; this.cargando = false; },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }
}
