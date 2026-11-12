import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideShieldAlert, lucideShieldCheck } from '@ng-icons/lucide';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Cuenta, FiltroAuditoria, RegistroAuditoria, RevisionAuditoria } from '../../core/modelos';

const METODOS = ['GET', 'POST', 'PUT', 'DELETE'];
const QUE_HACE: Record<string, string> = { GET: 'Consultó', POST: 'Creó o envió', PUT: 'Cambió', DELETE: 'Borró' };

/**
 * Bitácora de auditoría (solo ADMIN, docs/14-auditoria.md): cada petición a la API a nombre de quien la hizo,
 * también las rechazadas. Nadie la puede cambiar ni borrar: cada registro lleva el hash del anterior, y aquí se
 * revisa que la cadena siga cuadrando, completa o desde un sello guardado en el PC.
 */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [DatePipe, FormsModule, NgIcon],
  providers: [provideIcons({ lucideShieldCheck, lucideShieldAlert })],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Auditoría</h1>
        <p>
          Quién hizo qué y cuándo: cada petición a la aplicación, también las que se le negaron. Nadie puede
          cambiar ni borrar estos registros, y cada uno lleva el hash del anterior.
        </p>
        <div class="acciones">
          <button class="btn btn-primary" type="button" (click)="revisar()" [disabled]="revisando()">
            {{ revisando() ? 'Revisando…' : 'Revisar la cadena' }}
          </button>
        </div>
      </div>

      @if (error()) {
        <div class="alerta alerta-error" role="alert">{{ error() }}</div>
      }

      <div aria-live="polite">
        @if (revision(); as r) {
          <div
            class="card revision"
            [class.revision-ok]="r.cadena.integra"
            [class.revision-mal]="!r.cadena.integra"
          >
            <ng-icon [name]="r.cadena.integra ? 'lucideShieldCheck' : 'lucideShieldAlert'" size="28" aria-hidden="true" />
            <div>
              @if (r.cadena.integra) {
                <strong>La cadena cuadra</strong>
                <p>
                  {{ r.cadena.revisados }} registro(s) revisado(s){{ desdeSello() ? ' desde el sello' : '' }}.
                  Sello actual: registro <span class="mono">{{ r.cadena.ultimoId }}</span>,
                  hash <span class="mono hash" [title]="r.cadena.ultimoHash">{{ r.cadena.ultimoHash }}</span>
                </p>
              } @else {
                <strong>La cadena no cuadra desde el registro {{ r.cadena.roturaEn }}</strong>
                <p>
                  {{ r.cadena.motivo }}. Alguien modificó la bitácora por fuera de la aplicación: avise a la
                  dirección y compare con los sellos guardados en el PC (docs/14-auditoria.md).
                </p>
              }
              <small>Revisada {{ r.momento | date: 'd MMM y, h:mm:ss a' }}</small>
              @if (r.escritura.almacen === 'memoria') {
                <p class="aviso">
                  El gateway no tiene base de datos configurada: la bitácora está solo en memoria.
                </p>
              }
              @if (r.escritura.perdidos > 0) {
                <p class="aviso">
                  {{ r.escritura.perdidos }} acceso(s) no alcanzaron a guardarse desde el último arranque del
                  gateway (siguen en su registro AUDITORIA).
                </p>
              }
            </div>
          </div>
        }
      </div>

      <details class="card sello">
        <summary>Revisar desde un sello guardado en el PC</summary>
        <form class="sello-form" (ngSubmit)="revisarSello()">
          <div class="form-group">
            <label class="form-label" for="selloId">Número del registro</label>
            <input id="selloId" class="form-input" type="number" min="1" name="selloId" [(ngModel)]="selloId" />
          </div>
          <div class="form-group sello-hash">
            <label class="form-label" for="selloHash">Hash</label>
            <input
              id="selloHash"
              class="form-input mono"
              name="selloHash"
              [(ngModel)]="selloHash"
              maxlength="64"
              spellcheck="false"
              autocomplete="off"
            />
          </div>
          <button class="btn btn-secondary" type="submit" [disabled]="revisando()">Revisar</button>
        </form>
        <p class="form-ayuda">
          Cada respaldo diario deja un archivo <span class="mono">sello-auditoria-….txt</span> con el número y el
          hash del último registro. Si el servidor ya no tiene ese mismo hash, la bitácora se rehízo o se recortó.
        </p>
      </details>

      <form class="filtros" (ngSubmit)="buscar()" aria-label="Filtrar la bitácora">
        <select class="form-input" name="usuario" [(ngModel)]="filtro.usuario" aria-label="Persona">
          <option [ngValue]="null">Todas las personas</option>
          <option value="anonimo">Sin sesión</option>
          @for (c of cuentas(); track c.idUsuario) {
            <option [value]="'' + c.idUsuario">{{ c.nombre }}</option>
          }
        </select>
        <select class="form-input" name="metodo" [(ngModel)]="filtro.metodo" aria-label="Tipo de acción">
          <option [ngValue]="null">Toda acción</option>
          @for (m of metodos; track m) {
            <option [value]="m">{{ queHace(m) }}</option>
          }
        </select>
        <input
          class="form-input"
          name="ruta"
          [(ngModel)]="filtro.ruta"
          placeholder="Ruta, p. ej. /procesos/34"
          aria-label="Ruta contiene"
        />
        <input class="form-input" type="date" name="desde" [(ngModel)]="filtro.desde" aria-label="Desde el día" />
        <input class="form-input" type="date" name="hasta" [(ngModel)]="filtro.hasta" aria-label="Hasta el día" />
        <label class="resumen-chip">
          <input type="checkbox" name="fallos" [(ngModel)]="filtro.fallos" /> Solo negadas y errores
        </label>
        <button class="btn btn-primary" type="submit">Buscar</button>
      </form>

      <section class="card">
        <div class="tabla-wrapper">
          <table class="tabla">
            <caption class="sr-only">Registros de la bitácora de auditoría, los más recientes primero</caption>
            <thead>
              <tr>
                <th scope="col">Cuándo</th>
                <th scope="col">Quién</th>
                <th scope="col">Qué</th>
                <th scope="col">Resultado</th>
                <th scope="col">Desde</th>
                <th scope="col">Registro</th>
              </tr>
            </thead>
            <tbody>
              @for (r of registros(); track r.id) {
                <tr>
                  <td class="fecha-celda">{{ r.acceso.momento | date: 'd MMM y, h:mm:ss a' }}</td>
                  <td>
                    {{ nombre(r.acceso.usuario) }}
                    @if (r.acceso.roles) {
                      <small class="detalle">{{ r.acceso.roles }}</small>
                    }
                  </td>
                  <td>
                    {{ queHace(r.acceso.metodo) }}
                    <small class="detalle mono">{{ r.acceso.metodo }} {{ r.acceso.ruta }}</small>
                  </td>
                  <td>
                    <span [class]="'badge ' + claseEstado(r.acceso.estado)">{{
                      textoEstado(r.acceso.estado)
                    }}</span>
                    <small class="detalle">{{ r.acceso.duracionMs }} ms</small>
                  </td>
                  <td class="mono">{{ r.acceso.ip ?? '—' }}</td>
                  <td>
                    <span class="mono">{{ r.id }}</span>
                    <small class="detalle mono hash" [title]="r.hash">{{ r.hash.slice(0, 12) }}…</small>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="empty-state">
                    {{ cargando() ? 'Cargando…' : 'No hay registros con esos filtros.' }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (siguiente() !== null) {
          <div class="mas">
            <button class="btn btn-secondary" type="button" (click)="verMas()" [disabled]="cargando()">
              Ver más antiguos
            </button>
          </div>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .revision {
        display: flex;
        gap: 14px;
        align-items: flex-start;
        padding: 16px 18px;
        margin-bottom: 16px;
        border-left: 4px solid var(--line);
      }
      .revision p {
        margin: 4px 0;
      }
      .revision small {
        color: var(--muted);
      }
      .revision-ok {
        border-left-color: var(--ok);
      }
      .revision-ok ng-icon {
        color: var(--ok);
      }
      .revision-mal {
        border-left-color: var(--danger);
      }
      .revision-mal ng-icon {
        color: var(--danger);
      }
      .aviso {
        color: var(--ink);
        font-weight: 600;
      }
      .hash {
        word-break: break-all;
      }
      .sello {
        padding: 12px 18px;
        margin-bottom: 16px;
      }
      .sello summary {
        cursor: pointer;
        font-weight: 600;
      }
      .sello-form {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-end;
        margin-top: 12px;
      }
      .sello-form .form-group {
        margin: 0;
      }
      .sello-hash {
        flex: 1 1 320px;
      }
      .detalle {
        display: block;
        color: var(--muted);
        font-size: 12px;
      }
      td .mono {
        font-size: 12px;
      }
      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
        white-space: nowrap;
      }
      .mas {
        display: flex;
        justify-content: center;
        padding: 12px;
      }
    `,
  ],
})
export class AuditoriaComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly metodos = METODOS;
  readonly registros = signal<RegistroAuditoria[]>([]);
  readonly siguiente = signal<number | null>(null);
  readonly cuentas = signal<Cuenta[]>([]);
  readonly revision = signal<RevisionAuditoria | null>(null);
  readonly desdeSello = signal(false);
  readonly revisando = signal(false);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  filtro: FiltroAuditoria = { usuario: null, metodo: null, ruta: null, fallos: false, desde: null, hasta: null };
  selloId: number | null = null;
  selloHash = '';

  ngOnInit(): void {
    this.api.cuentas().subscribe({ next: c => this.cuentas.set(c), error: () => this.cuentas.set([]) });
    this.buscar();
  }

  buscar(): void {
    this.registros.set([]);
    this.cargar(null);
  }

  verMas(): void {
    this.cargar(this.siguiente());
  }

  private cargar(antesDe: number | null): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.auditoria({ ...this.filtro, antesDe, limite: 50 }).subscribe({
      next: p => {
        this.registros.update(r => [...r, ...p.registros]);
        this.siguiente.set(p.siguiente);
        this.cargando.set(false);
      },
      error: e => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  revisar(): void {
    this.hacerRevision();
  }

  revisarSello(): void {
    const hash = this.selloHash.trim().toLowerCase();
    if (!this.selloId || this.selloId < 1 || !/^[0-9a-f]{64}$/.test(hash)) {
      this.error.set('Escriba el número del registro y su hash de 64 caracteres, como están en el sello.');
      return;
    }
    this.hacerRevision({ id: this.selloId, hash });
  }

  private hacerRevision(sello?: { id: number; hash: string }): void {
    this.revisando.set(true);
    this.error.set(null);
    this.api.revisarAuditoria(sello).subscribe({
      next: r => {
        this.revision.set(r);
        this.desdeSello.set(!!sello);
        this.revisando.set(false);
      },
      error: e => {
        this.error.set(mensajeDeError(e));
        this.revisando.set(false);
      },
    });
  }

  nombre(usuario: string): string {
    if (usuario === 'anonimo') return 'Sin sesión';
    const c = this.cuentas().find(x => String(x.idUsuario) === usuario);
    return c ? `${c.nombre} (${usuario})` : usuario;
  }

  queHace(metodo: string): string {
    return QUE_HACE[metodo] ?? metodo;
  }

  textoEstado(estado: number | null): string {
    if (estado === null) return 'Cortada';
    if (estado === 401) return 'Sin sesión';
    if (estado === 403) return 'Negada';
    if (estado === 429) return 'Demasiadas';
    if (estado >= 500) return `Error ${estado}`;
    if (estado >= 400) return `Rechazada ${estado}`;
    return `Hecha ${estado}`;
  }

  claseEstado(estado: number | null): string {
    if (estado === null) return 'badge-muted';
    if (estado >= 500) return 'badge-danger';
    if (estado === 401 || estado === 403) return 'badge-warning';
    if (estado >= 400) return 'badge-muted';
    return 'badge-success';
  }
}
