import { Component, Input, OnChanges, signal } from '@angular/core';

import { InicioMfa } from './auth.service';
import { guardarArchivo } from './descargas';

/**
 * Código QR para la app autenticadora (F13) y el secreto escrito, por si no se puede escanear.
 * La librería del QR se carga solo aquí, la primera vez que se usa: no pesa en la carga inicial.
 */
@Component({
  selector: 'app-qr-mfa',
  standalone: true,
  template: `
    <div class="qr-mfa">
      @if (imagen()) { <img [src]="imagen()" width="196" height="196" alt="Código QR para la app autenticadora" /> }
      @else { <div class="qr-vacio" aria-hidden="true"></div> }
      <div>
        <ol class="pasos-mfa">
          <li>Abra una app autenticadora en su teléfono (Google Authenticator, Microsoft Authenticator, Authy…).</li>
          <li>Agregue una cuenta escaneando este código.</li>
          <li>Si no puede escanearlo, escriba esta clave:
            <code class="secreto-mfa">{{ agrupado }}</code></li>
          <li>Escriba abajo el código de 6 dígitos que muestra la app.</li>
        </ol>
      </div>
    </div>
  `,
  styles: [`
    .qr-mfa { display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start; margin-bottom: 12px; }
    .qr-mfa img, .qr-vacio { width: 196px; height: 196px; border: 1px solid var(--line); border-radius: 12px; background: #fff; }
    .pasos-mfa { margin: 0; padding-left: 20px; display: grid; gap: 6px; max-width: 420px; }
    .secreto-mfa { display: block; margin-top: 4px; font-size: 15px; letter-spacing: 1px; word-break: break-all; }
  `]
})
export class QrMfaComponent implements OnChanges {
  @Input({ required: true }) inicio!: InicioMfa;
  /** Signal: el await nativo corre fuera de zone.js, así la vista se actualiza igual. */
  readonly imagen = signal('');

  get agrupado(): string {
    return (this.inicio?.secreto ?? '').replace(/(.{4})/g, '$1 ').trim();
  }

  async ngOnChanges(): Promise<void> {
    this.imagen.set('');
    if (!this.inicio?.uri) return;
    // qrcode es CommonJS: al importarlo de forma dinámica sus funciones pueden quedar bajo "default".
    const modulo = await import('qrcode');
    const qr = ((modulo as unknown as { default?: typeof modulo }).default ?? modulo);
    try {
      this.imagen.set(await qr.toDataURL(this.inicio.uri, { margin: 1, width: 196, errorCorrectionLevel: 'M' }));
    } catch (e) {
      console.error('No se pudo dibujar el código QR', e);   // queda la clave escrita para agregarla a mano
    }
  }
}

/** Los códigos de recuperación, que se muestran una sola vez: copiarlos o descargarlos. */
@Component({
  selector: 'app-codigos-recuperacion',
  standalone: true,
  template: `
    <div class="alerta alerta-exito" role="status">
      <strong>Guarde estos códigos de recuperación en un lugar seguro.</strong>
      Cada uno sirve una sola vez para entrar si pierde el teléfono. No se volverán a mostrar.
    </div>
    <ul class="codigos-recuperacion">@for (c of codigos; track c) { <li class="mono">{{ c }}</li> }</ul>
    <div class="form-actions">
      <button class="btn btn-secondary btn-sm" type="button" (click)="copiar()">{{ copiados ? 'Copiados' : 'Copiar' }}</button>
      <button class="btn btn-secondary btn-sm" type="button" (click)="descargar()">Descargar (.txt)</button>
    </div>
  `,
  styles: [`
    .codigos-recuperacion { list-style: none; padding: 0; margin: 10px 0; display: grid;
      grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; }
    .codigos-recuperacion li { background: var(--parchment-2); border-radius: 8px; padding: 8px 10px; text-align: center; font-size: 15px; }
  `]
})
export class CodigosRecuperacionComponent {
  @Input({ required: true }) codigos: string[] = [];
  @Input() usuario = '';
  copiados = false;

  copiar(): void {
    navigator.clipboard?.writeText(this.codigos.join('\n')).then(() => this.copiados = true, () => {});
  }

  descargar(): void {
    const texto = `Gestión Jurídica — códigos de recuperación${this.usuario ? ' de ' + this.usuario : ''}\n`
      + `Cada código sirve una sola vez.\n\n${this.codigos.join('\n')}\n`;
    guardarArchivo(new Blob([texto], { type: 'text/plain;charset=utf-8' }), 'codigos-recuperacion.txt');
  }
}
