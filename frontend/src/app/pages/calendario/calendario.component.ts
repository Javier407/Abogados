import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { DiaInhabil, Termino } from '../../core/modelos';
import { SesionService } from '../../core/sesion.service';

interface Dia {
  fecha: string;          // AAAA-MM-DD
  numero: number;
  delMes: boolean;
  hoy: boolean;
  finDeSemana: boolean;
  inhabil?: DiaInhabil;
  terminos: Termino[];
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre',
  'octubre', 'noviembre', 'diciembre'];

/** Mes con los días inhábiles judiciales y los vencimientos de términos de cada día. */
@Component({
  selector: 'app-calendario',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './calendario.component.html',
  styleUrl: './calendario.component.css'
})
export class CalendarioComponent implements OnInit {
  private api = inject(ApiService);
  readonly sesion = inject(SesionService);
  readonly diasSemana = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

  anio = new Date().getFullYear();
  mes = new Date().getMonth();          // 0 = enero
  semanas: Dia[][] = [];
  soloMios = false;
  seleccionado: Dia | null = null;
  error = '';

  get titulo(): string {
    return `${MESES[this.mes]} de ${this.anio}`;
  }

  ngOnInit(): void {
    this.cargar();
  }

  mover(delta: number): void {
    const d = new Date(this.anio, this.mes + delta, 1);
    this.anio = d.getFullYear();
    this.mes = d.getMonth();
    this.seleccionado = null;
    this.cargar();
  }

  hoy(): void {
    const d = new Date();
    this.anio = d.getFullYear();
    this.mes = d.getMonth();
    this.cargar();
  }

  cargar(): void {
    // La cuadrícula va de lunes a domingo: incluye días del mes anterior y del siguiente.
    const primero = new Date(this.anio, this.mes, 1);
    const inicio = new Date(primero);
    inicio.setDate(1 - ((primero.getDay() + 6) % 7));
    const fin = new Date(inicio);
    fin.setDate(inicio.getDate() + 41);
    const desde = iso(inicio), hasta = iso(fin);

    forkJoin({
      inhabiles: this.api.diasInhabiles(desde, hasta),
      terminos: this.api.terminos({ desde, hasta, idResponsable: this.soloMios ? this.sesion.idPersona() : undefined })
    }).subscribe({
      next: ({ inhabiles, terminos }) => {
        const porFecha = new Map(inhabiles.map(d => [d.fecha, d]));
        const hoy = iso(new Date());
        const dias: Dia[] = [];
        for (let i = 0; i < 42; i++) {
          const d = new Date(inicio);
          d.setDate(inicio.getDate() + i);
          const f = iso(d);
          dias.push({
            fecha: f, numero: d.getDate(), delMes: d.getMonth() === this.mes, hoy: f === hoy,
            finDeSemana: d.getDay() === 0 || d.getDay() === 6, inhabil: porFecha.get(f),
            terminos: terminos.filter(t => t.fechaVencimiento === f)
          });
        }
        this.semanas = Array.from({ length: 6 }, (_, s) => dias.slice(s * 7, s * 7 + 7));
        if (this.seleccionado) this.seleccionado = dias.find(d => d.fecha === this.seleccionado!.fecha) ?? null;
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  claseTermino(t: Termino): string {
    return 'chip-' + t.situacion.toLowerCase().replace('_', '-');
  }
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
