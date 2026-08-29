CREATE TABLE IF NOT EXISTS terminos_prohibidos (
    id BIGSERIAL PRIMARY KEY,
    termino VARCHAR(255) NOT NULL UNIQUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);
