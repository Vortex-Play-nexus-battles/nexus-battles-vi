# Servicio de misiones

Módulo de misiones de THE NEXUS BATTLES VI (sección 7.8 del documento del curso, M11, Grupo 2): tablón, detalle, matrícula de un héroe con su estrategia, simulación en segundo plano contra el entorno (JvE), reporte, historial, favoritas y estrategias guardadas, con la **progresión del héroe persistida en el inventario**. Java 21 + Spring Boot 4.1, MongoDB 8, Gradle.

Contrato: [`contracts/openapi/misiones.yaml`](../../../contracts/openapi/misiones.yaml) 1.0.0. Todas las rutas son del jugador autenticado (el `uid` de su token de ms-identidad); un token de servicio recibe 403.

## Qué implementa del documento

| Sección | Qué hace el servicio |
|---|---|
| 7.8.2 Categorías | Historia (lineal, con misiones previas que desbloquean), Desafío (intentos por día o semana, declarados en la semilla) y Exploración (24 a 72 horas, validado al cargar la semilla). |
| 7.8.3 Estructura | Cada misión trae nombre, categoría, descripción, dificultad, duración, nivel recomendado, requisitos, narrativa, objetivos principales y secundarios, enemigos, jefe, Máster y recompensas. |
| 7.8.4 Máster | Aparición aleatoria con la probabilidad de la misión y con la del Máster afín al tipo del héroe (Tabla 20). Dos niveles por encima del héroe, tope 8 (6.1.2), y estadísticas superiores a las de los regulares de su misión (ver «El Máster», abajo). Posee una épica exclusiva, que lleva al combate. Al derrotarlo, su épica: se entrega al inventario si está en el catálogo oficial; si no, queda en la colección del jugador. En exploración, una tirada por cada 24 horas. |
| 7.8.5 Estrategia | Hasta tres rotaciones con prioridad Alta, Media y Baja. Las valida el servicio de héroes con el prototipo y el nivel **reales** del héroe (leídos del inventario) y la IA de cada turno la decide heroes (`POST /api/v1/estrategias/decision`). Aquí no se reimplementa. Con la IA con modelo propio encendida (HU-SIM-008, ver abajo), el modelo elige entre las jugadas que esa misma regla admite. |
| 7.8.6 Ejecución | Matrícula con todas las comprobaciones antes de tocar el inventario; el héroe queda bloqueado (`PUT .../bloqueo-mision`). Al vencer el plazo, el trabajo en segundo plano simula todos los combates con el **motor de combate** (el mismo de las batallas en línea: `turnos` y `acciones`, con el héroe real), calcula recompensas y experiencia, libera al héroe sumándole la experiencia y entrega lo ganado. |
| 7.8.7 Estados | Disponible, Bloqueada, En progreso, Completada, Fallida, Abandonada, vistos por el jugador que pregunta. |
| 7.8.8 Reporte e historial | Resultado, tiempo, héroe y nivel alcanzado, estadísticas de combate, enemigos y Máster derrotados, recompensas (y lo que no se pudo entregar, con motivo), objetivos; historial con estadísticas por categoría, mejores tiempos, colección de épicas y progreso de la historia. |
| 7.8.9 Interfaz | Tablón con pestañas y filtros (dificultad, estado, duración), 16 por página, destacadas, favoritas, panel de misiones en curso con cancelación. |
| 7.8.10 Integración | El héroe en misión no juega en línea ni entra en torneos (que se juegan en salas) porque el inventario lo marca `disponible: false`; no cambia de equipo (409) ni se subasta. Créditos por ms-finanzas (`refId` por misión), objetos y épicas por `POST /api/v1/inventario/entregas` (origen `MISION`). |
| 7.8.11 Escalones | Normal, Heroico (+50 % de estadísticas) y Legendario (+100 %), cada uno tras completar el anterior. Mítico no tiene cifra en el documento: no se ofrece mientras el PO no la fije. |
| 7.8.12 Técnico | Estado de las ejecuciones y registro de completadas en MongoDB; estrategias guardadas; simulación asíncrona y acelerada; la «cola» es la propia colección, con reintentos con espera exponencial. |
| 6.1.1 Progresión | Experiencia por enemigo no jugador derrotado: 10 × 1,2^(1d8). El dado lo tira este servicio; el valor lo da heroes. El nivel (100 × 1,2^(n−1), tope 8, sobrante conservado) lo aplica el inventario al liberar al héroe, en la misma escritura. |

Del documento se publica **«El Templo Olvidado»** (7.8.14) en `src/main/resources/semilla/misiones-del-documento.json` junto con la Tabla 20, con nivel recomendado 8 (el documento dice 15, que ningún héroe alcanza: §6.1.1; D-42).

**Catálogo de progresión (D-42, auditoría del 4-oct).** `semilla/misiones-de-progresion.json` publica siete misiones de historia de nivel 1 a 7 (origen `PROGRESION`), cada una tras la anterior, que llevan a un héroe nuevo hasta el Templo: «El Sendero de los Aprendices» (1), «La Mina Abandonada» (2), «El Pantano de los Susurros» (3), «La Fortaleza Quebrada» (4), «Las Cumbres Heladas» (5), «El Volcán Dormido» (6) y «La Ciudadela de las Sombras» (7). Son contenido del equipo 6, no del documento; el PO puede ajustar cifras y textos. El catálogo rechaza al arrancar cualquier misión que recomiende un nivel fuera de 1..8.

**Equilibrio.** `BalanceDeMisionesTest`, en motor-combate, juega cada misión publicada con el motor de combate real (la CI de motor-combate corre también cuando cambia una semilla de aquí). Exige que en su nivel recomendado ninguna se gane ni se pierda siempre (entre el 15 % y el 90 % para los seis prototipos que atacan), que un nivel menos cueste más, que un héroe de nivel 1 tenga una misión que gana más de lo que pierde y que un jugador nuevo llegue al nivel 8 jugando la misión que le toca. Los enemigos juegan en esa medición las estrategias predefinidas de `semilla/estrategias-de-enemigos.json` (HU-SIM-004), con la regla de rotaciones del héroe, que es lo que juega este servicio; una prueba falla si faltan, para que el equilibrio no se mida sobre otro juego. Resultado del 4-oct (con enemigos que atacaban con su golpe más fuerte): del 56 % al 83 % de 1 a 7 y 38 % en el Templo; de 1 a 8 en 11 a 12 ejecuciones (21 el Guerrero Tanque, que casi nunca causa efecto por su fila de la Tabla 21). `semilla/misiones-provisionales-dev.json` trae una misión técnica marcada «[PROVISIONAL DE DEV]» que solo se publica con `MISIONES_SEMILLA_PROVISIONAL=true` (banco E2E y desarrollo local).

## Cómo funciona una misión

1. **Matrícula** (`POST /api/v1/misiones/{id}/ejecuciones`, con `Idempotency-Key` opcional): la misión admite al jugador → el héroe es suyo, es un héroe y está libre (inventario) → tiene equipo y no es un sanador en solitario (heroes, RC-09) → la estrategia vale para su nivel (heroes) → foto del héroe con su equipo → **bloqueo en el inventario** → se guarda la ejecución. Si guardar falla, se libera al héroe: nada queda a medias.
2. **Espera**: la ejecución está En progreso `duracionHoras`. En el banco E2E una hora dura menos (`MISIONES_SEGUNDOS_POR_HORA`).
3. **Simulación** (trabajo en segundo plano): rivales con las estadísticas de la vista por nivel de heroes o las de la semilla, escaladas por el escalón; combates uno a uno turno a turno con el motor de combate (ver «El combate de la misión»); la experiencia de cada enemigo derrotado.
4. **Liquidación**, paso a paso y con reintentos: liberación con experiencia (idempotente por estado en el inventario), créditos (idempotentes por `refId = mision-{id}`), botín y épica (`Idempotency-Key = mision-{id}-botin` / `-epica`), correo (`Idempotency-Key = mision-{id}-correo`). Un rechazo definitivo (4xx) queda anotado y aparece en el reporte; un servicio que no contesta se reintenta cada vez más tarde (30 s, 1 min, 2 min… hasta 1 h).
5. **Cancelación** (`POST .../ejecuciones/{id}/cancelacion`): Abandonada, el héroe vuelve sin experiencia.

## El combate de la misión (HU-SIM-003)

Cada turno de cada duelo lo resuelve el **mismo motor de combate que las batallas en línea** (`motor-combate.yaml` 1.2.0, el contrato que usa salas-partidas): `POST /api/v1/combate/turnos` al empezar el turno de un combatiente (+2 de poder, efectos por turno, protecciones) y `POST /api/v1/combate/acciones` para su jugada. Misiones ya no usa `POST /combate/ataques`. El motor no guarda nada: el simulador le manda el estado de los dos combatientes y guarda el que devuelve.

- **El héroe entra con lo suyo.** Nivel, estadísticas con el equipamiento aplicado y fórmulas (`GET .../heroes/{id}/estadisticas`, que el inventario calcula en el nivel del héroe), nombres de lo que lleva puesto (`GET .../equipamiento` + la vitrina + el nombre del producto) y sus épicas disponibles. Las acciones de la Tabla 7 y las épicas las conoce el motor por el prototipo y el nivel. Se lee **al simular**, no al matricular: el equipamiento no cambia mientras está en misión (7.8.10), y así también se simulan bien las ejecuciones guardadas antes de este cambio. Si el inventario ya no conoce al héroe, pelea con lo del catálogo y se anota; si el inventario o productos no responden, la simulación espera a la vuelta siguiente.
- **Los enemigos** entran con su prototipo en el **nivel recomendado de la misión** (§7.8.13; D-42 — antes, en el del héroe, y subir de nivel no hacía más fácil ninguna misión; la que no tiene nivel recomendado, la provisional de DEV, sigue en el del héroe; Máster: dos por encima del héroe, con el refuerzo de «El Máster»), con la **vida y la defensa de la semilla y del escalón** y las fórmulas de la vista por nivel de heroes.
- **La IA decide para los dos lados** con la lógica de heroes (rotaciones con prioridad, poder y recarga; HU-SIM-002): el héroe con la estrategia guardada del jugador; los enemigos con la que la misión escribe para ese enemigo y, si no la trae, con la **estrategia predefinida de su prototipo y nivel** (siguiente sección; HU-SIM-004). El poder lo lleva el motor. Esa decisión puede tomarla, en cambio, el modelo propio de HU-SIM-008 (siguiente sección), siempre acotado por la misma regla.
- **Si el motor rechaza la jugada** (409 `accion-no-permitida`, p. ej. `EN_CARGA`, porque heroes y el motor cuentan la recarga a su manera), la IA prueba la siguiente opción de su rotación y, al final, el ataque básico. Nunca se queda a medias. Si el motor no responde, la simulación entera se reintenta en la vuelta siguiente (corta circuitos de HU-DIS-003) sin guardar nada.
- **Velocidad acelerada.** No se espera tiempo real por turno: una misión entera se simula en una pasada en cuanto vence el plazo; la duración solo gobierna cuándo se ve el resultado.

### Las estrategias de los enemigos (HU-SIM-004, RF-MIS-20)

«La IA controla al héroe según las rotaciones y a los enemigos con estrategias predefinidas» (7.8.6). Una estrategia es lo mismo que la del héroe (7.8.5): hasta tres rotaciones por prioridad (Alta, Media, Baja) y el ataque básico de respaldo. **Las predefinidas son datos versionados**, no una heurística: [`semilla/estrategias-de-enemigos.json`](src/main/resources/semilla/estrategias-de-enemigos.json) trae una por cada uno de los 8 prototipos de la Tabla 7 y por cada tramo de nivel que marcan los desbloqueos (RC-01): niveles 1 a 3, 4 a 7 y 8 en adelante. Son 24, cada una con su `id` (`mago-fuego-n4`), sus rotaciones con los nombres exactos de la Tabla 7 y la razón de cada orden. Para cambiar una basta editar ese archivo en un PR; ninguna lleva código.

**Precedencia**, de mayor a menor, para cada enemigo (regular, Máster o jefe):

1. la rotación que **la misión escribe** para ese enemigo (`enemigos[].rotaciones` y `jefe.rotaciones` de la semilla de misiones; los Máster no tienen ese campo). Hoy ninguna misión publicada la trae, y no se valida al cargar la semilla (heroes la valida contra el nivel real al simular);
2. la **predefinida** de su prototipo en el tramo del nivel con el que pelea de verdad: el recomendado de la misión (D-42), no el del héroe; un enemigo del Templo (nivel 8) usa la de 8 en adelante aunque el héroe sea de nivel 3;
3. la **heurística** de antes (`RotacionesPorDefectoDeEnemigos`: una rotación por habilidad desbloqueada, la más avanzada primero), que queda solo de respaldo.

**Criterio de diseño** (decisión provisional del PO, también escrita en el archivo):

- **El ataque va primero.** Las habilidades de ataque (las que dan bono al ataque o al daño) ocupan las primeras rotaciones; las de defensa y sanación van en la de menor prioridad. La regla de héroes (HU-SIM-002) **no condiciona una rotación a la vida del combatiente** (solo exige que tenga vida; pendiente P-E de esa historia), así que no se puede escribir «cura solo si está herido»: una rotación de defensa o sanación se juega en cuanto las de ataque no son viables ese turno (sin poder o en recarga), esté herido o no.
- **Dentro de una familia, primero la de mayor valor esperado**, sumando los bonos al ataque y al daño que escribe la Tabla 7 (los dados cuentan por su media; los efectos sobre el rival y los bonos sin cifra no suman); a igual valor, el orden de la tabla. Las justificaciones numéricas están en el archivo.
- **Cada habilidad en su propia rotación** y solo las ya desbloqueadas en el tramo.
- **Reanimación no entra** (Médico, nivel 8): sana a un compañero y el motor solo admite compañeros como objetivo; en una misión el enemigo pelea solo y la rechazaría en cada turno.

| Prototipo | Niveles 1 a 3 | Niveles 4 a 7 | Nivel 8 en adelante |
|---|---|---|---|
| Guerrero Tanque | Golpe con escudo | Golpe con escudo › Mano de piedra | Golpe con escudo › Defensa feroz › Mano de piedra |
| Guerrero Armas | Embate sangriento | Embate sangriento › Lanza de los dioses | Golpe de tormenta › Embate sangriento › Lanza de los dioses |
| Mago Fuego | Misiles de magma | Vulcano › Misiles de magma | Vulcano › Misiles de magma › Pare de fuego |
| Mago Hielo | Lluvia de hielo | Lluvia de hielo › Cono de hielo | Lluvia de hielo › Cono de hielo › Bola de hielo |
| Pícaro Veneno | Flor de loto | Flor de loto › Agonía | Flor de loto › Agonía › Piquete |
| Pícaro Machete | Cortada | Machetazo › Cortada | Machetazo › Planazo › Cortada |
| Chamán | Toque de la Vida | Vínculo Natural › Toque de la Vida | Canto del Bosque › Vínculo Natural › Toque de la Vida |
| Médico | Curación Directa | Neutralización de Efectos › Curación Directa | Neutralización de Efectos › Curación Directa |

Cada `›` es la siguiente rotación por prioridad. Quien resuelve cada turno es la regla de héroes: en cada uno juega la primera rotación **viable** (poder suficiente y recarga cumplida) y, si ninguna lo es, el ataque básico (la sanación básica en un sanador). El costo en poder y la recarga no se repiten aquí: los cobra heroes y los aplica el motor.

**Validación y qué pasa si una estrategia está mal.** Al arrancar, cada estrategia se valida contra la copia local de la Tabla 7 (`Tabla7Local`, derivada del vocabulario de la IA y comprobada contra `contracts/esquemas/catalogo-oficial.yaml`): prototipo conocido, tramo 1, 4 u 8, de una a tres rotaciones, y solo habilidades de ese prototipo ya desbloqueadas en el tramo (sin tildes ni mayúsculas se acepta y se guarda el nombre exacto). Heroes no se consulta en el arranque, porque puede no estar levantado. La primera vez que un enemigo usa una estrategia, heroes la valida con su regla (una vez por estrategia, no por enemigo). **Una estrategia inválida no tumba el servicio**: se anota en la bitácora con su motivo y ese prototipo y tramo caen a la heurística; un archivo ilegible o ausente deja a todos los enemigos con la heurística. Si heroes no responde en ese momento, la simulación se reintenta en la vuelta siguiente, igual que con cualquier otra consulta. Una prueba (`CatalogoDeEstrategiasDesdeSemillaTest`) asegura que la semilla del repositorio está completa y sin rechazos, para que esa red de seguridad no sea la forma de publicar contenido roto.

**Trazabilidad.** La jugada de un enemigo con rotaciones guarda `estrategia` (`MISION`, `PREDEFINIDA` o `HEURISTICA`) y, si es predefinida, `estrategiaId`. El héroe y un enemigo que juega siempre el ataque básico (sin rotaciones) no llevan esos campos. Es para auditar y para medir cada estrategia; el entrenamiento de la IA no los lee.

### El Máster (HU-SIM-006, RG-106, RG-107, RG-108)

Un Máster es el enemigo que aparece al azar y suelta una épica al caer (7.8.4). Qué hace el servicio con él, criterio por criterio, y la prueba que lo demuestra (`MasterReforzadoTest`, con las estadísticas reales de la Tabla 6 y el motor en memoria):

| Criterio de HU-SIM-006 | Cómo se cumple | Prueba |
|---|---|---|
| 1. Sus estadísticas superan a las de los regulares de la misión | Se prepara como cualquier enemigo (con la fracción de la regla de equilibrio, de abajo) y después `RefuerzoDeMaster`, que manda sobre esa regla, compara su vida, su defensa y el valor esperado de su ataque y de su daño (la base más la media de los dados) con los del regular más fuerte de **su** misión y **su** escalón; lo que quede por debajo sube hasta superarlo en un punto. Un Máster sanador no tiene ataque ni daño que comparar. | `criterio1_estadisticasSuperiores` (8 niveles de héroe × Normal, Heroico y Legendario), `RefuerzoDeMasterTest` |
| 2. Dos niveles por encima y épica exclusiva | Nivel = el del héroe + 2 al aparecer (no está fijo en la semilla), con las estadísticas de la vista por nivel de heroes para ese nivel (vida y defensa, por la fracción de la regla de equilibrio). Tiene su épica en el perfil que va al motor y la juega: es lo primero que prueba en su turno; no gasta poder y tiene dos turnos de recarga (6.1.2). Al cargar la semilla se rechaza que dos Máster de misión suelten la misma épica. El evento de combate lo identifica con `actor.lado = MASTER` y su `nivel`. | `criterio2_dosNivelesPorEncima`, `criterio2_tope8`, `criterio2_poseeSuEpica`, `criterio2_laLlevaAlCombate`; `SimuladorDeMisionTest` (épica); `CatalogoDeMisionesDesdeSemillaTest` (exclusividad) |
| 3. Derrotado, el jugador obtiene su épica | Al 100 % (RG-106, RC-19): el azar solo está en la aparición. Se entrega al inventario con `Idempotency-Key = mision-{id}-epica`, una sola vez por ejecución aunque el mismo Máster caiga dos veces; si la épica no está en el catálogo oficial (la del ejemplo del documento) queda en la colección del jugador y el reporte lo dice. Si el Máster aparece y no cae, no hay épica. | `criterio3_laEpicaSeEntrega`, `criterio3_sinVictoriaNoHayEpica`, `criterio3_epicaSinProducto`; `CalculadoraDeRecompensasTest` |
| 4. Fuera de las misiones no hay Máster ni épicas por esta vía | Solo `SimularEjecucion` sortea Máster, y solo `LiquidarEjecucion` entrega épicas, con origen `MISION`. El motor de combate (batallas en línea) no tiene noción de Máster y su botín descarta los elementos que no son arma, armadura o ítem. | `criterio4_sinMasterNoHayEpica`; `ClientesBotinHttpTest` en motor-combate |

**La regla de equilibrio del Máster** (decisión del PO del 2026-10-05; el docente dijo que el balance no se califica, así que basta una regla simple, documentada y con prueba). *El Máster pelea con una fracción de la vida y la defensa completas de su prototipo en su nivel; la fracción depende del nivel del héroe y deja a un héroe del nivel recomendado ganándole cerca de la mitad de las veces.*

- **Por qué.** Desde D-42 los regulares llevan vida y defensa provisionales bajas (12 a 30 de vida en el Templo). El Máster, con las estadísticas completas de su prototipo (unos 288 de vida), era casi imbatible: en el Templo, un héroe de nivel 8 ganaba el 34,4 % sin Máster y el 1,4 % con el Máster aparecido. La épica del Máster es la única fuente de épicas (RG-085) y quedaba cerrada en la práctica.
- **Qué toca y qué no.** Solo la vida y la defensa, antes del escalón. No cambia el nivel (héroe + 2, tope 8: RG-107), el ataque, el daño ni la épica, que sigue cayendo al 100 % (RG-106). El piso del criterio 1 (`RefuerzoDeMaster`) se aplica después y manda: ninguna fracción deja al Máster por debajo de un regular.
- **Dónde vive.** Como dato versionado en [`semilla/refuerzo-del-master.json`](src/main/resources/semilla/refuerzo-del-master.json) (`version`, `notas` con la justificación y las tasas medidas, y `fraccionPorNivelDelHeroe`). Lo lee `ReglaDelMaster` aquí y el simulador de motor-combate (`SimuladorDeMisiones`) como dato, sin depender de este código. Un archivo ilegible no tumba el arranque: se anota como error y el Máster pelea completo, como antes; que el del repositorio es válido lo garantiza `ReglaDelMasterTest`.
- **Por qué una fracción por nivel y no una sola.** Un héroe de nivel 1 enfrenta a un Máster de nivel 3 (el triple de estadísticas) y uno de nivel 8 a uno de su mismo nivel; además, desde el nivel 2 el Máster ya trae habilidades del nivel 4 que el héroe no tiene. Con una fracción única el promedio por nivel iba de 1 % a 78 % (con 0,50) o de 8 % a 80 % (con 0,30), nunca cerca de la mitad en todos.
- **Cómo se mide.** `BalanceDelMasterTest` (motor-combate): duelo 1 contra 1, héroe de nivel L a vida completa contra el Máster de nivel min(L+2, 8), con el motor real, por cada misión de la semilla y los seis prototipos que atacan, contra el Máster afín de la Tabla 20 y, en el Templo, contra «Sombra del Olvido»; exige entre 35 y 65 % en cada cifra, con semillas fijas. Si cambia el motor, la Tabla 6 o una semilla, esa prueba dice qué nivel recalibrar.

| Nivel del héroe | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Fracción de la vida y la defensa | 0,17 | 0,15 | 0,22 | 0,47 | 0,55 | 0,58 | 0,80 | 0,50 |
| Gana el héroe antes (Máster completo) | 0,0 % | 0,0 % | 0,0 % | 0,3 % | 0,8 % | 10,8 % | 29,2 % | 32,5 % |
| Gana el héroe con la regla | 54,7 % | 47,8 % | 50,6 % | 49,4 % | 48,1 % | 48,9 % | 58,3 % | 59,7 % |

Promedio de los seis prototipos contra el Máster afín. El reparto entre prototipos es muy desigual (el Guerrero Tanque casi no hace daño, Tabla 21, y casi nunca gana; el Mago de Fuego casi siempre) y es del juego, no de la regla. Contra «Sombra del Olvido» en el Templo (nivel 8): de 11,1 % a 39,4 %.

**El Templo completo, solo para informar** (héroe de nivel 8, sin equipo): sin Máster 34,4 %; con el Máster aparecido, antes 1,4 % y con la regla 5,8 %. Sigue bajo aunque el duelo suelto esté parejo: el Máster se cuela entre los 18 regulares y el héroe llega a él con la vida arrastrada, y los regulares provisionales del Templo (defensa de hasta 84) suben por el piso del criterio 1 la defensa del Máster a 85 sea cual sea la fracción; aun con la fracción en cero el Templo con Máster no pasa del 20 %. Abrir más la épica en el Templo exige cambiar esos regulares provisionales, decisión del PO.

**Decisiones provisionales del PO** (el documento no da la cifra o la regla):

- **El tope del nivel.** RG-107 dice que un héroe de nivel 8 enfrenta un Máster de nivel 10, pero el nivel máximo es 8 (6.1.1) en heroes (`GET /heroes/{nombre}/niveles/{nivel}` responde fuera de rango más allá del 8) y en el motor (`nivel` de 1 a 8 en `motor-combate.yaml`). Hoy el Máster de un héroe de nivel 7 u 8 es de nivel 8: contra un nivel 8, del mismo nivel. La superioridad se mantiene por el refuerzo. Permitir el nivel 10 exige subir el máximo en `heroes.yaml` y en `motor-combate.yaml` (contratos de otro dueño) y que heroes publique las estadísticas de los niveles 9 y 10.
- **Cuánto es «superior».** Un punto por encima del regular más fuerte (`RefuerzoDeMaster.MARGEN_MINIMO`, con `FACTOR_SOBRE_EL_REGULAR_MAS_FUERTE` = 1,0). La superioridad real sale de los dos niveles de más; el piso solo la garantiza en los bordes (tope 8, un prototipo de menos vida, dados que no escalan). Si el PO quiere Máster más duros basta subir el factor, por ejemplo al 1,5 del escalón Heroico.
- **Las épicas de la Tabla 20.** Un Máster de misión puede soltar la épica de una fila de la tabla (decisión de HU-MIS-012: «Hija de la Escarcha» y «Frío concentrado»); lo que se rechaza al arrancar es que dos Máster de misión suelten la misma. El Máster afín de la Tabla 20 sí comparte épica con ese Máster de misión.
- **Una épica que el motor no conoce.** El motor solo conoce las ocho de la Tabla 20. La del ejemplo del documento («Velo de Sombras») la rechaza como acción desconocida: se anota una vez en el evento y el Máster pelea con su estrategia.

### La IA con modelo propio (HU-SIM-008, RF-MOT-59, RF-ONL-22)

La IA de los personajes propios y de los adversarios puede apoyarse en una **red neuronal pequeña, propia y entrenada por el equipo** (decisión del PO, 2026-10-01): PyTorch sobre los eventos de combate de abajo, exportada a ONNX (≈18 KB) y ejecutada aquí con ONNX Runtime. **El modelo propone y la regla acota.** Lo entrenado y su receta viven en [`ia/`](ia/README.md); lo que corre en el servicio está en `nexus.misiones.ia`.

- **Apagada por omisión** (`MISIONES_IA_MODELO_HABILITADO=false`). Apagada, sin ruta, con un archivo ausente, dañado o de otras características, decide la regla de heroes y la simulación es idéntica a la de antes, evento por evento (probado). Un modelo que falla al cargarse o al inferir se registra en la bitácora y decide la regla; nunca un error al jugador.
- **Candidatas.** En cada turno, `DecisorConModelo` pregunta a la regla lo de siempre y, además, la siguiente jugada viable de cada rotación de menor prioridad (una llamada a heroes por rotación, sin reimplementar aquí su viabilidad) y suma el ataque básico. Las candidatas respetan la rotación y su cursor, el costo en poder y la recarga **porque las dijo heroes**: el modelo no puede elegir fuera de rotación ni sin poder.
- **Quién decide.** El modelo puntúa cada candidata a partir de un vector de 54 características (vida, poder, nivel, turno y efectos de los dos, prototipos, acción y costo; definición única en `Caracteristicas.java` y en `ia/nexus_ia/caracteristicas.py`, comprobadas una contra otra con casos dorados). Decide el modelo si su mejor candidata tiene al menos `MISIONES_IA_MODELO_CONFIANZA_MINIMA` (0,6) de probabilidad y le gana a la jugada de la regla; con empate, confianza baja, un error o sin contexto del duelo, **gana la prioridad de la regla**.
- **Costo en llamadas.** Con el modelo encendido, una llamada a heroes por turno más hasta dos (una por cada rotación de menor prioridad que quede por mirar). Con tres rotaciones viables, tres en vez de una.
- **Carga segura.** El `modelo.onnx` debe traer su `modelo.json` al lado; se rechaza si la versión o la dimensión de las características no son las de este servicio, si el hash no es el del archivo, si el grafo no tiene la entrada y la salida esperadas, o si tres entradas de ejemplo no dan, al correrlas aquí, lo mismo que dieron en PyTorch.
- **Trazabilidad.** Cada jugada guarda quién la decidió (`decididaPor`: `REGLA` o `MODELO`), la versión del modelo y las candidatas con su puntaje, para poder medir después al modelo contra la regla y reentrenarlo con lo que hizo.

| Variable | Qué hace |
|---|---|
| `MISIONES_IA_MODELO_HABILITADO` | `true` enciende el modelo; `false` (por omisión) deja solo la regla |
| `MISIONES_IA_MODELO_RUTA` | ruta del `modelo.onnx` (su `modelo.json` en la misma carpeta) |
| `MISIONES_IA_MODELO_CONFIANZA_MINIMA` | probabilidad mínima de la mejor candidata para que decida el modelo (0,6) |

El modelo de producción **no está en el repositorio**: se entrena con los eventos reales de `eventos_de_combate` (`python -m nexus_ia.entrenar`, ver `ia/README.md`) y se entrega por la ruta de arriba. El que hay en `src/test/resources/ia` se entrenó con datos sintéticos y es solo de pruebas (`"sintetico": true`).

### Los turnos quedan registrados

Cada turno de cada combatiente se guarda en la colección `eventos_de_combate` (índice único `ejecucionId + secuencia`; el id del documento es `{ejecucionId}:{secuencia}`). Es lo que consumirá el modelo de IA de HU-SIM-008. Se escribe **antes** que la ejecución terminada y reemplazando lo que dejara un intento anterior. Campos de cada documento:

| Campo | Contenido |
|---|---|
| `ejecucionId`, `misionId`, `secuencia` | la ejecución, la misión y el orden del turno dentro de ella (desde 1, sin saltos) |
| `encuentro`, `enemigo`, `turno` | número del encuentro (el jefe va al final), nombre del enemigo y ronda dentro del encuentro (desde 1) |
| `actor`, `oponente` | quien juega y contra quién: `lado` (`HEROE`, `ENEMIGO`, `JEFE`, `MASTER`), `nombre`, `prototipo`, `nivel` (HU-SIM-008 añadió `oponente`: así un turno se basta solo para entrenar aunque el rival caiga antes de actuar) |
| `antes`, `despues` | `{actor, oponente}`; cada uno con `vida`, `vidaMaxima`, `poder`, `poderMaximo`, `recargas` (`accion`, `turnosRestantes`) y `efectos` (`nombre`, `tipo`, `valor`, `turnos`). `antes` es el estado al decidir (ya aplicado el inicio del turno) |
| `alIniciar` | lo que pasó al empezar el turno: efectos por turno, poder recuperado (`tipo`, `combatiente`, `origen`, `efecto`, `cantidad`) |
| `jugada` | ausente si el actor cayó al empezar su turno. `decidida` (lo que eligió la IA), `ejecutada` (lo que se jugó; nula si el motor no admitió ni el ataque básico), `enValorBase`, `costoDecidido`, `costoDePoder` (el gastado de verdad), `rechazadas` (`accion`, `motivo` del 409), `resultado` y, desde HU-SIM-008, `decididaPor` (`REGLA` o `MODELO`), `versionDelModelo` (si se consultó al modelo), `candidatas` (`accion`, `costoDePoder`, `rotacion`, `puntaje`: lo que puntuó el modelo; vacía si no se consultó) y, desde HU-SIM-004, `estrategia` (`MISION`, `PREDEFINIDA` o `HEURISTICA`: de dónde salieron las rotaciones con que jugó un enemigo) y `estrategiaId` (la predefinida, si lo es). Ambos ausentes en el héroe y en un enemigo sin rotaciones |
| `jugada.resultado` | `categoria`, `acierta`, `ataqueResuelto`, `defensaObjetivo`, `porcentajeDano`, `danoBase`, `danoAplicado`, `critico` y `sucesos` (lo que dijo el motor; `combatiente` y `origen` hablan de `HEROE`/`ENEMIGO`/`JEFE`/`MASTER`). `categoria` y compañía son nulas si la acción no golpea |

## Dependencias y configuración

REST síncrono con la credencial de servicio de misiones (`client_credentials`, ADR-001/ADR-005), tiempos de espera acotados, un corta circuitos por dependencia (HU-DIS-003) y la traza propagada (regla 5). Sin bus de mensajes y sin importar clases de otros servicios.

| Dependencia | Variable | Para qué |
|---|---|---|
| inventario (1.6.0) | `INVENTARIO_BASE_URL` | dueño del héroe, bloqueo, liberación con experiencia, entregas, y lo que lleva al combate (estadísticas con equipo, equipamiento, épicas). Si la consulta interna del héroe responde 409 (inventario histórico: su producto no tiene id UUID), el héroe se lee de la vitrina del jugador (`GET /api/v1/inventario/elementos` con `X-User-Name`) |
| productos | `PRODUCTOS_BASE_URL` | prototipo del producto HÉROE y nombre del equipo y las épicas (como los conoce el motor) |
| heroes (1.2.0) | `HEROES_BASE_URL` | validaciones, IA de cada turno (con el modelo encendido, hasta dos consultas más por turno), vista por nivel (con fórmulas), habilidades por nivel, experiencia por enemigo |
| motor de combate (1.2.0) | `MOTOR_COMBATE_URL` | cada turno y cada acción de la simulación (`/combate/turnos`, `/combate/acciones`) |
| ms-finanzas (créditos 1.4.0) | `CREDITOS_URL` | abono de créditos |
| correo (1.4.0) | `CORREO_URL` | `POST /correos/mision` |
| ms-identidad | `IDENTIDAD_URL`, `IDENTIDAD_JWKS_URL`, `DIRECTORIO_ACTIVO_*` | contacto del jugador, JWKS y credencial de servicio |

El resto de variables está en [`.env.example`](.env.example) y explicado en `src/main/resources/application.properties`.

## Decisiones del PO pendientes (valor provisional)

| Decisión | Valor provisional | Dónde se cambia |
|---|---|---|
| Experiencia por completar según dificultad (6.1.1 no da cifras) | 0 | `MISIONES_XP_COMPLETAR_*` |
| Penalización por abandonar (HU-MIS-015 no la cuantifica) | se pierden todas las recompensas de esa ejecución, experiencia incluida | código (`CancelarEjecucion`) |
| Estadísticas de los enemigos regulares de «El Templo Olvidado» | prototipos Guerrero Armas / Guerrero Tanque / Mago Fuego en nivel 8 con vida y defensa PROVISIONALES (18/70, 30/84, 12/70: con la vida completa del prototipo los 18 encuentros no los superaba nadie) | semilla 1.1.0 (D-42) |
| «Habilidades potenciadas» del jefe | sin cifra: la estrategia predefinida de su prototipo (como todo enemigo sin estrategia escrita), con los 100 de vida del documento | `semilla/estrategias-de-enemigos.json` / semilla |
| Estrategia de cada enemigo (7.8.6 dice «estrategias predefinidas» pero no las escribe) | las 24 de `estrategias-de-enemigos.json`: el ataque primero; defensa y sanación en la rotación de menor prioridad; dentro de cada familia, la de mayor valor esperado según la Tabla 7 | `semilla/estrategias-de-enemigos.json` |
| Enemigos sanadores (Chamán, Médico) y Reanimación | sin ataque, su estrategia solo sana y el respaldo es la sanación básica; Reanimación no entra porque cura a un compañero y el enemigo pelea solo | `semilla/estrategias-de-enemigos.json` |
| «Mazo completo equipado» (7.8.6) | al menos un arma, armadura o ítem (como ADR-004) | `MatricularHeroe` |
| Probabilidad del Máster del ejemplo («0.15% (15% de probabilidad)») | 15 %; la Tabla 20 se toma literal (0,04 % = 0,0004) | semilla |
| Nivel recomendado 15 con héroes hasta nivel 8 | **resuelto (D-42)**: se publica con 8; el catálogo rechaza cualquier nivel fuera de 1..8 | semilla 1.1.0, `Mision` |
| ¿La épica exige completar la misión? (HU-MIS-007 dice sí; 7.8.4 solo derrotar al Máster) | manda el documento | `MISIONES_EPICA_EXIGE_COMPLETAR` |
| Multiplicador del escalón Mítico | sin cifra: no se ofrece | `MISIONES_MULTIPLICADOR_MITICO` |
| Créditos por escalón («mejores recompensas») | el mismo factor que las estadísticas (×1,5 y ×2) | `MISIONES_CREDITOS_*` |
| Tiradas de Máster en exploración («mayor probabilidad») | una por cada 24 horas | `TiradaDeMasters` |
| «Explorar las 5 cámaras» | superar todos los encuentros regulares | semilla |
| Recompensas del ejemplo que no están en el catálogo oficial (Cofre de Bronce, Fragmentos del Sello Antiguo, «Piel del Guardián», «Espada del Templo», «Velo de Sombras», el título) | se informan en el reporte como `sinEntregar`; no se inventan productos | semilla (`productoId`) |
| Preferencias de correo por categoría | no hay dónde leerlas: `debeEnviarCorreo` siempre verdadero | `MISIONES_CORREO_ACTIVO` apaga el correo del módulo |

## Límites conocidos

- Una rotación de defensa o sanación de un enemigo se juega cuando las de ataque no son viables, no porque esté herido: la regla de héroes no condiciona las rotaciones a la vida (HU-SIM-004, pendiente P-E de HU-SIM-002). Si un enemigo defensivo no debe malgastar el turno, hay que pedirle a héroes ese condicionante.
- La IA solo elige acciones de la Tabla 7 (las que traen las rotaciones); las épicas del héroe viajan al motor pero la IA de rotaciones no las elige. El Máster sí juega la suya (ver «El Máster»), así que hoy el Máster usa una épica que el héroe del jugador, con las suyas equipadas, no usa en misión.
- Si el jugador ya tiene la épica que gana, el inventario le crea otro elemento: la clave de idempotencia protege a una ejecución de entregar dos veces, no a un jugador de ganar la misma épica en dos ejecuciones. Si el PO prefiere que no se repita, hay que decidir quién lo evita (misiones antes de entregar, o inventario al recibir).
- Simular una misión entera son cientos de llamadas síncronas al motor y a heroes (tres por turno de combatiente; hasta cinco con el modelo de IA encendido); el trabajo las atiende una ejecución tras otra.
- El modelo de IA aprende, mientras solo haya eventos de la regla, sobre todo a imitarla: lo nuevo viene del peso por resultado y de los eventos que genere el propio modelo encendido (con sus candidatas). El ciclo es encender, acumular partidas y reentrenar (`ia/README.md`).
- ONNX Runtime suma ≈55 MB al jar (bibliotecas nativas de tres plataformas, que no se cargan con el modelo apagado). Su consumo de memoria nativa encendido no está medido en la instancia de contenido: medirlo con la compuerta de capacidad antes de encenderlo en DEV.
- `POST /api/v1/inventario/entregas` lo implementa la fase B4. Si el inventario de un entorno aún no la trae, las entregas quedan pendientes y se reintentan (el reporte lo dice con `entregaPendiente`), sin perderse.
- El correo de fin de misión (RF-COR-005) usa `GET /api/v1/internal/usuarios/{uid}/contacto` de ms-identidad, ya implementada. Si un entorno no la sirve, el correo queda como no enviado y lo demás se entrega igual.
- Avisos en la bandeja del jugador (HU-NOT-004 / RF-NOT-004, contrato 1.2.0): finalización con el detalle de cada recompensa, épica obtenida y misiones de historia desbloqueadas, por `POST /internal/notifications` con `tipo` `MISION` e `id` estable por ejecución (un reintento no duplica). Quedan fuera, porque piden decisión: avisos de misiones de tiempo limitado (no hay ninguna en el catálogo ni umbral de «próximas a expirar»), logros e hitos (no hay sistema de logros), preferencias por categoría y agrupación de avisos (eso es de notificaciones). `MISIONES_AVISOS_ACTIVO=false` los apaga.
- Simulación que falla: se aplaza con espera creciente (30 s, 1, 2, 4 min; tope 5 min) en vez de reintentarse en cada vuelta; así no ocupa el lote de las demás. El héroe sigue en misión y el jugador la puede cancelar.
- Entrega: un 401, 408 o 429 de otro servicio se reintenta (credencial que no vale, tiempo o cupo); antes dejaba el paso FALLIDO y, si era la liberación, el héroe En misión para siempre.

## Despliegue

Va al host de contenido (`nexus-contenido-dev`, IP elástica 34.193.90.11), puerto **8105** del host y 8080 dentro del contenedor, con su base `misiones` en la Mongo de contenido (`docker-compose.contenido.yml`, bloque `srv-misiones`). El borde publica `/api/v1/misiones` hacia `34.193.90.11:8105` (`infrastructure/red-balanceo/borde-dev.conf`). Su imagen la construye y publica `cd.yml` como la de sus vecinos: el catálogo (`infrastructure/despliegue/servicios.json`) es lo único que hace falta para que el flujo lo conozca.

**28-sep (topología, fase 1): `desplegableDev: true`.** Lo que le faltaba en ese host no era código, y ya está resuelto así:

1. **Credencial de servicio.** `misiones` está en `CLIENTES_DE_SERVICIO` de `scripts/cd/desplegar.sh`, y su valor es el secret `SECRETO_SERVICIO_MISIONES` del entorno `dev`, que `cd.yml` pasa a los **dos** jobs de dev: el emisor (ms-identidad, host de plataforma) y este servicio (host de contenido) ven el mismo (`repartir_credenciales_de_servicio`; prueba en `scripts/cd/pruebas/credenciales-de-servicio.sh`). El compose lleva `DIRECTORIO_ACTIVO_CLIENT_ID: misiones` literal, como exige el guardián de catálogo. En local, levantar `srv-misiones` con `docker-compose.contenido.yml` pide exportar `SECRETO_SERVICIO_MISIONES` con cualquier valor: con client-id y sin secreto no arranca.
2. **Red de salida.** Desde el host de contenido alcanza ms-identidad (8089) y ms-finanzas (8093): los dos admiten a `34.193.90.11/32` en el grupo de seguridad de plataforma desde B12. La bandeja de notificaciones (8085) está en el bloque 8081–8088 que ese mismo grupo admite desde el host de contenido, así que los avisos van encendidos (`NOTIFICACIONES_URL`, `MISIONES_AVISOS_ACTIVO=true`). El correo (8082) está en el mismo bloque, comprobado el 5-oct con la acción `inventario` de infra-dev (corrida 37264710414): desde RFINAL-01 va **encendido por omisión** (`MISIONES_CORREO_ACTIVO=true` en `docker-compose.contenido.yml`; ponerla a `false` en el entorno lo apaga sin tocar el archivo).
3. **Capacidad.** La compuerta de capacidad corre ahora también en el job de contenido (antes y después, con el núcleo de ese host: heroes, inventario, productos y motor). Medido el 28-sep: 722–740 MiB disponibles antes de esta fase (`CAPACIDAD.md`).
4. **Lo que falta no es nuestro: la entrada.** El borde (host de plataforma) llama a `34.193.90.11:8105`, y el grupo de seguridad de contenido es de la cuenta del Grupo 2. Hasta que admita el 8105 desde `35.168.124.119/32` (`infrastructure/entornos/contenido/reglas-entrada.json`; lo aplica `infra-dev.yml` con `accion=reglas-sg` en cuanto exista `vars.AWS_ROLE_ARN_CONTENIDO`, o su dueño desde la consola), `/api/v1/misiones` responde 504 a los 5 s y la vista enseña su estado «sin abrir», igual que hoy. El servicio corre y responde en su host mientras tanto.

Primer despliegue tras fusionar: un `workflow_dispatch` de `cd.yml` con `perfil=base` (el host de plataforma registra el cliente en el emisor; `desplegar.sh` lo recrea en el paso 3d) y otro con `servicios=misiones,ms-subastas` (host de contenido). Comprobación: `diagnostico-dev.yml` con `ambiente=dev`, sección «RED ENTRE HOSTS» → `34.193.90.11:8105` deja de dar `000`.

**Semilla extra del banco (HU-SIM-005/006).** `MISIONES_SEMILLA_EXTRA` es la ruta de un archivo de semilla en disco, que no está en el jar. Solo `tests/e2e/compose.yml` la pone: monta `tests/e2e/semilla-misiones-banco.json`, con «[PROVISIONAL DE DEV] Máster seguro», una misión de una hora (2 s en el banco) cuyo Máster aparece con probabilidad 1,0 y trae la épica de la Tabla 20 del Guerrero Tanque con su `productoId` del catálogo oficial. Tiene las mismas reglas que la provisional (origen `PROVISIONAL_DEV`, nombre marcado, sin Tabla 20); `docker-compose.contenido.yml` ni la monta ni la nombra, así que en AWS DEV el archivo no existe, el Máster seguro no se publica y ninguna épica se regala; y una ruta a un archivo que no está impide arrancar. Ese Máster pelea con vida 1 y defensa 0 (`MasterDeMision.vida` y `defensa`, opcionales como en el jefe): el héroe del kit del banco, un Guerrero Tanque de nivel 1, no vence nunca a un Máster de nivel 3 con las estadísticas del catálogo (0 de 2.000 con el motor real), así que sin esto no habría semilla que ganara. Ningún Máster del juego las fija.

En el banco E2E (`tests/e2e/compose.yml`) corre entero: credencial propia, reloj acelerado (una hora de misión = 2 s) y la semilla provisional de desarrollo. `tests/e2e/misiones.e2e.spec.js` recorre el ciclo desde la vista: tablón, matrícula con héroe y estrategia, simulación en segundo plano, reporte con experiencia y créditos, progresión persistida en el inventario y en ms-finanzas, estrategia guardada, misión en curso y cancelación. Su jar entra en la imagen del banco por `tests/e2e/Dockerfile.servicio.dockerignore`.

## Cómo correr

```
./gradlew :services:contenido:misiones:check    # pruebas (dominio, casos de uso, MongoDB y HTTP reales) + JaCoCo ≥ 80 %
./gradlew :services:contenido:misiones:bootRun  # con MongoDB local y las dependencias de .env.example
cd services/contenido/misiones/ia && python -m pytest pruebas -q   # el entrenamiento de la IA (ver ia/README.md)
```

Las pruebas de integración levantan MongoDB 8 con Testcontainers (hace falta Docker) y las siete dependencias como servidores HTTP falsos; cada respuesta se valida contra el contrato. El pacto de consumidor misiones → ms-inventario se escribe en `contracts/pactos/`.
