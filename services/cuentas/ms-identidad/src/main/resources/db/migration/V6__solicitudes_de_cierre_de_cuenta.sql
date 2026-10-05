-- RFINAL-05 — derecho al olvido (HU-PRV-005, RF-PRV-005, RN-USR-011, 7.3.6).
--
-- Migracion ADITIVA (regla 8): una tabla nueva y sus indices; no toca datos.
-- V5 queda reservada al segundo factor (HU-AUT-007), que se integra aparte.
--
-- Una fila por solicitud de cierre de cuenta. La persona la pide desde «Mi
-- cuenta» con su contrasena; se programa a 30 dias y hasta entonces la puede
-- cancelar. Al vencer, la tarea programada de ms-identidad anonimiza la cuenta
-- (AnonimizadorDeCuentas) y marca la fila EJECUTADA. Las filas no se borran:
-- son la constancia de que la eliminacion se pidio y se cumplio, y no guardan
-- ningun dato personal (solo el uid, que tras la anonimizacion ya no nombra a
-- nadie). Las horas son las del servidor, como el resto de este esquema.
CREATE TABLE solicitudes_cierre_cuenta (
    id              UUID         NOT NULL,
    usuario_uid     UUID         NOT NULL,
    estado          VARCHAR(16)  NOT NULL,
    solicitada_en   TIMESTAMP(6) NOT NULL,
    programada_para TIMESTAMP(6) NOT NULL,
    cancelada_en    TIMESTAMP(6),
    ejecutada_en    TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_cierre_usuario FOREIGN KEY (usuario_uid)
        REFERENCES usuarios (public_id) ON DELETE CASCADE,
    CONSTRAINT ck_cierre_estado
        CHECK (estado IN ('PROGRAMADA', 'CANCELADA', 'EJECUTADA')),
    -- El plazo es del requisito (RN-USR-011: treinta dias), no un ajuste: la
    -- base tampoco acepta otro.
    CONSTRAINT ck_cierre_plazo
        CHECK (programada_para = solicitada_en + INTERVAL '30 days')
);

-- Como mucho UN cierre programado por cuenta. Dos solicitudes simultaneas no
-- pueden programar dos cierres: la segunda choca aqui y el servicio devuelve
-- el que ya existe.
CREATE UNIQUE INDEX ux_cierre_programado_por_cuenta
    ON solicitudes_cierre_cuenta (usuario_uid)
    WHERE estado = 'PROGRAMADA';

-- La tarea programada busca las PROGRAMADA ya vencidas, por fecha.
CREATE INDEX ix_cierre_vencimiento ON solicitudes_cierre_cuenta (estado, programada_para);
