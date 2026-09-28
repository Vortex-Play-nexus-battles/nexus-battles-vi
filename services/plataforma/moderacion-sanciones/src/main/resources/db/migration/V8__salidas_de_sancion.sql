-- 7.3.2 — sanciones unificadas (B2, BACKEND-04): la cola de avisos pasa a ser
-- la cola de TODAS las salidas de una sancion.
--
-- Regla 8: aditiva. La tabla conserva su nombre, sus filas y sus columnas; se
-- anaden columnas y se relaja el NOT NULL de titulo y cuerpo, que solo tienen
-- sentido para el aviso en la app.
--
-- Hasta aqui una sancion salia por un solo canal: el aviso a la bandeja del
-- jugador (HU-NOT-005), con reintento cada 15 s. Desde B2 esta es la unica
-- fuente de verdad de las sanciones (moderacion-sanciones-admin.yaml 1.1.x) y
-- cada cambio que afecta al jugador sale por tres canales, con el mismo
-- reintento y sin perderse si el destino esta caido:
--
--   AVISO       la bandeja del jugador (notificaciones, POST /internal/notifications).
--   PROYECCION  el estado de acceso de la cuenta en ms-identidad
--               (PUT /api/v1/internal/usuarios/{uid}/estado-sancion), que es lo
--               que niega el login a una cuenta suspendida o baneada.
--   CORREO      el correo al jugador (correo, POST /api/v1/correos/sancion), con
--               la direccion que da ms-identidad en el momento de enviar: aqui no
--               se guarda ninguna copia del correo (regla 7, minimizacion).
--
-- Nada de esto se envia dentro de la transaccion de la sancion: la sancion y
-- sus salidas se guardan juntas y un trabajador las entrega despues. Si
-- identidad o correo estan caidos la sancion queda registrada igual y la
-- salida espera su turno.
--
--   canal              AVISO (las filas que ya existian), PROYECCION o CORREO.
--   sancion_id         la sancion que la origina (null en avisos anteriores).
--   apelacion_id       la apelacion, si la salida es de su resolucion.
--   clave              identificador estable de la salida; en CORREO es la
--                      cabecera Idempotency-Key (p. ej. sancion-<id>-emision),
--                      asi un reintento no manda dos correos. Unica.
--   proximo_intento_en espera entre reintentos, que crece con cada fallo hasta
--                      un maximo: un destino caido no se martillea cada 15 s ni
--                      tapa a las salidas que vienen detras.
--
-- En las filas de PROYECCION y CORREO, `tipo` es el evento que las origina
-- (EMISION, APELACION_RESUELTA, LEVANTAMIENTO); en las de AVISO sigue siendo el
-- tipo de notificacion (SANCION_SUSPENSION, APELACION_REVERTIDA...).
ALTER TABLE avisos_pendientes ADD COLUMN IF NOT EXISTS canal varchar(20) NOT NULL DEFAULT 'AVISO';
ALTER TABLE avisos_pendientes ADD COLUMN IF NOT EXISTS sancion_id uuid;
ALTER TABLE avisos_pendientes ADD COLUMN IF NOT EXISTS apelacion_id uuid;
ALTER TABLE avisos_pendientes ADD COLUMN IF NOT EXISTS clave varchar(160);
ALTER TABLE avisos_pendientes ADD COLUMN IF NOT EXISTS proximo_intento_en timestamptz;

ALTER TABLE avisos_pendientes ALTER COLUMN titulo DROP NOT NULL;
ALTER TABLE avisos_pendientes ALTER COLUMN cuerpo DROP NOT NULL;

ALTER TABLE avisos_pendientes
    ADD CONSTRAINT ck_avisos_pendientes_canal CHECK (canal IN ('AVISO', 'PROYECCION', 'CORREO'));

-- Un aviso sin titulo o sin cuerpo no se puede entregar; una proyeccion o un
-- correo sin sancion tampoco.
ALTER TABLE avisos_pendientes
    ADD CONSTRAINT ck_avisos_pendientes_contenido CHECK (
        (canal = 'AVISO' AND titulo IS NOT NULL AND cuerpo IS NOT NULL)
        OR (canal <> 'AVISO' AND sancion_id IS NOT NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_avisos_pendientes_clave ON avisos_pendientes (clave);

COMMENT ON TABLE avisos_pendientes IS
    'Salidas de las sanciones (AVISO, PROYECCION, CORREO) con reintento; nunca se descarta ninguna.';
