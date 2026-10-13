-- ms-chatbot.yaml 1.3.6 (7.4.5 «recordar preferencias del usuario» y «nivel
-- de detalle de las respuestas»): las preferencias de respuesta de cada
-- conversacion. La clave es la de la conversacion
-- (IdentidadDelChat.claveDeConversacion): el uid del jugador, que asi las
-- conserva entre dispositivos, o 'anonimo:<sesion>' para un visitante, que
-- las pierde cuando vence su sesion. Sin datos personales.
CREATE TABLE preferencias_chat (
                                 clave           VARCHAR(255) PRIMARY KEY,
                                 idioma          VARCHAR(20)  NOT NULL,
                                 nivel_detalle   VARCHAR(20)  NOT NULL,
                                 actualizado_en  TIMESTAMP WITH TIME ZONE NOT NULL,
                                 CONSTRAINT chk_preferencias_chat_idioma
                                   CHECK (idioma IN ('AUTOMATICO', 'ES', 'EN')),
                                 CONSTRAINT chk_preferencias_chat_nivel_detalle
                                   CHECK (nivel_detalle IN ('BREVE', 'NORMAL', 'DETALLADO'))
);
