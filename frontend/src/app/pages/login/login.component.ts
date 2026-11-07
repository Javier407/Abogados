import { Component, OnInit, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { mensajeDeError } from '../../core/api.service';
import { AuthService, InicioMfa, Sesion, esSegundoPaso } from '../../core/auth.service';
import { LogoComponent } from '../../core/logo.component';
import { CodigosRecuperacionComponent, QrMfaComponent } from '../../core/mfa-ui';
import { CodigoOtpComponent } from '../../ui/codigo-otp.component';

/**
 * Inicio de sesión. Con la verificación en dos pasos (F13) la contraseña correcta lleva a un segundo paso: el código
 * de la app autenticadora (o uno de recuperación). Si el rol la exige y aún no está activa, se configura aquí mismo
 * y se muestran los códigos de recuperación antes de entrar.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, LogoComponent, QrMfaComponent, CodigosRecuperacionComponent, CodigoOtpComponent],
  template: `
    <main class="login">
      <!-- Columna de marca (se oculta en el celular) -->
      <section class="marca" aria-hidden="true">
        <app-logo class="marca-logo" />
        <div class="marca-texto">
          <h2>Cada término, a tiempo.</h2>
          <p>Procesos, plazos, audiencias y facturación de la firma en un solo lugar.</p>
        </div>
        <ul class="marca-puntos">
          <li><span></span>Términos en días hábiles judiciales</li>
          <li><span></span>Vigilancia de la Rama Judicial</li>
          <li><span></span>Borradores con IA local, sin salir del servidor</li>
        </ul>
        <div class="orbita" aria-hidden="true"></div>
      </section>

      <section class="lado-form">
        <form class="login-caja" [class.ancha]="paso === 'configurar' || paso === 'codigos'" #form="ngForm"
              (ngSubmit)="enviar(form)" novalidate>
          <header class="login-cabeza">
            <app-logo class="logo-movil" />
            <h1>{{ titulo }}</h1>
            <p>{{ subtitulo }}</p>
          </header>

          @if (aviso) { <div class="alerta alerta-exito" role="status">{{ aviso }}</div> }
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
              <button class="btn btn-primary btn-grande" type="submit" [disabled]="enviando">{{ enviando ? 'Entrando…' : 'Entrar' }}</button>
            }
            @case ('codigo') {
              @if (usarRecuperacion) {
                <div class="form-group">
                  <label class="form-label" for="codigo">Código de recuperación</label>
                  <input id="codigo" class="form-input codigo" name="codigo" [(ngModel)]="codigo" required autofocus
                    autocomplete="one-time-code" maxlength="9" placeholder="abcd-efgh" />
                </div>
              } @else {
                <app-codigo-otp name="codigo" [(ngModel)]="codigo" required [conError]="!!error"
                  etiqueta="Código de 6 dígitos de su app autenticadora" (completo)="enviar(form)" />
              }
              <button class="btn btn-primary btn-grande" type="submit" [disabled]="enviando">{{ enviando ? 'Verificando…' : 'Verificar' }}</button>
              <div class="enlaces">
                <button class="btn-enlace" type="button" (click)="usarRecuperacion = !usarRecuperacion; codigo = ''">
                  {{ usarRecuperacion ? 'Usar el código de la app' : '¿Perdió el teléfono? Use un código de recuperación' }}</button>
                <button class="btn-enlace" type="button" (click)="volver()">Volver</button>
              </div>
            }
            @case ('configurar') {
              <p class="explicacion"><strong>Su cuenta exige la verificación en dos pasos.</strong> Actívela para entrar:
                además de la contraseña, cada vez se pedirá un código de su teléfono.</p>
              @if (inicio) { <app-qr-mfa [inicio]="inicio" /> }
              <app-codigo-otp name="codigo" [(ngModel)]="codigo" required [conError]="!!error"
                etiqueta="Código de 6 dígitos para activar" />
              <button class="btn btn-primary btn-grande" type="submit" [disabled]="enviando || !inicio">{{ enviando ? 'Activando…' : 'Activar y entrar' }}</button>
              <div class="enlaces"><button class="btn-enlace" type="button" (click)="volver()">Volver</button></div>
            }
            @case ('codigos') {
              <app-codigos-recuperacion [codigos]="codigosRecuperacion" [usuario]="usuario" />
              <button class="btn btn-primary btn-grande" type="submit">Ya los guardé, continuar</button>
            }
          }
          <p class="pie">Rodríguez &amp; Asociados · acceso solo para personal y clientes de la firma</p>
        </form>
      </section>
    </main>
  `,
  styles: [`
    .login { min-height: 100vh; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); background: var(--surface); }

    .marca {
      position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; gap: 40px;
      padding: 48px 56px; color: #fff;
      background: radial-gradient(120% 90% at 0% 0%, #5B50FF 0%, #3529E6 38%, #0F1E45 100%);
    }
    .marca-logo { --logo-tam: 42px; --logo-texto: 24px; position: relative; z-index: 1; }
    .marca-logo ::ng-deep .marca { color: #fff; }
    .marca-logo ::ng-deep rect { fill: rgba(255, 255, 255, .16); }
    .marca-texto { position: relative; z-index: 1; max-width: 460px; }
    .marca-texto h2 { color: #fff; font-size: 40px; font-weight: 800; letter-spacing: -0.03em; line-height: 1.1; }
    .marca-texto p { color: rgba(255, 255, 255, .85); font-size: 16px; margin-top: 14px; line-height: 1.6; }
    .marca-puntos { position: relative; z-index: 1; list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
    .marca-puntos li { display: flex; align-items: center; gap: 12px; color: rgba(255, 255, 255, .92); font-weight: 500; }
    .marca-puntos span { width: 8px; height: 8px; border-radius: 50%; background: #fff; box-shadow: 0 0 0 5px rgba(255, 255, 255, .18); }
    /* Anillos decorativos que giran despacio */
    .orbita {
      position: absolute; right: -180px; top: 50%; width: 560px; height: 560px; translate: 0 -50%; border-radius: 50%;
      border: 1px solid rgba(255, 255, 255, .14); animation: girar 60s linear infinite;
    }
    .orbita::before, .orbita::after { content: ""; position: absolute; border-radius: 50%; border: 1px solid rgba(255, 255, 255, .1); }
    .orbita::before { inset: 70px; }
    .orbita::after { inset: 150px; border-style: dashed; }
    @keyframes girar { to { rotate: 360deg; } }

    .lado-form { display: grid; place-items: center; padding: 32px 24px; background: var(--bg-page); }
    .login-caja {
      width: 100%; max-width: 420px; display: flex; flex-direction: column; gap: 16px;
      background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-xl); padding: 36px 32px;
      box-shadow: var(--shadow-lg);
    }
    .login-caja.ancha { max-width: 680px; }
    .login-cabeza { display: flex; flex-direction: column; gap: 6px; margin-bottom: 4px; }
    .logo-movil { display: none; --logo-tam: 36px; --logo-texto: 20px; margin-bottom: 14px; }
    h1 { font-size: 26px; }
    .login-cabeza p { color: var(--muted); }
    .btn-grande { min-height: 46px; font-size: 15px; margin-top: 4px; }
    .explicacion { color: var(--ink); }
    .codigo { font-size: 18px; letter-spacing: 3px; text-align: center; }
    .enlaces { display: flex; flex-direction: column; align-items: center; gap: 6px; }
    .btn-enlace { background: none; border: 0; color: var(--accent-ink); cursor: pointer; padding: 4px; font: inherit; font-size: 13.5px; }
    .btn-enlace:hover { text-decoration: underline; }
    .pie { text-align: center; color: var(--muted); font-size: 12px; margin-top: 8px; }

    @media (max-width: 900px) {
      .login { grid-template-columns: 1fr; }
      .marca { display: none; }
      .logo-movil { display: inline-flex; }
      .login-caja { padding: 28px 22px; }
    }
    @media (prefers-reduced-motion: reduce) { .orbita { animation: none; } }
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

  get titulo(): string {
    return { clave: 'Bienvenido de nuevo', codigo: 'Verificación en dos pasos', configurar: 'Active la verificación',
      codigos: 'Guarde sus códigos' }[this.paso];
  }
  get subtitulo(): string {
    return { clave: 'Inicie sesión en Lexora', codigo: this.usarRecuperacion ? 'Escriba uno de sus códigos de recuperación.'
      : 'Escriba el código de 6 dígitos de su app autenticadora.', configurar: 'Escanee el código QR con su app autenticadora.',
      codigos: 'Los necesitará si pierde el teléfono.' }[this.paso];
  }

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
