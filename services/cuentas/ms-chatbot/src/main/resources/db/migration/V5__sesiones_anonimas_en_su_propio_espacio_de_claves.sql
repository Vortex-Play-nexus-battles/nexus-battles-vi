-- B11 (ms-chatbot.yaml 1.2.0): la conversacion del visitante vive en su propio
-- espacio de claves, nunca en el de los uid.
--
-- El fallo que cierra: hasta aqui el visitante mandaba en X-Id-Sesion-Anonima
-- un identificador que elegia el propio frontend, y el servidor lo usaba como
-- la MISMA clave de conversacion que el uid de un usuario registrado. Quien
-- conociera un uid (son publicos, p. ej. en GET /torneos) leia, calificaba o
-- borraba el historial de ese jugador mandandolo como si fuera suyo.
--
-- Desde esta version:
--   * los usuarios con sesion siguen en su clave de siempre: el uid del token;
--   * los visitantes tienen una sesion -emitida por el servidor (256 bits
--     aleatorios) o declarada por el navegador ('visitante-...', la que manda
--     el asistente de la interfaz)- de la que aqui solo se guarda la huella
--     SHA-256, y su conversacion vive en otro espacio de claves:
--     'anonimo:<id de la sesion>';
--   * las conversaciones anonimas anteriores, con claves que eligio el cliente,
--     se apartan a 'legado-anonimo:<id>': ya no las puede abrir nadie (tampoco
--     el atacante que se hubiera apropiado de la clave de un uid), y dejan libre
--     ese uid para la conversacion de su dueno. Sus mensajes se conservan: las
--     analiticas los siguen contando.

CREATE TABLE sesiones_anonimas (
                                 id                 UUID PRIMARY KEY,
                                 huella             VARCHAR(64) NOT NULL,
                                 creada_en          TIMESTAMP WITH TIME ZONE NOT NULL,
                                 ultima_actividad   TIMESTAMP WITH TIME ZONE NOT NULL,
                                 expira_en          TIMESTAMP WITH TIME ZONE NOT NULL
);

-- La busqueda por huella es la que valida cada peticion de un visitante.
CREATE UNIQUE INDEX uk_sesiones_anonimas_huella ON sesiones_anonimas (huella);

UPDATE conversaciones
SET identificador_sesion = 'legado-anonimo:' || id::text
WHERE autenticado = FALSE;

-- La base garantiza la separacion de los dos espacios de claves: una
-- conversacion de visitante nunca puede tener como clave un uid, y una de
-- usuario nunca puede tener una clave de visitante.
ALTER TABLE conversaciones ADD CONSTRAINT chk_conversaciones_espacio_de_claves CHECK (
    (autenticado AND identificador_sesion NOT LIKE 'anonimo:%' AND identificador_sesion NOT LIKE 'legado-anonimo:%')
    OR (NOT autenticado AND (identificador_sesion LIKE 'anonimo:%' OR identificador_sesion LIKE 'legado-anonimo:%'))
);
