import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Honorarios pactados y pagos llegan en la fase F4 (servicio gabinete). */
@Component({
  selector: 'app-pagos',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Honorarios y pagos</h1>
        <p>Acuerdos de honorarios por proceso y pagos de los clientes</p>
      </div>
      <div class="card">
        <div class="card-body">
          <p>Este módulo se construye en la <strong>fase F4</strong>. La base de datos ya tiene las tablas
            <code>honorarios_pactados</code> y <code>pagos</code> con datos de prueba.</p>
          <a class="btn btn-secondary" routerLink="/procesos">Ir a procesos</a>
        </div>
      </div>
    </div>
  `
})
export class PagosComponent {}
