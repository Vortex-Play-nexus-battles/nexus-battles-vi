# ADR-005 · Emisor transitorio de credenciales de servicio (ms-identidad hasta Keycloak)

**Estado:** aceptado — 21-sep-2026. **Dueño:** Plataforma (M16A/M16B, Grupo 6).
**Disparador:** bloque de seguridad/identidad transversal (#441): `comentarios`,
`notificaciones`, `correo`, `inventario`, `ms-ecommerce` y `ms-cumplimiento`
identificaban al llamante por el cuerpo, por `X-User-Name`/`X-User-Id` o por
un parámetro de la URL, y ninguna llamada entre servicios llevaba credencial.
**Complementa:** ADR-001 (qué patrón) y ADR-002 (quién emite los tokens de usuario).

## Problema

ADR-001 decidió `client_credentials` contra Keycloak y `plataforma-seguridad`
ya trae el lado cliente (`TokenDeServicioOAuth2`, `InterceptorDePortadorDeServicio`)
y el lado servidor (`CadenaDeSeguridad`, `ActorDeServicio`). Pero **no hay
Keycloak**: no está aprovisionado en ningún entorno y no cabe en el host de
desarrollo (`t3.small`, 2 GiB, con nueve servicios ya en memoria limitada; ver
#430). `ms-finanzas` dejó `/creditos/**` abierto "hasta que infra registre el
cliente en el realm"; `inventario` sigue leyendo `X-User-Name`; `correo` y
`notificaciones` no tienen cadena de seguridad. Cada servicio que quiso cerrar
sus rutas entre servicios se topó con que no había quién emitiera la credencial.

Mientras tanto, ADR-002 ya convirtió a `ms-identidad` en **el emisor de la
plataforma**: firma RS256, publica su clave en `GET /api/v1/auth/jwks` y todos
los servidores de recursos lo verifican por `IDENTIDAD_JWKS_URL`.

## Decisión

**`ms-identidad` emite también las credenciales de servicio, con el mismo
protocolo que emitiría Keycloak, hasta que exista un realm.**

1. `POST /api/v1/auth/token` con `grant_type=client_credentials`, autenticación
   del cliente por HTTP Basic (`client_secret_basic`, lo que envía Spring) o por
   `client_id`/`client_secret` en el cuerpo. Respuesta RFC 6749 §5.1
   (`access_token`, `token_type=Bearer`, `expires_in`); errores §5.2
   (`invalid_client` 401, `unsupported_grant_type`/`invalid_request` 400).
2. Los clientes admitidos y sus secretos salen de `AUTH_CLIENTES_SERVICIO`
   (`cliente=secreto;otro=secreto`, mínimo 16 caracteres). Sin la variable no
   hay clientes: es el equivalente a un realm vacío. Regla 10: ningún valor
   real se versiona; en el host lo genera y persiste el flujo de CD.
3. El token de servicio se firma con **la misma clave RSA y el mismo `kid`** que
   los tokens de usuario. Para un servidor de recursos no hay nada nuevo que
   configurar: mismo `jwk-set-uri`, mismo `ConversorRolesJwt`.
4. Forma del token: `sub` = `azp` = `client_id`, `rol` = `SERVICIO`, `iss`,
   `iat`, `exp` (15 minutos por defecto). **Sin `uid` ni `ver`**: un token de
   servicio nunca identifica a un usuario, y `IdentidadDelToken.idDe` falla a
   la vista si alguien intenta leerlo como tal.
5. Autorización en el servidor de recursos: `hasRole("SERVICIO")` para "¿es un
   servicio autorizado?" y `ActorDeServicio.desde(jwt)` (`azp`) cuando importa
   cuál (por ejemplo, solo `ms-subastas` libera un bloqueo de subasta).
   El propietario afectado viaja **explícito** en la petición (`X-User-Name`,
   `propietarioUid`, `usuarioId` en el cuerpo), como fija ADR-001; el token
   solo dice qué servicio habla.
6. `ms-identidad` no se pide la credencial a sí mismo por HTTP: se la firma
   (`CredencialPropia`, `client_id=ms-identidad`) y la lleva en cada
   `RestClient` saliente (correo, notificaciones, cumplimiento).
7. En los clientes Gradle, `TokenDeServicioOAuth2.endpointDeToken` acepta una
   URL que ya termina en `/token` y la usa tal cual. Con Keycloak,
   `DIRECTORIO_ACTIVO_URL` vuelve a ser la URL del realm y no cambia nada más.

```
salas-partidas                       ms-identidad                        inventario
   │  POST /api/v1/auth/token            │                                   │
   │  Basic salas-partidas:secreto ─────▶│  ¿está en AUTH_CLIENTES_SERVICIO? │
   │◀──── {access_token (RS256, kid)} ───│                                   │
   │  GET /inventario/elementos                                              │
   │  Authorization: Bearer <token de servicio>                              │
   │  X-User-Name: <apodo del jugador afectado> ────────────────────────────▶│
   │                                     │  JWKS ◀── jwk-set-uri ────────────│
   │                                     │        ROLE_SERVICIO + azp        │
```

## Por qué no las alternativas

- **Esperar a Keycloak.** No cabe en el host actual sin sacrificar estabilidad
  o pagar (restricción del bloque: costo cero, un solo host). Mientras tanto
  las rutas entre servicios quedaban abiertas o con cabeceras falsificables.
- **Secreto compartido HS256 entre servicios.** Rechazado en ADR-001: la clave
  que verifica también emite; ocho servicios podrían fabricar tokens.
- **API keys estáticas por servicio.** Sin caducidad, sin `azp`, sin el
  protocolo que ya implementa `plataforma-seguridad`; y habría que tirarlas
  al llegar Keycloak. El endpoint de token, en cambio, se sustituye cambiando
  una URL.
- **Que ms-identidad firme tokens de usuario "de servicio"** (`rol=JUGADOR`
  con un `uid` inventado). Es exactamente la suplantación que ADR-001 prohíbe.

## Consecuencias

- Los seis servicios del bloque pueden exigir `ROLE_SERVICIO` en sus rutas
  internas hoy, y `ms-finanzas` puede cerrar `/creditos/**` cuando
  `ms-subastas` adopte `TokenDeServicio` (ADR-001, migración gradual).
- Un token de servicio filtrado vale 15 minutos y no sirve como usuario en
  ningún servicio: en `ms-identidad`, `Role.valueOf("SERVICIO")` falla; en la
  plataforma, `hasRole("JUGADOR")` no lo admite.
- `ms-identidad` pasa a ser dependencia de arranque de quien pida credencial
  (ya lo era por el JWKS). Los clientes la piden perezosamente y la cachean
  (`TokenDeServicioOAuth2`), así que un reinicio del emisor no tumba a nadie
  mientras los tokens vigentes no caduquen.
- Rotar un secreto = cambiar `AUTH_CLIENTES_SERVICIO` y el
  `DIRECTORIO_ACTIVO_CLIENT_SECRET` del cliente, y reiniciar ambos.
- Las pruebas de los servidores de recursos usan **tokens reales** firmados por
  `EmisorDeTokensDePrueba` (`testFixtures` de `plataforma-seguridad`), con su
  JWKS servido por HTTP: se verifica firma, caducidad y claims con el mismo
  decodificador que en producción, no con un `JwtDecoder` que acepta todo.

## Qué queda igual que en ADR-001

Nombres de rol de servicio, `aud`, y el aprovisionamiento del realm siguen
pendientes de aprobación de los tres Scrum Masters. Este ADR no los decide:
usa un único rol `SERVICIO` porque es lo mínimo que distingue un servicio de
un usuario, y deja el `azp` para las reglas finas.
