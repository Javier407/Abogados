import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, JURIDICO, JURIDICO_Y_DEPENDIENTES } from '../../core/auth.service';
import {
  ActuacionSolicitud, ConflictoInteres, EventoExpediente, Instancia, Parte, ParteSolicitud, Proceso, Providencia,
  ProvidenciaSolicitud, ROLES_PARTE, TIPOS_ACTUACION, Termino, etiqueta
} from '../../core/modelos';
import { TerminosProcesoComponent } from './terminos-proceso.component';
import { AsistenteProcesoComponent } from './asistente-proceso.component';

type Filtro = 'TODO' | 'ACTUACION' | 'PROVIDENCIA' | 'TERMINO';

/**
 * Expediente de un proceso: datos, partes (con aviso de conflicto de intereses), línea de tiempo de
 * actuaciones, providencias y términos, y formularios para registrar lo que pasa en el proceso.
 */
@Component({
  selector: 'app-expediente',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, TerminosProcesoComponent, AsistenteProcesoComponent],
  templateUrl: './expediente.component.html',
  styleUrl: './expediente.component.css'
})
export class ExpedienteComponent implements OnInit {
  private api = inject(ApiService);
  private ruta = inject(ActivatedRoute);
  private auth = inject(AuthService);

  readonly roles = ROLES_PARTE;
  readonly tiposActuacion = TIPOS_ACTUACION;
  readonly etiqueta = etiqueta;
  readonly juridico = this.auth.tiene(...JURIDICO);
  readonly registraActuaciones = this.auth.tiene(...JURIDICO_Y_DEPENDIENTES);
  readonly hoy = new Date().toLocaleDateString('en-CA');      // AAAA-MM-DD en hora local

  idProceso = 0;
  proceso: Proceso | null = null;
  eventos: EventoExpediente[] = [];
  instancias: Instancia[] = [];
  partes: Parte[] = [];
  providencias: Providencia[] = [];
  terminosPendientes: Termino[] = [];
  filtro: Filtro = 'TODO';
  version = 0;               // vuelve a crear la tarjeta de términos cuando una actuación cumple uno

  formulario: 'ACTUACION' | 'PROVIDENCIA' | 'PARTE' | null = null;
  actuacion: ActuacionSolicitud = this.actuacionVacia();
  providencia: ProvidenciaSolicitud = this.providenciaVacia();
  parte: ParteSolicitud = { rol: 'DEMANDADO', nombre: '' };
  conflictos: ConflictoInteres[] = [];
  sinVerificar = false;

  cargando = true;
  guardando = false;
  error = '';
  errorFormulario = '';
  mensaje = '';

  ngOnInit(): void {
    this.ruta.paramMap.subscribe(p => {
      this.idProceso = Number(p.get('id'));
      this.formulario = null;
      this.cargar();
    });
  }

  get eventosVisibles(): EventoExpediente[] {
    return this.filtro === 'TODO' ? this.eventos : this.eventos.filter(e => e.categoria === this.filtro);
  }

  contar(categoria: Filtro): number {
    return categoria === 'TODO' ? this.eventos.length : this.eventos.filter(e => e.categoria === categoria).length;
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      proceso: this.api.proceso(this.idProceso),
      expediente: this.api.expediente(this.idProceso),
      partes: this.api.partes(this.idProceso),
      providencias: this.api.providencias(this.idProceso),
      terminos: this.api.terminos({ idProceso: this.idProceso })
    }).subscribe({
      next: r => {
        this.proceso = r.proceso;
        this.eventos = r.expediente.lineaDeTiempo;
        this.instancias = r.expediente.otrasInstancias;
        this.partes = r.partes;
        this.providencias = r.providencias;
        this.terminosPendientes = r.terminos.filter(t => t.estado === 'PENDIENTE');
        this.cargando = false;
      },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  abrir(f: 'ACTUACION' | 'PROVIDENCIA' | 'PARTE'): void {
    this.formulario = this.formulario === f ? null : f;
    this.errorFormulario = this.mensaje = '';
    this.conflictos = [];
    this.sinVerificar = false;
  }

  /** Clase del punto en la línea de tiempo: firma (azul), juzgado (caoba), término pendiente o cumplido. */
  claseEvento(e: EventoExpediente): string {
    if (e.categoria === 'TERMINO') return e.tipo === 'CUMPLIDO' ? 'ev-cumplido' : 'ev-termino';
    return e.categoria === 'PROVIDENCIA' ? 'ev-juzgado' : 'ev-firma';
  }

  esFutura(e: EventoExpediente): boolean {
    return e.fecha > this.hoy;
  }

  // ---------- formularios ----------

  registrarActuacion(form: NgForm): void {
    if (form.invalid) { form.control.markAllAsTouched(); return; }
    this.enviar(this.api.registrarActuacion(this.idProceso, this.limpiar(this.actuacion)), () => {
      const cumplio = !!this.actuacion.idTerminoCumplido;
      this.mensaje = 'Actuación registrada' + (cumplio ? ' y término marcado como cumplido' : '');
      this.actuacion = this.actuacionVacia();
      if (cumplio) this.version++;
    });
  }

  registrarProvidencia(form: NgForm): void {
    if (form.invalid) { form.control.markAllAsTouched(); return; }
    const p = this.limpiar(this.providencia);
    if (p.tipo === 'AUTO') p.sentido = null;
    this.enviar(this.api.registrarProvidencia(this.idProceso, p), () => {
      this.mensaje = p.tipo === 'SENTENCIA' ? 'Sentencia registrada' : 'Auto registrado';
      this.providencia = this.providenciaVacia();
    });
  }

  agregarParte(form: NgForm): void {
    if (form.invalid) { form.control.markAllAsTouched(); return; }
    this.guardando = true;
    this.errorFormulario = this.mensaje = '';
    this.api.agregarParte(this.idProceso, this.limpiar(this.parte)).subscribe({
      next: r => {
        this.guardando = false;
        this.conflictos = r.conflictos;
        this.sinVerificar = !r.conflictosVerificados;
        this.mensaje = `${r.parte.nombre} agregado como ${etiqueta(r.parte.rol).toLowerCase()}`;
        this.parte = { rol: 'DEMANDADO', nombre: '' };
        form.resetForm(this.parte);
        this.api.partes(this.idProceso).subscribe(p => this.partes = p);
      },
      error: e => { this.guardando = false; this.errorFormulario = mensajeDeError(e); }
    });
  }

  quitarParte(p: Parte): void {
    if (!confirm(`¿Quitar a ${p.nombre} del proceso?`)) return;
    this.api.eliminarParte(this.idProceso, p.idParte).subscribe({
      next: () => this.partes = this.partes.filter(x => x.idParte !== p.idParte),
      error: e => this.error = mensajeDeError(e)
    });
  }

  eliminarActuacion(e: EventoExpediente): void {
    if (!confirm(`¿Eliminar la actuación «${e.titulo}»?`)) return;
    this.api.eliminarActuacion(this.idProceso, e.id).subscribe({
      next: () => this.cargar(),
      error: err => this.error = mensajeDeError(err)
    });
  }

  private enviar(peticion: import('rxjs').Observable<unknown>, alTerminar: () => void): void {
    this.guardando = true;
    this.errorFormulario = this.mensaje = '';
    peticion.subscribe({
      next: () => { this.guardando = false; alTerminar(); this.formulario = null; this.cargar(); },
      error: e => { this.guardando = false; this.errorFormulario = mensajeDeError(e); }
    });
  }

  /** Cadenas vacías a null: el backend las trata como "sin dato". */
  private limpiar<T extends object>(o: T): T {
    return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v === '' ? null : v])) as T;
  }

  private actuacionVacia(): ActuacionSolicitud {
    return { tipo: 'MEMORIAL', fecha: this.hoy, descripcion: '', idProvidencia: null, idTerminoCumplido: null, documentoUrl: '' };
  }

  private providenciaVacia(): ProvidenciaSolicitud {
    return { tipo: 'AUTO', clase: '', fechaProvidencia: this.hoy, fechaEstado: this.hoy, decision: '', sentido: null };
  }
}
