-- ms-chatbot.yaml 1.3.0 (7.4.3, RF-CHA-007, RF-ADM-004): tickets de soporte
-- humano. Cuando el chatbot no resuelve algo, el jugador con sesion abre un
-- ticket y un administrador lo atiende desde el panel.
--
-- Datos personales: solo el uid del token. Sin email, nombre ni telefono
-- (acordado con plataforma). El contexto de la conversacion se copia al abrir
-- el ticket YA REDACTADO (sin contrasenas, tarjetas ni tokens), para que el
-- ticket no dependa de que el jugador conserve su historial.

CREATE TABLE tickets_soporte (
                               id              UUID PRIMARY KEY,
                               uid             VARCHAR(64)  NOT NULL,
                               categoria       VARCHAR(40)  NOT NULL,
                               asunto          VARCHAR(150) NOT NULL,
                               mensaje         VARCHAR(2000) NOT NULL,
                               estado          VARCHAR(20)  NOT NULL,
                               respuesta       VARCHAR(2000),
                               asignado_a      VARCHAR(64),
                               creado_en       TIMESTAMP WITH TIME ZONE NOT NULL,
                               actualizado_en  TIMESTAMP WITH TIME ZONE NOT NULL,
                               CONSTRAINT chk_tickets_soporte_estado
                                 CHECK (estado IN ('ABIERTO', 'EN_PROCESO', 'RESUELTO', 'CERRADO')),
  -- Un ticket resuelto siempre le dice algo al jugador.
                               CONSTRAINT chk_tickets_soporte_resuelto_con_respuesta
                                 CHECK (estado <> 'RESUELTO' OR (respuesta IS NOT NULL AND btrim(respuesta) <> ''))
);

-- Un solo ticket abierto (ABIERTO o EN_PROCESO) por conversacion. La
-- conversacion de un jugador es su uid (V5), asi que basta con el uid. Lo
-- garantiza la base aunque lleguen dos peticiones a la vez.
CREATE UNIQUE INDEX uk_tickets_soporte_uno_abierto_por_jugador
  ON tickets_soporte (uid)
  WHERE estado IN ('ABIERTO', 'EN_PROCESO');

-- "Mis tickets" del jugador y la bandeja del administrador por estado, los
-- mas recientes primero.
CREATE INDEX ix_tickets_soporte_uid_creado ON tickets_soporte (uid, creado_en DESC);
CREATE INDEX ix_tickets_soporte_estado_creado ON tickets_soporte (estado, creado_en DESC);

-- Ultimos mensajes de la conversacion al abrir el ticket, ya redactados.
CREATE TABLE tickets_soporte_contexto (
                                        ticket_id    UUID NOT NULL REFERENCES tickets_soporte (id) ON DELETE CASCADE,
                                        orden        INTEGER NOT NULL,
                                        remitente    VARCHAR(10) NOT NULL,
                                        contenido    TEXT NOT NULL,
                                        fecha_envio  TIMESTAMP WITH TIME ZONE NOT NULL,
                                        PRIMARY KEY (ticket_id, orden),
                                        CONSTRAINT chk_tickets_soporte_contexto_remitente CHECK (remitente IN ('USUARIO', 'BOT'))
);
