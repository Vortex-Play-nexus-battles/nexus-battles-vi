-- HU-SUB-001 (publicar) / HU-SUB-011 (listar) / HU-SUB-004 (pujar)
-- Esquema BORRADOR pendiente del diseno conjunto del Dia 1 del Sprint 2
-- entre Edwin, Cristian y Andres. Los campos de subastas los gobierna
-- HU-SUB-001; aqui se crean los minimos que necesita el motor de pujas.

CREATE TABLE subastas (
    id                        UUID            NOT NULL PRIMARY KEY,
    producto_id               UUID            NOT NULL,
    vendedor_id               UUID            NOT NULL,
    oferta_vigente            NUMERIC(19, 2)  NOT NULL,
    incremento_minimo         NUMERIC(19, 2)  NOT NULL,
    precio_compra_inmediata   NUMERIC(19, 2),
    mejor_postor_id           UUID,
    estado                    VARCHAR(20)     NOT NULL,
    fecha_fin                 TIMESTAMPTZ     NOT NULL,
    version                   BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT chk_subastas_estado
        CHECK (estado IN ('ACTIVA', 'ADJUDICADA', 'SIN_ADJUDICACION')),
    -- RF-SUB-004: el incremento minimo debe ser positivo. Su valor por
    -- defecto sigue POR DEFINIR con el cliente, asi que no se fija aqui.
    CONSTRAINT chk_subastas_incremento_positivo
        CHECK (incremento_minimo > 0)
);

-- HU-SUB-011 lista subastas activas ordenadas por tiempo restante.
CREATE INDEX idx_subastas_estado_fecha_fin ON subastas (estado, fecha_fin);

CREATE TABLE pujas (
    id                  UUID            NOT NULL PRIMARY KEY,
    subasta_id          UUID            NOT NULL REFERENCES subastas (id),
    jugador_id          UUID            NOT NULL,
    monto               NUMERIC(19, 2)  NOT NULL,
    tipo                VARCHAR(20)     NOT NULL,
    estado              VARCHAR(20)     NOT NULL,
    creada_en           TIMESTAMPTZ     NOT NULL,
    reserva_credito_id  VARCHAR(36),

    CONSTRAINT chk_pujas_tipo   CHECK (tipo IN ('MANUAL', 'AUTOMATICA')),
    CONSTRAINT chk_pujas_estado CHECK (estado IN ('ACTIVA', 'SUPERADA', 'GANADORA', 'RECHAZADA')),
    CONSTRAINT chk_pujas_monto_positivo CHECK (monto > 0)
);

-- Invariante que la validacion en Java NO puede garantizar bajo concurrencia:
-- una subasta tiene como maximo UNA puja vigente. Si dos transacciones
-- lograran insertar dos pujas ACTIVA sobre la misma subasta, esta constraint
-- aborta la segunda en vez de dejar la subasta con dos ofertas vigentes.
CREATE UNIQUE INDEX uq_pujas_una_vigente_por_subasta
    ON pujas (subasta_id)
    WHERE estado = 'ACTIVA';

-- Intervalo minimo de 5 s entre pujas del mismo jugador: se resuelve
-- buscando su ultima puja por fecha descendente.
CREATE INDEX idx_pujas_jugador_creada_en ON pujas (jugador_id, creada_en DESC);

-- Topes de 50 pujas activas y 10 subastas activas simultaneas por jugador.
CREATE INDEX idx_pujas_jugador_activas
    ON pujas (jugador_id, subasta_id)
    WHERE estado = 'ACTIVA';

CREATE TABLE pujas_automaticas (
    id          UUID            NOT NULL PRIMARY KEY,
    subasta_id  UUID            NOT NULL REFERENCES subastas (id),
    jugador_id  UUID            NOT NULL,
    limite      NUMERIC(19, 2)  NOT NULL,
    activa      BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_pujas_automaticas_jugador_subasta UNIQUE (subasta_id, jugador_id),
    CONSTRAINT chk_pujas_automaticas_limite_positivo CHECK (limite > 0)
);

CREATE INDEX idx_pujas_automaticas_subasta_activa
    ON pujas_automaticas (subasta_id)
    WHERE activa;
