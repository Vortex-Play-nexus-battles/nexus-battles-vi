-- BACKEND-02 (B1 + identidad de B2) — correo verificado, recuperacion
-- endurecida, preguntas de seguridad y proyeccion de la sancion.
--
-- Migracion ADITIVA (regla 8): columnas y una tabla nuevas, ningun DROP.
-- Ninguna cuenta existente pasa a PENDIENTE_VERIFICACION: solo las altas
-- nuevas nacen asi (RegistroService). Lo unico que se reescribe son datos que
-- ya no deben existir tal como estaban (ver 1 y 2).

-- ---------------------------------------------------------------------------
-- 1. Codigos de un solo uso: se guarda su RESUMEN BCrypt, nunca el valor.
--
-- Hasta aqui `token` guardaba el codigo en claro: cualquiera con lectura de
-- la base (una copia de respaldo, una consulta de diagnostico) podia canjear
-- el restablecimiento de cualquier cuenta. Desde B1 el codigo solo viaja al
-- correo y aqui queda su resumen, igual que una contrasena.
--
-- `token` pasa a admitir nulos (las filas nuevas no lo llenan) y conserva su
-- UNIQUE: en PostgreSQL varios NULL no chocan entre si.
ALTER TABLE tokens_credencial ALTER COLUMN token DROP NOT NULL;
ALTER TABLE tokens_credencial ADD COLUMN codigo_hash VARCHAR(100);
ALTER TABLE tokens_credencial ADD COLUMN intentos_fallidos INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tokens_credencial ADD COLUMN anulado_en TIMESTAMP(6);
-- Cuando se emitio: los limites de reenvio (60 s entre dos, N por hora) se
-- calculan en la base, porque este servicio no tiene Redis.
ALTER TABLE tokens_credencial ADD COLUMN creado_en TIMESTAMP(6);
ALTER TABLE tokens_credencial ADD COLUMN usado_en TIMESTAMP(6);

-- Los codigos en claro que seguian pendientes quedan anulados: el esquema
-- nuevo no sabe verificarlos (no tienen resumen) y caducaban solos en horas
-- (30 min el restablecimiento, 24 h la activacion). Quien lo necesite pide
-- otro. Y el valor en claro se borra de TODAS las filas: usado o caducado ya
-- no sirve para nada, y una version anterior del servicio que volviera por
-- una reversion no mira `anulado_en`: con el valor aun ahi, lo aceptaria.
UPDATE tokens_credencial SET anulado_en = now() WHERE usado = false AND anulado_en IS NULL;
UPDATE tokens_credencial SET token = NULL WHERE token IS NOT NULL;

-- El canje y los limites buscan siempre el ultimo codigo de un tipo de una
-- cuenta.
CREATE INDEX ix_tokens_usuario_tipo ON tokens_credencial (usuario_id, tipo, id);

-- ---------------------------------------------------------------------------
-- 2. Proyeccion de la sancion (B2).
--
-- La fuente de verdad de una sancion es moderacion-sanciones; aqui queda la
-- proyeccion que el login necesita para negarse sin llamar a nadie: estado,
-- fin de la suspension (ya existian) y la sancion que los produjo, que es lo
-- que hace idempotente `PUT /internal/usuarios/{uid}/estado-sancion`.
ALTER TABLE usuarios ADD COLUMN sancion_id UUID;

-- Los estados de sancion se escriben como los publica el contrato
-- (ms-identidad-admin.yaml: SUSPENDIDO, BANEADO). El panel antiguo los
-- guardaba en femenino; el codigo sigue leyendo las dos formas.
UPDATE usuarios SET estado = 'SUSPENDIDO' WHERE estado = 'SUSPENDIDA';
UPDATE usuarios SET estado = 'BANEADO' WHERE estado = 'BANEADA';

-- ---------------------------------------------------------------------------
-- 3. Preguntas de seguridad (7.1.1: «recuperar su cuenta contestando
-- preguntas con respuestas previamente configuradas y el codigo enviado a su
-- correo»). La PERSONA escribe sus preguntas; el sistema no trae catalogo.
-- La respuesta se guarda normalizada y con BCrypt, nunca en claro.
CREATE TABLE preguntas_seguridad (
    id             UUID         NOT NULL,
    usuario_id     BIGINT       NOT NULL,
    texto          VARCHAR(200) NOT NULL,
    respuesta_hash VARCHAR(100) NOT NULL,
    orden          INTEGER      NOT NULL,
    creado_en      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_preguntas_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT uk_preguntas_usuario_orden UNIQUE (usuario_id, orden)
);
