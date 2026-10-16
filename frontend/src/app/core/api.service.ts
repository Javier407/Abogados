import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  Abogado, AreaPractica, Aviso, Cargo, Cliente, ConsultaVigilancia, EstadoAsistente, Honorario, HonorarioSolicitud,
  Pago, Perito, Tarifa, DesgloseRadicado, Despacho, DiaInhabil, EstadoTelegram,
  FuenteVigilancia, Proceso, ProcesoSolicitud, Providencia, RadicadoVigilado, Rama, ResultadoConsulta, Revision, Sede,
  Termino, TerminoSolicitud, TipoProceso, Vencimiento, VinculoTelegram
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

  // ---------- providencias ----------
  providencias(idProceso: number): Observable<Providencia[]> {
    return this.http.get<Providencia[]>(`${API}/procesos/${idProceso}/providencias`);
  }

  // ---------- vigilancia ----------
  radicadosVigilados(): Observable<RadicadoVigilado[]> {
    return this.http.get<RadicadoVigilado[]>(`${API}/vigilancia/radicados`);
  }
  fuentesVigilancia(): Observable<{ disponibles: FuenteVigilancia[] }> {
    return this.http.get<{ disponibles: FuenteVigilancia[] }>(`${API}/vigilancia/fuentes`);
  }
  configurarVigilancia(radicado: string, fuente: FuenteVigilancia, frecuenciaHoras: number, activo: boolean):
    Observable<RadicadoVigilado> {
    return this.http.put<RadicadoVigilado>(`${API}/vigilancia/radicados/${radicado}`, { fuente, frecuenciaHoras, activo });
  }
  consultarRadicado(radicado: string): Observable<ResultadoConsulta> {
    return this.http.post<ResultadoConsulta>(`${API}/vigilancia/radicados/${radicado}/consultar`, {});
  }
  historialVigilancia(radicado: string): Observable<ConsultaVigilancia[]> {
    return this.http.get<ConsultaVigilancia[]>(`${API}/vigilancia/radicados/${radicado}/consultas`);
  }
  agregarActuacionSimulada(radicado: string, a: { fecha: string; actuacion: string; anotacion: string }): Observable<unknown> {
    return this.http.post(`${API}/vigilancia/radicados/${radicado}/simuladas`, a);
  }

  // ---------- Telegram ----------
  estadoTelegram(idPersona: number): Observable<EstadoTelegram> {
    return this.http.get<EstadoTelegram>(`${API}/notificaciones/telegram`, { params: filtros({ idPersona }) });
  }
  vincularTelegram(idPersona: number): Observable<VinculoTelegram> {
    return this.http.post<VinculoTelegram>(`${API}/notificaciones/telegram/vinculo`, {}, { params: filtros({ idPersona }) });
  }
  desvincularTelegram(idPersona: number): Observable<unknown> {
    return this.http.delete(`${API}/notificaciones/telegram`, { params: filtros({ idPersona }) });
  }

  // ---------- peritos, tarifas, honorarios ----------
  peritos(q?: string): Observable<Perito[]> {
    return this.http.get<Perito[]>(`${API}/peritos`, { params: filtros({ q }) });
  }
  crearPerito(p: Perito): Observable<Perito> { return this.http.post<Perito>(`${API}/peritos`, p); }
  actualizarPerito(id: number, p: Perito): Observable<Perito> { return this.http.put<Perito>(`${API}/peritos/${id}`, p); }

  tarifas(anio?: number): Observable<Tarifa[]> {
    return this.http.get<Tarifa[]>(`${API}/gabinete/tarifas`, { params: filtros({ anio }) });
  }
  honorarios(f: { idCliente?: number; idProceso?: number; estado?: string } = {}): Observable<Honorario[]> {
    return this.http.get<Honorario[]>(`${API}/honorarios`, { params: filtros(f) });
  }
  crearHonorario(h: HonorarioSolicitud): Observable<Honorario> { return this.http.post<Honorario>(`${API}/honorarios`, h); }
  cambiarEstadoHonorario(id: number, estado: string): Observable<Honorario> {
    return this.http.put<Honorario>(`${API}/honorarios/${id}/estado`, { estado });
  }
  pagos(idHonorario: number): Observable<Pago[]> { return this.http.get<Pago[]>(`${API}/honorarios/${idHonorario}/pagos`); }
  registrarPago(idHonorario: number, p: Pago): Observable<Honorario> {
    return this.http.post<Honorario>(`${API}/honorarios/${idHonorario}/pagos`, p);
  }

  // ---------- asistente ----------
  estadoAsistente(): Observable<EstadoAsistente> { return this.http.get<EstadoAsistente>(`${API}/asistente/estado`); }
  /** URL para pedir un borrador por partes (se lee con fetch: ver AsistenteService). */
  urlBorradores(): string { return `${API}/asistente/borradores`; }
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
