import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { ESTADOS, Proceso, Rama, etiqueta } from '../../core/modelos';

@Component({
  selector: 'app-procesos',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './procesos.component.html'
})
export class ProcesosComponent implements OnInit {
  private api = inject(ApiService);
  readonly estados = ESTADOS;
  readonly etiqueta = etiqueta;

  procesos: Proceso[] = [];
  ramas: Rama[] = [];
  filtro = { estado: '', rama: '', q: '' };
  private clientes = new Map<number, string>();
  private abogados = new Map<number, string>();

  cargando = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    // Nombres de clientes y abogados: viven en el servicio gabinete, el proceso solo guarda los id.
    forkJoin({ ramas: this.api.ramas(), clientes: this.api.clientes(), abogados: this.api.abogados() }).subscribe({
      next: r => {
        this.ramas = r.ramas;
        r.clientes.forEach(c => this.clientes.set(c.idCliente!, c.nombre));
        r.abogados.forEach(a => this.abogados.set(a.idPersona, `${a.nombres} ${a.apellidos}`));
      },
      error: e => this.error = mensajeDeError(e)
    });
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.api.procesos(this.filtro).subscribe({
      next: p => { this.procesos = p; this.cargando = false; },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  limpiar(): void {
    this.filtro = { estado: '', rama: '', q: '' };
    this.cargar();
  }

  eliminar(p: Proceso): void {
    if (!confirm(`¿Eliminar el proceso ${p.radicadoLegible}?`)) return;
    this.mensaje = this.error = '';
    this.api.eliminarProceso(p.idProceso).subscribe({
      next: () => { this.mensaje = `Proceso ${p.radicadoLegible} eliminado.`; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  contar(estado: string): number {
    return this.procesos.filter(p => p.estado === estado).length;
  }

  cliente(id: number): string { return this.clientes.get(id) ?? `Cliente ${id}`; }
  abogado(id?: number): string { return id ? (this.abogados.get(id) ?? String(id)) : '—'; }
}
