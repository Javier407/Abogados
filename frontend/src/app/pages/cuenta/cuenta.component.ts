import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Router } from '@angular/router';

import { mensajeDeError } from '../../core/api.service';
import { AuthService, EstadoMfa, InicioMfa } from '../../core/auth.service';
import { CodigosRecuperacionComponent, QrMfaComponent } from '../../core/mfa-ui';

/** Datos de la cuenta, cambio de contraseña y verificación en dos pasos (F13). */
@Component({
  selector: 'app-cuenta',
  standalone: true,
  imports: [FormsModule, DatePipe, QrMfaComponent, CodigosRecuperacionComponent],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Mi cuenta</h1>
        <p>{{ auth.sesion()?.nombre }} · {{ auth.sesion()?.usuario }} · {{ auth.sesion()?.roles?.join(', ') }}</p>
      </div>

      @if (auth.sesion()?.debeCambiarClave) {
        <div class="alerta alerta-error" role="alert">Su contraseña es temporal. Póngale una propia para seguir usando la aplicación.</div>
      }
      @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }

      <div class="card">
        <div class="card-header"><h2>Cambiar contraseña</h2></div>
        <div class="card-body">
          <form #form="ngForm" (ngSubmit)="cambiar(form)" novalidate>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label" for="actual">Contraseña actual</label>
                <input id="actual" class="form-input" type="password" name="actual" [(ngModel)]="actual" required autocomplete="current-password" />
              </div>
              <div class="form-group">
                <label class="form-label" for="nueva">Contraseña nueva</label>
                <input id="nueva" class="form-input" type="password" name="nueva" [(ngModel)]="nueva" required minlength="10"
                  autocomplete="new-password" />
                <span class="form-ayuda">Mínimo 10 caracteres, con letras y números, sin su usuario ni documento.</span>
              </div>
              <div class="form-group">
                <label class="form-label" for="repetir">Repita la contraseña nueva</label>
                <input id="repetir" class="form-input" type="password" name="repetir" [(ngModel)]="repetir" required autocomplete="new-password" />
              </div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="submit">Cambiar contraseña</button>
            </div>
          </form>
        </div>
      </div>

      @if (!auth.sesion()?.debeCambiarClave) {
        <div class="card">
          <div class="card-header"><h2>Verificación en dos pasos</h2></div>
          <div class="card-body">
            @if (codigos.length) {
              <app-codigos-recuperacion [codigos]="codigos" [usuario]="auth.sesion()?.usuario ?? ''" />
              <div class="form-actions"><button class="btn btn-primary" type="button" (click)="codigos = []">Ya los guardé</button></div>
            } @else if (mfa?.activo) {
              <p><span class="badge badge-success">Activa</span> desde el {{ mfa!.desde | date:'d MMM y' }}.
                Al entrar se pide, además de la contraseña, el código de su app autenticadora.
                Le quedan <strong>{{ mfa!.codigosRestantes }}</strong> código(s) de recuperación.</p>
              <div class="form-grid">
                <div class="form-group"><label class="form-label" for="mfaClave">Contraseña</label>
                  <input id="mfaClave" class="form-input" type="password" [(ngModel)]="claveMfa" autocomplete="current-password" /></div>
                @if (!mfa!.obligatorio) {
                  <div class="form-group"><label class="form-label" for="mfaCodigo">Código de la app (para desactivar)</label>
                    <input id="mfaCodigo" class="form-input" inputmode="numeric" maxlength="9" [(ngModel)]="codigoMfa" autocomplete="one-time-code" /></div>
                }
              </div>
              <div class="form-actions">
                <button class="btn btn-secondary" type="button" (click)="regenerar()" [disabled]="!claveMfa">Nuevos códigos de recuperación</button>
                @if (mfa!.obligatorio) { <span class="form-ayuda">Su rol exige la verificación en dos pasos: no se puede desactivar.</span> }
                @else { <button class="btn btn-danger" type="button" (click)="desactivar()" [disabled]="!claveMfa || !codigoMfa">Desactivar</button> }
              </div>
            } @else if (inicio) {
              <app-qr-mfa [inicio]="inicio" />
              <div class="form-grid">
                <div class="form-group"><label class="form-label" for="mfaNuevo">Código de 6 dígitos</label>
                  <input id="mfaNuevo" class="form-input" inputmode="numeric" maxlength="6" [(ngModel)]="codigoMfa"
                    autocomplete="one-time-code" (keyup.enter)="activar()" /></div>
              </div>
              <div class="form-actions">
                <button class="btn btn-primary" type="button" (click)="activar()" [disabled]="codigoMfa.length !== 6">Activar</button>
                <button class="btn btn-secondary" type="button" (click)="inicio = null">Cancelar</button>
              </div>
            } @else if (mfa) {
              <p>Además de la contraseña, pida un código de 6 dígitos de una app en su teléfono. Así, aunque alguien
                conozca su contraseña, no puede entrar sin el teléfono.
                @if (mfa.obligatorio) { <strong>Su rol la exige: la próxima vez que entre se la pedirá.</strong> }</p>
              <div class="form-actions"><button class="btn btn-primary" type="button" (click)="empezar()">Activar la verificación en dos pasos</button></div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class CuentaComponent implements OnInit {
  readonly auth = inject(AuthService);
  private router = inject(Router);
  actual = '';
  nueva = '';
  repetir = '';
  mfa: EstadoMfa | null = null;
  inicio: InicioMfa | null = null;
  codigos: string[] = [];
  claveMfa = '';
  codigoMfa = '';
  mensaje = '';
  error = '';

  ngOnInit(): void {
    if (!this.auth.sesion()?.debeCambiarClave) this.cargarMfa();
  }

  cambiar(form: NgForm): void {
    this.mensaje = this.error = '';
    if (form.invalid) { this.error = 'Complete los tres campos (la nueva con al menos 10 caracteres).'; return; }
    if (this.nueva !== this.repetir) { this.error = 'Las contraseñas nuevas no coinciden.'; return; }
    this.auth.cambiarClave(this.actual, this.nueva).subscribe({
      next: () => {
        const eraObligatorio = !!this.auth.sesion()?.debeCambiarClave;
        this.mensaje = 'Contraseña cambiada.';
        form.resetForm();
        // Token nuevo sin la marca de clave temporal (las demás sesiones abiertas ya se cerraron).
        this.auth.marcarClaveCambiada().subscribe(() => {
          if (eraObligatorio) this.router.navigateByUrl(this.auth.inicio());
        });
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  empezar(): void {
    this.limpiar();
    this.auth.iniciarMfa().subscribe({ next: i => this.inicio = i, error: e => this.error = mensajeDeError(e) });
  }

  activar(): void {
    this.limpiar();
    this.auth.activarMfa(this.codigoMfa.trim()).subscribe({
      next: r => { this.codigos = r.codigos; this.inicio = null; this.codigoMfa = ''; this.mensaje = 'Verificación en dos pasos activada.'; this.cargarMfa(); },
      error: e => { this.codigoMfa = ''; this.error = mensajeDeError(e); }
    });
  }

  regenerar(): void {
    if (!confirm('Los códigos de recuperación anteriores dejarán de servir. ¿Continuar?')) return;
    this.limpiar();
    this.auth.regenerarCodigos(this.claveMfa).subscribe({
      next: r => { this.codigos = r.codigos; this.claveMfa = ''; this.cargarMfa(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  desactivar(): void {
    if (!confirm('¿Desactivar la verificación en dos pasos? Su cuenta quedará protegida solo por la contraseña.')) return;
    this.limpiar();
    this.auth.desactivarMfa(this.claveMfa, this.codigoMfa.trim()).subscribe({
      next: () => { this.mensaje = 'Verificación en dos pasos desactivada.'; this.claveMfa = this.codigoMfa = ''; this.cargarMfa(); },
      error: e => this.error = mensajeDeError(e)
    });
  }

  private cargarMfa(): void {
    this.auth.estadoMfa().subscribe({ next: m => this.mfa = m, error: () => {} });
  }

  private limpiar(): void {
    this.mensaje = this.error = '';
  }
}
