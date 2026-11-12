import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ALMACENAMIENTO, DATOS_LEGALES } from './datos-legales';
import { PaginaLegalComponent } from './pagina-legal.component';

/** Política de cookies: lista exacta de lo que la app guarda en el navegador (sale de datos-legales.ts). */
@Component({
  selector: 'app-cookies',
  standalone: true,
  imports: [PaginaLegalComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-pagina-legal titulo="Política de cookies">
      <p>Una cookie es un pequeño archivo que un sitio guarda en su navegador. {{ d.producto }} usa muy pocas: solo las
        necesarias para que usted pueda iniciar sesión de forma segura, y unas preferencias que se guardan en su propio navegador.</p>
      <p><strong>No usamos cookies de analítica, publicidad ni seguimiento, ni de terceros.</strong> Por eso no le pedimos
        aceptar cookies opcionales: no las hay.</p>

      <h2>Lo que se guarda en su navegador</h2>
      <div class="tabla-wrapper">
        <table class="tabla">
          <caption class="sr-only">Cookies y almacenamiento del navegador que usa {{ d.producto }}</caption>
          <thead><tr><th scope="col">Nombre</th><th scope="col">Tipo</th><th scope="col">Para qué</th><th scope="col">Duración</th></tr></thead>
          <tbody>
            @for (c of almacenamiento; track c.nombre) {
              <tr>
                <td><code>{{ c.nombre }}</code><br /><small>{{ c.necesaria ? 'Necesaria' : 'Preferencia' }}</small></td>
                <td>{{ c.tipo }}</td>
                <td>{{ c.finalidad }}</td>
                <td>{{ c.duracion }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <h2>Entrar con Google, Microsoft o GitHub</h2>
      <p>Si elige entrar con una de esas cuentas, por unos momentos estará en la página del proveedor, que puede usar sus propias
        cookies según su política. {{ d.producto }} no instala cookies de esos proveedores.</p>

      <h2>Cómo borrarlas</h2>
      <p>Puede borrar las cookies y el almacenamiento desde la configuración de su navegador. Si borra las necesarias, se cerrará
        su sesión y tendrá que volver a entrar. Las preferencias (tema y menú) volverán a sus valores iniciales.</p>

      <h2>Más información</h2>
      <p>Cómo tratamos sus datos personales y cómo ejercer sus derechos: <a routerLink="/privacidad">política de tratamiento de
        datos personales</a>.</p>
    </app-pagina-legal>
  `,
  styles: [`
    code { font-family: var(--mono); font-size: 13px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    .tabla td { vertical-align: top; font-size: 14px; }
  `]
})
export class CookiesComponent {
  readonly d = DATOS_LEGALES;
  readonly almacenamiento = ALMACENAMIENTO;
}
