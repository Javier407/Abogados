import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CambioCuenta, Cuenta, CuentaSolicitud, ROLES_CUENTA, etiqueta } from '../../core/modelos';

/**
 * Cuentas del sistema (solo ADMIN). La contraseña temporal la escribe el administrador y se la comunica a la
 * persona por un canal seguro; al entrar, la app la obliga a cambiarla.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.css'
})
export class UsuariosComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  readonly roles = ROLES_CUENTA;
  readonly etiqueta = etiqueta;

  cuentas: Cuenta[] = [];
  filtro = '';
  creando = false;
  nueva: CuentaSolicitud = this.vacia();
  editando: Cuenta | null = null;
  cambio: CambioCuenta = { usuario: '', nombre: '', email: '', roles: [], activo: true };
  claveTemporal = '';
  mensaje = '';
  error = '';
  guardando = false;

  ngOnInit(): void {
    this.cargar();
  }

  get visibles(): Cuenta[] {
    const t = this.filtro.trim().toLowerCase();
    return !t ? this.cuentas : this.cuentas.filter(c =>
      c.nombre.toLowerCase().includes(t) || c.usuario.includes(t) || String(c.idUsuario).includes(t) || c.roles.some(r => r.toLowerCase().includes(t)));
  }

  esYo(c: Cuenta): boolean {
    return c.idUsuario === this.auth.idUsuario();
  }

  bloqueada(c: Cuenta): boolean {
    return !!c.bloqueadoHasta && new Date(c.bloqueadoHasta).getTime() > Date.now();
  }

  cargar(): void {
    this.api.cuentas().subscribe({ next: c => this.cuentas = c, error: e => this.error = mensajeDeError(e) });
  }

  alternarRol(lista: string[], rol: string): void {
    const i = lista.indexOf(rol);
    if (i >= 0) lista.splice(i, 1); else lista.push(rol);
  }

  crear(form: NgForm): void {
    if (form.invalid || !this.nueva.roles.length) { this.error = 'Complete los datos y asigne al menos un rol.'; return; }
    this.enviar(this.api.crearCuenta(this.nueva), c => {
      this.mensaje = `Cuenta ${c.usuario} creada. Entréguele la contraseña temporal por un canal seguro: deberá cambiarla al entrar.`;
      this.nueva = this.vacia();
      this.creando = false;
    });
  }

  editar(c: Cuenta): void {
    this.editando = c;
    this.cambio = { usuario: c.usuario, nombre: c.nombre, email: c.email, roles: [...c.roles], activo: c.activo };
    this.claveTemporal = '';
    this.mensaje = this.error = '';
  }

  guardarCambios(): void {
    const c = this.editando;
    if (!c) return;
    if (!this.cambio.roles.length) { this.error = 'Asigne al menos un rol.'; return; }
    this.enviar(this.api.actualizarCuenta(c.idUsuario, this.cambio), act => {
      this.mensaje = `Cuenta ${act.usuario} actualizada.`;
      this.editando = null;
    });
  }

  restablecer(): void {
    const c = this.editando;
    if (!c) return;
    if (this.claveTemporal.length < 10) { this.error = 'La contraseña temporal debe tener al menos 10 caracteres.'; return; }
    this.enviar(this.api.restablecerClave(c.idUsuario, this.claveTemporal), act => {
      this.mensaje = `Contraseña de ${act.usuario} restablecida y cuenta desbloqueada. Deberá cambiarla al entrar.`;
      this.claveTemporal = '';
      this.editando = null;
    });
  }

  desbloquear(c: Cuenta): void {
    this.enviar(this.api.desbloquearCuenta(c.idUsuario), act => this.mensaje = `Cuenta ${act.usuario} desbloqueada.`);
  }

  private enviar<T>(peticion: import('rxjs').Observable<T>, alTerminar: (r: T) => void): void {
    this.guardando = true;
    this.mensaje = this.error = '';
    peticion.subscribe({
      next: r => { this.guardando = false; alTerminar(r); this.cargar(); },
      error: e => { this.guardando = false; this.error = mensajeDeError(e); }
    });
  }

  private vacia(): CuentaSolicitud {
    return { idUsuario: null, usuario: '', nombre: '', email: '', roles: [], claveTemporal: '' };
  }
}
