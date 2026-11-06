import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { ConsultaVigilancia, FUENTES, FuenteVigilancia, RadicadoVigilado } from '../../core/modelos';

/** Radicados vigilados en la Rama Judicial: de dónde se consultan, cómo les fue y simulación para pruebas. */
@Component({
  selector: 'app-vigilancia',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './vigilancia.component.html',
  styleUrl: './vigilancia.component.css'
})
export class VigilanciaComponent implements OnInit {
  private api = inject(ApiService);
  readonly fuentes = FUENTES;

  radicados: RadicadoVigilado[] = [];
  disponibles: FuenteVigilancia[] = ['MANUAL'];
  filtro = '';
  seleccionado: RadicadoVigilado | null = null;
  historial: ConsultaVigilancia[] = [];
  simulada = { fecha: hoy(), actuacion: '', anotacion: '' };

  cargando = false;
  consultando: string | null = null;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.api.fuentesVigilancia().subscribe({ next: f => this.disponibles = f.disponibles, error: () => {} });
    this.cargar();
  }

  get visibles(): RadicadoVigilado[] {
    const f = this.filtro.replace(/\D/g, '');
    return f ? this.radicados.filter(r => r.radicado.includes(f)) : this.radicados;
  }

  contar(fuente: FuenteVigilancia): number {
    return this.radicados.filter(r => r.fuente === fuente).length;
  }

  get enAlerta(): number {
    return this.radicados.filter(r => r.enAlerta).length;
  }

  etiquetaFuente(f: FuenteVigilancia): string {
    return this.fuentes.find(x => x.valor === f)?.etiqueta ?? f;
  }

  cargar(): void {
    this.cargando = true;
    this.api.radicadosVigilados().subscribe({
      next: r => {
        this.radicados = r;
        this.cargando = false;
        if (this.seleccionado) this.seleccionado = r.find(x => x.radicado === this.seleccionado!.radicado) ?? null;
      },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  elegir(r: RadicadoVigilado): void {
    this.seleccionado = r;
    this.mensaje = this.error = '';
    this.api.historialVigilancia(r.radicado).subscribe({ next: h => this.historial = h, error: () => this.historial = [] });
  }

  cambiarFuente(r: RadicadoVigilado, fuente: FuenteVigilancia): void {
    if (fuente === 'PORTAL_HTTP' && !confirm(
      `¿Consultar ${r.radicadoLegible} en el portal de la Rama Judicial?\n\n` +
      'Hágalo solo con radicados reales de clientes de la firma: los radicados de la base de pruebas pueden ' +
      'coincidir con procesos de otras personas.')) {
      this.cargar();
      return;
    }
    const frecuencia = fuente === 'PORTAL_HTTP' ? Math.max(r.frecuenciaHoras, 24) : r.frecuenciaHoras;
    this.api.configurarVigilancia(r.radicado, fuente, frecuencia, r.activo).subscribe({
      next: act => {
        this.mensaje = `${act.radicadoLegible}: fuente ${this.etiquetaFuente(act.fuente)}. `
          + (fuente === 'MANUAL' ? '' : 'La primera consulta solo guarda el historial actual, sin avisar.');
        this.cargar();
        if (this.seleccionado?.radicado === r.radicado) this.elegir(act);
      },
      error: e => { this.error = mensajeDeError(e); this.cargar(); }
    });
  }

  consultar(r: RadicadoVigilado): void {
    this.consultando = r.radicado;
    this.mensaje = this.error = '';
    this.api.consultarRadicado(r.radicado).subscribe({
      next: res => {
        this.consultando = null;
        if (!res.exitosa) this.error = `${r.radicadoLegible}: ${res.error}`;
        else if (res.lineaBase) this.mensaje = `${r.radicadoLegible}: línea base guardada (${res.actuaciones} actuaciones), sin avisos.`;
        else this.mensaje = `${r.radicadoLegible}: ${res.nuevas} actuación(es) nueva(s) de ${res.actuaciones}.`
          + (res.nuevas ? ' Se registraron en el proceso y se avisó al responsable.' : '');
        this.cargar();
        if (this.seleccionado?.radicado === r.radicado) this.elegir(r);
      },
      error: e => { this.consultando = null; this.error = mensajeDeError(e); }
    });
  }

  agregarSimulada(): void {
    const r = this.seleccionado;
    if (!r || this.simulada.actuacion.trim().length < 3) { this.error = 'Escriba la actuación (mínimo 3 letras).'; return; }
    this.api.agregarActuacionSimulada(r.radicado, this.simulada).subscribe({
      next: () => {
        this.mensaje = 'Actuación simulada agregada. Pulse "Consultar ahora" para que la vigilancia la detecte.';
        this.simulada = { fecha: hoy(), actuacion: '', anotacion: '' };
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  resumen(c: ConsultaVigilancia): string {
    const d = c.detalle as Record<string, unknown>;
    if (!c.exitosa) return String(d['error'] ?? 'Falló');
    if (d['lineaBase']) return `Línea base: ${d['actuaciones']} actuaciones`;
    return c.nuevas ? `${c.nuevas} nueva(s): ${d['ultima'] ?? ''}` : 'Sin actuaciones nuevas';
  }
}

function hoy(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
