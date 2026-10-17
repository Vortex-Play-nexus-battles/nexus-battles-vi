-- SA-3 — §7.3.4 «filtrar por ... historial de sanciones» (RF-USR-009).
--
-- Un canal mas en la cola de salidas: HISTORIAL. Cada emision deja, en la
-- MISMA transaccion que la sancion, una salida que al entregarse fija en
-- ms-identidad el resumen del historial de la cuenta (cuantas sanciones y
-- cuando fue la ultima; PUT /internal/usuarios/{uid}/historial-sanciones). Se
-- entrega con los mismos reintentos que la proyeccion y el correo.
--
-- Migracion ADITIVA (regla 8): solo amplia la lista de canales admitidos; no
-- toca filas.
ALTER TABLE avisos_pendientes DROP CONSTRAINT IF EXISTS ck_avisos_pendientes_canal;
ALTER TABLE avisos_pendientes
    ADD CONSTRAINT ck_avisos_pendientes_canal CHECK (canal IN ('AVISO', 'PROYECCION', 'CORREO', 'HISTORIAL'));
