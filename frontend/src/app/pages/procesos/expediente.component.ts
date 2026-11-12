import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideFilePen,
  lucideFileText,
  lucideFolderOpen,
  lucideHandCoins,
  lucideListChecks,
  lucidePencil,
  lucideSparkles,
  lucideUsers,
  lucideWallet,
  lucideCalendarClock,
  lucideGavel,
  lucideArchive,
  lucideArchiveRestore,
} from '@ng-icons/lucide';

import { ApiService, mensajeDeError } from '../../core/api.service';
import {
  AuthService,
  DIRECCION,
  JURIDICO,
  JURIDICO_Y_DEPENDIENTES,
} from '../../core/auth.service';
import {
  MOTIVOS_CIERRE,
  MotivoCierre,
  ActuacionSolicitud,
  ConflictoInteres,
  ESTADOS_PRUEBA,
  ETAPAS,
  EstadoPrueba,
  EventoExpediente,
  Instancia,
  Parte,
  ParteSolicitud,
  Perito,
  Proceso,
  Providencia,
  ProvidenciaSolicitud,
  Prueba,
  PruebaSolicitud,
  ROLES_PARTE,
  SugerenciaTermino,
  TIPOS_ACTUACION,
  TIPOS_PRUEBA,
  Termino,
  etiqueta,
} from '../../core/modelos';
import { TerminosProcesoComponent } from './terminos-proceso.component';
import { AsistenteProcesoComponent } from './asistente-proceso.component';
import { GastosProcesoComponent } from './gastos-proceso.component';
import { DocumentosProcesoComponent } from './documentos-proceso.component';
import { EquipoProcesoComponent } from './equipo-proceso.component';

type Filtro = 'TODO' | 'ACTUACION' | 'PROVIDENCIA' | 'TERMINO';
export type Pestana =
  'resumen' | 'terminos' | 'pruebas' | 'documentos' | 'equipo' | 'gastos' | 'asistente';
interface OpcionPestana {
  id: Pestana;
  texto: string;
  icono: string;
}

/**
 * Expediente de un proceso, de punta a punta: cabecera con la etapa y las acciones encadenadas (providencia →
 * término sugerido → borrador → gasto) y pestañas enlazables por URL (?pestana=asistente).
 */
@Component({
  selector: 'app-expediente',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    RouterLink,
    TerminosProcesoComponent,
    AsistenteProcesoComponent,
    GastosProcesoComponent,
    DocumentosProcesoComponent,
    EquipoProcesoComponent,
    NgIcon,
  ],
  providers: [
    provideIcons({
      lucideFilePen,
      lucideFileText,
      lucideFolderOpen,
      lucideHandCoins,
      lucideListChecks,
      lucidePencil,
      lucideSparkles,
      lucideUsers,
      lucideWallet,
      lucideCalendarClock,
      lucideGavel,
      lucideArchive,
      lucideArchiveRestore,
    }),
  ],
  templateUrl: './expediente.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './expediente.component.css',
})
export class ExpedienteComponent implements OnInit {
  private api = inject(ApiService);
  private ruta = inject(ActivatedRoute);
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly roles = ROLES_PARTE;
  readonly tiposActuacion = TIPOS_ACTUACION;
  readonly tiposPrueba = TIPOS_PRUEBA;
  readonly estadosPrueba = ESTADOS_PRUEBA;
  readonly etiqueta = etiqueta;
  readonly juridico = this.auth.tiene(...JURIDICO);
  readonly direccion = this.auth.tiene(...DIRECCION);
  readonly motivosCierre = MOTIVOS_CIERRE;
  cerrando = false;
  cierre: { motivo: MotivoCierre | null; fecha: string; nota: string } = { motivo: null, fecha: '', nota: '' };
  readonly registraActuaciones = this.auth.tiene(...JURIDICO_Y_DEPENDIENTES);
  readonly veFinanzas = this.auth.tiene('ADMIN', 'SOCIO', 'ABOGADO', 'ADMINISTRATIVO');
  readonly etapas = ETAPAS;

  pestana: Pestana = 'resumen';
  /** Providencia elegida en la línea de tiempo para pedir el borrador del recurso. */
  providenciaParaBorrador: number | null = null;
  versionAsistente = 0;

  get pestanas(): OpcionPestana[] {
    const l: OpcionPestana[] = [
      { id: 'resumen', texto: 'Resumen', icono: 'lucideFolderOpen' },
      { id: 'terminos', texto: 'Términos', icono: 'lucideCalendarClock' },
      { id: 'pruebas', texto: 'Pruebas', icono: 'lucideListChecks' },
      { id: 'documentos', texto: 'Documentos', icono: 'lucideFileText' },
      { id: 'equipo', texto: 'Equipo', icono: 'lucideUsers' },
    ];
    if (this.veFinanzas) l.push({ id: 'gastos', texto: 'Gastos', icono: 'lucideWallet' });
    if (this.juridico) l.push({ id: 'asistente', texto: 'Asistente IA', icono: 'lucideSparkles' });
    return l;
  }
  readonly hoy = new Date().toLocaleDateString('en-CA'); // AAAA-MM-DD en hora local

  idProceso = 0;
  proceso: Proceso | null = null;
  eventos: EventoExpediente[] = [];
  instancias: Instancia[] = [];
  partes: Parte[] = [];
  providencias: Providencia[] = [];
  terminosPendientes: Termino[] = [];
  pruebas: Prueba[] = [];
  peritos: Perito[] = [];
  filtro: Filtro = 'TODO';

  // Términos sugeridos de una providencia (se abren desde la línea de tiempo o al registrarla)
  sugerenciasDe: number | null = null;
  sugerencias: SugerenciaTermino[] = [];
  cargandoSugerencias = false;
  errorSugerencias = '';
  version = 0; // vuelve a crear la tarjeta de términos cuando una actuación cumple uno

  formulario: 'ACTUACION' | 'PROVIDENCIA' | 'PARTE' | 'PRUEBA' | null = null;
  prueba: PruebaSolicitud = this.pruebaVacia();
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
    this.api
      .peritos()
      .subscribe({ next: (p) => (this.peritos = p), error: () => (this.peritos = []) });
    this.ruta.paramMap.subscribe((p) => {
      this.idProceso = Number(p.get('id'));
      this.formulario = null;
      this.cargar();
    });
    this.ruta.queryParamMap.subscribe((q) => {
      const p = q.get('pestana') as Pestana | null;
      this.pestana = p && this.pestanas.some((x) => x.id === p) ? p : 'resumen';
    });
  }

  irA(p: Pestana): void {
    this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: { pestana: p === 'resumen' ? null : p },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** Flechas izquierda/derecha entre pestañas (patrón de pestañas de WAI-ARIA). */
  teclaPestana(e: KeyboardEvent, actual: Pestana): void {
    const l = this.pestanas;
    const i = l.findIndex((x) => x.id === actual);
    const destino =
      e.key === 'ArrowRight'
        ? l[(i + 1) % l.length]
        : e.key === 'ArrowLeft'
          ? l[(i - 1 + l.length) % l.length]
          : e.key === 'Home'
            ? l[0]
            : e.key === 'End'
              ? l[l.length - 1]
              : null;
    if (!destino) return;
    e.preventDefault();
    this.irA(destino.id);
    setTimeout(() => document.getElementById('pestana-' + destino.id)?.focus());
  }

  /** Encadenado: de la providencia al borrador del recurso en el asistente. */
  pedirBorrador(idProvidencia: number): void {
    this.providenciaParaBorrador = idProvidencia;
    this.versionAsistente++;
    this.irA('asistente');
  }

  get estaCerrado(): boolean {
    return this.proceso?.estado === 'TERMINADO' || this.proceso?.estado === 'ARCHIVADO';
  }

  nombreMotivoCierre(m?: string | null): string {
    return this.motivosCierre.find((x) => x.valor === m)?.etiqueta ?? (m ? etiqueta(m) : '');
  }

  ayudaMotivoCierre(m: MotivoCierre | null): string {
    return this.motivosCierre.find((x) => x.valor === m)?.ayuda ?? '';
  }

  abrirCierre(): void {
    this.cierre = { motivo: null, fecha: this.hoy, nota: '' };
    this.cerrando = true;
    this.error = this.mensaje = '';
  }

  cerrarProceso(form: NgForm): void {
    if (form.invalid || !this.cierre.motivo) {
      form.control.markAllAsTouched();
      return;
    }
    this.guardando = true;
    this.errorFormulario = '';
    this.api
      .cerrarProceso(this.idProceso, { motivo: this.cierre.motivo, fecha: this.cierre.fecha || null, nota: this.cierre.nota || null })
      .subscribe({
        next: (r) => {
          const e = r.efectos;
          const sinEfecto = [
            e.terminosPendientes ? `${e.terminosPendientes} término(s) pendiente(s)` : '',
            e.audienciasFuturas ? `${e.audienciasFuturas} audiencia(s)` : '',
          ].filter(Boolean);
          this.mensaje =
            `Proceso cerrado: ${this.nombreMotivoCierre(r.proceso.motivoCierre).toLowerCase()}.` +
            (sinEfecto.length ? ` Dejan de avisar ${sinEfecto.join(' y ')}.` : '') +
            (e.seApagoLaVigilancia ? ' Se dejó de vigilar en la Rama Judicial.' : '') +
            ' Si hay honorarios por liquidar, hágalo en Honorarios.';
          this.guardando = false;
          this.cerrando = false;
          this.cargar();
        },
        error: (e) => {
          this.errorFormulario = mensajeDeError(e);
          this.guardando = false;
        },
      });
  }

  reabrirProceso(): void {
    if (!confirm('¿Reabrir el proceso? Vuelve a la etapa que tenía y sus términos pendientes vuelven a la agenda.')) return;
    this.error = this.mensaje = '';
    this.api.reabrirProceso(this.idProceso).subscribe({
      next: () => {
        this.mensaje = 'Proceso reabierto. Si debe vigilarse otra vez, actívelo en Vigilancia.';
        this.cargar();
      },
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  indiceEtapa(p: Proceso): number {
    return (this.etapas as readonly string[]).indexOf(p.etapa);
  }

  get eventosVisibles(): EventoExpediente[] {
    return this.filtro === 'TODO'
      ? this.eventos
      : this.eventos.filter((e) => e.categoria === this.filtro);
  }

  contar(categoria: Filtro): number {
    return categoria === 'TODO'
      ? this.eventos.length
      : this.eventos.filter((e) => e.categoria === categoria).length;
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      proceso: this.api.proceso(this.idProceso),
      expediente: this.api.expediente(this.idProceso),
      partes: this.api.partes(this.idProceso),
      providencias: this.api.providencias(this.idProceso),
      terminos: this.api.terminos({ idProceso: this.idProceso }),
      pruebas: this.api.pruebas(this.idProceso),
    }).subscribe({
      next: (r) => {
        this.proceso = r.proceso;
        this.eventos = r.expediente.lineaDeTiempo;
        this.instancias = r.expediente.otrasInstancias;
        this.partes = r.partes;
        this.providencias = r.providencias;
        this.terminosPendientes = r.terminos.filter((t) => t.estado === 'PENDIENTE');
        this.pruebas = r.pruebas;
        this.cargando = false;
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.cargando = false;
      },
    });
  }

  abrir(f: 'ACTUACION' | 'PROVIDENCIA' | 'PARTE' | 'PRUEBA'): void {
    this.formulario = this.formulario === f ? null : f;
    if (f === 'PRUEBA' && !this.peritos.length) {
      this.api
        .peritos()
        .subscribe({ next: (p) => (this.peritos = p), error: () => (this.peritos = []) });
    }
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
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }
    const a = this.limpiar(this.actuacion);
    if (a.tipo !== 'AUDIENCIA') {
      a.hora = null;
      a.lugar = null;
    }
    this.enviar(this.api.registrarActuacion(this.idProceso, a), () => {
      const cumplio = !!this.actuacion.idTerminoCumplido;
      this.mensaje = 'Actuación registrada' + (cumplio ? ' y término marcado como cumplido' : '');
      this.actuacion = this.actuacionVacia();
      if (cumplio) this.version++;
    });
  }

  registrarProvidencia(form: NgForm): void {
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }
    const p = this.limpiar(this.providencia);
    if (p.tipo === 'AUTO') {
      p.sentido = null;
      p.anticipada = false;
    }
    this.enviar(this.api.registrarProvidencia(this.idProceso, p), (creada) => {
      this.mensaje =
        (p.tipo !== 'SENTENCIA' ? 'Auto registrado' : p.anticipada ? 'Sentencia anticipada registrada' : 'Sentencia registrada') +
        '. Revise los términos sugeridos.';
      this.providencia = this.providenciaVacia();
      this.verSugerencias(creada.idProvidencia, true);
    });
  }

  agregarParte(form: NgForm): void {
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }
    this.guardando = true;
    this.errorFormulario = this.mensaje = '';
    this.api.agregarParte(this.idProceso, this.limpiar(this.parte)).subscribe({
      next: (r) => {
        this.guardando = false;
        this.conflictos = r.conflictos;
        this.sinVerificar = !r.conflictosVerificados;
        this.mensaje = `${r.parte.nombre} agregado como ${etiqueta(r.parte.rol).toLowerCase()}`;
        this.parte = { rol: 'DEMANDADO', nombre: '' };
        form.resetForm(this.parte);
        this.api.partes(this.idProceso).subscribe((p) => (this.partes = p));
      },
      error: (e) => {
        this.guardando = false;
        this.errorFormulario = mensajeDeError(e);
      },
    });
  }

  quitarParte(p: Parte): void {
    if (!confirm(`¿Quitar a ${p.nombre} del proceso?`)) return;
    this.api.eliminarParte(this.idProceso, p.idParte).subscribe({
      next: () => (this.partes = this.partes.filter((x) => x.idParte !== p.idParte)),
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  // ---------- términos sugeridos ----------

  verSugerencias(idProvidencia: number, abrir = false): void {
    if (this.sugerenciasDe === idProvidencia && !abrir) {
      this.sugerenciasDe = null;
      return;
    }
    this.sugerenciasDe = idProvidencia;
    this.sugerencias = [];
    this.errorSugerencias = '';
    this.cargandoSugerencias = true;
    this.api.sugerencias(this.idProceso, idProvidencia).subscribe({
      next: (s) => {
        this.sugerencias = s;
        this.cargandoSugerencias = false;
      },
      error: (e) => {
        this.errorSugerencias = mensajeDeError(e);
        this.cargandoSugerencias = false;
      },
    });
  }

  crearSugerido(s: SugerenciaTermino): void {
    if (this.sugerenciasDe === null) return;
    const idProvidencia = this.sugerenciasDe;
    this.api
      .crearTermino(this.idProceso, {
        descripcion: s.descripcion,
        fechaInicio: s.fechaInicio,
        diasHabiles: s.diasHabiles,
        idProvidencia,
        norma: s.norma,
      })
      .subscribe({
        next: (t) => {
          this.mensaje = `Término creado: «${t.descripcion}», vence el ${t.fechaVencimiento.split('-').reverse().join('/')}`;
          this.version++;
          this.cargar();
          this.verSugerencias(idProvidencia, true);
        },
        error: (e) => (this.errorSugerencias = mensajeDeError(e)),
      });
  }

  // ---------- pruebas ----------

  nombrePerito(id?: number | null): string {
    return id ? (this.peritos.find((p) => p.idPerito === id)?.nombre ?? 'Perito ' + id) : '';
  }

  registrarPrueba(form: NgForm): void {
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }
    const p = this.limpiar(this.prueba);
    if (p.tipo !== 'PERICIAL') p.idPerito = null;
    this.enviar(this.api.crearPrueba(this.idProceso, p), (creada) => {
      this.mensaje = 'Prueba registrada' + (creada.idTermino ? ' con su término de entrega' : '');
      this.prueba = this.pruebaVacia();
      if (creada.idTermino) this.version++;
    });
  }

  cambiarEstado(p: Prueba, estado: EstadoPrueba): void {
    const datos: PruebaSolicitud = {
      tipo: p.tipo,
      descripcion: p.descripcion,
      idPerito: p.idPerito,
      estado,
      fechaEntrega: p.fechaEntrega,
    };
    this.api.actualizarPrueba(this.idProceso, p.idPrueba, datos).subscribe({
      next: () => {
        this.version++;
        this.cargar();
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.cargar();
      },
    });
  }

  quitarPrueba(p: Prueba): void {
    if (!confirm(`¿Eliminar la prueba «${p.descripcion}»?`)) return;
    this.api.eliminarPrueba(this.idProceso, p.idPrueba).subscribe({
      next: () => {
        this.version++;
        this.cargar();
      },
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  eliminarActuacion(e: EventoExpediente): void {
    if (!confirm(`¿Eliminar la actuación «${e.titulo}»?`)) return;
    this.api.eliminarActuacion(this.idProceso, e.id).subscribe({
      next: () => this.cargar(),
      error: (err) => (this.error = mensajeDeError(err)),
    });
  }

  private enviar<T>(
    peticion: import('rxjs').Observable<T>,
    alTerminar: (resultado: T) => void,
  ): void {
    this.guardando = true;
    this.errorFormulario = this.mensaje = '';
    peticion.subscribe({
      next: (r) => {
        this.guardando = false;
        alTerminar(r);
        this.formulario = null;
        this.cargar();
      },
      error: (e) => {
        this.guardando = false;
        this.errorFormulario = mensajeDeError(e);
      },
    });
  }

  /** Cadenas vacías a null: el backend las trata como "sin dato". */
  private limpiar<T extends object>(o: T): T {
    return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v === '' ? null : v])) as T;
  }

  private actuacionVacia(): ActuacionSolicitud {
    return {
      tipo: 'MEMORIAL',
      fecha: this.hoy,
      descripcion: '',
      idProvidencia: null,
      idTerminoCumplido: null,
      documentoUrl: '',
    };
  }

  private pruebaVacia(): PruebaSolicitud {
    return {
      tipo: 'DOCUMENTAL',
      descripcion: '',
      idPerito: null,
      estado: 'SOLICITADA',
      fechaEntrega: null,
    };
  }

  private providenciaVacia(): ProvidenciaSolicitud {
    return {
      tipo: 'AUTO',
      clase: '',
      fechaProvidencia: this.hoy,
      fechaEstado: this.hoy,
      decision: '',
      sentido: null,
    };
  }
}
