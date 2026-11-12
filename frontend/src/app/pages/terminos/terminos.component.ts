import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import {
  SITUACIONES,
  Situacion,
  Termino,
  avancePlazo,
  diasNoContados,
  etiqueta,
  textoRestantes,
} from '../../core/modelos';
import { SesionService } from '../../core/sesion.service';
import { PestanasAgendaComponent } from '../../ui/pestanas-agenda.component';

/**
 * Términos: las fechas límite para actuar en cada proceso. Cada fila dice qué hay que hacer, en qué proceso, cuándo
 * vence y cómo se contó el plazo (notificación + días hábiles), para que se entienda sin conocer el sistema.
 */
@Component({
  selector: 'app-terminos',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, PestanasAgendaComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './terminos.component.html',
  styleUrl: './terminos.component.css',
})
export class TerminosComponent implements OnInit {
  private api = inject(ApiService);
  readonly sesion = inject(SesionService);
  readonly situaciones = SITUACIONES;
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;
  readonly avance = avancePlazo;
  readonly noContados = diasNoContados;

  terminos: Termino[] = [];
  situacion: Situacion | '' = '';
  /** De quién son los términos: 'todos', 'mios' o el documento de un abogado. */
  quien = 'todos';

  cargando = false;
  revisando = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.quien = this.sesion.idPersona() ? 'mios' : 'todos';
    this.cargar();
  }

  private get idResponsable(): number | undefined {
    if (this.quien === 'todos') return undefined;
    if (this.quien === 'mios') return this.sesion.idPersona() ?? undefined;
    return Number(this.quien);
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.api
      .terminos({ situacion: this.situacion || undefined, idResponsable: this.idResponsable })
      .subscribe({
        next: (t) => {
          // Primero lo urgente: vencidos y por vencer arriba, cumplidos al final.
          const orden = { VENCIDO: 0, POR_VENCER: 1, AL_DIA: 2, CUMPLIDO: 3 } as Record<
            string,
            number
          >;
          this.terminos = t.sort(
            (a, b) =>
              orden[a.situacion] - orden[b.situacion] ||
              a.fechaVencimiento.localeCompare(b.fechaVencimiento),
          );
          this.cargando = false;
        },
        error: (e) => {
          this.error = mensajeDeError(e);
          this.cargando = false;
        },
      });
  }

  contar(situacion: string): number {
    return this.terminos.filter((t) => t.situacion === situacion).length;
  }

  /** Los contadores también filtran: un clic deja solo esa situación y otro la quita. */
  filtrar(s: Situacion): void {
    this.situacion = this.situacion === s ? '' : s;
    this.cargar();
  }

  nombre(id: number): string {
    const a = this.sesion.abogados().find((x) => x.idPersona === id);
    return a ? `${a.nombres} ${a.apellidos}` : String(id);
  }

  cumplir(t: Termino): void {
    this.mensaje = this.error = '';
    this.api.cumplirTermino(t.idTermino).subscribe({
      next: () => {
        this.mensaje = `Listo: «${t.descripcion}» quedó como cumplido.`;
        this.cargar();
      },
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  revisar(): void {
    this.revisando = true;
    this.mensaje = this.error = '';
    this.api.revisarTerminos().subscribe({
      next: (r) => {
        this.revisando = false;
        this.mensaje =
          `Avisos enviados: ${r.porVencer} término(s) por vencer y ${r.vencidos} vencido(s). ` +
          'Cada responsable recibe el aviso en la app y por correo (una vez al día por término).';
        setTimeout(() => this.sesion.refrescarNoLeidos(), 1500);
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.revisando = false;
      },
    });
  }
}
