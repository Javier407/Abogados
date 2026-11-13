/**
 * Convierte la respuesta en texto del asistente (la misma que va por Telegram) en bloques para el portal: párrafos,
 * tablas y listas, con lo importante resaltado (radicados, fechas, vencidos, lo que vence hoy o pronto).
 *
 * El servidor responde texto plano con un formato estable (consultas_bot.py): una línea de título, viñetas «• …» y
 * líneas de detalle con dos espacios. Aquí no se interpreta HTML: todo se pinta como texto.
 */

export type TipoSegmento = 'texto' | 'radicado' | 'fecha' | 'vencido' | 'hoy' | 'pronto' | 'ok' | 'comando';
export interface Segmento { texto: string; tipo: TipoSegmento; }
export type Urgencia = 'alta' | 'media' | 'normal';

export interface Parrafo { tipo: 'parrafo'; segmentos: Segmento[]; titulo: boolean; }
export interface Tabla {
  tipo: 'tabla';
  columnas: string[];
  filas: { celdas: Segmento[][]; urgencia: Urgencia }[];
}
export interface Lista { tipo: 'lista'; items: { segmentos: Segmento[]; urgencia: Urgencia }[]; }
export type Bloque = Parrafo | Tabla | Lista;

const RADICADO = /\d{5}-\d{2}-\d{2}-\d{3}-\d{4}-\d{5}-\d{2}/;
const FECHA = /\d{2}\/\d{2}\/\d{4}/;
const PIEZAS = new RegExp(
  [
    `(?<radicado>${RADICADO.source})`,
    '(?<vencido>VENCIDO\\(S\\)|VENCIDOS?|venció hace \\d+ días? hábil(?:es)?)',
    '(?<hoy>vence hoy)',
    '(?<falta>faltan? (?<dias>\\d+) días? hábil(?:es)?)',
    `(?<fecha>${FECHA.source}(?: a las \\d{2}:\\d{2})?)`,
    '(?<comando>(?<=^|\\s)\\/[a-z_]+)',
  ].join('|'),
  'g',
);

/** Parte un texto en segmentos con su tipo (para resaltarlos). */
export function segmentar(texto: string): Segmento[] {
  const segmentos: Segmento[] = [];
  let desde = 0;
  for (const m of texto.matchAll(PIEZAS)) {
    const g = m.groups ?? {};
    if (m.index! > desde) segmentos.push({ texto: texto.slice(desde, m.index), tipo: 'texto' });
    let tipo: TipoSegmento = 'texto';
    if (g['radicado']) tipo = 'radicado';
    else if (g['vencido']) tipo = 'vencido';
    else if (g['hoy']) tipo = 'hoy';
    else if (g['falta']) tipo = Number(g['dias']) <= 3 ? 'pronto' : 'ok';
    else if (g['fecha']) tipo = 'fecha';
    else if (g['comando']) tipo = 'comando';
    segmentos.push({ texto: m[0], tipo });
    desde = m.index! + m[0].length;
  }
  if (desde < texto.length) segmentos.push({ texto: texto.slice(desde), tipo: 'texto' });
  return segmentos;
}

export function urgenciaDe(...partes: Segmento[][]): Urgencia {
  const tipos = new Set(partes.flat().map((s) => s.tipo));
  if (tipos.has('vencido')) return 'alta';
  if (tipos.has('hoy') || tipos.has('pronto')) return 'media';
  return 'normal';
}

interface Item { titulo: string; detalle: string[]; }
type Clase = 'proceso' | 'termino' | 'audiencia' | 'plazo' | 'persona' | 'otro';

function clase(i: Item): Clase {
  if (RADICADO.test(i.titulo.slice(0, 29)) && i.titulo.includes(' — ')) return 'proceso';
  if (/^\d{2}\/\d{2}\/\d{4}/.test(i.titulo) && i.titulo.includes(' — ')) {
    if (i.detalle.length === 0) return /\)$/.test(i.titulo) ? 'plazo' : 'otro';
    return i.detalle[0].includes(' · ') ? 'termino' : 'audiencia';
  }
  if (/^.+? \(.+\) — .+$/.test(i.titulo) || /^[^—]+ — \d+ procesos?/.test(i.titulo)) return 'persona';
  return 'otro';
}

const COLUMNAS: Record<Exclude<Clase, 'otro'>, string[]> = {
  proceso: ['Proceso', 'Asunto', 'Tipo y etapa', 'Términos'],
  termino: ['Vence', 'Qué hay que hacer', 'Proceso', 'Estado'],
  audiencia: ['Cuándo', 'Audiencia', 'Proceso', 'Lugar'],
  plazo: ['Vence', 'Qué hay que hacer', 'Estado'],
  persona: ['Persona', 'Cargo y área', 'Procesos'],
};

const VACIO: Segmento[] = [{ texto: '—', tipo: 'texto' }];
const s = (t: string | undefined): Segmento[] => (t && t.trim() ? segmentar(t.trim()) : VACIO);

function celdas(c: Exclude<Clase, 'otro'>, i: Item): Segmento[][] {
  const [izq, ...der] = i.titulo.split(' — ');
  const resto = der.join(' — ');
  switch (c) {
    case 'proceso':
      return [s(izq), s(resto), s(i.detalle[0]), s(i.detalle.slice(1).join(' '))];
    case 'termino': {
      const [proceso, estado] = i.detalle[0].split(' · ');
      return [s(izq), s(resto), s(proceso), s(estado)];
    }
    case 'audiencia': {
      const [proceso, ...lugar] = i.detalle[0].split(' — ');
      return [s(izq), s(resto), s(proceso), s(lugar.join(' — '))];
    }
    case 'plazo': {
      const m = /^(.*) \(([^()]*)\)$/.exec(resto);
      return [s(izq), s(m ? m[1] : resto), s(m ? m[2] : '')];
    }
    case 'persona': {
      const m = /^(.+?) \((.+)\)$/.exec(izq);
      return [s(m ? m[1] : izq), s(m ? m[2] : ''), s(resto)];
    }
  }
}

/** Los bloques de una respuesta: párrafos, y las viñetas como tabla (si todas son del mismo tipo) o lista. */
export function bloquesDe(texto: string): Bloque[] {
  const bloques: Bloque[] = [];
  let items: Item[] = [];

  const cerrarItems = () => {
    if (items.length === 0) return;
    const clases = new Set(items.map(clase));
    const unica = clases.size === 1 ? [...clases][0] : 'otro';
    if (unica !== 'otro') {
      bloques.push({
        tipo: 'tabla',
        columnas: COLUMNAS[unica],
        filas: items.map((i) => {
          const fila = celdas(unica, i);
          return { celdas: fila, urgencia: urgenciaDe(...fila) };
        }),
      });
    } else {
      bloques.push({
        tipo: 'lista',
        items: items.map((i) => {
          const seg = segmentar([i.titulo, ...i.detalle].join(' · '));
          return { segmentos: seg, urgencia: urgenciaDe(seg) };
        }),
      });
    }
    items = [];
  };

  for (const linea of (texto ?? '').split('\n')) {
    if (linea.startsWith('• ')) {
      items.push({ titulo: linea.slice(2).trim(), detalle: [] });
    } else if (linea.startsWith('  ') && items.length > 0 && linea.trim()) {
      items[items.length - 1].detalle.push(linea.trim());
    } else if (linea.trim()) {
      cerrarItems();
      const t = linea.trim();
      bloques.push({ tipo: 'parrafo', segmentos: segmentar(t), titulo: t.endsWith(':') });
    } else {
      cerrarItems();
    }
  }
  cerrarItems();
  return bloques;
}

/** Solo los dígitos de un radicado (para volver a preguntar por ese proceso). */
export function digitos(radicado: string): string {
  return radicado.replace(/\D/g, '');
}
