-- HU-CHA-011: calificar las respuestas del chatbot.
-- Cada respuesta del BOT admite UNA sola calificacion (util / no util) con
-- comentario opcional. Las preguntas no cubiertas se agrupan en
-- brechas_conocimiento para alimentar la base de conocimiento (HU-CHA-004).

CREATE TABLE calificaciones (
                              id                   UUID PRIMARY KEY,
                              mensaje_id           UUID NOT NULL REFERENCES mensajes (id) ON DELETE CASCADE,
                              util                 BOOLEAN NOT NULL,
                              comentario           TEXT,
                              fecha_calificacion   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Unico, no solo indexado: es la regla de negocio "no se admite calificar
-- dos veces la misma respuesta". Aunque el servicio lo revisa antes de
-- guardar, este indice es la garantia final si llegan dos peticiones
-- simultaneas para el mismo mensaje.
CREATE UNIQUE INDEX uk_calificaciones_mensaje_id
  ON calificaciones (mensaje_id);

-- Una fila por pregunta distinta (tras normalizar tildes, mayusculas y
-- signos). Dos fuentes, cada una con su propio contador, para distinguir
-- despues "respondi mal" de "no supe responder":
--   contador_no_util:      la respuesta a esta pregunta se califico no util.
--   contador_escalamiento: MotorRespuestas no entendio la pregunta y escalo.
-- Sin FK a mensajes a proposito: es un agregado anonimo que debe sobrevivir
-- aunque el usuario borre su historial.
CREATE TABLE brechas_conocimiento (
                                    id                         UUID PRIMARY KEY,
                                    texto_normalizado          VARCHAR(500) NOT NULL,
                                    ejemplo_pregunta           TEXT NOT NULL,
                                    contador_no_util           INTEGER NOT NULL DEFAULT 0,
                                    contador_escalamiento      INTEGER NOT NULL DEFAULT 0,
                                    fecha_primera_ocurrencia   TIMESTAMP WITH TIME ZONE NOT NULL,
                                    fecha_ultima_ocurrencia    TIMESTAMP WITH TIME ZONE NOT NULL,
                                    CONSTRAINT chk_brechas_contadores_no_negativos
                                      CHECK (contador_no_util >= 0 AND contador_escalamiento >= 0)
);

-- Clave de agrupacion: la busca el servicio para incrementar el contador
-- de una brecha existente en vez de crear una fila duplicada.
CREATE UNIQUE INDEX uk_brechas_conocimiento_texto_normalizado
  ON brechas_conocimiento (texto_normalizado);
