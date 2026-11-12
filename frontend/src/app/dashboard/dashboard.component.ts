import {
  Component,
  OnInit,
  computed,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowRight,
  lucideBell,
  lucideCalendarClock,
  lucideCircleAlert,
  lucideEye,
  lucideFolderPlus,
  lucideGavel,
  lucideSearch,
  lucideUserPlus,
} from '@ng-icons/lucide';

import { ApiService, mensajeDeError } from '../core/api.service';
import {
  AuthService,
  DIRECCION,
  EDITAN_CLIENTES,
  JURIDICO,
  JURIDICO_Y_DEPENDIENTES,
} from '../core/auth.service';
import {
  Audiencia,
  Aviso,
  Proceso,
  RadicadoVigilado,
  Termino,
  etiqueta,
  textoRestantes,
} from '../core/modelos';
import { SesionService } from '../core/sesion.service';
import { PaletaService } from '../ui/paleta.service';
import { contar } from '../core/animacion';
import { AnimoMascota, MascotaComponent, NOMBRE_ASISTENTE } from '../ui/mascota.component';

interface ConteoRama {
  rama: string;
  activos: number;
  total: number;
}

/**
 * Inicio «Mi día»: lo urgente de quien entra (sus términos, audiencias y avisos) arriba, y cada tarjeta lleva
 * al detalle. Socios y administradores ven además «La firma»: los totales y los procesos por rama.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe, NgIcon, MascotaComponent],
  providers: [
    provideIcons({
      lucideArrowRight,
      lucideBell,
      lucideCalendarClock,
      lucideCircleAlert,
      lucideEye,
      lucideFolderPlus,
      lucideGavel,
      lucideSearch,
      lucideUserPlus,
    }),
  ],
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private sesion = inject(SesionService);
  readonly paleta = inject(PaletaService);
  /** Lo que muestran las cifras mientras suben (vencidos, esta semana, audiencias, avisos). */
  readonly vista = signal([0, 0, 0, 0]);

  protected readonly creaProcesos = this.auth.tiene(...JURIDICO);
  protected readonly creaClientes = this.auth.tiene(...EDITAN_CLIENTES);
  protected readonly veFirma = this.auth.tiene(...DIRECCION);
  protected readonly veVigilancia = this.auth.tiene(...JURIDICO_Y_DEPENDIENTES);
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;

  readonly cargando = signal(true);
  readonly error = signal('');
  readonly asistente = NOMBRE_ASISTENTE;

  readonly nombre = computed(() => (this.auth.sesion()?.nombre ?? '').split(/\s+/)[0] ?? '');
  readonly saludo = (() => {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  })();
  readonly fecha = new Intl.DateTimeFormat('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());

  // Mi día
  readonly misTerminos = signal<Termino[]>([]);
  readonly audiencias = signal<Audiencia[]>([]);
  readonly audienciasMias = signal(true);
  readonly avisos = signal<Aviso[]>([]);
  readonly enAlerta = signal<RadicadoVigilado[]>([]);
  readonly vencidos = computed(() => this.misTerminos().filter((t) => t.situacion === 'VENCIDO'));
  readonly estaSemana = computed(() =>
    this.misTerminos().filter((t) => t.situacion === 'POR_VENCER'),
  );
  /** Lo que hay que atender: vencidos primero y luego por fecha de vencimiento. */
  readonly urgentes = computed(() =>
    [
      ...this.vencidos(),
      ...this.estaSemana(),
      ...this.misTerminos().filter((t) => t.situacion === 'AL_DIA'),
    ].slice(0, 6),
  );

  /** Toga piensa mientras carga y se alegra si no hay nada urgente. */
  readonly animo = computed<AnimoMascota>(() =>
    this.cargando() ? 'pensando' : this.vencidos().length || this.estaSemana().length ? 'neutro' : 'contento',
  );

  /** Lo que Toga le dice a quien entra, en una frase. */
  readonly resumenToga = computed(() => {
    if (this.cargando()) return 'Estoy revisando su agenda…';
    const v = this.vencidos().length;
    const s = this.estaSemana().length;
    const a = this.audiencias().length;
    const partes: string[] = [];
    if (v) partes.push(`${v} término${v === 1 ? '' : 's'} vencido${v === 1 ? '' : 's'}`);
    if (s) partes.push(`${s} por vencer en 5 días hábiles`);
    if (a && this.audienciasMias()) partes.push(`${a} audiencia${a === 1 ? '' : 's'} en los próximos 14 días`);
    if (!partes.length) return 'Todo está al día. Buen momento para adelantar trabajo.';
    return `Hoy tiene ${partes.join(', ').replace(/, ([^,]*)$/, ' y $1')}.` + (v ? ' Empiece por los vencidos.' : '');
  });

  /** Sus términos pendientes por situación, para el anillo. */
  readonly reparto = computed(() => {
    const t = this.misTerminos();
    const vencidos = t.filter((x) => x.situacion === 'VENCIDO').length;
    const porVencer = t.filter((x) => x.situacion === 'POR_VENCER').length;
    const conTiempo = t.filter((x) => x.situacion === 'AL_DIA').length;
    const total = vencidos + porVencer + conTiempo;
    const p = (n: number) => (total ? (n / total) * 100 : 0);
    const a = p(vencidos);
    const b = a + p(porVencer);
    return {
      total, vencidos, porVencer, conTiempo,
      fondo: total
        ? `conic-gradient(var(--danger) 0 ${a}%, var(--warn) ${a}% ${b}%, var(--ok) ${b}% 100%)`
        : 'conic-gradient(var(--surface-3) 0 100%)',
    };
  });

  // La firma
  readonly firma = signal<{
    procesos: number;
    activos: number;
    clientes: number;
    abogados: number;
    porRama: ConteoRama[];
    recientes: Proceso[];
  } | null>(null);

  ngOnInit(): void {
    const yo = this.sesion.idPersona();
    const hoy = iso(new Date());
    const en14 = iso(new Date(Date.now() + 14 * 864e5));
    forkJoin({
      terminos: this.api
        .terminos(yo ? { idResponsable: yo } : {})
        .pipe(catchError(() => of([] as Termino[]))),
      audienciasMias: yo
        ? this.api.audiencias(hoy, en14, yo).pipe(catchError(() => of([] as Audiencia[])))
        : of([] as Audiencia[]),
      audienciasTodas: this.api.audiencias(hoy, en14).pipe(catchError(() => of([] as Audiencia[]))),
      avisos: yo
        ? this.api.avisos(yo, true).pipe(catchError(() => of([] as Aviso[])))
        : of([] as Aviso[]),
      vigilados: this.veVigilancia
        ? this.api.radicadosVigilados().pipe(catchError(() => of([] as RadicadoVigilado[])))
        : of([] as RadicadoVigilado[]),
    }).subscribe({
      next: (r) => {
        this.misTerminos.set(
          r.terminos
            .filter((t) => t.estado === 'PENDIENTE')
            .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)),
        );
        // Si no tiene audiencias asignadas, se muestran las de la firma (para no dejar la tarjeta vacía sin razón).
        this.audienciasMias.set(r.audienciasMias.length > 0);
        this.audiencias.set(
          (r.audienciasMias.length ? r.audienciasMias : r.audienciasTodas)
            .sort((a, b) => (a.fecha + (a.hora ?? '')).localeCompare(b.fecha + (b.hora ?? '')))
            .slice(0, 5),
        );
        this.avisos.set(r.avisos.slice(0, 5));
        this.enAlerta.set(r.vigilados.filter((v) => v.activo && v.enAlerta));
        this.cargando.set(false);
        setTimeout(() => this.animar());
      },
      error: (e) => {
        this.error.set(mensajeDeError(e, 'No se pudo conectar con el servidor.'));
        this.cargando.set(false);
      },
    });
    if (this.veFirma) this.cargarFirma();
  }

  private cargarFirma(): void {
    forkJoin({
      procesos: this.api.procesos(),
      clientes: this.api.clientes(),
      abogados: this.api.abogados(true),
    }).subscribe({
      next: ({ procesos, clientes, abogados }) =>
        this.firma.set({
          procesos: procesos.length,
          activos: procesos.filter((p) => p.estado === 'ACTIVO').length,
          clientes: clientes.length,
          abogados: abogados.length,
          porRama: agruparPorRama(procesos),
          recientes: procesos.slice(0, 5),
        }),
      error: () => this.firma.set(null),
    });
  }

  private animar(): void {
    const valores = [
      this.vencidos().length,
      this.estaSemana().length,
      this.audiencias().length,
      this.avisos().length,
    ];
    valores.forEach((v, i) =>
      contar(v, (n) => this.vista.update((l) => l.map((x, j) => (j === i ? n : x)))),
    );
  }

  ancho(c: ConteoRama, porRama: ConteoRama[]): number {
    return Math.round((c.total / (porRama[0]?.total || 1)) * 100);
  }

  enlaceAviso(a: Aviso): (string | number)[] {
    return a.referencia?.id_proceso ? ['/procesos', a.referencia.id_proceso] : ['/avisos'];
  }
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function agruparPorRama(procesos: Proceso[]): ConteoRama[] {
  const mapa = new Map<string, ConteoRama>();
  for (const p of procesos) {
    const c = mapa.get(p.rama) ?? { rama: p.rama, activos: 0, total: 0 };
    c.total++;
    if (p.estado === 'ACTIVO') c.activos++;
    mapa.set(p.rama, c);
  }
  return [...mapa.values()].sort((a, b) => b.total - a.total);
}
