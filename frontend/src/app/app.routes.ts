import { Routes } from '@angular/router';
import { LayoutComponent } from './layout/layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ProcesosComponent } from './pages/procesos/procesos.component';
import { ProcesoFormComponent } from './pages/procesos/proceso-form.component';
import { ExpedienteComponent } from './pages/procesos/expediente.component';
import { ClientesComponent } from './pages/clientes/clientes.component';
import { AbogadosComponent } from './pages/abogados/abogados.component';
import { HonorariosComponent } from './pages/honorarios/honorarios.component';
import { PeritosComponent } from './pages/peritos/peritos.component';
import { TerminosComponent } from './pages/terminos/terminos.component';
import { CalendarioComponent } from './pages/calendario/calendario.component';
import { AvisosComponent } from './pages/avisos/avisos.component';
import { VigilanciaComponent } from './pages/vigilancia/vigilancia.component';
import { LoginComponent } from './pages/login/login.component';
import { CuentaComponent } from './pages/cuenta/cuenta.component';
import { claveAlDia, conRol, conSesion } from './core/auth.guard';
import { UsuariosComponent } from './pages/usuarios/usuarios.component';
import { ReportesComponent } from './pages/reportes/reportes.component';
import { DIRECCION, INTERNOS, JURIDICO, JURIDICO_Y_DEPENDIENTES, VEN_HONORARIOS } from './core/auth.service';

const personal = conRol(...INTERNOS);
const juridico = conRol(...JURIDICO);
const cliente = conRol('CLIENTE');

export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'Iniciar sesión' },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [conSesion],
    canActivateChild: [claveAlDia],
    children: [
      { path: 'cuenta', component: CuentaComponent, title: 'Mi cuenta' },
      { path: '', component: DashboardComponent, title: 'Panel', canActivate: [personal] },
      { path: 'procesos', component: ProcesosComponent, title: 'Procesos', canActivate: [personal] },
      { path: 'procesos/nuevo', component: ProcesoFormComponent, title: 'Nuevo proceso', canActivate: [juridico] },
      { path: 'procesos/:id', component: ExpedienteComponent, title: 'Expediente', canActivate: [personal] },
      { path: 'procesos/:id/editar', component: ProcesoFormComponent, title: 'Editar proceso', canActivate: [juridico] },
      { path: 'clientes', component: ClientesComponent, title: 'Clientes', canActivate: [personal] },
      { path: 'abogados', component: AbogadosComponent, title: 'Abogados', canActivate: [personal] },
      { path: 'terminos', component: TerminosComponent, title: 'Términos', canActivate: [personal] },
      { path: 'calendario', component: CalendarioComponent, title: 'Calendario judicial', canActivate: [personal] },
      { path: 'vigilancia', component: VigilanciaComponent, title: 'Vigilancia', canActivate: [conRol(...JURIDICO_Y_DEPENDIENTES)] },
      { path: 'avisos', component: AvisosComponent, title: 'Avisos' },
      // Portal del cliente (F9): sus procesos, novedades y pagos. Se cargan aparte (el personal no los descarga).
      { path: 'portal', loadComponent: () => import('./pages/portal/portal-procesos.component').then(m => m.PortalProcesosComponent), title: 'Mis procesos', canActivate: [cliente] },
      { path: 'portal/procesos/:id', loadComponent: () => import('./pages/portal/portal-proceso.component').then(m => m.PortalProcesoComponent), title: 'Mi proceso', canActivate: [cliente] },
      { path: 'portal/pagos', loadComponent: () => import('./pages/portal/portal-pagos.component').then(m => m.PortalPagosComponent), title: 'Mis pagos', canActivate: [cliente] },
      { path: 'honorarios', component: HonorariosComponent, title: 'Honorarios y pagos', canActivate: [conRol(...VEN_HONORARIOS)] },
      { path: 'pagos', redirectTo: 'honorarios' },
      { path: 'usuarios', component: UsuariosComponent, title: 'Usuarios', canActivate: [conRol('ADMIN')] },
      { path: 'reportes', component: ReportesComponent, title: 'Reportes', canActivate: [conRol(...DIRECCION)] },
      { path: 'peritos', component: PeritosComponent, title: 'Peritos', canActivate: [personal] },
      { path: '**', redirectTo: '' }
    ]
  }
];
