# Migración del backlog de Jira a GitHub

Documento de gobierno. Registra de dónde salen los datos, cómo se transforman y
cómo repetir la operación en los Sprints siguientes.

## 1. Origen

| Dato | Valor |
|---|---|
| Sitio | `proyectonexusbattles.atlassian.net` |
| Proyecto | `SCRUM` — ProyectoIntegrador2 |
| Consulta por defecto | `project = SCRUM AND issuetype = Historia AND sprint IS NOT EMPTY ORDER BY key ASC` |
| Destino | Issues del repositorio + Project v2 de la organización, número 1 |

En Jira el Sprint 1 no es único: cada grupo tiene el suyo, y los tres estaban
activos al preparar esta migración.

| Sprint en Jira | Etiqueta | Equipo | Historias | Puntos |
|---|---|---|---|---|
| SCRUM Sprint 1 Grupo 2 | `Grupo-2` | Thomas | 29 | 92 |
| SCRUM Sprint 1 Grupo 4 | `Grupo-4` | Santiago | 13 | 65 |
| SCRUM Sprint 1 Grupo 6 | `Grupo-6` | Simón | 11 | 41 |
| **Total** | | | **53** | **198** |

## 2. Por qué se lee Jira en vivo

El workflow **no lleva una copia de las historias en el repositorio**: consulta
Jira en el momento de ejecutarse. La razón es que una copia versionada envejece
en cuanto alguien edita una historia en Jira, y a partir de ahí habría dos
verdades sobre el mismo backlog. Leyendo en vivo, lo que se migra es siempre lo
que Jira dice hoy.

La contrapartida es que la migración depende de que Jira esté disponible y de
que el token siga siendo válido. Se acepta: es una operación puntual por
Sprint, no un proceso continuo.

## 3. Qué se migra y qué no

- Se migran las **historias de usuario**. Cada una se convierte en un Issue.
- Las **subtareas** no se convierten en Issues independientes: viajan como lista
  de tareas marcable dentro del cuerpo de su historia. GitHub muestra el
  progreso en el tablero igual que Jira, y el Project no se llena de tarjetas.
- Las **épicas** no se migran como Issues. Se conservan como texto en cada
  historia, con su clave de Jira.
- El **responsable de Jira** se conserva **como texto**, no como asignado de
  GitHub: un nombre para mostrar de Jira no equivale a un usuario de GitHub y no
  se ha inventado ninguna correspondencia.
- La **descripción** se copia literal. La API de Jira la devuelve como árbol
  ADF y el script se limita a aplanarla a texto: no reescribe, no resume y no
  reinterpreta criterios de aceptación.

## 4. Correspondencia de datos

**Grupo de Jira → equipo y carpeta.** Deducida del contenido de las épicas de
cada grupo, no de una convención de nombres.

| Etiqueta en Jira | Campo `Equipo` | Carpeta del monorepo | Épicas |
|---|---|---|---|
| `Grupo-2` | Thomas | `services/contenido/` | Héroes, catálogo de productos, inventario |
| `Grupo-4` | Santiago | `services/cuentas/` | Seguridad, usuarios, roles, carro, auditoría |
| `Grupo-6` | Simón | `services/plataforma/` | Sala de batalla, correo, notificaciones, comentarios, moderación |

**Prefijo de la historia → campo `Módulo`.**

| Prefijo | Módulo | Prefijo | Módulo |
|---|---|---|---|
| `HU-HER` | Héroes y personajes | `HU-COR` | Correo electrónico |
| `HU-INV` | Jugador e inventario | `HU-NOT` | Notificaciones |
| `HU-PRD` | Administración de productos | `HU-COM` | Comentarios |
| `HU-AUT` | Seguridad y acceso | `HU-ADM` | Administración general |
| `HU-USR` | Usuarios y comentarios | `HU-AUD` | Auditoría |
| `HU-RBAC` | Roles y permisos | `HU-CAR` | Carro de compras |
| `HU-JUE` | Juego en línea | `HU-SAL` | Juego en línea |
| `HU-MIS` | Misiones | `HU-TOR` | Torneos |
| `HU-PAG` | Pagos | `HU-SUB` | Subastas |
| `HU-CHA` | Chatbot | `HU-PRI` | Privacidad |
| `HU-MET` | Métricas y analítica | | |

**Prioridad y estado.**

| Jira | GitHub |
|---|---|
| Highest, High | Alta |
| Medium | Media |
| Low, Lowest | Baja |
| Por hacer | Backlog |
| En curso | In progress |
| En revisión | In review |
| Finalizado | Done |

Si un prefijo, una prioridad o un estado no aparecen en estas tablas, el script
**avisa en el registro y deja el campo vacío**. Nunca adivina.

## 5. Limitación conocida

El campo `Requisito` solo se rellena cuando la descripción de la historia cita
explícitamente un código `RF-XXX-000`. En el Sprint 1 eso ocurre en **13 de las
53** historias; en las 40 restantes el campo queda vacío a propósito. **No se
infiere ningún requisito**: hacerlo introduciría trazabilidad falsa entre la
especificación y el backlog. Completarlas es trabajo de cada grupo.

## 6. Cómo ejecutarla

Hacen falta tres secretos del repositorio, en Settings → Secrets and variables →
Actions. Los escribe una persona directamente en esa pantalla; no se comparten
por chat ni se versionan.

| Secreto | Qué es |
|---|---|
| `JIRA_EMAIL` | Correo de la cuenta de Atlassian |
| `JIRA_TOKEN` | Token de API de Atlassian (id.atlassian.com → Security → API tokens) |
| `TOKEN_MIGRACION` | Token de GitHub con escritura de Issues del repositorio y de proyectos de la organización |

Después:

1. Actions → **Migrar backlog de Jira** → Run workflow, dejando
   **simulacion = true**. El registro muestra la consulta usada, los campos y
   opciones detectados en el Project, y la lista completa de lo que se crearía.
   No escribe nada.
2. Revisar ese registro.
3. Volver a lanzarlo con **simulacion = false**.

La operación es **idempotente**: antes de crear nada, el script lee los Issues
existentes y omite toda historia cuya clave de Jira ya aparezca en el cuerpo de
un Issue. Relanzarlo no duplica.

## 7. Sprints siguientes

No hay que tocar el código. En el formulario del workflow se cambian los dos
campos de texto:

- **Consulta de Jira**, por ejemplo
  `project = SCRUM AND issuetype = Historia AND sprint IN ("SCRUM Sprint 2 Grupo 2") ORDER BY key ASC`
- **Iteración del Project**, por ejemplo `Sprint 2`

Si el título no coincide con ninguna iteración del Project, el script avisa y
usa la primera, dejando constancia en el registro.
