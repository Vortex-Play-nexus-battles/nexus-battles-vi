# THE NEXUS BATTLES VI — Return of the Warriors

RPG por turnos en web, con IA y arquitectura de microservicios en la nube. Proyecto Integrador II,
UPB Seccional Bucaramanga, periodo 2026-20 (instructor Lenin Javier Serrano Gil). El proyecto se
organiza como una "empresa" (Empresa A) con 3 Scrum Teams, 18 desarrolladores, 20 módulos y 190 RF.

Repo: `github.com/Vortex-Play-nexus-battles/nexus-battles-vi.git`

**Regla de fondo para cualquier tarea en este repo: seguir al pie de la letra este documento, los
`.claude/rules/` correspondientes, y los demás documentos de contexto del proyecto (`PILA_T_1.PDF`,
`PaletaDeColoresYPilaTecnologica.pdf`, `REQUISITOS_GRUPO_6_EMPRESA_A.pdf`, issues de GitHub). No
reinterpretar requisitos, no proponer alternativas de stack, no "mejorar" decisiones ya tomadas y
justificadas. Si algo no está cubierto por estos documentos, preguntar antes de asumir — no inventar.**

## Equipo 6 / Grupo 6 — "Tiempo Real, Comunidad y Plataforma"

| Integrante | Rol |
|---|---|
-Santiago Anaya — HU-COR-001, HU-COR-002, HU-COR-003 (Correo), HU-ADM-002 (Lista negra), Diseño UML de Correo y notificaciones
-Simón Pérez Gómez — HU-SAL-001, HU-SAL-002, HU-SAL-003, HU-SAL-004, HU-SAL-005 (Sala de batalla completa), Diseño UML de Plataforma, canal y observabilidad
-lexander Niño — HU-NOT-006, HU-JUE-015, HU-COM-001, Diseño UML de Comentarios y moderación
-Andrés Felipe Sánchez Palacio — HU-REN-001, HU-REN-003, HU-DIS-001, HU-DIS-003, Diseño UML de Tiempo real / Jugar online
-Santiago González Osorio — HU-CICD-003, HU-POR-001, HU-POR-003, Diseño UML de Usuarios, sanciones y panel
-Néstor Pinto Roa — HU-CICD-001, HU-CICD-002, Diseño UML de Torneos y transmisión
Equipo 6 es además dueño de M16A y M16B (infraestructura, red, datos, DR, CI/CD) para **toda la
empresa** — por eso la pila tecnológica la propuso este mismo equipo y aplica a los tres Scrum Teams
por igual, no es negociable por equipo.

**Regla de oro:** cada quien trabaja con responsabilidad directa en su propia historia asignada (ver
tabla de Sprint 1 más abajo). Al tocar código de otro módulo o de otro grupo (grupo-2, grupo-4),
coordinar antes de modificar, y nunca fusionar un cambio a un contrato ajeno sin aprobación de su dueño.

## Pila tecnológica — resumen (vigente desde ago 2026)

**Arquitectura: microservicios reales — 20 módulos = 20 servicios desplegables independientes, cada
uno con su propia base de datos. Nada de monolito ni de contexto de Spring compartido entre módulos.**
Detalle y ejemplo concreto en `.claude/rules/backend-spring.md`.

Ya NO se usa Angular. Detalle completo y justificado por decisión, con backend, comunicación entre
servicios y datos, en `.claude/rules/backend-spring.md`; frontend, paleta y tipografía en
`.claude/rules/frontend-web.md` — se cargan solos según la carpeta en la que estemos trabajando.

- **Backend:** Java 21 LTS + Spring Boot 4.1, build con Gradle
- **Frontend:** HTML5 + CSS3 + JavaScript ES2022 sin framework, servido por Spring Boot como estático
- **Datos:** PostgreSQL 17 (relacional), MongoDB 8 (documental), Redis 8 (caché/estado)
- **Identidad:** Keycloak 26 · **Mensajería:** RabbitMQ 4 · **Tiempo real:** WebSocket + STOMP
- **Infra:** Docker, k3s, OpenTofu, Azure for Students
- **Calidad:** JUnit5/JaCoCo (80% mínimo), Testcontainers, Pact JVM, ArchUnit, Playwright, k6,
  SonarQube Cloud, GitHub Actions, Conventional Commits

## Doce reglas de plataforma — obligatorias para los tres equipos, sin excepciones

Un servicio que no las cumple no se despliega, no aparece en observabilidad y no pasa la compuerta
de calidad. Nunca romperlas "temporalmente para probar algo".

1. **Contrato primero** — OpenAPI/AsyncAPI se publica y se acuerda antes de implementar
2. Todo bajo `/api/v1` — un cambio incompatible abre versión nueva, no modifica la existente
3. Todo servicio expone salud y métricas de Actuator
4. Formato de error estándar (problem details), idéntico en los 20 módulos
5. Propagación del trace id en toda llamada entre servicios y todo mensaje de cola
6. Bitácora en JSON hacia stdout, nunca a archivo
7. Aislamiento de datos: nunca acceder a la BD de otro servicio
8. Todo cambio de esquema por Flyway, versionado, nunca a mano
9. Definición de contenedor versionada junto al servicio, sin excepciones
10. Configuración solo por variable de entorno, plantilla en el repo, ningún valor real versionado
11. Compuerta de calidad: cobertura ≥80% + análisis estático sin defectos nuevos + contratos en
    verde, o no hay fusión a la rama principal
12. Ramas cortas por funcionalidad sobre la rama principal, con revisión cruzada obligatoria

## Flujo de ramas

**Confirmado en PRs reales del repo (ej. #152): las ramas de feature salen de `develop` y el Pull
Request va contra `develop`, no contra `main`.** GitHub tiene `main` como rama por defecto a nivel
de configuración, pero esa no es la que usa el equipo para integrar — no asumir `main` solo porque
aparece primero al abrir un PR nuevo. `main` queda para versiones estables ya integradas desde
`develop`. Convención de nombres vista en el repo: `feat/<slug>`, `feature/<HU o SCRUM-id>`,
`fix/<slug>`, `chore/<slug>`, `test/<slug>` — commit inicial siguiendo Conventional Commits
(`tipo(alcance): descripción`).

Antes de crear una rama de feature: `git checkout develop && git pull origin develop`.

## Estructura del monorepo (ya definida — no reorganizar)

```
services/cuentas/              → Equipo Cuentas y comercio
services/contenido/            → Equipo Contenido y juego
services/plataforma/           → Equipo 6 — Tiempo real, comunidad y plataforma
  ├─ salas-partidas/  torneos/  comentarios/  moderacion-sanciones/
  ├─ notificaciones/  correo/
  └─ admin-parametros/  metricas-plataforma/
frontend/app-web/src/          → HTML/CSS/JS, un .html + un .js por vista (mismo nombre)
  ├─ comun/                    → compartido — dueños: los 3 Scrum Masters juntos
  ├─ plataforma/               → espejo de services/plataforma/ de arriba
  ├─ cuentas/  contenido/      → de los otros dos equipos
shared/libs/                   → biblioteca Java compartida (error, trazas, tipos, utils de prueba)
shared/ui-kit/                 → JS y CSS compartidos, derivados de las maquetas de Figma
shared/config/                 → complementos de convención de Gradle, reglas de Spotless
contracts/{openapi,eventos,websocket,esquemas}/ → contratos entre los tres equipos
infrastructure/{contenedores,entornos,observabilidad,datos,respaldo-dr}/
tests/{contratos,rendimiento}/ · .github/workflows/
```

## Fuente de verdad del backlog: GitHub Issues, no documentos viejos

El backlog vivo está en los **Issues de este repo**, migrado desde Jira. Cualquier `.md` o `.docx`
de research/backlog que encuentres es una foto de un momento anterior — puede estar desactualizado.

- Labels relevantes: `grupo-6`, `historia-usuario`, `tarea`, `migrado-jira`.
- Cada issue trae una tabla "Metadatos migrados desde Jira" con los campos **`Sprint (Jira)`** y
  **`Responsable (Jira)`** — son el sprint y el responsable reales; el texto narrativo de la
  historia puede quedar desactualizado tras un rebalanceo. El responsable es texto migrado de Jira,
  no un assignee real de GitHub, porque un nombre de Jira no equivale a un usuario de GitHub.
- Antes de implementar algo, revisar el issue correspondiente en GitHub. No asumir alcance ni
  responsable por el nombre de la historia.

## Sprint 1 · Grupo 6 (16–31 ago 2026) — 21 ítems / 91 pts

**Historias de usuario (12)**

| HU | Qué es | Responsable |
|---|---|---|
| HU-COR-001 | Todo correo del sistema sobre la plantilla corporativa (MJML) | Santiago Anaya |
| HU-COR-002 | Correo de confirmación de cuenta con código de vigencia | Santiago Anaya |
| HU-COR-003 | Recuperación de contraseña con código de un solo uso | Santiago Anaya |
| HU-ADM-002 | Lista negra de términos prohibidos, administrable | Santiago Anaya |
| HU-SAL-001 | Creación de sala de batalla con parámetros y recompensa | Simón Pérez Gómez |
| HU-SAL-002 | Ingreso a sala existente desde el listado | Simón Pérez Gómez |
| HU-SAL-003 | Verificación de héroe equipado y disponible antes de partida | Simón Pérez Gómez |
| HU-SAL-004 | Modalidades de partida: 1v1, contra IA, hasta 6 | Simón Pérez Gómez |
| HU-SAL-005 | Barra de vida con umbrales de color al 60% y 40% | Simón Pérez Gómez |
| HU-NOT-006 | Notificaciones push en tiempo real sincronizadas entre sesiones | Alexander Niño |
| HU-JUE-015 | Chat en la sala de batalla y en la vista general | Alexander Niño |
| HU-COM-001 | Publicación de comentarios con texto e imágenes | Alexander Niño |

**Habilitadores técnicos (9)**

| HU | Qué es | Responsable |
|---|---|---|
| HU-CICD-001 | Flujo de integración continua con pruebas y análisis | Néstor Pinto Roa |
| HU-CICD-002 | Flujo de distribución continua con promoción y reversión | Néstor Pinto Roa |
| HU-CICD-003 | Despliegue exclusivo por el flujo automatizado | Santiago González Osorio |
| HU-POR-001 | Verificación del despliegue en local y en nube | Santiago González Osorio |
| HU-POR-003 | Entornos reproducibles con contenedores versionados | Santiago González Osorio |
| HU-REN-001 | Instrumentación de latencia extremo a extremo bajo 500ms | Andrés Felipe Sánchez Palacio |
| HU-REN-003 | Búsquedas indexadas dentro del objetivo de latencia | Andrés Felipe Sánchez Palacio |
| HU-DIS-001 | Monitoreo y registro de disponibilidad desde el primer sprint | Andrés Felipe Sánchez Palacio |
| HU-DIS-003 | Degradación controlada ante caída de un microservicio | Andrés Felipe Sánchez Palacio |

**Diseño UML (6)**

| Bloque | Responsable |
|---|---|
| Tiempo real / Jugar online | Andrés Felipe Sánchez Palacio |
| Torneos y transmisión | Néstor Pinto Roa |
| Comentarios y moderación | Alexander Niño |
| Usuarios, sanciones y panel | Santiago González Osorio |
| Correo y notificaciones | Santiago Anaya |
| Plataforma, canal y observabilidad | Simón Pérez Gómez |

**Por confirmar con el equipo:** `REQUISITOS_GRUPO_6_EMPRESA_A.pdf` describe Correo y Notificaciones
como un solo módulo (M15) bajo Santiago Anaya, pero la asignación real de Sprint 1 en GitHub tiene
HU-NOT-006 con Alexander Niño. No se resolvió esa diferencia aquí — validarlo en la próxima reunión
de equipo y actualizar esta tabla según lo que se acuerde.

**Bloqueadores activos:** RF-ADM-003 (sin asignar) bloquea RF-NOT-002 · RF-PRV-003 (fuera de
alcance del grupo) bloquea RF-COR-004.

## Diagramas UML y diseño

UML del módulo de correo/notificaciones ya generado: casos de uso UC-05, clases CL-05, secuencia
SEQ-CN-01 a 04, actividad ACT-CN-01/02 (`.mdj` de StarUML). Figma con auditoría UX y pantallas de
Sprint 1: https://www.figma.com/design/PXanKCqsAYLemJhyTyTTHk — usar como fuente, no rediseñar.

## Comandos

_Aún no hay `build.gradle` real en el repo. En cuanto exista: `./gradlew build`, `./gradlew test`;
frontend sin build step; lint de JS con ESLint. Actualizar esta sección apenas existan los primeros
wrappers de Gradle._

## Convenciones de código

- Conventional Commits en cada confirmación
- Paquetes por feature en Spring Boot; DTOs vía MapStruct; sin lógica de negocio en controllers
- JS: JSDoc para tipos, módulos ES2022 nativos, sin frameworks ni bundlers

## Límites — no hacer sin avisar al equipo

- **No proponer ni usar un stack alternativo** — ya está decidido y justificado en `PILA_T_1.PDF`.
  Solo el cliente puede aprobar un cambio; ni un integrante ni Claude Code lo deciden a mitad de tarea.
- **No romper ninguna de las 12 reglas de plataforma**, ni "temporalmente para probar algo".
- No acceder a la base de datos de otro servicio directamente — solo por API o evento.
- No modificar carpetas/servicios de otro integrante o de otro grupo sin coordinarlo primero.
- No fusionar un cambio a un contrato ajeno sin aprobación de su dueño.
- No inventar requisitos que no estén en un issue de GitHub o en `REQUISITOS_GRUPO_6_EMPRESA_A.pdf`.
- No commitear `.env`, credenciales de Keycloak, Azure ni Brevo.
- No reordenar la prioridad del Sprint 1 sin reflejarlo también en Jira y en GitHub.
