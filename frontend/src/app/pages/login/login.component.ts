import { Component, OnInit, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { mensajeDeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LogoComponent } from '../../core/logo.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, LogoComponent],
  template: `
    <main class="login">
      <form class="login-caja" #form="ngForm" (ngSubmit)="entrar(form)" novalidate>
        <header class="login-cabeza">
          <span class="login-logo"><app-logo /></span>
          <div>
            <h1>Gestión Jurídica</h1>
            <p>Rodríguez &amp; Asociados</p>
          </div>
        </header>
        <div class="login-cuerpo">

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
        </div>
      </form>
    </main>
  `,
  styles: [`
    .login { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
    .login-caja { width: 100%; max-width: 400px; background: var(--bg); border-radius: 24px; overflow: hidden;
      box-shadow: 0 30px 60px -20px rgba(16,38,64,.5); }
    .login-cabeza { background: linear-gradient(160deg, var(--blue) 0%, #15304f 100%); padding: 22px 24px;
      display: flex; align-items: center; gap: 16px; }
    .login-logo { width: 58px; height: 58px; flex: none; display: grid; }
    h1 { margin: 0; font-family: var(--display); font-size: 24px; font-weight: 600; color: var(--parchment); }
    .login-cabeza p { margin: 2px 0 0; font-family: var(--second); color: #c3cfdd; }
    .login-cuerpo { padding: 22px 24px 24px; display: flex; flex-direction: column; gap: 14px; }
    .login-cuerpo .alerta { margin-bottom: 0; }
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
    if (this.auth.token()) this.router.navigateByUrl(this.auth.inicio());
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
        const destino = volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : this.auth.inicio();
        if (s.debeCambiarClave) { this.router.navigate(['/cuenta'], { queryParams: { obligatorio: 1 } }); return; }
        this.router.navigateByUrl(destino);
      },
      error: e => {
        this.entrando = false;
        this.clave = '';
        this.error = mensajeDeError(e, 'No se pudo iniciar sesión.');
      }
    });
  }
}
