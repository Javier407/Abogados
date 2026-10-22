import { Component, OnInit, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, JURIDICO } from '../../core/auth.service';
import { Abogado, Integrante, RolEquipo, etiqueta } from '../../core/modelos';

/**
 * Equipo del proceso (F11): el responsable (se cambia editando el proceso), abogados de apoyo y dependientes.
 * Quien está en el equipo ve el proceso en "solo mis procesos".
 */
@Component({
  selector: 'app-equipo-proceso',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <section class="card">
      <div class="card-header"><h2>Equipo ({{ equipo.length }})</h2></div>
      <div class="card-body">
        @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
        <ul class="lista-pagos">
          @for (i of equipo; track i.idPersona) {
            <li><span><strong>{{ i.nombre || i.idPersona }}</strong> · {{ etiqueta(i.rol) }} · desde {{ i.desde | date:'dd/MM/yyyy' }}</span>
              @if (juridico && i.rol !== 'RESPONSABLE') {
                <button class="btn-quitar" type="button" (click)="quitar(i)" [attr.aria-label]="'Quitar a ' + (i.nombre || i.idPersona)">×</button>
              }
            </li>
          }
        </ul>
        @if (juridico) {
          <div class="filtros" style="margin-top: 12px">
            <select class="form-input" [(ngModel)]="idPersona" aria-label="Persona que se agrega">
              <option [ngValue]="null">Agregar a…</option>
              @for (a of disponibles(); track a.idPersona) { <option [ngValue]="a.idPersona">{{ a.nombres }} {{ a.apellidos }}</option> }
            </select>
            <select class="form-input" [(ngModel)]="rol" aria-label="Rol en el proceso">
              <option value="APOYO">Abogado de apoyo</option>
              <option value="DEPENDIENTE">Dependiente judicial</option>
            </select>
            <button class="btn btn-secondary btn-sm" type="button" (click)="agregar()" [disabled]="!idPersona">Agregar</button>
          </div>
          <p class="form-ayuda">El responsable se cambia en "Editar datos"; el anterior queda como apoyo.</p>
        }
      </div>
    </section>
  `
})
export class EquipoProcesoComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  readonly idProceso = input.required<number>();
  readonly juridico = this.auth.tiene(...JURIDICO);
  readonly etiqueta = etiqueta;

  equipo: Integrante[] = [];
  personal: Abogado[] = [];
  idPersona: number | null = null;
  rol: RolEquipo = 'APOYO';
  error = '';

  ngOnInit(): void {
    this.cargar();
    if (this.juridico) this.api.abogados(true).subscribe({ next: a => this.personal = a, error: () => {} });
  }

  disponibles(): Abogado[] {
    return this.personal.filter(a => !this.equipo.some(i => i.idPersona === a.idPersona));
  }

  cargar(): void {
    this.api.equipo(this.idProceso()).subscribe({ next: e => this.equipo = e, error: e => this.error = mensajeDeError(e) });
  }

  agregar(): void {
    if (!this.idPersona) return;
    this.error = '';
    this.api.agregarIntegrante(this.idProceso(), this.idPersona, this.rol).subscribe({
      next: () => { this.idPersona = null; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  quitar(i: Integrante): void {
    if (!confirm(`¿Quitar a ${i.nombre || i.idPersona} del equipo del proceso?`)) return;
    this.api.quitarIntegrante(this.idProceso(), i.idPersona).subscribe({
      next: () => this.cargar(),
      error: e => this.error = mensajeDeError(e)
    });
  }
}
