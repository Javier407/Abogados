import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { claveAlDia, conRol, conSesion } from './core/auth.guard';
import { DIRECCION, INTERNOS, JURIDICO, JURIDICO_Y_DEPENDIENTES, VEN_HONORARIOS } from './core/auth.service';

const personal = conRol(...INTERNOS);
const juridico = conRol(...JURIDICO);
const cliente = conRol('CLIENTE');

export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'Iniciar sesión' },
  {
    path: '',
    // El marco (menú, barra y sus componentes de spartan) se descarga aparte: el login carga más liviano.
    loadComponent: () => import('./layout/layout.component').then(m => m.LayoutComponent),
    canActivate: [conSesion],
    canActivateChild: [claveAlDia],
    children: [
      { path: 'cuenta', loadComponent: () => import('./pages/cuenta/cuenta.component').then(m => m.CuentaComponent), title: 'Mi cuenta' },
      { path: '', loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent), title: 'Panel', canActivate: [personal] },
      { path: 'procesos', loadComponent: () => import('./pages/procesos/procesos.component').then(m => m.ProcesosComponent), title: 'Procesos', canActivate: [personal] },
      { path: 'procesos/nuevo', loadComponent: () => import('./pages/procesos/proceso-form.component').then(m => m.ProcesoFormComponent), title: 'Nuevo proceso', canActivate: [juridico] },
      { path: 'procesos/:id', loadComponent: () => import('./pages/procesos/expediente.component').then(m => m.ExpedienteComponent), title: 'Expediente', canActivate: [personal] },
      { path: 'procesos/:id/editar', loadComponent: () => import('./pages/procesos/proceso-form.component').then(m => m.ProcesoFormComponent), title: 'Editar proceso', canActivate: [juridico] },
      { path: 'clientes', loadComponent: () => import('./pages/clientes/clientes.component').then(m => m.ClientesComponent), title: 'Clientes', canActivate: [personal] },
      { path: 'consultas', loadComponent: () => import('./pages/consultas/consultas.component').then(m => m.ConsultasComponent),
        title: 'Consultas iniciales', canActivate: [personal] },
      { path: 'abogados', loadComponent: () => import('./pages/abogados/abogados.component').then(m => m.AbogadosComponent), title: 'Abogados', canActivate: [personal] },
      { path: 'terminos', loadComponent: () => import('./pages/terminos/terminos.component').then(m => m.TerminosComponent), title: 'Términos', canActivate: [personal] },
      { path: 'calendario', loadComponent: () => import('./pages/calendario/calendario.component').then(m => m.CalendarioComponent), title: 'Calendario judicial', canActivate: [personal] },
      { path: 'vigilancia', loadComponent: () => import('./pages/vigilancia/vigilancia.component').then(m => m.VigilanciaComponent), title: 'Vigilancia', canActivate: [conRol(...JURIDICO_Y_DEPENDIENTES)] },
      { path: 'avisos', loadComponent: () => import('./pages/avisos/avisos.component').then(m => m.AvisosComponent), title: 'Avisos' },
      // Portal del cliente (F9): sus procesos, novedades y pagos. Se cargan aparte (el personal no los descarga).
      { path: 'portal', loadComponent: () => import('./pages/portal/portal-procesos.component').then(m => m.PortalProcesosComponent), title: 'Mis procesos', canActivate: [cliente] },
      { path: 'portal/procesos/:id', loadComponent: () => import('./pages/portal/portal-proceso.component').then(m => m.PortalProcesoComponent), title: 'Mi proceso', canActivate: [cliente] },
      { path: 'portal/pagos', loadComponent: () => import('./pages/portal/portal-pagos.component').then(m => m.PortalPagosComponent), title: 'Mis pagos', canActivate: [cliente] },
      { path: 'honorarios', loadComponent: () => import('./pages/honorarios/honorarios.component').then(m => m.HonorariosComponent), title: 'Honorarios y pagos', canActivate: [conRol(...VEN_HONORARIOS)] },
      { path: 'pagos', redirectTo: 'honorarios' },
      { path: 'facturacion', loadComponent: () => import('./pages/facturacion/facturacion.component').then(m => m.FacturacionComponent),
        title: 'Facturación', canActivate: [conRol(...VEN_HONORARIOS)] },
      { path: 'facturas/:id', loadComponent: () => import('./pages/facturacion/factura.component').then(m => m.FacturaComponent),
        title: 'Factura', canActivate: [conRol(...VEN_HONORARIOS)] },
      { path: 'usuarios', loadComponent: () => import('./pages/usuarios/usuarios.component').then(m => m.UsuariosComponent), title: 'Usuarios', canActivate: [conRol('ADMIN')] },
      { path: 'reportes', loadComponent: () => import('./pages/reportes/reportes.component').then(m => m.ReportesComponent), title: 'Reportes', canActivate: [conRol(...DIRECCION)] },
      { path: 'firma', loadComponent: () => import('./pages/firma/firma.component').then(m => m.FirmaComponent),
        title: 'La firma', canActivate: [conRol(...DIRECCION)] },
      { path: 'reglas', loadComponent: () => import('./pages/reglas/reglas.component').then(m => m.ReglasComponent),
        title: 'Reglas de términos', canActivate: [personal] },
      { path: 'servidor', loadComponent: () => import('./pages/servidor/servidor.component').then(m => m.ServidorComponent),
        title: 'Servidor', canActivate: [conRol('ADMIN')] },
      { path: 'peritos', loadComponent: () => import('./pages/peritos/peritos.component').then(m => m.PeritosComponent), title: 'Peritos', canActivate: [personal] },
      { path: '**', redirectTo: '' }
    ]
  }
];
