import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Cliente, Sede, etiqueta } from '../../core/modelos';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './clientes.component.html'
})
export class ClientesComponent implements OnInit {
  private api = inject(ApiService);
  readonly etiqueta = etiqueta;

  clientes: Cliente[] = [];
  sedes: Sede[] = [];
  cliente: Cliente = this.vacio();
  editandoId: number | null = null;
  busqueda = '';

  cargando = false;
  guardando = false;
  enviado = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.api.sedes().subscribe({ next: s => this.sedes = s, error: e => this.error = mensajeDeError(e) });
    this.cargar();
  }

  vacio(): Cliente {
    return { tipoPersona: 'NATURAL', tipoDocumento: 'CC', numeroDocumento: '', nombre: '', email: '', telefono: '',
      direccion: '', ciudad: '', idSede: 1 };
  }

  cargar(): void {
    this.cargando = true;
    this.api.clientes(this.busqueda).subscribe({
      next: c => { this.clientes = c; this.cargando = false; },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  /** Persona jurídica implica NIT, y al revés. */
  cambioTipoPersona(): void {
    if (this.cliente.tipoPersona === 'JURIDICA') this.cliente.tipoDocumento = 'NIT';
    else if (this.cliente.tipoDocumento === 'NIT') this.cliente.tipoDocumento = 'CC';
  }

  guardar(form: NgForm): void {
    this.enviado = true;
    this.mensaje = this.error = '';
    if (form.invalid) return;
    this.guardando = true;
    const peticion = this.editandoId
      ? this.api.actualizarCliente(this.editandoId, this.cliente)
      : this.api.crearCliente(this.cliente);
    peticion.subscribe({
      next: c => {
        this.mensaje = this.editandoId ? `Cliente ${c.nombre} actualizado.` : `Cliente ${c.nombre} registrado con el id ${c.idCliente}.`;
        this.cancelar(form);
        this.cargar();
      },
      error: e => { this.error = mensajeDeError(e); this.guardando = false; }
    });
  }

  editar(c: Cliente): void {
    this.editandoId = c.idCliente!;
    this.cliente = { ...c };
    this.mensaje = this.error = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  eliminar(c: Cliente): void {
    if (!confirm(`¿Eliminar al cliente ${c.nombre}?`)) return;
    this.api.eliminarCliente(c.idCliente!).subscribe({
      next: () => { this.mensaje = `Cliente ${c.nombre} eliminado.`; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  cancelar(form: NgForm): void {
    this.editandoId = null;
    this.enviado = false;
    this.guardando = false;
    form.resetForm(this.vacio());
    this.cliente = this.vacio();
  }

  nombreSede(id: number): string {
    return this.sedes.find(s => s.idSede === id)?.ciudad ?? String(id);
  }
}
