-- B3 (BACKEND-05) — comunidad de producto, contrato comentarios 1.5.0.
--
-- Todo aditivo: tablas y columnas nuevas, datos copiados, nada borrado. V1..V4
-- ya estan aplicadas en el servidor de desarrollo y no se tocan (misma razon
-- que documenta V3: cambiarlas romperia su checksum).
--
-- ============================================================ calificaciones
-- 7.1 del documento del curso: «Los usuarios solo pueden calificar un producto
-- una vez, pero podran agregar o retirar tantos comentarios como sea de su
-- agrado». Hasta aqui la calificacion era la columna `estrellas` del
-- comentario, y eso producia tres defectos que la auditoria dejo escritos:
--   * no se podia calificar sin escribir un comentario;
--   * el promedio se calculaba trayendo a memoria el hilo entero en cada GET;
--   * un comentario ocultado o eliminado por moderacion conservaba sus
--     estrellas —y con ellas el indice unico de V2—, de modo que su autor ya
--     no podia volver a calificar y su calificacion tampoco contaba.
-- Desde aqui la calificacion es su propia fila, y esta tabla es la UNICA
-- fuente del promedio.
CREATE TABLE IF NOT EXISTS calificaciones (
    id           VARCHAR(36)  PRIMARY KEY,
    producto_id  VARCHAR(64)  NOT NULL,
    autor_id     VARCHAR(64)  NOT NULL,
    estrellas    INTEGER      NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
    creada_en    TIMESTAMPTZ  NOT NULL,
    -- «Una sola vez» lo decide la base, no el servicio: dos peticiones
    -- simultaneas del mismo jugador no se ven entre si, esta restriccion si.
    -- El servicio inserta con ON CONFLICT DO NOTHING sobre ella y traduce la
    -- fila no insertada a 409 (o a calificacionDescartada al comentar). Su
    -- indice, que empieza por producto_id, es tambien el que usa el resumen.
    CONSTRAINT uk_calificacion_por_producto_y_autor UNIQUE (producto_id, autor_id)
);

-- Las estrellas que ya existian pasan a ser la calificacion de su autor: una
-- por producto y autor, la MAS ANTIGUA con estrellas (el indice parcial de V2
-- ya impedia que hubiera dos, pero se elige de forma determinista por si
-- acaso). Se copian las de cualquier estado —tambien las de comentarios en
-- revision, ocultos o eliminados por moderacion—: la calificacion es la
-- opinion del jugador sobre el producto, no contenido que moderar, y era
-- justo el defecto que un comentario moderado se la llevara por delante. Los
-- que retiro su autor no aportan nada: HU-COM-004 ya les quito las estrellas.
--
-- La columna comentarios.estrellas se queda donde esta, con sus datos: nada
-- nuevo la escribe y nada la lee, pero borrarla no seria aditivo.
INSERT INTO calificaciones (id, producto_id, autor_id, estrellas, creada_en)
SELECT gen_random_uuid()::text,
       primera.producto_id,
       primera.autor_id,
       primera.estrellas,
       primera.fecha_publicacion
FROM (
    SELECT DISTINCT ON (producto_id, autor_id)
           producto_id, autor_id, estrellas, fecha_publicacion
    FROM comentarios
    WHERE estrellas IS NOT NULL
    ORDER BY producto_id, autor_id, fecha_publicacion ASC, id ASC
) AS primera
ON CONFLICT (producto_id, autor_id) DO NOTHING;

-- =================================================================== imagenes
-- 7.1: «Los comentarios permiten incluir texto e imagenes». Hasta aqui solo
-- se guardaba el NOMBRE del archivo (comentario_imagenes.nombre_archivo), sin
-- bytes detras. Ahora la imagen se sube aparte, se comprueba por su firma de
-- bytes y su cabecera, y se guarda aqui mismo, en PostgreSQL.
--
-- Por que en la base y no en un volumen del host: asi viaja con los respaldos
-- de la base (infrastructure/respaldo-dr) y no depende de un disco que
-- desaparece al recrear el contenedor o al mover el servicio de host. El
-- tamano esta acotado (2 MB por imagen, 3 por comentario) y las que nadie usa
-- se borran a las 24 h, asi que el crecimiento lo marca el uso real.
CREATE TABLE IF NOT EXISTS imagenes_de_comentario (
    id             VARCHAR(36)  PRIMARY KEY,
    autor_id       VARCHAR(64)  NOT NULL,
    tipo           VARCHAR(20)  NOT NULL
                   CHECK (tipo IN ('image/jpeg', 'image/png', 'image/webp')),
    tamano         INTEGER      NOT NULL CHECK (tamano > 0),
    sha256         VARCHAR(64)  NOT NULL,
    datos          BYTEA        NOT NULL,
    creada_en      TIMESTAMPTZ  NOT NULL,
    -- Nula mientras la imagen esta pendiente: subida pero sin comentario que
    -- la use. Solo su autor puede asociarla, y una sola vez.
    comentario_id  VARCHAR(36)  REFERENCES comentarios (id)
);

CREATE INDEX IF NOT EXISTS idx_imagenes_por_comentario
    ON imagenes_de_comentario (comentario_id);

-- La limpieza de las 24 h solo mira las pendientes, por antiguedad.
CREATE INDEX IF NOT EXISTS idx_imagenes_pendientes
    ON imagenes_de_comentario (creada_en)
    WHERE comentario_id IS NULL;

-- Los comentarios nuevos guardan en comentario_imagenes.nombre_archivo el id
-- de la imagen (36 caracteres, cabe en el VARCHAR(255) de V1). El nombre de
-- la columna es historico; renombrarla no aportaria nada y romperia lo que ya
-- esta desplegado.

-- ================================================================ comentarios
-- 7.3.3: «Editar: modificar contenido inapropiado manteniendo el contexto del
-- comentario (con registro de la edicion)» y «Marcar: senalar comentarios para
-- seguimiento especial». El texto anterior de una edicion va al asiento (abajo);
-- aqui solo queda que el comentario fue editado y si esta marcado.
ALTER TABLE comentarios
    ADD COLUMN IF NOT EXISTS editado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE comentarios
    ADD COLUMN IF NOT EXISTS marcado BOOLEAN NOT NULL DEFAULT FALSE;

-- El hilo se lee paginado, del mas reciente al mas antiguo y luego por id
-- (orden estable entre paginas). Sin este indice cada pagina ordenaria todo
-- el producto.
CREATE INDEX IF NOT EXISTS idx_comentarios_hilo
    ON comentarios (producto_id, estado, fecha_publicacion DESC, id DESC);

-- La lista de seguimiento (cola con marcado=true) solo mira los marcados.
CREATE INDEX IF NOT EXISTS idx_comentarios_marcados
    ON comentarios (fecha_publicacion)
    WHERE marcado;

-- ===================================================== asientos de moderacion
-- 7.3.3: cada accion registra usuario, fecha, motivo, estado anterior y nuevo
-- (ya estaban desde V4). EDITAR guarda ademas el texto anterior y el nuevo, y
-- toda accion guarda la IP de origen de la peticion. Nulas en los asientos
-- anteriores a esta version: no se inventa lo que no se registro.
ALTER TABLE comentario_moderacion
    ADD COLUMN IF NOT EXISTS texto_anterior TEXT;

ALTER TABLE comentario_moderacion
    ADD COLUMN IF NOT EXISTS texto_nuevo TEXT;

-- 45 caracteres: la forma textual mas larga de una IPv6 (con IPv4 embebida).
ALTER TABLE comentario_moderacion
    ADD COLUMN IF NOT EXISTS ip_origen VARCHAR(45);
