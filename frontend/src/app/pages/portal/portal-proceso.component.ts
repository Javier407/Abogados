import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { guardarArchivo } from '../../core/descargas';
import { DetallePortal, DocumentoPortal, etiqueta, tamanoLegible } from '../../core/modelos';

/**
 * Un proceso visto por el cliente: el resumen que publicó su abogado, las próximas audiencias y las novedades
 * (lo que hizo la firma y lo que decidió el juzgado). Sin la agenda interna de términos.
 */
@Component({
  selector: 'app-portal-proceso',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <div class="modulo-container">
      <p><a routerLink="/portal">← Mis procesos</a></p>
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
      @if (cargando) { <p class="form-ayuda">Cargando…</p> }

      @if (detalle; as d) {
        <div class="page-header">
          <p class="mono radicado">{{ d.proceso.radicado }}</p>
          <h1>{{ d.proceso.asunto }}</h1>
          <p>{{ d.proceso.tipoProceso }} · {{ d.proceso.despacho || 'Despacho por asignar' }}</p>
        </div>

        <div class="resumen-bar">
          <span class="resumen-chip">Estado: <strong>{{ etiqueta(d.proceso.estado) }}</strong></span>
          <span class="resumen-chip">Etapa: <strong>{{ etiqueta(d.proceso.etapa) }}</strong></span>
          <span class="resumen-chip">Usted es: <strong>{{ etiqueta(d.proceso.rolCliente) }}</strong></span>
          <span class="resumen-chip">Su abogado: <strong>{{ d.proceso.abogado || '—' }}</strong></span>
        </div>

        <section class="card">
          <div class="card-header"><h2>Lo que le cuenta su abogado</h2></div>
          <div class="card-body">
            @if (d.resumen; as r) {
              <p class="portal-resumen">{{ r.texto }}</p>
              <p class="form-ayuda">Publicado el {{ r.publicadoEn | date:'dd/MM/yyyy' }}</p>
            } @else {
              <p class="form-ayuda">Su abogado aún no ha publicado un resumen de este proceso.</p>
            }
          </div>
        </section>

        @if (d.documentos.length) {
          <section class="card">
            <div class="card-header"><h2>Documentos de su proceso</h2></div>
            <div class="card-body">
              <ul class="lista-pagos">
                @for (doc of d.documentos; track doc.idDocumento) {
                  <li><span>
                    <strong>{{ doc.nombre }}</strong> · {{ etiqueta(doc.categoria) }} · {{ tamano(doc.tamano) }}
                    @if (doc.descripcion) { <br><small>{{ doc.descripcion }}</small> }
                  </span>
                  <button class="btn btn-secondary btn-sm" type="button" (click)="descargar(doc)">Descargar</button></li>
                }
              </ul>
            </div>
          </section>
        }

        @if (d.audiencias.length) {
          <section class="card">
            <div class="card-header"><h2>Próximas audiencias</h2></div>
            <div class="card-body">
              <ul class="lista-pagos">
                @for (a of d.audiencias; track $index) {
                  <li><span>
                    <strong>{{ a.fecha | date:'EEEE d MMMM yyyy' }}</strong>@if (a.hora) { a las {{ a.hora.slice(0, 5) }}}
                    · {{ a.descripcion }}@if (a.lugar) { · {{ a.lugar }}}
                  </span></li>
                }
              </ul>
              <p class="form-ayuda">Su abogado le indicará si debe asistir.</p>
            </div>
          </section>
        }

        <section class="card">
          <div class="card-header"><h2>Novedades del proceso</h2></div>
          <div class="card-body">
            @if (d.novedades.length === 0) {
              <p class="form-ayuda">Aún no hay novedades registradas.</p>
            } @else {
              <ul class="lista-avisos">
                @for (n of d.novedades; track $index) {
                  <li class="aviso">
                    <div class="aviso-texto">
                      <small>{{ n.fecha | date:'dd/MM/yyyy' }} · {{ n.origen }}</small>
                      <strong>{{ n.titulo }}</strong>
                      @if (n.detalle) { <span>{{ n.detalle }}</span> }
                    </div>
                  </li>
                }
              </ul>
            }
          </div>
        </section>
        <p><a routerLink="/portal/pagos">Ver mis pagos y saldo</a></p>
      }
    </div>
  `
})
export class PortalProcesoComponent implements OnInit {
  private api = inject(ApiService);
  private ruta = inject(ActivatedRoute);
  readonly etiqueta = etiqueta;
  readonly tamano = tamanoLegible;

  detalle: DetallePortal | null = null;
  cargando = true;
  error = '';

  descargar(doc: DocumentoPortal): void {
    if (!this.detalle) return;
    this.api.archivoDocumento(this.detalle.proceso.idProceso, doc.idDocumento, true).subscribe({
      next: b => guardarArchivo(b, doc.nombre),
      error: e => this.error = mensajeDeError(e)
    });
  }

  ngOnInit(): void {
    this.api.portalProceso(Number(this.ruta.snapshot.paramMap.get('id'))).subscribe({
      next: d => { this.detalle = d; this.cargando = false; },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }
}
