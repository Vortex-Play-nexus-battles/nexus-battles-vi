-- §7.3.5 «El registro de auditoría debe ser inmutable (no puede ser
-- modificado o eliminado)».
--
-- V1 (y V2) cerraron UPDATE y DELETE fila a fila. TRUNCATE no dispara
-- disparadores por fila: un `TRUNCATE audit_log` vaciaba la bitácora entera
-- sin que nada lo impidiera (la nota del final de V1 lo dejaba anotado como
-- pendiente). Un disparador por sentencia, BEFORE TRUNCATE, lo cierra igual:
-- deja el incidente en el log del servidor (RAISE WARNING, como V2) y aborta
-- la sentencia. Nada de la bitácora se toca: solo se añade el disparador.
CREATE OR REPLACE FUNCTION fn_block_audit_log_truncate()
RETURNS TRIGGER AS $$
BEGIN
  RAISE WARNING
    'INCIDENTE_SEGURIDAD audit_log operacion=TRUNCATE usuario_bd=%',
    current_user;

  RAISE EXCEPTION
    'audit_log es append-only: TRUNCATE no permitido'
    USING ERRCODE = 'raise_exception';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_audit_log_truncate
  BEFORE TRUNCATE ON audit_log
  FOR EACH STATEMENT EXECUTE FUNCTION fn_block_audit_log_truncate();
