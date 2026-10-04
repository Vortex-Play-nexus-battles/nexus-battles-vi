# Motor de combate

Nucleo de las reglas de combate de THE NEXUS BATTLES VI. El modulo usa Java
21, dominio puro y TDD para que las reglas puedan integrarse despues con la
capa de aplicacion sin acoplarlas a transporte o persistencia.

## Alcance implementado

| Historia | Que cubre |
|---|---|
| HU-JUE-001 | Sorteo uniforme del orden inicial, cada participante exactamente una vez, secuencia inmutable durante el combate y avance circular entre rondas |
| HU-JUE-002 | Una sola accion por turno, avance al resolver o expirar y la misma duracion configurable para todos los participantes |
| HU-JUE-005 | Fallecimiento al llegar a cero vida, cierre individual o por equipos y rechazo de acciones posteriores |
| HU-JUE-007 | Tope de 6 minutos, derrota por 1 minuto de inactividad, vida conservada y estrategia de desempate |
| HU-JUE-008 | Proteccion contra dano a companeros en combate cooperativo, salvo que la accion permita afectar aliados expresamente
| HU-JUE-010 | Botin por derrota con evaluacion independiente de la tasa de caida de cada arma, armadura o item equipado o almacenado por el enemigo |

El sorteo usa Fisher-Yates y `SecureRandom` en produccion. Las pruebas inyectan
un generador con semilla para que la validacion estadistica sea reproducible.

Todos los cierres producen un único `ResultadoPartida` y pasan por el puerto
`AlCerrarPartida`, que permite conectar recompensas e historial sin tratar de
forma distinta los cierres por supervivencia, tiempo o inactividad.

El botin se procesa unicamente cuando un ataque cambia al objetivo de activo a
derrotado con cero puntos de vida. El motor consulta la tasa configurada en
productos, identifica en inventario si cada candidato estaba equipado o
almacenado y registra en el inventario del ganador todos los objetos cuyo sorteo
resulte exitoso. Las integraciones usan `PRODUCTOS_URL` e `INVENTARIO_URL` sin
modificar los servicios que implementan esas historias.

El criterio concreto para un empate exacto no está documentado todavía en el
repositorio. `CriterioDesempate` lo recibe por inyección para que el equipo
pueda conectar la decisión aprobada sin inventar una regla provisional.

## El combate de verdad (B7, contrato 1.2.0)

`POST /api/v1/combate/acciones` resuelve UNA acción del turno y
`POST /api/v1/combate/turnos` lo que pasa al EMPEZAR uno. Son sin estado: el
estado de los combatientes (vida, poder, cargas, efectos, último golpe) llega
entero y sale entero, y lo guarda `salas-partidas`. Solo con credencial de
servicio.

| Regla del documento | Dónde vive | Prueba |
|---|---|---|
| §6.1.4 índice pseudoaleatorio con distribución normal (no uniforme), truncado a 1..8000 | `IndiceNormal` | `IndiceNormalTest`, `TablaDeEfectosEnCombateTest` |
| Tablas 21 y 22: reparto por tipo de héroe y porcentaje de daño; crítico de 120 a 180 % | `DistribucionEfectos.dePrototipo`, `MotorDeAcciones.golpear` | `DistribucionPorPrototipoTest`, `TablaDeEfectosEnCombateTest` |
| Tabla 23: el crítico del equipo resta de «no causar daño» | `DistribucionEfectos.ajustarCritico`, `EquipoDeCombate` | `EquipoEnCombateTest`, `TablaDeEfectosEnCombateTest` |
| §6.1.1 poder: +2 por turno, coste por acción, valor base sin poder | `MotorDeAcciones` (`iniciarTurno`, `planEnValorBase`) | `InicioDeTurnoTest`, `MotorDeAccionesTest` |
| Tabla 7: las 24 acciones como reglas; §6.1.2 un turno de carga y multiplicador de nivel | `Reglamento` (datos en heroes, efecto aquí) | `AccionesEspecialesTest` |
| Tabla 20: épicas, dos turnos de recarga, efecto potenciado del héroe afín | `Reglamento.tabla20` | `EpicasEnCombateTest` |
| Tablas 8 a 19: efectos de combate de armas e ítems | `EquipoDeCombate` | `EquipoEnCombateTest` |
| §6.1.3 cooperativo: sin daño a compañeros; mismas reglas para la IA | `MotorDeAcciones.elegirObjetivo`, `PoliticaDeLaMaquina` | `MotorDeAccionesTest`, `PoliticaDeLaMaquinaTest`, `MatrizDeAccionesTest`, `SimulacionDeLaMaquinaTest` |
| §6.1.1 el sanador no inflige daño | `Reglamento.exigirQueAtaque` | `AccionesEspecialesTest`, `AccionesDisponiblesTest` |

**Datos contra reglas.** El nombre, el coste, la carga y el nivel de
desbloqueo de cada acción son datos del catálogo de héroes (heroes.yaml 1.2.0);
lo que la acción HACE es una regla y vive una sola vez en `Reglamento`. El
catálogo se consulta por `CatalogoDeCombateHttp`, con caché.

**La IA** (`DECISION_DE_LA_MAQUINA`) es una **IA táctica por reglas** (D-41,
auditoría del 4-oct; sustituye a la política fija de D-B7-12). No es una IA
entrenada: el «aprendizaje profundo» del §7.6 queda fuera de este bloque
(D-B7-13) y no se simula.

`PoliticaDeLaMaquina` enumera cada acción disponible con cada objetivo que esa
acción admite (un ataque, solo rivales en pie), la ensaya con
`MotorDeAcciones.ensayar` —el mismo código que resuelve una jugada de verdad,
sobre una copia de la mesa— y juega la de mejor puntaje esperado: vida de cada
bando a escala √ (rematar y curar al que está en apuros valen más), seguir en
pie, efectos activos, poder útil y, en DIFÍCIL, la respuesta esperada de los
rivales. Los ensayos usan un generador propio sembrado con el estado: no ve ni
gasta el azar de la partida, y el mismo estado da la misma decisión.

| Dificultad | Ensayos por jugada | Anticipa la respuesta | Descuidos |
|---|---|---|---|
| `FACIL` | 4 | no | 40 % de las veces, una de sus 3 mejores |
| `NORMAL` | 12 | no | nunca |
| `DIFICIL` | 24 | sí | nunca |

**Equilibrio de las misiones** (D-42). `BalanceDeMisionesTest` juega cada
misión publicada por el servicio de misiones —lee sus semillas como datos, no
su código— con este motor, como la simulación de ese servicio (encuentros en
orden, vida arrastrada, poder y cargas de cero en cada duelo, 100 rondas por
duelo, enemigos en el nivel recomendado). Los enemigos juegan la estrategia
predefinida de su prototipo y tramo de nivel (`estrategias-de-enemigos.json`,
HU-SIM-004) con la regla de rotaciones del héroe; sin ese archivo, su ataque
más fuerte al alcance. La CI de motor-combate corre también cuando cambia una
semilla de misiones.

**Simulaciones** (`SimuladorDeCombates`, partidas completas contra el motor
real). `SimulacionDeLaMaquinaTest` corre en CI con semillas fijas: matriz 8×8
de prototipos en niveles 1 y 8, 3 contra 3 con sanadores en las tres
dificultades, y la IA contra la regla fija anterior. El informe completo, con
cientos de partidas, se pide a mano:

```powershell
./gradlew :services:contenido:motor-combate:test --tests '*InformeDeSimulacionTest' `
    "-Dsimulacion.informe=true" "-Dsimulacion.partidas=16" "-Dsimulacion.tope=150"
```

Resultado del 4-oct (4.896 partidas): **0 jugadas ilegales, 0 auto-daño y 0
fuego amigo**. En espejo (mismo prototipo y nivel), NORMAL gana el 68 % contra
la regla fija (21 % pierde, el resto tablas), DIFÍCIL el 61 % y FÁCIL el 59 %;
NORMAL gana el 61 % contra FÁCIL y DIFÍCIL el 49 % contra NORMAL (33 %).
Decidir cuesta de media 0,3 ms en un duelo (NORMAL) y 8 ms en un 3 contra 3 de
nivel 8 (DIFÍCIL). Las tablas son de las propias reglas: un sanador no puede
ganar un duelo (§6.1.1) y el espejo de Guerrero Tanque desde el nivel 4 no se
hace daño en 150 turnos (en partida real cierra el tope de 6 minutos).

### Configuración (regla 10, todas con valor por omisión seguro)

| Variable | Por omisión | Qué es |
|---|---|---|
| `HEROES_URL` | `http://localhost:8081` | Servicio de héroes |
| `MOTOR_HEROES_CACHE_SEGUNDOS` | `300` | Vigencia de la ficha de combate en caché; `0` la desactiva |
| `MOTOR_INDICE_MEDIA` | `4000.5` | Media del índice normal (D-B7-01, el documento no la fija) |
| `MOTOR_INDICE_DESVIACION` | `1333.3333333333333` | Desviación del índice normal (D-B7-01) |
| `MOTOR_IA_DIFICULTAD` | `NORMAL` | Cómo decide la IA (D-41): `FACIL`, `NORMAL` o `DIFICIL`; una mal escrita no arranca |

Con el índice normal, el porcentaje de FILAS de un efecto no es su
probabilidad: con los valores por omisión, el 60 % de filas de «causar daño»
del Guerrero Armas (1-4800) sale el 72,6 % de las veces.

## Como correr

```powershell
.\gradlew.bat check
```

La tarea `check` ejecuta JUnit y falla si la cobertura de lineas es inferior al
80 %. El reporte HTML queda en `build/reports/jacoco/test/html/index.html`.

## Pendiente

- Configurar el `CriterioDesempate` cuando el cliente documente la regla.
- Exponer el inicio de partida desde la capa de aplicacion; HU-JUE-001 y
  HU-JUE-002 no exigen por si solas un endpoint.

## Despliegue

Este servicio se despliega en el host propio del dominio de contenido (`infrastructure/entornos/contenido/`), por el flujo `cd.yml` (job `desplegar-contenido-dev`) en cada push a `develop` que toque `services/contenido/motor-combate`. Queda publicado en el puerto **8104** del host (8080 dentro del contenedor), consumiendo héroes, productos e inventario mediante `HEROES_URL`, `PRODUCTOS_URL` e `INVENTARIO_URL`. Salud: `http://<ip-del-host>:8104/actuator/health`.

La imagen lleva la etiqueta propia de este servicio (`TAG_MOTOR_COMBATE`, el sha corto del push que lo cambió); las dependencias que no cambiaron conservan la etiqueta que ya tienen desplegada. Lo resuelve `resolver_etiquetas_contenido` en `scripts/cd/desplegar.sh`.
