CREATE TABLE IF NOT EXISTS comentarios (
    id VARCHAR(36) PRIMARY KEY,
    producto_id VARCHAR(64) NOT NULL,
    autor_id VARCHAR(64) NOT NULL,
    apodo_autor VARCHAR(100) NOT NULL,
    texto TEXT NOT NULL,
    estrellas SMALLINT CHECK (estrellas BETWEEN 1 AND 5),
    fecha_publicacion TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comentarios_producto ON comentarios (producto_id);

CREATE TABLE IF NOT EXISTS comentario_imagenes (
    comentario_id VARCHAR(36) NOT NULL REFERENCES comentarios (id),
    orden SMALLINT NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    PRIMARY KEY (comentario_id, orden)
);

-- El largo maximo del texto sigue pendiente de definir con el Product Owner
-- (issue 34), por eso la columna es TEXT sin restriccion todavia. Cuando se
-- decida, el limite entra por una migracion nueva, nunca editando esta.
