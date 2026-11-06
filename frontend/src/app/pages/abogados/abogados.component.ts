import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Abogado, AreaPractica, Cargo, Sede } from '../../core/modelos';

@Component({
  selector: 'app-abogados',
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  templateUrl: './abogados.component.html'
})
export class AbogadosComponent implements OnInit {
  private api = inject(ApiService);

  abogados: Abogado[] = [];
  cargos: Cargo[] = [];
  sedes: Sede[] = [];
  areas: AreaPractica[] = [];
  abogado: Abogado = this.vacio();
  editandoId: number | null = null;
  soloActivos = false;

  cargando = false;
  guardando = false;
  enviado = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    forkJoin({ cargos: this.api.cargos(), sedes: this.api.sedes(), areas: this.api.areas() }).subscribe({
      next: r => {
        this.cargos = r.cargos.filter(c => c.esAbogado);   // solo cargos que exigen tarjeta profesional
        this.sedes = r.sedes;
        this.areas = r.areas;
      },
      error: e => this.error = mensajeDeError(e)
    });
    this.cargar();
  }

  vacio(): Abogado {
    return { idPersona: null as unknown as number, nombres: '', apellidos: '', email: '', telefono: '', idCargo: 3,
      idSede: 1, idArea: null, tarjetaProfesional: '', tarifaHora: null, fechaIngreso: new Date().toISOString().slice(0, 10) };
  }

  cargar(): void {
    this.cargando = true;
    this.api.abogados(this.soloActivos).subscribe({
      next: a => { this.abogados = a; this.cargando = false; },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  guardar(form: NgForm): void {
    this.enviado = true;
    this.mensaje = this.error = '';
    if (form.invalid) return;
    this.guardando = true;
    const peticion = this.editandoId
      ? this.api.actualizarAbogado(this.editandoId, this.abogado)
      : this.api.crearAbogado(this.abogado);
    peticion.subscribe({
      next: a => {
        this.mensaje = `${a.nombres} ${a.apellidos} ${this.editandoId ? 'actualizado' : 'registrado'}.`;
        this.cancelar(form);
        this.cargar();
      },
      error: e => { this.error = mensajeDeError(e); this.guardando = false; }
    });
  }

  editar(a: Abogado): void {
    this.editandoId = a.idPersona;
    this.abogado = { ...a };
    this.mensaje = this.error = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  desactivar(a: Abogado): void {
    if (!confirm(`¿Desactivar a ${a.nombres} ${a.apellidos}? Sus procesos conservan el historial.`)) return;
    this.api.desactivarAbogado(a.idPersona).subscribe({
      next: () => { this.mensaje = `${a.nombres} ${a.apellidos} quedó inactivo.`; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  cancelar(form: NgForm): void {
    this.editandoId = null;
    this.enviado = false;
    this.guardando = false;
    form.resetForm(this.vacio());
    this.abogado = this.vacio();
  }
}
