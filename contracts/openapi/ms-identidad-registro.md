# Contrato de API: ms-identidad (Registro de Usuario)

## POST /api/auth/registro
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