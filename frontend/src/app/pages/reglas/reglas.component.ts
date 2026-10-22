import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CambioRegla, PruebaPatron, Rama, ReglaSolicitud, ReglaTermino, TipoProceso } from '../../core/modelos';

/**
 * Reglas de términos (F12). Con ellas el expediente sugiere un término cuando llega un auto o una sentencia.
 * Las ve todo el personal; las cambia y valida un socio. Cambiar lo que dice una regla (o los días de contestación
 * que usa) la deja otra vez sin validar, y la sugerencia lo advierte. Cada cambio queda en el historial.
 */
@Component({
  selector: 'app-reglas',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Reglas de términos</h1>
        <p>Cuando llega una providencia, el expediente sugiere los términos que abre según estas reglas.
          Una regla sin validar se muestra como "de referencia". @if (!esSocio) { Solo un socio puede cambiarlas o validarlas. }</p>
      </div>
      @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }

      <div class="resumen-bar">
        <span class="resumen-chip">Validadas <strong>{{ validadas }} de {{ activas }}</strong></span>
        <label class="resumen-chip chip-check"><input type="checkbox" [(ngModel)]="soloPendientes" /> Solo sin validar</label>
        <select class="form-input filtro-corto" [(ngModel)]="filtroProvidencia" aria-label="Tipo de providencia">
          <option value="">Autos y sentencias</option><option value="AUTO">Autos</option><option value="SENTENCIA">Sentencias</option>
        </select>
        @if (esSocio) { <button class="btn btn-secondary btn-sm" type="button" (click)="editar(null)">Nueva regla</button> }
      </div>

      @if (regla) {
        <section class="card">
          <div class="card-header"><h2>{{ idEditando ? 'Editar la regla ' + idEditando : 'Nueva regla' }}</h2></div>
          <div class="card-body">
            @if (idEditando && reglaOriginal?.validada) {
              <div class="alerta alerta-error">Esta regla está validada. Si cambia lo que dice, quedará otra vez sin validar.</div>
            }
            <div class="form-grid">
              <div class="form-group"><label class="form-label" for="rProv">Providencia</label>
                <select id="rProv" class="form-input" [(ngModel)]="regla.tipoProvidencia">
                  <option value="AUTO">Auto</option><option value="SENTENCIA">Sentencia</option></select></div>
              <div class="form-group"><label class="form-label" for="rRama">Rama</label>
                <select id="rRama" class="form-input" [(ngModel)]="regla.codigoRama" (ngModelChange)="regla.idTipo = null">
                  <option [ngValue]="null">Todas</option>
                  @for (r of ramas; track r.codigo) { <option [ngValue]="r.codigo">{{ r.nombre }}</option> }
                </select></div>
              <div class="form-group"><label class="form-label" for="rTipo">Tipo de proceso</label>
                <select id="rTipo" class="form-input" [(ngModel)]="regla.idTipo">
                  <option [ngValue]="null">Todos</option>
                  @for (t of tiposDe(regla.codigoRama); track t.idTipo) { <option [ngValue]="t.idTipo">{{ t.nombre }}</option> }
                </select></div>
              <div class="form-group"><label class="form-label" for="rLado">Lado del cliente</label>
                <select id="rLado" class="form-input" [(ngModel)]="regla.lado">
                  <option [ngValue]="null">Ambos</option><option value="DEMANDANTE">Demandante</option><option value="DEMANDADO">Demandado</option>
                </select></div>
              <div class="form-group form-group-full"><label class="form-label" for="rPatron">Patrón sobre la clase de la providencia</label>
                <input id="rPatron" class="form-input mono" [(ngModel)]="regla.patron" maxlength="200" placeholder="\\badmite|admisorio" />
                <small class="form-ayuda">Expresión regular sobre el texto sin tildes, en minúsculas y sin puntuación. "." se aplica a todas.</small></div>
              <div class="form-group form-group-full"><label class="form-label" for="rDesc">Término que abre</label>
                <input id="rDesc" class="form-input" [(ngModel)]="regla.descripcion" maxlength="150" placeholder="Contestar la demanda" /></div>
              <div class="form-group"><label class="form-label" for="rDias">Días hábiles</label>
                <input id="rDias" class="form-input" type="number" min="1" max="365" [(ngModel)]="regla.dias" placeholder="Los de contestación" /></div>
              <div class="form-group"><label class="form-label" for="rNorma">Norma</label>
                <input id="rNorma" class="form-input" [(ngModel)]="regla.norma" maxlength="120" placeholder="CGP art. 90" /></div>
              <div class="form-group"><label class="form-label" for="rPrio">Prioridad</label>
                <select id="rPrio" class="form-input" [(ngModel)]="regla.prioridad">
                  <option [ngValue]="1">Principal</option><option [ngValue]="2">Opcional ("si procede")</option></select></div>
              <div class="form-group"><span class="form-label">Estado</span>
                <label class="resumen-chip chip-check"><input type="checkbox" [(ngModel)]="regla.activa" /> Activa (sugiere términos)</label></div>
            </div>
            <div class="prueba-patron">
              <input class="form-input" [(ngModel)]="claseDePrueba" placeholder="Pruebe el patrón con la clase de un auto, p. ej. «Auto admite demanda»"
                     aria-label="Clase de providencia para probar el patrón" (keyup.enter)="probar()" />
              <button class="btn btn-secondary btn-sm" type="button" (click)="probar()" [disabled]="!claseDePrueba || !regla.patron">Probar</button>
              @if (prueba) {
                <span class="badge" [class.badge-success]="prueba.coincide" [class.badge-muted]="!prueba.coincide">
                  {{ prueba.coincide ? 'Coincide' : 'No coincide' }}</span>
                <small class="mono">{{ prueba.claseNormalizada }}</small>
              }
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="button" (click)="guardar()">Guardar</button>
              <button class="btn btn-secondary" type="button" (click)="regla = null">Cancelar</button>
            </div>
          </div>
        </section>
      }

      <section class="card">
        <div class="card-body">
          <div class="tabla-wrapper"><table class="tabla">
            <thead><tr><th>Providencia</th><th>Se aplica a</th><th>Patrón</th><th>Término</th><th>Días</th><th>Norma</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (r of visibles; track r.idRegla) {
                <tr [class.fila-inactiva]="!r.activa">
                  <td>{{ r.tipoProvidencia === 'AUTO' ? 'Auto' : 'Sentencia' }}
                    @if (r.prioridad === 2) { <span class="badge badge-muted">opcional</span> }</td>
                  <td>{{ aplicaA(r) }}</td>
                  <td class="mono">{{ r.patron }}</td>
                  <td class="titulo-celda">{{ r.descripcion }}</td>
                  <td>{{ r.dias ?? 'Contestación' }}</td>
                  <td>{{ r.norma }}</td>
                  <td>
                    @if (!r.activa) { <span class="badge badge-muted">Inactiva</span> }
                    @else if (r.validada) {
                      <span class="badge badge-success">Validada</span>
                      <small class="detalle-validacion">{{ r.nombreValidador || r.validadaPor }} · {{ r.validadaEn | date:'d MMM y' }}</small>
                    } @else { <span class="badge badge-warning">Sin validar</span> }
                  </td>
                  <td class="acciones-celda">
                    @if (esSocio) {
                      <button class="btn btn-secondary btn-sm" type="button" (click)="editar(r)">Editar</button>
                      @if (r.activa && !r.validada) {
                        <button class="btn btn-primary btn-sm" type="button" (click)="validar(r)">Validar</button>
                      }
                    }
                    <button class="btn btn-secondary btn-sm" type="button" (click)="verHistorial(r)"
                            [attr.aria-expanded]="historialDe === r.idRegla">Historial</button>
                  </td>
                </tr>
                @if (historialDe === r.idRegla) {
                  <tr class="fila-detalle"><td colspan="8">
                    @if (historial.length === 0) { <span class="form-ayuda">Sin cambios desde que se cargó la regla.</span> }
                    <ul class="lista-cambios">
                      @for (c of historial; track $index) {
                        <li><strong>{{ acciones[c.accion] }}</strong> · {{ c.nombre || c.idUsuario }} · {{ c.momento | date:'d MMM y, h:mm a' }}
                          @if (c.detalle) { <div class="form-ayuda">{{ c.detalle }}</div> }</li>
                      }
                    </ul>
                  </td></tr>
                }
              } @empty { <tr><td colspan="8" class="empty-state">No hay reglas con ese filtro.</td></tr> }
            </tbody>
          </table></div>
        </div>
      </section>

      <section class="card">
        <div class="card-header"><h2>Días para contestar por tipo de proceso</h2></div>
        <div class="card-body">
          <p class="form-ayuda">Los usan las reglas sin días propios (por ejemplo, "Contestar la demanda"). Cambiarlos deja esas reglas sin validar.</p>
          <div class="tabla-wrapper"><table class="tabla">
            <thead><tr><th>Rama</th><th>Tipo de proceso</th><th>Días hábiles</th>@if (esSocio) { <th></th> }</tr></thead>
            <tbody>
              @for (t of tipos; track t.idTipo) {
                <tr>
                  <td class="mono">{{ t.codigoRama }}</td><td>{{ t.nombre }}</td>
                  <td>@if (esSocio) {
                      <input class="form-input dias-input" type="number" min="1" max="365" [(ngModel)]="contestacion[t.idTipo]"
                             placeholder="No aplica" [attr.aria-label]="'Días para contestar en ' + t.nombre" />
                    } @else { {{ t.terminoContestacionDias ?? 'No aplica' }} }</td>
                  @if (esSocio) {
                    <td class="acciones-celda">
                      @if ((contestacion[t.idTipo] ?? null) !== (t.terminoContestacionDias ?? null)) {
                        <button class="btn btn-primary btn-sm" type="button" (click)="guardarContestacion(t)">Guardar</button>
                      }
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table></div>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .filtro-corto { width: auto; min-width: 180px; }
    .prueba-patron { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 6px 0 14px; }
    .prueba-patron .form-input { flex: 1 1 280px; }
    .detalle-validacion { display: block; color: var(--muted); font-size: 12px; margin-top: 3px; }
    .fila-inactiva td { color: var(--muted); }
    .fila-detalle td { background: var(--parchment-2); }
    .lista-cambios { margin: 0; padding-left: 18px; display: grid; gap: 6px; }
    .dias-input { width: 110px; }
  `]
})
export class ReglasComponent implements OnInit {
  private api = inject(ApiService);
  readonly esSocio = inject(AuthService).tiene('SOCIO');

  readonly acciones: Record<CambioRegla['accion'], string> = {
    CREADA: 'Creada', EDITADA: 'Editada', VALIDADA: 'Validada', ACTIVADA: 'Activada', DESACTIVADA: 'Desactivada'
  };
  reglas: ReglaTermino[] = [];
  ramas: Rama[] = [];
  tipos: TipoProceso[] = [];
  contestacion: Record<number, number | null> = {};
  filtroProvidencia = '';
  soloPendientes = false;
  regla: ReglaSolicitud | null = null;
  reglaOriginal: ReglaTermino | null = null;
  idEditando: number | null = null;
  claseDePrueba = '';
  prueba: PruebaPatron | null = null;
  historialDe: number | null = null;
  historial: CambioRegla[] = [];
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.cargar();
    forkJoin({ ramas: this.api.ramas(), tipos: this.api.tipos() }).subscribe({
      next: r => { this.ramas = r.ramas; this.ponerTipos(r.tipos); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  get visibles(): ReglaTermino[] {
    return this.reglas.filter(r => (!this.filtroProvidencia || r.tipoProvidencia === this.filtroProvidencia)
      && (!this.soloPendientes || (r.activa && !r.validada)));
  }

  get activas(): number { return this.reglas.filter(r => r.activa).length; }
  get validadas(): number { return this.reglas.filter(r => r.activa && r.validada).length; }

  cargar(): void {
    this.api.reglas().subscribe({ next: r => this.reglas = r, error: e => this.error = mensajeDeError(e) });
  }

  tiposDe(rama?: string | null): TipoProceso[] {
    return rama ? this.tipos.filter(t => t.codigoRama === rama) : this.tipos;
  }

  aplicaA(r: ReglaTermino): string {
    const tipo = this.tipos.find(t => t.idTipo === r.idTipo)?.nombre;
    const rama = this.ramas.find(x => x.codigo === r.codigoRama)?.nombre ?? r.codigoRama;
    const lado = r.lado === 'DEMANDANTE' ? ' · cliente demandante' : r.lado === 'DEMANDADO' ? ' · cliente demandado' : '';
    return (tipo ?? rama ?? 'Todas las ramas') + lado;
  }

  editar(r: ReglaTermino | null): void {
    this.limpiar();
    this.idEditando = r?.idRegla ?? null;
    this.reglaOriginal = r;
    this.regla = r
      ? { tipoProvidencia: r.tipoProvidencia, codigoRama: r.codigoRama ?? null, idTipo: r.idTipo ?? null, patron: r.patron,
          lado: r.lado ?? null, descripcion: r.descripcion, dias: r.dias ?? null, norma: r.norma, prioridad: r.prioridad,
          activa: r.activa }
      : { tipoProvidencia: 'AUTO', codigoRama: null, idTipo: null, patron: '', lado: null, descripcion: '', dias: null,
          norma: '', prioridad: 1, activa: true };
  }

  probar(): void {
    if (!this.regla?.patron || !this.claseDePrueba) return;
    this.api.probarPatron(this.regla.patron, this.claseDePrueba).subscribe({
      next: p => { this.prueba = p; this.error = ''; },
      error: e => { this.prueba = null; this.error = mensajeDeError(e); }
    });
  }

  guardar(): void {
    if (!this.regla) return;
    this.mensaje = this.error = '';
    this.api.guardarRegla(this.idEditando, this.regla).subscribe({
      next: r => {
        this.mensaje = this.reglaOriginal?.validada && !r.validada
          ? 'Regla guardada. Quedó sin validar: falta que un socio la revise.' : 'Regla guardada.';
        this.regla = null;
        this.cargar();
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  validar(r: ReglaTermino): void {
    if (!confirm(`¿Validar la regla "${r.descripcion}" (${r.norma})? Quedará a su nombre.`)) return;
    this.limpiar();
    this.api.validarRegla(r.idRegla).subscribe({
      next: () => { this.mensaje = `Regla "${r.descripcion}" validada.`; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  verHistorial(r: ReglaTermino): void {
    if (this.historialDe === r.idRegla) { this.historialDe = null; return; }
    this.historialDe = r.idRegla;
    this.historial = [];
    this.api.historialRegla(r.idRegla).subscribe({ next: h => this.historial = h, error: e => this.error = mensajeDeError(e) });
  }

  guardarContestacion(t: TipoProceso): void {
    const dias = this.contestacion[t.idTipo] || null;
    this.limpiar();
    this.api.cambiarContestacion(t.idTipo, dias).subscribe({
      next: r => {
        this.mensaje = `Días para contestar en ${t.nombre}: ${dias ?? 'no aplica'}.`
          + (r.reglasSinValidar ? ` ${r.reglasSinValidar} regla(s) quedaron sin validar.` : '');
        this.cargar();
        this.api.tipos().subscribe({ next: tipos => this.ponerTipos(tipos), error: () => {} });
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  private ponerTipos(tipos: TipoProceso[]): void {
    this.tipos = tipos;
    this.contestacion = Object.fromEntries(tipos.map(t => [t.idTipo, t.terminoContestacionDias ?? null]));
  }

  private limpiar(): void {
    this.mensaje = this.error = '';
    this.prueba = null;
    this.claseDePrueba = '';
  }
}
