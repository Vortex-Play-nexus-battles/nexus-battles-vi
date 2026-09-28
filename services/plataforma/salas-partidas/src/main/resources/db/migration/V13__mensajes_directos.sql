-- B6 · Mensajes privados entre jugadores.
--
-- FEEDBACK DEL PROFESOR, no requisito del documento: la seccion 7.6 pide chat
-- en las salas y en la vista general; los mensajes privados se pidieron en la
-- demostracion. Se montan sobre el chat de HU-JUE-015 (mismo broker, misma
-- autenticacion, mismas reglas de sancion y lista negra), sin servicio nuevo.
--
-- Regla 8: por Flyway, versionado; V1 a V12 no se tocan. Regla 7: solo
-- salas-partidas escribe aqui.
--
-- ## Por que una tabla propia y no `mensajes_de_chat` ensanchada
--
-- Ensanchar `mensajes_de_chat` (canal a VARCHAR(100), mas destinatario y
-- leido) era la otra opcion. Se descarto por cuatro motivos:
--
--   1. Privacidad distinta. El historial del chat lo lee cualquiera que siga el
--      canal; una conversacion privada, solo sus dos participantes. En la misma
--      tabla, cualquier consulta del chat que se olvidara de filtrar el prefijo
--      `dm:` (o `Canal.desdeClave`, que no lo conoce) serviria mensajes
--      privados a quien no debe verlos. Separadas, ninguna consulta del chat
--      puede ni nombrarlos.
--   2. Columnas que el chat no tiene: destinatario, leido (por destinatario),
--      el `id_cliente` de la idempotencia y el apodo del destinatario. En el
--      chat serian nulas siempre, y el `logro` del chat en un privado tambien.
--   3. Indices propios: por conversacion y fecha, y por destinatario no leido.
--   4. La tabla del chat es de HU-JUE-015 (Alexander Nino). Esta migracion es
--      aditiva pura: no altera nada suyo.
--
-- Se reutiliza la LOGICA de EnviarMensaje (largo 500, recorte, sancion y lista
-- negra con fallo cerrado), no la tabla.
--
-- La conversacion canonica es `dm:{uidMenor}:{uidMayor}` (76 caracteres); cabe
-- de sobra en 100. El apodo del destinatario se guarda tal como lo dio
-- ms-identidad al comprobar que existe: asi la lista de conversaciones muestra
-- el nombre del otro sin una llamada por fila. Es una foto, como el apodo del
-- autor en el chat.

CREATE TABLE mensajes_directos (
    id                  UUID          PRIMARY KEY,
    conversacion        VARCHAR(100)  NOT NULL,
    id_remitente        UUID          NOT NULL,
    apodo_remitente     VARCHAR(60)   NOT NULL,
    id_destinatario     UUID          NOT NULL,
    apodo_destinatario  VARCHAR(60),
    texto               VARCHAR(500)  NOT NULL,
    enviado_en          TIMESTAMPTZ   NOT NULL,
    leido_en            TIMESTAMPTZ,
    id_cliente          VARCHAR(64),

    CONSTRAINT ck_mensajes_directos_conversacion CHECK (conversacion LIKE 'dm:%:%'),
    CONSTRAINT ck_mensajes_directos_no_a_si_mismo CHECK (id_remitente <> id_destinatario),
    CONSTRAINT ck_mensajes_directos_texto CHECK (char_length(texto) BETWEEN 1 AND 500)
);

COMMENT ON TABLE mensajes_directos IS
    'B6 (feedback del profesor): mensajes privados 1 a 1. Solo sus dos participantes los leen; conversacion = dm:{uidMenor}:{uidMayor}.';

-- El historial de una conversacion se lee de lo mas reciente hacia atras.
CREATE INDEX ix_mensajes_directos_conversacion_fecha
    ON mensajes_directos (conversacion, enviado_en DESC, id DESC);

-- Los no leidos de un destinatario, por remitente: contador de la lista de
-- conversaciones, aviso en la bandeja y «marcar como leido».
CREATE INDEX ix_mensajes_directos_no_leidos
    ON mensajes_directos (id_destinatario, id_remitente)
    WHERE leido_en IS NULL;

-- «Mis conversaciones»: lo escrito y lo recibido por un jugador.
CREATE INDEX ix_mensajes_directos_remitente_fecha
    ON mensajes_directos (id_remitente, enviado_en DESC);
CREATE INDEX ix_mensajes_directos_destinatario_fecha
    ON mensajes_directos (id_destinatario, enviado_en DESC);

-- Idempotencia del envio: un reintento con el mismo idCliente no duplica.
CREATE UNIQUE INDEX ux_mensajes_directos_id_cliente
    ON mensajes_directos (id_remitente, id_cliente)
    WHERE id_cliente IS NOT NULL;
