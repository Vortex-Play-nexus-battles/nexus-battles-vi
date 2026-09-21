# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Ámbito: `services/cuentas/ms-finanzas/`. Complementa el `CLAUDE.md` de la raíz
y los `.claude/rules/`, **no los reemplaza**. Leer primero la advertencia de
abajo.

## ⚠️ El CLAUDE.md de la raíz es de otro equipo

El `CLAUDE.md` de la raíz está escrito íntegramente para **Equipo 6 / Grupo 6
(Simón — Tiempo Real, Comunidad y Plataforma)**: su tabla de integrantes, su
Sprint 1, sus HU (`HU-COR`, `HU-SAL`, `HU-NOT`, `HU-CICD`, `HU-REN`, `HU-DIS`)
y sus bloqueadores. Lo mismo `.claude/rules/project-charter.md`, que es el
Project Charter del Equipo 6 y **excluye pagos y créditos explícitamente** de
su alcance ("No se implementa, aunque se consuma: ... pagos, subastas, ...").

`ms-finanzas` pertenece al **equipo de Cuentas y Economía (grupo-4, Santiago
y Juan Diego)** — M07, Pagos, créditos y monetización. Ese equipo no aparece
en el `CLAUDE.md` de la raíz. Al trabajar aquí:

- **Sí aplican** de la raíz: las 12 reglas de plataforma, el flujo de ramas,
  la estructura del monorepo, la pila tecnológica, y
  `.claude/rules/backend-spring.md`.
- **No aplican**: la tabla de integrantes, el Sprint 1 de Grupo 6, sus
  bloqueadores, y las exclusiones de alcance del charter de Equipo 6.
- La fuente de verdad del backlog son los **Issues de GitHub**, no los
  `.docx`. Cualquier backlog en documento es una foto de un momento anterior.

## Estado del servicio

Skeleton — Sprint 2. Solo tiene `MsFinanzasApplication` y el `build.gradle`.
Ninguna HU implementada. Las HU objetivo (HU-PAG-001, HU-PAG-002, HU-JUE-012)
y los endpoints previstos están en `README.md`.

## Comandos

Siempre desde la **raíz del monorepo**: este servicio es un módulo del build
raíz y no trae wrapper propio (`guardia-monorepo.yml` falla el PR si un
servicio trae su propio Gradle).

```bash
./gradlew :services:cuentas:ms-finanzas:test     # solo pruebas
./gradlew :services:cuentas:ms-finanzas:check    # pruebas + (en el futuro) compuerta JaCoCo del 80 %
./gradlew :services:cuentas:ms-finanzas:build    # jar en build/libs/
./gradlew :services:cuentas:ms-finanzas:bootRun  # arranca el servicio
```

Informe de cobertura: `services/cuentas/ms-finanzas/build/reports/jacoco/test/html/index.html`.

CI: la cubre `ci.yml` de la raíz, que detecta los servicios modificados y
compila cada uno con su herramienta. Este servicio **no lleva workflow
propio**; sería duplicar lo que ya hace `ci.yml`.

## Compuerta de calidad — pendiente de activar

`nexus.spring-conventions` deja declarada la regla del 80 % (JaCoCo
`jacocoTestCoverageVerification`) pero **no** la engancha a `check` por
defecto. En este servicio, mientras sea skeleton la dejamos también
desactivada (ver el bloque comentado al final de `build.gradle`); en cuanto
llegue el primer test real de una HU se descomenta y a partir de ahí el 80 %
es obligatorio, como en ms-subastas.

## Cortacircuitos hacia ms-finanzas (ya declarado en ms-subastas)

`ms-subastas` invoca `/creditos/reservar` **dentro del lock pesimista** de la
subasta. Eso significa que la latencia de este servicio serializa todas las
pujas de una misma subasta. `ms-subastas` ya declaró un `CircuitBreaker` y un
`Retry` con el instance `creditos` (ver su `application.properties`), pero es
responsabilidad de este servicio **acordar un SLA de latencia** con Andrés
(HU-SUB-004) antes de que las HU económicas se demuestren en el Sprint
Review.

## Aislamiento de la base de datos (regla 7)

Este servicio tiene su **propia base de datos** `finanzas_db`, en un
contenedor Postgres aislado (`services/cuentas/ms-finanzas/docker-compose.yml`,
puerto host 5436 para no chocar con `plataforma-db` en 5432 ni con
`db-subastas` en 5435). Nunca acceder a la BD de otro servicio; toda
integración es por REST o por evento.

## Deuda técnica reconocida en este skeleton

- **R5/R6 (trace id y logs JSON):** no implementados. El `application.properties`
  sale sin `logback-spring.xml` estructurado. Cuando exista una biblioteca
  compartida de logging JSON en `shared/libs/`, adoptarla aquí.
- **RFC 7807 (regla 4):** activado a nivel de Spring con
  `spring.mvc.problem-details.enabled=true`, pero cada `@ControllerAdvice`
  concreto del servicio se escribirá cuando existan controllers.
- **Compuerta JaCoCo 80 % (regla 11):** desactivada mientras no haya código
  de negocio ni tests.

## Decisiones abiertas — no rellenar el hueco

`backend-spring.md` y el charter son explícitos: ante una decisión pendiente,
**parar y preguntar, no asumir**. Pendientes conocidos ahora:

- **HU-PAG-001**: umbral de "compra de alto valor" (`RF-PAG-006`) sigue POR
  DEFINIR. Mientras tanto, alto valor se marca para revisión manual en vez
  de exigir 2FA (HU-AUT-007 aplazada a Sprint 3).
- **HU-JUE-012 (RF-JUE-013)**: contenido y probabilidades del cofre, y día
  de reinicio de la semana, sin definir por el Product Owner.
