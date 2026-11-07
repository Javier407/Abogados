import { Component, afterRenderEffect, computed, inject, signal, untracked, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowRight, lucideCalendarClock, lucideFolderOpen, lucideFolderPlus, lucideHandCoins, lucideReceipt, lucideUser,
  lucideUserPlus, lucideUsers
} from '@ng-icons/lucide';
import { HlmCommandImports } from '@spartan-ng/helm/command';
import type { BrnDialogState } from '@spartan-ng/brain/dialog';
import { BrnCommand } from '@spartan-ng/brain/command';

import { ApiService } from '../core/api.service';
import { AuthService, EDITAN_CLIENTES, JURIDICO, VEN_HONORARIOS } from '../core/auth.service';
import { Cliente, Proceso } from '../core/modelos';
import { PaletaService } from './paleta.service';

interface Accion { id: string; texto: string; icono: string; ruta: string; params?: Record<string, string | number>; }

/**
 * Búsqueda global (Ctrl+K o ⌘K): procesos por radicado o asunto, clientes por nombre o documento, y atajos
 * a las acciones que el rol puede hacer. Enter abre lo elegido; Esc cierra.
 */
@Component({
  selector: 'app-paleta-comandos',
  standalone: true,
  imports: [HlmCommandImports, NgIcon],
  providers: [provideIcons({
    lucideArrowRight, lucideCalendarClock, lucideFolderOpen, lucideFolderPlus, lucideHandCoins, lucideReceipt, lucideUser,
    lucideUserPlus, lucideUsers
  })],
  template: `
    <hlm-command-dialog [state]="estado()" (stateChange)="cambio($event)" title="Buscar en Lexora"
      description="Busque procesos y clientes, o elija una acción" dialogContentClass="w-[min(640px,calc(100vw-2rem))] max-w-none sm:max-w-none">
      <!-- Se recrea en cada apertura: búsqueda vacía y sin elemento activo viejo -->
      @for (k of [apertura()]; track k) {
      <hlm-command [filter]="filtro" (searchChange)="buscar($event)">
        <hlm-command-input placeholder="Radicado, asunto, cliente o acción…" />
        @if (buscando()) { <div class="estado" role="status">Buscando…</div> }
        <div *hlmCommandEmptyState hlmCommandEmpty role="status">Sin resultados para «{{ texto() }}».</div>
        <!-- Sin opciones visibles la lista se oculta: un listbox vacío no es válido para los lectores de pantalla -->
        <hlm-command-list [class.hidden]="!hayVisibles()">

          @if (procesos().length) {
            <hlm-command-group>
              <hlm-command-group-label>Procesos</hlm-command-group-label>
              @for (p of procesos(); track p.idProceso) {
                <button hlm-command-item [value]="'p:' + p.idProceso" (selected)="ir(['/procesos', p.idProceso])">
                  <ng-icon name="lucideFolderOpen" />
                  <span class="resultado"><strong>{{ p.asunto }}</strong><small class="mono">{{ p.radicadoLegible }}</small></span>
                </button>
              }
            </hlm-command-group>
          }
          @if (clientes().length) {
            <hlm-command-group>
              <hlm-command-group-label>Clientes</hlm-command-group-label>
              @for (c of clientes(); track c.idCliente) {
                <button hlm-command-item [value]="'c:' + c.idCliente" (selected)="ir(['/clientes'], { id: c.idCliente! })">
                  <ng-icon name="lucideUser" />
                  <span class="resultado"><strong>{{ c.nombre }}</strong><small>{{ c.tipoDocumento }} {{ c.numeroDocumento }}</small></span>
                </button>
              }
            </hlm-command-group>
          }
          <hlm-command-group>
            <hlm-command-group-label>Acciones</hlm-command-group-label>
            @for (a of acciones(); track a.id) {
              <button hlm-command-item [value]="a.texto" (selected)="ir([a.ruta], a.params)">
                <ng-icon [name]="a.icono" /> {{ a.texto }}
              </button>
            }
          </hlm-command-group>
        </hlm-command-list>
      </hlm-command>
      }
    </hlm-command-dialog>
  `,
  styles: [`
    .resultado { display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
    .resultado strong { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .resultado small { font-size: 12px; }
    .estado { padding: 10px 12px; color: var(--muted); font-size: 13px; }
  `]
})
export class PaletaComandosComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly paleta = inject(PaletaService);

  readonly estado = computed<BrnDialogState>(() => this.paleta.abierta() ? 'open' : 'closed');
  readonly texto = signal('');
  readonly apertura = signal(0);
  readonly hayVisibles = signal(true);
  private readonly comando = viewChild(BrnCommand);
  readonly buscando = signal(false);
  readonly procesos = signal<Proceso[]>([]);
  readonly clientes = signal<Cliente[]>([]);
  private readonly consultas = new Subject<string>();

  /** Los resultados del servidor ya vienen filtrados; las acciones se filtran por su texto. */
  readonly filtro = (valor: string, busqueda: string) =>
    /^[pc]:\d+$/.test(valor) || normalizar(valor).includes(normalizar(busqueda));

  readonly acciones = computed<Accion[]>(() => {
    const a = this.auth;
    const lista: Accion[] = [];
    if (a.tiene(...JURIDICO)) lista.push({ id: 'np', texto: 'Nuevo proceso', icono: 'lucideFolderPlus', ruta: '/procesos/nuevo' });
    if (a.tiene(...EDITAN_CLIENTES)) lista.push({ id: 'nc', texto: 'Nuevo cliente', icono: 'lucideUserPlus', ruta: '/clientes', params: { nuevo: 1 } });
    lista.push({ id: 'ag', texto: 'Ir a la agenda de términos', icono: 'lucideCalendarClock', ruta: '/terminos' });
    lista.push({ id: 'pr', texto: 'Ir a procesos', icono: 'lucideFolderOpen', ruta: '/procesos' });
    lista.push({ id: 'cl', texto: 'Ir a clientes', icono: 'lucideUsers', ruta: '/clientes' });
    if (a.tiene(...VEN_HONORARIOS)) {
      lista.push({ id: 'ho', texto: 'Ir a honorarios', icono: 'lucideHandCoins', ruta: '/honorarios' });
      lista.push({ id: 'fa', texto: 'Ir a facturación', icono: 'lucideReceipt', ruta: '/facturacion' });
    }
    return lista;
  });

  constructor() {
    this.consultas.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        if (q.trim().length < 2) return of({ procesos: [] as Proceso[], clientes: [] as Cliente[] });
        this.buscando.set(true);
        return forkJoin({
          procesos: this.api.procesos({ q }).pipe(catchError(() => of([] as Proceso[]))),
          clientes: this.api.clientes(q).pipe(catchError(() => of([] as Cliente[])))
        });
      }),
      takeUntilDestroyed()
    ).subscribe(r => {
      this.procesos.set(r.procesos.slice(0, 6));
      this.clientes.set(r.clientes.slice(0, 5));
      this.buscando.set(false);
    });
    // Después de pintar resultados o filtrar, se corrige el elemento activo.
    afterRenderEffect({ read: () => {
      this.procesos(); this.clientes(); this.texto(); this.apertura(); this.comando();
      untracked(() => this.reactivar());
    } });
  }

  /**
   * Cuando cambian los resultados, spartan no mueve el elemento activo: el campo quedaría señalando una opción
   * que ya no existe (aria-activedescendant inválido). Se activa la primera visible, o ninguna.
   */
  private reactivar(): void {
    const c = this.comando();
    if (!c) return;
    const visibles = c.items().filter(i => i.visible() && !i.disabled);
    this.hayVisibles.set(visibles.length > 0);
    if (visibles.length) c.keyManager.setFirstItemActive(); else c.keyManager.setActiveItem(-1);
  }

  buscar(q: string): void {
    this.texto.set(q);
    this.consultas.next(q);
  }

  cambio(e: BrnDialogState): void {
    if (e === 'closed') {
      this.paleta.cerrar();
      this.apertura.update(n => n + 1);
      this.texto.set('');
      this.consultas.next('');
      this.procesos.set([]);
      this.clientes.set([]);
    }
  }

  ir(ruta: (string | number)[], params?: Record<string, string | number>): void {
    this.paleta.cerrar();
    this.router.navigate(ruta, params ? { queryParams: params } : {});
  }
}

function normalizar(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
