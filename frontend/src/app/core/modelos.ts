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
  representacion: 'representación', cuantia: 'cuantía', victima: 'víctima', juridica: 'jurídica', minima: 'mínima'
};

/** Texto legible para valores en MAYÚSCULAS_CON_GUION: 'SIN_CUANTIA' -> 'Sin cuantía'. */
export function etiqueta(valor: string | null | undefined): string {
  if (!valor) return '';
  const t = valor.toLowerCase().split('_').map(p => TILDES[p] ?? p).join(' ');
  return t.charAt(0).toUpperCase() + t.slice(1);
}
