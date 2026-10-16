import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { TerminosProcesoComponent } from './terminos-proceso.component';
import { ProvidenciasProcesoComponent } from './providencias-proceso.component';
import { AsistenteProcesoComponent } from './asistente-proceso.component';
import {
  Abogado, CLASES_CUANTIA, Cliente, DesgloseRadicado, Despacho, ESTADOS, ETAPAS, ProcesoSolicitud, ROLES_CLIENTE,
  Rama, TIPOS_SERVICIO, TipoProceso, etiqueta
} from '../../core/modelos';

/** Crear (/procesos/nuevo) o editar (/procesos/:id/editar) un proceso. */
@Component({
  selector: 'app-proceso-form',
  standalone: true,
  imports: [FormsModule, RouterLink, TerminosProcesoComponent, ProvidenciasProcesoComponent,
    AsistenteProcesoComponent],
  templateUrl: './proceso-form.component.html'
})
export class ProcesoFormComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  readonly estados = ESTADOS;
  readonly etapas = ETAPAS;
  readonly rolesCliente = ROLES_CLIENTE;
  readonly clasesCuantia = CLASES_CUANTIA;
  readonly tiposServicio = TIPOS_SERVICIO;
  readonly etiqueta = etiqueta;

  idProceso: number | null = null;
  proceso: ProcesoSolicitud = this.vacio();
  rama = '';

  ramas: Rama[] = [];
  tipos: TipoProceso[] = [];
  despachos: Despacho[] = [];
  clientes: Cliente[] = [];
  abogados: Abogado[] = [];

  /** Resultado de analizar el radicado mientras se escribe. */
  desglose: DesgloseRadicado | null = null;
  errorRadicado = '';
  private radicado$ = new Subject<string>();

  guardando = false;
  enviado = false;
  error = '';

  ngOnInit(): void {
    forkJoin({
      ramas: this.api.ramas(), tipos: this.api.tipos(), despachos: this.api.despachos(),
      clientes: this.api.clientes(), abogados: this.api.abogados(true)
    }).subscribe({
      next: r => Object.assign(this, r),
      error: e => this.error = mensajeDeError(e)
    });

    this.radicado$.pipe(
      map(t => t.replace(/[\s.\-]/g, '')),
      debounceTime(350),
      distinctUntilChanged(),
      switchMap(numero => {
        if (numero.length === 0) return of({ desglose: null, error: '' });
        if (!/^\d{23}$/.test(numero)) {
          return of({ desglose: null, error: `Lleva ${numero.length} de 23 dígitos` });
        }
        return this.api.desglosarRadicado(numero).pipe(
          map(d => ({ desglose: d, error: '' })),
          catchError(e => of({ desglose: null, error: mensajeDeError(e) })));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(r => { this.desglose = r.desglose; this.errorRadicado = r.error; });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.idProceso = Number(id);
      this.api.proceso(this.idProceso).subscribe({
        next: p => {
          this.rama = p.codigoRama;
          this.proceso = {
            radicado: p.radicadoLegible, idTipo: p.idTipo, idDespachoActual: p.idDespachoActual ?? null,
            idCliente: p.idCliente, rolCliente: p.rolCliente, asunto: p.asunto, cuantia: p.cuantia ?? null,
            claseCuantia: p.claseCuantia, tipoServicio: p.tipoServicio, estado: p.estado, etapa: p.etapa,
            fechaRadicacion: p.fechaRadicacion, vigilado: p.vigilado, idResponsable: p.idResponsable ?? null
          };
          this.radicadoCambio(p.radicado);
        },
        error: e => this.error = mensajeDeError(e)
      });
    }
  }

  vacio(): ProcesoSolicitud {
    return {
      radicado: '', idTipo: null, idDespachoActual: null, idCliente: null, rolCliente: 'DEMANDANTE', asunto: '',
      cuantia: null, claseCuantia: 'MENOR', tipoServicio: 'REPRESENTACION', estado: 'ACTIVO', etapa: 'DEMANDA',
      fechaRadicacion: new Date().toISOString().slice(0, 10), vigilado: true, idResponsable: null
    };
  }

  radicadoCambio(valor: string): void {
    this.radicado$.next(valor ?? '');
  }

  get tiposDeLaRama(): TipoProceso[] {
    return this.rama ? this.tipos.filter(t => t.codigoRama === this.rama) : this.tipos;
  }

  get radicadoValido(): boolean {
    return !!this.desglose?.despacho
      && (!this.desglose.idProcesoExistente || this.desglose.idProcesoExistente === this.idProceso);
  }

  ramaCambio(): void {
    if (!this.tiposDeLaRama.some(t => t.idTipo === this.proceso.idTipo)) this.proceso.idTipo = null;
  }

  claseCambio(): void {
    if (this.proceso.claseCuantia === 'SIN_CUANTIA') this.proceso.cuantia = null;
  }

  /** Terminado o archivado implica etapa CERRADO; reabrir lo devuelve a una etapa activa. */
  estadoCambio(): void {
    const cerrado = this.proceso.estado === 'TERMINADO' || this.proceso.estado === 'ARCHIVADO';
    if (cerrado) this.proceso.etapa = 'CERRADO';
    else if (this.proceso.etapa === 'CERRADO') this.proceso.etapa = 'SENTENCIA';
  }

  guardar(form: NgForm): void {
    this.enviado = true;
    this.error = '';
    if (form.invalid || !this.radicadoValido) {
      this.error = this.radicadoValido ? 'Revise los campos marcados.' : 'El radicado no es válido o ya está registrado.';
      return;
    }
    this.guardando = true;
    const datos: ProcesoSolicitud = {
      ...this.proceso,
      idDespachoActual: this.desglose!.instancia > 0 ? this.proceso.idDespachoActual : null
    };
    const peticion = this.idProceso
      ? this.api.actualizarProceso(this.idProceso, datos)
      : this.api.crearProceso(datos);
    peticion.subscribe({
      next: () => this.router.navigate(['/procesos']),
      error: e => { this.error = mensajeDeError(e); this.guardando = false; }
    });
  }
}
