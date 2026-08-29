Project Charter — restricciones y criterios que no dependen del equipo

ProjectCharterEquipo6NexusBattlesVI_1_0.pdf, v2.0. Esto lo aprobó el cliente (el docente mediador, actuando como Ingeniero Informático delegado de UPB-COMPANY) — no es negociable a nivel de equipo, a diferencia de otras decisiones que sí se pueden ajustar internamente.

Alcance formal del bloque — 6 módulos

M02 (Comentarios, moderación y sanciones) · M09 (Juego en línea y chat) · M13 (Torneo y transmisión) · M15 (Correo y notificaciones) · M16A (Infraestructura en la nube, red, datos, DR) · M16B (Integración y distribución continuas, calidad y pruebas).

49 RF propios + 19 reglas de negocio + 25 RNF coordinados con los otros dos equipos. Línea base: 286 puntos de historia, 574 horas.

Qué NO es responsabilidad de este equipo (exclusiones)

No se implementa, aunque se consuma: identidad y control de acceso, auditoría, privacidad, libro de créditos, pagos, subastas, motor de combate, efectos aleatorios, inventario, héroes, catálogo de productos, misiones — 26 RF ajenos referenciados por identificador, nunca implementados aquí. Si una tarea implica tocar algo de esta lista, es señal de que se salió del alcance — parar y preguntar, no improvisar la integración.

Sí corresponde: las interfaces que el bloque expone a esos módulos (sala de batalla y canal en tiempo real, notificaciones y correo, comentarios y calificación, lista negra, entornos y despliegue, CI/CD, configuración de parámetros) y las que consume de ellos.

También excluido del alcance: implementación completa de los servicios externos consumidos, pagos reales (pasarela simulada), apps móviles nativas, internacionalización más allá del bilingüismo del chatbot, reentrenamiento periódico del modelo de IA, y cualquier cambio a la línea base sin autorización del Product Owner.

Calendario de releases por Sprint
Sprint 1 (sem. 6–7, libera 28/ago, se demuestra 4/sep): plataforma común de los tres equipos
sala de batalla 1v1 con canal en tiempo real + correo con plantilla corporativa + lista negra. 23 requisitos comprometidos.
Sprint 2 (sem. 9–10, libera 18/sep, se demuestra 25/sep): juego en línea hasta 6 participantes, chat, barras de vida, apuesta de créditos, bus de notificaciones, comentarios. 26 requisitos.
Sprint 3 (sem. 12–13, libera 9/oct, se demuestra 16/oct): torneo con árbol doble y transmisión, moderación/sanciones/apelaciones, métricas, informes de latencia/carga/recuperación. 25 requisitos.
Integración final: 16/oct (sem. 14) · Sustentación: sem. 15–16 (fecha exacta por confirmar por el docente) · Cierre: 6/nov (sem. 17).
Definition of Done — un elemento NO está terminado si le falta uno solo de estos

a) Implementado conforme a su ficha oficial y a las reglas de negocio que lo condicionan b) Criterios de aceptación aprobados y verificados c) Código revisado por otro developer e integrado en la rama principal d) Pruebas unitarias (escritas ANTES del código) ejecutadas y en verde, cobertura ≥80% e) Pruebas de integración ejecutadas contra el proveedor real o contra su doble f) Sin defectos críticos ni bloqueantes abiertos g) RNF aplicables verificados con su métrica h) Seguridad y permisos por rol comprobados i) Trazabilidad y documentación técnica/funcional actualizadas j) Desplegado en el ambiente acordado por el flujo automatizado — nunca a mano k) Evidencias adjuntas al acta l) Demostrado en la Sprint Review m) El Product Owner lo validó y lo aceptó

Lo que no cumpla todo esto no se libera ni se presenta — vuelve al Product Backlog.

Restricciones técnicas duras (no reinterpretar)
Valores numéricos de estadísticas, costos, efectos, comisiones, límites y plazos son inalterables — no son parámetros de ajuste libre para el desarrollador.
Comunicación en tiempo real cifrada en tránsito, nunca en texto claro.
Diseño para 99,95% de resiliencia; autoescalado al superar 75% de uso de procesador.
TDD con ciclo Red-Green-Refactor, cobertura mínima 80% — mismo umbral que las 12 reglas de plataforma de CLAUDE.md, viene de la misma fuente.
Riesgos con plan de mitigación directamente accionable
Riesgo #3 (contratos que cambian después de ser consumidos): el contrato se congela y versiona al inicio de cada Sprint. Si cambia a mitad de sprint, se aísla detrás de una capa de adaptación y se migra en el Sprint siguiente — no se reescribe todo de inmediato.
Riesgo #5 (un integrante se ausenta o se desvincula): revisión cruzada de código obligatoria para que el conocimiento no quede en una sola persona — nunca "solo yo entiendo este servicio".
Riesgo #6 (implementar sobre un requisito con una decisión pendiente): en el refinamiento, ningún elemento marcado como pendiente entra al Sprint Backlog. Si aparece un [P-0X] o una decisión sin tomar, Claude Code se detiene y pregunta — no asume ni rellena el hueco.
Riesgo #7 (latencia sobre 500ms o pérdida de conexión en tiempo real): medir desde Sprint 1; si falla, degradación controlada a consulta periódica con reconciliación de estado al restablecerse — nunca fallar en silencio.
