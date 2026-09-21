-- Reemplaza dblink por RAISE WARNING, para no depender de permisos de
-- superusuario en el despliegue (AWS no los otorga por defecto).
-- El incidente ya no queda en una tabla SQL consultable, pero sí en los
-- logs del servidor de PostgreSQL -- esos logs no forman parte de la
-- transacción, así que sobreviven al RAISE EXCEPTION sin necesitar
-- ninguna conexión aparte.

CREATE OR REPLACE FUNCTION fn_block_audit_log_tampering()
RETURNS TRIGGER AS $$
BEGIN
  RAISE WARNING
    'INCIDENTE_SEGURIDAD audit_log operacion=% registro=% usuario_bd=%',
    TG_OP, OLD.id, current_user;

  RAISE EXCEPTION
    'audit_log es append-only: % no permitido sobre el registro %',
    TG_OP, OLD.id
    USING ERRCODE = 'raise_exception';

RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Ya no se necesita la extensión.
DROP EXTENSION IF EXISTS dblink;
