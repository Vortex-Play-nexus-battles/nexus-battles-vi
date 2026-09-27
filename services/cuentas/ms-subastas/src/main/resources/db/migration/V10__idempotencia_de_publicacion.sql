-- B8: la idempotencia de POST /subastas (Idempotency-Key) pasa de un mapa en
-- memoria a la base de datos del servicio.
--
-- En memoria se perdia con cada reinicio y no se compartia entre replicas: un
-- reintento del cliente despues de un despliegue volvia a publicar, y con ello
-- a debitar la comision y a pedir otro bloqueo de inventario. La auditoria
-- contractual lo marco como parcial.
--
-- clave:   uid del vendedor + ":" + la cabecera (la misma clave de dos
--          vendedores distintos son dos operaciones distintas).
-- huella:  SHA-256 de la solicitud; la misma clave con otra solicitud es 409.
-- titular: quien la adquirio; solo el titular la confirma o la libera.
-- estado:  EN_CURSO mientras se publica; CONFIRMADA con la respuesta
--          guardada para reproducirla; INCIERTA cuando no se sabe si el
--          debito o el commit llegaron (requiere conciliacion).
CREATE TABLE publicaciones_idempotentes (
    clave          VARCHAR(160) NOT NULL PRIMARY KEY,
    huella         VARCHAR(64)  NOT NULL,
    titular        UUID         NOT NULL,
    estado         VARCHAR(20)  NOT NULL,
    subasta_id     UUID,
    respuesta      TEXT,
    creada_en      TIMESTAMPTZ  NOT NULL,
    actualizada_en TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_publicaciones_idempotentes_estado
        CHECK (estado IN ('EN_CURSO', 'CONFIRMADA', 'INCIERTA'))
);
