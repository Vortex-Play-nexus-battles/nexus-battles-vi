# ADR-002 — Identidad de usuario entre ms-identidad y la plataforma

- **Estado:** aceptado (2026-09-17). Implementado en `feat/identidad-jwks-plataforma`.
- **Sustituye a:** nada. **Se relaciona con:** ADR-001 (autenticación entre servicios).
- **Decide:** quién emite los tokens de usuario y cómo los verifica el resto.

## Problema

El login de la aplicación no servía para jugar. Verificado el 2026-09-17 sobre
`develop` y sobre el host de desarrollo:

- `ms-identidad` emitía un JWT **HS256** firmado con `app.jwt.clave-secreta`,
  con `sub` = **apodo**, y los claims `rol` (singular) y `uid`
  (`JwtService.java:52,63`).
- `salas-partidas` y `moderacion-sanciones` eran servidores de recursos
  configurados contra **Keycloak** (`issuer-uri: .../realms/nexus-battles`) y
  exigían `ROLE_JUGADOR` leído de `realm_access.roles`
  (`SecurityConfig.java:41`, `ConversorRolesJwt.java:39`).
- **Keycloak no está provisionado** en ningún `docker-compose` del repositorio.

Consecuencia medible: tras iniciar sesión, `GET /api/v1/salas`, `POST
/api/v1/salas` y el `CONNECT` de STOMP respondían 401/403. Y aunque la firma se
hubiera aceptado, `SalasController.java:98` hace `UUID.fromString` sobre el
nombre del principal, que era un apodo: habría fallado igual.

Esto bloqueaba el MVP completo: sin identidad válida no hay sala, ni chat, ni
canal de partida.

## Opciones consideradas

| Opción | Por qué no / por qué sí |
|---|---|
| Provisionar Keycloak y migrar el registro y el login | Es el destino declarado en la pila (RF-AUT/RF-RBAC), pero exige mover registro, login, recuperación de contraseña, roles y sesiones de `ms-identidad` a Keycloak, y sincronizar los usuarios ya creados. No cabe antes del MVP y arriesga funcionalidad que hoy sí funciona. |
| Repartir la clave HMAC a los servicios que verifican | Es lo más rápido. **Rechazada:** la clave de HMAC sirve igual para verificar que para **emitir**. Dársela a ocho servicios es darles la capacidad de fabricar un token de cualquier usuario con cualquier rol. Rompe "seguridad y permisos por rol comprobados" del Definition of Done. |
| **`ms-identidad` firma con RSA y publica su JWKS** | **Elegida.** |

## Decisión

1. **`ms-identidad` firma con RS256** (`ClavesDeFirma`, `JwtService`). La clave
   privada sale de `JWT_CLAVE_PRIVADA` (PKCS#8 en base64); si no está definida
   se genera un par al arrancar y el servicio lo avisa por bitácora, con la
   consecuencia explícita de que los tokens no sobreviven a un reinicio.
2. **Publica su clave pública** en `GET /api/v1/auth/jwks` (RFC 7517), igual
   que hace Keycloak en `/protocol/openid-connect/certs`. Es público y no
   expone ningún secreto: una clave pública verifica firmas, no las produce.
3. **La plataforma verifica con `jwk-set-uri`**, no con `issuer-uri`, porque
   `ms-identidad` no publica documento de descubrimiento OpenID. La URL viaja
   en `IDENTIDAD_JWKS_URL`.
4. **`ConversorRolesJwt` (compartido) entiende las dos formas de token**: roles
   desde `realm_access.roles` (Keycloak) o desde `rol` (ms-identidad), y usa
   `uid` como nombre del principal cuando existe, con `sub` como alternativa.

## Por qué el principal es `uid` y no `sub`

El `sub` de `ms-identidad` es el apodo, y es deliberado: su propio
`SecurityInterceptor` lo lee como identidad para los controladores de
administración, así que cambiarlo rompería esa cadena. Pero el apodo es
**mutable**, y los servicios de plataforma guardan al usuario por identificador
(el anfitrión de una sala se compara por ese valor): con el apodo como
principal, cambiar de apodo convertiría a alguien en otra persona a ojos de sus
propias salas. Por eso manda `uid`.

Cuando la identidad se mueva a Keycloak, el `sub` ya será el identificador
estable y esta regla seguirá valiendo sin tocar código.

## Consecuencias

- El login de la aplicación sirve para jugar: un mismo token abre sala, chat y
  canal de partida.
- Ningún servicio salvo `ms-identidad` puede emitir tokens de usuario.
- Migrar a Keycloak es **cambiar una variable de entorno** (`IDENTIDAD_JWKS_URL`)
  en los servicios que verifican, más mover el alta de usuarios. El conversor ya
  entiende el formato de destino.
- `app.jwt.clave-secreta` desaparece. Un despliegue que la tuviera configurada
  la ignora; lo que hay que definir ahora es `JWT_CLAVE_PRIVADA`.
- Mientras `JWT_CLAVE_PRIVADA` no esté definida en el host, reiniciar
  `ms-identidad` obliga a iniciar sesión de nuevo. Aceptable en desarrollo y
  avisado por bitácora; en cuanto haya más de una instancia, deja de serlo.
- **Actualización R18.5 (24-sep-2026).** En dev resultó no ser aceptable, y
  no por las sesiones: los demás servicios guardan su credencial de servicio
  hasta 15 min, así que tras cada despliegue de `ms-identidad`
  `salas-partidas` seguía presentando a inventario un token firmado con la
  clave anterior, inventario respondía 401 y crear una sala daba 503 hasta que
  caducaba (smoke de dev rojo tres veces el mismo día). Desde R18.5
  `desplegar.sh` genera la clave **una vez** en el host
  (`/opt/nexus/secretos-firma.env`, 600), la reutiliza en cada despliegue y
  en `revertir.sh`, y la exporta solo para la interpolación de
  `docker-compose.cuentas.yml`: **nunca al `.env`**, que cargan los ocho
  servicios de plataforma y con el que cualquiera de ellos podría fabricar
  tokens. Un secret de GitHub `JWT_CLAVE_PRIVADA`, si existe, manda. Prueba:
  `scripts/cd/pruebas/clave-de-firma.sh`.

## Pendiente, no bloqueante

- Rotación de claves: el JWKS ya publica `kid`, así que admitir dos claves a la
  vez es aditivo cuando haga falta.
- `ms-identidad` valida la versión del token (`ver`) contra su base de datos
  para invalidar sesiones tras un cambio de rol. La plataforma **no** hace esa
  comprobación: un token sigue siendo válido hasta que expira. Cerrarlo exige
  una consulta entre servicios o tokens de vida corta con refresco; se decide
  cuando exista el caso de uso, no antes.
