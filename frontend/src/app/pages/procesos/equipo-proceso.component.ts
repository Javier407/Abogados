import { Component, OnInit, inject, input, output, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, JURIDICO } from '../../core/auth.service';
import {
  Abogado,
  Integrante,
  MOTIVOS_RELEVO,
  MotivoRelevo,
  PeriodoEquipo,
  RelevoSolicitud,
  RolEquipo,
  etiqueta,
} from '../../core/modelos';

/**
 * Equipo del proceso: quién está a cargo, quién apoya y quién estuvo antes. Cambiar al abogado a cargo
 * (renuncia, revocatoria, sustitución o sucesión del poder, retiro de la firma o reorganización) deja escrito el
 * motivo y, si se pide, le pasa al nuevo los términos pendientes y las audiencias.
 */
@Component({
  selector: 'app-equipo-proceso',
  standalone: true,
  imports: [FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './equipo-proceso.component.html',
  styleUrl: './equipo-proceso.component.css',
})
export class EquipoProcesoComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  readonly idProceso = input.required<number>();
  /** Un proceso cerrado no cambia de abogado ni de equipo. */
  readonly cerrado = input(false);
  /** Avisa al expediente que cambió el responsable (para refrescar la cabecera). */
  readonly cambio = output<void>();
  readonly juridico = this.auth.tiene(...JURIDICO);
  readonly etiqueta = etiqueta;
  readonly motivos = MOTIVOS_RELEVO;

  equipo: Integrante[] = [];
  historial: PeriodoEquipo[] = [];
  personal: Abogado[] = [];
  idPersona: number | null = null;
  rol: RolEquipo = 'APOYO';
  error = '';
  mensaje = '';

  relevando = false;
  guardando = false;
  relevo: RelevoSolicitud = this.relevoVacio();

  ngOnInit(): void {
    this.cargar();
    if (this.juridico)
      this.api.abogados(true).subscribe({ next: (a) => (this.personal = a), error: () => {} });
  }

  get responsable(): Integrante | undefined {
    return this.equipo.find((i) => i.rol === 'RESPONSABLE');
  }

  /** Los periodos que ya terminaron: quién estuvo antes y por qué salió. */
  get anteriores(): PeriodoEquipo[] {
    return this.historial.filter((p) => p.hasta);
  }

  disponibles(): Abogado[] {
    return this.personal.filter((a) => !this.equipo.some((i) => i.idPersona === a.idPersona));
  }

  /** Para recibir el proceso: cualquier abogado activo menos el que ya está a cargo. */
  candidatos(): Abogado[] {
    return this.personal.filter((a) => a.idPersona !== this.responsable?.idPersona);
  }

  cargar(): void {
    this.api.equipo(this.idProceso()).subscribe({
      next: (e) => (this.equipo = e),
      error: (e) => (this.error = mensajeDeError(e)),
    });
    this.api.historialEquipo(this.idProceso()).subscribe({
      next: (h) => (this.historial = h),
      error: () => (this.historial = []),
    });
  }

  agregar(): void {
    if (!this.idPersona) return;
    this.error = this.mensaje = '';
    this.api.agregarIntegrante(this.idProceso(), this.idPersona, this.rol).subscribe({
      next: () => {
        this.idPersona = null;
        this.cargar();
      },
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  quitar(i: Integrante): void {
    if (!confirm(`¿Quitar a ${i.nombre || i.idPersona} del equipo del proceso?`)) return;
    this.error = this.mensaje = '';
    this.api.quitarIntegrante(this.idProceso(), i.idPersona).subscribe({
      next: () => this.cargar(),
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  relevar(form: NgForm): void {
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }
    this.guardando = true;
    this.error = this.mensaje = '';
    this.api.relevarResponsable(this.idProceso(), this.relevo).subscribe({
      next: (r) => {
        const nuevo = this.personal.find((a) => a.idPersona === r.idNuevo);
        const quien = nuevo ? `${nuevo.nombres} ${nuevo.apellidos}` : String(r.idNuevo);
        const pasaron = [
          r.terminosTrasladados ? `${r.terminosTrasladados} término(s)` : '',
          r.audienciasTrasladadas ? `${r.audienciasTrasladadas} audiencia(s)` : '',
        ].filter(Boolean);
        this.mensaje =
          `${quien} quedó a cargo del proceso.` +
          (pasaron.length ? ` Se le pasaron ${pasaron.join(' y ')}.` : '') +
          ' Se avisó a los dos abogados.';
        this.guardando = false;
        this.relevando = false;
        this.relevo = this.relevoVacio();
        this.cargar();
        this.cambio.emit();
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.guardando = false;
      },
    });
  }

  nombreMotivo(m?: string | null): string {
    return this.motivos.find((x) => x.valor === m)?.etiqueta ?? (m ? etiqueta(m) : 'Sin motivo indicado');
  }

  ayudaMotivo(m: MotivoRelevo | null): string {
    return this.motivos.find((x) => x.valor === m)?.ayuda ?? '';
  }

  private relevoVacio(): RelevoSolicitud {
    return { idResponsable: null, motivo: null, nota: '', anteriorQuedaDeApoyo: false, trasladarPendientes: true };
  }
}
