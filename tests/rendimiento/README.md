# Pruebas de rendimiento (k6)

Suite de carga de Nexus Battles VI. Mide la latencia extremo a extremo de los
caminos que fija **RNF-REN-001** (500 ms) y la búsqueda indexada de
**HU-REN-003 CA-01**, y produce un informe adjuntable como evidencia de Sprint.

El percentil de evaluación es **p95**, por
[`docs/gobierno/ADR-006-percentil-de-evaluacion-de-latencia.md`](../../docs/gobierno/ADR-006-percentil-de-evaluacion-de-latencia.md).
Los 500 ms no son negociables (los fija el requisito y el Project Charter); el
percentil con que se comprueban es una convención de medición del equipo.

```
tests/rendimiento/
  README.md                       este archivo
  k6/
    rendimiento.js                punto de entrada: options, scenarios, thresholds, handleSummary
    lib/entorno.js                variables de entorno, validación y fallo temprano
    lib/perfiles.js               smoke / baseline / load, y el techo de VUs
    lib/medicion.js               series por escenario + serie total
    lib/sesion.js                 login de setup() y comprobación del entorno
    lib/informe.js                el informe de texto y el resumen.json
    escenarios/autenticacion.js   login
    escenarios/salas.js           listado y creación de sala
    escenarios/inventario.js      búsqueda en inventario
```

---

## Qué mide cada escenario

| Escenario | Ruta | Requisito | Por qué está |
|---|---|---|---|
| `login` | `POST /api/v1/auth/login` | RNF-REN-001 | Camino obligado de todo lo demás. Si se degrada, el jugador no llega a ninguna pantalla donde se noten las otras latencias. |
| `listar_salas` | `GET /api/v1/salas?pagina&tamano` | RNF-REN-001 (RF-JUE-002) | La lectura más frecuente y la primera pantalla del flujo de juego. Consulta paginada. |
| `crear_sala` | `POST /api/v1/salas` | RNF-REN-001 (RF-JUE-001) | La escritura de referencia: no es un *insert* suelto sino la cadena completa —comprobar sanción, preguntar al inventario por el héroe equipado, persistir—. |
| `busqueda_inventario` | `GET /api/v1/inventario/elementos/busqueda?criterio&pagina` | HU-REN-003 CA-01 | La búsqueda indexada que CA-01 exige dentro del objetivo de latencia. Cruza además la red entre el host de plataforma y el de contenido. |

Los cuatro se miden **por el borde** (nginx), no contra el servicio suelto:
RNF-REN-001 habla de latencia *extremo a extremo*, y lo que sufre el jugador
incluye el proxy.

---

## La acción de partida: por qué NO está en esta suite

El quinto camino que justifica el documento de gobierno es la acción de turno
sobre el canal en tiempo real: **STOMP sobre WebSocket**, destino
`/app/partidas/{idPartida}/acciones`, endpoint `/ws`
(`contracts/websocket/salas-partidas.yaml`, canal `accionDelJugador`).

**Se dejó fuera a propósito.** No es que k6 no pueda hablar STOMP —se puede,
escribiendo los *frames* `CONNECT`/`SUBSCRIBE`/`SEND` a mano sobre `k6/ws`—; es
que el número que saldría no mediría lo que una prueba de carga tiene que medir.
Las razones, por orden de peso:

1. **El ritmo no lo decide el cliente, lo decide el juego.** Un jugador solo
   puede enviar una acción **cuando es su turno**; si no lo es,
   `PartidasStompController` responde por la cola privada con un error. Ni
   `constant-vus` ni `constant-arrival-rate` pueden gobernar eso: la carga
   ofrecida la fija la máquina de turnos del servidor. Una prueba de carga que
   no controla su propia tasa de llegada no es una prueba de carga.
2. **La preparación domina la medición.** Cada medición necesita antes crear una
   sala, pasar la puerta de héroe (que consulta a `inventario`) e iniciar la
   partida: tres peticiones HTTP y dos servicios más, por una sola acción
   medida.
3. **Es destructiva por naturaleza.** Cada acción modifica la vida persistida y
   avanza el turno; unos pocos golpes terminan la partida y obligan a crear otra.
   Un perfil `load` de dos minutos dejaría cientos de partidas a medias en un
   entorno compartido con la demo.
4. **Mezclaría dos hosts.** La acción llama a `motor-combate`, que vive en el
   host de Contenido. El número resultante no sería atribuible a
   `salas-partidas` ni al canal, sino a la suma de dos instancias EC2 y el salto
   entre ellas. Peor: en DEV `motor-combate` **no está desplegado** —no cabe en
   el `t3.small`, ver `infrastructure/entornos/plataforma/README.md`— y la acción
   responde `503 motor-de-combate-no-disponible`. Se estaría midiendo la
   latencia de un rechazo.
5. **Un `SEND` de STOMP no tiene respuesta que correlacionar.** `@MessageMapping`
   devuelve `void`. Habría que medir desde el `SEND` hasta que llega
   `partida.accion.resuelta` por `/tema/partidas/{id}`, con contabilidad escrita
   a mano que falla en silencio: si se pierde la correlación, el script sigue
   dando un número y nadie se entera.

**Dónde sí está cubierto el canal hoy**, para que la ausencia no se lea como un
agujero sin vigilancia:

- `tests/e2e/sala-de-batalla.e2e.spec.js` y `modalidades.e2e.spec.js` juegan
  combates enteros por el canal real, contra servicios reales.
- `ChatDeSalaIT` y las pruebas de `PartidasStompController` cubren el lado
  servidor.
- La instrumentación de HU-REN-001 registra la latencia del canal **dentro** del
  servicio (`shared/libs/plataforma-observabilidad`), que es una vía de medida
  distinta y complementaria a ésta.

**Si el equipo decide medir el canal con k6**, la propuesta honesta no es
reproducir la acción de turno, sino medir **el establecimiento del canal**:
handshake WebSocket + `CONNECT` + `SUBSCRIBE` a `/tema/partidas/{id}`. Eso sí
tiene tasa de llegada controlable, no muta estado, no necesita partida ni
`motor-combate`, y es parte real de lo que espera el jugador antes de ver la
pantalla de batalla viva. **No se ha implementado**: sería un escenario nuevo, y
esta suite tiene los cuatro que el documento de gobierno declaró justificados.
Discutirlo en refinamiento antes de escribirlo.

---

## Cómo se corre

### Instalar k6

k6 es un binario suelto, sin dependencias. Ver
<https://grafana.com/docs/k6/latest/set-up/install-k6/>. En CI lo instala el
workflow.

```bash
k6 version
```

### Contra el banco E2E local (perfil `smoke`)

El banco de `tests/e2e/compose.yml` levanta los servicios reales con sus bases
de datos y el borde en el puerto **8099**. `sembrar.sh` deja las cuentas de
prueba con héroe equipado, que es lo que necesita `crear_sala`.

```bash
# 1. Levantar el banco y sembrarlo (desde la raíz del repositorio)
docker compose -f tests/e2e/compose.yml up -d --build --wait
./tests/e2e/sembrar.sh

# 2. Correr el perfil smoke
mkdir -p tests/rendimiento/resultados && cd tests/rendimiento/resultados
BASE_URL=http://localhost:8099 \
USUARIO=anfitriona_e2e@nexus.test \
CLAVE=Contrasena-E2E-2026 \
PERFIL=smoke \
  k6 run ../k6/rendimiento.js

# 3. Desmontar
docker compose -f tests/e2e/compose.yml down -v --remove-orphans
```

> `anfitriona_e2e@nexus.test` / `Contrasena-E2E-2026` son la cuenta sembrada del
> banco, no una credencial: están en claro en `tests/e2e/sembrar.sh` desde que
> existe el banco. Contra cualquier entorno que no sea el banco efímero, la clave
> va por secreto (ver más abajo).

### Contra AWS DEV

**Nunca automáticamente.** DEV es el entorno de la demo: la URL que se mide es
siempre el borde del **host de plataforma**, un `t3.small` compartido, y detrás
de él responde el host de contenido, igual de pequeño (ver
[`docs/arquitectura/README.md`](../../docs/arquitectura/README.md)). Una corrida
de carga se lanza **a mano**, sabiendo que se lanza, y preferiblemente fuera de
la ventana de una demo o una sustentación.

```bash
cd tests/rendimiento/resultados
BASE_URL=http://<ip-o-dominio-de-dev> \
USUARIO=<cuenta-de-pruebas> \
CLAVE=<clave-de-esa-cuenta> \
PERFIL=baseline \
  k6 run ../k6/rendimiento.js
```

Desde GitHub: **Actions → «Rendimiento (k6)» → Run workflow**, eligiendo perfil
y URL base. Es la única vía por la que esta suite toca AWS.

---

## Variables de entorno

### Obligatorias — sin ellas el proceso se detiene antes de la primera petición

| Variable | Qué es | Ejemplo |
|---|---|---|
| `BASE_URL` | Base del **borde**, sin barra final | `http://localhost:8099` |
| `USUARIO` | Correo de la cuenta de pruebas | `anfitriona_e2e@nexus.test` |
| `CLAVE` | Su contraseña | *(por secreto fuera del banco local)* |

Ninguna tiene valor por omisión, y es deliberado: un `BASE_URL` de reserva mide
un entorno que no era el que se quería medir y entrega un informe que parece
bueno; un usuario de reserva convierte el fallo en un 401 a mitad de corrida,
que es justo el síntoma confuso que se quería evitar. Si falta una, el mensaje
dice cuál y da un ejemplo.

La cuenta de `USUARIO` tiene que **tener un héroe equipado**: si no,
`crear_sala` recibe `422 heroe-no-equipado` en todas las iteraciones. En el
banco local eso lo garantiza `sembrar.sh`.

### Opcionales

| Variable | Por omisión | Qué hace |
|---|---|---|
| `PERFIL` | `smoke` | `smoke`, `baseline` o `load` |
| `VUS` | según perfil | Usuarios virtuales. Limitado por `TECHO_VUS` |
| `DURACION` | según perfil | Segundos **por escenario** (la corrida entera son 4 × esto) |
| `PAUSA_MS` | según perfil | Pausa de reflexión entre iteraciones de un mismo VU |
| `TECHO_VUS` | `25` | Techo de seguridad. Superarlo aborta la corrida con un mensaje |
| `OBJETIVO_MS` | `500` | Umbral de latencia (RNF-REN-001) |
| `PERCENTIL` | `95` | Percentil de evaluación (ADR-006) |
| `TASA_ERROR_MAXIMA` | `0.01` | Tasa de error tolerada, en tanto por uno |
| `TAMANO_PAGINA` | `16` | Elementos por página en el listado (valor de diseño, RNF-USA-001) |
| `PAGINAS` | `3` | Cuántas páginas se rotan, para no medir siempre la misma consulta caliente |
| `CRITERIO_BUSQUEDA` | `a` | Término de la búsqueda de inventario |
| `LIMPIAR_SALAS` | `true` | Cancelar cada sala creada justo después de medirla |
| `RESUMEN_TXT` | `resumen.txt` | Ruta del informe de texto |
| `RESUMEN_JSON` | `resumen.json` | Ruta del informe en JSON |

---

## Los tres perfiles

Los escenarios corren **en serie**, escalonados con `startTime`: cada uno espera
a que termine el anterior. Si corrieran a la vez, el p95 del listado incluiría la
contención que provoca la creación de salas y la latencia no sería atribuible a
nada.

| Perfil | VUs | Duración por escenario | Pausa | Corrida completa | Para qué |
|---|---|---|---|---|---|
| `smoke` | 1 | 20 s | 0 ms | ~1 min 40 s | Comprobar que la suite y el entorno funcionan |
| `baseline` | 5 | 60 s | 500 ms | ~4 min 20 s | **La medición de referencia de RNF-REN-001** |
| `load` | 20 | 120 s | 200 ms | ~8 min 20 s | Concurrencia alta dentro de lo que aguanta DEV |

(La corrida completa incluye 5 s de margen entre escenarios.)

`smoke` **no es una medición publicable**: con 1 VU no hay muestras suficientes
para un p95 que signifique algo, y el propio informe lo avisa. Para evidencia de
Sprint, `baseline`.

### El techo de carga

**`load` no es una prueba de estrés, y el techo es 25 VUs.**

El host de plataforma en DEV es un `t3.small`: **2 vCPU y 2 GiB**, con unas 5
JVMs de Spring Boot (~320 MB de `mem_limit` cada una), PostgreSQL, Redis,
Mailpit y el borde nginx encima —los números están en
`infrastructure/entornos/plataforma/README.md`—. Con 2 vCPU, 20 peticiones
simultáneas ya son diez veces el número de núcleos: suficiente para que aparezca
el encolamiento que interesa medir, y todavía lejos del punto en que la
instancia se va a *swap* y el OOM killer elige víctima. Ese README avisa de que
la víctima puede ser `salas-partidas`, o sea la demo.

El techo está **en el código**, no solo en este documento: pedir `VUS` por
encima de `TECHO_VUS` aborta la corrida con el motivo. Se puede subir, pero a
propósito y avisando al equipo.

Los perfiles usan el **modelo cerrado** (`constant-vus`) y no el abierto
(`constant-arrival-rate`). No es solo purismo: en el modelo abierto, si el
servicio se ralentiza, k6 levanta más VUs para mantener la tasa, que es
exactamente como se tumba un host pequeño. El cerrado se auto-frena: cuando el
servidor tarda más, la carga ofrecida baja sola. En un entorno compartido con la
demo, esa propiedad vale más que la pureza del modelo. A cambio, el throughput
que sale **no es un máximo alcanzable**: es el ritmo que el servidor permitió.

---

## El informe

Cada corrida produce tres cosas con el mismo contenido:

- el informe por consola,
- `resumen.txt` — el mismo texto, para adjuntar al acta,
- `resumen.json` — legible por máquina, para comparar corridas entre sí.

Los dos archivos llevan, por escenario y en total: **p50, p90, p95, p99, tasa de
error y throughput**, más el veredicto CUMPLE / NO CUMPLE contra el objetivo. El
JSON incluye además la ruta y el requisito de cada escenario, y la lista de
limitaciones: un artefacto de evidencia que no dice dónde no llega invita a
leerlo de más.

`tests/rendimiento/resultados/` es carpeta de borradores y está en el
`.gitignore` de esta carpeta: el informe de una corrida local no es evidencia de
nada. El que sí vale —un `baseline` contra un entorno de verdad— viaja como
artefacto del workflow y se adjunta al acta.

> **k6 no crea directorios** para los archivos de `handleSummary`. Si se pasa
> `RESUMEN_JSON=informes/x.json` y `informes/` no existe, el archivo no se
> escribe y k6 no avisa. Por eso las recetas de arriba hacen `mkdir -p` y `cd`
> antes de correr.

El **código de salida** lo deciden los `thresholds` de k6: `0` si todo cumple,
`99` si algún umbral se rompió. Eso es lo que pone roja la compuerta en CI. El
veredicto del informe se calcula por separado, a partir de los mismos números
que ve el lector: son dos caminos independientes hacia la misma conclusión, y si
algún día divergen es que uno de los dos está mal y se nota.

---

## Qué NO mide esta suite

Explícito, porque un informe de rendimiento leído de más es peor que no tenerlo:

- **El canal en tiempo real (STOMP sobre WebSocket).** Ver la sección «La acción
  de partida» de arriba.
- **Registro en memoria y por proceso.** Los percentiles salen de las muestras de
  *esa* corrida, en *ese* único proceso de k6. No se agregan con las de otra
  réplica, ni con el registro en memoria de `metricas-plataforma` (que mantiene
  su propia ventana de 10 000 muestras por proceso, ver ADR-006). Dos fuentes
  distintas del mismo número pueden no coincidir, y ninguna de las dos está mal.
- **Sin agregación entre réplicas.** Hoy hay una instancia por servicio. El día
  que haya dos, esta suite seguirá midiendo lo que el borde reparta, sin saber
  a cuál fue cada petición.
- **La red del runner.** Se mide desde donde corre k6. Una corrida desde GitHub
  Actions contra AWS incluye Internet en el número; una corrida desde el banco
  local, no. No son comparables entre sí.
- **Perfil de tráfico realista.** Los escenarios corren en serie y aislados: cada
  número describe su operación sin contención de las otras. Un día real tiene
  las cuatro cosas a la vez, y eso no está medido.
- **El punto de rotura.** Buscarlo necesita un entorno desechable y un perfil que
  esta suite se prohíbe. No se hace contra DEV.
- **Latencia de peticiones que nunca llegaron.** Un fallo de transporte (conexión
  rechazada, DNS, tiempo agotado sin respuesta) cuenta como error y como
  petición, pero no entra en la distribución de latencia: k6 le asigna una
  duración que no es latencia de servicio, y meterla abarataría el p95 justo
  cuando el entorno está caído. Las respuestas 4xx y 5xx **sí** entran: tardaron
  lo que tardaron.
- **Los créditos.** `crear_sala` usa `recompensaCreditos: 0` a propósito: con
  recompensa, cada sala reserva créditos en `ms-finanzas` (RF-JUE-014) y a los
  pocos minutos lo único que se mediría es el rechazo por saldo insuficiente. Esa
  ruta tiene su cobertura en `tests/e2e/apuesta-de-creditos.e2e.spec.js`.

---

## Decisiones de diseño

**Un solo guion con `scenarios` de k6, no un archivo por escenario.** El informe
tiene que dar percentiles, tasa de error y throughput *por escenario y en
total*, y `handleSummary` corre una vez por proceso de k6. Con un archivo por
escenario habría cuatro resúmenes sueltos y el total habría que calcularlo a
mano fuera de k6 —o sea, un sitio más donde equivocarse—. Con un solo proceso,
una corrida produce un `resumen.json` con las cinco filas ya hechas. De paso, la
sesión se consigue una sola vez en `setup()` en lugar de cuatro veces. Los
escenarios siguen viviendo en archivos separados bajo `escenarios/`; lo que se
comparte es el proceso, no el archivo.

**Métricas propias en vez de `http_req_duration`.** La suite hace tres cosas que
no son la medición: el login de comprobación de `setup()` y la cancelación de
cada sala creada. Si los umbrales colgaran de `http_req_*`, el informe mezclaría
preparación y limpieza con la medición. Cada petición medida se anota
explícitamente en la serie de su escenario y en la total; lo que no se anota, no
cuenta. En la salida nativa de k6 se siguen viendo los `http_req_*` completos,
que son útiles para depurar pero **no** son el informe.

**La sala creada se cancela en el acto.** Sin eso, un `load` de dos minutos deja
miles de salas abiertas en el listado de un entorno compartido con la demo, y
además falsea el escenario de listado, que empezaría a paginar sobre basura de la
propia medición. La cancelación va etiquetada como `limpieza` y no entra en
ninguna serie.

**Nada de dependencias externas.** Solo módulos propios de k6 (`k6`, `k6/http`,
`k6/metrics`). El informe de texto está escrito a mano en lugar de usar
`jslib.k6.io/k6-summary`: una descarga menos en tiempo de ejecución y un
artefacto que se puede leer sin red.

---

## Lint y formato

**ESLint no cubre esta carpeta, y es correcto.** `frontend/app-web/eslint.config.js`
declara `files: ['src/**/*.js']` con los globales del navegador: está escrito
para el código de la aplicación web. Apuntarlo aquí daría errores falsos de
`no-undef` sobre `__ENV` y `__ITER`, que son globales de k6 y no del navegador.
`tests/e2e/*.js` está en la misma situación desde que existe. Si en algún momento
se quiere lintar los arneses, hace falta un bloque de configuración propio con
los globales de k6 declarados; es una decisión de equipo, no algo que arreglar
por la puerta de atrás.

**Prettier sí se aplicó.** La configuración vive en la raíz (`.prettierrc.json`)
y estos archivos la cumplen, aunque el `npm run format:check` de CI tenga el
alcance acotado a `frontend/app-web/src/`:

```bash
npx prettier --config .prettierrc.json --check "tests/rendimiento/k6/**/*.js"
```
