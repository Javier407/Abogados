import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

/** Lo que devuelve /api/auth/login. */
export interface Sesion {
  token: string;
  expira: string;
  idUsuario: number;
  usuario: string;
  nombre: string;
  roles: string[];
}

const CLAVE = 'gj.sesion';

/** Roles del personal (los mismos grupos que la política de acceso del gateway). */
export const INTERNOS = ['ADMIN', 'SOCIO', 'ABOGADO', 'DEPENDIENTE', 'ADMINISTRATIVO'];
export const JURIDICO = ['ADMIN', 'SOCIO', 'ABOGADO'];
export const JURIDICO_Y_DEPENDIENTES = ['ADMIN', 'SOCIO', 'ABOGADO', 'DEPENDIENTE'];
export const VEN_HONORARIOS = ['ADMIN', 'SOCIO', 'ABOGADO', 'ADMINISTRATIVO'];
export const EDITAN_CLIENTES = ['ADMIN', 'SOCIO', 'ABOGADO', 'ADMINISTRATIVO'];
export const DIRECCION = ['ADMIN', 'SOCIO'];

/**
 * Sesión del usuario. El token se guarda en sessionStorage: dura lo que la pestaña y no se comparte
 * con otras pestañas ni sobrevive a cerrar el navegador. El permiso real lo decide el gateway; aquí
 * los roles solo sirven para no mostrar opciones que el usuario no puede usar.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly sesion = signal<Sesion | null>(leer());
  readonly conectado = computed(() => !!this.sesion());
  readonly idUsuario = computed(() => this.sesion()?.idUsuario ?? null);
  readonly esPersonal = computed(() => this.tiene(...INTERNOS));

  iniciar(usuario: string, clave: string): Observable<Sesion> {
    return this.http.post<Sesion>('/api/auth/login', { usuario, clave }).pipe(tap(s => {
      try { sessionStorage.setItem(CLAVE, JSON.stringify(s)); } catch { /* sin almacenamiento: solo en memoria */ }
      this.sesion.set(s);
    }));
  }

  cambiarClave(actual: string, nueva: string): Observable<unknown> {
    return this.http.post('/api/auth/clave', { actual, nueva });
  }

  /** vencida = la cerró el sistema (token vencido o inválido), no el usuario. */
  cerrar(vencida = false): void {
    try { sessionStorage.removeItem(CLAVE); } catch { /* nada que borrar */ }
    this.sesion.set(null);
    this.router.navigate(['/login'], vencida ? { queryParams: { vencida: 1 } } : {});
  }

  token(): string | null {
    const s = this.sesion();
    if (!s) return null;
    if (new Date(s.expira).getTime() <= Date.now()) {
      this.cerrar(true);
      return null;
    }
    return s.token;
  }

  tiene(...roles: string[]): boolean {
    const propios = this.sesion()?.roles ?? [];
    return roles.some(r => propios.includes(r));
  }
}

function leer(): Sesion | null {
  try {
    const s = JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null') as Sesion | null;
    return s && new Date(s.expira).getTime() > Date.now() ? s : null;
  } catch {
    return null;
  }
}
