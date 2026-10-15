import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../core/api.service';
import { Proceso, etiqueta } from '../core/modelos';

interface ConteoRama { rama: string; activos: number; total: number; }

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  readonly etiqueta = etiqueta;

  cargando = true;
  error = '';

  totalProcesos = 0;
  activos = 0;
  suspendidos = 0;
  cerrados = 0;
  totalClientes = 0;
  abogadosActivos = 0;
  porRama: ConteoRama[] = [];
  ultimos: Proceso[] = [];

  ngOnInit(): void {
    forkJoin({
      procesos: this.api.procesos(),
      clientes: this.api.clientes(),
      abogados: this.api.abogados(true)
    }).subscribe({
      next: ({ procesos, clientes, abogados }) => {
        this.totalProcesos = procesos.length;
        this.activos = procesos.filter(p => p.estado === 'ACTIVO').length;
        this.suspendidos = procesos.filter(p => p.estado === 'SUSPENDIDO').length;
        this.cerrados = procesos.filter(p => p.estado === 'TERMINADO' || p.estado === 'ARCHIVADO').length;
        this.totalClientes = clientes.length;
        this.abogadosActivos = abogados.length;
        this.porRama = this.agruparPorRama(procesos);
        this.ultimos = procesos.slice(0, 6);   // el servicio los devuelve del más reciente al más antiguo
        this.cargando = false;
      },
      error: e => { this.error = mensajeDeError(e, 'No se pudo conectar con el servidor.'); this.cargando = false; }
    });
  }

  private agruparPorRama(procesos: Proceso[]): ConteoRama[] {
    const mapa = new Map<string, ConteoRama>();
    for (const p of procesos) {
      const c = mapa.get(p.rama) ?? { rama: p.rama, activos: 0, total: 0 };
      c.total++;
      if (p.estado === 'ACTIVO') c.activos++;
      mapa.set(p.rama, c);
    }
    return [...mapa.values()].sort((a, b) => b.total - a.total);
  }

  ancho(c: ConteoRama): number {
    const max = this.porRama[0]?.total || 1;
    return Math.round((c.total / max) * 100);
  }
}
