# ADR-001 · Autenticación entre microservicios (service-to-service)

**Estado:** propuesto — pendiente de aprobación de los tres Scrum Masters.
**Fecha:** 14-sep-2026. **Dueño:** Plataforma (M16A/M16B, Grupo 6).
**Disparador:** integración `ms-subastas → inventario` (HU-SUB-001 / HU-INV-010).

## Problema

Un microservicio necesita llamar a otro **por sí mismo**: cierre automático de
subastas (`@Scheduled`), liberación de un bloqueo tras una compensación,
reintentos técnicos. En esos caminos no hay petición HTTP de un jugador ni JWT
que propagar, y **no debe fabricarse uno**. Hay que resolver quién autentica al
servicio llamante y cómo se separa de la identidad del jugador afectado.

## Situación en `develop` (auditada el 14-sep, `e74c3b8`)

- Ninguna llamada entre servicios lleva credencial (`InventarioClientHttp`,
  `NotificacionesClientHttp`, `CatalogoProductosClientHttp`, `SancionesClientHttp`,
  `ResolutorDeProductoHttp`, `ClienteHeroesHttp`, `CorreoClient`).
- Los endpoints que consumen otros servicios están en `permitAll` o sin cadena
  de seguridad (`lista-negra/verificar`, `sanciones/*/activa`, `productos/{id}`,
  `correo`, `notificaciones`). Inventario deriva el propietario de la cabecera
  `X-User-Name`, sin verificar.
- Dos emisores de JWT incompatibles: `ms-identidad` (HS256, secreto compartido,
  claims `sub`=apodo, `rol`, `ver`, `uid`) y Keycloak (Plataforma y Productos,
  `plataforma-seguridad`). Keycloak está en la pila aprobada pero no está
  aprovisionado en el repositorio.
- Las variables `DIRECTORIO_ACTIVO_URL`, `DIRECTORIO_ACTIVO_CLIENT_ID` y
  `DIRECTORIO_ACTIVO_CLIENT_SECRET` existen en `.env.example` y en los secretos
  de CD, y ningún código las lee.

## Decisión

**OAuth2 `client_credentials` (RFC 6749 §4.4) contra Keycloak, con una cuenta de
servicio por microservicio.** El servicio llamante obtiene un token con su
`client_id`/`client_secret` y lo envía en `Authorization: Bearer`. El servicio
llamado es servidor de recursos (`CadenaDeSeguridad` de `plataforma-seguridad`),
valida firma y emisor contra el JWKS del realm y autoriza por **rol de servicio**.

```
Authorization: Bearer <token de la cuenta de servicio>   → ACTOR (claim azp = client_id)

{ "propietarioUid": "...", "subastaId": "...", ... }      → SOBRE QUIÉN SE OPERA (dato de negocio)
```

Reglas:

1. El token identifica **al servicio**, nunca a un jugador. El servidor de
   recursos **no infiere el propietario del token**: lo recibe en la petición
   (`propietarioUid`, `subastaId`, `elementoInventarioId`…) y aplica sus reglas
   de dominio sobre él (pertenencia, estado, idempotencia).
2. Autorización gruesa por rol de realm en la cadena de seguridad
   (`hasRole(<rol de servicio>)`); trazabilidad por `azp`
   (`ActorDeServicio.desde(authentication)`).
3. Funciona sin usuario: la credencial se pide y renueva con las credenciales
   propias del servicio (`TokenDeServicio.portador()`), en cualquier hilo,
   incluido `@Scheduled`, compensaciones y reintentos.
4. `Idempotency-Key` y demás cabeceras de negocio no cambian.

## Por qué no las alternativas

| Alternativa | Motivo de rechazo |
|---|---|
| Propagar o reutilizar el JWT del jugador | No existe en jobs; caduca; confunde actor con propietario. |
| Fabricar un JWT de jugador | Suplantación. Prohibido. |
| JWT «de servicio» firmado con `app.jwt.clave-secreta` (HS256 compartido) | Quien tenga el secreto acuña tokens de cualquier servicio o jugador; consolida la desviación de la pila. |
| `X-User-Name` u otra cabecera de confianza | Sin firma ni caducidad: cualquiera en la red la pone. |
| API key estática | Sin identidad verificable, sin caducidad, sin rotación. |
| mTLS | No hay PKI ni ingress que lo soporte en el repositorio. |

## Cómo se usa (implementado en `shared/libs/plataforma-seguridad`)

**Servicio que llama** — declara la biblioteca y las tres variables; recibe por
autoconfiguración `TokenDeServicio` e `InterceptorDePortadorDeServicio`:

```java
// Con RestClient:
RestClient inventario = RestClient.builder().baseUrl(url).requestInterceptor(interceptor).build();
// Con java.net.http.HttpClient:
peticion.header("Authorization", "Bearer " + tokenDeServicio.portador());
```

Un servicio que aún no usa Spring Security (hoy `ms-subastas`) debe declarar una
`SecurityFilterChain` con `permitAll` para conservar su autenticación actual de
jugador; sin ella, Spring Security protegería todas sus rutas por defecto.

**Servicio llamado** — `nexus.seguridad-conventions` + `CadenaDeSeguridad.aplicarBase`
y, en sus rutas internas, `hasRole(<rol de servicio>)`. Puede convivir con la
autenticación de jugador actual usando dos cadenas (`securityMatcher`) mientras
no se unifique el emisor.

**Contrato** — en el OpenAPI del servicio llamado, las operaciones internas
declaran un `securityScheme` `bearerAuth` (JWT) y el propietario afectado como
campo explícito del cuerpo o de la ruta, fuera del esquema de seguridad.

## Pendiente de aprobación (no decidido en este ADR)

- **Nombre del rol de servicio.** No hay convención. Propuesta: rol de realm
  `SERVICIO_<MODULO>` (p. ej. `SERVICIO_SUBASTAS`). En las pruebas de la
  biblioteca el nombre es provisional y está marcado como tal.
- **Audience.** Recomendado añadir un mapper `aud` = servicio llamado; no es
  requisito de la primera versión.
- **Aprovisionamiento de Keycloak** (compose + realm export `nexus-battles`,
  unificando el realm `nexus` de productos) y confirmación de la instancia
  desplegada detrás de `DIRECTORIO_ACTIVO_URL`.
- **Emisor único de identidad de jugador** (Keycloak vs `ms-identidad`). Este ADR
  no lo resuelve ni lo bloquea.

## Migración gradual

Los endpoints S2S hoy abiertos (`lista-negra/verificar`, `sanciones/*/activa`,
`productos/{id}`, `correo`, `notificaciones`) se migran uno a uno a este patrón
cuando su dueño lo programe. No se migra nada en el PR que introduce este ADR.
