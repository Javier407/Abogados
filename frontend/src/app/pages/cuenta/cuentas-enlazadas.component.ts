import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { mensajeDeError } from '../../core/api.service';
import {
  AuthService, CodigoProveedor, EnlaceExterno, MOTIVOS_EXTERNO, ProveedorExterno
} from '../../core/auth.service';
import { LogoProveedorComponent } from '../../ui/logo-proveedor.component';

/**
 * «Cuentas para entrar»: la persona enlaza su cuenta de Google, Microsoft o GitHub para entrar con ella, y puede
 * quitarla. Solo se ofrecen los proveedores configurados en el servidor. Al volver del proveedor, /cuenta trae
 * ?enlazado=GOOGLE o ?enlazado=error&motivo=...
 */
@Component({
  selector: 'app-cuentas-enlazadas',
  standalone: true,
  imports: [DatePipe, LogoProveedorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (proveedores().length) {
      <section class="card" aria-labelledby="titulo-enlazadas">
        <div class="card-header"><h2 id="titulo-enlazadas">Cuentas para entrar</h2></div>
        <div class="card-body">
          <p class="explicacion">Enlace una cuenta que ya use para entrar sin escribir la contraseña. Solo usted puede
            enlazarla, y la verificación en dos pasos se le seguirá pidiendo si la tiene activa.</p>
          @if (mensaje()) { <div class="alerta alerta-exito" role="status">{{ mensaje() }}</div> }
          @if (error()) { <div class="alerta alerta-error" role="alert">{{ error() }}</div> }
          <ul class="lista">
            @for (p of filas(); track p.codigo) {
              <li>
                <app-logo-proveedor [codigo]="p.codigo" />
                <span class="datos">
                  <strong>{{ p.nombre }}</strong>
                  @if (p.enlace; as e) {
                    <small>{{ e.correo || 'Cuenta enlazada' }} · desde {{ e.creado | date: 'dd/MM/yyyy' }}
                      @if (e.ultimoUso) { · último ingreso {{ e.ultimoUso | date: 'dd/MM/yyyy HH:mm' }} }</small>
                  } @else {
                    <small>Sin enlazar</small>
                  }
                </span>
                @if (p.enlace) {
                  <button class="btn btn-ghost btn-sm" type="button" (click)="quitar(p.codigo, p.nombre)"
                    [disabled]="ocupado()" [attr.aria-label]="'Quitar la cuenta de ' + p.nombre">Quitar</button>
                } @else {
                  <button class="btn btn-secondary btn-sm" type="button" (click)="enlazar(p.codigo, p.nombre)"
                    [disabled]="ocupado()" [attr.aria-label]="'Enlazar una cuenta de ' + p.nombre">
                    {{ ocupado() === p.codigo ? 'Abriendo…' : 'Enlazar' }}</button>
                }
              </li>
            }
          </ul>
        </div>
      </section>
    }
  `,
  styles: [`
    .explicacion { color: var(--muted); margin-bottom: 16px; }
    .alerta { margin-bottom: 12px; }
    .lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .lista li { display: flex; align-items: center; gap: 14px; padding: 12px 0; border-bottom: 1px solid var(--line); }
    .lista li:last-child { border-bottom: 0; }
    app-logo-proveedor { width: 22px; height: 22px; }
    .datos { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .datos small { color: var(--muted); overflow-wrap: anywhere; }
  `]
})
export class CuentasEnlazadasComponent implements OnInit {
  private auth = inject(AuthService);
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);

  readonly proveedores = signal<ProveedorExterno[]>([]);
  readonly enlaces = signal<EnlaceExterno[]>([]);
  readonly ocupado = signal<CodigoProveedor | 'quitando' | null>(null);
  readonly mensaje = signal('');
  readonly error = signal('');
  readonly filas = computed(() => this.proveedores().map(p => ({
    ...p, enlace: this.enlaces().find(e => e.proveedor === p.codigo) ?? null
  })));

  ngOnInit(): void {
    const q = this.ruta.snapshot.queryParamMap;
    const enlazado = q.get('enlazado');
    if (enlazado) {
      if (enlazado === 'error') this.error.set(MOTIVOS_EXTERNO[q.get('motivo') ?? ''] ?? 'No se pudo enlazar la cuenta.');
      else this.mensaje.set(`Listo: su cuenta de ${nombre(enlazado)} quedó enlazada. Ya puede entrar con ella.`);
      this.router.navigate([], { relativeTo: this.ruta, queryParams: {}, replaceUrl: true });
    }
    this.cargar();
  }

  private cargar(): void {
    forkJoin({ proveedores: this.auth.proveedoresExternos(), enlaces: this.auth.enlacesExternos() }).subscribe({
      next: r => { this.proveedores.set(r.proveedores); this.enlaces.set(r.enlaces); },
      error: e => this.error.set(mensajeDeError(e))
    });
  }

  enlazar(codigo: CodigoProveedor, nombreProveedor: string): void {
    this.mensaje.set(''); this.error.set('');
    this.ocupado.set(codigo);
    this.auth.irAProveedor(codigo, true).subscribe({
      error: e => { this.ocupado.set(null); this.error.set(mensajeDeError(e, `No se pudo abrir ${nombreProveedor}.`)); }
    });
  }

  quitar(codigo: CodigoProveedor, nombreProveedor: string): void {
    if (!confirm(`¿Quitar su cuenta de ${nombreProveedor}? Ya no podrá entrar con ella (podrá enlazarla de nuevo).`)) return;
    this.mensaje.set(''); this.error.set('');
    this.ocupado.set('quitando');
    this.auth.quitarEnlace(codigo).subscribe({
      next: () => {
        this.ocupado.set(null);
        this.enlaces.update(l => l.filter(e => e.proveedor !== codigo));
        this.mensaje.set(`Se quitó su cuenta de ${nombreProveedor}.`);
      },
      error: e => { this.ocupado.set(null); this.error.set(mensajeDeError(e)); }
    });
  }
}

function nombre(codigo: string): string {
  return ({ GOOGLE: 'Google', MICROSOFT: 'Microsoft', GITHUB: 'GitHub' } as Record<string, string>)[codigo] ?? codigo;
}
