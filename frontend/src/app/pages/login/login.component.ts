import { Component, OnInit, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { mensajeDeError } from '../../core/api.service';
import { AuthService, InicioMfa, Sesion, esSegundoPaso } from '../../core/auth.service';
import { LogoComponent } from '../../core/logo.component';
import { CodigosRecuperacionComponent, QrMfaComponent } from '../../core/mfa-ui';

/**
 * Inicio de sesión. Con la verificación en dos pasos (F13) la contraseña correcta lleva a un segundo paso: el código
 * de la app autenticadora (o uno de recuperación). Si el rol la exige y aún no está activa, se configura aquí mismo
 * y se muestran los códigos de recuperación antes de entrar.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, LogoComponent, QrMfaComponent, CodigosRecuperacionComponent],
  template: `
    <main class="login">
      <form class="login-caja" [class.ancha]="paso === 'configurar' || paso === 'codigos'" #form="ngForm"
            (ngSubmit)="enviar(form)" novalidate>
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

        @switch (paso) {
          @case ('clave') {
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
            <button class="btn btn-primary" type="submit" [disabled]="enviando">{{ enviando ? 'Entrando...' : 'Entrar' }}</button>
          }
          @case ('codigo') {
            <p class="explicacion">{{ usarRecuperacion ? 'Escriba uno de sus códigos de recuperación.'
              : 'Escriba el código de 6 dígitos de su app autenticadora.' }}</p>
            <div class="form-group">
              <label class="form-label" for="codigo">{{ usarRecuperacion ? 'Código de recuperación' : 'Código' }}</label>
              <input id="codigo" class="form-input codigo" name="codigo" [(ngModel)]="codigo" required autofocus
                [attr.inputmode]="usarRecuperacion ? 'text' : 'numeric'" autocomplete="one-time-code"
                [attr.maxlength]="usarRecuperacion ? 9 : 6" [placeholder]="usarRecuperacion ? 'abcd-efgh' : '123456'" />
            </div>
            <button class="btn btn-primary" type="submit" [disabled]="enviando">{{ enviando ? 'Verificando...' : 'Verificar' }}</button>
            <button class="btn-enlace" type="button" (click)="usarRecuperacion = !usarRecuperacion; codigo = ''">
              {{ usarRecuperacion ? 'Usar el código de la app' : '¿Perdió el teléfono? Use un código de recuperación' }}</button>
            <button class="btn-enlace" type="button" (click)="volver()">Volver</button>
          }
          @case ('configurar') {
            <p class="explicacion"><strong>Su cuenta exige la verificación en dos pasos.</strong> Actívela para entrar:
              además de la contraseña, cada vez se pedirá un código de su teléfono.</p>
            @if (inicio) { <app-qr-mfa [inicio]="inicio" /> }
            <div class="form-group">
              <label class="form-label" for="codigoNuevo">Código de 6 dígitos</label>
              <input id="codigoNuevo" class="form-input codigo" name="codigo" [(ngModel)]="codigo" required inputmode="numeric"
                maxlength="6" autocomplete="one-time-code" placeholder="123456" />
            </div>
            <button class="btn btn-primary" type="submit" [disabled]="enviando || !inicio">{{ enviando ? 'Activando...' : 'Activar y entrar' }}</button>
            <button class="btn-enlace" type="button" (click)="volver()">Volver</button>
          }
          @case ('codigos') {
            <app-codigos-recuperacion [codigos]="codigosRecuperacion" [usuario]="usuario" />
            <button class="btn btn-primary" type="submit">Ya los guardé, continuar</button>
          }
        }
        </div>
      </form>
    </main>
  `,
  styles: [`
    .login { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
    .login-caja { width: 100%; max-width: 400px; background: var(--bg); border-radius: 24px; overflow: hidden;
      box-shadow: 0 30px 60px -20px rgba(16,38,64,.5); }
    .login-caja.ancha { max-width: 680px; }
    .login-cabeza { background: linear-gradient(160deg, var(--blue) 0%, #15304f 100%); padding: 22px 24px;
      display: flex; align-items: center; gap: 16px; }
    .login-logo { width: 58px; height: 58px; flex: none; display: grid; }
    h1 { margin: 0; font-family: var(--display); font-size: 24px; font-weight: 600; color: var(--parchment); }
    .login-cabeza p { margin: 2px 0 0; font-family: var(--second); color: #c3cfdd; }
    .login-cuerpo { padding: 22px 24px 24px; display: flex; flex-direction: column; gap: 14px; }
    .login-cuerpo .alerta { margin-bottom: 0; }
    .explicacion { margin: 0; color: var(--ink); }
    .codigo { font-size: 20px; letter-spacing: 4px; text-align: center; }
    .btn-enlace { background: none; border: 0; color: var(--blue); cursor: pointer; padding: 2px; font: inherit; text-decoration: underline; }
  `]
})
export class LoginComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  paso: 'clave' | 'codigo' | 'configurar' | 'codigos' = 'clave';
  usuario = '';
  clave = '';
  codigo = '';
  desafio = '';
  usarRecuperacion = false;
  inicio: InicioMfa | null = null;
  codigosRecuperacion: string[] = [];
  private sesionPendiente: Sesion | null = null;
  enviando = false;
  error = '';
  aviso = '';

  ngOnInit(): void {
    if (this.ruta.snapshot.queryParamMap.get('vencida')) this.aviso = 'Su sesión terminó. Inicie sesión de nuevo.';
    if (this.auth.token()) this.router.navigateByUrl(this.auth.inicio());
  }

  enviar(form: NgForm): void {
    this.error = this.aviso = '';
    if (this.paso === 'codigos') { this.continuar(this.sesionPendiente!); return; }
    if (form.invalid) {
      this.error = this.paso === 'clave' ? 'Escriba el usuario y la contraseña.' : 'Escriba el código.';
      return;
    }
    this.enviando = true;
    if (this.paso === 'clave') this.entrar();
    else if (this.paso === 'codigo') this.verificar();
    else this.activar();
  }

  volver(): void {
    this.paso = 'clave';
    this.codigo = this.desafio = '';
    this.inicio = null;
    this.usarRecuperacion = false;
  }

  private entrar(): void {
    this.auth.iniciar(this.usuario.trim(), this.clave).subscribe({
      next: r => {
        this.clave = '';
        this.enviando = false;
        if (!esSegundoPaso(r)) { this.continuar(r); return; }
        this.desafio = r.desafio;
        this.codigo = '';
        if (r.configurarMfa) {
          this.paso = 'configurar';
          this.auth.iniciarMfaAlEntrar(r.desafio).subscribe({ next: i => this.inicio = i, error: e => this.fallo(e) });
        } else {
          this.paso = 'codigo';
        }
      },
      error: e => { this.clave = ''; this.fallo(e, 'No se pudo iniciar sesión.'); }
    });
  }

  private verificar(): void {
    this.auth.completarMfa(this.desafio, this.codigo.trim()).subscribe({
      next: s => this.continuar(s),
      error: e => { this.codigo = ''; this.fallo(e, 'No se pudo verificar el código.'); }
    });
  }

  private activar(): void {
    this.auth.activarMfaAlEntrar(this.desafio, this.codigo.trim()).subscribe({
      next: s => {
        this.enviando = false;
        this.sesionPendiente = s;
        this.codigosRecuperacion = s.codigosRecuperacion ?? [];
        this.paso = 'codigos';
      },
      error: e => { this.codigo = ''; this.fallo(e, 'No se pudo activar.'); }
    });
  }

  /** El desafío dura 5 minutos: si venció o la cuenta se bloqueó, se vuelve a la contraseña (con el mensaje). */
  private fallo(e: unknown, porDefecto = 'No se pudo completar.'): void {
    this.enviando = false;
    this.error = mensajeDeError(e, porDefecto);
    if (this.paso !== 'clave' && (this.error.includes('venció') || this.error.includes('Demasiados intentos'))) this.volver();
  }

  private continuar(s: Sesion): void {
    const volver = this.ruta.snapshot.queryParamMap.get('volver');
    // Solo rutas internas: un ?volver= hacia otro sitio no debe sacar al usuario de la app.
    const destino = volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : this.auth.inicio();
    if (s.debeCambiarClave) { this.router.navigate(['/cuenta'], { queryParams: { obligatorio: 1 } }); return; }
    this.router.navigateByUrl(destino);
  }
}
