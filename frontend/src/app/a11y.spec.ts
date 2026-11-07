import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Type } from '@angular/core';
import axe from 'axe-core';

import { LoginComponent } from './pages/login/login.component';
import { CalendarioComponent } from './pages/calendario/calendario.component';
import { AsistenteProcesoComponent } from './pages/procesos/asistente-proceso.component';
import { ProcesosComponent } from './pages/procesos/procesos.component';
import { TerminosComponent } from './pages/terminos/terminos.component';
import { ClientesComponent } from './pages/clientes/clientes.component';
import { FacturacionComponent } from './pages/facturacion/facturacion.component';
import { DashboardComponent } from './dashboard/dashboard.component';

// F17: datos de ejemplo para que las listas se revisen con filas (tablas, enlaces, insignias), no vacías.
const PROCESO = {
  idProceso: 2, radicado: '11001310303220240045800', radicadoLegible: '11001-31-03-032-2024-00458-00', idTipo: 1,
  tipoProceso: 'Verbal', codigoRama: 'CIV', rama: 'Civil y comercial', despachoOrigen: 'Juzgado 032 Civil del Circuito',
  despachoActual: 'Juzgado 032 Civil del Circuito', instancia: 1, idCliente: 2, rolCliente: 'DEMANDADO',
  asunto: 'Responsabilidad contractual por defectos de obra', claseCuantia: 'MAYOR', tipoServicio: 'LITIGIO',
  estado: 'ACTIVO', etapa: 'PRUEBAS', fechaRadicacion: '2024-03-11', vigilado: true, idResponsable: 79123456
};
const TERMINOS = ['VENCIDO', 'POR_VENCER', 'AL_DIA', 'CUMPLIDO'].map((situacion, i) => ({
  idTermino: i + 1, idProceso: 2, radicado: PROCESO.radicado, radicadoLegible: PROCESO.radicadoLegible, rama: PROCESO.rama,
  asunto: PROCESO.asunto, descripcion: 'Presentar alegatos', fechaInicio: '2026-10-01', diasHabiles: 5,
  fechaVencimiento: '2026-11-0' + (i + 2), estado: situacion === 'CUMPLIDO' ? 'CUMPLIDO' : 'PENDIENTE',
  idResponsable: 79123456, diasHabilesRestantes: 2 - i, situacion
}));
const CLIENTE = {
  idCliente: 2, tipoPersona: 'JURIDICA', tipoDocumento: 'NIT', numeroDocumento: '900123456',
  nombre: 'Constructora Altamira SAS', email: 'contacto@altamira.test', telefono: '6015550000', ciudad: 'Bogotá',
  idSede: 1, creado: '2025-01-15T10:00:00'
};
const FACTURA = {
  idFactura: 1, prefijo: 'SETP', numero: 990000001, resolucion: '18760000001', ambiente: 'PRUEBAS', idCliente: 2,
  emitida: '2026-10-30T09:00:00', vence: '2026-11-29', formaPago: 'CREDITO', estado: 'ACEPTADA', emitidaPor: 79123456,
  totales: { subtotal: 1000000, iva: 190000, reembolsos: 0, total: 1190000, retencionPct: 11, retencion: 110000, neto: 1080000 },
  adquirente: { nombre: CLIENTE.nombre, tipoDocumento: 'NIT', numeroDocumento: '900123456', direccion: 'Cra 7 # 1-1',
                ciudad: 'Bogotá', email: CLIENTE.email },
  lineas: []
};
let filasRevisadas = 0;   // filas de tabla que tenía la última pantalla revisada

function datosDeEjemplo(url: string): object {
  if (url.endsWith('/api/procesos')) return [PROCESO];
  if (url.endsWith('/api/terminos')) return TERMINOS;
  if (url.endsWith('/api/clientes')) return [CLIENTE];
  if (url.endsWith('/api/facturas')) return [FACTURA];
  if (url.endsWith('/api/abogados')) return [{ idPersona: 79123456, nombres: 'Andrés Felipe', apellidos: 'Rodríguez Mejía' }];
  return [];
}

/**
 * F16: accesibilidad automática con axe-core, el motor de Axe DevTools (Deque). Falla si una pantalla tiene
 * violaciones graves o críticas de WCAG 2.1 A/AA. El contraste no se puede medir en jsdom (no pinta): ese se
 * revisa con la auditoría en el navegador (docs/10-accesibilidad.md).
 */
async function violaciones<T>(componente: Type<T>, entradas: Record<string, unknown> = {}, conDatos = false) {
  await TestBed.configureTestingModule({
    imports: [componente],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();
  const f = TestBed.createComponent(componente);
  for (const [k, v] of Object.entries(entradas)) f.componentRef.setInput(k, v);
  f.detectChanges();
  if (conDatos) {
    // Responde las consultas de la pantalla (y las que esas disparen) con los datos de ejemplo.
    const http = TestBed.inject(HttpTestingController);
    for (let ronda = 0; ronda < 3; ronda++) {
      const pendientes = http.match(() => true);
      if (pendientes.length === 0) break;
      for (const r of pendientes) if (!r.cancelled) r.flush(datosDeEjemplo(r.request.url));
      f.detectChanges();
    }
    await f.whenStable();
    f.detectChanges();
  }
  document.body.appendChild(f.nativeElement);
  const r = await axe.run(f.nativeElement, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } }
  });
  filasRevisadas = f.nativeElement.querySelectorAll('table tbody tr').length;
  f.nativeElement.remove();
  return r.violations
    .filter(v => v.impact === 'serious' || v.impact === 'critical')
    .map(v => `${v.id}: ${v.help} (${v.nodes.length})`);
}

describe('Accesibilidad (axe-core)', () => {
  it('detecta una violación conocida (la prueba no da verde en falso)', async () => {
    const malo = document.createElement('div');
    malo.innerHTML = '<input type="text"><img src="x.png">';
    document.body.appendChild(malo);
    const r = await axe.run(malo, { runOnly: { type: 'tag', values: ['wcag2a'] } });
    malo.remove();
    expect(r.violations.map(v => v.id)).toEqual(expect.arrayContaining(['label', 'image-alt']));
  });

  it('el inicio de sesión no tiene violaciones graves', async () => {
    expect(await violaciones(LoginComponent)).toEqual([]);
  });

  it('el asistente de borradores no tiene violaciones graves', async () => {
    expect(await violaciones(AsistenteProcesoComponent, { idProceso: 1, radicado: '11001-31-03-032-2024-00458-00' }))
      .toEqual([]);
  });

  it('el calendario no tiene violaciones graves (F16: antes role=grid sin filas)', async () => {
    expect(await violaciones(CalendarioComponent)).toEqual([]);
  });

  it('el inicio «Mi día» no tiene violaciones graves (diseño Lexora)', async () => {
    expect(await violaciones(DashboardComponent, {}, true)).toEqual([]);
  });

  // F17: las pantallas de trabajo diario, con datos.
  it.each([
    ['procesos', ProcesosComponent, 1],
    ['términos', TerminosComponent, 4],
    ['clientes', ClientesComponent, 1],
    ['facturación', FacturacionComponent, 1]
  ] as [string, Type<unknown>, number][])('la lista de %s no tiene violaciones graves', async (_n, componente, filas) => {
    expect(await violaciones(componente, {}, true)).toEqual([]);
    expect(filasRevisadas).toBeGreaterThanOrEqual(filas);   // se revisó la tabla con datos, no el estado vacío
  });
});
