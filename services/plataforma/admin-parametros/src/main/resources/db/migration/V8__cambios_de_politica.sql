-- SA-11 (§7.3.7, RF-NOT-005): los cambios en las politicas de uso que la
-- administracion publica para avisar a los jugadores. Cada fila es el cambio
-- y, a la vez, su asiento de auditoria pendiente (§7.3.5): el envio a la
-- bitacora central la marca con auditado_en, con los mismos reintentos que
-- las versiones de parametros (V7).
CREATE TABLE cambios_de_politica (
    id                           UUID PRIMARY KEY,
    politica                     VARCHAR(40)  NOT NULL,
    titulo                       VARCHAR(120) NOT NULL,
    descripcion                  VARCHAR(2000) NOT NULL,
    motivo                       VARCHAR(500) NOT NULL,
    vigente_desde                TIMESTAMPTZ  NOT NULL,
    publicado_por                UUID         NOT NULL,
    publicado_en                 TIMESTAMPTZ  NOT NULL,
    ip_origen                    VARCHAR(45),
    auditado_en                  TIMESTAMPTZ,
    intentos_de_auditoria        INTEGER      NOT NULL DEFAULT 0,
    error_de_auditoria           VARCHAR(300),
    proximo_intento_de_auditoria TIMESTAMPTZ,
    CONSTRAINT cambios_de_politica_politica_ck
        CHECK (politica IN ('TERMINOS_Y_CONDICIONES', 'POLITICA_DE_PRIVACIDAD'))
);

-- La bandeja de notificaciones lee los posteriores a su cursor, en orden.
CREATE INDEX cambios_de_politica_publicado_idx ON cambios_de_politica (publicado_en, id);
CREATE INDEX cambios_de_politica_por_auditar_idx ON cambios_de_politica (publicado_en) WHERE auditado_en IS NULL;
