# Servicio de misiones

Módulo de misiones de THE NEXUS BATTLES VI (sección 7.8 del documento del curso, M11, Grupo 2): tablón, detalle, matrícula de un héroe con su estrategia, simulación en segundo plano contra el entorno (JvE), reporte, historial, favoritas y estrategias guardadas, con la **progresión del héroe persistida en el inventario**. Java 21 + Spring Boot 4.1, MongoDB 8, Gradle.

Contrato: [`contracts/openapi/misiones.yaml`](../../../contracts/openapi/misiones.yaml) 1.0.0. Todas las rutas son del jugador autenticado (el `uid` de su token de ms-identidad); un token de servicio recibe 403.

## Qué implementa del documento

| Sección | Qué hace el servicio |
|---|---|
| 7.8.2 Categorías | Historia (lineal, con misiones previas que desbloquean), Desafío (intentos por día o semana, declarados en la semilla) y Exploración (24 a 72 horas, validado al cargar la semilla). |
| 7.8.3 Estructura | Cada misión trae nombre, categoría, descripción, dificultad, duración, nivel recomendado, requisitos, narrativa, objetivos principales y secundarios, enemigos, jefe, Máster y recompensas. |
| 7.8.4 Máster | Aparición aleatoria con la probabilidad de la misión y con la del Máster afín al tipo del héroe (Tabla 20). Dos niveles por encima del héroe, tope 8 (6.1.2). Al derrotarlo, su épica: se entrega al inventario si está en el catálogo oficial; si no, queda en la colección del jugador. En exploración, una tirada por cada 24 horas. |
| 7.8.5 Estrategia | Hasta tres rotaciones con prioridad Alta, Media y Baja. Las valida el servicio de héroes con el prototipo y el nivel **reales** del héroe (leídos del inventario) y la IA de cada turno la decide heroes (`POST /api/v1/estrategias/decision`). Aquí no se reimplementa. |
| 7.8.6 Ejecución | Matrícula con todas las comprobaciones antes de tocar el inventario; el héroe queda bloqueado (`PUT .../bloqueo-mision`). Al vencer el plazo, el trabajo en segundo plano simula todos los combates con el **motor de combate** (el mismo de las batallas en línea), calcula recompensas y experiencia, libera al héroe sumándole la experiencia y entrega lo ganado. |
| 7.8.7 Estados | Disponible, Bloqueada, En progreso, Completada, Fallida, Abandonada, vistos por el jugador que pregunta. |
| 7.8.8 Reporte e historial | Resultado, tiempo, héroe y nivel alcanzado, estadísticas de combate, enemigos y Máster derrotados, recompensas (y lo que no se pudo entregar, con motivo), objetivos; historial con estadísticas por categoría, mejores tiempos, colección de épicas y progreso de la historia. |
| 7.8.9 Interfaz | Tablón con pestañas y filtros (dificultad, estado, duración), 16 por página, destacadas, favoritas, panel de misiones en curso con cancelación. |
| 7.8.10 Integración | El héroe en misión no juega en línea ni entra en torneos (que se juegan en salas) porque el inventario lo marca `disponible: false`; no cambia de equipo (409) ni se subasta. Créditos por ms-finanzas (`refId` por misión), objetos y épicas por `POST /api/v1/inventario/entregas` (origen `MISION`). |
| 7.8.11 Escalones | Normal, Heroico (+50 % de estadísticas) y Legendario (+100 %), cada uno tras completar el anterior. Mítico no tiene cifra en el documento: no se ofrece mientras el PO no la fije. |
| 7.8.12 Técnico | Estado de las ejecuciones y registro de completadas en MongoDB; estrategias guardadas; simulación asíncrona y acelerada; la «cola» es la propia colección, con reintentos con espera exponencial. |
| 6.1.1 Progresión | Experiencia por enemigo no jugador derrotado: 10 × 1,2^(1d8). El dado lo tira este servicio; el valor lo da heroes. El nivel (100 × 1,2^(n−1), tope 8, sobrante conservado) lo aplica el inventario al liberar al héroe, en la misma escritura. |

La única misión publicada es la del documento, **«El Templo Olvidado»** (7.8.14), copiada tal cual en `src/main/resources/semilla/misiones-del-documento.json` junto con la Tabla 20. No se inventan misiones ni textos de historia. `semilla/misiones-provisionales-dev.json` trae una misión técnica marcada «[PROVISIONAL DE DEV]» que solo se publica con `MISIONES_SEMILLA_PROVISIONAL=true` (banco E2E y desarrollo local).

## Cómo funciona una misión

1. **Matrícula** (`POST /api/v1/misiones/{id}/ejecuciones`, con `Idempotency-Key` opcional): la misión admite al jugador → el héroe es suyo, es un héroe y está libre (inventario) → tiene equipo y no es un sanador en solitario (heroes, RC-09) → la estrategia vale para su nivel (heroes) → foto del héroe con su equipo → **bloqueo en el inventario** → se guarda la ejecución. Si guardar falla, se libera al héroe: nada queda a medias.
2. **Espera**: la ejecución está En progreso `duracionHoras`. En el banco E2E una hora dura menos (`MISIONES_SEGUNDOS_POR_HORA`).
3. **Simulación** (trabajo en segundo plano): rivales con las estadísticas de la vista por nivel de heroes o las de la semilla, escaladas por el escalón; combates uno a uno con el motor de combate; la experiencia de cada enemigo derrotado.
4. **Liquidación**, paso a paso y con reintentos: liberación con experiencia (idempotente por estado en el inventario), créditos (idempotentes por `refId = mision-{id}`), botín y épica (`Idempotency-Key = mision-{id}-botin` / `-epica`), correo (`Idempotency-Key = mision-{id}-correo`). Un rechazo definitivo (4xx) queda anotado y aparece en el reporte; un servicio que no contesta se reintenta cada vez más tarde (30 s, 1 min, 2 min… hasta 1 h).
5. **Cancelación** (`POST .../ejecuciones/{id}/cancelacion`): Abandonada, el héroe vuelve sin experiencia.

## Dependencias y configuración

REST síncrono con la credencial de servicio de misiones (`client_credentials`, ADR-001/ADR-005), tiempos de espera acotados, un corta circuitos por dependencia (HU-DIS-003) y la traza propagada (regla 5). Sin bus de mensajes y sin importar clases de otros servicios.

| Dependencia | Variable | Para qué |
|---|---|---|
| inventario (1.6.0) | `INVENTARIO_BASE_URL` | dueño del héroe, bloqueo, liberación con experiencia, entregas. Si la consulta interna del héroe responde 409 (inventario histórico: su producto no tiene id UUID), el héroe se lee de la vitrina del jugador (`GET /api/v1/inventario/elementos` con `X-User-Name`) |
| productos | `PRODUCTOS_BASE_URL` | prototipo del producto HÉROE |
| heroes (1.1.0) | `HEROES_BASE_URL` | validaciones, IA de cada turno, vista por nivel, experiencia por enemigo |
| motor de combate (1.1.0) | `MOTOR_COMBATE_URL` | cada golpe |
| ms-finanzas (créditos 1.4.0) | `CREDITOS_URL` | abono de créditos |
| correo (1.4.0) | `CORREO_URL` | `POST /correos/mision` |
| ms-identidad | `IDENTIDAD_URL`, `IDENTIDAD_JWKS_URL`, `DIRECTORIO_ACTIVO_*` | contacto del jugador, JWKS y credencial de servicio |

El resto de variables está en [`.env.example`](.env.example) y explicado en `src/main/resources/application.properties`.

## Decisiones del PO pendientes (valor provisional)

| Decisión | Valor provisional | Dónde se cambia |
|---|---|---|
| Experiencia por completar según dificultad (6.1.1 no da cifras) | 0 | `MISIONES_XP_COMPLETAR_*` |
| Penalización por abandonar (HU-MIS-015 no la cuantifica) | se pierden todas las recompensas de esa ejecución, experiencia incluida | código (`CancelarEjecucion`) |
| Estadísticas de los enemigos regulares de «El Templo Olvidado» | prototipos Guerrero Armas / Guerrero Tanque / Mago Fuego con sus estadísticas en el nivel del héroe | semilla (notas marcadas PROVISIONAL) |
| «Habilidades potenciadas» del jefe | sin cifra: ataque básico, con los 100 de vida del documento | semilla |
| «Mazo completo equipado» (7.8.6) | al menos un arma, armadura o ítem (como ADR-004) | `MatricularHeroe` |
| Probabilidad del Máster del ejemplo («0.15% (15% de probabilidad)») | 15 %; la Tabla 20 se toma literal (0,04 % = 0,0004) | semilla |
| Nivel recomendado 15 con héroes hasta nivel 8 | se publica tal cual, es solo una sugerencia | semilla |
| ¿La épica exige completar la misión? (HU-MIS-007 dice sí; 7.8.4 solo derrotar al Máster) | manda el documento | `MISIONES_EPICA_EXIGE_COMPLETAR` |
| Multiplicador del escalón Mítico | sin cifra: no se ofrece | `MISIONES_MULTIPLICADOR_MITICO` |
| Créditos por escalón («mejores recompensas») | el mismo factor que las estadísticas (×1,5 y ×2) | `MISIONES_CREDITOS_*` |
| Tiradas de Máster en exploración («mayor probabilidad») | una por cada 24 horas | `TiradaDeMasters` |
| «Explorar las 5 cámaras» | superar todos los encuentros regulares | semilla |
| Recompensas del ejemplo que no están en el catálogo oficial (Cofre de Bronce, Fragmentos del Sello Antiguo, «Piel del Guardián», «Espada del Templo», «Velo de Sombras», el título) | se informan en el reporte como `sinEntregar`; no se inventan productos | semilla (`productoId`) |
| Preferencias de correo por categoría | no hay dónde leerlas: `debeEnviarCorreo` siempre verdadero | `MISIONES_CORREO_ACTIVO` apaga el correo del módulo |

## Límites conocidos

- El motor 1.1.0 resuelve el golpe con la fórmula del prototipo; las acciones de la Tabla 7 que elige la IA se registran en el reporte pero no cambian el daño hasta que el motor las reciba (fase B7).
- `POST /api/v1/inventario/entregas` lo implementa la fase B4. Si el inventario de un entorno aún no la trae, las entregas quedan pendientes y se reintentan (el reporte lo dice con `entregaPendiente`), sin perderse.
- `GET /api/v1/internal/usuarios/{uid}/contacto` de ms-identidad es de la fase B2: sin él, el correo de la misión queda como no enviado y lo demás se entrega igual.
- Las notificaciones dentro de la aplicación (HU-NOT-004) no se integran en esta fase.

## Despliegue

Va al host de contenido (`nexus-contenido-dev`, IP elástica 34.193.90.11), puerto **8105** del host y 8080 dentro del contenedor, con su base `misiones` en la Mongo de contenido (`docker-compose.contenido.yml`, bloque `srv-misiones`). El borde publica `/api/v1/misiones` hacia `34.193.90.11:8105` (`infrastructure/red-balanceo/borde-dev.conf`). Su imagen la construye y publica `cd.yml` como la de sus vecinos: el catálogo (`infrastructure/despliegue/servicios.json`) es lo único que hace falta para que el flujo lo conozca.

**28-sep (topología, fase 1): `desplegableDev: true`.** Lo que le faltaba en ese host no era código, y ya está resuelto así:

1. **Credencial de servicio.** `misiones` está en `CLIENTES_DE_SERVICIO` de `scripts/cd/desplegar.sh`, y su valor es el secret `SECRETO_SERVICIO_MISIONES` del entorno `dev`, que `cd.yml` pasa a los **dos** jobs de dev: el emisor (ms-identidad, host de plataforma) y este servicio (host de contenido) ven el mismo (`repartir_credenciales_de_servicio`; prueba en `scripts/cd/pruebas/credenciales-de-servicio.sh`). El compose lleva `DIRECTORIO_ACTIVO_CLIENT_ID: misiones` literal, como exige el guardián de catálogo. En local, levantar `srv-misiones` con `docker-compose.contenido.yml` pide exportar `SECRETO_SERVICIO_MISIONES` con cualquier valor: con client-id y sin secreto no arranca.
2. **Red de salida.** Desde el host de contenido alcanza ms-identidad (8089) y ms-finanzas (8093): los dos admiten a `34.193.90.11/32` en el grupo de seguridad de plataforma desde B12. El correo de misiones sigue apagado (`MISIONES_CORREO_ACTIVO=false`).
3. **Capacidad.** La compuerta de capacidad corre ahora también en el job de contenido (antes y después, con el núcleo de ese host: heroes, inventario, productos y motor). Medido el 28-sep: 722–740 MiB disponibles antes de esta fase (`CAPACIDAD.md`).
4. **Lo que falta no es nuestro: la entrada.** El borde (host de plataforma) llama a `34.193.90.11:8105`, y el grupo de seguridad de contenido es de la cuenta del Grupo 2. Hasta que admita el 8105 desde `35.168.124.119/32` (`infrastructure/entornos/contenido/reglas-entrada.json`; lo aplica `infra-dev.yml` con `accion=reglas-sg` en cuanto exista `vars.AWS_ROLE_ARN_CONTENIDO`, o su dueño desde la consola), `/api/v1/misiones` responde 504 a los 5 s y la vista enseña su estado «sin abrir», igual que hoy. El servicio corre y responde en su host mientras tanto.

Primer despliegue tras fusionar: un `workflow_dispatch` de `cd.yml` con `perfil=base` (el host de plataforma registra el cliente en el emisor; `desplegar.sh` lo recrea en el paso 3d) y otro con `servicios=misiones,ms-subastas` (host de contenido). Comprobación: `diagnostico-dev.yml` con `ambiente=dev`, sección «RED ENTRE HOSTS» → `34.193.90.11:8105` deja de dar `000`.

En el banco E2E (`tests/e2e/compose.yml`) corre entero: credencial propia, reloj acelerado (una hora de misión = 2 s) y la semilla provisional de desarrollo. `tests/e2e/misiones.e2e.spec.js` recorre el ciclo desde la vista: tablón, matrícula con héroe y estrategia, simulación en segundo plano, reporte con experiencia y créditos, progresión persistida en el inventario y en ms-finanzas, estrategia guardada, misión en curso y cancelación. Su jar entra en la imagen del banco por `tests/e2e/Dockerfile.servicio.dockerignore`.

## Cómo correr

```
./gradlew :services:contenido:misiones:check    # pruebas (dominio, casos de uso, MongoDB y HTTP reales) + JaCoCo ≥ 80 %
./gradlew :services:contenido:misiones:bootRun  # con MongoDB local y las dependencias de .env.example
```

Las pruebas de integración levantan MongoDB 8 con Testcontainers (hace falta Docker) y las siete dependencias como servidores HTTP falsos; cada respuesta se valida contra el contrato. El pacto de consumidor misiones → ms-inventario se escribe en `contracts/pactos/`.
