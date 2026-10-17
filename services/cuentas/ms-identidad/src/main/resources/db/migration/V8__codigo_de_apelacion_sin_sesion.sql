-- SA-4 — §7.3.2 «Los usuarios sancionados deben tener derecho a apelar».
--
-- Una cuenta suspendida o baneada no puede iniciar sesion, y sin sesion no
-- habia por donde apelar (RF-USR-007 deja sin decir si el baneado conserva
-- el portal: no lo conserva). Al comprobar su contrasena, el login le
-- entrega un codigo de un solo uso con el proposito APELAR: con el ve la
-- sancion que le impide entrar y presenta su apelacion, y para nada mas.
--
-- Es la misma tabla y el mismo tratamiento que el desafio del segundo paso
-- (V5): valor opaco de 256 bits, guardado como SHA-256, caduca y se liga a
-- la version del token. Migracion ADITIVA (regla 8): solo amplia el CHECK.
ALTER TABLE desafios_acceso DROP CONSTRAINT IF EXISTS ck_desafios_acceso_proposito;
ALTER TABLE desafios_acceso ADD CONSTRAINT ck_desafios_acceso_proposito
    CHECK (proposito IN ('VERIFICAR', 'ENROLAR', 'APELAR'));
