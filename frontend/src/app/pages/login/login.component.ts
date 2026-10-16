import { Component, OnInit, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { mensajeDeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="login">
      <form class="login-caja" #form="ngForm" (ngSubmit)="entrar(form)" novalidate>
        <h1>Gestión Jurídica</h1>
        <p class="form-ayuda">Rodríguez &amp; Asociados</p>

        @if (aviso) { <div class="alerta alerta-exito">{{ aviso }}</div> }
        @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }

        <div class="form-group">
          <label class="form-label" for="usuario">Usuario o correo</label>
          <input id="usuario" class="form-input" name="usuario" [(ngModel)]="usuario" required autocomplete="username"
            autofocus />
        </div>
        <div class="form-group">
          <label class="form-label" for="clave">Contraseña</label>
          <input id="clave" class="form-input" type="password" name="clave" [(ngModel)]="clave" required
            autocomplete="current-password" />
        </div>
        <button class="btn btn-primary" type="submit" [disabled]="entrando">{{ entrando ? 'Entrando...' : 'Entrar' }}</button>
      </form>
    </main>
  `,
  styles: [`
    .login { min-height: 100vh; display: grid; place-items: center; padding: 16px; background: var(--primary); }
    .login-caja { width: 100%; max-width: 360px; background: var(--bg); border-radius: 8px; padding: 28px;
      display: flex; flex-direction: column; gap: 14px; box-shadow: 0 8px 30px rgba(0,0,0,0.25); }
    h1 { margin: 0; font-size: 1.3rem; color: var(--primary); }
  `]
})
export class LoginComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  usuario = '';
  clave = '';
  entrando = false;
  error = '';
  aviso = '';

  ngOnInit(): void {
    if (this.ruta.snapshot.queryParamMap.get('vencida')) this.aviso = 'Su sesión terminó. Inicie sesión de nuevo.';
    if (this.auth.token()) this.router.navigateByUrl('/');
  }

  entrar(form: NgForm): void {
    this.error = this.aviso = '';
    if (form.invalid) { this.error = 'Escriba el usuario y la contraseña.'; return; }
    this.entrando = true;
    this.auth.iniciar(this.usuario.trim(), this.clave).subscribe({
      next: s => {
        this.clave = '';
        const volver = this.ruta.snapshot.queryParamMap.get('volver');
        // Solo rutas internas: un ?volver= hacia otro sitio no debe sacar al usuario de la app.
        const destino = volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : '/';
        this.router.navigateByUrl(s.roles.includes('CLIENTE') && s.roles.length === 1 ? '/cuenta' : destino);
      },
      error: e => {
        this.entrando = false;
        this.clave = '';
        this.error = mensajeDeError(e, 'No se pudo iniciar sesión.');
      }
    });
  }
}
