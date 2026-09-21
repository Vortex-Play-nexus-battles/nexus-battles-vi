# Demostración del Sprint 2 — guion reproducible (Review 25-sep-2026)

Incremento comprometido en el Project Charter para el Sprint 2: **juego en
línea hasta seis participantes, chat, barras de vida, apuesta de créditos,
bus de notificaciones y comentarios**.

## Dónde se demuestra cada cosa, y por qué

| Entorno | Qué corre | Qué se demuestra allí |
|---|---|---|
| **AWS dev** (`http://35.168.124.119`, un `t3.small`) | borde nginx, ms-identidad, salas-partidas, comentarios, notificaciones, correo, moderación-sanciones, métricas, torneos, admin-parámetros | Registro y login reales, listado de salas por el borde, canal STOMP con JWT, comentarios, bandeja de notificaciones, correo de bienvenida (Mailpit), informe de disponibilidad, degradación controlada (`seccion-no-disponible`), reversión automática (acta en `SIMULACRO-REVERSION.md`). Lo comprueba el smoke `smoke-dev.yml` en cada despliegue. |
| **Banco E2E** (`tests/e2e/compose.yml`, en un portátil) | identidad, héroes, productos, inventario, motor-combate, **ms-finanzas**, salas-partidas y el **mismo** `borde-dev.conf` | El corte vertical completo de la batalla: héroe equipado → sala → segundo jugador → modalidad → partida → combate → barra de vida → final → **apuesta liquidada en el libro real**. |

El host de dev no puede correr la batalla porque `inventario` (MongoDB) y
`ms-finanzas` no caben en 2 GiB junto con lo que ya hay (#435, #430), y la
regla del bloque es costo cero y un solo host. No es una limitación del
producto sino del entorno de demostración; el banco E2E usa los mismos
contratos, los mismos jars y la misma configuración del borde.

## Evidencia automática

Cada corrida verde de `E2E de aceptación` (`.github/workflows/e2e.yml`) es un
acta: el spec `tests/e2e/zz-guion-de-demo.e2e.spec.js` recorre los doce pasos
de abajo **por la interfaz real** y deja en el artefacto `informe-e2e` la
carpeta `test-results/evidencia/` con una captura numerada por paso
(`02-login.png` … `12-chat-de-la-sala.png`) y `guion.json` con los datos
afirmados (ids, saldos antes y después, quién quedó en pie). Los demás specs
del mismo job afirman las reglas finas (28 escenarios: identidad, puerta de
héroe, código de invitación, modalidades, salir/cancelar, apuesta, degradación
con contenedores apagados).

## Cómo levantar el banco (5–8 min la primera vez)

```bash
# Desde la raíz del monorepo. Docker en marcha; JDK 21 y Node 22 instalados.
./gradlew -x test :services:contenido:heroes:bootJar :services:contenido:productos:bootJar \
          :services:contenido:inventario:bootJar :services:contenido:motor-combate:bootJar \
          :services:cuentas:ms-finanzas:bootJar :services:plataforma:salas-partidas:bootJar
(cd services/cuentas/ms-identidad && ./mvnw -B -DskipTests package)

docker compose -f tests/e2e/compose.yml up -d --build --wait
./tests/e2e/sembrar.sh          # cuentas, héroes equipados y 500 créditos a cada jugador
```

Con eso el juego está en `http://localhost:8099` (el borde). Para volver a
generar la evidencia en local:

```bash
cd frontend/app-web && npx playwright test --config=playwright.e2e.config.js zz-guion-de-demo
# capturas y guion.json en frontend/app-web/test-results/evidencia/
```

Para apagarlo: `docker compose -f tests/e2e/compose.yml down -v`.

## Guion (12 pasos, ~10 minutos)

Cuentas sembradas: `anfitriona_e2e`, `invitado_e2e`, `curioso_e2e` (con héroe
y 500 créditos) y `pobre_e2e` (con héroe, sin créditos). Clave de todas:
`Contrasena-E2E-2026`. Correo: `<apodo>@nexus.test`.

| # | Paso | Qué se hace | Qué se ve (y qué afirma la máquina) | HU |
|---|---|---|---|---|
| 1 | Banco levantado | `docker compose … ps`; abrir `http://localhost:8099/salud-borde` | Todos los contenedores `healthy`; el borde reparte | HU-POR-001 (#67) |
| 2 | Login | `…/cuentas/login.html` con `anfitriona_e2e` | Entra al inicio; el token lleva `uid` estable y apodo (ADR-002) | HU-AUT-004 (#50) |
| 3 | Héroe / inventario | `…/salas-partidas/validacion-heroe.html?sala=<id>` (o «Verificar héroe» en la sala) | «Guerrero…, listo para combatir»: el héroe viene del inventario real | HU-SAL-003 (#27) |
| 4 | Crear sala | `…/salas-partidas/crear-sala.html`: 2 jugadores, 60 créditos, 1 contra 1 | «Sala creada»; en el libro quedan 60 reservados a la anfitriona | HU-SAL-001 (#29), HU-JUE-014 (#442) |
| 5 | Segundo jugador | Otra ventana (o incógnito) con `invitado_e2e`: `batallas.html` → entrar | La tarjeta pasa a «2 de 2»; el invitado queda con 60 reservados | HU-SAL-002 (#30), HU-SAL-006 (#443) |
| 6 | Modalidad / IA | Con `curioso_e2e`, crear una sala «Contra la IA» | En el listado aparece «Con heroe de la IA» y ocupa cupo desde que nace; opcionalmente «Hasta seis» con equipos | HU-SAL-004 (#28) |
| 7 | Partida | Anfitriona: «Iniciar partida» | La vista de batalla muestra dos barras a tope, turno asignado, «Conectado» | HU-SAL-004 (#28), RF-JUE-017 |
| 8 | Combate | Quien tiene el turno pulsa «Atacar»; se alternan | El daño lo calcula motor-combate; la vida baja y llega por STOMP a las dos ventanas | HU-JUE-002 (#89), HU-JUE-003 (#92) |
| 9 | Barra de vida | Seguir atacando | La barra pasa a amarillo bajo el 60 % y a rojo bajo el 40 %, con el número al lado | HU-SAL-005 (#31) |
| 10 | Final | Último golpe | «Has ganado / has perdido»; los botones desaparecen | HU-JUE-005 (#94) |
| 11 | Apuesta / liquidación | `GET /api/v1/creditos/<uid>/saldo` con el token de cada uno (o `guion.json`) | Ganador: +60 y sin reserva; perdedor: −60 y sin reserva. Un jugador **no** puede mover el libro con su token (403, #455) | HU-JUE-014 (#442) |
| 12 | Chat / notificaciones | «Chat de la sala» desde la batalla; en dev, la campana de notificaciones | El mensaje llega por el canal y queda en el historial; la bandeja es del dueño del token | HU-JUE-015 (#33), HU-NOT-006 (#32) |

Si el motor falla los primeros golpes, no es un defecto: «Guerrero Tanque»
ataca 10+1d6 contra defensa 11, y un 1 en el dado falla. Se insiste.

## Lo que NO se muestra, y por qué (para no improvisar en la Review)

- **Créditos por jugar (RF-JUE-012)**: ms-finanzas ya acredita 2/4/1, pero
  salas-partidas todavía no le informa el resultado. Es el primer trabajo de
  R4 y se dirá así.
- **Subastas, tienda, cofres**: sin HU en GitHub y ms-subastas fuera del
  banco; en R2 entran al backlog y en R4 se diagnostica el 502.
- **Notificaciones de inicio/fin de partida**: no están en el SRS (D-06).

## Evidencias para el acta (DoD k)

1. Enlace al job «E2E de aceptación» verde del commit demostrado y su artefacto `informe-e2e`.
2. Enlace al smoke de dev verde del mismo despliegue.
3. `docs/gobierno/SIMULACRO-REVERSION.md` (CICD-002) y el informe de disponibilidad (`GET /api/v1/disponibilidad/informe`).
4. Este documento, con la fecha y el commit anotados por quien presenta.
