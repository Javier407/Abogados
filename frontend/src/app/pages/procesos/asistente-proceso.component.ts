import { Component, NgZone, OnDestroy, OnInit, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../../core/api.service';
import { AsistenteService } from '../../core/asistente.service';
import { EstadoAsistente, TipoBorrador } from '../../core/modelos';

/** Borradores con el modelo local: contestación de la demanda o resumen del proceso para el cliente. */
@Component({
  selector: 'app-asistente-proceso',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="card">
      <div class="card-header"><h2>Asistente de redacción</h2></div>
      <div class="card-body">
        <p class="form-ayuda">
          Modelo local {{ estado?.modelo ?? '' }}: los datos del proceso no salen del servidor.
          @if (estado && !estado.disponible) { <strong>No disponible ({{ estado.detalle }}): se entregará la plantilla.</strong> }
          Es un borrador: revíselo completo antes de usarlo.
        </p>
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label" for="tipoBorrador">Borrador</label>
            <select id="tipoBorrador" class="form-input" [(ngModel)]="tipo" [disabled]="redactando">
              <option value="CONTESTACION">Contestación de la demanda</option>
              <option value="RESUMEN_CLIENTE">Resumen del proceso para el cliente</option>
            </select>
          </div>
          <div class="form-group form-group-full">
            <label class="form-label" for="instrucciones">Indicaciones (opcional)</label>
            <input id="instrucciones" class="form-input" [(ngModel)]="instrucciones" maxlength="800" [disabled]="redactando"
              placeholder="Ej: proponer la excepción de contrato no cumplido" />
          </div>
        </div>
        <div class="form-actions">
          @if (!redactando) {
            <button class="btn btn-primary" type="button" (click)="generar()">Generar borrador</button>
          } @else {
            <button class="btn btn-secondary" type="button" (click)="detener()">Detener</button>
            <span class="form-ayuda">Redactando{{ segundos ? ' · ' + segundos + ' s' : '' }}… el modelo corre sin GPU y puede tardar unos minutos.</span>
          }
        </div>

        @if (aviso) { <div class="alerta alerta-error" style="margin-top: 12px">{{ aviso }}</div> }
        @if (texto || redactando) {
          <textarea class="form-input borrador" [(ngModel)]="texto" [readonly]="redactando" rows="22"
            aria-label="Borrador generado"></textarea>
          <div class="form-actions">
            <button class="btn btn-secondary btn-sm" type="button" (click)="copiar()" [disabled]="redactando || !texto">
              {{ copiado ? 'Copiado' : 'Copiar' }}
            </button>
            <button class="btn btn-secondary btn-sm" type="button" (click)="descargar()" [disabled]="redactando || !texto">
              Descargar .txt
            </button>
            @if (resumen) { <span class="form-ayuda">{{ resumen }}</span> }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`.borrador { margin-top: 14px; font-family: Georgia, 'Times New Roman', serif; font-size: 0.92rem; line-height: 1.55; white-space: pre-wrap; }`]
})
export class AsistenteProcesoComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private asistente = inject(AsistenteService);
  private zona = inject(NgZone);
  readonly idProceso = input.required<number>();
  readonly radicado = input<string>('');

  estado: EstadoAsistente | null = null;
  tipo: TipoBorrador = 'CONTESTACION';
  instrucciones = '';
  texto = '';
  aviso = '';
  resumen = '';
  redactando = false;
  copiado = false;
  segundos = 0;
  private cancelar: AbortController | null = null;
  private reloj: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.api.estadoAsistente().subscribe({ next: e => this.estado = e, error: () => this.estado = null });
  }

  async generar(): Promise<void> {
    this.texto = this.aviso = this.resumen = '';
    this.redactando = true;
    this.segundos = 0;
    this.reloj = setInterval(() => this.segundos++, 1000);
    this.cancelar = new AbortController();
    try {
      await this.asistente.redactar(this.idProceso(), this.tipo, this.instrucciones, e => this.zona.run(() => {
        if (e.evento === 'texto') this.texto += e.texto ?? '';
        else if (e.evento === 'aviso') this.aviso = e.mensaje ?? '';
        else if (e.evento === 'fin') {
          this.resumen = e.conModelo ? `Redactado por el modelo en ${e.segundos} s.` : 'Plantilla sin modelo.';
        }
      }), this.cancelar.signal);
    } catch (err) {
      if (!(err instanceof DOMException && err.name === 'AbortError')) {
        this.aviso = err instanceof Error ? err.message : 'No se pudo generar el borrador.';
      }
    } finally {
      this.terminar();
    }
  }

  detener(): void {
    this.cancelar?.abort();
    this.resumen = 'Detenido: puede editar lo que alcanzó a redactar.';
  }

  async copiar(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.texto);
      this.copiado = true;
      setTimeout(() => this.copiado = false, 2000);
    } catch {
      this.aviso = 'El navegador no permitió copiar; seleccione el texto y cópielo a mano.';
    }
  }

  descargar(): void {
    const nombre = `${this.tipo === 'CONTESTACION' ? 'contestacion' : 'resumen'}-${this.radicado() || this.idProceso()}.txt`;
    const url = URL.createObjectURL(new Blob([this.texto], { type: 'text/plain;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.click();
    URL.revokeObjectURL(url);
  }

  private terminar(): void {
    this.redactando = false;
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
  }

  ngOnDestroy(): void {
    this.cancelar?.abort();
    if (this.reloj) clearInterval(this.reloj);
  }
}
