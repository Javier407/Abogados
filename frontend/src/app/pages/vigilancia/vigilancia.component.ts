import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import {
  ActuacionVista,
  ConsultaVigilancia,
  ETAPAS,
  Proceso,
  Termino,
  etiqueta,
  textoRestantes,
  FUENTES,
  FuenteVigilancia,
  Providencia,
  RadicadoVigilado,
  SugerenciaTermino,
} from '../../core/modelos';

/** Cómo va un proceso, en una línea: su etapa, lo último que pasó y lo próximo que vence. */
interface Avance {
  asunto: string;
  etapa: string;
  estado: string;
  /** Posición de la etapa (para la barrita de avance), de 0 a 1. */
  progreso: number;
  proximo: Termino | null;
  pendientes: number;
}

/**
 * Vigilancia de la Rama Judicial: cómo va cada proceso vigilado (etapa, lo último que pasó en el juzgado y lo
 * próximo que vence) y la salud de la consulta (fuente, última consulta, fallos). En el panel del radicado se ve
 * todo lo que la vigilancia vio en la Rama Judicial, sus providencias y se crea el término sugerido ahí mismo.
 */
@Component({
  selector: 'app-vigilancia',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './vigilancia.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './vigilancia.component.css',
})
export class VigilanciaComponent implements OnInit {
  private api = inject(ApiService);
  readonly fuentes = FUENTES;
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;

  /** Cómo va cada proceso, por id de proceso. */
  avances = new Map<number, Avance>();
  actuaciones: ActuacionVista[] = [];
  cargandoActuaciones = false;

  radicados: RadicadoVigilado[] = [];
  disponibles: FuenteVigilancia[] = ['MANUAL'];
  filtro = '';
  seleccionado: RadicadoVigilado | null = null;
  historial: ConsultaVigilancia[] = [];
  providencias: Providencia[] = [];
  sugerenciasDe: number | null = null;
  sugerencias: SugerenciaTermino[] = [];
  cargandoSugerencias = false;
  simulada = { fecha: hoy(), actuacion: '', anotacion: '' };

  cargando = false;
  consultando: string | null = null;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.api
      .fuentesVigilancia()
      .subscribe({ next: (f) => (this.disponibles = f.disponibles), error: () => {} });
    this.cargar();
  }

  get visibles(): RadicadoVigilado[] {
    const f = this.filtro.replace(/\D/g, '');
    return f ? this.radicados.filter((r) => r.radicado.includes(f)) : this.radicados;
  }

  contar(fuente: FuenteVigilancia): number {
    return this.radicados.filter((r) => r.fuente === fuente).length;
  }

  get enAlerta(): number {
    return this.radicados.filter((r) => r.enAlerta).length;
  }

  etiquetaFuente(f: FuenteVigilancia): string {
    return this.fuentes.find((x) => x.valor === f)?.etiqueta ?? f;
  }

  /** Los datos del proceso (etapa, asunto) y sus términos: viven en el servicio procesos. */
  private cargarAvances(): void {
    forkJoin({ procesos: this.api.procesos({}), terminos: this.api.terminos({}) }).subscribe({
      next: ({ procesos, terminos }) => {
        const pendientes = new Map<number, Termino[]>();
        for (const t of terminos.filter((x) => x.estado === 'PENDIENTE')) {
          pendientes.set(t.idProceso, [...(pendientes.get(t.idProceso) ?? []), t]);
        }
        this.avances = new Map(
          procesos.map((p: Proceso) => {
            const suyos = (pendientes.get(p.idProceso) ?? []).sort((a, b) =>
              a.fechaVencimiento.localeCompare(b.fechaVencimiento),
            );
            const i = (ETAPAS as readonly string[]).indexOf(p.etapa);
            return [
              p.idProceso,
              {
                asunto: p.asunto,
                etapa: p.etapa,
                estado: p.estado,
                progreso: i < 0 ? 0 : (i + 1) / ETAPAS.length,
                proximo: suyos[0] ?? null,
                pendientes: suyos.length,
              },
            ];
          }),
        );
      },
      error: () => (this.avances = new Map()),      // sin esto, la tabla sigue mostrando la vigilancia
    });
  }

  avance(r: RadicadoVigilado): Avance | undefined {
    return this.avances.get(r.idProceso);
  }

  /** Hubo movimiento en el juzgado en los últimos 7 días. */
  novedad(r: RadicadoVigilado): boolean {
    if (!r.ultimaActuacion) return false;
    return Date.now() - Date.parse(r.ultimaActuacion) <= 7 * 86_400_000;
  }

  cargar(): void {
    this.cargando = true;
    this.cargarAvances();
    this.api.radicadosVigilados().subscribe({
      next: (r) => {
        this.radicados = r;
        this.cargando = false;
        if (this.seleccionado)
          this.seleccionado = r.find((x) => x.radicado === this.seleccionado!.radicado) ?? null;
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.cargando = false;
      },
    });
  }

  elegir(r: RadicadoVigilado): void {
    this.seleccionado = r;
    this.mensaje = this.error = '';
    this.actuaciones = [];
    if (r.fuente !== 'MANUAL') {
      this.cargandoActuaciones = true;
      this.api.actuacionesVigiladas(r.radicado).subscribe({
        next: (a) => {
          this.actuaciones = a;
          this.cargandoActuaciones = false;
        },
        error: () => {
          this.actuaciones = [];
          this.cargandoActuaciones = false;
        },
      });
    }
    this.api
      .historialVigilancia(r.radicado)
      .subscribe({ next: (h) => (this.historial = h), error: () => (this.historial = []) });
    this.sugerenciasDe = null;
    this.api.providencias(r.idProceso).subscribe({
      next: (p) =>
        (this.providencias = [...p]
          .sort((a, b) => b.fechaEstado.localeCompare(a.fechaEstado))
          .slice(0, 5)),
      error: () => (this.providencias = []),
    });
  }

  verSugerencias(p: Providencia): void {
    if (this.sugerenciasDe === p.idProvidencia) {
      this.sugerenciasDe = null;
      return;
    }
    this.sugerenciasDe = p.idProvidencia;
    this.sugerencias = [];
    this.cargandoSugerencias = true;
    this.api.sugerencias(p.idProceso, p.idProvidencia).subscribe({
      next: (s) => {
        this.sugerencias = s;
        this.cargandoSugerencias = false;
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.cargandoSugerencias = false;
      },
    });
  }

  crearTermino(p: Providencia, s: SugerenciaTermino): void {
    this.api
      .crearTermino(p.idProceso, {
        descripcion: s.descripcion,
        fechaInicio: s.fechaInicio,
        diasHabiles: s.diasHabiles,
        idProvidencia: p.idProvidencia,
        norma: s.norma,
      })
      .subscribe({
        next: (t) => {
          this.mensaje = `Término creado: «${t.descripcion}», vence el ${t.fechaVencimiento.split('-').reverse().join('/')}.`;
          this.sugerencias = this.sugerencias.filter((x) => x !== s);
        },
        error: (e) => (this.error = mensajeDeError(e)),
      });
  }

  cambiarFuente(r: RadicadoVigilado, fuente: FuenteVigilancia): void {
    if (
      fuente === 'PORTAL_HTTP' &&
      !confirm(
        `¿Consultar ${r.radicadoLegible} en el portal de la Rama Judicial?\n\n` +
          'Hágalo solo con radicados reales de clientes de la firma: los radicados de la base de pruebas pueden ' +
          'coincidir con procesos de otras personas.',
      )
    ) {
      this.cargar();
      return;
    }
    const frecuencia =
      fuente === 'PORTAL_HTTP' ? Math.max(r.frecuenciaHoras, 24) : r.frecuenciaHoras;
    this.api.configurarVigilancia(r.radicado, fuente, frecuencia, r.activo).subscribe({
      next: (act) => {
        this.mensaje =
          `${act.radicadoLegible}: fuente ${this.etiquetaFuente(act.fuente)}. ` +
          (fuente === 'MANUAL'
            ? ''
            : 'La primera consulta solo guarda el historial actual, sin avisar.');
        this.cargar();
        if (this.seleccionado?.radicado === r.radicado) this.elegir(act);
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.cargar();
      },
    });
  }

  consultar(r: RadicadoVigilado): void {
    this.consultando = r.radicado;
    this.mensaje = this.error = '';
    this.api.consultarRadicado(r.radicado).subscribe({
      next: (res) => {
        this.consultando = null;
        if (!res.exitosa) this.error = `${r.radicadoLegible}: ${res.error}`;
        else if (res.lineaBase)
          this.mensaje = `${r.radicadoLegible}: línea base guardada (${res.actuaciones} actuaciones), sin avisos.`;
        else
          this.mensaje =
            `${r.radicadoLegible}: ${res.nuevas} actuación(es) nueva(s) de ${res.actuaciones}.` +
            (res.nuevas ? ' Se registraron en el proceso y se avisó al responsable.' : '');
        this.cargar();
        if (this.seleccionado?.radicado === r.radicado) this.elegir(r);
      },
      error: (e) => {
        this.consultando = null;
        this.error = mensajeDeError(e);
      },
    });
  }

  agregarSimulada(): void {
    const r = this.seleccionado;
    if (!r || this.simulada.actuacion.trim().length < 3) {
      this.error = 'Escriba la actuación (mínimo 3 letras).';
      return;
    }
    this.api.agregarActuacionSimulada(r.radicado, this.simulada).subscribe({
      next: () => {
        this.mensaje =
          'Actuación simulada agregada. Pulse "Consultar ahora" para que la vigilancia la detecte.';
        this.simulada = { fecha: hoy(), actuacion: '', anotacion: '' };
      },
      error: (e) => (this.error = mensajeDeError(e)),
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
