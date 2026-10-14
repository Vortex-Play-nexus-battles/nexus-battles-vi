-- ms-chatbot.yaml 1.7.0 (7.4.5 «aprendizaje continuo» y 7.4.7 «analisis y
-- metricas», RF-CHA-011 y RF-CHA-012).
--
-- ---------------------------------------------------------------------------
-- 1. Las preguntas sin respuesta llegan al panel con su estado
-- ---------------------------------------------------------------------------
-- Las brechas de conocimiento (V3) se guardaban y nadie las veia. Desde
-- 1.7.0 el administrador las lista (GET /chatbot/admin/brechas) y las marca:
--
--   PENDIENTE   nadie la ha atendido (todas las de antes de V13 lo son);
--   ATENDIDA    un tema de la base ya la cubre;
--   DESCARTADA  es ruido (una prueba, algo que no es del juego).
--
-- `estado_cambiado_por` es el uid del administrador que la marco (claim del
-- token, nunca un dato del cuerpo) y `estado_cambiado_en`, cuando. Una
-- brecha ATENDIDA que se vuelve a preguntar vuelve sola a PENDIENTE (sin
-- quien, con la hora) y suma una `reaperturas`: es la senal de que el tema
-- que la cubria no basta. Sigue sin vinculo con ninguna sesion: es un
-- agregado anonimo que sobrevive a que el usuario borre su historial.
ALTER TABLE brechas_conocimiento
    ADD COLUMN estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN estado_cambiado_por VARCHAR(64),
    ADD COLUMN estado_cambiado_en  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reaperturas         INTEGER     NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_brechas_estado CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'DESCARTADA')),
    ADD CONSTRAINT chk_brechas_reaperturas_no_negativas CHECK (reaperturas >= 0);

-- El listado filtra por estado y por la ultima vez que se pregunto.
CREATE INDEX ix_brechas_estado_ultima_ocurrencia
    ON brechas_conocimiento (estado, fecha_ultima_ocurrencia DESC);

-- ---------------------------------------------------------------------------
-- 2. El resultado de cada respuesta del bot, guardado y no adivinado
-- ---------------------------------------------------------------------------
-- La tasa de resolucion contaba como resuelta toda respuesta no escalada,
-- tambien la consulta en vivo que decia «servicio no disponible» y el «escribe
-- la pregunta completa». Desde 1.7.0 el motor que responde dice como termino
-- y se guarda aqui (ResultadoDeRespuesta). Resuelven TEMA, CONSULTA_EN_VIVO y
-- NAVEGACION. Nulable: los mensajes del USUARIO no lo tienen, y las
-- respuestas anteriores a V13 tampoco; esas no cuentan en la tasa (no se
-- reconstruye un resultado que nadie guardo).
ALTER TABLE mensajes
    ADD COLUMN resultado VARCHAR(30),
    ADD CONSTRAINT chk_mensajes_resultado CHECK (resultado IS NULL OR resultado IN (
        'TEMA', 'CONSULTA_EN_VIVO', 'NAVEGACION', 'NO_DISPONIBLE', 'ESCALADO', 'NO_SIRVIO', 'PIDE_REFORMULAR'));

-- ---------------------------------------------------------------------------
-- 3. Conversaciones iniciadas por periodo
-- ---------------------------------------------------------------------------
-- 7.4.7 pide las conversaciones INICIADAS por periodo. Se cuentan por
-- `fecha_inicio`, que no cambia aunque el usuario borre su historial (la
-- fila de la conversacion queda); antes se contaban las que tenian alguna
-- pregunta en el periodo, y borrar un historial cambiaba el pasado.
CREATE INDEX ix_conversaciones_fecha_inicio ON conversaciones (fecha_inicio);
