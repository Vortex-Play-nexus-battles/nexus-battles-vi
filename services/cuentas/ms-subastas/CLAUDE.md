# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Ámbito: `services/cuentas/ms-subastas/`. Complementa el `CLAUDE.md` de la raíz y los
`.claude/rules/`, **no los reemplaza**. Leer primero la advertencia de abajo.

## ⚠️ El CLAUDE.md de la raíz es de otro equipo

El `CLAUDE.md` de la raíz está escrito íntegramente para **Equipo 6 / Grupo 6 (Simón — Tiempo Real,
Comunidad y Plataforma)**: su tabla de integrantes, su Sprint 1, sus HU (`HU-COR`, `HU-SAL`,
`HU-NOT`, `HU-CICD`, `HU-REN`, `HU-DIS`) y sus bloqueadores. Lo mismo
`.claude/rules/project-charter.md`, que es el Project Charter del Equipo 6 y **excluye subastas
explícitamente** de su alcance ("No se implementa, aunque se consuma: ... pagos, subastas, ...").

`ms-subastas` pertenece al **equipo de Cuentas y Economía (grupo-4, Santiago)** — M12, Subasta. Ese
equipo no aparece en el CLAUDE.md de la raíz. Al trabajar aquí:

- **Sí aplican** de la raíz: las 12 reglas de plataforma, el flujo de ramas, la estructura del
  monorepo, la pila tecnológica, y `.claude/rules/backend-spring.md`.
- **No aplican**: la tabla de integrantes, el Sprint 1 de Grupo 6, sus bloqueadores, y las
  exclusiones de alcance del charter de Equipo 6.
- La fuente de verdad del backlog son los **Issues de GitHub**, no los `.docx`. Cualquier backlog
  en documento es una foto de un momento anterior.

## Comandos

Siempre desde la **raíz del monorepo**: este servicio es un módulo del build raíz y no trae
wrapper propio (`guardia-monorepo.yml` falla el PR si un servicio trae su propio Gradle).

```bash
./gradlew :services:cuentas:ms-subastas:test     # solo pruebas
./gradlew :services:cuentas:ms-subastas:check    # pruebas + compuerta JaCoCo del 80 % (rompe el build)
./gradlew :services:cuentas:ms-subastas:test --tests '*MotorPujasServiceTest'
./gradlew :services:cuentas:ms-subastas:test --tests '*MotorPujasServiceTest.pasadosLos5sElMismoJugadorPuedeVolverAPujar'
```

Informe de cobertura: `services/cuentas/ms-subastas/build/reports/jacoco/test/html/index.html`.

CI: la cubre `ci.yml` de la raíz, que detecta los servicios modificados y compila cada uno con su
herramienta. Este servicio **no lleva workflow propio**; sería duplicar lo que ya hace `ci.yml`.

Nota sobre Maven: `ms-identidad`, `ms-cumplimiento` y `ms-ecommerce` siguen en Maven, pero son
excepciones heredadas. `guardia-monorepo.yml` tiene esa lista blanca y dice que **solo puede
encoger, nunca crecer**: un `pom.xml` nuevo bajo `services/` falla el PR. Todo servicio nuevo va en
Gradle, como módulo declarado en el `settings.gradle` de la raíz.

## Arquitectura: por qué el dominio no conoce la base de datos

El motor de pujas está deliberadamente separado de la persistencia, porque HU-SUB-004 depende de dos
piezas que otras personas construyen en paralelo (la tabla `Subastas` de HU-SUB-001 y el endpoint de
créditos de `ms-finanzas`). La separación permite desarrollar y probar las reglas sin esperarlas.

```
PujaApplicationService   ← @Transactional + lock pesimista. Lee de los repos, arma el contexto.
  └─ MotorPujasService   ← reglas de negocio puras. No conoce repos ni HTTP.
       └─ CreditoClient  ← interfaz hacia ms-finanzas (reservar/liberar/consumir/saldo)
```

**`MotorPujasService` recibe todo por parámetro** (la `Subasta`, la puja vigente, un
`ContextoParticipacion` con los contadores ya resueltos) y solo muta esos objetos. No se sincroniza
a sí mismo: hacerlo serializaría pujas de subastas distintas sin necesidad.

**El guardia de concurrencia es `SubastaRepository.findByIdParaActualizar`** (`SELECT ... FOR UPDATE`),
tomado por `PujaApplicationService` antes de leer la oferta vigente. Eso hace que
"leer → validar → escribir" sea atómico entre procesos y réplicas. Más un índice único parcial en
la migración (`uq_pujas_una_vigente_por_subasta`) que garantiza la invariante "una sola puja vigente
por subasta" incluso si la validación en Java fallara bajo carrera.

### Invariantes que hay que respetar al tocar este código

- **`java.time.Clock` se inyecta, nunca `Instant.now()`.** El intervalo mínimo de 5 s entre pujas es
  intestable si el reloj no es inyectable. Bean declarado en `MsSubastasApplication`.
- **La clave de idempotencia la provee el cliente**, nunca se deriva del reloj: un reintento por
  timeout generaría una clave distinta y reservaría los créditos dos veces.
- **En todo momento hay como máximo una puja `ACTIVA` por subasta**, y solo su dueño mantiene una
  reserva de créditos viva. A los postores anteriores se les liberó al ser superados. Cualquier
  código que cierre o adjudique una subasta solo tiene una reserva que liberar o consumir.
- **Nunca acceder a la BD de otro servicio.** Los créditos se piden por API a `ms-finanzas`; el saldo
  no se lee ni se escribe aquí.
- `Subasta` es un **borrador** pendiente del diseño conjunto con HU-SUB-001 (Edwin) y HU-SUB-011
  (Cristian). Puede cambiar; no construir nada que asuma que está congelada.

### La llamada a ms-finanzas ocurre dentro del lock

`creditoClient.reservar(...)` se invoca con el lock de la fila tomado, así que **la latencia de
ms-finanzas serializa todas las pujas de esa subasta**. Es el riesgo de rendimiento principal del
servicio. Hace falta timeout y cortacircuitos (Resilience4j, exigido por `backend-spring.md`) y
acordar un SLA de latencia con el dueño de `ms-finanzas`.

## Esquema

Solo por Flyway (`src/main/resources/db/migration/`), nunca `ddl-auto` — regla 8 de plataforma.
Los índices de `V1` no son decorativos: cada uno sostiene una regla concreta de la HU (intervalo de
5 s, topes de 10/50, una sola puja vigente). Leer los comentarios antes de modificarlos.

## Parámetros de negocio

Los 4 límites de participación viven en `ParametrosPuja` (`app.pujas.*`). Los valores numéricos
**no son ajustables libremente**: el Project Charter los fija ("límites y plazos son inalterables").
La HU pide que sean "configurables desde administración", lo que hoy solo se cumple por variable de
entorno — falta la superficie de administración real.

## Decisiones abiertas — no rellenar el hueco

`backend-spring.md` y el charter son explícitos: ante una decisión pendiente, **parar y preguntar,
no asumir**. Las asunciones ya tomadas están listadas en el `README.md` de este servicio (guerra
entre pujas automáticas resuelta de forma iterativa, saldo validado al configurar, anti-sniping no
implementado) y el valor por defecto del incremento mínimo sigue `POR DEFINIR` (RF-SUB-004).
Validarlas con el Product Owner antes de construir encima.
