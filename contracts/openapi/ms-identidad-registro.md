> **SUPERSEDED — 23 de septiembre de 2026.**
> El contrato vigente de `/api/v1/auth` es
> [`ms-identidad-auth.yaml`](./ms-identidad-auth.yaml), que describe las
> **siete** rutas reales. Este documento se conserva porque es la unica
> version del contrato que existio durante los sprints 1 y 2, y borrarlo
> dejaria sin referencia a los issues que lo citan. **No lo uses para
> integrar**: lo que dice ya no coincide con el servicio.
>
> Lo que quedo desactualizado, comprobado contra el codigo:
>
> | Dice este documento | Hace el servicio hoy |
> |---|---|
> | «No se genera token de sesion (JWT) por ahora» | `POST /auth/login` **si** devuelve un JWT RS256 en el campo `token`, que este documento no lista |
> | «reenviar `rol` y `apodo` como cabeceras `X-User-Role` / `X-User-Name`» | Ese camino esta **cerrado**: `SecurityInterceptor` responde **403** y audita el intento como `SECURITY_BYPASS_ATTEMPT` (PR #629). Un cliente que siga esta instruccion no funciona |
> | Umbral de bloqueo: 4 intentos | `app.seguridad.umbral-intentos-fallidos=3` |
> | `PUT /auth/password` sin token -> 401 | Responde **403** (`.../errors/forbidden`): el interceptor es fail-closed y no distingue «no te identificaste» de «no puedes» |
> | Solo 4 rutas | Existen 7: faltan `/restablecer/solicitar`, `/jwks` y `/token` |
>
> La instruccion de las cabeceras `X-User-*` no es solo obsoleta: seguirla
> genera eventos de auditoria de intento de evasion. Por eso este aviso va
> arriba del todo y no en una nota al pie.

# Contrato de API: ms-identidad (Registro y Login de Usuario)

## POST /api/v1/auth/registro
Permite el autorregistro de un nuevo jugador en el sistema (HU-AUT-001).

### Request Body (multipart/form-data)

nombres: string (obligatorio)
apellidos: string (obligatorio)
email: string (obligatorio, único)
password: string (obligatorio, mín. 9 caracteres, con mayúscula, minúscula, número y símbolo)
apodo: string (obligatorio, único)
avatar: file (opcional, imagen JPG/PNG/WEBP, máx. 500 MB)

El registro deja de aceptar JSON puro — el body es un formulario multipart, ya que el avatar es
ahora un archivo real subido por el usuario (antes era una URL fija de una galería predefinida).
Si se envía, el archivo se valida y se guarda en el servidor; la URL resultante queda disponible en
`perfilUsuario.avatar`.

### Respuesta exitosa — `201 Created`
```json
{
  "id": 1,
  "apodo": "string",
  "email": "string",
  "estado": "ACTIVO",
  "intentosFallidos": 0,
  "bloqueadoHasta": null,
  "suspendidoHasta": null,
  "rol": {
    "id": 1,
    "nombre": "JUGADOR",
    "descripcion": "string"
  }
}
```
La contraseña nunca se incluye en la respuesta.

### Respuestas de error — `400 Bad Request`
El body es un string de texto plano (no un objeto JSON), con el motivo del rechazo. Casos:
- Correo ya registrado.
- Apodo ya en uso.
- Apodo rechazado por la lista negra (el mensaje incluye el motivo real devuelto por el servicio
  de moderación, ej. `"término ofensivo detectado"`).
- Contraseña que no cumple la política.
- Avatar con formato no permitido (solo JPG/PNG/WEBP) o que supera el tamaño máximo.

---

## POST /api/v1/auth/login
Autentica a un usuario existente y devuelve sus datos y rol vigente (HU-AUT-004).

### Request Body (JSON)
```json
{
  "email": "string (obligatorio)",
  "password": "string (obligatorio)"
}
```

### Respuesta exitosa — `200 OK`
```json
{
  "usuarioId": 1,
  "apodo": "string",
  "email": "string",
  "rol": "JUGADOR | MODERADOR | ADMINISTRADOR | SUPER_ADMINISTRADOR",
  "dispositivoNuevo": true
}
```
No se genera token de sesión (JWT) por ahora — el frontend debe conservar `rol` y `apodo` para
reenviarlos como los headers `X-User-Role` / `X-User-Name` en peticiones posteriores a endpoints
protegidos por RBAC.

### Bloqueo por intentos fallidos (RF-AUT-009)
Cada intento fallido de contraseña se cuenta por separado del resultado de esa misma petición
(persiste incluso si la petición termina rechazada por otro motivo). Al alcanzar el umbral
configurado, la cuenta queda bloqueada temporalmente, incluso si en un intento posterior se usa la
contraseña correcta.
- **Umbral:** 4 intentos fallidos consecutivos (el 4.º intento ya devuelve el bloqueo).
- **Duración del bloqueo:** 15 minutos desde el momento en que se alcanza el umbral.
- Un login exitoso reinicia el contador a 0.

### Respuestas de error
El body es un string de texto plano con el mensaje, en todos los casos:

| Caso | Código HTTP |
|---|---|
| Correo no existe o contraseña incorrecta | `401 Unauthorized` |
| Cuenta baneada permanentemente | `403 Forbidden` |
| Cuenta aún no activada (creada por un administrador, pendiente de canjear el token) | `403 Forbidden` |
| Cuenta suspendida (el mensaje incluye minutos restantes) | `403 Forbidden` |
| Cuenta bloqueada por intentos fallidos (el mensaje incluye minutos restantes) | `423 Locked` |

---

## POST /api/v1/auth/restablecer/confirmar
Permite a un usuario definir su propia contraseña usando un token de un solo uso — ya sea para
activar una cuenta creada por un administrador (HU-USR-002), o para restablecer una contraseña
olvidada (HU-USR-003).

### Request Body (JSON)
```json
{
  "token": "string (obligatorio)",
  "nuevaPassword": "string (obligatorio, mín. 9 caracteres, con mayúscula, minúscula, número y símbolo)"
}
```

### Respuesta exitosa — `200 OK`

"Contraseña actualizada correctamente. Ya puedes iniciar sesión."

Si el token es de tipo activación, el canje también cambia el estado de la cuenta de `INACTIVO` a
`ACTIVO`, en la misma operación.

### Respuestas de error — `400 Bad Request`
El body es un string de texto plano. Casos:
- El token no existe.
- El token ya fue utilizado.
- El token expiró (vencimiento configurable, por defecto 24 horas desde su generación).
- La contraseña nueva no cumple la política (misma regla que el registro).

---

## PUT /api/v1/auth/password
El usuario autenticado cambia su propia contraseña desde «Mi Cuenta» (HU-AUT-006, RF-AUT-006).

### Autenticación
`Authorization: Bearer <token de login>`. El sujeto del token es quien cambia; el cuerpo no
identifica a nadie. Requiere el permiso `MODIFICAR_PERFIL_PROPIO` (cualquier rol lo tiene).

### Request Body (JSON)
```json
{
  "passwordActual": "string (obligatorio)",
  "nuevaPassword": "string (obligatorio, mín. 9 caracteres, con mayúscula, minúscula, número y símbolo)",
  "confirmacion": "string (obligatorio, igual a nuevaPassword)"
}
```

### Respuesta exitosa — `200 OK`
```json
{
  "token": "string — token nuevo para ESTA sesión",
  "mensaje": "Contraseña actualizada. Las demás sesiones abiertas se cerraron."
}
```
El cambio sube la versión del token del usuario: todos los tokens emitidos antes dejan de valer
(las demás sesiones se cierran, CA-04). El cliente debe sustituir el token guardado por el
devuelto. Se envía un correo de aviso a la cuenta (CA-01) y se registra en auditoría; ninguno de
los dos frena el cambio si no responde.

### Respuestas de error
Problem details (`application/problem+json`) con `type`, `title`, `status` y `detail`. El
`detail` dice qué regla falla, para que la interfaz lo muestre tal cual (CA-03, CA-06).

| Caso | `type` (bajo `https://nexusbattles.upb.edu.co/errors/`) | Código HTTP |
|---|---|---|
| Contraseña actual incorrecta (cuenta como intento fallido, RF-AUT-009) | `contrasena-actual-incorrecta` | `422 Unprocessable Content` |
| Confirmación distinta de la nueva | `contrasena-confirmacion-no-coincide` | `422 Unprocessable Content` |
| La nueva no cumple la política | `contrasena-no-cumple-politica` | `422 Unprocessable Content` |
| La nueva es igual a la actual | `contrasena-repetida` | `422 Unprocessable Content` |
| Cuenta bloqueada por intentos fallidos | `cuenta-bloqueada` | `423 Locked` |
| Sin token o token de una versión anterior | — | `401 Unauthorized` |
| Campo vacío | — | `400 Bad Request` |
