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
  dia: 'día'
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
  referencia: { id_proceso?: number; id_termino?: number };
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
