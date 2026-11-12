import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowLeft,
  lucideBriefcase,
  lucideCalendarClock,
  lucideGavel,
  lucideMail,
  lucidePhone,
  lucideUsers,
} from '@ng-icons/lucide';
import { catchError, forkJoin, of } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import {
  Abogado,
  Audiencia,
  ProcesoDeAbogado,
  Termino,
  etiqueta,
  textoRestantes,
} from '../../core/modelos';

/** Un tramo del «rango de respuesta»: cuánto tiempo queda para responder los términos del abogado. */
interface Rango {
  id: 'vencidos' | 'urgentes' | 'proximos' | 'holgados';
  nombre: string;
  ayuda: string;
  total: number;
  clase: string;
}

type Filtro = 'abiertos' | 'cargo' | 'apoyo' | 'cerrados';

const CERRADOS = ['TERMINADO', 'ARCHIVADO'];

/**
 * Ficha del abogado: qué casos tiene (y con qué papel), de qué tipo son, y su rango de respuesta: cuánto tiempo
 * le queda en los términos que corren a su nombre. Es de consulta; los datos del abogado se editan en la lista.
 */
@Component({
  selector: 'app-ficha-abogado',
  standalone: true,
  imports: [RouterLink, DatePipe, NgIcon],
  providers: [
    provideIcons({
      lucideArrowLeft,
      lucideBriefcase,
      lucideCalendarClock,
      lucideGavel,
      lucideMail,
      lucidePhone,
      lucideUsers,
    }),
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './ficha-abogado.component.html',
  styleUrl: './ficha-abogado.component.css',
})
export class FichaAbogadoComponent implements OnInit {
  private api = inject(ApiService);
  private ruta = inject(ActivatedRoute);
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;

  readonly idPersona = signal(0);
  readonly abogado = signal<Abogado | null>(null);
  readonly procesos = signal<ProcesoDeAbogado[]>([]);
  readonly terminos = signal<Termino[]>([]);
  readonly audiencias = signal<Audiencia[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly filtro = signal<Filtro>('abiertos');

  readonly abiertos = computed(() => this.procesos().filter((p) => !CERRADOS.includes(p.estado)));
  readonly aCargo = computed(() => this.abiertos().filter((p) => p.rol === 'RESPONSABLE'));
  readonly enApoyo = computed(() => this.abiertos().filter((p) => p.rol !== 'RESPONSABLE'));
  readonly cerrados = computed(() => this.procesos().filter((p) => CERRADOS.includes(p.estado)));

  readonly visibles = computed(() => {
    switch (this.filtro()) {
      case 'cargo':
        return this.aCargo();
      case 'apoyo':
        return this.enApoyo();
      case 'cerrados':
        return this.cerrados();
      default:
        return this.abiertos();
    }
  });

  /** Términos que corren a su nombre, del más urgente al más lejano. */
  readonly pendientes = computed(() =>
    this.terminos()
      .filter((t) => t.estado === 'PENDIENTE')
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)),
  );

  readonly rangos = computed<Rango[]>(() => {
    const p = this.pendientes();
    const cuenta = (desde: number, hasta: number) =>
      p.filter((t) => t.diasHabilesRestantes >= desde && t.diasHabilesRestantes <= hasta).length;
    return [
      {
        id: 'vencidos',
        nombre: 'Vencidos',
        ayuda: 'ya pasó la fecha',
        total: p.filter((t) => t.diasHabilesRestantes < 0).length,
        clase: 'vencido',
      },
      {
        id: 'urgentes',
        nombre: 'De hoy a 5 días hábiles',
        ayuda: 'hay que responder ya',
        total: cuenta(0, 5),
        clase: 'por-vencer',
      },
      {
        id: 'proximos',
        nombre: 'De 6 a 15 días hábiles',
        ayuda: 'para preparar',
        total: cuenta(6, 15),
        clase: 'al-dia',
      },
      {
        id: 'holgados',
        nombre: 'Más de 15 días hábiles',
        ayuda: 'con tiempo',
        total: cuenta(16, Number.MAX_SAFE_INTEGER),
        clase: 'al-dia',
      },
    ];
  });

  readonly mayorRango = computed(() => Math.max(1, ...this.rangos().map((r) => r.total)));

  /** Procesos abiertos agrupados por tipo de proceso, del más numeroso al menos. */
  readonly porTipo = computed(() => {
    const grupos = new Map<string, { tipo: string; rama: string; total: number; aCargo: number }>();
    for (const p of this.abiertos()) {
      const g = grupos.get(p.tipoProceso) ?? { tipo: p.tipoProceso, rama: p.rama, total: 0, aCargo: 0 };
      g.total++;
      if (p.rol === 'RESPONSABLE') g.aCargo++;
      grupos.set(p.tipoProceso, g);
    }
    return [...grupos.values()].sort((a, b) => b.total - a.total || a.tipo.localeCompare(b.tipo));
  });

  readonly mayorTipo = computed(() => Math.max(1, ...this.porTipo().map((g) => g.total)));

  ngOnInit(): void {
    this.ruta.paramMap.subscribe((p) => {
      this.idPersona.set(Number(p.get('id')));
      this.cargar();
    });
  }

  cargar(): void {
    const id = this.idPersona();
    this.cargando.set(true);
    this.error.set('');
    const hoy = new Date();
    const en30 = new Date(hoy.getTime() + 30 * 86_400_000);
    forkJoin({
      abogados: this.api.abogados(false),
      procesos: this.api.procesosDeAbogado(id),
      // Lo secundario no tumba la ficha: si falla, esa sección sale vacía.
      terminos: this.api.terminos({ idResponsable: id }).pipe(catchError(() => of([] as Termino[]))),
      audiencias: this.api
        .audiencias(fecha(hoy), fecha(en30), id)
        .pipe(catchError(() => of([] as Audiencia[]))),
    }).subscribe({
      next: (r) => {
        const a = r.abogados.find((x) => x.idPersona === id) ?? null;
        this.abogado.set(a);
        this.procesos.set(r.procesos);
        this.terminos.set(r.terminos);
        this.audiencias.set(r.audiencias);
        this.cargando.set(false);
        if (!a) this.error.set('No hay un abogado con ese documento.');
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  iniciales(a: Abogado): string {
    return ((a.nombres[0] ?? '') + (a.apellidos[0] ?? '')).toUpperCase();
  }

  papel(rol: string): string {
    return rol === 'RESPONSABLE' ? 'A cargo' : rol === 'APOYO' ? 'Apoyo' : 'Dependiente';
  }
}

function fecha(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
