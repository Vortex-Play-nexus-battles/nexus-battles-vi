# Contrato de API: ms-identidad (Registro y Login de Usuario)

## POST /api/v1/auth/registro
Permite el autorregistro de un nuevo jugador en el sistema (HU-AUT-001).

### Request Body (JSON)
```json
{
  "nombres": "string (obligatorio)",
  "apellidos": "string (obligatorio)",
  "email": "string (obligatorio, único)",
  "password": "string (obligatorio, mín. 9 caracteres, con mayúscula, minúscula, número y símbolo)",
  "apodo": "string (obligatorio, único)",
  "avatar": "string (opcional, URL)"
}
```

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
reenviarlos como los headers `X-User-Role` /
