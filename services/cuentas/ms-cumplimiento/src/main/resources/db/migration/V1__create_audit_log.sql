-- HU-AUD-001 / HU-AUD-002
-- Tabla de auditoría + barrera a nivel de base de datos que garantiza
-- append-only real (PostgreSQL 16).

-- Necesaria para que el registro de incidentes sobreviva al rollback
-- que provoca el propio trigger de bloqueo (ver explicación abajo).
CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TABLE audit_log (
                         id               VARCHAR(36)      NOT NULL PRIMARY KEY,
                         fecha_hora       TIMESTAMPTZ      NOT NULL,
                         administrador_id VARCHAR(64)      NOT NULL,
                         tipo_accion      VARCHAR(40)      NOT NULL,
                         afectado         VARCHAR(255)     NOT NULL,
                         valor_anterior   TEXT,
                         valor_nuevo      TEXT,
                         motivo           VARCHAR(500)     NOT NULL,
                         ip_origen        VARCHAR(45)      NOT NULL
);

CREATE INDEX idx_audit_log_administrador ON audit_log (administrador_id);
CREATE INDEX idx_audit_log_tipo_accion   ON audit_log (tipo_accion);
CREATE INDEX idx_audit_log_fecha_hora    ON audit_log (fecha_hora);

-- Tabla de incidentes de seguridad: aquí caen los intentos bloqueados
-- de UPDATE/DELETE.
CREATE TABLE audit_log_tampering_incident (
                                            id            BIGSERIAL PRIMARY KEY,
                                            detectado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                            operacion     VARCHAR(10) NOT NULL,
                                            audit_log_id  VARCHAR(36),
                                            usuario_bd    NAME        NOT NULL DEFAULT current_user
);

-- IMPORTANTE: el INSERT de abajo se hace por una conexión dblink
-- separada (transacción autónoma), NO en la transacción actual.
-- Motivo: esta función termina con RAISE EXCEPTION, lo que hace
-- rollback de TODA la transacción que la disparó — si insertáramos
-- el incidente directamente aquí, ese INSERT también se revertiría
-- y la tabla audit_log_tampering_incident quedaría siempre vacía.
-- Usando dblink, esa conexión hace su propio COMMIT antes de que
-- la transacción principal aborte, así que el incidente sobrevive.
CREATE OR REPLACE FUNCTION fn_block_audit_log_tampering()
RETURNS TRIGGER AS $$
DECLARE
v_conn text := 'dbname=' || current_database();
BEGIN
  PERFORM dblink_exec(
    v_conn,
    format(
      'INSERT INTO audit_log_tampering_incident (operacion, audit_log_id, usuario_bd) VALUES (%L, %L, %L)',
      TG_OP, OLD.id, current_user
    )
  );

  RAISE EXCEPTION
    'audit_log es append-only: % no permitido sobre el registro %',
    TG_OP, OLD.id
    USING ERRCODE = 'raise_exception';

RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_audit_log_update
  BEFORE UPDATE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION fn_block_audit_log_tampering();

CREATE TRIGGER trg_block_audit_log_delete
  BEFORE DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION fn_block_audit_log_tampering();

-- NOTA: TRUNCATE no dispara triggers por fila (BEFORE UPDATE/DELETE),
-- así que un TRUNCATE audit_log se saltaría esta protección por
-- completo. Mitigación recomendada: revocar el privilegio a nivel de
-- rol de aplicación (ajusta 'app_role' al rol real que usa tu
-- datasource en producción):
--
-- REVOKE TRUNCATE, UPDATE, DELETE ON audit_log FROM app_role;
