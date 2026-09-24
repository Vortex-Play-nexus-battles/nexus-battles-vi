# Decisiones funcionales pendientes del Product Owner

Registro único de lo que el código **no puede decidir por su cuenta** y quedó
resuelto de forma provisional, configurable donde tuvo sentido, para no
bloquear el MVP. Cada entrada dice qué se hizo mientras tanto, dónde se cambia
en cuanto el PO decida, y qué NO se hizo a propósito.

Regla de fondo (Project Charter, riesgo #6): nada de esto se inventó. Donde
había hueco, se eligió la opción que no le quita nada a nadie y se dejó el
interruptor a la vista.

| # | Decisión pendiente | Qué hace hoy el sistema | Dónde se cambia | Origen |
|---|---|---|---|---|
| D-01 | **Desempate.** Qué pasa cuando la partida termina sin nadie en pie. | `partida.finalizada` sale con `ganadores: []` (empate). Con apuesta, se **devuelve** lo apostado a todos (`reparto` con 0). | `Partida.ganador()`; `LiquidarApuesta.moverCreditos` | HU-JUE-005, HU-JUE-014 CA-04 |
| D-02 | **Gana la máquina con apuesta.** La IA no tiene bolsa (RF-JUE-014: no apuesta). ¿Qué pasa con lo apostado por los humanos si gana? | Configurable: `salas.apuestas.si-gana-la-maquina` = `LIBERAR` (por defecto: se devuelve) o `CONSUMIR` (la casa se queda con la apuesta). | `application.yml` de salas-partidas / variable `APUESTAS_SI_GANA_LA_MAQUINA` | HU-JUE-014 |
| D-03 | **Héroe de la IA.** Con qué héroe combate la máquina. | Usa el del anfitrión a plena vida (único que la partida conoce). | `ParticipanteDePartida.inteligenciaArtificial` | HU-SAL-004 |
| D-04 | **Estrategia de la IA.** A quién ataca cuando hay varios rivales. | Al primer rival en pie en orden de turno. | `EjecutarAccion.elegirObjetivo` | HU-SAL-004 |
| D-05 | **Roster y equipos.** Cómo se forman los equipos en modalidad de hasta seis (`tamanoEquipo`): por orden, por sorteo, a elección. | Por **orden de entrada**, llenando cada equipo antes de abrir el siguiente: el anfitrión abre el equipo 1, los héroes de la IA van al final. Sin barajar ni equilibrar. Ganan todos los del único equipo en pie; no se puede atacar a un compañero. | `Partida.repartirEnEquipos`; `EjecutarAccion.elegirObjetivo` | HU-SAL-004 / RF-JUE-004 |
| D-06 | **Notificaciones de partida.** Si el inicio/fin de partida (o la entrada de alguien a mi sala) debe generar una notificación push además del aviso STOMP. No está en el SRS (RF-NOT-001/003 son catálogo y subastas). | Solo el canal STOMP de la sala/partida. | `CanalDePartidaStomp` + integración con notificaciones | #441, HU-NOT-006 |
| D-07 | **Segunda calificación del mismo jugador (HU-COM-001).** Si un jugador que ya calificó un producto vuelve a comentar con estrellas: ¿409 o se acepta el comentario «sin estrellas»? | Se acepta el comentario y se le quitan las estrellas (`CALIFICACION_DUPLICADA`), sin tratarlo como error. | `comentarios`: `HiloDeComentarios` | #34, #441 |
| D-08 | **Entornos test/prod.** Si se despliegan y con qué host, dado el coste (un solo `t3.small` en el plan gratuito). | Solo `dev`; `test` y `prod` condicionados a secretos que no existen. | `cd.yml`, `scripts/cd/desplegar.sh` | HU-CICD-002 |
| D-09 | **Segundo factor para la bitácora.** HU-AUD-001 menciona 2FA para consultar la auditoría, pero ms-identidad no implementa ninguno. | Exigencia **apagada** por configuración (`cumplimiento.auditoria.exigir-2fa=false`); al encenderla se lee `amr` (RFC 8176). | `application.properties` de ms-cumplimiento / `AUDITORIA_EXIGIR_2FA` | #453 |
| D-10 | **Comisión sobre la apuesta.** El Charter fija que comisiones y límites son inalterables, pero ningún documento del bloque da un valor para la apuesta de batalla. | Sin comisión: el ganador recibe el total apostado por los demás, entero. | `LiquidarApuesta.moverCreditos` (una llamada `consumir` por perdedor) | HU-JUE-014 CA-04 |
| D-11 | **Reservas huérfanas.** Si el servicio muere entre reservar y guardar la sala, la reserva queda viva hasta que ms-finanzas la expire (72 h). | Se acepta: la clave de idempotencia (sala, jugador, versión) hace que un reintento del mismo ingreso reutilice la reserva; el resto lo expira el libro. | `ClienteCreditos.claveDeIdempotencia`; expiración en ms-finanzas | HU-JUE-014 |
| D-12 | **Apuesta con victoria por equipo.** Cuando gana un equipo con más de uno en pie, cómo se reparte lo apostado por los perdedores entre los ganadores (a partes iguales con redondeo, al que más daño hizo, al anfitrión…). | En toda partida por equipos se **devuelve** lo apostado a todos (`reparto` con 0), igual que en empate: no se mueve dinero sin una regla, y declarar ganador al único en pie le daría lo apostado por su propio compañero caído. | `Partida.ganador()` (vacío en modo cooperativo); `LiquidarApuesta.moverCreditos` | HU-SAL-004, HU-JUE-014 |
| D-13 | **Héroe de cada máquina en hasta seis.** RF-JUE-001 habla de «héroe aleatorio» de la IA; con varios cupos de la IA, ¿héroes distintos del catálogo, o el mismo? | Cada máquina combate con una copia del héroe del anfitrión a plena vida (D-03 aplicado a todas). | `Partida.iniciar` | HU-SAL-004 |
| D-14 | **Qué sanción silencia (RF-USR-004).** `moderacion-sanciones-consulta.yaml` solo dice `sancionActiva: true/false`, sin tipo (silencio, suspensión, expulsión). | **Actualizada (HU-USR-004, contrato 1.1.0 con `tipo`):** la consulta responde `sancionActiva: true` solo con **SUSPENSION** no vencida o **BANEO**; una **ADVERTENCIA no silencia** (CA-02 de HU-USR-004). Los consumidores (chat, comentarios, subastas) no cambian: siguen leyendo `sancionActiva`. Si sanciones no responde, no se publica (503 `sanciones-no-disponibles`), igual que con la lista negra. | `salas-partidas`: `chat.integracion.ClienteSanciones`; `comentarios`: `publicacion.ClienteSanciones`. Cuando el contrato traiga tipo, cambian solo esas dos clases. | #33, #34, #441 |
| D-15 | **El motor de combate cae a mitad de partida (HU-DIS-003).** Qué pasa con el turno: ¿se congela la partida hasta que vuelva, se pasa el turno, se anula la partida y se devuelve la apuesta? | La acción del humano **no se aplica** y el turno **sigue siendo suyo**: ve «Motor de combate no disponible temporalmente» sobre sus controles y reintenta cuando quiera. Si el que falla es el turno de la máquina, la máquina **pasa** y el combate sigue (ya era así con `MotorNoDisponible`). Nada se anula ni se devuelve. Umbrales técnicos del corta circuitos: 3 fallos seguidos, 30 s (`RESILIENCIA_*`). | `EjecutarAccion.jugarTurnoDeLaMaquina`; `PartidasStompController.seccionNoDisponible`; `combate.js` (`rechazar`) | #72, HU-SAL-005 |
| D-16 | **Retención del historial del chat y límite de mensajes por minuto (HU-JUE-015).** La HU los deja «por definir con el PO»; el SRS no da cifra. | Historial **sin caducidad**; al entrar se cargan los últimos `CHAT_HISTORIAL_TAMANO` (50) mensajes; **sin límite de ritmo** (el canal queda expuesto a inundación, como advierte la propia HU). | `salas-partidas`: `chat.historial.tamano` en `application.yml`; un límite de ritmo iría en `EnviarMensaje` | #33 |
| D-17 | **Retención del registro de disponibilidad (HU-DIS-001, SCRUM-1145).** Cuánto tiempo se conservan las interrupciones y las ventanas de mantenimiento guardadas, y quién puede borrarlas. | Se conservan **para siempre** (una fila por caída, no por sondeo: crecimiento mínimo); nadie las borra. El informe mensual del Charter necesita al menos el mes en curso y el anterior. | `metricas-plataforma`: `AlmacenEnPostgres` (una sentencia de borrado por antigüedad) + tarea programada | #71 |
| D-18 | **Créditos por partida para los cupos de la máquina (HU-JUE-012, RF-JUE-012).** La ficha oficial dice 2/4 al ganador y 1 por participar, y anota que «no aclara si los participantes controlados por la inteligencia artificial reciben créditos». Tampoco dice si, cuando gana la máquina, el humano cuenta como «los demás jugadores» (1 por participar) o no recibe nada. | La máquina **no se informa al libro** (no tiene cuenta a la que acreditar) y, si gana, el humano recibe **1 por participar**, que es lo que la ficha da a todo el que no ganó. Con equipos, todo el equipo ganador recibe lo del ganador grupal (4) — es la lectura literal de «el jugador ganador» aplicada a cada integrante. | `salas-partidas`: `AcreditarRecompensa.informeDe` (filtra `esIA`, manda `ganadores`); `ms-finanzas`: `AcreditacionPartidaService` (cifras) | #470 |
| D-19 | **Retirar un comentario con calificación (HU-COM-004 / RF-COM-002).** RF-COM-004 dice que el jugador puede retirar sus comentarios y RF-COM-002 que califica «una sola vez»; ninguna ficha dice si, al retirar el comentario que llevaba su calificación, esa calificación sigue contando o si el jugador puede volver a calificar. | Al retirar el comentario se **retira también su calificación**: deja de contar en el promedio (CA-01 de HU-COM-004) y **libera** la única calificación del jugador sobre ese producto, que puede volver a calificar en un comentario nuevo. Es la lectura que no deja un promedio con una estrella de un comentario que ya nadie ve. | `comentarios`: `Comentario.eliminado()` (pone `estrellas` a nulo) y `HiloDeComentarios.eliminar` | #485 |
| D-20 | **Duración máxima de una suspensión temporal (HU-USR-005).** La ficha dice «horas, días o semanas» y no fija tope; tampoco cuántas advertencias escalan a suspensión (CA-05 de HU-USR-004). | Rango configurable: mínimo **1 hora**, máximo **30 días** por defecto (`SANCIONES_SUSPENSION_MINIMA_HORAS`, `SANCIONES_SUSPENSION_MAXIMA_DIAS`); fuera de rango → 400 con motivo. **Sin escalado automático** de advertencias. | `moderacion-sanciones`: `SancionesService` (`sanciones.suspension.*`) | #478, #477 |
| D-21 | **Rol «administrador de torneo» (RF-TOR-001, INC-13).** La ficha lo nombra pero no está en la Tabla 24 de roles. | Crear, iniciar y cancelar un torneo lo hacen ADMINISTRADOR y SUPER_ADMINISTRADOR; registrar un resultado a mano también, con motivo obligatorio (RF-ADM-005). | `torneos`: `Actor.ADMINISTRACION`, `SecurityConfig` | #486, #497 |
| D-22 | **Formato del encuentro (RF-TOR-003/004, INC-12).** Equipos de 2 y batallas de hasta 6 no encajan con un árbol de pares. | Cada encuentro es **equipo contra equipo** (2 contra 2, cuatro jugadores): es el único formato compatible con el árbol de ganadores/secundarios que la ficha numera. El resultado lo aporta la partida jugada (ROLE_SERVICIO) o un administrador con motivo; nunca un jugador. | `torneos`: `Arbol`, `TorneosService.registrarResultado` | #488, #489 |
| D-23 | **Valor de la inscripción y quién paga (RF-TOR-002).** La ficha no fija el valor; el pago en dinero real es de la pasarela (RF-PAG-001, otro equipo). | El valor lo fija quien crea el torneo, en créditos (`costoInscripcion`, 0 = gratuito); paga el integrante que inscribe al equipo, con reserva en el libro que se cobra al iniciar y se devuelve al cancelar. Sin dinero real. Cuando HU-ADM-001 exista, el valor por defecto sale de ahí. | `torneos`: `TorneosService.inscribir`, `ClienteCreditos` | #487, #481 |
| D-24 | **Premiación (RF-TOR-007) y transmisión (RF-TOR-006, INC-18).** Monto de créditos y «épica única» sin definir; el vídeo en vivo choca con presupuesto cero. | **No se acredita ningún premio** ni se transmite nada: el torneo termina con `campeonEquipoId` y los encuentros con su `partidaId`. Se implementa cuando el PO fije montos y el modo de transmisión. | `torneos`: pendiente | #492, #491 |
| D-25 | **Umbral de «alta frecuencia» de sanciones y de reportes (RF-MET-001).** La ficha pide alertas pero ningún documento fija la cifra. | El tablero de moderación publica los conteos por día y **no evalúa ninguna alerta** hasta que se fije `METRICAS_UMBRAL_SANCIONES_POR_DIA` (`alertasConfiguradas: false`). Registro de nuevos usuarios y frecuencia de reportes aparecen en `pendientes` (ms-identidad sin contrato de lectura; HU-COM-006 sin implementar). | `metricas-plataforma`: `metricas.moderacion.umbral-sanciones-por-dia` | #527 |
| D-26 | **Encuentro de torneo ganado por la máquina (RF-TOR-004 + RF-TOR-005).** Salas-partidas informa el resultado con el `uid` del jugador en pie (`torneos.yaml` 1.1.0, HU-TOR-004 CA-04); el equipo de la máquina no tiene cuenta ni `uid`, y la ficha no dice si ese resultado se registra solo o lo confirma alguien. | Cuando gana la máquina (o hay empate) la sala **anota el fallo en el vínculo** (`ultimo_fallo`) y **no informa nada**; el administrador registra el resultado con motivo (RF-ADM-005). Cuando el PO lo decida, salas mandará al equipo máquina (haría falta que torneos exponga el id del equipo por encuentro a la sala, o un `ganoLaMaquina` en el contrato). | `salas-partidas`: `InformarEncuentroDeTorneo` | #489 |

## Estado al 23 de septiembre de 2026 — qué sigue siendo del PO y qué no

Esta tabla nació mezclando dos cosas distintas, y conviene separarlas antes de
la Sprint Review: **decisiones de producto** (el PO las tiene que tomar) y
**convenciones técnicas** (las toma el equipo y el PO puede revisarlas). Poner
las segundas en una lista titulada «pendientes del PO» las dejaba esperando
una firma que nadie iba a pedir.

### CERRADAS como decisión técnica del equipo

No vuelven a presentarse al PO. Si quiere cambiar alguna, es configuración.

| # | Valor adoptado | Por qué | Evidencia |
|---|---|---|---|
| **D-08** | Solo `dev`. `test` y `prod` quedan condicionados a secretos inexistentes. | Restricción de coste, no de producto: un entorno más es otra factura. Los jobs existen y no se pueden disparar. | `cd.yml` (`vars.HAY_ENTORNO_TEST`, `HAY_ENTORNO_PRODUCCION`); simulacro de reversión ejecutado en dev por eso mismo |
| **D-09** | 2FA para la bitácora **apagado** por configuración. | ms-identidad no implementa ningún segundo factor: exigirlo encendido dejaría la auditoría inaccesible. Al encenderlo se lee `amr` (RFC 8176). | `AUDITORIA_EXIGIR_2FA`; `RequireSuperAdmin2FAAspect` |
| **D-11** | Se aceptan las reservas huérfanas; las expira el libro a las 72 h. | La clave de idempotencia (sala, jugador, versión) hace que un reintento reutilice la reserva. Inventar un proceso de barrido sería más código para un caso que el proveedor ya resuelve. | `ClienteCreditos.claveDeIdempotencia` |
| **D-15** | El turno **no se pierde**: la acción no se aplica y sigue siendo del mismo jugador. | Es la única opción que no castiga al jugador por una caída de infraestructura. Anular la partida le quitaría lo apostado a todos. | HU-DIS-003; `degradacion.e2e.spec.js` apaga el motor de verdad |
| **D-16** | Historial de chat sin caducidad, últimos 50 al entrar. **Sin límite de ritmo.** | El tamaño es configurable y ya se lee del catálogo. El límite de ritmo no se inventa: no hay cifra en ningún documento y **el código no lo implementa** — está declarado como hueco, no como hecho. | `chat.historial.tamano` en el catálogo; `PARAMETROS-DINAMICOS.md` |
| **D-17** | Las interrupciones se conservan **para siempre**; nadie las borra. | Una fila por caída, no por sondeo: el crecimiento es mínimo y el informe mensual del Charter necesita al menos el mes en curso y el anterior. Borrar evidencia de disponibilidad para ahorrar kilobytes es mal negocio. | `AlmacenEnPostgres`; esquema `metricas` |
| **D-25** | Los conteos se publican; **no se evalúa ninguna alerta** hasta que se escriba un umbral. | No poner umbral y no alertar es honesto; poner uno inventado convierte una cifra arbitraria en un aviso que alguien atenderá. La copia visible lo dice con palabras, sin citar el código de la decisión. | `METRICAS_UMBRAL_SANCIONES_POR_DIA`; `metricas-tecnicas.e2e.spec.js` |
| **LATENCIA_PERCENTIL** | **p95**, convención de medición del equipo. | Ningún documento del proyecto fija el percentil — se buscó en Charter, `CLAUDE.md`, `.claude/rules/`, contratos y catálogo. Los 500 ms sí están en cinco sitios; el percentil en cero. Esperar una aprobación escrita dejaba RNF-REN-001 sin poder evaluarse **en ninguno** de los veinte módulos. | [ADR-006](./ADR-006-percentil-de-evaluacion-de-latencia.md); suite k6 con números reales |

### SIGUEN SIENDO DEL PRODUCT OWNER

Estas dos no las puede decidir el equipo sin inventar producto:

| # | Qué falta decidir | Qué pasa mientras tanto |
|---|---|---|
| **D-24** | Monto de la premiación del torneo (RF-TOR-007) y si hay transmisión (RF-TOR-006). | No se acredita ningún premio ni se transmite nada. **HU-TOR-006 (#491) y HU-TOR-007 (#492) quedan bloqueadas**, no parciales. Escalado en #636. |
| **D-26** | Qué se registra cuando un encuentro de torneo lo gana el equipo de la máquina, que no tiene cuenta ni `uid`. | El resultado se informa con el `uid` del jugador en pie. Escalado en #636. |

### Hueco detectado y registrado hoy

| # | Decisión | Qué hace hoy | Dónde se cambia |
|---|---|---|---|
| **D-27** | **Tope de reportes de comentarios por usuario y día (HU-COM-006).** Ninguna ficha da la cifra. | **20 reportes/día**, valor elegido por el equipo y hasta hoy sin registrar en ninguna parte — que es justo lo que el riesgo #6 del Charter prohíbe. Queda anotado para que sea revisable en vez de invisible. | `comentarios.reportes.maximo-por-usuario-por-dia` en `ServicioDeModeracion` |

---

Cuando el PO decida una de las dos que quedan, se aplica el cambio en el sitio
indicado, se actualiza esta tabla y se cierra la entrada en la Sprint Review.

**Desde HU-ADM-001 (#481, `admin-parametros`)** los valores configurables de
D-02, D-16, D-20, D-23 y D-25 tienen su parámetro en el catálogo
(`GET /api/v1/parametros`): cambiarlos ya no exige tocar variables de entorno
ni desplegar, queda versionado y auditado. Consumidores conectados por API:
`moderacion-sanciones` (rango de suspensión y plazo de apelación). Los demás
(chat, salas, torneos, métricas) siguen leyendo su variable de entorno hasta
que se conecten al mismo puerto de lectura. Los valores del Charter están en
el catálogo como **inalterables**.
