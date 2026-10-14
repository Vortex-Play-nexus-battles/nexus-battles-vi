-- SA-3 — §7.3.4 «filtrar por ... historial de sanciones» (RF-USR-009).
--
-- Migracion ADITIVA (regla 8): dos columnas con valor por omision; no toca
-- datos existentes.
--
-- La fuente de verdad de las sanciones es moderacion-sanciones (su historial
-- dice quien, cuando, por que y hasta cuando). Aqui solo queda el RESUMEN que
-- el directorio necesita para filtrar y paginar en la base sin consultar otro
-- servicio por cada fila: cuantas sanciones se le han emitido a la cuenta y
-- cuando fue la ultima. Lo fija moderacion-sanciones por la ruta interna
-- PUT /internal/usuarios/{uid}/historial-sanciones con el valor absoluto
-- (idempotente) al emitir una sancion y en su reconciliacion periodica. Una
-- sancion revertida en apelacion sigue contando: es historial. Las horas son
-- las del servidor, como el resto de este esquema.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS sanciones_registradas INTEGER NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultima_sancion_en TIMESTAMP(6);
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_sanciones_registradas CHECK (sanciones_registradas >= 0);
