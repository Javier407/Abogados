import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  Abogado, AreaPractica, Aviso, Cargo, Cliente, DesgloseRadicado, Despacho, DiaInhabil, Proceso, ProcesoSolicitud, Rama,
  Revision, Sede, Termino, TerminoSolicitud, TipoProceso, Vencimiento
} from './modelos';

/**
 * Único punto de acceso al backend. Todo pasa por el gateway en /api:
 * en el servidor nginx lo reenvía al gateway; con "ng serve" lo hace proxy.conf.json.
 */
const API = '/api';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // ---------- gabinete ----------
  clientes(q?: string): Observable<Cliente[]> {
    return this.http.get<Cliente[]>(`${API}/clientes`, { params: filtros({ q }) });
  }
  crearCliente(c: Cliente): Observable<Cliente> {
    return this.http.post<Cliente>(`${API}/clientes`, c);
  }
  actualizarCliente(id: number, c: Cliente): Observable<Cliente> {
    return this.http.put<Cliente>(`${API}/clientes/${id}`, c);
  }
  eliminarCliente(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/clientes/${id}`);
  }

  abogados(soloActivos = false): Observable<Abogado[]> {
    return this.http.get<Abogado[]>(`${API}/abogados`, { params: filtros({ soloActivos }) });
  }
  crearAbogado(a: Abogado): Observable<Abogado> {
    return this.http.post<Abogado>(`${API}/abogados`, a);
  }
  actualizarAbogado(id: number, a: Abogado): Observable<Abogado> {
    return this.http.put<Abogado>(`${API}/abogados/${id}`, a);
  }
  desactivarAbogado(id: number): Observable<Abogado> {
    return this.http.delete<Abogado>(`${API}/abogados/${id}`);
  }

  sedes(): Observable<Sede[]> { return this.http.get<Sede[]>(`${API}/gabinete/sedes`); }
  cargos(): Observable<Cargo[]> { return this.http.get<Cargo[]>(`${API}/gabinete/cargos`); }
  areas(): Observable<AreaPractica[]> { return this.http.get<AreaPractica[]>(`${API}/gabinete/areas`); }

  // ---------- procesos ----------
  procesos(f: { estado?: string; rama?: string; idCliente?: number; idAbogado?: number; q?: string } = {}):
    Observable<Proceso[]> {
    return this.http.get<Proceso[]>(`${API}/procesos`, { params: filtros(f) });
  }
  proceso(id: number): Observable<Proceso> {
    return this.http.get<Proceso>(`${API}/procesos/${id}`);
  }
  crearProceso(p: ProcesoSolicitud): Observable<Proceso> {
    return this.http.post<Proceso>(`${API}/procesos`, p);
  }
  actualizarProceso(id: number, p: ProcesoSolicitud): Observable<Proceso> {
    return this.http.put<Proceso>(`${API}/procesos/${id}`, p);
  }
  eliminarProceso(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/procesos/${id}`);
  }
  desglosarRadicado(numero: string): Observable<DesgloseRadicado> {
    return this.http.get<DesgloseRadicado>(`${API}/procesos/radicado/${encodeURIComponent(numero)}`);
  }

  ramas(): Observable<Rama[]> { return this.http.get<Rama[]>(`${API}/catalogos/ramas`); }
  tipos(rama?: string): Observable<TipoProceso[]> {
    return this.http.get<TipoProceso[]>(`${API}/catalogos/tipos`, { params: filtros({ rama }) });
  }
  despachos(rama?: string): Observable<Despacho[]> {
    return this.http.get<Despacho[]>(`${API}/catalogos/despachos`, { params: filtros({ rama }) });
  }

  // ---------- términos y calendario ----------
  terminos(f: { situacion?: string; idResponsable?: number | null; idProceso?: number; desde?: string; hasta?: string } = {}):
    Observable<Termino[]> {
    return this.http.get<Termino[]>(`${API}/terminos`, { params: filtros(f) });
  }
  crearTermino(idProceso: number, t: TerminoSolicitud): Observable<Termino> {
    return this.http.post<Termino>(`${API}/procesos/${idProceso}/terminos`, t);
  }
  cumplirTermino(id: number): Observable<Termino> {
    return this.http.post<Termino>(`${API}/terminos/${id}/cumplir`, {});
  }
  eliminarTermino(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/terminos/${id}`);
  }
  /** Ejecuta ya la revisión que se hace cada mañana a las 7:00. */
  revisarTerminos(): Observable<Revision> {
    return this.http.post<Revision>(`${API}/terminos/revision`, {});
  }
  diasInhabiles(desde: string, hasta: string): Observable<DiaInhabil[]> {
    return this.http.get<DiaInhabil[]>(`${API}/calendario`, { params: filtros({ desde, hasta }) });
  }
  vencimiento(desde: string, dias: number): Observable<Vencimiento> {
    return this.http.get<Vencimiento>(`${API}/calendario/vencimiento`, { params: filtros({ desde, dias }) });
  }

  // ---------- avisos ----------
  avisos(idPersona: number, soloNoLeidas = false): Observable<Aviso[]> {
    return this.http.get<Aviso[]>(`${API}/notificaciones`, { params: filtros({ idPersona, soloNoLeidas }) });
  }
  avisosNoLeidos(idPersona: number): Observable<{ noLeidas: number }> {
    return this.http.get<{ noLeidas: number }>(`${API}/notificaciones/resumen`, { params: filtros({ idPersona }) });
  }
  marcarAvisoLeido(id: number, idPersona: number): Observable<unknown> {
    return this.http.post(`${API}/notificaciones/${id}/leida`, {}, { params: filtros({ idPersona }) });
  }
  marcarAvisosLeidos(idPersona: number): Observable<unknown> {
    return this.http.post(`${API}/notificaciones/leidas`, {}, { params: filtros({ idPersona }) });
  }
  /** URL del stream de avisos en vivo (Server-Sent Events). */
  urlAvisosEnVivo(idPersona: number): string {
    return `${API}/notificaciones/stream?idPersona=${idPersona}`;
  }
}

/** Quita los filtros vacíos para no mandar ?estado=&rama= */
function filtros(valores: Record<string, string | number | boolean | undefined | null>): HttpParams {
  let params = new HttpParams();
  for (const [k, v] of Object.entries(valores)) {
    if (v !== undefined && v !== null && v !== '') params = params.set(k, String(v));
  }
  return params;
}

/**
 * Mensaje para el usuario a partir de un error HTTP. Los servicios responden
 * { error: "...", campos?: { campo: "..." } }.
 */
export function mensajeDeError(e: unknown, porDefecto = 'No se pudo completar la operación.'): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'No hay conexión con el servidor.';
    const cuerpo = e.error;
    if (cuerpo?.campos) return Object.values(cuerpo.campos).join(' · ');
    if (cuerpo?.error) return cuerpo.error;
    if (e.status === 502 || e.status === 503 || e.status === 504) return 'El servicio no está disponible en este momento.';
  }
  return porDefecto;
}
