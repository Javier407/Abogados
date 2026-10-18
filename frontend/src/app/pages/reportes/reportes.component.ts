import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Abogado, CargaAbogado, CarteraCliente, CumplimientoTerminos, ResultadoRama } from '../../core/modelos';

/** Reportes para la dirección (ADMIN y SOCIO): carga, cumplimiento de términos, resultados y cartera. */
@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './reportes.component.html',
  styleUrl: './reportes.component.css'
})
export class ReportesComponent implements OnInit {
  private api = inject(ApiService);

  desde = haceUnAnio();
  hasta = new Date().toLocaleDateString('en-CA');
  carga: CargaAbogado[] = [];
  terminos: CumplimientoTerminos[] = [];
  resultados: ResultadoRama[] = [];
  cartera: CarteraCliente[] = [];
  private abogados = new Map<number, string>();
  cargando = true;
  error = '';

  ngOnInit(): void {
    this.api.abogados().subscribe({
      next: a => this.abogados = new Map(a.map((x: Abogado) => [x.idPersona, `${x.nombres} ${x.apellidos}`])),
      error: () => { /* sin nombres: se muestra el documento */ }
    });
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      carga: this.api.reporteCarga(),
      terminos: this.api.reporteTerminos(this.desde, this.hasta),
      resultados: this.api.reporteResultados(this.desde, this.hasta),
      cartera: this.api.cartera()
    }).subscribe({
      next: r => {
        this.carga = r.carga;
        this.terminos = r.terminos;
        this.resultados = r.resultados.filter(x => this.total(x) > 0);
        this.cartera = r.cartera;
        this.cargando = false;
      },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  nombre(id: number): string {
    return this.abogados.get(id) ?? String(id);
  }

  /** A tiempo sobre lo que ya se puede juzgar (a tiempo + tarde + vencidos sin cumplir). */
  porcentaje(t: CumplimientoTerminos): number | null {
    const juzgables = t.aTiempo + t.tarde + t.vencidosSinCumplir;
    return juzgables === 0 ? null : (t.aTiempo * 100) / juzgables;
  }

  total(r: ResultadoRama): number {
    return r.favorables + r.parciales + r.desfavorables + r.sinSentido;
  }

  get totalCartera(): number {
    return this.cartera.reduce((s, c) => s + c.totalPorCobrar, 0);
  }

  // ---------- exportar a CSV (se abre en Excel) ----------

  exportarCarga(): void {
    descargar('carga-por-abogado.csv', ['Abogado', 'Documento', 'Procesos activos', 'Suspendidos', 'Términos pendientes',
      'Términos vencidos', 'Audiencias (30 días)'],
      this.carga.map(c => [this.nombre(c.idAbogado), c.idAbogado, c.procesosActivos, c.procesosSuspendidos,
        c.terminosPendientes, c.terminosVencidos, c.audienciasProximas]));
  }

  exportarTerminos(): void {
    descargar(`terminos-${this.desde}-a-${this.hasta}.csv`, ['Abogado', 'Documento', 'Total', 'A tiempo', 'Tarde',
      'Cumplidos sin fecha', 'Vencidos sin cumplir', 'Pendientes', '% a tiempo'],
      this.terminos.map(t => [this.nombre(t.idAbogado), t.idAbogado, t.total, t.aTiempo, t.tarde, t.cumplidosSinFecha,
        t.vencidosSinCumplir, t.pendientes, this.porcentaje(t)?.toFixed(1) ?? '']));
  }

  exportarResultados(): void {
    descargar(`resultados-${this.desde}-a-${this.hasta}.csv`, ['Rama', 'Favorables', 'Parciales', 'Desfavorables', 'Sin sentido', 'Total'],
      this.resultados.map(r => [r.rama, r.favorables, r.parciales, r.desfavorables, r.sinSentido, this.total(r)]));
  }

  exportarCartera(): void {
    descargar('cartera-por-cliente.csv', ['Cliente', 'Acuerdos', 'Pactado', 'Pagado', 'Saldo honorarios',
      'Gastos por reembolsar', 'Total por cobrar', 'Último pago', 'Días sin pagar'],
      this.cartera.map(c => [c.cliente, c.acuerdos, c.pactado ?? '', c.pagado, c.saldoHonorarios, c.gastosPorReembolsar,
        c.totalPorCobrar, c.ultimoPago ?? '', c.diasSinPagar ?? '']));
  }
}

function haceUnAnio(): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 1);
  return d.toLocaleDateString('en-CA');
}

/** CSV con punto y coma (lo que espera Excel en español) y BOM para que lea bien las tildes. */
function descargar(nombre: string, encabezado: (string | number)[], filas: (string | number | null)[][]): void {
  const celda = (v: string | number | null) => {
    const t = v == null ? '' : String(v);
    return /[;"\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
  };
  const texto = [encabezado, ...filas].map(f => f.map(celda).join(';')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿' + texto], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  URL.revokeObjectURL(url);
}
