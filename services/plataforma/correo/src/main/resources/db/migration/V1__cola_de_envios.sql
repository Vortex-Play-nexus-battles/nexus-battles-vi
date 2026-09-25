-- B1 — correo durable (contracts/openapi/correo.yaml 1.4.0).
--
-- Hasta aqui el servicio aceptaba cada peticion con 202 y la entregaba desde
-- memoria: un fallo del SMTP se descartaba y un reinicio perdia lo aceptado.
-- Desde ahora un 202 significa «guardado en esta tabla», y un trabajador lo
-- entrega con reintentos (TrabajadorDeEntrega).
--
-- Esquema propio del servicio dentro de la instancia compartida (regla 7),
-- igual que comentarios, torneos o metricas.

CREATE SCHEMA IF NOT EXISTS correo;

CREATE TABLE correo.envios (
    id               UUID         PRIMARY KEY,
    -- Nombre corto de la plantilla (bienvenida, recuperacion-clave, sancion...).
    plantilla        VARCHAR(64)  NOT NULL,
    destinatario     VARCHAR(320) NOT NULL,
    asunto           TEXT         NOT NULL,
    -- Variables de la plantilla. Los codigos de un solo uso se borran de aqui
    -- en cuanto el envio termina (Plantilla#sinDatosSensibles).
    datos            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    estado           VARCHAR(24)  NOT NULL,
    -- Intentos EMPEZADOS: se suma al reclamar la fila, no al terminar, para
    -- que una caida a mitad de envio tambien cuente.
    intentos         INTEGER      NOT NULL DEFAULT 0,
    proximo_intento  TIMESTAMPTZ  NOT NULL,
    -- Resumen corto y saneado del ultimo fallo, o el motivo de una omision.
    -- Nunca el cuerpo del correo ni credenciales.
    ultimo_error     VARCHAR(300) NULL,
    -- Cabecera Idempotency-Key del productor: la misma clave no encola dos
    -- correos. UNIQUE admite varios NULL, que es lo que se quiere.
    idempotency_key  VARCHAR(120) NULL,
    destino          VARCHAR(24)  NULL,
    -- Message-ID que puso el servidor al aceptar el mensaje.
    identificador    TEXT         NULL,
    trace_id         VARCHAR(64)  NULL,
    creado_en        TIMESTAMPTZ  NOT NULL,
    actualizado_en   TIMESTAMPTZ  NOT NULL,
    enviado_en       TIMESTAMPTZ  NULL,
    CONSTRAINT envios_idempotency_key_unica UNIQUE (idempotency_key),
    CONSTRAINT envios_estado_valido CHECK (estado IN (
        'PENDIENTE', 'ENVIANDO', 'ENVIADO', 'ERROR_REINTENTABLE', 'FALLIDO', 'DESVIADO', 'OMITIDO')),
    CONSTRAINT envios_destino_valido CHECK (destino IS NULL OR destino IN ('PROVEEDOR', 'BUZON_DE_PRUEBAS')),
    CONSTRAINT envios_intentos_no_negativos CHECK (intentos >= 0)
);

-- El trabajador busca por estado y vencimiento (PENDIENTE/ERROR_REINTENTABLE
-- con proximo_intento vencido) y los atascados por estado ENVIANDO.
CREATE INDEX envios_por_entregar ON correo.envios (estado, proximo_intento);

-- La evidencia de entrega (GET /correos/envios) lista por ultimo cambio, y la
-- purga de retencion borra por esa misma fecha.
CREATE INDEX envios_por_actualizacion ON correo.envios (actualizado_en DESC);
