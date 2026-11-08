/**
 * Datos del responsable del tratamiento (Ley 1581 de 2012, art. 17 y Decreto 1377 de 2013, art. 13) y versión de
 * los textos legales. Se completan aquí y en ningún otro lado: la política de privacidad, la de cookies y el aviso
 * los toman de este archivo. Lo que dice [COMPLETAR] lo debe llenar la firma.
 *
 * revisada: false muestra en las páginas que el texto es un borrador pendiente de revisión por un abogado.
 */
export const DATOS_LEGALES = {
  responsable: 'Rodríguez & Asociados',
  nit: '[COMPLETAR: NIT]',
  direccion: '[COMPLETAR: dirección de la sede principal]',
  ciudad: 'Bogotá D. C., Colombia',
  correoDatos: '[COMPLETAR: correo para consultas y reclamos de datos personales]',
  telefono: '[COMPLETAR: teléfono]',
  producto: 'Lexora',
  version: '1.1',            // 1.1: bitácora de auditoría de cada acción
  vigenteDesde: '2026-11-08',
  revisada: false
} as const;

/** Clave del navegador con la versión del aviso de cookies que la persona ya leyó. */
export const CLAVE_AVISO_COOKIES = 'lexora.avisoCookies';

/** Lo que la app guarda en el navegador: lo lista la política de cookies (si se agrega algo, va aquí). */
export const ALMACENAMIENTO = [
  { nombre: 'gj_refresco', tipo: 'Cookie propia (HttpOnly, Secure, SameSite=Strict)', finalidad:
      'Mantiene la sesión abierta y renueva el acceso de forma segura. La página no puede leerla.',
    duracion: '12 horas desde el último uso (máximo 7 días); se borra al cerrar sesión', necesaria: true },
  { nombre: 'gj_oauth', tipo: 'Cookie propia (HttpOnly, Secure, SameSite=Lax)', finalidad:
      'Solo al entrar o enlazar con Google, Microsoft o GitHub: comprueba que la vuelta del proveedor es de este mismo navegador.',
    duracion: '10 minutos; se borra al volver', necesaria: true },
  { nombre: 'gj.sesion', tipo: 'Almacenamiento de la pestaña (sessionStorage)', finalidad:
      'El permiso de acceso de corta duración de esta pestaña.', duracion: 'Hasta cerrar la pestaña o la sesión', necesaria: true },
  { nombre: 'lexora.tema', tipo: 'Almacenamiento local (localStorage)', finalidad: 'Recuerda si eligió el tema claro u oscuro.',
    duracion: 'Hasta que lo cambie o borre los datos del navegador', necesaria: false },
  { nombre: 'lexora.menu.colapsado · lexora.menu.grupos', tipo: 'Almacenamiento local (localStorage)', finalidad:
      'Recuerda si el menú está contraído y qué grupos dejó abiertos.', duracion: 'Hasta que lo cambie o borre los datos del navegador',
    necesaria: false },
  { nombre: 'lexora.avisoCookies', tipo: 'Almacenamiento local (localStorage)', finalidad:
      'Recuerda que ya leyó este aviso, para no mostrarlo en cada visita.', duracion: 'Hasta que cambie el aviso o borre los datos del navegador',
    necesaria: false }
] as const;
