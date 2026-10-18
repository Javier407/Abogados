import { Component, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Router } from '@angular/router';

import { mensajeDeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

/** Datos de la cuenta y cambio de contraseña. */
@Component({
  selector: 'app-cuenta',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Mi cuenta</h1>
        <p>{{ auth.sesion()?.nombre }} · {{ auth.sesion()?.usuario }} · {{ auth.sesion()?.roles?.join(', ') }}</p>
      </div>

      @if (!auth.esPersonal()) {
        <div class="alerta alerta-exito">El portal para que los clientes consulten sus procesos estará disponible más adelante.</div>
      }
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
    </div>
  `
})
export class CuentaComponent {
  readonly auth = inject(AuthService);
  private router = inject(Router);
  actual = '';
  nueva = '';
  repetir = '';
  mensaje = '';
  error = '';

  cambiar(form: NgForm): void {
    this.mensaje = this.error = '';
    if (form.invalid) { this.error = 'Complete los tres campos (la nueva con al menos 10 caracteres).'; return; }
    if (this.nueva !== this.repetir) { this.error = 'Las contraseñas nuevas no coinciden.'; return; }
    this.auth.cambiarClave(this.actual, this.nueva).subscribe({
      next: () => {
        const eraObligatorio = !!this.auth.sesion()?.debeCambiarClave;
        this.mensaje = 'Contraseña cambiada.';
        form.resetForm();
        this.auth.marcarClaveCambiada();
        if (eraObligatorio) this.router.navigateByUrl(this.auth.esPersonal() ? '/' : '/cuenta');
      },
      error: e => this.error = mensajeDeError(e)
    });
  }
}
