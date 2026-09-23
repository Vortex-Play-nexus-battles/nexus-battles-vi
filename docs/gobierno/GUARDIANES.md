# Guardianes automatizados

Lo que impide que un defecto ya conocido vuelva a entrar sin que nadie se
entere. Cada fila dice **qué defecto previene**, no solo qué comprueba: un
guardián sin un defecto real detrás es ceremonia, y se acaba desactivando.

Casi todos nacieron de algo que **ya pasó**. Esa es la regla de admisión de
esta lista.

## Regla de fondo: un guardián condicional es un guardián apagado

Tres veces en este proyecto un job salió **verde sin ejecutar nada**, porque
sus pasos útiles estaban condicionados a que el PR tocara cierta carpeta:

| Job | Cuándo se arregló |
|---|---|
| `ci-contratos` | R11.6 — «`CI - contratos OpenAPI: pass` significaba literalmente *no se miró*» |
| `ci-enrutado-borde` | R13 — mismo patrón, y es justo el guardián que habría evitado #614 |
| Verificación de pactos | R11.6 — `disabledWithoutDocker = true` salta la clase entera en una máquina sin Docker |

Un PR que cambia un controlador y rompe su correspondencia con el contrato o
con el borde **no toca esas carpetas**: es exactamente el caso que hay que
detectar, y era el único que se escapaba. Validar cuesta segundos; la
condición no ahorraba nada y escondía el estado real.

**Al añadir un guardián, la pregunta no es «¿cuándo hace falta correrlo?» sino
«¿cuánto cuesta correrlo siempre?».** Si la respuesta son segundos, que corra
siempre.

## Contratos e integración

| Guardián | Implementación | Dónde corre | Defecto que previene |
|---|---|---|---|
| **Contratos OpenAPI válidos** | `tests/contratos/validar-contratos.py` | `ci.yml` · `ci-contratos`, **siempre** | `$ref` colgando, YAML mal formado, respuesta sin `description`, `operationId` duplicado entre documentos, ruta declarada por dos contratos, `info.version` ausente o no semántica. En su primera corrida falló **5 de 21** contratos que llevaban semanas rotos. |
| **Cambios incompatibles de contrato** | `tests/contratos/cambios-de-contrato.py` | `ci.yml` · `ci-contratos`, si cambió `contracts/` | Regla 2: que desaparezca una operación, un `operationId` o una respuesta `2xx` sin abrir versión mayor; o que un contrato cambie sin mover `info.version`. Compara con `git show BASE:archivo` — sin broker, no se cae por un tercero. |
| **Pactos con verificador** | `tests/contratos/pactos-verificados.py` | `ci.yml` · `ci-contratos`, **siempre, sin Docker** | Que un `providerState` del pacto no tenga su `@State` literal. Existe porque la verificación real está anotada `disabledWithoutDocker` y se salta entera dejando verde falso. |
| **Verificación de proveedor (Pact)** | `VerificacionDelPactoDeSubastasTest` en ms-finanzas | `ci.yml` · job del servicio | Que ms-finanzas cambie una ruta, un código o un nombre de campo que ms-subastas lee, y no se sepa hasta la integración de la semana siguiente. |
| **Reparto de rutas del borde** | `infrastructure/red-balanceo/pruebas/comprobar-rutas.sh` | `ci.yml` · `ci-enrutado-borde`, **siempre** desde R13 | Que un `location` mande un prefijo al upstream equivocado. Es la causa raíz de #614. |

## Seguridad

| Guardián | Implementación | Dónde corre | Defecto que previene |
|---|---|---|---|
| **Secretos en la bitácora** | `tests/contratos/sin-secretos-en-bitacora.py` | `ci.yml` · `ci-secretos-bitacora`, **siempre** | Un `log.info("token={}", token)` mandando material secreto al agregador. Desde que la bitácora sale en JSON hacia stdout, lo que antes se perdía en una consola queda indexado. Analiza la llamada entera, no la línea. |
| **innerHTML interpolado** | `frontend/app-web/src/comun/ui/sin-innerhtml.test.js` | `ci.yml` · `ci-frontend` (Jest, bloqueante) | XSS. Dos incidentes reales detrás: el interceptor pintando el `detail` del servidor (#598) y `comprobanteUrl` en un `href`, ejecutable con `javascript:` (#609). |
| **Marcado sin plantillas** | `frontend/app-web/src/comun/marcado-sin-plantillas.test.js` | ídem | Componer marcado con `innerHTML` + literal de plantilla. Caso real: dos paneles de administración construyendo `<option value="${o}">` con datos del servicio de parámetros, sin escapar. |
| **Permisos por vista** | `tests/visual/permisos.spec.js` | `visual.yml` · `auditoria` | Que una vista sea accesible a un rol que la matriz de acceso no permite. |

## Calidad del código

| Guardián | Implementación | Dónde corre | Defecto que previene |
|---|---|---|---|
| **Cobertura ≥ 80 %** | `buildSrc/.../nexus.spring-conventions.gradle` (`jacocoTestCoverageVerification`, enganchado a `check`) | `ci.yml` · job por servicio | Que un servicio se fusione por debajo del umbral de la regla 11. Su comentario lo resume: «ninguno estaba por debajo — lo que faltaba no era cobertura, era la compuerta». |
| **Estructura del monorepo** | `.github/workflows/guardia-monorepo.yml` | PR y push a `develop`/`main` | (a) un servicio con su propio `gradlew`; (b) un `pom.xml` nuevo fuera de la lista blanca, que «solo puede encoger»; (c) **un servicio con `src/main/java` no declarado en `settings.gradle`** — código que nadie compila, que es el incidente del Sprint 1 que lo motivó. |
| **ESLint y Prettier** | config del frontend | `ci.yml` · `ci-frontend`, bloqueante sobre lo que cambia | Regresiones de lint y formato. |
| **ArchUnit** | `ReglasDeArquitecturaTest` en ms-subastas | `ci.yml` vía `check` | Imports de dominio ajeno. **Alcance real: un solo servicio** — ms-finanzas declara la dependencia y no tiene clase de prueba. Está listado como transversal en `CLAUDE.md` y no lo es. |

## Interfaz

| Guardián | Implementación | Dónde corre | Defecto que previene |
|---|---|---|---|
| **Catálogo de vistas** | `tests/visual/vistas.js` + aserciones de `auditoria-visual.spec.js` | `visual.yml` | Una vista nueva sin registrar, o una entrada apuntando a un archivo borrado. |
| **Kit cargado** | `auditoria-visual.spec.js` (`kitCargado`) | ídem | Que el arnés fotografíe HTML desnudo porque la raíz servida está mal, y todos los hallazgos salgan falsamente. |
| **Accesibilidad** | `tests/visual/accesibilidad.spec.js` (axe-core, 32 vistas × 2 anchuras) | ídem | Hallazgos `serious` y `critical`. |
| **Clases del kit** | `clases-del-kit.test.js` | `ci.yml` · `ci-frontend` | Una vista que escribe una clase CSS que nadie define: no falla, no avisa, no se ve en revisión. Había 5 reales. |
| **Tokens sin sombra** | `tokens-sin-sombra.test.js` | ídem | Que un CSS de vista redeclare un token y tape el bloque `@media (prefers-contrast: more)`. Medido antes de arreglar: **56 declaraciones duplicadas, 43 sobre variables de alto contraste**. |
| **Ortografía visible** | `ortografia-visible.test.js` | ídem | Texto visible en castellano sin tildes. Medido: 125 palabras en 36 ficheros. |

## Despliegue y operación

| Guardián | Implementación | Dónde corre | Defecto que previene |
|---|---|---|---|
| **Reparto de etiquetas del CD** | `scripts/cd/pruebas/desplegar-etiquetas-contenido.sh` | `ci.yml` · `ci-etiquetas-cd`, **siempre** desde R13 | Que `docker compose up` arrastre un servicio que no cambió con una etiqueta que no existe. Pasó de verdad (corrida 34307790392, 9-sep) y tumbó el despliegue. **La prueba existía desde entonces y nadie la ejecutaba jamás.** |
| **Salud tras desplegar y reversión** | healthcheck de `scripts/cd/desplegar.sh` + `scripts/cd/revertir.sh` | `cd.yml` | Que una imagen mala se quede en pie. Reversión medida: 24 s (`SIMULACRO-REVERSION.md`). |
| **Guardián del propio simulacro** | aserción en `cd.yml` del job de simulacro | `cd.yml`, solo en `workflow_dispatch` | Que el mecanismo de reversión deje de detectar fallos **en silencio**. |
| **Importación del host de contenido** | condición sobre `vars.IMPORTACION_CONTENIDO_COMPLETADA` en `infra-dev.yml` | `infra-dev.yml` | Que un `apply` con el estado remoto vacío **cree un tercer EC2** y duplique el coste. |
| **Tipos de instancia del plan gratuito** | `validation` en `infrastructure/entornos/plataforma/variables.tf` | `tofu plan/apply` | Lanzar un tipo que el plan gratuito no cubre. |
| **Smoke de comportamiento** | `tests/e2e/smoke-aws.smoke.spec.js` | `smoke-dev.yml`, tras cada CD | Que el despliegue «esté verde» porque `/actuator/health` responde, mientras registrarse no funciona. Comprueba registro, identidad del token (ADR-002), correo en Mailpit y métricas viendo a los servicios. |
| **E2E de aceptación** | `tests/e2e/*.e2e.spec.js` + banco de servicios reales | `e2e.yml` | El corte vertical completo. Incluye apagar contenedores de verdad (`degradacion.e2e.spec.js`). |
| **Rendimiento (smoke k6)** | `tests/rendimiento/k6/` | `rendimiento.yml`, contra el banco efímero | Regresión de latencia y **de tasa de error**. Las dos, no solo la primera: la primera corrida real dio p95 de 2 ms con **100 % de error** en un escenario, porque medía el rechazo de una validación. Un umbral de latencia sin umbral de error lo habría dado por bueno. |

## Lo que NO es guardián

- **Aceptación en navegador** (`ci.yml`) — informativo, por #581.
- **SonarCloud** — corre fuera del repositorio (#429); hoy bloquea #390 y no se sabe si por hallazgos o por configuración.
