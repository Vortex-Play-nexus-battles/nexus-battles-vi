# Búsquedas críticas del bloque y verificación de índices

**HU-REN-003** (RNF-REN-003, issue #69) · CA-02 — *«el reporte técnico demuestra que el motor
está utilizando los índices predefinidos para resolver la búsqueda»*.

## 1 · Las tres búsquedas elegidas

La historia pide **al menos tres** búsquedas críticas y pone como ejemplos «ítems en el
mercado, perfiles de jugadores o gremios». **Esos tres ejemplos no son del bloque**: el
catálogo de productos y el inventario son de Contenido, y los perfiles de Cuentas. El
Project Charter los lista entre las exclusiones —se consumen, no se implementan—, así que
instrumentarlos aquí sería salirse del alcance.

Las tres elegidas son las búsquedas más frecuentes **que sí son del bloque** y que hoy
tienen tabla y datos en `develop`:

| # | Búsqueda | Servicio | Tabla | Frecuencia real |
|---|---|---|---|---|
| B-01 | Verificación de término prohibido | `moderacion-sanciones` | `terminos_prohibidos` | **Una por cada mensaje de chat y por cada comentario publicado.** Es la consulta más caliente del bloque |
| B-02 | Bandeja de notificaciones de un usuario | `notificaciones` | `notificaciones` | Una por apertura de la bandeja y por reconexión de sesión |
| B-03 | Qué avisos ya recibió una sesión | `notificaciones` | `notificacion_entregas` | Una por reconexión, para no reenviar lo ya entregado |

Cuando `salas-partidas` y `comentarios` lleguen a `develop` con sus tablas, el listado de
salas y el hilo de comentarios son las dos candidatas siguientes. No se adelantan aquí
porque su esquema todavía no existe en la rama de integración.

## 2 · Estado de los índices según el esquema en `develop`

| Búsqueda | Índice declarado | Lectura |
|---|---|---|
| B-02 | `idx_notificaciones_usuario (usuario_id, creada_en)` | Cubre el filtro y el orden. Correcto |
| B-03 | `uk_entrega (usuario_id, aviso_id, sesion_id)` | El único índice `UNIQUE` sirve de índice de búsqueda. Correcto |
| B-01 | `UNIQUE (termino)` | **Sospecha de escaneo secuencial — ver abajo** |

### 2.1 · B-01: el índice existe pero la consulta probablemente no lo usa

`TerminoProhibidoRepository` declara:

```java
boolean existsByTerminoIgnoreCase(String termino);
Optional<TerminoProhibido> findByTerminoIgnoreCase(String termino);
```

Spring Data traduce `IgnoreCase` a `upper(termino) = upper(?)`. El índice que crea la
restricción `UNIQUE (termino)` es un btree **sobre la columna**, no sobre `upper(termino)`:
PostgreSQL no puede usarlo para resolver una expresión, así que el plan esperado es
`Seq Scan`.

Con las decenas de términos que hay hoy no se nota. Con la lista negra poblada, y
ejecutándose **en cada mensaje de chat**, es exactamente el cuello de botella que esta
historia existe para prevenir.

**Esto es una sospecha razonada a partir del esquema, no un hecho verificado.** Se confirma
o se descarta ejecutando el paso 3. No se ha añadido ninguna migración a
`moderacion-sanciones` —que además es módulo de otro integrante— antes de tener el plan de
ejecución delante: eso sería cambiar el esquema de un compañero por una corazonada.

Si el plan confirma el `Seq Scan`, el arreglo es un índice funcional, y hay que acordarlo
con el dueño del módulo:

```sql
-- Propuesta, NO aplicada: requiere el visto bueno del dueño de moderacion-sanciones
CREATE INDEX IF NOT EXISTS idx_terminos_prohibidos_upper
    ON terminos_prohibidos (upper(termino));
```

## 3 · Cómo se obtiene la evidencia de CA-02

Contra la base de datos del servicio, ya poblada. **CP-01 pide miles de registros**: con una
tabla vacía el planificador elige `Seq Scan` aunque el índice sea perfecto, porque leer
cuatro filas enteras es más barato que abrir un índice. Un `EXPLAIN` sobre una tabla vacía
no demuestra nada.

```sql
-- Poblar para que el planificador tenga algo que decidir
INSERT INTO terminos_prohibidos (termino)
SELECT 'termino_' || g FROM generate_series(1, 100000) g;
ANALYZE terminos_prohibidos;

-- B-01
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM terminos_prohibidos WHERE upper(termino) = upper('Espada de Fuego');

-- B-02
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM notificaciones WHERE usuario_id = 'jugador-1' ORDER BY creada_en DESC LIMIT 20;

-- B-03
EXPLAIN (ANALYZE, BUFFERS)
SELECT 1 FROM notificacion_entregas
WHERE usuario_id = 'jugador-1' AND aviso_id = 'aviso-1' AND sesion_id = 'sesion-1';
```

Se busca `Index Scan` o `Index Only Scan` en el plan. `Seq Scan` sobre una tabla poblada es
el hallazgo que hay que corregir, no un resultado que se pueda presentar como aprobado.

La salida de los tres `EXPLAIN` es la evidencia que se adjunta al acta, junto con el informe
de `GET /api/v1/consultas/informe` filtrado por esas sentencias.

## 4 · Qué falta para cerrar CA-02

CA-02 **no está cumplido** y no se marca como tal:

- Hace falta un PostgreSQL desplegado con datos de volumen para ejecutar los `EXPLAIN`. Es
  evidencia de entorno, no de código, y la propia historia la declara como «evidencia a
  enlazar durante el Sprint».
- Si el plan de B-01 confirma el `Seq Scan`, la migración del índice funcional la tiene que
  aprobar y aplicar el dueño de `moderacion-sanciones`.

Lo que sí queda cubierto desde ahora es la **medición** (CA-01) y el **registro de consultas
lentas** (CA-03): cuando B-01 se ejecute contra datos reales, aparecerá sola en
`GET /api/v1/consultas/lentas` sin que nadie tenga que ir a buscarla.
