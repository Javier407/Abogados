# Pruebas de carga con k6 y JMeter (fase F16)

## Qué son

Una **prueba de carga** simula muchas personas usando el sistema al mismo tiempo. Mide cuánto tarda en responder y si empieza a fallar. Responde la meta pendiente de la evaluación ISO 25010 (`docs/04`, eficiencia de desempeño): **50 usuarios concurrentes**.

| | **k6** (Grafana Labs) | **Apache JMeter** |
|---|---|---|
| Cómo se escribe | Un script en JavaScript (`perf/k6/carga-gateway.js`) | Un plan XML armado en su interfaz gráfica (`perf/jmeter/carga-gateway.jmx`) |
| Motor | Go: muchos usuarios virtuales con poca memoria | Java: un hilo por usuario |
| Criterio de éxito | `thresholds` en el script: la corrida falla si no se cumplen | Aserciones por respuesta e informe HTML |
| Para qué sirve más | Automatizar, guardar en git y correr en CI | Explorar, protocolos variados, equipos que prefieren lo gráfico |

Usamos **los dos con el mismo escenario** para comparar las herramientas.

## Escenario

- **Destino:** el gateway (`127.0.0.1:8091`), desde la propia VM y por la red del host. Así se mide el sistema y no la red Tailscale.
- **Usuario:** una cuenta interna sin verificación en dos pasos (rol DEPENDIENTE). Las credenciales llegan por las variables de entorno `K6_USUARIO` y `K6_CLAVE`, nunca escritas en los archivos.
- **Un solo login** al inicio (`setup()` en k6, *setUp Thread Group* en JMeter), y todos los usuarios virtuales comparten el token. Hay una razón: 50 logins seguidos superarían el límite de 100 intentos por IP. Por el enmascaramiento (SNAT) de Tailscale todos llegan con la misma IP, y eso es justamente el caso de abuso 1 de `docs/07`.
- **Recorrido de cada usuario:** consulta la lista de **procesos**, la de **términos** y la de **clientes**, y "lee" entre 1 y 3 segundos.
- **Perfil:**
  - k6: rampa a 10 usuarios en 30 s, a 50 en 30 s, 2 min sostenido en 50 y bajada en 30 s.
  - JMeter: 50 hilos que arrancan en 60 s, durante 3 min. Desde F17 la pausa de lectura va una vez por recorrido, como en k6 (ver abajo).
- **Umbrales (k6):** el 95 % de las respuestas en menos de **800 ms** (en total y por pantalla) y menos de **1 %** de errores.

## Cómo correrla

En la VM, desde `~/proyectos/abogados`. Son contenedores de una sola corrida (`--rm`) con imágenes de versión fija: `grafana/k6:1.3.0` y `justb4/jmeter:5.5`. No queda ningún servicio encendido.

```bash
export K6_USUARIO=<usuario de prueba> K6_CLAVE=<su clave>
perf/correr.sh k6
perf/correr.sh jmeter
```

Los resultados quedan en `perf/resultados/` (fuera de git): el resumen de k6 en JSON, y el `.jtl` y el informe HTML de JMeter.

No va en el CI de GitHub, porque necesita el sistema completo levantado y con datos.

## Resultados (2026-10-27, VM de 4 vCPU y 9,6 GB, sistema completo en marcha)

**k6:** 10 213 peticiones en 3 min 30 s (48 por segundo), **0 errores**. Todos los umbrales se cumplen.

| Pantalla | Mediana | p(90) | p(95) | Máximo | Umbral p(95) |
|---|---|---|---|---|---|
| Procesos | 52 ms | 201 ms | 269 ms | 880 ms | < 800 ms ✔ |
| Términos | 125 ms | 352 ms | 457 ms | 1,1 s | < 800 ms ✔ |
| Clientes | 41 ms | 105 ms | 132 ms | 425 ms | < 800 ms ✔ |
| **Total** | **63 ms** | **244 ms** | **326 ms** | 1,1 s | < 800 ms ✔ |

**JMeter:** 3 669 peticiones en 3 min 5 s (20 por segundo), **0 errores**.

| Pantalla | Mediana | p(95) |
|---|---|---|
| Procesos | 16 ms | 40 ms |
| Términos | 42 ms | 91 ms |
| Clientes | 14 ms | 33 ms |
| **Total** | **20 ms** | **66 ms** |

## Lectura

- **Se cumple la meta.** Con 50 personas consultando a la vez, el 95 % de las respuestas llega en menos de medio segundo y no hay errores.
- **Los tiempos de JMeter son más bajos que los de k6 porque generó menos carga, no porque el sistema fuera más rápido.** Su temporizador espera de 1 a 3 s después de **cada** petición, mientras que k6 espera una vez por recorrido de tres pantallas. Con 20 peticiones por segundo frente a 48, el sistema está más holgado. Es una lección práctica: dos herramientas con "el mismo escenario" solo son comparables si generan la misma tasa de peticiones.
- **Términos era la pantalla más pesada.** Calcula el semáforo de vencimientos de cada término. Se optimizó en F17 (ver abajo).
- **Hubo picos aislados de 1 a 3 s al arrancar,** con la JVM todavía en calentamiento.
- **Fuera del alcance:** el asistente de IA, que por diseño atiende un borrador a la vez (ver `docs/08`).

## F17: optimización de la pantalla de términos (2026-11-06)

**Causa.** La vista `procesos.v_terminos` llamaba dos veces por término a `dias_habiles_entre`: una para los días restantes y otra para el semáforo. Esa función generaba cada día del intervalo y consultaba `festivos` por cada uno. En un término vencido hace meses son cientos de consultas por fila.

**Cambio.** Migración `db/migraciones/013-f17-dias-habiles-rapidos.sql`:
- **Cálculo directo:** 5 hábiles por semana completa, más los días sueltos del final que caen de lunes a viernes, menos los festivos de lunes a viernes del intervalo. Los festivos se buscan con una consulta por rango sobre su llave primaria.
- **Una sola llamada:** la vista calcula los días restantes una vez por término, con un `LATERAL`, y el semáforo reutiliza ese valor.

**Mismo resultado comprobado.** Dentro de una transacción deshecha al final:
- la función vieja y la nueva dieron lo mismo en **26 078 pares de fechas**, de diciembre de 2023 a enero de 2027 y con intervalos de 0 a 400 días, con **0 diferencias**;
- los 19 términos reales conservan sus días y su situación.

**Resultado** (k6, 50 usuarios, mismo escenario):

| Medida | F16 | F17, VM recién encendida | F17, sistema caliente |
|---|---|---|---|
| Consulta de la lista en la base | 45 ms | 5 ms | 5 ms |
| Términos: mediana / p95 | 125 / 457 ms | 74 / 383 ms | **27 / 116 ms** |
| Clientes: p95 (sin cambios de código) | 132 ms | 319 ms | 63 ms |
| Total: p95 | 326 ms | 352 ms | 87 ms |
| Errores | 0 % | 0 % | 0 % |

**Lectura honesta.** Parte de la diferencia entre corridas viene del estado de la VM:
- **Con la VM recién encendida,** la JVM está en calentamiento y todas las pantallas salen más lentas, también las que no cambiaron.
- **La comparación justa es contra las demás pantallas:** antes, términos tardaba **3,5 veces** lo que clientes (p95), y ahora **1,8 veces**.
- **La consulta en la base** pasó de 45 ms a 5 ms.

## F17: JMeter con la misma tasa que k6 (2026-11-06)

**Causa.** En F16, el temporizador de lectura de JMeter estaba al nivel de las peticiones. JMeter aplica un temporizador así antes de **cada** petición del grupo: cada recorrido esperaba tres veces y salían 20 peticiones por segundo, frente a 48 de k6.

**Cambio.** El temporizador ahora va dentro de un *Flow Control Action* (pausa) al final del recorrido, que JMeter no registra como petición. Así espera de 1 a 3 s **una vez** por recorrido, igual que el `sleep()` de k6.

**Resultado** (las dos corridas una tras otra, con el sistema caliente y la optimización de términos aplicada):

| Medida | JMeter F16 | **JMeter F17** | **k6 F17** |
|---|---|---|---|
| Peticiones por segundo | 20 | **58** | **54** |
| Peticiones | 3 669 | 10 750 | 11 440 |
| Errores | 0 % | 0 % | 0 % |
| Procesos: mediana / p95 | 16 / 40 ms | 25 / 86 ms | 15 / 45 ms |
| Términos: mediana / p95 | 42 / 91 ms | 36 / 119 ms | 24 / 68 ms |
| Clientes: mediana / p95 | 14 / 33 ms | 22 / 73 ms | 12 / 35 ms |
| Total: mediana / p95 | 20 / 66 ms | 28 / 95 ms | 17 / 52 ms |

**Lectura.**
- **Ahora las dos generan la misma carga** (58 frente a 54 peticiones por segundo), y por eso los números ya son comparables.
- **Con más carga, los tiempos de JMeter subieron:** el p95 total pasó de 66 a 95 ms. Confirma lo que decía la lectura de F16: antes salía más rápido porque exigía menos.
- **JMeter sigue un poco por encima de k6.** Su perfil mantiene los 50 usuarios todo el tiempo, sin la rampa de bajada de k6. Además, el primer minuto tuvo un pico de 2,9 s, porque esa corrida fue la primera tras una hora con Ollama ocupando la CPU.
- **Las dos coinciden en lo importante:** 0 errores, todo muy por debajo del umbral de 800 ms, y términos sigue siendo la pantalla más lenta, pero ya en el mismo orden que las demás.
