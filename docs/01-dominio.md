# 1. Dominio: gestión de procesos judiciales para firmas de abogados

> Fase 0 del proyecto. Este documento ordena las ideas antes de programar. Lo marcado como **(a validar)** debe confirmarlo un abogado antes de convertirse en regla del software.

## 1.1 El problema

Los avances de un proceso judicial (autos, sentencias, cambios de estado) **solo se pueden consultar entrando al portal de la Rama Judicial**, proceso por proceso. Los autos se notifican por **estado electrónico**: si el abogado no revisa a tiempo, se entera tarde y se le pueden vencer los **términos** para actuar.

## 1.2 La solución propuesta

Un software para firmas de abogados que:

1. **Hace seguimiento** de cada proceso por su número de radicado, desde el poder hasta la sentencia.
2. **Vigila la Rama Judicial** y detecta autos y sentencias nuevos sin entrar al portal.
3. **Notifica** al abogado por correo, dentro de la app y por Telegram.
4. **Controla los términos** en días hábiles, con calendario y alertas antes del vencimiento.
5. **Apoya la redacción** de demandas y contestaciones con un asistente de IA.
6. **Administra la firma:** abogados, clientes, peritos, tarifas, honorarios y pagos.

## 1.3 Glosario

| Término | Significado |
|---|---|
| **Rama / jurisdicción** | Especialidad del derecho a la que pertenece el proceso (civil, familia, laboral, pensional, penal, administrativo, constitucional). |
| **Tipo de proceso** | Procedimiento que se sigue dentro de una rama (verbal, ejecutivo, ordinario laboral…). |
| **Radicado** | Número de 23 dígitos que identifica el proceso en la Rama Judicial. Es la llave para consultarlo. |
| **Despacho** | Juzgado, tribunal o corte que conoce el proceso. |
| **Partes** | Demandante, demandado y terceros (llamados en garantía, intervinientes). |
| **Poder** | Documento con el que el cliente autoriza al abogado para representarlo. |
| **Actuación** | Cualquier paso del proceso: radicar la demanda, contestar, presentar un memorial, asistir a una audiencia. |
| **Providencia** | Decisión del juez. Puede ser un **auto** (impulsa el proceso y se notifica por estado) o una **sentencia** (decide el fondo y termina la instancia). |
| **Estado electrónico** | Publicación diaria del despacho con los autos que notifica. Es lo que hay que vigilar. |
| **Término** | Plazo para actuar, contado en días hábiles desde la notificación. |
| **Vacancia judicial** | Periodo en que no corren términos (fin de año y Semana Santa). |
| **Prueba** | Documental, pericial (dictamen de un perito), testimonial, interrogatorio, inspección o videos. |
| **Perito** | Experto que rinde un dictamen pericial. |
| **Honorarios** | Lo que cobra la firma: valor fijo, mensualidad, cuota litis (porcentaje del resultado) o mixto. |
| **Tipo de servicio** | Presencial, virtual o representación en todo el proceso. |

## 1.4 Ramas y tipos de proceso

Este catálogo se carga en las tablas `procesos.ramas` y `procesos.tipos_proceso`.

| Rama / jurisdicción | Norma principal | Tipos de proceso |
|---|---|---|
| Civil y comercial | Código General del Proceso (CGP) | Verbal, verbal sumario, ejecutivo, monitorio, divisorio, deslinde y amojonamiento, restitución de inmueble arrendado, sucesión |
| Familia | CGP | Divorcio contencioso, divorcio de mutuo acuerdo, alimentos, custodia y cuidado personal, filiación, liquidación de sociedad conyugal, sucesión |
| Laboral | Código Procesal del Trabajo y de la Seguridad Social (CPTSS) | Ordinario laboral de primera instancia, ordinario de única instancia, ejecutivo laboral, fuero sindical |
| Pensional (seguridad social) | CPTSS | Ordinario contra Colpensiones o AFP, reconocimiento de pensión, reliquidación de pensión |
| Penal | Ley 906 de 2004 | Indagación, imputación y acusación, juicio oral, procedimiento abreviado, incidente de reparación integral |
| Administrativo | Ley 1437 de 2011 (CPACA) | Nulidad y restablecimiento del derecho, reparación directa, controversias contractuales, nulidad simple |
| Constitucional (transversal) | Decreto 2591 de 1991 y Ley 472 de 1998 | Tutela, acción popular, acción de grupo |

## 1.5 El radicado

El número de radicación tiene **23 dígitos** y se escribe a menudo con guiones para leerlo mejor:

```
20011 - 31 - 05 - 001 - 2019 - 00320 - 01
└─┬─┘   └┬┘  └┬┘  └┬┘   └┬─┘   └─┬─┘   └┬┘
  │      │    │    │     │       │      └─ Recurso / instancia (2): 00 primera, 01 segunda…
  │      │    │    │     │       └──────── Consecutivo del año (5)
  │      │    │    │     └──────────────── Año de radicación (4)
  │      │    │    └────────────────────── Despacho (3)
  │      │    └─────────────────────────── Especialidad (2)
  │      └──────────────────────────────── Entidad o corporación (2)
  └─────────────────────────────────────── Departamento (2) + municipio (3), código DANE
```

- **Ejemplo real:** `20011-31-05-001-2019-00320-01`. En primera instancia fue `…-00`; al apelar solo cambia el último bloque a `01`.
- **Reglas del software:**
  - Se guarda sin guiones y se valida con `^\d{23}$`.
  - Al registrar un proceso se desglosan los componentes para mostrarlos y filtrarlos.
  - Una segunda instancia es el mismo radicado base con otro sufijo, así que se puede enlazar con su primera instancia.
- **(a validar):** los códigos de entidad y especialidad deben confirmarse con las tablas oficiales del Consejo Superior de la Judicatura. En la base de pruebas se usan códigos de ejemplo.

## 1.6 Etapas de un proceso

```
Poder → Demanda → Admisión → Notificación → Contestación → Pruebas → Audiencias → Sentencia → Recursos
```

| Etapa | Qué pasa | Qué registra el software |
|---|---|---|
| 1. Poder | El cliente (demandante o demandado) autoriza al abogado | Actuación tipo PODER, documento |
| 2. Demanda | Se radica la demanda; el despacho asigna radicado | Proceso con su radicado; actuación DEMANDA |
| 2.1 Contestación | El demandado contesta dentro del término | Término de contestación; actuación CONTESTACION |
| 3. Providencias | El juez emite autos de avance (admite, decreta pruebas, fija audiencia) | Providencia AUTO con fecha de estado; los términos que origina |
| 4. Sentencia | El juez decide y termina la instancia | Providencia SENTENCIA; cambio de estado del proceso |
| 5. Pruebas | Dictamen pericial, anexos, videos, testimonios | Prueba con su tipo y, si es pericial, el perito |
| Transversal | Términos y calendario | Términos con vencimiento en días hábiles y alertas |

### Términos de referencia (a validar)

| Actuación | Término de referencia |
|---|---|
| Contestar demanda en proceso verbal (CGP) | 20 días hábiles |
| Contestar demanda en verbal sumario (CGP) | 10 días hábiles |
| Contestar demanda en ordinario laboral (CPTSS) | 10 días hábiles |
| Apelar un auto (CGP) | 3 días hábiles |
| Impugnar un fallo de tutela | 3 días hábiles |

Los términos se cuentan en **días hábiles** desde el día siguiente a la notificación. No corren en fines de semana, festivos ni vacancia judicial (aprox. 20 de diciembre a 10 de enero y Semana Santa).

## 1.7 Tarifas de honorarios de referencia (Conalbos 2026)

Son **orientativas, no obligatorias**: los honorarios se pactan libremente entre abogado y cliente. Se cargan en `gabinete.tarifas` como referencia, junto con la tarifa propia de la firma.

SMMLV 2026 usado por las fuentes: **$1.750.905** (verificar con el decreto oficial).

| Servicio | Referencia | Valor aprox. 2026 |
|---|---|---|
| Consulta verbal | 0,5 SMMLV | $875.453 |
| Consulta escrita | 1 SMMLV | $1.750.905 |
| Concepto jurídico elaborado | 2 SMMLV (base) | $3.501.810 |
| Proceso de mínima cuantía | 20 % del valor del proceso | — |
| Proceso de menor cuantía | 15 % del valor del proceso | — |
| Proceso de mayor cuantía | 10 % del valor del proceso | — |
| Restitución de inmueble arrendado | 2 SMMLV + 15 % de los cánones de un año | desde $3.501.810 |
| Sucesión ante juzgado | Escalonado, desde 15 % del primer tramo | — |
| Sucesión ante notaría | 50 % de la tarifa ante juzgado | — |
| Divorcio contencioso | 5 SMMLV | $8.754.525 |
| Divorcio de mutuo acuerdo | 3 SMMLV | $5.252.715 |
| Separación de bienes / liquidación de sociedad conyugal | 2 SMMLV + porcentaje según patrimonio | desde $3.501.810 |
| Alimentos | 20 % de la cuota alimentaria de un año | — |
| Laboral: representar al trabajador | Cuota litis hasta 30 % de lo obtenido | — |
| Laboral: defender al empleador (1.ª instancia) | Desde 3 SMMLV | desde $5.252.715 |
| Constitución de sociedad anónima | 10 SMMLV | $17.509.050 |
| Penal: asistencia a indagatoria | 1 a 3 SMMLV | $1.750.905 – $5.252.715 |
| Penal: defensa en primera instancia | 5 a 10 SMMLV | $8.754.525 – $17.509.050 |
| Penal: actuación ante la Corte Suprema (casación) | 30 SMMLV | $52.527.150 |

### Formas de cobro
- **Valor fijo** por actuación (consulta, poder, demanda, contestación, recurso).
- **Mensualidad** por seguimiento del proceso.
- **Cuota litis:** porcentaje del resultado; solo se cobra al terminar el proceso.
- **Mixto:** valor inicial más porcentaje del resultado.
- **Tipo de servicio:** presencial, virtual o representación completa (la firma define si el virtual tiene descuento).

**Fuentes:**
- [Valora Analitik — Tarifas de abogados en Colombia 2026](https://www.valoraanalitik.com/?p=552776)
- [cuantomecuesta.com — Tarifas Conalbos 2026](https://cuantomecuesta.com/co/abogado/)
- El documento de Scribd "Tarifas Abogados Colombia 2026" (Conalbos / LitiApp) no se pudo leer sin sesión. Conviene contrastar las cifras con el tarifario oficial de Conalbos.

## 1.8 Organización de una firma (modelo usado en la base de pruebas)

| Cargo | Qué hace en el software |
|---|---|
| Socio | Dirige un área de práctica; ve todos los procesos de su área y los honorarios. |
| Asociado | Responsable de procesos; redacta y asiste a audiencias. |
| Abogado junior | Apoya procesos bajo supervisión de un asociado. |
| Dependiente judicial | Revisa estados, radica memoriales y hace vueltas en los despachos. |
| Paralegal | Organiza expedientes, pruebas y documentos. |
| Secretaria | Agenda, atención a clientes, registro de consultas. |
| Contador | Honorarios, facturación y pagos. |

La firma tiene **sedes** y **áreas de práctica** (una por rama). Cada proceso tiene un **abogado responsable** y puede tener abogados de **apoyo**.

## 1.9 Preguntas abiertas

1. ¿Hay una forma oficial y autorizada de consultar la Rama Judicial de manera automática? (ver ADR 0005)
2. ¿El cliente también recibe notificaciones o solo el abogado?
3. ¿El asistente de IA solo sugiere borradores, o también resume autos? ¿Quién revisa lo que genera?
4. ¿Los valores de tarifas se manejan en pesos o en SMMLV?
5. ¿Se facturan honorarios desde el software o solo se registran?
