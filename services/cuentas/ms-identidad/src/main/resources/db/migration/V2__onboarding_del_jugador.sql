-- R17.1 — Estado de preparacion (onboarding) de cada jugador nuevo.
--
-- El registro crea la identidad y el perfil aqui mismo, pero el resto del
-- estado inicial vive en otros servicios (creditos en ms-finanzas, heroe y
-- equipo en inventario). Ninguna transaccion abarca cuatro bases, asi que el
-- alta se guarda como una lista de pasos con su estado: cada paso se ejecuta
-- contra el servicio dueno de ese dato, con una clave idempotente, y se marca
-- HECHO solo cuando ese servicio respondio que si. Un paso que falla se
-- reintenta despues sin repetir los que ya estan hechos.
--
-- Una fila por jugador (la cabecera) y una por paso. La clave es el uid
-- publico (usuarios.public_id, ADR-002): es el mismo identificador con el que
-- los otros servicios guardan al jugador.

-- La clave ajena necesita que public_id sea unico. En una base nueva lo es por
-- V1. En una base heredada (creada por Hibernate con ddl-auto=update, donde la
-- columna se anadio despues) la restriccion la ponia Hibernate «si podia», con
-- un nombre generado: se comprueba que haya un indice unico sobre esa columna
-- sola y, si no, se crea. Asi esta migracion no depende de lo que Hibernate
-- hiciera o dejara de hacer en cada entorno.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_index i
          JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
         WHERE i.indrelid = 'usuarios'::regclass
           AND i.indisunique
           AND i.indnatts = 1
           AND i.indpred IS NULL
           AND a.attname = 'public_id'
    ) THEN
        ALTER TABLE usuarios ADD CONSTRAINT uk_usuarios_public_id UNIQUE (public_id);
    END IF;
END $$;

CREATE TABLE onboarding_jugador (
    usuario_uid       UUID         NOT NULL,
    version_bootstrap INTEGER      NOT NULL,
    estado            VARCHAR(24)  NOT NULL,
    intentos          INTEGER      NOT NULL DEFAULT 0,
    ultimo_error      VARCHAR(500),
    siguiente_intento TIMESTAMP(6),
    en_proceso_hasta  TIMESTAMP(6),
    traza             VARCHAR(32),
    creado_en         TIMESTAMP(6) NOT NULL,
    actualizado_en    TIMESTAMP(6) NOT NULL,
    completado_en     TIMESTAMP(6),
    PRIMARY KEY (usuario_uid),
    CONSTRAINT fk_onboarding_usuario FOREIGN KEY (usuario_uid)
        REFERENCES usuarios (public_id) ON DELETE CASCADE,
    CONSTRAINT ck_onboarding_estado
        CHECK (estado IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETO', 'ERROR_REINTENTABLE'))
);

-- El reintento programado busca por estado y hora del siguiente intento.
CREATE INDEX ix_onboarding_por_reintentar ON onboarding_jugador (estado, siguiente_intento);

CREATE TABLE onboarding_paso (
    usuario_uid    UUID         NOT NULL,
    paso           VARCHAR(24)  NOT NULL,
    estado         VARCHAR(16)  NOT NULL,
    detalle        VARCHAR(500),
    intentos       INTEGER      NOT NULL DEFAULT 0,
    ultimo_error   VARCHAR(500),
    actualizado_en TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (usuario_uid, paso),
    CONSTRAINT fk_paso_onboarding FOREIGN KEY (usuario_uid)
        REFERENCES onboarding_jugador (usuario_uid) ON DELETE CASCADE,
    CONSTRAINT ck_paso_nombre CHECK (paso IN ('PERFIL', 'CREDITOS', 'HEROE', 'EQUIPO')),
    CONSTRAINT ck_paso_estado CHECK (estado IN ('PENDIENTE', 'HECHO', 'ERROR'))
);
