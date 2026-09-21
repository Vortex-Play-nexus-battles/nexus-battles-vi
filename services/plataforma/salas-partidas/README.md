# salas-partidas

Servicio de la sala de batalla y del canal en tiempo real de la partida
(HU-SAL-001 a HU-SAL-005, Simón; incluye el chat de HU-JUE-015, Alexander).
Módulo M09 del Grupo 6. Puerto local `8084`, esquema PostgreSQL `salas_partidas`.

## Contratos (se acuerdan antes de implementar — regla 1)

| Contrato | Archivo |
|---|---|
| HTTP | `contracts/openapi/salas-partidas.yaml` |
| Tiempo real (STOMP sobre WebSocket) | `contracts/websocket/salas-partidas.yaml` |

Los errores salen como problem details (RFC 7807) y la interfaz decide por
`type`, nunca por texto (`shared/ui-kit/MAPEO-ERRORES.md`).

## Qué hace hoy

| Operación | Estado | Historia |
|---|---|---|
| `POST /api/v1/salas` — crear sala con modalidad, participantes, recompensa | Implementado | HU-SAL-001 |
| `GET /api/v1/salas` — listado paginado con filtros | Implementado | HU-SAL-002 |
| `POST /api/v1/salas/{id}/participantes` — ingreso con cupo y bloqueo optimista | Implementado | HU-SAL-002 |
| `/tema/salas/{idSala}` — `sala.participante.ingreso` a los suscritos | Implementado y probado extremo a extremo (`CanalDeSalaIT`) | HU-SAL-002 |
| `/tema/salas/{idSala}/chat`, `/tema/chat/general` — chat con filtro de lista negra | Implementado | HU-JUE-015 |
| `/tema/partidas/{idPartida}` — `partida.accion.resuelta`, `partida.turno.cambiado`, `partida.finalizada` (con `reparto` y `equipoGanador`) | Implementado; lo dispara `EjecutarAccion` | HU-SAL-005, HU-JUE-014 |
| `GET /salas/{id}/verificacion-heroe` | Implementado (`PuertaDeHeroe` contra inventario) | HU-SAL-003 |
| Modalidades: `CONTRA_IA` con la máquina en el segundo cupo; `HASTA_SEIS` con `heroesIA` (0..n−1) que ocupan cupo y `tamanoEquipo` (1–3) con equipos por orden de entrada, victoria por equipo y sin fuego amigo; el turno salta a los caídos | Implementado (V10, contrato 1.2.0); decisiones D-05/D-12/D-13 en `docs/gobierno` | HU-SAL-004 |

## Canal en tiempo real

- Endpoint `${SALAS_WS_ENDPOINT:/ws}`; prefijos `/tema`, `/cola`, `/app`, `/usuario`
  (los mismos que notificaciones).
- El handshake HTTP está abierto porque el navegador no puede poner cabeceras;
  el **JWT viaja en la cabecera `Authorization` del frame `CONNECT`** y sin él
  no hay sesión (`AutenticacionStomp`). Nunca en la URL.
- `AutorizacionDeDestinos`: `/tema/salas/{id}` pública para cualquier jugador
  autenticado, privada solo para participantes, inexistente se rechaza.
  `/tema/partidas/{id}` y `/tema/chat/general` pasan.
- El cliente del navegador es `frontend/app-web/src/comun/transporte-stomp.js`
  (único transporte STOMP del frontend); `plataforma/salas-partidas/cliente-chat.js`
  solo añade el JWT y `ErrorDeCanal`.

## Puertos que dependen de otros equipos

| Puerto | Adaptador hoy | Qué falta y de quién |
|---|---|---|
| `CreditosDelJugador` (reservar/liberar/consumir la apuesta, HU-JUE-014) | `ClienteCreditos` contra `ms-finanzas` por `contracts/openapi/creditos.yaml` (`CREDITOS_URL`), con la credencial de servicio de ADR-005. Si el libro contesta algo que no sirve: `503 creditos-no-disponibles`; si no responde: degradación (abajo). Nada queda reservado; una partida ya terminada deja su liquidacion `PENDIENTE` (tabla `liquidaciones_de_apuesta`, V9) y `ReintentarLiquidaciones` la cierra despues. | Que Cuentas cierre `/creditos/**` a `ROLE_SERVICIO` (hoy `permitAll`) y desplegar `ms-finanzas` en el host de dev cuando quepa. |
| `HeroeDelJugador` (puerta de héroe, HU-SAL-003) | `ClienteInventarioHeroes` contra `inventario.yaml` (`INVENTARIO_BASE_URL`) con la credencial de servicio; prototipo y defensa de `productos`/`heroes` (degradan solos). | Que inventario publique «el héroe activo»: hoy se toma el primero disponible y equipado. Contenido (#27). |
| `MotorDeCombate` (resultado de la acción) | `ClienteMotorCombate` contra `motor-combate.yaml` (`MOTOR_COMBATE_URL`). | Mapeo héroe → prototipo de distribución (hoy `GUERRERO_ARMAS` para todos). Grupo 2 (#31). |
| Sanción activa (chat, HU-JUE-015) | `ClienteSanciones` contra `moderacion-sanciones-consulta.yaml` (`SANCIONES_URL`); sin respuesta, `503 sanciones-no-disponibles` y nada sale al canal (D-14). | Tipo de sanción en el contrato, si el PO distingue silencio de otras. |

## Degradación controlada (HU-DIS-003)

Cada dependencia de arriba va detrás de su propio `CortaCircuitos`
(`shared/libs/plataforma-resiliencia`, `ConfiguracionDeResiliencia`): inventario
→ sección «Inventario», motor → «Motor de combate», libro → «Apuesta de
creditos». Cuando una **no responde** (conexión, tiempo, 5xx, credencial de
servicio que no se pudo obtener) la operación sale `503` con `type`
`seccion-no-disponible`, `seccion`, `reintentarEnSegundos` y `Retry-After`; tras
`RESILIENCIA_FALLOS_PARA_ABRIR` fallos seguidos se deja de llamar durante
`RESILIENCIA_REINTENTAR_EN_SEGUNDOS` y luego pasa **una** llamada de prueba. Un
4xx es una respuesta, no una caída: no abre nada (`Contestacion`). Por STOMP,
el mismo problem detail vuelve por `/usuario/cola/salas`. El frontend lo pinta
con `Seccion degradada` (`comun/degradacion/aviso-degradacion.js`) en
`crear-sala`, `batallas`, `validacion-heroe` y los controles de combate, y el
resto de la vista sigue. Probado apagando contenedores de verdad en
`tests/e2e/degradacion.e2e.spec.js` y con `DegradacionDeInventarioIT`.

## Variables de entorno

`DB_RELACIONAL_URL`, `DB_USER`, `DB_PASS`, `DIRECTORIO_ACTIVO_URL`,
`DIRECTORIO_ACTIVO_CLIENT_ID`, `DIRECTORIO_ACTIVO_CLIENT_SECRET`,
`INVENTARIO_BASE_URL`, `PRODUCTOS_BASE_URL`, `HEROES_BASE_URL`,
`MOTOR_COMBATE_URL`, `CREDITOS_URL`, `SANCIONES_URL`,
`RESILIENCIA_FALLOS_PARA_ABRIR`, `RESILIENCIA_REINTENTAR_EN_SEGUNDOS`,
`SALAS_WS_ENDPOINT`, `SALAS_WS_ORIGENES`, `LISTA_NEGRA_VERIFICAR_URL`,
`CHAT_WS_ORIGENES`, `CHAT_HISTORIAL_TAMANO`. Ningún valor real en el repo
(regla 10); los valores tras `:` en `application.yml` son los del entorno local.

## Pruebas

```bash
./gradlew :services:plataforma:salas-partidas:test --rerun-tasks
```

Requiere Docker Desktop: `RepositorioSalasJpaIT`, `IngresoConcurrenteIT`,
`CanalDeSalaIT` y `CanalDePartidaIT` levantan PostgreSQL 17 por Testcontainers
y **fallan** si no está, en vez de omitirse. El único doble de seguridad en las
IT es el `JwtDecoder` (que en producción valida contra Keycloak); la cadena de
filtros y la exigencia de token no se debilitan.
