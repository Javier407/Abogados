import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { SITUACIONES, Termino, etiqueta, textoRestantes } from '../../core/modelos';
import { SesionService } from '../../core/sesion.service';

/** Agenda de términos: lo que vence, lo vencido y lo cumplido, con la revisión de avisos. */
@Component({
  selector: 'app-terminos',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './terminos.component.html'
})
export class TerminosComponent implements OnInit {
  private api = inject(ApiService);
  readonly sesion = inject(SesionService);
  readonly situaciones = SITUACIONES;
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;

  terminos: Termino[] = [];
  situacion = '';
  soloMios = false;

  cargando = false;
  revisando = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.soloMios = !!this.sesion.idPersona();
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.api.terminos({
      situacion: this.situacion || undefined,
      idResponsable: this.soloMios ? this.sesion.idPersona() : undefined
    }).subscribe({
      next: t => {
        // Primero lo urgente: vencidos y por vencer arriba, cumplidos al final.
        const orden = { VENCIDO: 0, POR_VENCER: 1, AL_DIA: 2, CUMPLIDO: 3 } as Record<string, number>;
        this.terminos = t.sort((a, b) => orden[a.situacion] - orden[b.situacion]
          || a.fechaVencimiento.localeCompare(b.fechaVencimiento));
        this.cargando = false;
      },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  contar(situacion: string): number {
    return this.terminos.filter(t => t.situacion === situacion).length;
  }

  nombre(id: number): string {
    const a = this.sesion.abogados().find(x => x.idPersona === id);
    return a ? `${a.nombres} ${a.apellidos}` : String(id);
  }

  cumplir(t: Termino): void {
    this.mensaje = this.error = '';
    this.api.cumplirTermino(t.idTermino).subscribe({
      next: () => { this.mensaje = `Término "${t.descripcion}" marcado como cumplido.`; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  revisar(): void {
    this.revisando = true;
    this.mensaje = this.error = '';
    this.api.revisarTerminos().subscribe({
      next: r => {
        this.revisando = false;
        this.mensaje = `Revisión enviada: ${r.porVencer} término(s) por vencer y ${r.vencidos} vencido(s). `
          + 'Los responsables reciben el aviso en la app y por correo (una vez al día por término).';
        setTimeout(() => this.sesion.refrescarNoLeidos(), 1500);
      },
      error: e => { this.error = mensajeDeError(e); this.revisando = false; }
    });
  }
}
