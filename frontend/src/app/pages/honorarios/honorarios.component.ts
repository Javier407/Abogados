import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import {
  FORMAS_COBRO, FormaCobro, Honorario, HonorarioSolicitud, MEDIOS_PAGO, Pago, Proceso, Tarifa, camposDeForma, etiqueta
} from '../../core/modelos';

/** Acuerdos de honorarios por proceso, pagos de los clientes y tarifas de referencia. */
@Component({
  selector: 'app-honorarios',
  standalone: true,
  imports: [FormsModule, RouterLink, CurrencyPipe, DatePipe],
  templateUrl: './honorarios.component.html'
})
export class HonorariosComponent implements OnInit {
  private api = inject(ApiService);
  readonly formas = FORMAS_COBRO;
  readonly medios = MEDIOS_PAGO;
  readonly etiqueta = etiqueta;
  readonly camposDeForma = camposDeForma;

  honorarios: Honorario[] = [];
  procesos: Proceso[] = [];
  tarifas: Tarifa[] = [];
  filtroEstado = 'VIGENTE';

  seleccionado: Honorario | null = null;
  pagos: Pago[] = [];
  pago: Pago = this.pagoVacio();

  nuevo: HonorarioSolicitud = this.acuerdoVacio();
  creando = false;
  verTarifas = false;

  mensaje = '';
  error = '';

  ngOnInit(): void {
    forkJoin({ procesos: this.api.procesos(), tarifas: this.api.tarifas() }).subscribe({
      next: r => { this.procesos = r.procesos; this.tarifas = r.tarifas; },
      error: e => this.error = mensajeDeError(e)
    });
    this.cargar();
  }

  hoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  pagoVacio(): Pago { return { fecha: this.hoy(), valor: null, medio: 'TRANSFERENCIA', referencia: '' }; }
  acuerdoVacio(): HonorarioSolicitud {
    return { idProceso: null, idCliente: null, idTarifa: null, formaCobro: 'FIJO', valorFijo: null, porcentaje: null,
      valorEstimado: null, fechaPacto: this.hoy() };
  }

  cargar(): void {
    this.api.honorarios({ estado: this.filtroEstado || undefined }).subscribe({
      next: h => {
        this.honorarios = h;
        if (this.seleccionado) this.seleccionado = h.find(x => x.idHonorario === this.seleccionado!.idHonorario) ?? null;
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  radicado(idProceso: number): string {
    return this.procesos.find(p => p.idProceso === idProceso)?.radicadoLegible ?? `Proceso ${idProceso}`;
  }

  get procesosSinAcuerdo(): Proceso[] {
    return this.procesos.filter(p => p.estado !== 'ARCHIVADO');
  }

  get totalSaldo(): number {
    return this.honorarios.reduce((s, h) => s + (h.saldo ?? 0), 0);
  }

  /** limpiar = false conserva el mensaje (p. ej. "Pago registrado") al refrescar el acuerdo. */
  elegir(h: Honorario, limpiar = true): void {
    this.seleccionado = h;
    this.pago = this.pagoVacio();
    if (limpiar) this.mensaje = this.error = '';
    this.api.pagos(h.idHonorario).subscribe({ next: p => this.pagos = p, error: e => this.error = mensajeDeError(e) });
  }

  registrarPago(form: NgForm): void {
    const h = this.seleccionado;
    if (!h || form.invalid || !this.pago.valor) { this.error = 'Indique fecha, valor y medio del pago.'; return; }
    this.mensaje = this.error = '';
    this.api.registrarPago(h.idHonorario, this.pago).subscribe({
      next: act => {
        this.mensaje = `Pago registrado. ${act.saldo != null ? 'Saldo: $' + act.saldo.toLocaleString('es-CO') + '.' : ''}`
          + (act.estado === 'CUMPLIDO' ? ' El acuerdo quedó pagado por completo.' : '');
        this.cargar();
        this.elegir(act, false);
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  procesoCambio(): void {
    this.nuevo.idCliente = this.procesos.find(p => p.idProceso === this.nuevo.idProceso)?.idCliente ?? null;
  }

  formaCambio(): void {
    const c = camposDeForma(this.nuevo.formaCobro);
    if (!c.fijo) this.nuevo.valorFijo = null;
    if (!c.porcentaje) this.nuevo.porcentaje = null;
  }

  /** Toma de la tarifa de referencia la forma de cobro y los valores sugeridos (editables). */
  usarTarifa(t: Tarifa): void {
    this.creando = true;
    this.nuevo.idTarifa = t.idTarifa;
    this.nuevo.formaCobro = t.formaCobro as FormaCobro;
    const c = camposDeForma(this.nuevo.formaCobro);
    this.nuevo.valorFijo = c.fijo ? (t.desdePesos ?? null) : null;
    this.nuevo.porcentaje = c.porcentaje ? (t.porcentaje ?? null) : null;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  crear(form: NgForm): void {
    if (form.invalid) { this.error = 'Complete el proceso, la forma de cobro y los valores.'; return; }
    this.mensaje = this.error = '';
    this.api.crearHonorario(this.nuevo).subscribe({
      next: h => {
        this.mensaje = `Acuerdo registrado para ${h.cliente}.`;
        this.creando = false;
        this.nuevo = this.acuerdoVacio();
        this.filtroEstado = 'VIGENTE';
        this.cargar();
        this.elegir(h, false);
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  cancelarAcuerdo(h: Honorario): void {
    if (!confirm(`¿Cancelar el acuerdo de ${h.cliente}? Dejará de recibir pagos.`)) return;
    this.api.cambiarEstadoHonorario(h.idHonorario, 'CANCELADO').subscribe({
      next: () => { this.mensaje = 'Acuerdo cancelado.'; this.seleccionado = null; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }
}
