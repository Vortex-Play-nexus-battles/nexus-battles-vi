# Servicio de notificaciones

Bandeja de avisos del jugador y su canal en tiempo real. Cubre HU-NOT-006
(RF-NOT-003, RF-NOT-004 y RF-NOT-006): el aviso llega a todas las sesiones
abiertas, no se pierde cuando no hay ninguna, y la sesion que vuelve de una
caida recupera unicamente lo que se perdio. El estado de lectura es del
jugador, no del dispositivo.

Desde la 1.3.0 del contrato HTTP cubre ademas HU-NOT-001 CA-02 (#532): marcar
todos los avisos como leidos de una vez, sin dejar la bandeja a medias.

Antes de entregar lo pendiente a una sesion incorpora avisos de dos fuentes
(`bandeja.FuentesDeAvisos`): los cambios del catalogo (HU-NOT-001, 1.4.0,
`CAMBIO_CATALOGO`) y, desde la 1.5.0, los cambios en las politicas de uso que
publica la administracion en admin-parametros (SA-11, §7.3.7,
`CAMBIO_POLITICA`). Si una fuente no responde, la entrega sigue sin sus avisos.

## Como correrlo

1. Levantar la base con `docker compose up -d plataforma-db`.
2. Copiar `cp .env.example .env` si no se tiene.
3. Pruebas: `./gradlew :services:plataforma:notificaciones:test`
4. Arrancar: `./gradlew :services:plataforma:notificaciones:bootRun` (puerto 8085).

Los contratos viven en `contracts/openapi/notificaciones.yaml` (HTTP) y en
`contracts/websocket/notificaciones.yaml` (canal STOMP).

## Variables de entorno

| Variable | Que es |
|---|---|
| `DB_RELACIONAL_URL`, `DB_USER`, `DB_PASS` | Conexion a PostgreSQL, esquema `notificaciones` |
| `NOTIFICACIONES_WS_ENDPOINT` | Ruta del handshake WebSocket, `/ws` en local |
| `NOTIFICACIONES_WS_ORIGENES` | Origenes permitidos del handshake, separados por coma |
| `PRODUCTOS_BASE_URL`, `NOTIFICACIONES_CATALOGO_*` | Avisos de cambios del catalogo (HU-NOT-001); ver `catalogo.ConfiguracionDelCatalogo` |
| `PARAMETROS_URL`, `NOTIFICACIONES_POLITICAS_*` | Avisos de cambios en las politicas de uso (SA-11); ver `politicas.ConfiguracionDePoliticas` |

## Pendientes declarados

La seguridad OAuth2 y la identidad de cada conexion WebSocket en el handshake,
el bus de eventos que reemplace al endpoint interno provisional, y la eleccion
del broker externo de mensajes, que es una decision de equipo.
