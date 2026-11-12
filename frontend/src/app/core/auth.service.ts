import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';

/** Lo que devuelven /api/auth/login y /api/auth/renovar. */
export interface Sesion {
  token: string;
  expira: string;
  idUsuario: number;
  usuario: string;
  nombre: string;
  roles: string[];
  /** Cuenta nueva o clave restablecida por el administrador: debe cambiarla antes de seguir. */
  debeCambiarClave?: boolean;
  /** Solo en las cuentas del portal del cliente. */
  idCliente?: number;
  /** Solo al activar la verificación en dos pasos al entrar: se muestran una vez. */
  codigosRecuperacion?: string[];
}

/** Respuesta del login cuando falta el segundo paso (F13): con el desafío se envía el código. */
export interface SegundoPaso {
  mfaRequerido: true;
  /** La cuenta debe activar la verificación en dos pasos antes de entrar. */
  configurarMfa: boolean;
  desafio: string;
}

/** Secreto para la app autenticadora: uri para el código QR y el texto por si se escribe a mano. */
export interface InicioMfa { secreto: string; uri: string; }
export interface EstadoMfa { activo: boolean; desde?: string; codigosRestantes: number; obligatorio: boolean; }

/** Cuentas externas con que se puede entrar, una vez enlazadas desde «Mi cuenta». */
export type CodigoProveedor = 'GOOGLE' | 'MICROSOFT' | 'GITHUB';
export interface ProveedorExterno { codigo: CodigoProveedor; nombre: string; }
export interface EnlaceExterno { proveedor: CodigoProveedor; correo?: string | null; creado: string; ultimoUso?: string | null; }

/** Qué significa cada motivo con que vuelve un intento fallido (los códigos los define identidad). */
export const MOTIVOS_EXTERNO: Record<string, string> = {
  'sin-enlace': 'Esa cuenta no está enlazada a ningún usuario de la firma. Entre con su usuario y contraseña y enlácela en «Mi cuenta».',
  'cancelado': 'Se canceló el inicio de sesión en el proveedor.',
  'vencido': 'El intento venció o ya se usó. Vuelva a intentarlo.',
  'otro-navegador': 'El intento se inició en otro navegador. Vuelva a intentarlo desde aquí.',
  'proveedor': 'El proveedor no respondió como se esperaba. Vuelva a intentarlo en unos minutos.',
  'bloqueada': 'La cuenta está bloqueada por intentos fallidos. Espere 15 minutos.',
  'inactiva': 'La cuenta está desactivada; hable con el administrador.',
  'de-otra-persona': 'Esa cuenta externa ya está enlazada a otro usuario de la firma.',
  'ya-enlazada': 'Ya tiene una cuenta de ese proveedor enlazada; quítela primero.'
};

export function esSegundoPaso(r: Sesion | SegundoPaso): r is SegundoPaso {
  return (r as SegundoPaso).mfaRequerido === true;
}

const CLAVE = 'gj.sesion';
/** Renovar el token un minuto antes de que venza. */
const MARGEN_MS = 60_000;
/** /auth/renovar y /auth/salir exigen esta cabecera (otra página no puede enviarla: protección CSRF). */
const AJAX = new HttpHeaders({ 'X-Requested-With': 'XMLHttpRequest' });

/** Roles del personal (los mismos grupos que la política de acceso del gateway). */
export const INTERNOS = ['ADMIN', 'SOCIO', 'ABOGADO', 'DEPENDIENTE', 'ADMINISTRATIVO'];
export const JURIDICO = ['ADMIN', 'SOCIO', 'ABOGADO'];
export const JURIDICO_Y_DEPENDIENTES = ['ADMIN', 'SOCIO', 'ABOGADO', 'DEPENDIENTE'];
export const VEN_HONORARIOS = ['ADMIN', 'SOCIO', 'ABOGADO', 'ADMINISTRATIVO'];
export const EDITAN_CLIENTES = ['ADMIN', 'SOCIO', 'ABOGADO', 'ADMINISTRATIVO'];
export const DIRECCION = ['ADMIN', 'SOCIO'];
export const MANEJAN_FINANZAS = ['ADMIN', 'SOCIO', 'ADMINISTRATIVO'];

/**
 * Sesión del usuario (F9).
 * - El token de acceso dura 15 minutos y se guarda en sessionStorage (solo esta pestaña).
 * - El refresh token viaja en una cookie HttpOnly que esta página no puede leer; con ella se pide un token
 *   nuevo un minuto antes de que venza, o al abrir otra pestaña. Cada renovación gasta la cookie y trae otra.
 * - Cerrar sesión la cierra también en el servidor: el token y la cookie dejan de servir.
 * El permiso real lo decide el gateway; aquí los roles solo sirven para no mostrar lo que no se puede usar.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly sesion = signal<Sesion | null>(leer());
  readonly conectado = computed(() => !!this.sesion());
  readonly idUsuario = computed(() => this.sesion()?.idUsuario ?? null);
  readonly esPersonal = computed(() => this.tiene(...INTERNOS));
  readonly esCliente = computed(() => this.tiene('CLIENTE'));

  private renovando: Observable<Sesion | null> | null = null;
  private temporizador: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.programarRenovacion();
  }

  /** Con la verificación en dos pasos, la contraseña correcta devuelve un SegundoPaso en lugar de la sesión. */
  iniciar(usuario: string, clave: string): Observable<Sesion | SegundoPaso> {
    return this.http.post<Sesion | SegundoPaso>('/api/auth/login', { usuario, clave })
      .pipe(tap(r => { if (!esSegundoPaso(r)) this.guardar(r); }));
  }

  // ---------------------------------------------------------------- verificación en dos pasos (F13)

  /** Segundo paso del login: código de la app (6 dígitos) o de recuperación. */
  completarMfa(desafio: string, codigo: string): Observable<Sesion> {
    return this.http.post<Sesion>('/api/auth/login/mfa', { desafio, codigo }).pipe(tap(s => this.guardar(s)));
  }

  iniciarMfaAlEntrar(desafio: string): Observable<InicioMfa> {
    return this.http.post<InicioMfa>('/api/auth/login/mfa/inicio', { desafio });
  }

  activarMfaAlEntrar(desafio: string, codigo: string): Observable<Sesion> {
    return this.http.post<Sesion>('/api/auth/login/mfa/activacion', { desafio, codigo }).pipe(tap(s => this.guardar(s)));
  }

  estadoMfa(): Observable<EstadoMfa> { return this.http.get<EstadoMfa>('/api/auth/mfa'); }
  iniciarMfa(): Observable<InicioMfa> { return this.http.post<InicioMfa>('/api/auth/mfa/inicio', {}); }
  activarMfa(codigo: string): Observable<{ codigos: string[] }> {
    return this.http.post<{ codigos: string[] }>('/api/auth/mfa/activacion', { codigo });
  }
  regenerarCodigos(clave: string): Observable<{ codigos: string[] }> {
    return this.http.post<{ codigos: string[] }>('/api/auth/mfa/codigos', { clave });
  }
  desactivarMfa(clave: string, codigo: string): Observable<unknown> {
    return this.http.post('/api/auth/mfa/desactivacion', { clave, codigo });
  }

  // ---------------------------------------------------------------- cuentas externas (Google, Microsoft, GitHub)

  /** Proveedores configurados en el servidor (ninguno si no se han registrado las apps OAuth). */
  proveedoresExternos(): Observable<ProveedorExterno[]> {
    return this.http.get<ProveedorExterno[]>('/api/auth/externo/proveedores').pipe(catchError(() => of([])));
  }

  /**
   * Va al proveedor para entrar (enlazar = false) o para enlazar la cuenta de quien ya está dentro. El servidor
   * devuelve la URL y deja la cookie del flujo; el navegador sale de la app y vuelve por /api/auth/externo/.../vuelta.
   */
  irAProveedor(codigo: CodigoProveedor, enlazar = false): Observable<void> {
    const ruta = `/api/auth/externo/${codigo.toLowerCase()}/${enlazar ? 'enlazar' : 'entrar'}`;
    return this.http.post<{ url: string }>(ruta, null, { headers: AJAX })
      .pipe(map(r => { window.location.assign(r.url); }));
  }

  enlacesExternos(): Observable<EnlaceExterno[]> {
    return this.http.get<EnlaceExterno[]>('/api/auth/externo/enlaces');
  }

  quitarEnlace(codigo: CodigoProveedor): Observable<unknown> {
    return this.http.delete(`/api/auth/externo/enlaces/${codigo.toLowerCase()}`);
  }

  /**
   * Pide un token nuevo con la cookie. Si varias llamadas lo piden a la vez, comparten la misma petición
   * (la cookie es de un solo uso: dos renovaciones simultáneas cerrarían la sesión por seguridad).
   * Devuelve null si la sesión ya no existe en el servidor.
   */
  renovar(): Observable<Sesion | null> {
    if (!this.renovando) {
      this.renovando = this.http.post<Sesion>('/api/auth/renovar', null, { headers: AJAX }).pipe(
        tap(s => this.guardar(s)),
        catchError(() => of(null)),
        finalize(() => (this.renovando = null)),
        shareReplay(1)
      );
    }
    return this.renovando;
  }

  /** Token vigente; si falta o está por vencer, lo renueva primero. null = hay que iniciar sesión. */
  tokenVigente(): Observable<string | null> {
    const s = this.sesion();
    if (s && new Date(s.expira).getTime() - Date.now() > MARGEN_MS / 2) return of(s.token);
    return this.renovar().pipe(map(n => n?.token ?? null));
  }

  cambiarClave(actual: string, nueva: string): Observable<unknown> {
    return this.http.post('/api/auth/clave', { actual, nueva });
  }

  /** Tras cambiar la contraseña obligatoria: se pide un token sin la marca de clave temporal. */
  marcarClaveCambiada(): Observable<Sesion | null> {
    return this.renovar();
  }

  /** vencida = la cerró el sistema (sesión vencida o revocada), no el usuario. */
  cerrar(vencida = false): void {
    if (!vencida) {
      // Cierra la sesión en el servidor (la cookie va sola); si falla, igual se cierra aquí.
      this.http.post('/api/auth/salir', null, { headers: AJAX }).subscribe({ error: () => {} });
    }
    this.limpiar();
    this.router.navigate(['/login'], vencida ? { queryParams: { vencida: 1 } } : {});
  }

  /** Token actual si no ha vencido (para EventSource y fetch, que no pasan por el interceptor). */
  token(): string | null {
    const s = this.sesion();
    return s && new Date(s.expira).getTime() > Date.now() ? s.token : null;
  }

  tiene(...roles: string[]): boolean {
    const propios = this.sesion()?.roles ?? [];
    return roles.some(r => propios.includes(r));
  }

  /** Pantalla de inicio según el rol. */
  inicio(): string {
    return this.esPersonal() ? '/' : this.esCliente() ? '/portal' : '/cuenta';
  }

  private guardar(s: Sesion): void {
    const { codigosRecuperacion, ...sinCodigos } = s;   // los códigos no se guardan en el navegador
    void codigosRecuperacion;
    s = sinCodigos;
    try { sessionStorage.setItem(CLAVE, JSON.stringify(s)); } catch { /* sin almacenamiento: solo en memoria */ }
    this.sesion.set(s);
    this.programarRenovacion();
  }

  private limpiar(): void {
    clearTimeout(this.temporizador);
    try { sessionStorage.removeItem(CLAVE); } catch { /* nada que borrar */ }
    this.sesion.set(null);
  }

  /** Renueva un minuto antes del vencimiento; si no se puede, la sesión terminó. */
  private programarRenovacion(): void {
    clearTimeout(this.temporizador);
    const s = this.sesion();
    if (!s) return;
    const espera = Math.max(new Date(s.expira).getTime() - Date.now() - MARGEN_MS, 5_000);
    this.temporizador = setTimeout(() => this.renovar().subscribe(n => {
      if (!n && this.sesion()) { this.limpiar(); this.router.navigate(['/login'], { queryParams: { vencida: 1 } }); }
    }), espera);
  }
}

function leer(): Sesion | null {
  try {
    return JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null') as Sesion | null;
  } catch {
    return null;
  }
}
