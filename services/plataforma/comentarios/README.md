# Servicio de comentarios

Comentarios de los jugadores sobre los productos de la vitrina. Cubre la
publicacion con texto e imagenes de HU-COM-001 (RF-COM-001 y RF-COM-002):
un jugador comenta cuantas veces quiera pero califica una sola vez, el texto
pasa siempre por la lista negra de HU-ADM-002, y lo senalado por el filtro
queda retenido para un moderador en lugar de rechazarse, como fija RF-COM-007.

## Como correrlo

1. Levantar la base con `docker compose up -d plataforma-db`.
2. Copiar `cp .env.example .env` si no se tiene.
3. Pruebas: `./gradlew :services:plataforma:comentarios:test`
4. Arrancar: `./gradlew :services:plataforma:comentarios:bootRun` (puerto 8081).

El contrato del endpoint vive en `contracts/openapi/comentarios.yaml`.

## Variables de entorno

| Variable | Que es |
|---|---|
| `DB_RELACIONAL_URL`, `DB_USER`, `DB_PASS` | Conexion a PostgreSQL, esquema `comentarios` |
| `LISTA_NEGRA_VERIFICAR_URL` | Endpoint de verificacion de la lista negra (HU-ADM-002) |
| `COMENTARIOS_FORMATOS_IMAGEN` | Formatos de imagen admitidos, pendientes del Product Owner |

## Pendientes declarados

La seguridad OAuth2, el bloqueo real por sancion (RF-USR-004, Sprint 3), la
verificacion de que el producto exista contra el catalogo, y los valores que
faltan del Product Owner: largo maximo del texto y formatos de imagen.
