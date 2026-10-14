-- SA-20 — §7.3.4 «Alertas de comportamiento sospechoso», señal 1 de D-45
-- (decidida por el PO el 2026-10-06): una cuenta bloqueada por intentos
-- fallidos de inicio de sesion.
--
-- Migracion ADITIVA (regla 8): una columna nula y su indice; no toca datos.
--
-- `bloqueado_hasta` dice si la cuenta esta bloqueada AHORA, y se borra en
-- cuanto vuelve a entrar o falla otra vez despues del bloqueo: con eso solo,
-- un bloqueo de la madrugada no se veria por la mañana. `ultimo_bloqueo_en`
-- guarda cuando empezo el ultimo bloqueo y no se borra: con el, la consola ve
-- las cuentas bloqueadas en un periodo. Las horas son las del servidor, como
-- el resto de este esquema. Los bloqueos anteriores a esta migracion no
-- tienen marca y no se inventa: cuentan desde el despliegue.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultimo_bloqueo_en TIMESTAMP(6);
CREATE INDEX IF NOT EXISTS idx_usuarios_ultimo_bloqueo_en ON usuarios (ultimo_bloqueo_en);
