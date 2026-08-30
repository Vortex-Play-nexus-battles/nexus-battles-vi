-- HU-SAL-001 · tabla de salas de batalla.
--
-- Regla 8 de plataforma: todo cambio de esquema por Flyway, versionado, nunca a
-- mano. Este archivo no se edita una vez fusionado; los cambios van en un V2.
--
-- El esquema salas_partidas lo crea Flyway a partir de spring.flyway.schemas.
-- Regla 7: ningun otro servicio lee ni escribe aqui.

CREATE TABLE IF NOT EXISTS salas (
    id                    UUID         PRIMARY KEY,
    nombre                VARCHAR(60)  NOT NULL,
    estado                VARCHAR(20)  NOT NULL,
    modalidad             VARCHAR(20)  NOT NULL,
    maximo_participantes  SMALLINT     NOT NULL,
    recompensa_creditos   INTEGER      NOT NULL,
    incluir_heroe_ia      BOOLEAN      NOT NULL,
    privada               BOOLEAN      NOT NULL,
    tamano_equipo         SMALLINT,
    id_anfitrion          UUID         NOT NULL,
    ocupacion             SMALLINT     NOT NULL,
    creada_en             TIMESTAMPTZ  NOT NULL,

    -- Las reglas del juego se validan en el dominio. Se repiten aqui como
    -- ultima linea: si algun dia otro camino escribe en esta tabla saltandose
    -- Sala.crear, la base de datos lo impide igual.

    -- RF-JUE-004: de dos a seis participantes.
    CONSTRAINT salas_participantes_en_rango
        CHECK (maximo_participantes BETWEEN 2 AND 6),

    -- RF-JUE-004: equipos de un maximo de tres integrantes.
    CONSTRAINT salas_equipo_en_rango
        CHECK (tamano_equipo IS NULL OR tamano_equipo BETWEEN 1 AND 3),

    -- RF-JUE-014: apostar es libre, incluso cero. Deber creditos, no.
    CONSTRAINT salas_recompensa_no_negativa
        CHECK (recompensa_creditos >= 0),

    -- Nunca puede haber dentro mas gente que el maximo declarado.
    CONSTRAINT salas_ocupacion_coherente
        CHECK (ocupacion >= 0 AND ocupacion <= maximo_participantes),

    CONSTRAINT salas_nombre_con_longitud_minima
        CHECK (char_length(nombre) >= 3)
);

COMMENT ON TABLE  salas IS 'Salas de batalla. HU-SAL-001, RF-JUE-001/004/014.';
COMMENT ON COLUMN salas.ocupacion IS 'Participantes dentro ahora mismo; al crearla, solo el anfitrion.';
COMMENT ON COLUMN salas.tamano_equipo IS 'Solo aplica en modalidad HASTA_SEIS; nulo en el resto.';
