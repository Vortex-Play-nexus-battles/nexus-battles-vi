-- HU-CHA-001: ventana de chat del chatbot.
-- Una Conversacion por sesion (visitante anonimo o usuario autenticado);
-- N Mensajes por Conversacion, en el orden en que se enviaron.

CREATE TABLE conversaciones (
                              id                       UUID PRIMARY KEY,
                              identificador_sesion     VARCHAR(255) NOT NULL,
                              autenticado              BOOLEAN NOT NULL DEFAULT FALSE,
                              fecha_inicio             TIMESTAMP WITH TIME ZONE NOT NULL,
                              fecha_ultima_actividad   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Unico, no solo indexado: es la clave de negocio que usa
-- ConversacionRepository.findByIdentificadorSesion para no crear una
-- conversacion duplicada por sesion.
CREATE UNIQUE INDEX uk_conversaciones_identificador_sesion
  ON conversaciones (identificador_sesion);

CREATE TABLE mensajes (
                        id                UUID PRIMARY KEY,
                        conversacion_id   UUID NOT NULL REFERENCES conversaciones (id) ON DELETE CASCADE,
                        remitente         VARCHAR(20) NOT NULL,
                        contenido         TEXT NOT NULL,
                        adjunto_url       VARCHAR(500),
                        fecha_envio       TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT chk_mensajes_remitente CHECK (remitente IN ('USUARIO', 'BOT'))
);

-- Para listar el historial de una conversacion ordenado, sin full scan.
CREATE INDEX idx_mensajes_conversacion_id ON mensajes (conversacion_id);
