-- HU-PAG-002 · Registro de transacciones en moneda real.
--
-- El esquema "finanzas" lo crea Flyway al arrancar por
-- spring.flyway.create-schemas=true y default-schema=finanzas. Este script
-- corre YA dentro del search_path del esquema, así que no repetimos el
-- prefijo aquí — si el schema cambia de nombre en el futuro no hay que tocar
-- las migraciones, solo el application.properties.

CREATE TABLE transacciones (
    id                    UUID PRIMARY KEY,
    ref_id                VARCHAR(128) NOT NULL UNIQUE,
    uid_usuario           VARCHAR(64)  NOT NULL,
    monto                 NUMERIC(15,2) NOT NULL CHECK (monto >= 0),
    moneda                CHAR(3)      NOT NULL,
    concepto              VARCHAR(128) NOT NULL,
    resultado             VARCHAR(32)  NOT NULL,
    comprobante_url       VARCHAR(512),
    pasarela_ref_externa  VARCHAR(128),
    creado                TIMESTAMPTZ  NOT NULL,
    actualizado           TIMESTAMPTZ  NOT NULL
);

-- El historial se consulta siempre por usuario, ordenado de la transacción
-- más reciente hacia atrás. Este índice sostiene el "GET /transacciones/mi-historial"
-- que se agrega en el PR siguiente.
CREATE INDEX idx_transacciones_uid_creado_desc
    ON transacciones (uid_usuario, creado DESC);

-- La conciliación de estados indeterminados busca por la referencia que
-- devuelve la pasarela. Índice parcial porque el 100 % de las transacciones
-- APROBADAS/RECHAZADAS lo tienen, pero las INDETERMINADAS pueden aparecer
-- sin él y no queremos entradas NULL contaminando el índice.
CREATE INDEX idx_transacciones_pasarela_ref_externa
    ON transacciones (pasarela_ref_externa)
    WHERE pasarela_ref_externa IS NOT NULL;
