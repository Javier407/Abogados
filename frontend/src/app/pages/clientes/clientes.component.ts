import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideFolderPlus,
  lucideMail,
  lucideMapPin,
  lucidePencil,
  lucidePhone,
  lucideTrash2,
  lucideUserPlus,
  lucideX,
} from '@ng-icons/lucide';

import { ApiService, mensajeDeError } from '../../core/api.service';
import {
  AuthService,
  EDITAN_CLIENTES,
  JURIDICO,
} from '../../core/auth.service';
import { Cliente, Proceso, Sede, etiqueta } from '../../core/modelos';

/**
 * Clientes: lista con ficha lateral (estilo School). Desde la ficha se encadena lo que sigue con ese cliente:
 * ver sus procesos y abrir uno nuevo con el cliente ya puesto. El formulario solo
 * aparece al registrar o editar (?nuevo=1 lo abre; ?id=N abre la ficha, como hace la búsqueda Ctrl+K).
 */
@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, NgIcon],
  providers: [
    provideIcons({
      lucideFolderPlus,
      lucideMail,
      lucideMapPin,
      lucidePencil,
      lucidePhone,
      lucideTrash2,
      lucideUserPlus,
      lucideX,
    }),
  ],
  templateUrl: './clientes.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './clientes.component.css',
})
export class ClientesComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  readonly etiqueta = etiqueta;
  readonly puedeEditar = this.auth.tiene(...EDITAN_CLIENTES);
  readonly creaProcesos = this.auth.tiene(...JURIDICO);

  clientes: Cliente[] = [];
  sedes: Sede[] = [];
  cliente: Cliente = this.vacio();
  editandoId: number | null = null;
  formularioAbierto = false;
  busqueda = '';

  // Ficha lateral
  elegido: Cliente | null = null;
  procesosDe: Proceso[] = [];
  cargandoFicha = false;

  cargando = false;
  guardando = false;
  enviado = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.api
      .sedes()
      .subscribe({ next: (s) => (this.sedes = s), error: (e) => (this.error = mensajeDeError(e)) });
    this.cargar();
    this.ruta.queryParamMap.subscribe((q) => {
      if (q.get('nuevo') && this.puedeEditar) this.nuevo();
      const id = Number(q.get('id'));
      if (id) this.api.cliente(id).subscribe({ next: (c) => this.elegir(c), error: () => {} });
    });
  }

  vacio(): Cliente {
    return {
      tipoPersona: 'NATURAL',
      tipoDocumento: 'CC',
      numeroDocumento: '',
      nombre: '',
      email: '',
      telefono: '',
      direccion: '',
      ciudad: '',
      idSede: 1,
    };
  }

  cargar(): void {
    this.cargando = true;
    this.api.clientes(this.busqueda).subscribe({
      next: (c) => {
        this.clientes = c;
        this.cargando = false;
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.cargando = false;
      },
    });
  }

  elegir(c: Cliente): void {
    if (this.elegido?.idCliente === c.idCliente && this.procesosDe.length) return;
    this.elegido = c;
    this.procesosDe = [];
    this.cargandoFicha = true;
    this.api.procesos({ idCliente: c.idCliente }).subscribe({
      next: (p) => {
        this.procesosDe = p;
        this.cargandoFicha = false;
      },
      error: () => {
        this.procesosDe = [];
        this.cargandoFicha = false;
      },
    });
  }

  cerrarFicha(): void {
    this.elegido = null;
    this.router.navigate([], { relativeTo: this.ruta, queryParams: {}, replaceUrl: true });
  }

  iniciales(nombre: string): string {
    const p = nombre.split(/\s+/).filter(Boolean);
    return ((p[0]?.[0] ?? '') + (p[1]?.[0] ?? '')).toUpperCase();
  }

  nuevo(): void {
    this.editandoId = null;
    this.cliente = this.vacio();
    this.enviado = false;
    this.formularioAbierto = true;
    this.mensaje = this.error = '';
    setTimeout(() => document.getElementById('tipoPersona')?.focus());
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
      next: (c) => {
        this.mensaje = this.editandoId
          ? `Cliente ${c.nombre} actualizado.`
          : `Cliente ${c.nombre} registrado.`;
        this.cancelar(form);
        this.cargar();
        this.elegido = null;
        this.elegir(c);
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.guardando = false;
      },
    });
  }

  editar(c: Cliente): void {
    this.editandoId = c.idCliente!;
    this.cliente = { ...c };
    this.formularioAbierto = true;
    this.mensaje = this.error = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
    setTimeout(() => document.getElementById('tipoPersona')?.focus());
  }

  eliminar(c: Cliente): void {
    if (!confirm(`¿Eliminar al cliente ${c.nombre}?`)) return;
    this.api.eliminarCliente(c.idCliente!).subscribe({
      next: () => {
        this.mensaje = `Cliente ${c.nombre} eliminado.`;
        this.elegido = null;
        this.cargar();
      },
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  cancelar(form: NgForm): void {
    this.editandoId = null;
    this.enviado = false;
    this.guardando = false;
    this.formularioAbierto = false;
    form.resetForm(this.vacio());
    this.cliente = this.vacio();
  }

  nombreSede(id: number): string {
    return this.sedes.find((s) => s.idSede === id)?.ciudad ?? String(id);
  }
}
