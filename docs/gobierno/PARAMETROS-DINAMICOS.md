# Parámetros dinámicos — qué se lee de `admin-parametros` y qué no

**HU-ADM-001 (RF-ADM-001) · catálogo en `services/plataforma/admin-parametros`, migración
`V1__catalogo_de_parametros.sql`.**

El catálogo tiene **22 claves**. Este documento dice, una por una, quién las consume hoy y por
qué. No es un inventario decorativo: existe porque «está en el catálogo» y «el sistema lo aplica»
son dos cosas distintas, y confundirlas es exactamente cómo se llega a un parámetro configurable
que ningún código lee.

| Grupo | Claves |
|---|---|
| A · PRODUCTO — consumida por API | **5** |
| B · PRODUCTO — sin consumidor todavía | **3** |
| C · TÉCNICA — variable de entorno, y está bien así | **2** |
| D · INALTERABLE DEL CHARTER — quemada a propósito | **8** |
| E · DE OTRO EQUIPO | **4** |
| **Total** | **22** |

---

## A · PRODUCTO — consumida por API (5)

El servicio pregunta al catálogo por HTTP en cada uso, con caché corta y el valor de su variable
de entorno como **respaldo**. Cambiar el valor desde el panel de Parámetros cambia el
comportamiento sin reiniciar nada.

| Clave | Quién la consume | Respaldo | Origen |
|---|---|---|---|
| `sanciones.suspension.minima-horas` | `moderacion-sanciones` · `sanciones/LimitesDesdeParametros.java` | `SANCIONES_SUSPENSION_MINIMA_HORAS` (1) | D-20 / HU-USR-005 |
| `sanciones.suspension.maxima-dias` | `moderacion-sanciones` · `sanciones/LimitesDesdeParametros.java` | `SANCIONES_SUSPENSION_MAXIMA_DIAS` (30) | D-20 / HU-USR-005 |
| `sanciones.apelacion.plazo-dias` | `moderacion-sanciones` · `sanciones/LimitesDesdeParametros.java` | `SANCIONES_APELACION_PLAZO_DIAS` (30) | HU-USR-007 |
| `salas.apuestas.si-gana-la-maquina` | `salas-partidas` · `aplicacion/LiquidarApuesta.java` | `APUESTAS_SI_GANA_LA_MAQUINA` (`LIBERAR`) | D-02 / HU-JUE-014 |
| `chat.historial.tamano` | `salas-partidas` · `chat/canal/ChatController.java` | `CHAT_HISTORIAL_TAMANO` (50) | D-16 / HU-JUE-015 |

Las tres de sanciones se consumen desde el Sprint 3 (#580). Las dos de `salas-partidas` se
añadieron en R12: hasta entonces estaban en el catálogo **y** en una variable de entorno, con el
resultado de que cambiarlas desde el panel no hacía nada.

La política de degradación es una sola y vive en un solo sitio:
`shared/libs/plataforma-resiliencia` · `parametros/LectorDeParametros.java`. El orden, siempre:
**valor cacheado → catálogo → respaldo, dejándolo escrito en la bitácora**. Leer un parámetro no
lanza nunca, y con `PARAMETROS_URL` vacía no hace ni una petición.

### El defecto que esto corrigió (y por qué merece una nota)

`sanciones.apelacion.plazo-dias` era configurable desde el Sprint 3, y `SancionesService.apelar`
lo respetaba. Pero el número **30** seguía escrito a mano en cinco sitios: el cuerpo del aviso al
jugador, la comparación de `sePuedeApelar()` en el frontend, el texto de «no has apelado ninguna
sanción», el párrafo de cabecera de *Mis sanciones* y —en su versión en horas, `max="720"`— el
formulario del panel de moderación. Bajar el plazo a 7 días dejaba al sistema prometiendo treinta
y rechazando al octavo, sin que nada en el código lo delatara.

La lección general: **un parámetro no está terminado cuando el servicio lo lee; está terminado
cuando lo lee todo lo que habla de él.** La interfaz obtiene ahora el valor vigente de
`GET /api/v1/sanciones/limites` (`moderacion-sanciones-consulta.yaml` 1.3.0) y, cuando no lo puede
leer, habla del plazo **sin número** en vez de inventar uno.

---

## B · PRODUCTO — sin consumidor todavía (3)

Están en el catálogo porque hay una decisión del PO detrás, pero hoy **ningún código las aplica**.
Se declaran aquí para que nadie las dé por vigentes.

| Clave | Qué la respalda | Qué falta |
|---|---|---|
| `chat.mensajes-por-minuto` | D-16 / HU-JUE-015 | **El límite de ritmo no está implementado.** No hay ningún código que lo aplique: ni en `EnviarMensaje`, ni en `ChatController`, ni en el borde. El catálogo lo deja sin valor (`NULL` = sin límite), que es honesto respecto a lo que hace el sistema, pero el canal sigue expuesto a inundación, como advierte la propia HU. Ponerle un valor no cambiaría nada hasta que exista el contador por jugador y ventana. |
| `torneos.costo-inscripcion-por-defecto` | D-23 / HU-TOR-002 | Hoy el costo lo teclea quien crea el torneo (`TorneosService.crear`). Este parámetro sería el valor que **propone** el formulario; falta que la vista de creación de torneo lo lea y lo use como valor inicial del campo. Nada más: el servicio ya acepta cualquier costo válido. |
| `metricas.umbral-sanciones-por-dia` | D-25 / HU-MET-001 | `metricas-plataforma` **sí** lo aplica, pero leyéndolo de `METRICAS_UMBRAL_SANCIONES_POR_DIA` al arrancar (`ModeracionController`), no del catálogo. Sin valor no evalúa ninguna alerta y lo dice (`alertasConfiguradas: false`). Falta que el PO fije la cifra; el día que lo haga, conviene moverlo al grupo A, porque es una decisión de producto que va a querer ajustar. |

---

## C · TÉCNICA — variable de entorno, y está bien así (2)

| Clave | Dónde se lee hoy | Por qué NO por API |
|---|---|---|
| `plataforma.latencia-objetivo-ms` | `metricas-plataforma` · `application.yml:49` → `${LATENCIA_OBJETIVO_MS:500}` | Es un objetivo técnico que `metricas-plataforma` usa **en cada medición** para decidir si una latencia está fuera de presupuesto. Pedirlo por HTTP metería una llamada de red dentro del instrumento que mide la red. Se lee al arrancar y punto. |
| `plataforma.disponibilidad-objetivo-porcentaje` | `metricas-plataforma` · `application.yml:79` → `${DISPONIBILIDAD_UMBRAL:99.95}` | Lo mismo: la sonda de disponibilidad corre cada 30 s y compara contra el umbral. Además, cambiar el objetivo a mitad de un mes de medición invalidaría el informe del mes. |

Las dos están además marcadas `inalterable = TRUE` en el catálogo, porque el Charter fija sus
valores (500 ms y 99,95 %). Aparecen en el catálogo **para consulta**, no para edición: que un
administrador pueda ver el objetivo vigente sin abrir un `application.yml` es útil; que lo pueda
cambiar desde una pantalla, no.

---

## D · INALTERABLE DEL CHARTER — quemada a propósito (8)

El `ProjectCharterEquipo6NexusBattlesVI_1_0.pdf` (v2.0, aprobado por el cliente) dice literalmente
que «valores numéricos de estadísticas, costos, efectos, comisiones, límites y plazos son
inalterables — no son parámetros de ajuste libre para el desarrollador». Convertirlas en
ajustables contradiría el Charter, y el Charter no es negociable a nivel de equipo.

Están en el catálogo con `inalterable = TRUE` para que el panel las **muestre** y `ParametrosService`
rechace cualquier intento de cambiarlas. El valor que de verdad aplica el sistema es la constante
del código:

| Clave | Valor | La constante vive en |
|---|---|---|
| `torneos.cupos` | 8 | `services/plataforma/torneos/src/main/java/com/nexusbattles/plataforma/torneos/torneo/Torneo.java:24` — `public static final int CUPOS = 8` |
| `torneos.dias-entre-torneos` | 91 | `services/plataforma/torneos/src/main/java/com/nexusbattles/plataforma/torneos/torneo/Torneo.java:27` — `public static final int DIAS_ENTRE_TORNEOS = 91` |
| `torneos.integrantes-por-equipo` | 2 | `services/plataforma/torneos/src/main/java/com/nexusbattles/plataforma/torneos/torneo/Equipo.java:73` — `Equipo.deJugadores(..., capitan, companero, ...)`: la pareja está en la **firma**, no en un número |
| `partidas.creditos.victoria-uno-a-uno` | 2 | `services/cuentas/ms-finanzas/src/main/java/com/nexusbattles/ms_finanzas/partidas/AcreditacionPartidaService.java:52` |
| `partidas.creditos.victoria-grupal` | 4 | `services/cuentas/ms-finanzas/src/main/java/com/nexusbattles/ms_finanzas/partidas/AcreditacionPartidaService.java:53` |
| `partidas.creditos.participacion` | 1 | `services/cuentas/ms-finanzas/src/main/java/com/nexusbattles/ms_finanzas/partidas/AcreditacionPartidaService.java:54` |
| `salas.participantes-maximo` | 6 | `services/plataforma/salas-partidas/src/main/java/com/nexusbattles/plataforma/salaspartidas/dominio/Modalidad.java:27` — `HASTA_SEIS(2, 6, true, 0, 5)` |
| `plataforma.cpu-autoescalado-porcentaje` | 75 % | `services/plataforma/metricas-plataforma/src/main/java/com/nexusbattles/plataforma/metricasplataforma/tecnicas/TableroTecnico.java:18` — `public static final double UMBRAL_CPU = 0.75d` |

Nota sobre `plataforma.cpu-autoescalado-porcentaje`: hoy el umbral se usa **solo para alertar** en
el tablero técnico. No hay autoescalado implementado —el entorno de desarrollo es una sola
instancia EC2—, así que el 75 % del Charter es, por ahora, el umbral de una alerta y no el disparo
de nada. Está dicho aquí para que nadie lea el catálogo y concluya lo contrario.

Las tres de `partidas.creditos.*` son además de **otro equipo** (`ms-finanzas`, Cuentas): aquí solo
se documentan.

---

## E · DE OTRO EQUIPO (4)

`ms-subastas` pertenece al equipo de Cuentas. Sus claves están en el catálogo porque el propio
servicio las declara «configurables desde admin-parametros», pero **cablear su consumo no es
nuestro** (CLAUDE.md: no modificar servicios de otro grupo sin coordinarlo).

| Clave | Valor en catálogo | Origen |
|---|---|---|
| `subastas.incremento-minimo` | `NULL` (pendiente del PO) | RF-SUB-002 |
| `subastas.max-subastas-activas-por-jugador` | 10 | RF-SUB-004 |
| `subastas.max-pujas-activas-por-jugador` | 50 | RF-SUB-004 |
| `subastas.intervalo-minimo-segundos` | 5 | RF-SUB-004 |

---

## Por qué NO se convierte todo en parámetro dinámico

**Cada parámetro por API es una dependencia de red en el camino caliente.** Un valor que hoy es
una constante en memoria pasa a ser, en el peor caso, una petición HTTP a otro microservicio
dentro de la petición del jugador: un puerto más que abrir, un tiempo de espera más que acotar, un
modo de fallo más que probar y una caché más que invalidar. El `LectorDeParametros` amortigua todo
eso —caché con vigencia, respaldo por variable de entorno, captura del fallo y constancia en la
bitácora—, pero amortiguar no es eliminar: sigue habiendo un servicio más del que depende el
arranque del combate, y sigue habiendo una ventana en la que el valor cacheado y el valor real no
coinciden. Ese precio se paga con gusto por `sanciones.apelacion.plazo-dias`, que el Product Owner
va a querer ajustar cuando vea cómo se comporta la moderación. No tiene ningún sentido pagarlo por
`torneos.cupos`, que vale 8 porque el Charter dice 8 y que, si algún día cambiara, cambiaría junto
con el árbol de dieciséis encuentros que lo asume.

**Y un catálogo que nadie cambia es complejidad sin beneficio.** Convertir las 22 claves en
parámetros por API costaría veintidós cableados, veintidós respaldos, veintidós pruebas de
degradación y veintidós sitios donde un valor mal tecleado en una pantalla de administración puede
romper el juego en caliente, a cambio de la capacidad de cambiar números que nadie va a cambiar
—ocho de ellos porque el cliente prohíbe cambiarlos—. El criterio que aplica este documento es el
único que se sostiene: **es parámetro dinámico lo que tiene un dueño que quiera ajustarlo sin
esperar a un despliegue.** Lo demás es una constante, y una constante bien nombrada en el código,
con su requisito citado al lado, se entiende mejor y falla menos que una fila en una tabla. El
catálogo sigue mostrándolas todas —ver el valor vigente sin abrir un `application.yml` es útil por
sí solo—, pero mostrarlas no es lo mismo que hacerlas ajustables.

---

## Cómo añadir un consumidor nuevo (resumen operativo)

1. Comprobar que la clave está en `V1__catalogo_de_parametros.sql` con `inalterable = FALSE`.
2. Declarar `implementation project(':shared:libs:plataforma-resiliencia')` en el `build.gradle`
   del servicio, si no lo tiene ya.
3. Un `@Bean LectorDeParametros` construido con `LectorDeParametros.desde(...)` y
   `${<servicio>.parametros.url:}` ← `PARAMETROS_URL`. **Siempre** `desde(...)`: con la URL vacía
   devuelve un lector sin catálogo, y así el camino «sin catálogo» pasa por el mismo código.
4. En el punto de uso, `entero(clave, respaldo)` / `texto(clave, respaldo)` /
   `opcion(clave, Tipo.class, respaldo)`. El respaldo es el valor de la variable de entorno que
   había antes: **la variable no se borra, cambia de papel**.
5. Leer el valor **en cada uso**, no al construir el objeto. Un parámetro que se lee una vez al
   arrancar es una constante con pasos de más.
6. Si la interfaz habla de ese número, publicarlo también para ella (un campo en un DTO que ya
   viaja, o un recurso de solo lectura) y degradar a un texto **sin número** cuando no se pueda
   leer. Nunca a un valor inventado.
