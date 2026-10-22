import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { EstadoServidor, PuntoServidor } from '../../core/modelos';

interface Grafica { linea: string; area: string; min: number; max: number; ultimo: number; caidas: number[]; }

const ANCHO = 600, ALTO = 120;

/**
 * Estado del servidor (F12, solo ADMIN): lo que guarda el vigía cada 5 minutos. Última revisión, disponibilidad de
 * la app, disco y memoria en el periodo, e incidentes (desde que aparecen hasta que se resuelven).
 */
@Component({
  selector: 'app-servidor',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Servidor</h1>
        <p>Lo que revisa el vigía cada 5 minutos: contenedores, disco, memoria, respaldos, colas y la app por HTTPS.
          Los avisos llegan por Telegram; aquí queda la historia.</p>
      </div>
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }

      <div class="resumen-bar" role="group" aria-label="Periodo">
        @for (d of periodos; track d) {
          <button class="btn btn-sm" type="button" [class.btn-primary]="dias === d" [class.btn-secondary]="dias !== d"
                  [attr.aria-pressed]="dias === d" (click)="cambiar(d)">{{ d === 1 ? 'Hoy (24 h)' : d + ' días' }}</button>
        }
      </div>

      @if (estado; as e) {
        @if (!e.vigiaActivo) {
          <div class="alerta alerta-error">El vigía no ha revisado en los últimos 15 minutos
            @if (e.ultima) { (última revisión: {{ e.ultima.momento | date:'d MMM, h:mm a' }}) }.
            Revise el timer con <span class="mono">systemctl status abogados-vigia.timer</span> en la VM.</div>
        }
        <div class="kpis">
          <div class="card kpi">
            <span class="kpi-titulo">Estado ahora</span>
            @if (e.ultima; as u) {
              <strong [class.texto-ok]="u.problemas === 0" [class.texto-mal]="u.problemas > 0">
                {{ u.problemas === 0 ? 'Todo en orden' : u.problemas + ' problema(s)' }}</strong>
              <small>{{ u.momento | date:'d MMM, h:mm a' }}</small>
            } @else { <strong>Sin revisiones</strong> }
          </div>
          <div class="card kpi">
            <span class="kpi-titulo">Disponibilidad de la app</span>
            <strong>{{ e.disponibilidad === null ? '—' : (e.disponibilidad | number:'1.0-2') + ' %' }}</strong>
            <small>{{ e.revisiones }} revisiones en el periodo</small>
          </div>
          <div class="card kpi">
            <span class="kpi-titulo">Disco</span>
            <strong>{{ e.ultima ? e.ultima.disco + ' %' : '—' }}</strong><small>aviso desde 85 %</small>
          </div>
          <div class="card kpi">
            <span class="kpi-titulo">Memoria disponible</span>
            <strong>{{ e.ultima ? (e.ultima.memoriaMb | number) + ' MB' : '—' }}</strong><small>aviso por debajo de 500 MB</small>
          </div>
          <div class="card kpi">
            <span class="kpi-titulo">Último respaldo</span>
            <strong>{{ e.ultima?.respaldoHoras == null ? '—' : 'hace ' + e.ultima!.respaldoHoras + ' h' }}</strong><small>aviso desde 26 h</small>
          </div>
        </div>

        @if (e.ultima?.detalle?.length) {
          <div class="alerta alerta-error"><strong>Problemas abiertos:</strong>
            <ul class="lista-problemas">@for (p of e.ultima!.detalle; track $index) { <li>{{ p }}</li> }</ul></div>
        }

        <div class="graficas">
          @for (g of [{ titulo: 'Uso del disco (%)', datos: disco }, { titulo: 'Memoria disponible (MB)', datos: memoria }]; track g.titulo) {
            <section class="card">
              <div class="card-header"><h2>{{ g.titulo }}</h2></div>
              <div class="card-body">
                @if (g.datos; as d) {
                  <svg [attr.viewBox]="'0 0 ' + ancho + ' ' + alto" class="grafica" preserveAspectRatio="none" role="img"
                       [attr.aria-label]="g.titulo + ': entre ' + d.min + ' y ' + d.max + ', último ' + d.ultimo">
                    @for (x of d.caidas; track $index) { <rect [attr.x]="x - 2" y="0" width="4" [attr.height]="alto" class="caida" /> }
                    <path [attr.d]="d.area" class="area" />
                    <path [attr.d]="d.linea" class="linea" />
                  </svg>
                  <div class="eje"><span>{{ e.serie[0].momento | date:'d MMM, h a' }}</span>
                    <span>mín {{ d.min | number }} · máx {{ d.max | number }}</span>
                    <span>{{ e.serie[e.serie.length - 1].momento | date:'d MMM, h a' }}</span></div>
                } @else { <p class="empty-state">Aún no hay revisiones en este periodo.</p> }
              </div>
            </section>
          }
        </div>
        @if (disco?.caidas?.length) { <p class="form-ayuda">Las franjas rojas marcan cuando la app no respondió por HTTPS.</p> }

        <section class="card">
          <div class="card-header"><h2>Incidentes</h2></div>
          <div class="card-body">
            <div class="tabla-wrapper"><table class="tabla">
              <thead><tr><th>Problema</th><th>Desde</th><th>Hasta</th><th>Duración</th></tr></thead>
              <tbody>
                @for (i of e.incidentes; track $index) {
                  <tr>
                    <td class="titulo-celda">@if (i.abierto) { <span class="badge badge-danger">Abierto</span> } {{ i.mensaje }}</td>
                    <td class="fecha-celda">{{ i.inicio | date:'d MMM, h:mm a' }}</td>
                    <td class="fecha-celda">{{ i.fin ? (i.fin | date:'d MMM, h:mm a') : '—' }}</td>
                    <td>{{ duracion(i.minutos) }}</td>
                  </tr>
                } @empty { <tr><td colspan="4" class="empty-state">Sin incidentes en el periodo.</td></tr> }
              </tbody>
            </table></div>
          </div>
        </section>
      } @else if (!error) { <p class="empty-state">Cargando…</p> }
    </div>
  `,
  styles: [`
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; margin-bottom: 18px; }
    .kpi { padding: 14px 16px; display: grid; gap: 4px; }
    .kpi-titulo { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: .4px; }
    .kpi strong { font-size: 22px; font-family: var(--display); color: var(--ink); }
    .kpi small { color: var(--muted); font-size: 12px; }
    .texto-ok { color: var(--ok) !important; } .texto-mal { color: var(--mahogany) !important; }
    .graficas { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px; }
    .grafica { width: 100%; height: 140px; display: block; }
    .grafica .linea { fill: none; stroke: var(--blue); stroke-width: 2; vector-effect: non-scaling-stroke; }
    .grafica .area { fill: var(--blue-soft); }
    .grafica .caida { fill: var(--mahogany); opacity: .35; }
    .eje { display: flex; justify-content: space-between; gap: 8px; font-size: 12px; color: var(--muted); margin-top: 6px; flex-wrap: wrap; }
    .lista-problemas { margin: 6px 0 0; padding-left: 18px; }
  `]
})
export class ServidorComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private reloj?: ReturnType<typeof setInterval>;

  readonly periodos = [1, 7, 30, 90];
  readonly ancho = ANCHO;
  readonly alto = ALTO;
  dias = 7;
  estado: EstadoServidor | null = null;
  disco: Grafica | null = null;
  memoria: Grafica | null = null;
  error = '';

  ngOnInit(): void {
    this.cargar();
    this.reloj = setInterval(() => this.cargar(), 5 * 60 * 1000);   // al ritmo del vigía
  }

  ngOnDestroy(): void {
    clearInterval(this.reloj);
  }

  cambiar(d: number): void {
    this.dias = d;
    this.cargar();
  }

  cargar(): void {
    this.api.estadoServidor(this.dias).subscribe({
      next: e => {
        this.estado = e;
        this.error = '';
        this.disco = grafica(e.serie, p => p.disco, 0, 100);
        this.memoria = grafica(e.serie, p => p.memoriaMb, 0);
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  duracion(minutos: number): string {
    if (minutos < 60) return `${minutos} min`;
    const h = Math.floor(minutos / 60), m = minutos % 60;
    return h < 48 ? `${h} h ${m} min` : `${Math.round(h / 24)} días`;
  }
}

/** Línea y área de una serie en el lienzo ANCHO × ALTO. minFijo/maxFijo: escala fija (p. ej. 0–100 %). */
function grafica(serie: PuntoServidor[], valor: (p: PuntoServidor) => number, minFijo?: number, maxFijo?: number): Grafica | null {
  if (serie.length === 0) return null;
  const valores = serie.map(valor);
  const min = Math.min(...valores), max = Math.max(...valores);
  const desde = minFijo ?? min, hasta = maxFijo ?? Math.max(max * 1.1, desde + 1);
  const x = (i: number) => serie.length === 1 ? ANCHO / 2 : (i * ANCHO) / (serie.length - 1);
  const y = (v: number) => ALTO - ((v - desde) / (hasta - desde)) * ALTO;
  // Un solo punto (p. ej. la primera hora con datos) se dibuja como una línea horizontal de lado a lado.
  const puntos = valores.length === 1 ? [`0,${y(valores[0]).toFixed(1)}`, `${ANCHO},${y(valores[0]).toFixed(1)}`]
    : valores.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`);
  const linea = 'M' + puntos.join(' L');
  const finX = valores.length === 1 ? ANCHO : x(valores.length - 1);
  return {
    linea,
    area: `${linea} L${finX.toFixed(1)},${ALTO} L0,${ALTO} Z`,
    min, max, ultimo: valores[valores.length - 1],
    caidas: serie.map((p, i) => p.sinApp > 0 ? x(i) : -1).filter(v => v >= 0)
  };
}
