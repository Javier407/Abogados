// Modelos que devuelve el gateway (/api). Contratos: docs/contratos/*.openapi.yaml

// ---------- gabinete ----------
export type TipoPersona = 'NATURAL' | 'JURIDICA';
export type TipoDocumento = 'CC' | 'CE' | 'NIT' | 'PAS';

export interface Cliente {
  idCliente?: number;
  tipoPersona: TipoPersona;
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  nombre: string;
  email?: string;
  telefono?: string;
  direccion?: string;
  ciudad: string;
  idSede: number;
  creado?: string;
}

export interface Abogado {
  idPersona: number;
  nombres: string;
  apellidos: string;
  email: string;
  telefono?: string;
  idCargo: number;
  cargo?: string;
  idSede: number;
  sede?: string;
  idArea?: number | null;
  area?: string;
  tarjetaProfesional: string;
  tarifaHora?: number | null;
  fechaIngreso?: string;
  activo?: boolean;
}

export interface Sede { idSede: number; nombre: string; ciudad: string; esPrincipal: boolean; }
export interface Cargo { idCargo: number; codigo: string; nombre: string; esAbogado: boolean; }
export interface AreaPractica { idArea: number; codigoRama: string; nombre: string; }

// ---------- procesos ----------
export const ESTADOS = ['ACTIVO', 'SUSPENDIDO', 'TERMINADO', 'ARCHIVADO'] as const;
export const ETAPAS = ['PODER', 'DEMANDA', 'ADMISION', 'NOTIFICACION', 'CONTESTACION', 'INVESTIGACION',
  'PRUEBAS', 'AUDIENCIA', 'JUICIO', 'SENTENCIA', 'RECURSO', 'CERRADO'] as const;
export const ROLES_CLIENTE = ['DEMANDANTE', 'DEMANDADO', 'VICTIMA', 'PROCESADO', 'ACCIONANTE', 'ACCIONADO'] as const;
export const CLASES_CUANTIA = ['MINIMA', 'MENOR', 'MAYOR', 'SIN_CUANTIA'] as const;
export const TIPOS_SERVICIO = ['REPRESENTACION', 'PRESENCIAL', 'VIRTUAL'] as const;

export type EstadoProceso = typeof ESTADOS[number];

export interface Rama { codigo: string; nombre: string; norma: string; }
export interface TipoProceso { idTipo: number; codigoRama: string; nombre: string; terminoContestacionDias?: number; }
export interface Despacho {
  idDespacho: number; nombre: string; ciudad: string; codDane: string; codEntidad: string;
  codEspecialidad: string; codDespacho: string; codigoRama?: string;
}

/** Lo que se envía al crear o editar un proceso. */
export interface ProcesoSolicitud {
  radicado: string;
  idTipo: number | null;
  idDespachoActual?: number | null;
  idCliente: number | null;
  rolCliente: string;
  asunto: string;
  cuantia?: number | null;
  claseCuantia: string;
  tipoServicio: string;
  estado?: string;
  etapa: string;
  fechaRadicacion: string;
  vigilado?: boolean;
  idResponsable: number | null;
}

export interface Proceso {
  idProceso: number;
  radicado: string;
  radicadoLegible: string;
  idTipo: number;
  tipoProceso: string;
  codigoRama: string;
  rama: string;
  despachoOrigen: string;
  idDespachoActual?: number;
  despachoActual: string;
  instancia: number;
  idCliente: number;
  rolCliente: string;
  asunto: string;
  cuantia?: number;
  claseCuantia: string;
  tipoServicio: string;
  estado: EstadoProceso;
  etapa: string;
  fechaRadicacion: string;
  vigilado: boolean;
  idResponsable?: number;
}

export interface DesgloseRadicado {
  radicado: string;
  radicadoLegible: string;
  codDane: string;
  codEntidad: string;
  codEspecialidad: string;
  codDespacho: string;
  anio: number;
  consecutivo: number;
  instancia: number;
  despacho?: Despacho;
  idProcesoExistente?: number;
}

/** Palabras que en el backend van sin tilde (los valores son MAYÚSCULAS ASCII). */
const TILDES: Record<string, string> = {
  admision: 'admisión', notificacion: 'notificación', contestacion: 'contestación', investigacion: 'investigación',
  representacion: 'representación', cuantia: 'cuantía', victima: 'víctima', juridica: 'jurídica', minima: 'mínima',
  dia: 'día', consignacion: 'consignación'
};

/** Texto legible para valores en MAYÚSCULAS_CON_GUION: 'SIN_CUANTIA' -> 'Sin cuantía'. */
export function etiqueta(valor: string | null | undefined): string {
  if (!valor) return '';
  const t = valor.toLowerCase().split('_').map(p => TILDES[p] ?? p).join(' ');
  return t.charAt(0).toUpperCase() + t.slice(1);
}

// ---------- términos y calendario (F2) ----------
export const SITUACIONES = ['VENCIDO', 'POR_VENCER', 'AL_DIA', 'CUMPLIDO'] as const;
export type Situacion = typeof SITUACIONES[number];

export interface Termino {
  idTermino: number;
  idProceso: number;
  radicado: string;
  radicadoLegible: string;
  rama: string;
  asunto: string;
  idProvidencia?: number;
  descripcion: string;
  fechaInicio: string;
  diasHabiles: number;
  fechaVencimiento: string;
  estado: 'PENDIENTE' | 'CUMPLIDO';
  idResponsable: number;
  diasHabilesRestantes: number;
  situacion: Situacion;
}

export interface TerminoSolicitud {
  descripcion: string;
  fechaInicio: string;
  diasHabiles: number | null;
  idProvidencia?: number | null;
  idResponsable?: number | null;
}

export interface DiaInhabil { fecha: string; descripcion: string; tipo: 'FESTIVO' | 'VACANCIA' | 'SEMANA_SANTA'; }
export interface Vencimiento { fechaInicio: string; diasHabiles: number; fechaVencimiento: string; }
export interface Revision { fecha: string; porVencer: number; vencidos: number; }

// ---------- avisos (servicio notificaciones) ----------
export interface Aviso {
  idNotificacion: number;
  evento: string;
  titulo: string;
  mensaje: string;
  referencia: { id_proceso?: number; id_termino?: number; portal?: boolean };
  leida: boolean;
  creada: string;
}

/** Texto para los días hábiles que faltan: 'vence hoy', 'faltan 3 días', 'venció hace 2 días'. */
export function textoRestantes(dias: number): string {
  if (dias === 0) return 'vence hoy';
  if (dias > 0) return `falta${dias === 1 ? '' : 'n'} ${dias} día${dias === 1 ? '' : 's'} hábil${dias === 1 ? '' : 'es'}`;
  const n = -dias;
  return `venció hace ${n} día${n === 1 ? '' : 's'} hábil${n === 1 ? '' : 'es'}`;
}

// ---------- vigilancia (F3) ----------
export type FuenteVigilancia = 'MANUAL' | 'SIMULADA' | 'PORTAL_HTTP';
export const FUENTES: { valor: FuenteVigilancia; etiqueta: string; ayuda: string }[] = [
  { valor: 'MANUAL', etiqueta: 'Manual', ayuda: 'No se consulta; las actuaciones se registran a mano.' },
  { valor: 'SIMULADA', etiqueta: 'Simulada (pruebas)', ayuda: 'Actuaciones escritas aquí, para demostraciones.' },
  { valor: 'PORTAL_HTTP', etiqueta: 'Portal Rama Judicial', ayuda: 'Consulta real, máximo cada 12 h. Solo radicados reales de la firma.' }
];

export interface RadicadoVigilado {
  radicado: string;
  radicadoLegible: string;
  idProceso: number;
  fuente: FuenteVigilancia;
  frecuenciaHoras: number;
  activo: boolean;
  ultimaConsulta: string | null;
  fallosConsecutivos: number;
  enAlerta: boolean;
  despacho: string | null;
  ultimaActuacion: string | null;
  ultimoError: string | null;
  lineaBaseHecha: boolean;
}

export interface ResultadoConsulta { radicado: string; exitosa: boolean; actuaciones?: number; nuevas?: number; lineaBase?: boolean; error?: string; }
export interface ConsultaVigilancia { fecha: string; exitosa: boolean; nuevas: number; detalle: Record<string, unknown>; }

export interface Providencia {
  idProvidencia: number;
  idProceso: number;
  tipo: 'AUTO' | 'SENTENCIA';
  clase: string;
  fechaProvidencia: string;
  fechaEstado: string;
  decision?: string;
  sentido?: string;
  fuente: 'MANUAL' | 'VIGILANCIA';
}

export interface EstadoTelegram { configurado: boolean; vinculado: boolean; bot: string | null; }
export interface VinculoTelegram { codigo: string; enlace: string | null; bot: string | null; }

// ---------- peritos, tarifas, honorarios y pagos (F4) ----------
export interface Perito {
  idPerito?: number;
  nombre: string;
  especialidad: string;
  profesion: string;
  registro?: string;
  telefono?: string;
  email?: string;
  ciudad: string;
  tarifaDictamen?: number | null;
  calificacion?: number | null;
}

export const FORMAS_COBRO = ['FIJO', 'MENSUAL', 'CUOTA_LITIS', 'MIXTO', 'PORCENTAJE_CUANTIA'] as const;
export type FormaCobro = typeof FORMAS_COBRO[number];
export const MEDIOS_PAGO = ['TRANSFERENCIA', 'EFECTIVO', 'TARJETA', 'CONSIGNACION'] as const;

/** Qué campos pide cada forma de cobro (igual que la regla del servicio gabinete). */
export function camposDeForma(f: FormaCobro): { fijo: boolean; porcentaje: boolean } {
  return { fijo: ['FIJO', 'MENSUAL', 'MIXTO'].includes(f), porcentaje: ['CUOTA_LITIS', 'MIXTO', 'PORCENTAJE_CUANTIA'].includes(f) };
}

export interface Tarifa {
  idTarifa: number;
  fuente: 'CONALBOS' | 'FIRMA';
  codigoRama?: string;
  servicio: string;
  formaCobro: FormaCobro;
  valorSmmlv?: number;
  valorSmmlvMax?: number;
  porcentaje?: number;
  nota?: string;
  anioSmmlv: number;
  desdePesos?: number;
  hastaPesos?: number;
}

export interface Honorario {
  idHonorario: number;
  idProceso: number;
  idCliente: number;
  cliente: string;
  idTarifa?: number;
  formaCobro: FormaCobro;
  valorFijo?: number;
  porcentaje?: number;
  valorEstimado?: number;
  fechaPacto: string;
  estado: 'VIGENTE' | 'CUMPLIDO' | 'CANCELADO';
  pagado: number;
  saldo?: number;
  pagos: number;
  valorBase?: number | null;
  valorLiquidado?: number | null;
  fechaLiquidacion?: string | null;
}

export interface HonorarioSolicitud {
  idProceso: number | null;
  idCliente: number | null;
  idTarifa?: number | null;
  formaCobro: FormaCobro;
  valorFijo?: number | null;
  porcentaje?: number | null;
  valorEstimado?: number | null;
  fechaPacto: string;
}

export interface Pago {
  idPago?: number;
  fecha: string;
  valor: number | null;
  medio: string;
  referencia?: string;
  anuladoEn?: string | null;
  anuladoPor?: number | null;
  motivoAnulacion?: string | null;
}

// ---------- asistente (F4) ----------
export type TipoBorrador = 'CONTESTACION' | 'RESUMEN_CLIENTE' | 'DEMANDA' | 'RECURSO';
export interface EstadoAsistente { modelo: string; disponible: boolean; detalle: string; }
export interface EventoBorrador { evento: 'inicio' | 'texto' | 'aviso' | 'fin'; texto?: string; mensaje?: string; modelo?: string | null; conModelo?: boolean; segundos?: number; }

// ---------- expediente (F6) ----------
export const ROLES_PARTE = ['DEMANDANTE', 'DEMANDADO', 'VICTIMA', 'PROCESADO', 'ACCIONANTE', 'ACCIONADO', 'TERCERO', 'LLAMADO_GARANTIA'] as const;
export type RolParte = typeof ROLES_PARTE[number];

export interface Parte {
  idParte: number;
  idProceso: number;
  rol: RolParte;
  tipoDocumento?: TipoDocumento | null;
  numeroDocumento?: string | null;
  nombre: string;
  esCliente: boolean;
  apoderado?: string | null;
}
export interface ParteSolicitud {
  rol: RolParte;
  tipoDocumento?: TipoDocumento | null;
  numeroDocumento?: string | null;
  nombre: string;
  apoderado?: string | null;
}
export interface ConflictoInteres { idCliente: number; nombre: string; numeroDocumento: string; motivo: string; }
export interface ParteRegistrada { parte: Parte; conflictos: ConflictoInteres[]; conflictosVerificados: boolean; }

export const TIPOS_ACTUACION = ['PODER', 'DEMANDA', 'CONTESTACION', 'MEMORIAL', 'RECURSO', 'AUDIENCIA', 'ALEGATOS', 'NOTIFICACION', 'OTRO'] as const;
export type TipoActuacion = typeof TIPOS_ACTUACION[number];

export interface Actuacion {
  idActuacion: number;
  idProceso: number;
  fecha: string;
  tipo: TipoActuacion;
  descripcion: string;
  idPersona?: number | null;
  idProvidencia?: number | null;
  documentoUrl?: string | null;
  registradoPor?: number | null;
  hora?: string | null;
  lugar?: string | null;
}
export interface ActuacionSolicitud {
  tipo: TipoActuacion;
  fecha: string;
  descripcion: string;
  idPersona?: number | null;
  idProvidencia?: number | null;
  documentoUrl?: string | null;
  idTerminoCumplido?: number | null;
  hora?: string | null;
  lugar?: string | null;
}
export interface ProvidenciaSolicitud {
  tipo: 'AUTO' | 'SENTENCIA';
  clase: string;
  fechaProvidencia: string;
  fechaEstado: string;
  decision?: string | null;
  sentido?: 'FAVORABLE' | 'DESFAVORABLE' | 'PARCIAL' | null;
}
export interface EventoExpediente {
  fecha: string;
  categoria: 'ACTUACION' | 'PROVIDENCIA' | 'TERMINO';
  tipo: string;
  id: number;
  titulo: string;
  detalle?: string | null;
  origen?: string | null;
}
export interface Instancia { idProceso: number; radicadoLegible: string; instancia: number; despacho: string; estado: string; }
export interface Expediente { lineaDeTiempo: EventoExpediente[]; otrasInstancias: Instancia[]; }

// ---------- agenda, pruebas y términos sugeridos (F7) ----------
export interface Audiencia {
  idActuacion: number;
  idProceso: number;
  radicado: string;
  radicadoLegible: string;
  asunto: string;
  fecha: string;
  hora?: string | null;
  descripcion: string;
  lugar?: string | null;
  idAsistente?: number | null;
}

export const TIPOS_PRUEBA = ['DOCUMENTAL', 'PERICIAL', 'TESTIMONIAL', 'INTERROGATORIO', 'INSPECCION', 'VIDEO', 'ANEXO'] as const;
export type TipoPrueba = typeof TIPOS_PRUEBA[number];
export const ESTADOS_PRUEBA = ['SOLICITADA', 'DECRETADA', 'PRACTICADA', 'NEGADA'] as const;
export type EstadoPrueba = typeof ESTADOS_PRUEBA[number];

export interface Prueba {
  idPrueba: number;
  idProceso: number;
  tipo: TipoPrueba;
  descripcion: string;
  idPerito?: number | null;
  estado: EstadoPrueba;
  fechaEntrega?: string | null;
  idTermino?: number | null;
}
export interface PruebaSolicitud {
  tipo: TipoPrueba;
  descripcion: string;
  idPerito?: number | null;
  estado: EstadoPrueba;
  fechaEntrega?: string | null;
}

export interface SugerenciaTermino {
  descripcion: string;
  diasHabiles: number;
  fechaInicio: string;
  fechaVencimiento: string;
  norma: string;
  origen: 'REGLA' | 'PROVIDENCIA';
  prioridad: number;
  validada: boolean;
}

// ---------- administración, gastos y reportes (F8) ----------
export const ROLES_CUENTA = ['ADMIN', 'SOCIO', 'ABOGADO', 'DEPENDIENTE', 'ADMINISTRATIVO', 'CLIENTE'] as const;

export interface Cuenta {
  idUsuario: number;
  usuario: string;
  nombre: string;
  email: string;
  activo: boolean;
  roles: string[];
  bloqueadoHasta?: string | null;
  ultimoIngreso?: string | null;
  debeCambiarClave: boolean;
  /** Solo las cuentas de cliente: su ficha en gabinete. */
  idCliente?: number | null;
}
export interface CuentaSolicitud {
  idUsuario: number | null;
  usuario: string;
  nombre: string;
  email: string;
  roles: string[];
  claveTemporal: string;
  idCliente?: number | null;
}
export interface CambioCuenta {
  usuario: string; nombre: string; email: string; roles: string[]; activo: boolean; idCliente?: number | null;
}

// ---------- portal del cliente (F9) ----------
export interface AudienciaPortal { fecha: string; hora: string | null; lugar: string | null; descripcion: string; }
export interface ProcesoPortal {
  idProceso: number;
  radicado: string;
  tipoProceso: string;
  rama: string;
  despacho: string | null;
  asunto: string;
  rolCliente: string;
  etapa: string;
  estado: string;
  fechaRadicacion: string | null;
  abogado: string | null;
  proximaAudiencia: AudienciaPortal | null;
  ultimaNovedad: string | null;
}
export interface NovedadPortal { fecha: string; origen: string; titulo: string; detalle: string | null; }
export interface ResumenPublicado { idResumen: number; idProceso: number; texto: string; publicadoPor: number; publicadoEn: string; }
export interface DetallePortal {
  proceso: ProcesoPortal;
  novedades: NovedadPortal[];
  audiencias: AudienciaPortal[];
  resumen: ResumenPublicado | null;
  documentos: DocumentoPortal[];
}
export interface DocumentoPortal {
  idDocumento: number; nombre: string; categoria: string; descripcion: string | null; tamano: number; subido: string;
}

// ---------- expediente digital (F10) ----------
export type CategoriaDocumento = 'PODER' | 'DEMANDA' | 'CONTESTACION' | 'MEMORIAL' | 'PRUEBA' | 'PROVIDENCIA' | 'CONTRATO' | 'OTRO';
export const CATEGORIAS_DOCUMENTO: CategoriaDocumento[] =
  ['PODER', 'DEMANDA', 'CONTESTACION', 'MEMORIAL', 'PRUEBA', 'PROVIDENCIA', 'CONTRATO', 'OTRO'];
export interface Documento {
  idDocumento: number;
  idProceso: number;
  nombre: string;
  tipoMime: string;
  tamano: number;
  sha256: string;
  categoria: CategoriaDocumento;
  descripcion: string | null;
  visibleCliente: boolean;
  subidoPor: number;
  subido: string;
}

// ---------- consulta inicial (F10) ----------
export type EstadoConsulta = 'ABIERTA' | 'CONTRATADA' | 'NO_CONTRATADA' | 'REMITIDA';
export interface Consulta {
  idConsulta: number;
  fecha: string;
  nombre: string;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  email: string | null;
  telefono: string | null;
  contraparte: string | null;
  contraparteDocumento: string | null;
  codigoRama: string | null;
  asunto: string;
  descripcion: string | null;
  idAbogado: number | null;
  estado: EstadoConsulta;
  motivoCierre: string | null;
  idCliente: number | null;
}
export interface ConsultaConAvisos { consulta: Consulta; avisos: string[]; }

/** Tamaño de archivo legible: 1,2 MB, 340 KB. */
export function tamanoLegible(bytes: number): string {
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toLocaleString('es-CO', { maximumFractionDigits: 1 }) + ' MB';
  return Math.max(1, Math.round(bytes / 1024)).toLocaleString('es-CO') + ' KB';
}
export interface PagoPortal { fecha: string; valor: number; medio: string; referencia: string | null; }
export interface AcuerdoPortal {
  idHonorario: number; idProceso: number; formaCobro: string; valorFijo: number | null; porcentaje: number | null;
  total: number | null; pagado: number; saldo: number | null; estado: string; pagos: PagoPortal[];
}
export interface GastoPortal { idProceso: number; fecha: string; concepto: string; valor: number; }
export interface FinanzasPortal {
  acuerdos: AcuerdoPortal[];
  gastosPorReembolsar: GastoPortal[];
  saldoHonorarios: number;
  totalGastosPorReembolsar: number;
}

export interface Gasto {
  idGasto: number;
  idProceso: number;
  idCliente: number;
  fecha: string;
  concepto: string;
  valor: number;
  pagadoPor: 'FIRMA' | 'CLIENTE';
  reembolsable: boolean;
  reembolsadoEn?: string | null;
  soporteUrl?: string | null;
  registradoPor?: number | null;
}
export interface GastoSolicitud {
  idProceso: number;
  fecha: string;
  concepto: string;
  valor: number | null;
  pagadoPor: 'FIRMA' | 'CLIENTE';
  reembolsable: boolean;
  soporteUrl?: string | null;
}
export interface CarteraCliente {
  idCliente: number;
  cliente: string;
  acuerdos: number;
  pactado?: number | null;
  pagado: number;
  saldoHonorarios: number;
  gastosPorReembolsar: number;
  totalPorCobrar: number;
  ultimoPago?: string | null;
  diasSinPagar?: number | null;
}
export interface CargaAbogado {
  idAbogado: number;
  procesosActivos: number;
  procesosSuspendidos: number;
  terminosPendientes: number;
  terminosVencidos: number;
  audienciasProximas: number;
}
export interface CumplimientoTerminos {
  idAbogado: number;
  total: number;
  aTiempo: number;
  tarde: number;
  cumplidosSinFecha: number;
  vencidosSinCumplir: number;
  pendientes: number;
}
export interface ResultadoRama {
  codigoRama: string;
  rama: string;
  favorables: number;
  parciales: number;
  desfavorables: number;
  sinSentido: number;
}
