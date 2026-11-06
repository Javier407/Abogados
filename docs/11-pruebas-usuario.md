# Pruebas con usuarios (fase F16)

## Qué es el *user testing*

Las pruebas con usuarios ponen a **personas reales** a hacer **tareas reales** con el sistema mientras alguien observa sin ayudar. No se pregunta "¿le gusta?": se mide si logran la tarea, cuánto tardan y dónde se equivocan. Encuentran lo que ninguna prueba automática ve: un botón que nadie encuentra, una palabra que un abogado entiende distinto o un paso que sobra.

Se apoya en dos ideas clásicas:
- **Jakob Nielsen (Nielsen Norman Group):** con **5 participantes** aparece la mayoría de los problemas de usabilidad de un perfil de usuario. Rinde más hacer varias rondas pequeñas que una grande.
- **Pensar en voz alta** (*think aloud*): el participante dice lo que piensa mientras trabaja. Así se entiende **por qué** se equivoca.

La usabilidad es la "capacidad de interacción" de la ISO/IEC 25010. Este protocolo da sus métricas: eficacia, eficiencia y satisfacción, como las define la ISO 9241-11.

## Protocolo

**Participantes:** 5 por perfil:
- abogados o socios;
- dependientes judiciales;
- clientes, con las tareas del portal.

Deben ser personas que no hayan visto la app.

**Antes de empezar:**
- Explicar que se evalúa el sistema, no a la persona.
- Pedir permiso para tomar notas, o grabar la pantalla si aceptan.
- Usar las cuentas de prueba, nunca datos reales de clientes.

**Tareas (abogado y dependiente):**

| # | Tarea (se lee tal cual) | Éxito si… | Tiempo meta |
|---|---|---|---|
| T1 | "Registre el proceso con radicado 11001-31-03-032-2024-00999-00, cliente Constructora Altamira." | El proceso queda creado con cliente y despacho correctos | 3 min |
| T2 | "¿Qué términos vencen esta semana y cuál es el más urgente?" | Nombra el término correcto | 1 min |
| T3 | "Emita la factura de honorarios del mes para Inversiones Andina." | La factura queda emitida (CUFE visible) | 3 min |
| T4 | "Active la verificación en dos pasos en su cuenta." | Queda activa y guardó los códigos de recuperación | 4 min |
| T5 | "Pida al asistente un borrador de contestación para el proceso de Altamira y dígame qué revisaría antes de radicarlo." | Obtiene el borrador y menciona revisar lo marcado o el aviso de IA | 6 min |

**Tareas (cliente, portal):** ver el estado de su proceso, leer el último resumen y encontrar su próxima audiencia.

**Durante la prueba:**
- No ayudar.
- Si la persona se bloquea más de 2 minutos, anotar "falló" y pasar a la siguiente tarea.

**Al final:** el cuestionario SUS y dos preguntas abiertas:
- ¿Qué fue lo más difícil?
- ¿Qué cambiaría primero?

## Métricas

| Métrica | Cálculo | Meta |
|---|---|---|
| Eficacia | Tareas logradas / tareas intentadas | ≥ 80 % por tarea |
| Eficiencia | Tiempo por tarea (mediana) frente al tiempo meta | ≤ tiempo meta |
| Errores | Acciones equivocadas o callejones sin salida por tarea | ≤ 1 en promedio |
| Satisfacción | Puntaje **SUS** (System Usability Scale, John Brooke, 1986) | ≥ 68 (promedio de la industria); ≥ 80 se considera bueno |

**SUS:** 10 afirmaciones, respondidas de 1 (muy en desacuerdo) a 5 (muy de acuerdo).

1. Creo que me gustaría usar este sistema con frecuencia.
2. Encontré el sistema innecesariamente complejo.
3. Pensé que el sistema era fácil de usar.
4. Creo que necesitaría ayuda de una persona técnica para poder usarlo.
5. Las funciones del sistema estaban bien integradas.
6. Había demasiadas inconsistencias en el sistema.
7. Imagino que la mayoría aprendería a usarlo muy rápido.
8. El sistema me pareció muy engorroso de usar.
9. Me sentí muy seguro usando el sistema.
10. Tuve que aprender muchas cosas antes de poder usarlo.

**Puntaje:**
- En las afirmaciones impares se resta 1 a la respuesta.
- En las pares, la respuesta se resta de 5.
- Se suman los 10 valores y la suma se multiplica por 2,5.
- El resultado va de 0 a 100.

## Plantilla de resultados

| Participante | Perfil | T1 | T2 | T3 | T4 | T5 | SUS | Lo más difícil |
|---|---|---|---|---|---|---|---|---|
| P1 | | ✔ / ✘ · tiempo | | | | | | |
| P2 | | | | | | | | |
| P3 | | | | | | | | |
| P4 | | | | | | | | |
| P5 | | | | | | | | |
| **Resumen** | | % éxito · mediana | | | | | promedio | |

Cada problema se registra con:
- la tarea donde ocurrió;
- cuántos participantes lo tuvieron;
- su gravedad, de 1 (cosmético) a 4 (impide la tarea);
- el cambio propuesto.

Se corrigen primero los de gravedad 3 y 4 que tuvieron 2 o más personas.

## Estado

**Pendiente de ejecutar.** Las sesiones las hace Javier con personas de la firma. Este documento queda como guion y plantilla. Al tener los resultados, se completa la tabla y se actualiza la "capacidad de interacción" en `docs/04`.
