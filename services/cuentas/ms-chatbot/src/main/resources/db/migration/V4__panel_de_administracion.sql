-- HU-CHA-012: panel de administracion y analitica del chatbot.
--   RF-CHA-012  analiticas     -> datos nuevos en cada respuesta del bot
--   RF-CHA-013  base de conocimiento -> prioridad y clave estable por tema
--   RF-CHA-014  "reentrenamiento" -> versiones de la base + casos de evaluacion
--
-- Sobre RF-CHA-014: el chatbot es un motor de reglas (HU-CHA-004), no un
-- modelo entrenable. Aqui el "modelo" es una VERSION de la base de
-- conocimiento: el administrador edita una version BORRADOR, el sistema la
-- evalua contra la version en PRODUCCION con los casos de evaluacion, y solo
-- la despliega si no acierta menos. Las versiones RETIRADA permiten revertir.
-- Interpretacion pendiente de confirmar con el cliente.

-- ---------------------------------------------------------------------------
-- Versiones de la base de conocimiento
-- ---------------------------------------------------------------------------
CREATE TABLE versiones_base_conocimiento (
                                           id                 UUID PRIMARY KEY,
                                           numero             INTEGER NOT NULL,
                                           estado             VARCHAR(20) NOT NULL,
                                           descripcion        VARCHAR(500),
                                           fecha_creacion     TIMESTAMP WITH TIME ZONE NOT NULL,
                                           fecha_despliegue   TIMESTAMP WITH TIME ZONE,
                                           CONSTRAINT chk_versiones_estado CHECK (estado IN ('BORRADOR', 'PRODUCCION', 'RETIRADA'))
);

CREATE UNIQUE INDEX uk_versiones_numero ON versiones_base_conocimiento (numero);

-- Exactamente una version responde a los jugadores, y como mucho hay una
-- candidata en edicion. Indices unicos PARCIALES: la base de datos garantiza
-- las dos reglas aunque dos administradores desplieguen a la vez.
CREATE UNIQUE INDEX uk_versiones_una_en_produccion
  ON versiones_base_conocimiento (estado) WHERE estado = 'PRODUCCION';
CREATE UNIQUE INDEX uk_versiones_un_borrador
  ON versiones_base_conocimiento (estado) WHERE estado = 'BORRADOR';

-- La base sembrada en V2 pasa a ser la version 1, en produccion.
INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion, fecha_despliegue)
VALUES (gen_random_uuid(), 1, 'PRODUCCION', 'Base inicial sembrada en V2 (HU-CHA-004).', now(), now());

-- ---------------------------------------------------------------------------
-- Temas: version a la que pertenecen, clave estable y prioridad
-- ---------------------------------------------------------------------------
ALTER TABLE temas_conocimiento
  ADD COLUMN version_id UUID REFERENCES versiones_base_conocimiento (id) ON DELETE CASCADE,
  ADD COLUMN clave      VARCHAR(80),
  ADD COLUMN prioridad  INTEGER NOT NULL DEFAULT 0;

-- 'clave' identifica un mismo tema a traves de todas sus copias en distintas
-- versiones (el id cambia en cada copia; la clave no). Para los temas de V2
-- se usa su propio id como clave: ya es unico y no hay que inventar nombres.
UPDATE temas_conocimiento
SET version_id = (SELECT id FROM versiones_base_conocimiento WHERE numero = 1),
    clave      = id::text;

ALTER TABLE temas_conocimiento
  ALTER COLUMN version_id SET NOT NULL,
ALTER COLUMN clave SET NOT NULL;

CREATE UNIQUE INDEX uk_temas_version_clave ON temas_conocimiento (version_id, clave);
CREATE INDEX idx_temas_conocimiento_version ON temas_conocimiento (version_id);

-- ---------------------------------------------------------------------------
-- Casos de evaluacion: preguntas con el tema que deberia responderlas
-- ---------------------------------------------------------------------------
CREATE TABLE casos_evaluacion (
                                id                    UUID PRIMARY KEY,
                                pregunta              TEXT NOT NULL,
  -- NULL = la respuesta correcta es escalar (el bot NO deberia saberlo).
                                tema_clave_esperada   VARCHAR(80),
                                activo                BOOLEAN NOT NULL DEFAULT TRUE,
                                fecha_creacion        TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Punto de partida: un caso por tema sembrado, usando como pregunta su primera
-- palabra clave. Es un chequeo de regresion minimo, no una evaluacion real
-- (esas preguntas coinciden por construccion con sus temas): el administrador
-- debe agregar casos con preguntas reales de usuarios.
INSERT INTO casos_evaluacion (id, pregunta, tema_clave_esperada, activo, fecha_creacion)
SELECT gen_random_uuid(), trim(split_part(palabras_clave_es, ',', 1)), clave, TRUE, now()
FROM temas_conocimiento
WHERE activo;

-- Resultado de cada evaluacion de una version (manual o periodica), para
-- comparar candidata contra produccion y detectar desempeno degradado.
CREATE TABLE evaluaciones_version (
                                    id                UUID PRIMARY KEY,
                                    version_id        UUID NOT NULL REFERENCES versiones_base_conocimiento (id) ON DELETE CASCADE,
                                    casos_evaluados   INTEGER NOT NULL,
                                    aciertos          INTEGER NOT NULL,
                                    fecha             TIMESTAMP WITH TIME ZONE NOT NULL,
                                    CONSTRAINT chk_evaluaciones_aciertos CHECK (aciertos >= 0 AND aciertos <= casos_evaluados)
);

CREATE INDEX idx_evaluaciones_version_version ON evaluaciones_version (version_id);

-- ---------------------------------------------------------------------------
-- Analiticas: lo que hace falta saber de cada respuesta del bot
-- ---------------------------------------------------------------------------
-- Todas nulables: los mensajes anteriores a esta migracion (y los mensajes
-- del USUARIO) no tienen estos datos. Las analiticas solo cuentan desde aqui.
ALTER TABLE mensajes
  ADD COLUMN tema_clave           VARCHAR(80),
  ADD COLUMN categoria            VARCHAR(40),
  ADD COLUMN escalado             BOOLEAN,
  ADD COLUMN tiempo_respuesta_ms  INTEGER;

-- Para filtrar las analiticas por periodo sin recorrer toda la tabla.
CREATE INDEX idx_mensajes_fecha_envio ON mensajes (fecha_envio);
