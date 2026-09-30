# Servicio de comentarios

Comentarios y calificaciones de los jugadores sobre los productos de la
vitrina. Cubre HU-COM-001..004 (RF-COM-001, 002, 004, 007), la moderación de
R10.1 (RF-COM-005, 006, 008) y, desde B3 (BACKEND-05), la comunidad de
producto del 7.1 del documento del curso: «la calificación promedio valorada en
cinco estrellas, incluyendo el hilo de comentarios... Los usuarios solo pueden
calificar un producto una vez, pero podrán agregar o retirar tantos
comentarios como sea de su agrado».

El contrato vive en `contracts/openapi/comentarios.yaml` (1.6.0).

## Qué hace

- **Calificación separada del comentario** (`/products/{id}/rating`): una por
  jugador y producto, sin editar ni retirar. La decide una restricción única de
  la base (`INSERT ... ON CONFLICT DO NOTHING`): dos peticiones a la vez dejan
  una (201) y la otra recibe 409 `ya-calificado`. La tabla `calificaciones` es
  la única fuente del promedio; `estrellas` en el cuerpo de un comentario sigue
  aceptándose y crea la calificación si aún no había (si ya había,
  `calificacionDescartada`).
- **Hilo paginado** (`pagina`, `tamano` hasta 50, del más reciente al más
  antiguo): un número fijo de consultas por página, sin cargar el producto
  entero. Las estrellas de cada comentario son las de la calificación de su
  autor.
- **Producto validado** contra el catálogo (`GET {PRODUCTOS_URL}/api/v1/productos/{id}`):
  404 si no existe; si el catálogo no contesta, las escrituras responden 503 y
  los GET del hilo siguen funcionando. Caché en memoria: «existe» 5 min, «no
  existe» 30 s.
- **Imágenes reales**: se suben aparte (`POST /comentarios/imagenes`, 2 MB,
  JPEG/PNG/WebP por firma de bytes y estructura, dimensiones ≤ 4096×4096 leídas
  de la cabecera sin decodificar), el comentario lleva sus `id` (hasta 3, solo
  del mismo autor y una vez) y se sirven con `nosniff`, `CSP default-src
  'none'` y caché larga solo si son públicas. Las pendientes se borran a las
  24 h.
- **Moderación**: cola priorizada, reportes, APROBAR/OCULTAR/ELIMINAR/RESTAURAR
  y, desde B3, EDITAR (texto anterior en el asiento), MARCAR/DESMARCAR (lista
  de seguimiento con `marcado=true`) y la IP de origen en cada asiento.
- **Filtro automático** con la lista negra (`contexto: COMENTARIO`): lo que la
  política manda a `REVISION` —o todo, si la lista negra no responde— queda
  EN_REVISION (202).

### Por qué las imágenes van en PostgreSQL

Se guardan en la propia base del servicio (`bytea`, tabla
`imagenes_de_comentario`) y no en un volumen del host: así viajan con los
respaldos de la base y no dependen de un disco que desaparece al recrear el
contenedor o mover el servicio de host. El crecimiento está acotado (2 MB por
imagen, 3 por comentario, limpieza de pendientes a las 24 h).

## Cómo correrlo

1. Levantar la base con `docker compose up -d plataforma-db`.
2. Copiar `cp .env.example .env` si no se tiene.
3. Pruebas: `./gradlew :services:plataforma:comentarios:check` (unitarias, IT
   con Testcontainers PostgreSQL y servidores HTTP de prueba para catálogo,
   sanciones y lista negra, JaCoCo ≥ 80 %). Genera además el pacto
   `contracts/pactos/comentarios-productos.json`.
4. Arrancar: `./gradlew :services:plataforma:comentarios:bootRun` (puerto 8081).

## Variables de entorno

| Variable | Qué es |
|---|---|
| `DB_RELACIONAL_URL`, `DB_USER`, `DB_PASS` | Conexión a PostgreSQL, esquema `comentarios` |
| `IDENTIDAD_JWKS_URL` | JWKS de ms-identidad para verificar los tokens |
| `LISTA_NEGRA_VERIFICAR_URL` | Verificación de la lista negra (HU-ADM-002) |
| `SANCIONES_URL` | Base `/api/v1` de moderacion-sanciones (sanción activa) |
| `PRODUCTOS_URL` | Base del catálogo de productos, sin `/api/v1` (B3) |
| `PRODUCTOS_TIMEOUT_CONEXION`, `PRODUCTOS_TIMEOUT_LECTURA` | Tiempos del cliente del catálogo (1 s y 2 s por omisión) |
| `NOTIFICACIONES_URL`, `AUDITORIA_URL` | Aviso al autor y auditoría de las decisiones de moderación (fail-open) |
| `COMENTARIOS_FORMATOS_IMAGEN` | Subconjunto de `jpg,png,webp` admitido (los tres por omisión) |
| `COMENTARIOS_MAXIMO_POR_LOTE` | Máximo de comentarios por lote de moderación (50 por omisión; D-35) |

## Pendientes declarados

- El largo máximo del texto de un comentario sigue pendiente del Product Owner
  (issue 34); EDITAR lo acota a 2000 caracteres porque así lo fija el contrato.
- La IP del asiento es el primer valor de `X-Forwarded-For`; el borde de dev
  añade la IP que ve a la cabecera del cliente en vez de sustituirla, así que
  ese valor lo puede declarar el cliente (ver el resumen de B3).
