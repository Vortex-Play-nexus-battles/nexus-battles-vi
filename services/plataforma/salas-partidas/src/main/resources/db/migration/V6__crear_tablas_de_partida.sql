-- P2.3 · RF-JUE-017 — Estado del combate.
--
-- Una sala tiene como mucho UNA partida: la restriccion unica sobre id_sala es
-- lo que lo garantiza de verdad. La comprobacion del caso de uso evita el error
-- comun; esta linea evita la carrera entre dos peticiones simultaneas del mismo
-- anfitrion, que la comprobacion sola no puede ver.
CREATE TABLE partidas (
    id                  uuid        PRIMARY KEY,
    id_sala             uuid        NOT NULL UNIQUE,
    estado              varchar(20) NOT NULL,
    turno_id_jugador    uuid        NOT NULL,
    turno_numero        integer     NOT NULL,
    recompensa_en_juego integer     NOT NULL DEFAULT 0,
    iniciada_en         timestamptz NOT NULL,

    CONSTRAINT ck_partidas_estado
        CHECK (estado IN ('EN_CURSO', 'FINALIZADA')),
    CONSTRAINT ck_partidas_turno_positivo
        CHECK (turno_numero >= 1),
    CONSTRAINT ck_partidas_recompensa_no_negativa
        CHECK (recompensa_en_juego >= 0),
    CONSTRAINT fk_partidas_sala
        FOREIGN KEY (id_sala) REFERENCES salas (id)
);

-- Participantes del combate, en el ORDEN DE LOS TURNOS.
--
-- El orden importa y por eso se persiste en una columna propia: una tabla no
-- garantiza el orden de lectura, y si el turno rotara distinto tras un reinicio
-- la partida recuperada no seria la misma partida.
--
-- heroe_* es anulable: mientras la verificacion de HU-SAL-003 no sea obligatoria
-- en el ingreso, hay participantes cuyo heroe este servicio no conoce. Guardar
-- null es decir la verdad; inventar una vida para rellenar la barra, no.
CREATE TABLE partida_participantes (
    id_partida          uuid        NOT NULL REFERENCES partidas (id) ON DELETE CASCADE,
    orden               integer     NOT NULL,
    id_jugador          uuid        NOT NULL,
    es_ia               boolean     NOT NULL DEFAULT false,
    equipo              integer,
    creditos_apostados  integer     NOT NULL DEFAULT 0,
    heroe_id            varchar(100),
    heroe_nombre        varchar(120),
    heroe_retrato_url   varchar(500),
    heroe_nivel         integer,
    heroe_vida_actual   integer,
    heroe_vida_maxima   integer,

    PRIMARY KEY (id_partida, orden),
    CONSTRAINT ck_participantes_creditos_no_negativos
        CHECK (creditos_apostados >= 0),
    -- O el heroe esta completo, o no esta. Media ficha de heroe no la sabe
    -- pintar ninguna vista.
    CONSTRAINT ck_participantes_heroe_completo
        CHECK (
            (heroe_id IS NULL AND heroe_nombre IS NULL
                AND heroe_vida_actual IS NULL AND heroe_vida_maxima IS NULL)
            OR (heroe_id IS NOT NULL AND heroe_nombre IS NOT NULL
                AND heroe_vida_actual IS NOT NULL AND heroe_vida_maxima IS NOT NULL)
        )
);

-- La vista de combate y la reconexion siempre entran por la partida.
CREATE INDEX ix_partida_participantes_partida ON partida_participantes (id_partida);
