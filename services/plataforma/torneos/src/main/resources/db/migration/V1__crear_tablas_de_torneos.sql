-- Torneos (RF-TOR-001), equipos (RF-TOR-003/002/005) y encuentros del arbol
-- (RF-TOR-004). Esquema propio `torneos` en el PostgreSQL de la plataforma.

CREATE TABLE torneos (
    id                       UUID PRIMARY KEY,
    nombre                   VARCHAR(80)  NOT NULL,
    estado                   VARCHAR(30)  NOT NULL,
    creado_por               UUID         NOT NULL,
    creado_en                TIMESTAMPTZ  NOT NULL,
    inscripciones_cierran_en TIMESTAMPTZ  NOT NULL,
    costo_inscripcion        INTEGER      NOT NULL DEFAULT 0 CHECK (costo_inscripcion >= 0),
    iniciado_en              TIMESTAMPTZ,
    finalizado_en            TIMESTAMPTZ,
    campeon_equipo_id        UUID,
    motivo_cancelacion       VARCHAR(500)
);

CREATE INDEX torneos_creado_en_idx ON torneos (creado_en DESC);

CREATE TABLE equipos (
    id           UUID PRIMARY KEY,
    torneo_id    UUID         NOT NULL REFERENCES torneos (id),
    nombre       VARCHAR(40)  NOT NULL,
    avatar       VARCHAR(300) NOT NULL,
    ia           BOOLEAN      NOT NULL DEFAULT FALSE,
    capitan_uid  UUID,
    integrante_1 UUID,
    integrante_2 UUID,
    inscrito     BOOLEAN      NOT NULL DEFAULT FALSE,
    pagado_por   UUID,
    reserva_id   UUID,
    posicion     INTEGER      CHECK (posicion BETWEEN 1 AND 8),
    derrotas     INTEGER      NOT NULL DEFAULT 0,
    eliminado    BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT equipos_posicion_unica UNIQUE (torneo_id, posicion)
);

CREATE INDEX equipos_torneo_idx ON equipos (torneo_id);

CREATE TABLE encuentros (
    id             UUID PRIMARY KEY,
    torneo_id      UUID        NOT NULL REFERENCES torneos (id),
    numero         INTEGER     NOT NULL CHECK (numero BETWEEN 1 AND 14),
    llave          VARCHAR(20) NOT NULL,
    ronda          INTEGER     NOT NULL,
    equipo_a       UUID,
    equipo_b       UUID,
    ganador        UUID,
    partida_id     UUID,
    estado         VARCHAR(20) NOT NULL,
    registrado_por VARCHAR(100),
    motivo         VARCHAR(500),
    jugado_en      TIMESTAMPTZ,
    CONSTRAINT encuentros_numero_unico UNIQUE (torneo_id, numero)
);
