import { Routes } from '@angular/router';
import { LayoutComponent } from './layout/layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ProcesosComponent } from './pages/procesos/procesos.component';
import { ProcesoFormComponent } from './pages/procesos/proceso-form.component';
import { ClientesComponent } from './pages/clientes/clientes.component';
import { AbogadosComponent } from './pages/abogados/abogados.component';
import { HonorariosComponent } from './pages/honorarios/honorarios.component';
import { PeritosComponent } from './pages/peritos/peritos.component';
import { TerminosComponent } from './pages/terminos/terminos.component';
import { CalendarioComponent } from './pages/calendario/calendario.component';
import { AvisosComponent } from './pages/avisos/avisos.component';
import { VigilanciaComponent } from './pages/vigilancia/vigilancia.component';

export const routes: Routes = [
  {
    path: '',
    component: LayoutComponent,
    children: [
      { path: '', component: DashboardComponent, title: 'Panel' },
      { path: 'procesos', component: ProcesosComponent, title: 'Procesos' },
      { path: 'procesos/nuevo', component: ProcesoFormComponent, title: 'Nuevo proceso' },
      { path: 'procesos/:id/editar', component: ProcesoFormComponent, title: 'Editar proceso' },
      { path: 'clientes', component: ClientesComponent, title: 'Clientes' },
      { path: 'abogados', component: AbogadosComponent, title: 'Abogados' },
      { path: 'terminos', component: TerminosComponent, title: 'Términos' },
      { path: 'calendario', component: CalendarioComponent, title: 'Calendario judicial' },
      { path: 'vigilancia', component: VigilanciaComponent, title: 'Vigilancia' },
      { path: 'avisos', component: AvisosComponent, title: 'Avisos' },
      { path: 'honorarios', component: HonorariosComponent, title: 'Honorarios y pagos' },
      { path: 'pagos', redirectTo: 'honorarios' },
      { path: 'peritos', component: PeritosComponent, title: 'Peritos' },
      { path: '**', redirectTo: '' }
    ]
  }
];
