markdown
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
