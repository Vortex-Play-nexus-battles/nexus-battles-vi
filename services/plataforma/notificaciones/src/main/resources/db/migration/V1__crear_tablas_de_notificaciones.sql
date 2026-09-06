CREATE TABLE IF NOT EXISTS notificaciones (
    fila_id BIGSERIAL PRIMARY KEY,
    usuario_id VARCHAR(64) NOT NULL,
    aviso_id VARCHAR(64) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    cuerpo TEXT NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_aviso_por_usuario UNIQUE (usuario_id, aviso_id)
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_usuario
    ON notificaciones (usuario_id, creada_en);

-- Que sesion ya recibio que aviso. Sin esta tabla no se cumple el escenario de
-- la reconexion, porque al volver hay que entregarle a la sesion unicamente lo
-- que se perdio y no todo lo que tiene sin leer.
CREATE TABLE IF NOT EXISTS notificacion_entregas (
    fila_id BIGSERIAL PRIMARY KEY,
    usuario_id VARCHAR(64) NOT NULL,
    aviso_id VARCHAR(64) NOT NULL,
    sesion_id VARCHAR(128) NOT NULL,
    CONSTRAINT uk_entrega UNIQUE (usuario_id, aviso_id, sesion_id)
);

CREATE INDEX IF NOT EXISTS idx_entregas_usuario
    ON notificacion_entregas (usuario_id);

-- Sesiones abiertas del jugador. El identificador es el estable que envia el
-- cliente, no el de STOMP, que se renueva en cada reconexion.
CREATE TABLE IF NOT EXISTS sesiones_abiertas (
    fila_id BIGSERIAL PRIMARY KEY,
    usuario_id VARCHAR(64) NOT NULL,
    sesion_id VARCHAR(128) NOT NULL,
    abierta_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_sesion_por_usuario UNIQUE (usuario_id, sesion_id)
);

CREATE INDEX IF NOT EXISTS idx_sesiones_usuario
    ON sesiones_abiertas (usuario_id);
