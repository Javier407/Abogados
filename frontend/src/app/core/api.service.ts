import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  Abogado, AreaPractica, Aviso, Cargo, Cliente, ConsultaVigilancia, EstadoAsistente, Honorario, HonorarioSolicitud,
  Pago, Perito, Tarifa, DesgloseRadicado, Despacho, DiaInhabil, EstadoTelegram,
  FuenteVigilancia, Proceso, ProcesoSolicitud, Providencia, RadicadoVigilado, Rama, ResultadoConsulta, Revision, Sede,
  Termino, TerminoSolicitud, TipoProceso, Vencimiento, VinculoTelegram,
  Actuacion, ActuacionSolicitud, Expediente, Parte, ParteRegistrada, ParteSolicitud, ProvidenciaSolicitud,
  Audiencia, Prueba, PruebaSolicitud, SugerenciaTermino,
  CambioCuenta, CargaAbogado, CarteraCliente, Cuenta, CuentaSolicitud, CumplimientoTerminos, Gasto, GastoSolicitud,
  ResultadoRama, ProcesoPortal, DetallePortal, FinanzasPortal, ResumenPublicado,
  CategoriaDocumento, Consulta, ConsultaConAvisos, Documento, EstadoConsulta, Integrante, RolEquipo,
  CambioRegla, EstadoServidor, PruebaPatron, ReglaSolicitud, ReglaTermino
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
  /** Sedes, cargos y áreas (F11, solo la dirección): sin id crea; con id cambia. */
  guardarCatalogo<T>(tipo: 'sedes' | 'cargos' | 'areas', id: number | null, datos: T): Observable<T> {
    return id === null
      ? this.http.post<T>(`${API}/gabinete/${tipo}`, datos)
      : this.http.put<T>(`${API}/gabinete/${tipo}/${id}`, datos);
  }
  borrarCatalogo(tipo: 'sedes' | 'cargos' | 'areas', id: number): Observable<void> {
    return this.http.delete<void>(`${API}/gabinete/${tipo}/${id}`);
  }

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

  // ---------------------------------------------------------------- reglas de términos (F12)

  reglas(): Observable<ReglaTermino[]> { return this.http.get<ReglaTermino[]>(`${API}/reglas-termino`); }
  historialRegla(id: number): Observable<CambioRegla[]> {
    return this.http.get<CambioRegla[]>(`${API}/reglas-termino/${id}/historial`);
  }
  probarPatron(patron: string, clase: string): Observable<PruebaPatron> {
    return this.http.get<PruebaPatron>(`${API}/reglas-termino/prueba`, { params: filtros({ patron, clase }) });
  }
  guardarRegla(id: number | null, r: ReglaSolicitud): Observable<ReglaTermino> {
    return id == null ? this.http.post<ReglaTermino>(`${API}/reglas-termino`, r)
      : this.http.put<ReglaTermino>(`${API}/reglas-termino/${id}`, r);
  }
  validarRegla(id: number): Observable<ReglaTermino> {
    return this.http.post<ReglaTermino>(`${API}/reglas-termino/${id}/validacion`, {});
  }
  cambiarContestacion(idTipo: number, dias: number | null): Observable<{ reglasSinValidar: number }> {
    return this.http.put<{ reglasSinValidar: number }>(`${API}/catalogos/tipos/${idTipo}/contestacion`, { dias });
  }

  /** Cuentas con la verificación en dos pasos activa (F13, solo ADMIN). */
  cuentasConMfa(): Observable<number[]> { return this.http.get<number[]>(`${API}/usuarios/mfa`); }
  reiniciarMfa(idUsuario: number): Observable<void> {
    return this.http.post<void>(`${API}/usuarios/${idUsuario}/mfa/reinicio`, {});
  }

  /** Estado del servidor según el vigía (F12, solo ADMIN). */
  estadoServidor(dias: number): Observable<EstadoServidor> {
    return this.http.get<EstadoServidor>(`${API}/notificaciones/servidor`, { params: filtros({ dias }) });
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

  registrarProvidencia(idProceso: number, p: ProvidenciaSolicitud): Observable<Providencia> {
    return this.http.post<Providencia>(`${API}/procesos/${idProceso}/providencias`, p);
  }

  // ---------- expediente ----------
  expediente(idProceso: number): Observable<Expediente> {
    return this.http.get<Expediente>(`${API}/procesos/${idProceso}/expediente`);
  }
  partes(idProceso: number): Observable<Parte[]> {
    return this.http.get<Parte[]>(`${API}/procesos/${idProceso}/partes`);
  }
  agregarParte(idProceso: number, p: ParteSolicitud): Observable<ParteRegistrada> {
    return this.http.post<ParteRegistrada>(`${API}/procesos/${idProceso}/partes`, p);
  }
  eliminarParte(idProceso: number, idParte: number): Observable<void> {
    return this.http.delete<void>(`${API}/procesos/${idProceso}/partes/${idParte}`);
  }
  actuaciones(idProceso: number): Observable<Actuacion[]> {
    return this.http.get<Actuacion[]>(`${API}/procesos/${idProceso}/actuaciones`);
  }
  registrarActuacion(idProceso: number, a: ActuacionSolicitud): Observable<Actuacion> {
    return this.http.post<Actuacion>(`${API}/procesos/${idProceso}/actuaciones`, a);
  }
  eliminarActuacion(idProceso: number, idActuacion: number): Observable<void> {
    return this.http.delete<void>(`${API}/procesos/${idProceso}/actuaciones/${idActuacion}`);
  }

  sugerencias(idProceso: number, idProvidencia: number): Observable<SugerenciaTermino[]> {
    return this.http.get<SugerenciaTermino[]>(`${API}/procesos/${idProceso}/providencias/${idProvidencia}/sugerencias`);
  }

  // ---------- pruebas y agenda ----------
  pruebas(idProceso: number): Observable<Prueba[]> {
    return this.http.get<Prueba[]>(`${API}/procesos/${idProceso}/pruebas`);
  }
  crearPrueba(idProceso: number, p: PruebaSolicitud): Observable<Prueba> {
    return this.http.post<Prueba>(`${API}/procesos/${idProceso}/pruebas`, p);
  }
  actualizarPrueba(idProceso: number, idPrueba: number, p: PruebaSolicitud): Observable<Prueba> {
    return this.http.put<Prueba>(`${API}/procesos/${idProceso}/pruebas/${idPrueba}`, p);
  }
  eliminarPrueba(idProceso: number, idPrueba: number): Observable<void> {
    return this.http.delete<void>(`${API}/procesos/${idProceso}/pruebas/${idPrueba}`);
  }
  audiencias(desde: string, hasta: string, idAsistente?: number | null): Observable<Audiencia[]> {
    return this.http.get<Audiencia[]>(`${API}/audiencias`, { params: filtros({ desde, hasta, idAsistente }) });
  }

  // ---------- administración de cuentas (solo ADMIN) ----------
  // ---------- portal del cliente (solo rol CLIENTE; el servidor filtra por el cliente del token) ----------
  portalProcesos(): Observable<ProcesoPortal[]> {
    return this.http.get<ProcesoPortal[]>(`${API}/portal/procesos`);
  }
  portalProceso(id: number): Observable<DetallePortal> {
    return this.http.get<DetallePortal>(`${API}/portal/procesos/${id}`);
  }
  portalFinanzas(): Observable<FinanzasPortal> {
    return this.http.get<FinanzasPortal>(`${API}/portal/finanzas`);
  }
  /** Último resumen publicado para el cliente (null si aún no hay). */
  resumenCliente(idProceso: number): Observable<ResumenPublicado | null> {
    return this.http.get<ResumenPublicado | null>(`${API}/procesos/${idProceso}/resumen-cliente`);
  }
  publicarResumen(idProceso: number, texto: string): Observable<ResumenPublicado> {
    return this.http.post<ResumenPublicado>(`${API}/procesos/${idProceso}/resumen-cliente`, { texto });
  }

  // ---------- equipo del proceso (F11) ----------
  equipo(idProceso: number): Observable<Integrante[]> {
    return this.http.get<Integrante[]>(`${API}/procesos/${idProceso}/asignaciones`);
  }
  agregarIntegrante(idProceso: number, idPersona: number, rol: RolEquipo): Observable<Integrante> {
    return this.http.post<Integrante>(`${API}/procesos/${idProceso}/asignaciones`, { idPersona, rol });
  }
  quitarIntegrante(idProceso: number, idPersona: number): Observable<void> {
    return this.http.delete<void>(`${API}/procesos/${idProceso}/asignaciones/${idPersona}`);
  }

  // ---------- expediente digital (F10) ----------
  documentos(idProceso: number): Observable<Documento[]> {
    return this.http.get<Documento[]>(`${API}/procesos/${idProceso}/documentos`);
  }
  subirDocumento(idProceso: number, archivo: File, categoria: CategoriaDocumento, descripcion: string,
                 visibleCliente: boolean): Observable<Documento> {
    const datos = new FormData();
    datos.append('archivo', archivo, archivo.name);
    datos.append('categoria', categoria);
    if (descripcion.trim()) datos.append('descripcion', descripcion.trim());
    datos.append('visibleCliente', String(visibleCliente));
    return this.http.post<Documento>(`${API}/procesos/${idProceso}/documentos`, datos);
  }
  actualizarDocumento(idProceso: number, d: Documento): Observable<Documento> {
    return this.http.put<Documento>(`${API}/procesos/${idProceso}/documentos/${d.idDocumento}`,
      { categoria: d.categoria, descripcion: d.descripcion, visibleCliente: d.visibleCliente });
  }
  eliminarDocumento(idProceso: number, idDocumento: number): Observable<void> {
    return this.http.delete<void>(`${API}/procesos/${idProceso}/documentos/${idDocumento}`);
  }
  /** El archivo como Blob (la descarga lleva el token: no se puede con un enlace directo). */
  archivoDocumento(idProceso: number, idDocumento: number, portal = false): Observable<Blob> {
    const base = portal ? `${API}/portal/procesos` : `${API}/procesos`;
    return this.http.get(`${base}/${idProceso}/documentos/${idDocumento}/archivo`, { responseType: 'blob' });
  }

  // ---------- consultas iniciales (F10) ----------
  consultas(estado?: EstadoConsulta | ''): Observable<Consulta[]> {
    return this.http.get<Consulta[]>(`${API}/consultas`, { params: filtros({ estado: estado || undefined }) });
  }
  consulta(id: number): Observable<ConsultaConAvisos> {
    return this.http.get<ConsultaConAvisos>(`${API}/consultas/${id}`);
  }
  registrarConsulta(c: Partial<Consulta>): Observable<ConsultaConAvisos> {
    return this.http.post<ConsultaConAvisos>(`${API}/consultas`, c);
  }
  contratarConsulta(id: number, datos: { ciudad: string; idSede: number | null; direccion: string }): Observable<ConsultaConAvisos> {
    return this.http.post<ConsultaConAvisos>(`${API}/consultas/${id}/contratacion`, datos);
  }
  cerrarConsulta(id: number, estado: EstadoConsulta, motivo: string): Observable<ConsultaConAvisos> {
    return this.http.post<ConsultaConAvisos>(`${API}/consultas/${id}/cierre`, { estado, motivo });
  }

  cuentas(): Observable<Cuenta[]> {
    return this.http.get<Cuenta[]>(`${API}/usuarios`);
  }
  crearCuenta(c: CuentaSolicitud): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${API}/usuarios`, c);
  }
  actualizarCuenta(id: number, c: CambioCuenta): Observable<Cuenta> {
    return this.http.put<Cuenta>(`${API}/usuarios/${id}`, c);
  }
  restablecerClave(id: number, claveTemporal: string): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${API}/usuarios/${id}/clave`, { claveTemporal });
  }
  desbloquearCuenta(id: number): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${API}/usuarios/${id}/desbloqueo`, {});
  }

  // ---------- gastos, cartera y reportes ----------
  gastos(f: { idProceso?: number; idCliente?: number }): Observable<Gasto[]> {
    return this.http.get<Gasto[]>(`${API}/gastos`, { params: filtros(f) });
  }
  registrarGasto(g: GastoSolicitud): Observable<Gasto> {
    return this.http.post<Gasto>(`${API}/gastos`, g);
  }
  reembolsarGasto(id: number): Observable<Gasto> {
    return this.http.post<Gasto>(`${API}/gastos/${id}/reembolso`, {});
  }
  eliminarGasto(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/gastos/${id}`);
  }
  cartera(): Observable<CarteraCliente[]> {
    return this.http.get<CarteraCliente[]>(`${API}/honorarios/cartera`);
  }
  reporteCarga(): Observable<CargaAbogado[]> {
    return this.http.get<CargaAbogado[]>(`${API}/reportes/carga`);
  }
  reporteTerminos(desde?: string, hasta?: string): Observable<CumplimientoTerminos[]> {
    return this.http.get<CumplimientoTerminos[]>(`${API}/reportes/terminos`, { params: filtros({ desde, hasta }) });
  }
  reporteResultados(desde?: string, hasta?: string): Observable<ResultadoRama[]> {
    return this.http.get<ResultadoRama[]>(`${API}/reportes/resultados`, { params: filtros({ desde, hasta }) });
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
  anularPago(idHonorario: number, idPago: number, motivo: string): Observable<Honorario> {
    return this.http.post<Honorario>(`${API}/honorarios/${idHonorario}/pagos/${idPago}/anulacion`, { motivo });
  }
  liquidarHonorario(idHonorario: number, valorBase: number, fecha?: string): Observable<Honorario> {
    return this.http.post<Honorario>(`${API}/honorarios/${idHonorario}/liquidacion`, { valorBase, fecha });
  }
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
