-- RFINAL-05 — HU-AUT-007 (RF-AUT-007): segundo factor TOTP para las cuentas
-- (obligatorio, por configuracion, para los roles administrativos).
--
-- Migracion ADITIVA (regla 8): tres tablas nuevas, ninguna columna tocada y
-- ningun dato reescrito. Ninguna cuenta existente pasa a tener segundo
-- factor: lo activa cada persona desde «Mi cuenta > Seguridad».
--
-- V6 queda reservada para el paquete de privacidad (RFINAL-05).

-- ---------------------------------------------------------------------------
-- 1. El segundo factor de cada cuenta: una fila por cuenta.
--
-- `secreto_cifrado` es el secreto TOTP cifrado con AES-GCM («v1:» + base64 de
-- IV + cifrado + etiqueta), con la clave de IDENTIDAD_2FA_CLAVE, que NO vive en
-- la base: una copia de esta tabla no basta para generar los codigos de nadie.
-- `activo` = false mientras el enrolamiento esta pendiente de confirmar con un
-- codigo de la aplicacion; solo con true lo pide el login.
-- `ultimo_paso_usado` es el ultimo paso TOTP aceptado (contador de 30 s): un
-- codigo de ese paso o de uno anterior ya no vuelve a valer.
CREATE TABLE segundo_factor (
    usuario_id        BIGINT       NOT NULL,
    secreto_cifrado   VARCHAR(255) NOT NULL,
    activo            BOOLEAN      NOT NULL,
    creado_en         TIMESTAMP(6) NOT NULL,
    activado_en       TIMESTAMP(6),
    ultimo_paso_usado BIGINT,
    PRIMARY KEY (usuario_id),
    CONSTRAINT fk_segundo_factor_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- 2. Codigos de recuperacion: su RESUMEN BCrypt, nunca el valor, y cuando se
-- uso cada uno (un solo uso: el canje es un UPDATE condicionado a usado_en
-- IS NULL).
CREATE TABLE codigos_recuperacion (
    id          UUID         NOT NULL,
    usuario_id  BIGINT       NOT NULL,
    codigo_hash VARCHAR(100) NOT NULL,
    creado_en   TIMESTAMP(6) NOT NULL,
    usado_en    TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_codigos_recuperacion_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE
);

-- El canje busca los codigos sin usar de una cuenta.
CREATE INDEX ix_codigos_recuperacion_usuario ON codigos_recuperacion (usuario_id);

-- ---------------------------------------------------------------------------
-- 3. Desafios del login en dos pasos: el valor es aleatorio (256 bits) y aqui
-- solo queda su SHA-256 (`token_hash`). `proposito` VERIFICAR (falta el codigo)
-- o ENROLAR (el rol exige segundo factor y la cuenta no lo tiene).
-- `version_token` es la de la cuenta al emitirlo: si cambia (cambio de
-- contrasena o de rol entre los dos pasos) el desafio deja de valer.
CREATE TABLE desafios_acceso (
    id            UUID         NOT NULL,
    usuario_id    BIGINT       NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    proposito     VARCHAR(16)  NOT NULL,
    version_token INTEGER      NOT NULL,
    creado_en     TIMESTAMP(6) NOT NULL,
    expira_en     TIMESTAMP(6) NOT NULL,
    usado_en      TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_desafios_acceso_token UNIQUE (token_hash),
    CONSTRAINT fk_desafios_acceso_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_desafios_acceso_proposito CHECK (proposito IN ('VERIFICAR', 'ENROLAR'))
);

-- La limpieza al emitir uno nuevo borra los gastados de la cuenta.
CREATE INDEX ix_desafios_acceso_usuario ON desafios_acceso (usuario_id);
