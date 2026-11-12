import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DATOS_LEGALES } from './datos-legales';
import { PaginaLegalComponent } from './pagina-legal.component';

/**
 * Política de tratamiento de datos personales (Ley 1581 de 2012, Decreto 1377 de 2013 compilado en el Decreto 1074
 * de 2015). Borrador técnico: describe lo que el sistema hace de verdad; lo jurídico lo revisa la firma.
 */
@Component({
  selector: 'app-privacidad',
  standalone: true,
  imports: [PaginaLegalComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-pagina-legal titulo="Política de tratamiento de datos personales">
      <p>Esta política explica cómo {{ d.responsable }} recoge, usa, guarda y protege los datos personales que maneja en
        {{ d.producto }}, su sistema de gestión jurídica, y cómo puede usted ejercer sus derechos. Se expide en
        cumplimiento de la Ley 1581 de 2012 y del Decreto 1377 de 2013 (compilado en el Decreto 1074 de 2015).</p>

      <h2>1. Responsable del tratamiento</h2>
      <ul>
        <li><strong>Razón social:</strong> {{ d.responsable }}</li>
        <li><strong>NIT:</strong> {{ d.nit }}</li>
        <li><strong>Dirección:</strong> {{ d.direccion }}, {{ d.ciudad }}</li>
        <li><strong>Correo para datos personales:</strong> {{ d.correoDatos }}</li>
        <li><strong>Teléfono:</strong> {{ d.telefono }}</li>
      </ul>

      <h2>2. Qué datos tratamos</h2>
      <ul>
        <li><strong>Clientes:</strong> nombre o razón social, tipo y número de documento, correo, teléfono, dirección y ciudad;
          la información de sus procesos, consultas, honorarios y pagos.</li>
        <li><strong>Personas que figuran en los procesos</strong> (contrapartes, apoderados, terceros, peritos): los datos que
          constan en el expediente o en las consultas públicas de la Rama Judicial.</li>
        <li><strong>Personal de la firma:</strong> datos de su cuenta (usuario, nombre, correo, rol), la bitácora de ingresos
          con fecha, resultado y dirección IP, y las cuentas de Google, Microsoft o GitHub que cada persona decida enlazar
          (el identificador que entrega el proveedor y el correo).</li>
        <li><strong>Toda persona que use el sistema</strong> (personal y clientes del portal): un registro de cada acción,
          con quién la hizo, qué pantalla o dato consultó o cambió, cuándo, con qué resultado y desde qué dirección IP.
          No guarda el contenido de lo que se consulta o se envía.</li>
      </ul>

      <h2>3. Para qué los usamos</h2>
      <ul>
        <li>Prestar los servicios jurídicos contratados: representar al cliente, llevar sus procesos y vigilar sus términos y audiencias.</li>
        <li>Informar al cliente del estado de sus asuntos por el portal y por los avisos que él mismo active.</li>
        <li>Cobrar los honorarios y gastos pactados, y cumplir las obligaciones tributarias y contables.</li>
        <li>Proteger la seguridad del sistema: controlar el acceso por roles, detectar intentos indebidos y avisar a cada persona de
          los cambios de seguridad de su cuenta.</li>
        <li>Atender requerimientos de autoridades competentes, cuando la ley lo exija.</li>
      </ul>
      <p>Los datos no se venden ni se usan para publicidad. El sistema no usa herramientas de analítica ni de seguimiento.</p>

      <h2>4. Datos sensibles y de niños, niñas y adolescentes</h2>
      <p>Algunos procesos contienen datos sensibles (por ejemplo, de salud) o de menores de edad. Se tratan solo cuando son
        necesarios para el asunto, con acceso restringido al equipo que lo atiende, y en el caso de menores respetando su interés
        superior y sus derechos prevalentes. Responder preguntas sobre datos sensibles es facultativo. Además, toda la información
        de los asuntos está cubierta por el secreto profesional del abogado.</p>

      <h2>5. Sus derechos</h2>
      <p>Como titular de los datos, usted puede (Ley 1581 de 2012, art. 8):</p>
      <ul>
        <li>Conocer, actualizar y rectificar sus datos.</li>
        <li>Pedir prueba de la autorización que dio para su tratamiento.</li>
        <li>Ser informado del uso que se les ha dado.</li>
        <li>Revocar la autorización o pedir que se supriman, cuando no exista un deber legal o contractual de conservarlos.</li>
        <li>Acceder gratuitamente a sus datos.</li>
        <li>Presentar quejas ante la Superintendencia de Industria y Comercio, después de haber acudido a la firma.</li>
      </ul>

      <h2>6. Cómo ejercerlos</h2>
      <p>Escriba a {{ d.correoDatos }} indicando su nombre, su documento, lo que pide y un medio para responderle.</p>
      <ul>
        <li><strong>Consultas:</strong> se responden en máximo 10 días hábiles, prorrogables 5 días hábiles más avisándole el motivo.</li>
        <li><strong>Reclamos</strong> (corregir, actualizar, suprimir o revocar): se responden en máximo 15 días hábiles,
          prorrogables 8 días hábiles más avisándole el motivo. Si el reclamo está incompleto, se le pedirá completarlo en 5 días.</li>
      </ul>
      <p>Desde «Mi cuenta» cada persona puede además cambiar su contraseña, activar la verificación en dos pasos y quitar las
        cuentas externas enlazadas.</p>

      <h2>7. Dónde están los datos y quién más los trata</h2>
      <ul>
        <li>{{ d.producto }} funciona en un servidor propio de la firma. Se accede solo por una red privada cifrada; no está
          publicado en internet.</li>
        <li>El asistente de redacción con inteligencia artificial funciona en ese mismo servidor: los datos de los procesos no se
          envían a servicios externos de IA.</li>
        <li><strong>Telegram:</strong> solo si usted vincula su cuenta para recibir avisos. En ese caso, los avisos (por ejemplo,
          el radicado y la actuación de un proceso o un término por vencer) y, si usted es del equipo de la firma, las respuestas
          a las consultas que haga al bot sobre sus propios procesos, se envían por Telegram y quedan en sus servidores,
          regidos por su propia política. Puede desvincularlo cuando quiera desde «Avisos».</li>
        <li><strong>Google, Microsoft o GitHub:</strong> solo si usted decide entrar con ellos. En ese caso, usted inicia sesión en
          su página, regida por su propia política, y nos devuelve un identificador de su cuenta y, si lo autoriza, su correo y
          nombre. No recibimos su contraseña de esos servicios.</li>
      </ul>

      <h2>8. Cómo los protegemos</h2>
      <ul>
        <li>Conexiones cifradas y acceso solo por la red privada de la firma.</li>
        <li>Contraseñas guardadas con un algoritmo de un solo sentido (no se pueden leer), bloqueo por intentos fallidos y
          verificación en dos pasos.</li>
        <li>Acceso por roles: cada persona ve solo lo que necesita para su trabajo, y el cliente solo ve sus propios asuntos.</li>
        <li>Registro de los ingresos y de cada acción en una bitácora de auditoría que nadie puede modificar ni borrar
          (cada registro queda encadenado al anterior), y copias de respaldo cifradas.</li>
      </ul>

      <h2>9. Cuánto tiempo los conservamos</h2>
      <p>Mientras dure la relación con el cliente y, después, durante los plazos que exigen las normas aplicables (por ejemplo,
        las de conservación de documentos contables) o los necesarios para atender reclamaciones: el expediente de cada
        proceso y sus documentos, diez (10) años después de terminado el proceso; los datos de contacto de quien solo hizo
        una consulta inicial y no contrató, dos (2) años.</p>
      <p>La bitácora de auditoría no se borra por partes: su valor como prueba de quién hizo qué depende de que esté
        completa. Por eso los registros de acceso no se suprimen a solicitud, por existir el deber de mantener la seguridad
        y la trazabilidad de la información. La bitácora se conserva cinco (5) años.</p>

      <h2>10. Cookies</h2>
      <p>El sistema solo usa las cookies y el almacenamiento del navegador necesarios para funcionar y recordar sus
        preferencias. El detalle está en la <a routerLink="/cookies">política de cookies</a>.</p>

      <h2>11. Cambios a esta política</h2>
      <p>Cualquier cambio importante se avisará en el sistema antes de aplicarse. Esta es la versión {{ d.version }},
        vigente desde {{ d.vigenteDesde }}.</p>
    </app-pagina-legal>
  `
})
export class PrivacidadComponent {
  readonly d = DATOS_LEGALES;
}
