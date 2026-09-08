-- HU-JUE-015 · historial del chat de salas y de la vista general.
--
-- Regla 8: por Flyway, versionado. Regla 7: solo salas-partidas escribe aqui.
--
-- Un solo historial para los dos chats: la columna canal vale 'general' o
-- 'sala:<uuid>'. No hay clave foranea a salas a proposito: la conversacion de
-- una sala se conserva aunque la sala se cancele, y el chat general no tiene
-- sala. El texto se limita a 500 como fija el contrato AsyncAPI.

CREATE TABLE IF NOT EXISTS mensajes_de_chat (
    id            UUID          PRIMARY KEY,
    canal         VARCHAR(50)   NOT NULL,
    id_autor      UUID          NOT NULL,
    apodo_autor   VARCHAR(60)   NOT NULL,
    tipo          VARCHAR(20)   NOT NULL,
    texto         VARCHAR(500)  NOT NULL,
    logro_mision  VARCHAR(120),
    logro_titulo  VARCHAR(120),
    enviado_en    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT ck_mensajes_de_chat_tipo CHECK (tipo IN ('MENSAJE', 'LOGRO'))
);

-- El historial se lee siempre por canal y de lo mas reciente hacia atras.
CREATE INDEX IF NOT EXISTS ix_mensajes_de_chat_canal_fecha
    ON mensajes_de_chat (canal, enviado_en DESC);
