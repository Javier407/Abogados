import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Abogado, AreaPractica, Cargo, Rama, Sede } from '../../core/modelos';

type Tipo = 'sedes' | 'cargos' | 'areas';

/**
 * La firma (F11, solo ADMIN y SOCIO): sedes, cargos y áreas de práctica. Antes solo se podían consultar.
 * Reglas (las aplica el servidor): una sola sede principal, códigos únicos, director activo, y no se borra lo
 * que tiene clientes o personal.
 */
@Component({
  selector: 'app-firma',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>La firma</h1>
        <p>Sedes, cargos y áreas de práctica. Lo que tiene clientes o personal asignado no se puede borrar.</p>
      </div>
      @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }

      <!-- Sedes -->
      <section class="card">
        <div class="card-header encabezado-gastos"><h2>Sedes</h2>
          <button class="btn btn-secondary btn-sm" type="button" (click)="editarSede(null)">Nueva sede</button></div>
        <div class="card-body">
          @if (sede) {
            <div class="form-grid">
              <div class="form-group"><label class="form-label" for="sNombre">Nombre</label>
                <input id="sNombre" class="form-input" [(ngModel)]="sede.nombre" maxlength="60" /></div>
              <div class="form-group"><label class="form-label" for="sCiudad">Ciudad</label>
                <input id="sCiudad" class="form-input" [(ngModel)]="sede.ciudad" maxlength="60" /></div>
              <div class="form-group"><label class="form-label" for="sDir">Dirección</label>
                <input id="sDir" class="form-input" [(ngModel)]="sede.direccion" maxlength="150" /></div>
              <div class="form-group"><label class="form-label" for="sTel">Teléfono</label>
                <input id="sTel" class="form-input" [(ngModel)]="sede.telefono" maxlength="20" /></div>
              <div class="form-group"><span class="form-label">Principal</span>
                <label class="resumen-chip chip-check"><input type="checkbox" [(ngModel)]="sede.esPrincipal" /> Sede principal</label></div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="button" (click)="guardar('sedes', idEditando, sede)">Guardar</button>
              <button class="btn btn-secondary" type="button" (click)="sede = null">Cancelar</button>
            </div>
          }
          <div class="tabla-wrapper"><table class="tabla">
            <thead><tr><th>Sede</th><th>Ciudad</th><th>Dirección</th><th>Teléfono</th><th></th></tr></thead>
            <tbody>
              @for (s of sedes; track s.idSede) {
                <tr>
                  <td class="titulo-celda">{{ s.nombre }} @if (s.esPrincipal) { <span class="badge badge-primary">Principal</span> }</td>
                  <td>{{ s.ciudad }}</td><td>{{ s.direccion }}</td><td>{{ s.telefono || '—' }}</td>
                  <td class="acciones-celda">
                    <button class="btn btn-secondary btn-sm" type="button" (click)="editarSede(s)">Editar</button>
                    @if (!s.esPrincipal) { <button class="btn-quitar" type="button" (click)="borrar('sedes', s.idSede, s.nombre)" [attr.aria-label]="'Borrar ' + s.nombre">×</button> }
                  </td>
                </tr>
              }
            </tbody>
          </table></div>
        </div>
      </section>

      <!-- Cargos -->
      <section class="card">
        <div class="card-header encabezado-gastos"><h2>Cargos</h2>
          <button class="btn btn-secondary btn-sm" type="button" (click)="editarCargo(null)">Nuevo cargo</button></div>
        <div class="card-body">
          @if (cargo) {
            <div class="form-grid">
              <div class="form-group"><label class="form-label" for="cCodigo">Código</label>
                <input id="cCodigo" class="form-input" [(ngModel)]="cargo.codigo" maxlength="20" placeholder="PRACTICANTE" /></div>
              <div class="form-group"><label class="form-label" for="cNombre">Nombre</label>
                <input id="cNombre" class="form-input" [(ngModel)]="cargo.nombre" maxlength="60" /></div>
              <div class="form-group"><span class="form-label">Tarjeta profesional</span>
                <label class="resumen-chip chip-check"><input type="checkbox" [(ngModel)]="cargo.esAbogado" /> Es un cargo de abogado</label></div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="button" (click)="guardar('cargos', idEditando, cargo)">Guardar</button>
              <button class="btn btn-secondary" type="button" (click)="cargo = null">Cancelar</button>
            </div>
          }
          <div class="tabla-wrapper"><table class="tabla">
            <thead><tr><th>Código</th><th>Cargo</th><th>Abogado</th><th></th></tr></thead>
            <tbody>
              @for (c of cargos; track c.idCargo) {
                <tr>
                  <td class="mono">{{ c.codigo }}</td><td class="titulo-celda">{{ c.nombre }}</td><td>{{ c.esAbogado ? 'Sí' : 'No' }}</td>
                  <td class="acciones-celda">
                    <button class="btn btn-secondary btn-sm" type="button" (click)="editarCargo(c)">Editar</button>
                    <button class="btn-quitar" type="button" (click)="borrar('cargos', c.idCargo, c.nombre)" [attr.aria-label]="'Borrar ' + c.nombre">×</button>
                  </td>
                </tr>
              }
            </tbody>
          </table></div>
        </div>
      </section>

      <!-- Áreas -->
      <section class="card">
        <div class="card-header encabezado-gastos"><h2>Áreas de práctica</h2>
          <button class="btn btn-secondary btn-sm" type="button" (click)="editarArea(null)">Nueva área</button></div>
        <div class="card-body">
          @if (area) {
            <div class="form-grid">
              <div class="form-group"><label class="form-label" for="aRama">Rama</label>
                <select id="aRama" class="form-input" [(ngModel)]="area.codigoRama">
                  @for (r of ramas; track r.codigo) { <option [value]="r.codigo">{{ r.nombre }}</option> }
                </select></div>
              <div class="form-group"><label class="form-label" for="aNombre">Nombre</label>
                <input id="aNombre" class="form-input" [(ngModel)]="area.nombre" maxlength="60" /></div>
              <div class="form-group"><label class="form-label" for="aDirector">Director(a)</label>
                <select id="aDirector" class="form-input" [(ngModel)]="area.idDirector">
                  @for (p of personal; track p.idPersona) { <option [ngValue]="p.idPersona">{{ p.nombres }} {{ p.apellidos }}</option> }
                </select></div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="button" (click)="guardar('areas', idEditando, area)">Guardar</button>
              <button class="btn btn-secondary" type="button" (click)="area = null">Cancelar</button>
            </div>
          }
          <div class="tabla-wrapper"><table class="tabla">
            <thead><tr><th>Área</th><th>Rama</th><th>Director(a)</th><th></th></tr></thead>
            <tbody>
              @for (a of areas; track a.idArea) {
                <tr>
                  <td class="titulo-celda">{{ a.nombre }}</td><td class="mono">{{ a.codigoRama }}</td><td>{{ nombreDe(a.idDirector) }}</td>
                  <td class="acciones-celda">
                    <button class="btn btn-secondary btn-sm" type="button" (click)="editarArea(a)">Editar</button>
                    <button class="btn-quitar" type="button" (click)="borrar('areas', a.idArea, a.nombre)" [attr.aria-label]="'Borrar ' + a.nombre">×</button>
                  </td>
                </tr>
              }
            </tbody>
          </table></div>
        </div>
      </section>
    </div>
  `
})
export class FirmaComponent implements OnInit {
  private api = inject(ApiService);

  sedes: Sede[] = [];
  cargos: Cargo[] = [];
  areas: AreaPractica[] = [];
  ramas: Rama[] = [];
  personal: Abogado[] = [];
  sede: Partial<Sede> | null = null;
  cargo: Partial<Cargo> | null = null;
  area: Partial<AreaPractica> | null = null;
  idEditando: number | null = null;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.cargar();
    forkJoin({ ramas: this.api.ramas(), personal: this.api.abogados(true) }).subscribe({
      next: r => { this.ramas = r.ramas; this.personal = r.personal; }, error: () => {}
    });
  }

  cargar(): void {
    forkJoin({ sedes: this.api.sedes(), cargos: this.api.cargos(), areas: this.api.areas() }).subscribe({
      next: r => { this.sedes = r.sedes; this.cargos = r.cargos; this.areas = r.areas; },
      error: e => this.error = mensajeDeError(e)
    });
  }

  nombreDe(id?: number): string {
    const p = this.personal.find(x => x.idPersona === id);
    return p ? `${p.nombres} ${p.apellidos}` : String(id ?? '—');
  }

  editarSede(s: Sede | null): void {
    this.limpiar(s?.idSede ?? null);
    this.sede = s ? { ...s } : { nombre: '', ciudad: '', direccion: '', telefono: '', esPrincipal: false };
  }

  editarCargo(c: Cargo | null): void {
    this.limpiar(c?.idCargo ?? null);
    this.cargo = c ? { ...c } : { codigo: '', nombre: '', esAbogado: false };
  }

  editarArea(a: AreaPractica | null): void {
    this.limpiar(a?.idArea ?? null);
    this.area = a ? { ...a } : { codigoRama: this.ramas[0]?.codigo ?? '', nombre: '', idDirector: this.personal[0]?.idPersona };
  }

  guardar<T>(tipo: Tipo, id: number | null, datos: T): void {
    this.mensaje = this.error = '';
    this.api.guardarCatalogo(tipo, id, datos).subscribe({
      next: () => { this.mensaje = 'Guardado.'; this.sede = this.cargo = this.area = null; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  borrar(tipo: Tipo, id: number, nombre: string): void {
    if (!confirm(`¿Borrar "${nombre}"?`)) return;
    this.mensaje = this.error = '';
    const peticion: Observable<void> = this.api.borrarCatalogo(tipo, id);
    peticion.subscribe({
      next: () => { this.mensaje = `"${nombre}" borrado.`; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  private limpiar(id: number | null): void {
    this.mensaje = this.error = '';
    this.sede = this.cargo = this.area = null;
    this.idEditando = id;
  }
}
