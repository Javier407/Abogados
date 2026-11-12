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

/** Un proceso en el que participa un abogado, con su papel y lo que tiene pendiente (ficha del abogado). */
export interface ProcesoDeAbogado {
  idProceso: number;
  radicadoLegible: string;
  asunto: string;
  codigoRama: string;
  rama: string;
  tipoProceso: string;
  etapa: string;
  estado: string;
  rol: 'RESPONSABLE' | 'APOYO' | 'DEPENDIENTE';
  desde: string;
  terminosPendientes: number;
  proximoVencimiento?: string | null;
}

export interface Sede { idSede: number; nombre: string; ciudad: string; direccion?: string; telefono?: string | null; esPrincipal: boolean; }
export interface Cargo { idCargo: number; codigo: string; nombre: string; esAbogado: boolean; }
export interface AreaPractica { idArea: number; codigoRama: string; nombre: string; idDirector?: number; }

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
  /** Solo en un proceso cerrado con «Cerrar proceso». */
  fechaCierre?: string | null;
  motivoCierre?: MotivoCierre | null;
  notaCierre?: string | null;
  /** La etapa que tenía al cerrarse (a la que vuelve si se reabre). */
  etapaAntesCierre?: string | null;
}

// ---------- relevo del abogado a cargo y cierre del proceso ----------
export type MotivoRelevo =
  | 'RENUNCIA' | 'REVOCATORIA' | 'SUSTITUCION' | 'SUCESION' | 'RETIRO_DE_LA_FIRMA' | 'REORGANIZACION';

export const MOTIVOS_RELEVO: { valor: MotivoRelevo; etiqueta: string; ayuda: string }[] = [
  { valor: 'RENUNCIA', etiqueta: 'Renuncia al poder', ayuda: 'El abogado renuncia al poder (CGP art. 76).' },
  { valor: 'REVOCATORIA', etiqueta: 'Revocatoria del poder', ayuda: 'El cliente le revoca el poder al abogado.' },
  { valor: 'SUSTITUCION', etiqueta: 'Sustitución del poder', ayuda: 'El abogado sustituye el poder en otro (CGP art. 75).' },
  { valor: 'SUCESION', etiqueta: 'Sucesión', ayuda: 'Otro abogado continúa porque el anterior no puede seguir (licencia, incapacidad, sanción).' },
  { valor: 'RETIRO_DE_LA_FIRMA', etiqueta: 'Retiro de la firma', ayuda: 'El abogado deja de trabajar en la firma.' },
  { valor: 'REORGANIZACION', etiqueta: 'Reorganización del trabajo', ayuda: 'La firma redistribuye la carga entre sus abogados.' },
];

export interface RelevoSolicitud {
  idResponsable: number | null;
  motivo: MotivoRelevo | null;
  nota?: string;
  anteriorQuedaDeApoyo: boolean;
  trasladarPendientes: boolean;
}
export interface ResultadoRelevo { idAnterior?: number | null; idNuevo: number; terminosTrasladados: number; audienciasTrasladadas: number; }

/** Un periodo de una persona en el equipo del proceso. hasta vacío = sigue. */
export interface PeriodoEquipo {
  idCambio: number; idPersona: number; rol: string; desde: string; hasta?: string | null; motivo?: string | null;
  nota?: string | null; registradoPor?: number | null; cerradoPor?: number | null; nombre?: string | null;
}

export type MotivoCierre =
  | 'SENTENCIA' | 'SENTENCIA_ANTICIPADA' | 'CONCILIACION' | 'TRANSACCION' | 'DESISTIMIENTO' | 'RETIRO_DE_LA_DEMANDA'
  | 'PAGO' | 'LIQUIDACION' | 'RENUNCIA_AL_PODER' | 'ARCHIVO' | 'OTRO';

export const MOTIVOS_CIERRE: { valor: MotivoCierre; etiqueta: string; ayuda: string }[] = [
  { valor: 'SENTENCIA', etiqueta: 'Sentencia en firme', ayuda: 'El proceso terminó con sentencia ejecutoriada.' },
  { valor: 'SENTENCIA_ANTICIPADA', etiqueta: 'Sentencia anticipada', ayuda: 'El juez falló sin agotar las etapas (CGP art. 278).' },
  { valor: 'CONCILIACION', etiqueta: 'Conciliación', ayuda: 'Las partes conciliaron y el juez la aprobó.' },
  { valor: 'TRANSACCION', etiqueta: 'Transacción', ayuda: 'Las partes transigieron el litigio (CGP art. 312).' },
  { valor: 'DESISTIMIENTO', etiqueta: 'Desistimiento', ayuda: 'El demandante desistió de las pretensiones (CGP art. 314).' },
  { valor: 'RETIRO_DE_LA_DEMANDA', etiqueta: 'Retiro de la demanda', ayuda: 'Se retiró la demanda antes de notificarla (CGP art. 92).' },
  { valor: 'PAGO', etiqueta: 'Pago total', ayuda: 'Se pagó la obligación (procesos ejecutivos).' },
  { valor: 'LIQUIDACION', etiqueta: 'Liquidación terminada', ayuda: 'Terminó la liquidación del crédito, de la sociedad o de la sucesión.' },
  { valor: 'RENUNCIA_AL_PODER', etiqueta: 'La firma deja el caso', ayuda: 'La firma renunció al poder o el cliente cambió de abogado.' },
  { valor: 'ARCHIVO', etiqueta: 'Archivo', ayuda: 'Se archivó sin decisión de fondo (por ejemplo, desistimiento tácito).' },
  { valor: 'OTRO', etiqueta: 'Otro motivo', ayuda: 'Explíquelo en la nota.' },
];

export interface EfectosCierre { terminosPendientes: number; audienciasFuturas: number; seApagoLaVigilancia: boolean; }
export interface ResultadoCierre { proceso: Proceso; efectos: EfectosCierre; }

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
  /** La norma que fija el plazo, si se indicó. */
  norma?: string;
  tipoProceso?: string;
  etapa?: string;
  /** La decisión del juez que abrió el término (su clase), si se creó desde una providencia. */
  providencia?: string;
}

export interface TerminoSolicitud {
  descripcion: string;
  fechaInicio: string;
  diasHabiles: number | null;
  idProvidencia?: number | null;
  idResponsable?: number | null;
  norma?: string | null;
}

/** Un plazo de referencia del tipo de proceso (GET /procesos/:id/plazos). */
export interface PlazoProceso {
  descripcion: string;
  diasHabiles: number;
  norma: string;
  /** Cuándo empieza a correr, en palabras. */
  momento?: string;
  tipoProvidencia: 'AUTO' | 'SENTENCIA';
  /** «Si procede»: recursos y similares. */
  opcional: boolean;
  /** Lo revisó un socio; si no, es de referencia. */
  validada: boolean;
  /** La regla es de este tipo de proceso, no heredada de la rama. */
  propio: boolean;
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

/**
 * Días del calendario entre la notificación y el vencimiento que no contaron: fines de semana, festivos,
 * Semana Santa y vacancia judicial.
 */
export function diasNoContados(t: { fechaInicio: string; fechaVencimiento: string; diasHabiles: number }): number {
  const corridos = Math.round((Date.parse(t.fechaVencimiento) - Date.parse(t.fechaInicio)) / 86_400_000);
  return Math.max(0, corridos - t.diasHabiles);
}

/** Parte del plazo ya consumida, de 0 a 100 (100 = vencido o cumplido). */
export function avancePlazo(t: { estado: string; diasHabiles: number; diasHabilesRestantes: number }): number {
  if (t.estado === 'CUMPLIDO' || t.diasHabiles <= 0) return 100;
  const usados = t.diasHabiles - Math.max(0, t.diasHabilesRestantes);
  return Math.min(100, Math.max(0, Math.round((usados / t.diasHabiles) * 100)));
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
  /** Sentencia anticipada (CGP art. 278). */
  anticipada?: boolean;
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
  anticipada?: boolean;
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
  /** Cuándo empieza a correr, en palabras (solo las que salen de una regla). */
  momento?: string;
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

// ---------- equipo del proceso (F11) ----------
export type RolEquipo = 'RESPONSABLE' | 'APOYO' | 'DEPENDIENTE';
export interface Integrante { idProceso: number; idPersona: number; rol: RolEquipo; desde: string; nombre: string | null; }

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

// ------------------------------------------------------------------ F12: estado del servidor

export interface PuntoServidor {
  momento: string; disco: number; memoriaMb: number; appOk: boolean; problemas: number; revisiones: number; sinApp: number;
}

export interface IncidenteServidor {
  clave: string; mensaje: string; inicio: string; fin?: string | null; abierto: boolean; minutos: number;
}

export interface EstadoServidor {
  dias: number;
  ultima: { momento: string; disco: number; memoriaMb: number; respaldoHoras?: number | null; appOk: boolean;
            problemas: number; detalle: string[] } | null;
  vigiaActivo: boolean; revisiones: number; disponibilidad: number | null;
  serie: PuntoServidor[]; incidentes: IncidenteServidor[];
  /** F15: errores de los registros de cada servicio en el periodo. */
  errores?: { porServicio: { servicio: string; total: number; ultimo: string }[];
              recientes: { momento: string; servicio: string; linea: string }[] };
}

// ---------- bitácora de auditoría encadenada (solo ADMIN; docs/14-auditoria.md) ----------
export interface AccesoAuditoria {
  momento: string;
  /** Documento de quien hizo la petición, o «anonimo» (sin sesión o con un token inválido). */
  usuario: string;
  roles: string;
  metodo: string;
  ruta: string;
  /** Código HTTP; null si la petición se cortó antes de responder. */
  estado: number | null;
  duracionMs: number;
  ip: string | null;
}
export interface RegistroAuditoria { id: number; acceso: AccesoAuditoria; hashAnterior: string; hash: string; }
export interface PaginaAuditoria { registros: RegistroAuditoria[]; siguiente: number | null; }
export interface FiltroAuditoria {
  usuario?: string | null; metodo?: string | null; ruta?: string | null; fallos?: boolean;
  desde?: string | null; hasta?: string | null; antesDe?: number | null; limite?: number;
}
export interface RevisionAuditoria {
  cadena: {
    integra: boolean; revisados: number; ultimoId: number; ultimoHash: string;
    roturaEn: number | null; motivo: string | null;
  };
  /** Cómo va la escritura en el gateway: accesos esperando a guardarse y perdidos (espera llena). */
  escritura: { almacen: 'postgres' | 'memoria'; pendientes: number; perdidos: number };
  momento: string;
}
